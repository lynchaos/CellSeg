package uk.yaylali.cellseg.data.remote.gradio

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── /info and /config ────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class SpaceInfoResponse(
    @Json(name = "named_endpoints") val namedEndpoints: Map<String, EndpointInfo>?,
    @Json(name = "unnamed_endpoints") val unnamedEndpoints: Map<String, EndpointInfo>?,
)

@JsonClass(generateAdapter = true)
data class EndpointInfo(
    @Json(name = "parameters") val parameters: List<ComponentInfo>?,
    @Json(name = "returns") val returns: List<ComponentInfo>?,
)

@JsonClass(generateAdapter = true)
data class ComponentInfo(
    @Json(name = "label") val label: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "component") val component: String?,
    @Json(name = "example_input") val exampleInput: Any?,
)

// ── Upload response ──────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class UploadResponse(
    @Json(name = "files") val files: List<String>?,
)

// Wrapper sent as a component value in predict/queue
@JsonClass(generateAdapter = true)
data class GradioFileRef(
    @Json(name = "path") val path: String,
    @Json(name = "meta") val meta: GradioFileMeta = GradioFileMeta(),
)

@JsonClass(generateAdapter = true)
data class GradioFileMeta(
    @Json(name = "_type") val type: String = "gradio.FileData",
)

// ── Queue join request/response ──────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class QueueJoinRequest(
    @Json(name = "data") val data: List<Any?>,
    @Json(name = "fn_index") val fnIndex: Int,
    @Json(name = "session_hash") val sessionHash: String,
    @Json(name = "api_name") val apiName: String? = null,
)

@JsonClass(generateAdapter = true)
data class QueueJoinResponse(
    @Json(name = "event_id") val eventId: String?,
    @Json(name = "status") val status: String?,
)

// ── SSE payloads ─────────────────────────────────────────────────────────────

sealed class SseEvent {
    data class Estimation(val rank: Int, val queueSize: Int, val avgDuration: Double?) : SseEvent()
    data class ProcessStarts(val eta: Double?) : SseEvent()
    data class ProcessCompleted(val output: CompletedOutput) : SseEvent()
    data class UnexpectedError(val message: String) : SseEvent()
    /** Gradio 4.x two-phase protocol: server asks client to confirm session hash */
    object SendHash : SseEvent()
    /** Gradio 4.x two-phase protocol: server asks client to POST actual input data */
    data class SendData(val eventId: String?) : SseEvent()
    object QueueFull : SseEvent()
    data class Heartbeat(val ts: Long) : SseEvent()
}

@JsonClass(generateAdapter = true)
data class CompletedOutput(
    @Json(name = "data") val data: List<Any?>?,
    @Json(name = "error") val error: String?,
)

// ── Resolved artefacts from process_completed payload ───────────────────────

data class RemoteArtifacts(
    val outlinesImageUrl: String?,
    val flowsImageUrl: String?,
    val masksTiffUrl: String?,
    val outlinesPngUrl: String?,
)

/** Thrown by [GradioClient] when the Space returns a non-JSON body (e.g. HTML 503 while waking up). */
class SpaceSleepingException(message: String = "Space is still waking up. Retry shortly.") : Exception(message)
