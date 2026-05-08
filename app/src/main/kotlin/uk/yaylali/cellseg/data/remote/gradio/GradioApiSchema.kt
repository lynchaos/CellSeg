package uk.yaylali.cellseg.data.remote.gradio

/**
 * Resolved schema for a Space's segmentation endpoint.
 * Discovered at runtime; never hard-coded.
 */
data class GradioApiSchema(
    val spaceBaseUrl: String,
    val fnIndex: Int,
    val apiName: String?,
)

/** HF Space base-URL helpers. */
object SpaceUrlHelper {
    /** Convert a Gradle slug "mouseland-cellpose" → "https://mouseland-cellpose.hf.space" */
    fun baseUrl(slug: String): String {
        val sanitised = slug.trim().lowercase()
        return if (sanitised.startsWith("http")) sanitised.trimEnd('/')
        else "https://$sanitised.hf.space"
    }

    fun infoUrl(slug: String, allEndpoints: Boolean = false) =
        "${baseUrl(slug)}/gradio_api/info" + if (allEndpoints) "?all_endpoints=True" else ""
    fun uploadUrl(slug: String) = "${baseUrl(slug)}/gradio_api/upload"
    fun queueJoinUrl(slug: String) = "${baseUrl(slug)}/gradio_api/queue/join"
    fun queueDataUrl(slug: String, sessionHash: String) =
        "${baseUrl(slug)}/gradio_api/queue/data?session_hash=$sessionHash"
}
