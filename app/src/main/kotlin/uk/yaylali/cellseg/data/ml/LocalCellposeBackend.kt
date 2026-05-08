package uk.yaylali.cellseg.data.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import uk.yaylali.cellseg.data.filestore.FileStore
import uk.yaylali.cellseg.domain.backend.SegmentationBackend
import uk.yaylali.cellseg.domain.backend.SegmentationError
import uk.yaylali.cellseg.domain.backend.SegmentationProgress
import uk.yaylali.cellseg.domain.backend.SegmentationResult
import uk.yaylali.cellseg.domain.model.ModelMetadata
import uk.yaylali.cellseg.domain.model.SegmentationParams
import java.io.File
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * On-device Cellpose cyto3 ONNX backend.
 *
 * Tile strategy for images >2048px: ≤1024×1024 patches, ~64px overlap, then stitch.
 */
class LocalCellposeBackend @Inject constructor(
    private val modelDownloader: ModelDownloader,
    private val onnx: OnnxRuntimeWrapper,
    private val fileStore: FileStore,
) : SegmentationBackend {

    private companion object {
        /** Tile side length in pixels. Each ONNX call processes at most this many pixels per side.
         *  256×256 → ~60 MB ONNX activations (safe for emulator/low-RAM devices). */
        const val TILE_SIZE = 256
        /** Cellpose cyto3 was trained with cells ~30 px in diameter. Rescale input to match. */
        const val REFERENCE_DIAMETER = 30.0
    }

    override fun segment(
        imageFile: File,
        params: SegmentationParams,
        runId: String,
    ): Flow<SegmentationProgress> = flow {

        emit(SegmentationProgress.StatusUpdate("Preparing local model…"))

        // 1. Ensure model is present
        val metadata = ModelMetadata.CYTO3_DEFAULT
        if (!modelDownloader.isModelPresent(metadata)) {
            emit(SegmentationProgress.StatusUpdate("Downloading Cellpose model (~13 MB)…"))
            try {
                modelDownloader.ensureModel(metadata) { pct ->
                    // emit download progress through a fire-and-forget style; not blocking
                }
            } catch (e: Exception) {
                emit(SegmentationProgress.Failed(SegmentationError.BackendError("Model download failed: ${e.message}")))
                return@flow
            }
            // New model on disk — invalidate any stale in-memory session so it is reloaded below.
            onnx.unload()
        }

        if (!onnx.isLoaded()) {
            emit(SegmentationProgress.StatusUpdate("Loading model into memory…"))
            val modelFile = fileStore.modelFile(modelDownloader.modelFilename(metadata))
            try {
                onnx.loadModel(modelFile)
            } catch (e: Exception) {
                emit(SegmentationProgress.Failed(SegmentationError.OnnxInitFailed("Failed to load ONNX model: ${e.message}")))
                return@flow
            }
        }

        emit(SegmentationProgress.StatusUpdate("Preprocessing image…"))

        val bitmap = loadBitmap(imageFile)
            ?: run {
                emit(SegmentationProgress.Failed(SegmentationError.UnsupportedFormat("Cannot decode image: ${imageFile.name}")))
                return@flow
            }

        val labels: IntArray
        val outputWidth: Int
        val outputHeight: Int

        try {
            emit(SegmentationProgress.StatusUpdate("Running segmentation…"))
            val result = runTiled(bitmap, params)
            // Full-res bitmap is no longer needed after tiling; each tile was recycled inside runTiled.
            bitmap.recycle()
            labels = result.first
            outputWidth = result.second
            outputHeight = result.third
        } catch (e: Throwable) {
            emit(SegmentationProgress.Failed(SegmentationError.BackendError("Inference failed: ${e.message}")))
            return@flow
        }

        emit(SegmentationProgress.StatusUpdate("Rendering outlines…"))

        // Reload bitmap at full resolution for outline rendering (tiled path recycled tiles,
        // not the original file). loadBitmap is cheap here — it only reads once for rendering.
        val renderBitmap = loadBitmap(imageFile)
            ?: run {
                emit(SegmentationProgress.Failed(SegmentationError.BackendError("Cannot reload image for rendering")))
                return@flow
            }

        val outlineFile = fileStore.outlineFile(runId)
        try {
            renderOutlines(labels, outputWidth, outputHeight, renderBitmap, outlineFile)
        } catch (e: Throwable) {
            emit(SegmentationProgress.Failed(SegmentationError.BackendError("Rendering failed: ${e.message}")))
            return@flow
        }

        emit(
            SegmentationProgress.Completed(
                SegmentationResult(
                    runId = runId,
                    maskLabelImage = labels,
                    maskWidth = outputWidth,
                    maskHeight = outputHeight,
                    outlineImagePath = outlineFile.absolutePath,
                    flowsImagePath = null,     // flows overlay not rendered for local backend
                    maskTiffPath = null,       // local backend produces IntArray directly
                    backendVersionTag = "local-${ModelMetadata.CYTO3_DEFAULT.version}",
                )
            )
        )
    }.flowOn(Dispatchers.Default)

    /**
     * Rescales the bitmap by [params.diameter] → REFERENCE_DIAMETER, runs tiled inference,
     * then rescales the label map back to the original image dimensions.
     */
    private fun runTiled(
        bitmap: Bitmap,
        params: SegmentationParams,
    ): Triple<IntArray, Int, Int> {
        val origW = bitmap.width
        val origH = bitmap.height

        // Scale so cells appear ~30 px wide (model training scale).
        val rescale = (REFERENCE_DIAMETER / params.diameter.coerceAtLeast(1.0)).toFloat()
        val workBitmap: Bitmap = if (kotlin.math.abs(rescale - 1f) > 0.02f) {
            val nw = (origW * rescale).toInt().coerceAtLeast(1)
            val nh = (origH * rescale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, nw, nh, true)
        } else {
            bitmap
        }

        val scaledLabels = tileInference(workBitmap, params)
        val scaledW = workBitmap.width
        val scaledH = workBitmap.height
        if (workBitmap !== bitmap) workBitmap.recycle()

        val labels = if (scaledW != origW || scaledH != origH) {
            resizeLabels(scaledLabels, scaledW, scaledH, origW, origH)
        } else {
            scaledLabels
        }
        return Triple(labels, origW, origH)
    }

    /** Tiled inference on [bitmap] without any diameter rescaling. Returns labels at bitmap dimensions. */
    private fun tileInference(bitmap: Bitmap, params: SegmentationParams): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val labels = IntArray(w * h) { 0 }
        var maxLabel = 0

        var y = 0
        while (y < h) {
            val y1 = minOf(y + TILE_SIZE, h)

            var x = 0
            while (x < w) {
                val x1 = minOf(x + TILE_SIZE, w)

                val tile = Bitmap.createBitmap(bitmap, x, y, x1 - x, y1 - y)
                // Int.MAX_VALUE: never downscale tiles — they are already ≤ TILE_SIZE pixels.
                val prep = CellposePreprocessor.prepare(tile, Int.MAX_VALUE)
                // scaledBitmap === tile when no resize occurred, so only recycle once.
                if (prep.scaledBitmap !== tile) prep.scaledBitmap.recycle()
                tile.recycle()
                val rawOut = onnx.run(prep.tensor, prep.paddedHeight, prep.paddedWidth)
                val out = cropOutput(rawOut, prep.paddedWidth, prep.paddedHeight, prep.width, prep.height)
                val tileLbls = FlowFollowingPostprocessor.postprocess(
                    output = out,
                    width = prep.width,
                    height = prep.height,
                    maxIter = params.maxIter,
                    flowThreshold = params.flowThreshold,
                    cellprobThreshold = params.cellProbThreshold,
                )

                // Stitch tile labels into full image (no overlap, so tile coords = image coords).
                for (row in 0 until (y1 - y)) {
                    for (col in 0 until (x1 - x)) {
                        val lbl = tileLbls[row * prep.width + col]
                        if (lbl > 0) {
                            labels[(y + row) * w + (x + col)] = lbl + maxLabel
                        }
                    }
                }
                val tileMax = tileLbls.maxOrNull() ?: 0
                if (tileMax > 0) maxLabel += tileMax

                x = x1
            }
            y = y1
        }
        return labels
    }

    /** Nearest-neighbour resize of a label (integer) map — preserves cell IDs without interpolation artefacts. */
    private fun resizeLabels(src: IntArray, srcW: Int, srcH: Int, dstW: Int, dstH: Int): IntArray {
        val dst = IntArray(dstW * dstH)
        for (y in 0 until dstH) {
            val srcY = (y * srcH / dstH).coerceIn(0, srcH - 1)
            for (x in 0 until dstW) {
                val srcX = (x * srcW / dstW).coerceIn(0, srcW - 1)
                dst[y * dstW + x] = src[srcY * srcW + srcX]
            }
        }
        return dst
    }

    private fun loadBitmap(file: File): Bitmap? =
        android.graphics.BitmapFactory.decodeFile(file.absolutePath)

    /**
     * Crop a padded model output (1, 3, paddedH, paddedW) down to (3, H, W).
     *
     * The model was fed a tensor padded with zeros on the bottom/right, so the valid
     * output pixels are in the top-left [height × width] region of each channel plane.
     */
    private fun cropOutput(
        output: FloatArray,
        paddedWidth: Int,
        paddedHeight: Int,
        width: Int,
        height: Int,
    ): FloatArray {
        if (paddedWidth == width && paddedHeight == height) return output
        val cropped = FloatArray(3 * height * width)
        for (c in 0 until 3) {
            for (y in 0 until height) {
                System.arraycopy(
                    output, c * paddedHeight * paddedWidth + y * paddedWidth,
                    cropped, c * height * width + y * width,
                    width
                )
            }
        }
        return cropped
    }

    private fun renderOutlines(
        labels: IntArray,
        width: Int,
        height: Int,
        original: Bitmap,
        outFile: File,
    ) {
        val scaled = if (original.width != width || original.height != height) {
            Bitmap.createScaledBitmap(original, width, height, true)
        } else original

        val out = scaled.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint().apply {
            color = Color.argb(220, 255, 107, 53)  // coral outline
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }

        for (row in 1 until height - 1) {
            for (col in 1 until width - 1) {
                val lbl = labels[row * width + col]
                if (lbl == 0) continue
                val isEdge = labels[(row - 1) * width + col] != lbl ||
                        labels[(row + 1) * width + col] != lbl ||
                        labels[row * width + (col - 1)] != lbl ||
                        labels[row * width + (col + 1)] != lbl
                if (isEdge) canvas.drawPoint(col.toFloat(), row.toFloat(), paint)
            }
        }

        outFile.outputStream().use { fos ->
            out.compress(Bitmap.CompressFormat.PNG, 95, fos)
        }
    }
}
