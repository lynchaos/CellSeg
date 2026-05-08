package uk.yaylali.cellseg.data.remote.gradio

import kotlinx.coroutines.CancellationException
import mil.nga.tiff.TiffReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import uk.yaylali.cellseg.data.filestore.FileStore
import uk.yaylali.cellseg.data.local.TokenStore
import uk.yaylali.cellseg.data.datastore.SettingsRepository
import uk.yaylali.cellseg.domain.backend.SegmentationBackend
import uk.yaylali.cellseg.domain.backend.SegmentationError
import uk.yaylali.cellseg.domain.backend.SegmentationProgress
import uk.yaylali.cellseg.domain.backend.SegmentationResult
import uk.yaylali.cellseg.domain.model.BackendTier
import uk.yaylali.cellseg.domain.model.SegmentationParams
import uk.yaylali.cellseg.util.MaskDecoder
import java.io.File
import java.util.UUID
import javax.inject.Inject

class RemoteHfGradioBackend @Inject constructor(
    private val gradioClient: GradioClient,
    private val fileStore: FileStore,
    private val tokenStore: TokenStore,
    private val settingsRepository: SettingsRepository,
    private val tier: BackendTier,
    private val spaceSlug: String,
) : SegmentationBackend {

    override fun segment(
        imageFile: File,
        params: SegmentationParams,
        runId: String,
    ): Flow<SegmentationProgress> = flow {
        emit(SegmentationProgress.StatusUpdate("Connecting to ${spaceSlug}…"))

        val schema = try {
            gradioClient.discoverSchema(spaceSlug)
        } catch (e: SpaceSleepingException) {
            Timber.d("RemoteHfGradioBackend: space is sleeping — %s", e.message)
            emit(SegmentationProgress.Failed(SegmentationError.SpaceCold()))
            return@flow
        } catch (e: Exception) {
            Timber.w(e, "RemoteHfGradioBackend: schema discovery failed")
            emit(SegmentationProgress.Failed(SegmentationError.NetworkUnavailable("Failed to reach Space: ${e.message}")))
            return@flow
        }

        emit(SegmentationProgress.StatusUpdate("Uploading image…"))
        val fileRef = try {
            gradioClient.upload(spaceSlug, imageFile)
        } catch (e: Exception) {
            Timber.w(e, "RemoteHfGradioBackend: upload failed")
            emit(SegmentationProgress.Failed(SegmentationError.BackendError("Image upload failed: ${e.message}")))
            return@flow
        }
        emit(SegmentationProgress.UploadProgress(100L, 100L))

        val sessionHash = UUID.randomUUID().toString().replace("-", "").take(11)
        emit(SegmentationProgress.StatusUpdate("Queued…"))

        var completedStatusLine: String? = null
        var collectFailed = false

        try {
            gradioClient.runCellposeSegment(spaceSlug, fileRef, params, schema, sessionHash)
                .collect { progress ->
                    if (progress is SegmentationProgress.StatusUpdate &&
                        progress.status.startsWith("__process_completed__:")
                    ) {
                        completedStatusLine = progress.status
                    } else {
                        // Remap GPU quota errors to a typed QuotaExceeded so callers can
                        // fall back to on-device processing without string matching.
                        val remapped = if (progress is SegmentationProgress.Failed &&
                            progress.error is SegmentationError.BackendError &&
                            (progress.error.message.contains("GPU quota", ignoreCase = true) ||
                                progress.error.message.contains("ZeroGPU", ignoreCase = true))
                        ) {
                            SegmentationProgress.Failed(SegmentationError.QuotaExceeded(progress.error.message))
                        } else progress
                        emit(remapped)
                        if (remapped is SegmentationProgress.Failed) {
                            collectFailed = true
                        }
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "RemoteHfGradioBackend: SSE stream failed")
            emit(SegmentationProgress.Failed(SegmentationError.BackendError("Stream error: ${e.message}")))
            return@flow
        }

        // If the inner flow already emitted a failure (e.g. ZeroGPU quota exceeded,
        // unexpected_error, queue_full), propagate it and stop — do not overwrite with
        // the generic "No completion event received" sentinel below.
        if (collectFailed) return@flow

        // Decode the internal completion sentinel
        val line = completedStatusLine
            ?: run {
                emit(SegmentationProgress.Failed(SegmentationError.BackendError("No completion event received")))
                return@flow
            }

        val urlsCsv = line.removePrefix("__process_completed__:")
        val urls = urlsCsv.split("|")
        val spaceBaseUrl = uk.yaylali.cellseg.data.remote.gradio.SpaceUrlHelper.baseUrl(spaceSlug)

        emit(SegmentationProgress.StatusUpdate("Downloading results…"))

        val outlineFile = fileStore.outlineFile(runId)
        val flowsFile = fileStore.flowsFile(runId)
        val maskFile = fileStore.maskTiffFile(runId)

        fun buildUrl(rawEntry: String): String? {
            if (rawEntry.isBlank()) return null
            return if (rawEntry.startsWith("http")) rawEntry
            else "$spaceBaseUrl/gradio_api/file=$rawEntry"
        }

        val outlinesUrl = buildUrl(urls.getOrElse(0) { "" })
        val flowsUrl = buildUrl(urls.getOrElse(1) { "" })
        val maskUrl = buildUrl(urls.getOrElse(2) { "" })

        try {
            outlinesUrl?.let { gradioClient.downloadArtifact(it, outlineFile) }
            flowsUrl?.let { gradioClient.downloadArtifact(it, flowsFile) }
            maskUrl?.let { gradioClient.downloadArtifact(it, maskFile) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(SegmentationProgress.Failed(SegmentationError.BackendError("Failed to download results: ${e.message ?: e.javaClass.simpleName}")))
            return@flow
        }

        // Decode the mask TIFF into a label IntArray for metrics computation.
        // BitmapFactory cannot decode TIFF — use mil.nga:tiff to read 16-bit label rasters.
        val (maskLabels, maskW, maskH) = if (maskUrl != null && maskFile.exists() && maskFile.length() > 0) {
            try {
                withContext(Dispatchers.IO) {
                    val tiff = TiffReader.readTiff(maskFile)
                    val rasters = tiff.fileDirectory.readRasters()
                    val w = rasters.width
                    val h = rasters.height
                    Timber.d("RemoteHfGradioBackend: decoded TIFF mask %dx%d", w, h)
                    val labels = IntArray(w * h) { i ->
                        rasters.getPixelSample(0, i % w, i / w).toInt()
                    }
                    Triple(labels, w, h)
                }
            } catch (e: Exception) {
                Timber.w(e, "RemoteHfGradioBackend: TIFF decode failed")
                Triple(IntArray(0), 0, 0)
            }
        } else {
            Triple(IntArray(0), 0, 0)
        }

        emit(
            SegmentationProgress.Completed(
                SegmentationResult(
                    runId = runId,
                    maskLabelImage = maskLabels,
                    maskWidth = maskW,
                    maskHeight = maskH,
                    outlineImagePath = if (outlinesUrl != null) outlineFile.absolutePath else null,
                    flowsImagePath = if (flowsUrl != null) flowsFile.absolutePath else null,
                    maskTiffPath = if (maskUrl != null && maskFile.exists()) maskFile.absolutePath else null,
                    backendVersionTag = schema.apiName ?: "gradio",
                )
            )
        )
    }
}
