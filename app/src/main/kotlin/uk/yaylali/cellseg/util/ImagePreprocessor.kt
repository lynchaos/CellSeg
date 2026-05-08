package uk.yaylali.cellseg.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Shared image-loading utilities.
 * Handles JPEG/PNG via [BitmapFactory] and TIFF via the TiffBitmapFactory wrapper.
 * Always corrects EXIF orientation for JPEG inputs.
 */
object ImagePreprocessor {

    /** Load a Bitmap from any supported format; returns null if unsupported. */
    fun load(file: File): Bitmap? {
        return when (file.extension.lowercase()) {
            "tiff", "tif" -> TiffSupport.decode(file)
            else -> loadAndCorrectOrientation(file)
        }
    }

    private fun loadAndCorrectOrientation(file: File): Bitmap? {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                else -> return bmp
            }
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        } catch (e: Exception) {
            bmp
        }
    }
}
