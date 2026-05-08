package uk.yaylali.cellseg.domain.model

/**
 * Parameters forwarded to both the local cyto3 pipeline and the remote Gradio Space.
 * Defaults match the Cellpose documentation recommendations.
 */
data class SegmentationParams(
    val maxResize: Int = 256,
    val maxIter: Int = 250,
    val diameter: Double = 30.0,
    val flowThreshold: Float = 0.4f,
    val cellProbThreshold: Float = 0.0f,
) {
    companion object {
        val DEFAULT = SegmentationParams()

        // Named factory presets — maxResize capped at 256 to keep ONNX intermediate
        // activation buffers within Android heap limits (~60 MB at 256×256 vs ~1.3 GB at 1024×).  
        val PRESET_4X  = SegmentationParams(maxResize = 256, maxIter = 200, flowThreshold = 0.4f, cellProbThreshold = 0.0f)
        val PRESET_10X = SegmentationParams(maxResize = 256, maxIter = 250, flowThreshold = 0.4f, cellProbThreshold = 0.0f)
        val PRESET_20X = SegmentationParams(maxResize = 256, maxIter = 300, flowThreshold = 0.4f, cellProbThreshold = 0.0f)
    }
}

data class NamedPreset(val name: String, val params: SegmentationParams)

/** Validation: returns null on success, or a human-readable error string. */
fun SegmentationParams.validate(): String? {
    if (maxResize !in 256..2000) return "Max dimension must be 256–2000 px."
    if (maxIter !in 100..500) return "Max iterations must be 100–500."
    if (diameter !in 5.0..500.0) return "Cell diameter must be 5–500 px."
    if (flowThreshold !in 0.0f..3.0f) return "Flow threshold must be 0.0–3.0."
    if (cellProbThreshold !in -6.0f..6.0f) return "Cell probability threshold must be -6.0 to 6.0."
    return null
}
