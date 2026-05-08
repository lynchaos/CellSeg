package uk.yaylali.cellseg.domain.backend

import kotlinx.coroutines.flow.Flow
import uk.yaylali.cellseg.domain.model.SegmentationParams
import java.io.File

/**
 * Pluggable segmentation backend interface.
 *
 * Both [LocalCellposeBackend] (cyto3 ONNX, on-device) and
 * [RemoteHfGradioBackend] (Cellpose-SAM via HF Spaces) implement this.
 *
 * Emits [SegmentationProgress] events until a terminal state is reached.
 */
interface SegmentationBackend {

    /**
     * Run segmentation on [imageFile] with [params].
     * Emits incremental [SegmentationProgress] updates.
     * The flow completes normally on success or throws on non-recoverable error.
     * Caller is responsible for cancelling the coroutine scope to abort mid-run.
     */
    fun segment(
        imageFile: File,
        params: SegmentationParams,
        runId: String,
    ): Flow<SegmentationProgress>
}

/**
 * Progress events emitted by a backend during a run.
 */
sealed interface SegmentationProgress {
    data class StatusUpdate(val status: String) : SegmentationProgress
    data class QueuePosition(val position: Int, val etaSeconds: Int?) : SegmentationProgress
    data class UploadProgress(val bytesWritten: Long, val totalBytes: Long) : SegmentationProgress
    data class Completed(val result: SegmentationResult) : SegmentationProgress
    data class Failed(val error: SegmentationError) : SegmentationProgress
}

/**
 * The artefacts produced when segmentation succeeds.
 * [maskLabelImage] is the integer label image (row-major, 0=background).
 */
data class SegmentationResult(
    val runId: String,
    val maskLabelImage: IntArray,
    val maskWidth: Int,
    val maskHeight: Int,
    val outlineImagePath: String?,
    val flowsImagePath: String?,
    val maskTiffPath: String?,
    val backendVersionTag: String,
) {
    override fun equals(other: Any?) = other is SegmentationResult && runId == other.runId
    override fun hashCode() = runId.hashCode()
}

/** Typed segmentation errors surfaced to the UI. */
sealed class SegmentationError(override val message: String) : Exception(message) {
    data class NetworkUnavailable(override val message: String = "No network connection.") : SegmentationError(message)
    data class RateLimited(override val message: String = "Rate limited. Try again later.") : SegmentationError(message)
    data class SpaceCold(override val message: String = "Space is waking up. Retry shortly.") : SegmentationError(message)
    data class ImageTooLarge(override val message: String) : SegmentationError(message)
    data class UnsupportedFormat(override val message: String) : SegmentationError(message)
    data class BackendError(override val message: String) : SegmentationError(message)
    data class Cancelled(override val message: String = "Run cancelled.") : SegmentationError(message)
    data class ModelNotDownloaded(override val message: String = "Local model not downloaded.") : SegmentationError(message)
    data class ModelCorrupted(override val message: String = "Local model file is corrupted. Re-download.") : SegmentationError(message)
    data class OnnxInitFailed(override val message: String = "ONNX Runtime could not initialise on this device.") : SegmentationError(message)
    data class InferenceOutOfMemory(override val message: String = "Image too large for on-device inference.") : SegmentationError(message)
    data class ValidationFailed(override val message: String) : SegmentationError(message)
    data class QuotaExceeded(override val message: String = "ZeroGPU quota exceeded. Falling back to on-device processing.") : SegmentationError(message)
}
