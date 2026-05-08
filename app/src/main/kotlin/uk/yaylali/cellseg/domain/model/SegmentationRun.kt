package uk.yaylali.cellseg.domain.model

import java.time.Instant

/**
 * Immutable record of one segmentation attempt.
 * The record is written once on start and updated (status, paths, etc.) on completion.
 *
 * [backendVersionTag] examples:
 *   - Local:  "cyto3-onnx-fp16-v1.0.0"
 *   - Remote: "mouseland-cellpose@abc123"
 */
data class SegmentationRun(
    val id: String,
    val sampleId: String,
    val params: SegmentationParams,
    val backendTier: BackendTier,
    val backendSpaceSlug: String,         // empty for LOCAL_CYTO3
    val backendVersionTag: String,
    val originalImagePath: String,
    val outlineOverlayPath: String?,
    val flowsPath: String?,
    val maskTiffPath: String?,
    val status: RunStatus,
    val startedAt: Instant,
    val completedAt: Instant?,
    val error: String?,
)

enum class RunStatus {
    PENDING,
    UPLOADING,
    QUEUED,
    RUNNING,
    DECODING,
    PREPROCESSING,
    INFERENCE,
    POSTPROCESSING,
    COMPLETED,
    FAILED,
    FAILED_VALIDATION,
    CANCELLED;

    val isTerminal get() = this in setOf(COMPLETED, FAILED, FAILED_VALIDATION, CANCELLED)
    val isCloud get() = this in setOf(UPLOADING, QUEUED, RUNNING, DECODING)
    val isLocal get() = this in setOf(PREPROCESSING, INFERENCE, POSTPROCESSING)
}
