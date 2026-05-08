package uk.yaylali.cellseg.util

import android.graphics.Bitmap
import android.graphics.Color
import timber.log.Timber

/**
 * Decodes a 16-bit grayscale mask TIFF (from remote backend) or a label IntArray
 * (from local backend) into the canonical IntArray label image used throughout the app.
 *
 * For remote TIFF masks, each pixel value in the grayscale bitmap represents a cell label.
 */
object MaskDecoder {

    /**
     * Convert a Bitmap mask (grayscale, 16-bit packed in ARGB_8888 by the TIFF decoder)
     * to a flat IntArray label image.
     *
     * The remote Cellpose TIFF encodes 16-bit cell IDs; Android decodes each channel as 8-bit,
     * so we reconstruct by (R<<8 | G) from the decoded ARGB pixel.
     */
    fun fromBitmap(mask: Bitmap): IntArray {
        val w = mask.width
        val h = mask.height
        val pixels = IntArray(w * h)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)
        return IntArray(pixels.size) { i ->
            val p = pixels[i]
            val r = Color.red(p)
            val g = Color.green(p)
            // 16-bit reconstruction: high byte = R, low byte = G
            (r shl 8) or g
        }
    }

    /**
     * Count distinct non-zero labels in the label image.
     */
    fun cellCount(labels: IntArray): Int {
        var max = 0
        for (lbl in labels) {
            if (lbl > max) max = lbl
        }
        return max
    }

    /**
     * Compute per-cell pixel areas. Returns a map of label→areaPixels.
     * Zero label (background) is excluded.
     */
    fun cellAreas(labels: IntArray): Map<Int, Int> {
        val counts = mutableMapOf<Int, Int>()
        for (lbl in labels) {
            if (lbl == 0) continue
            counts[lbl] = (counts[lbl] ?: 0) + 1
        }
        return counts
    }

    /**
     * Compute foreground fraction (non-zero pixels / total pixels).
     */
    fun confluencePercent(labels: IntArray): Float {
        val fg = labels.count { it != 0 }
        return if (labels.isEmpty()) 0f else (fg.toFloat() / labels.size) * 100f
    }
}
