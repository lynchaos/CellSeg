package uk.yaylali.cellseg.data.remote.gradio

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.json.JSONObject
import timber.log.Timber

/**
 * Parses Server-Sent Events from the Gradio queue/data stream.
 *
 * Each SSE message has the form:
 *   data: <json>
 *
 * We handle: estimation, process_starts, process_completed, unexpected_error, queue_full, heartbeat.
 */
class GradioSseParser(private val moshi: Moshi) {

    private val anyAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    fun parse(rawData: String): SseEvent? {
        return try {
            val obj = JSONObject(rawData)
            val msgType = obj.optString("msg")
            Timber.d("GradioSse: msg=%s", msgType)
            when (msgType) {
                "estimation" -> SseEvent.Estimation(
                    rank = obj.optInt("rank", 0),
                    queueSize = obj.optInt("queue_size", 0),
                    avgDuration = obj.optDouble("avg_event_process_time").takeIf { !it.isNaN() },
                )
                "process_starts" -> SseEvent.ProcessStarts(
                    eta = obj.optDouble("eta").takeIf { !it.isNaN() },
                )
                "process_completed" -> {
                    val outputObj = obj.optJSONObject("output")
                    val topSuccess = obj.optBoolean("success", true)
                    val topTitle = obj.optString("title").ifEmpty { null }
                    val topTraceback = obj.optString("traceback").ifEmpty { null }
                    Timber.d("GradioSse: process_completed success=%b title=%s", topSuccess, topTitle)
                    if (topTraceback != null) Timber.d("GradioSse: traceback=%s", topTraceback.take(300))
                    if (outputObj != null) {
                        // org.json returns the string "null" for JSON null — use isNull() guard
                        val error = if (outputObj.isNull("error")) null
                                    else outputObj.optString("error").ifEmpty { null }
                        val dataArray = outputObj.optJSONArray("data")
                        val dataList: List<Any?> = if (dataArray != null) {
                            (0 until dataArray.length()).map { dataArray.get(it) }
                        } else emptyList()
                        // When success=false and no data, surface the server error title
                        val effectiveError = error
                            ?: if (!topSuccess && dataList.isEmpty()) topTitle ?: "Server error (success=false)"
                               else null
                        SseEvent.ProcessCompleted(CompletedOutput(data = dataList, error = effectiveError))
                    } else {
                        null
                    }
                }
                "unexpected_error" -> SseEvent.UnexpectedError(
                    message = obj.optString("message", "Unknown error from Space")
                )
                "send_hash" -> SseEvent.SendHash
                "send_data" -> SseEvent.SendData(
                    eventId = obj.optString("event_id").ifEmpty { null }
                )
                "queue_full" -> SseEvent.QueueFull
                "heartbeat" -> SseEvent.Heartbeat(System.currentTimeMillis())
                "close_stream" -> null  // normal end-of-stream signal, no action needed
                else -> {
                    Timber.d("GradioSse: unhandled msg type '$msgType'")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "GradioSse: failed to parse: $rawData")
            null
        }
    }

    /**
     * Extract [RemoteArtifacts] from process_completed output.data.
     * Expected layout (index 0..3):
     *   [0] outlines_image     -> {"path":..., "url":...}
     *   [1] flows_image        -> {"path":..., "url":...}
     *   [2] download_button_masks  -> file data (TIFF)
     *   [3] download_button_outlines_png -> file data
     */
    fun extractArtifacts(output: CompletedOutput, spaceBaseUrl: String): RemoteArtifacts {
        val data = output.data ?: return RemoteArtifacts(null, null, null, null)
        fun urlAt(index: Int): String? {
            val elem = data.getOrNull(index) ?: return null
            return when (elem) {
                is Map<*, *> -> {
                    val url = elem["url"] as? String
                    val path = elem["path"] as? String
                    url ?: path?.let { "$spaceBaseUrl/gradio_api/file=$it" }
                }
                is String -> elem
                else -> null
            }
        }
        return RemoteArtifacts(
            outlinesImageUrl = urlAt(0),
            flowsImageUrl = urlAt(1),
            masksTiffUrl = urlAt(2),
            outlinesPngUrl = urlAt(3),
        )
    }
}
