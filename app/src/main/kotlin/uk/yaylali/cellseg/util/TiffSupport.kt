package uk.yaylali.cellseg.util

import android.graphics.Bitmap
import mil.nga.tiff.TiffReader
import timber.log.Timber
import java.io.File

/**
 * TIFF decoding using mil.nga:tiff (already a project dependency).
 */
object TiffSupport {

    /**
     * Decode a TIFF file to a flat IntArray of label values (one int per pixel, row-major).
     * Returns null if the file cannot be decoded.
     */
    fun decodeLabels(file: File): Triple<IntArray, Int, Int>? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val tiff = TiffReader.readTiff(file)
            val rasters = tiff.fileDirectory.readRasters()
            val w = rasters.width
            val h = rasters.height
            val labels = IntArray(w * h) { i ->
                rasters.getPixelSample(0, i % w, i / w).toInt()
            }
            Triple(labels, w, h)
        } catch (e: Exception) {
            Timber.w(e, "TiffSupport: decode failed for %s", file.name)
            null
        }
    }

    /** Decode a TIFF file to a [Bitmap], or null if decoding fails. */
    fun decode(file: File): Bitmap? = null // Bitmap path not required; use decodeLabels()
}
