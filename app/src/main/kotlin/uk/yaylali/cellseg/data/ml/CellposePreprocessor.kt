package uk.yaylali.cellseg.data.ml

import android.graphics.Bitmap

/**
 * Preprocesses a single-channel grayscale [Bitmap] into the
 * (1, 2, H, W) FP32 input tensor expected by the Cellpose cyto3 ONNX model.
 *
 * Normalisation: percentile stretch so p1→0 and p99→1, then clamp to [0, 1].
 * Channel 0 = normalised image; Channel 1 = zeros (no second channel for mono input).
 */
object CellposePreprocessor {

    /**
     * @param bitmap  Source image (any config); will be read as grayscale luma.
     * @param maxResize  If image larger than this (in either dimension), scale down proportionally.
     * @return [PreparedInput] containing the float tensor and the (possibly scaled) dimensions.
     *
     * The Cellpose cyto3 U-Net has 5 encoder stages (stride 2 each), so both spatial
     * dimensions must be multiples of 32 = 2⁵. The tensor is padded with zeros on the
     * bottom and right edges to satisfy this. [PreparedInput.paddedWidth] /
     * [PreparedInput.paddedHeight] are what gets fed to the model; [PreparedInput.width] /
     * [PreparedInput.height] are the true image dimensions to crop back to after inference.
     */
    fun prepare(bitmap: Bitmap, maxResize: Int): PreparedInput {
        val (width, height, scaled) = maybeResize(bitmap, maxResize)
        val paddedWidth = padTo32(width)
        val paddedHeight = padTo32(height)
        val gray = toGray(scaled)
        val (p1, p99) = percentiles(gray)
        val range = (p99 - p1).coerceAtLeast(1e-6f)
        // Input shape (1, 2, paddedH, paddedW): ch0 = normalized image, ch1 = zeros.
        // Image pixels placed in the top-left corner; padding area stays zero.
        val tensor = FloatArray(2 * paddedHeight * paddedWidth)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val norm = ((gray[y * width + x] - p1) / range).coerceIn(0f, 1f)
                tensor[y * paddedWidth + x] = norm
            }
        }
        // channel 1 already zero-initialised by FloatArray constructor
        return PreparedInput(tensor, width, height, paddedWidth, paddedHeight, scaled)
    }

    data class PreparedInput(
        val tensor: FloatArray,
        val width: Int,
        val height: Int,
        /** Width rounded up to the next multiple of 32 — actual model input width. */
        val paddedWidth: Int,
        /** Height rounded up to the next multiple of 32 — actual model input height. */
        val paddedHeight: Int,
        /**
         * The (possibly downscaled) bitmap at exactly [width]×[height] pixels.
         * Returned so the caller can use it for rendering overlays at model-output
         * dimensions without keeping the full-resolution original bitmap alive.
         * May be the same object as the original bitmap if no resizing was performed.
         */
        val scaledBitmap: Bitmap,
    )

    /** Round [size] up to the nearest multiple of 32. */
    private fun padTo32(size: Int): Int = ((size + 31) / 32) * 32

    private fun maybeResize(bmp: Bitmap, maxResize: Int): Triple<Int, Int, Bitmap> {
        val w = bmp.width
        val h = bmp.height
        if (w <= maxResize && h <= maxResize) return Triple(w, h, bmp)
        val scale = maxResize.toFloat() / maxOf(w, h)
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Triple(nw, nh, Bitmap.createScaledBitmap(bmp, nw, nh, true))
    }

    private fun toGray(bmp: Bitmap): FloatArray {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        return FloatArray(w * h) { i ->
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // BT.601 luma
            (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        }
    }

    private fun percentiles(data: FloatArray): Pair<Float, Float> {
        val sorted = data.copyOf()
        sorted.sort()
        val p1idx = (0.01f * (sorted.size - 1)).toInt()
        val p99idx = (0.99f * (sorted.size - 1)).toInt()
        return sorted[p1idx] to sorted[p99idx]
    }
}
