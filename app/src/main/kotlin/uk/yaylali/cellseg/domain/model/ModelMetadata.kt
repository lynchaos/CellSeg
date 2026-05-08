package uk.yaylali.cellseg.domain.model

import java.time.Instant

data class ModelMetadata(
    val modelId: String,        // "cyto3-onnx-fp16"
    val version: String,        // "1.0.0"
    val sizeBytes: Long,
    val sha256: String,
    val downloadedAt: Instant,
    val sourceUrl: String,
    val localPath: String,
) {
    companion object {
        /**
         * Default metadata for the cyto3 FP16 ONNX model.
         * SHA-256 and version are populated by convert_cyto3_to_onnx.py at conversion time.
         * The sha256 sentinel "" means "not yet verified" and triggers re-download.
         */
        val CYTO3_DEFAULT = ModelMetadata(
            modelId = "cyto3-onnx-fp16",
            version = "1.0.0",
            sizeBytes = 26_489_790L,          // ~25.3 MB, FP32 weights
            sha256 = "5339f1681a016a84dc39ea7d477779fb23dcc2140bb23620f1974428bc033b41",
            downloadedAt = Instant.EPOCH,
            sourceUrl = "https://huggingface.co/kmlyyll/cellpose-cyto3-onnx/resolve/main/cyto3-fp16.onnx",
            localPath = "",
        )
    }
}

/** Calibration for a specific magnification objective. */
data class CalibrationEntry(
    val magnificationLabel: String,  // e.g. "10×"
    val pxPerMicron: Float,
    val fovWidthMm: Float,
    val fovHeightMm: Float,
)
