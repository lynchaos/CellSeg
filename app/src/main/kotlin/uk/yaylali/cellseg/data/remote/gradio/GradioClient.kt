package uk.yaylali.cellseg.data.remote.gradio

import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import uk.yaylali.cellseg.domain.backend.SegmentationError
import uk.yaylali.cellseg.domain.backend.SegmentationProgress
import uk.yaylali.cellseg.domain.model.SegmentationParams
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class GradioClient @Inject constructor(
    private val api: GradioApiService,
    @Named("hf_client") private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val sseParser: GradioSseParser,
) {
    // In-memory schema cache keyed by slug (cleared on each app launch)
    private val schemaCache = mutableMapOf<String, GradioApiSchema>()

    /** Discover the segmentation fn_index for a Space. Caches result per session. */
    suspend fun discoverSchema(spaceSlug: String): GradioApiSchema {
        schemaCache[spaceSlug]?.let { return it }

        val infoUrl = SpaceUrlHelper.infoUrl(spaceSlug, allEndpoints = true)
        Timber.d("GradioClient: discovering schema at %s", SpaceUrlHelper.baseUrl(spaceSlug))

        // Use raw OkHttp so we can inspect the HTTP status code and body type before
        // handing off to Moshi. A sleeping/cold HF Space returns an HTML 503 page that
        // would otherwise cause a misleading "malformed JSON" exception.
        val bodyText = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(infoUrl).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    response.code == 503 || response.code == 502 ->
                        throw SpaceSleepingException("Space is waking up (HTTP ${response.code})")
                    !response.isSuccessful ->
                        throw RuntimeException("Space returned HTTP ${response.code}")
                    text.isBlank() || text.trimStart().firstOrNull() != '{' ->
                        throw SpaceSleepingException("Space is still waking up")
                    else -> text
                }
            }
        }

        // Parse only the top-level named_endpoints keys — we don't need the deep
        // parameter/type schema, and Moshi would choke on `type` being an object.
        val namedKeys: List<String> = try {
            val obj = org.json.JSONObject(bodyText)
            val endpoints = obj.optJSONObject("named_endpoints")
            if (endpoints != null) {
                val keys = mutableListOf<String>()
                val iter = endpoints.keys()
                while (iter.hasNext()) keys += iter.next()
                keys
            } else emptyList()
        } catch (e: Exception) {
            throw RuntimeException("Failed to parse /info response: ${e.message}")
        }
        val segmentKey = namedKeys.firstOrNull { it.contains("segment", ignoreCase = true) }
            ?: namedKeys.firstOrNull { it.contains("predict", ignoreCase = true) }
            ?: namedKeys.firstOrNull()
        val fnIndex = if (segmentKey != null) namedKeys.indexOf(segmentKey) else 0

        Timber.d("GradioClient: resolved fnIndex=%d apiName=%s", fnIndex, segmentKey)

        val schema = GradioApiSchema(
            spaceBaseUrl = SpaceUrlHelper.baseUrl(spaceSlug),
            fnIndex = fnIndex,
            apiName = segmentKey,
        )
        schemaCache[spaceSlug] = schema
        return schema
    }

    /**
     * Upload [imageFile] to the Space's upload endpoint.
     * Retries twice with exponential back-off on network errors.
     */
    suspend fun upload(spaceSlug: String, imageFile: File): GradioFileRef {
        val uploadUrl = SpaceUrlHelper.uploadUrl(spaceSlug)
        val part = MultipartBody.Part.createFormData(
            "files",
            imageFile.name,
            imageFile.asRequestBody("image/*".toMediaType()),
        )
        var lastException: Exception? = null
        repeat(3) { attempt ->
            try {
                val result = api.uploadFile(uploadUrl, part)
                val serverPath = result.firstOrNull()
                    ?: throw IllegalStateException("Upload returned empty file list")
                Timber.d("GradioClient: uploaded to %s", serverPath.take(60))
                return GradioFileRef(path = serverPath)
            } catch (e: Exception) {
                lastException = e
                if (attempt < 2) {
                    // back-off: 1 s, 4 s
                    kotlinx.coroutines.delay((1000L * (1 shl attempt)))
                }
            }
        }
        throw lastException ?: RuntimeException("Upload failed after retries")
    }

    /**
     * Joins the queue and streams [SegmentationProgress] via SSE.
     * Returns a cold [Flow] that completes when a terminal SSE event is received.
     */
    fun runCellposeSegment(
        spaceSlug: String,
        fileRef: GradioFileRef,
        params: SegmentationParams,
        schema: GradioApiSchema,
        sessionHash: String,
    ): Flow<SegmentationProgress> = flow {

        val baseUrl = schema.spaceBaseUrl
        val queueDataPostUrl = "$baseUrl/gradio_api/queue/data"

        // Build the inputs JSON once — reused for send_data POST
        val fileDataObj = org.json.JSONObject().apply {
            put("path", fileRef.path)
            put("meta", org.json.JSONObject().apply { put("_type", "gradio.FileData") })
        }
        val inputsArray = org.json.JSONArray().apply {
            put(org.json.JSONArray().apply { put(fileDataObj) }) // ListFiles wrapper
            put(params.maxResize)
            put(params.maxIter)
            put(params.flowThreshold)
            put(params.cellProbThreshold)
        }

        // 1. Join the queue — Gradio queue/join requires fn_index + data + session_hash
        // api_name is optional but fn_index must always be present
        val joinUrl = SpaceUrlHelper.queueJoinUrl(spaceSlug)
        val joinJson = org.json.JSONObject().apply {
            put("fn_index", schema.fnIndex)
            put("data", inputsArray)
            put("session_hash", sessionHash)
            schema.apiName?.let { put("api_name", it) }
        }.toString()
        Timber.d("GradioClient: joining queue session=%s body=%.300s", sessionHash, joinJson)
        api.queueJoin(joinUrl, joinJson.toRequestBody("application/json".toMediaType()))

        // 2. Open SSE stream
        val dataUrl = SpaceUrlHelper.queueDataUrl(spaceSlug, sessionHash)
        val sseEvents = openSseFlow(dataUrl)

        // 3. Process SSE events
        var reconnectAttempts = 0
        sseEvents.collect { rawData ->
            val event = sseParser.parse(rawData) ?: return@collect
            when (event) {
                is SseEvent.Estimation -> {
                    val eta = event.avgDuration?.toInt()
                    emit(SegmentationProgress.QueuePosition(event.rank, eta))
                }
                is SseEvent.ProcessStarts -> {
                    emit(SegmentationProgress.StatusUpdate("Running on GPU…"))
                }
                is SseEvent.ProcessCompleted -> {
                    if (event.output.error != null) {
                        emit(SegmentationProgress.Failed(SegmentationError.BackendError(event.output.error)))
                        return@collect
                    }
                    // data[] elements come back as org.json.JSONObject (not Map) from JSONArray.get().
                    // Some outputs are wrapped: {"visible":true,"value":{...FileData...}} — unwrap first.
                    val urlsCsv = event.output.data?.mapIndexed { idx, v ->
                        val obj = when (v) {
                            is org.json.JSONObject -> {
                                // Unwrap {"visible":..., "value":{...FileData...}} if present
                                val inner = v.optJSONObject("value")
                                inner ?: v
                            }
                            else -> null
                        }
                        val extracted = obj?.optString("url")?.ifEmpty { null }
                            ?: obj?.optString("path")?.ifEmpty { null }
                            ?: ""
                        Timber.d("GradioClient output[%d] type=%s value=%s", idx, v?.javaClass?.simpleName, extracted.take(80))
                        extracted
                    }?.joinToString("|") ?: ""
                    Timber.d("GradioClient process_completed urlsCsv: %s", urlsCsv)
                    emit(SegmentationProgress.StatusUpdate("__process_completed__:$urlsCsv"))
                    return@collect
                }
                is SseEvent.UnexpectedError -> {
                    emit(SegmentationProgress.Failed(SegmentationError.BackendError(event.message)))
                    return@collect
                }
                is SseEvent.QueueFull -> {
                    emit(SegmentationProgress.Failed(SegmentationError.RateLimited("Queue is full. Try again later.")))
                    return@collect
                }
                is SseEvent.SendHash -> {
                    // Two-phase Gradio 4.x protocol: confirm session hash
                    Timber.d("GradioClient: responding to send_hash for session %s", sessionHash)
                    val body = org.json.JSONObject().apply {
                        put("session_hash", sessionHash)
                    }.toString()
                    postQueueData(queueDataPostUrl, body)
                }
                is SseEvent.SendData -> {
                    // Two-phase Gradio 4.x protocol: POST actual inputs when server asks
                    Timber.d("GradioClient: responding to send_data event_id=%s", event.eventId)
                    val body = org.json.JSONObject().apply {
                        put("data", inputsArray)
                        if (schema.apiName == null) put("fn_index", schema.fnIndex)
                        put("session_hash", sessionHash)
                        schema.apiName?.let { put("api_name", it) }
                        event.eventId?.let { put("event_id", it) }
                    }.toString()
                    postQueueData(queueDataPostUrl, body)
                }
                is SseEvent.Heartbeat -> { /* keep-alive, ignore */ }
            }
        }
    }

    private suspend fun postQueueData(url: String, jsonBody: String) = withContext(Dispatchers.IO) {
        val body = jsonBody.toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.w("GradioClient: queueData POST failed: %d", response.code)
            } else {
                Timber.d("GradioClient: queueData POST OK")
            }
        }
    }

    private fun openSseFlow(url: String): Flow<String> = callbackFlow {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .get()
            .build()
        val call = okHttpClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Timber.w(e, "GradioClient SSE: connection failed")
                close(e)
            }
            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        close(RuntimeException("SSE HTTP ${response.code}"))
                        return
                    }
                    try {
                        val body = response.body
                        if (body == null) {
                            close(RuntimeException("SSE response has no body"))
                            return
                        }
                        val source = body.source()
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.startsWith("data: ")) {
                                val payload = line.removePrefix("data: ")
                                // Log in 200-char chunks to avoid truncation
                                var offset = 0
                                while (offset < payload.length) {
                                    val end = minOf(offset + 200, payload.length)
                                    Timber.d("GradioClient SSE raw[%d]: %s", offset, payload.substring(offset, end))
                                    offset = end
                                }
                                trySendBlocking(payload)
                            }
                        }
                        Timber.d("GradioClient SSE: stream ended normally")
                        close()
                    } catch (e: Exception) {
                        Timber.w(e, "GradioClient SSE: read error")
                        close(e)
                    }
                }
            }
        })
        awaitClose { call.cancel() }
    }

    /** Download a file from the Space to a local [destination] file. */
    suspend fun downloadArtifact(url: String, destination: File) = withContext(Dispatchers.IO) {
        Timber.d("GradioClient: downloading artifact → %s", destination.name)
        val request = Request.Builder().url(url).build()
        val call = okHttpClient.newCall(request)
        // Cancel the OkHttp call if the coroutine is cancelled
        val job = coroutineContext[kotlinx.coroutines.Job]
        val listener = job?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful)
                    throw RuntimeException("Artifact download failed: ${response.code}")
                val body = response.body
                    ?: throw RuntimeException("Empty response body for ${destination.name}")
                destination.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
        } finally {
            listener?.dispose()
        }
        Timber.d("GradioClient: downloaded → %s (%d bytes)", destination.name, destination.length())
    }
}
