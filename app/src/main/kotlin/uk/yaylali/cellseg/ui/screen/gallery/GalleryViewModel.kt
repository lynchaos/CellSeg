package uk.yaylali.cellseg.ui.screen.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import mil.nga.tiff.FieldTagType
import mil.nga.tiff.FileDirectory
import mil.nga.tiff.TiffReader
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.yaylali.cellseg.data.filestore.FileStore
import uk.yaylali.cellseg.domain.model.ImagingChannel
import uk.yaylali.cellseg.domain.model.Magnification
import uk.yaylali.cellseg.domain.model.Sample
import uk.yaylali.cellseg.domain.repo.SegmentationRepository
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class GalleryUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val repository: SegmentationRepository,
    private val fileStore: FileStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    fun onImagePicked(
        context: Context,
        uri: Uri,
        onReady: (sampleId: String, imageUri: String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = GalleryUiState(isLoading = true)
            try {
                val sampleId = UUID.randomUUID().toString()
                // Decode via ImageDecoder (API 28+) or BitmapFactory, then transcode to JPEG.
                // This normalises all input formats (PNG, WebP, TIFF on API 33+, etc.) so that
                // BitmapFactory.decodeFile() in the segmentation backend always succeeds.
                val bitmap = decodeBitmap(context, uri)
                    ?: throw IllegalArgumentException(
                        "Unsupported image format. Please use JPEG, PNG, or WebP. " +
                        "TIFF requires Android 13 or later."
                    )
                val destFile = fileStore.originalFile(sampleId, UUID.randomUUID().toString(), "jpg")
                destFile.parentFile?.mkdirs()
                destFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    bitmap.recycle()
                }
                val sample = Sample(
                    id = sampleId,
                    sampleLabel = null,
                    timepoint = null,
                    passage = null,
                    wellId = null,
                    channel = ImagingChannel.BRIGHTFIELD,
                    magnification = Magnification.X20,
                    cellLineLabel = null,
                    createdAt = Instant.now(),
                )
                repository.insertSample(sample)
                withContext(Dispatchers.Main) {
                    _uiState.value = GalleryUiState(isLoading = false)
                    onReady(sampleId, destFile.absolutePath)
                }
            } catch (e: Exception) {
                _uiState.value = GalleryUiState(error = e.message ?: "Failed to load image")
            }
        }
    }

    /**
     * Decode a content URI to a software [Bitmap].
     *
     * Routing:
     *  1. [ImageDecoder] (API 28+) with ALLOCATOR_SOFTWARE — tried first for ALL types.
     *     On API 33+ this natively handles TIFF (including 16-bit scientific files).
     *  2. [decodeTiff] — fallback when ImageDecoder fails and the MIME type is TIFF.
     *     Uses mil.nga.tiff for API < 33 or unusual TIFF variants.
     *  3. [BitmapFactory] — last resort for API 26–27 non-TIFF formats.
     */
    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        val mimeType = context.contentResolver.getType(uri)
        val isTiff = mimeType == "image/tiff" || mimeType == "image/x-tiff"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    // ALLOCATOR_SOFTWARE keeps pixels CPU-accessible for JPEG compression.
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } catch (_: Exception) {
                // ImageDecoder failed (e.g. unsupported TIFF variant on API < 33).
                // Fall through to manual decoder below.
            }
        }

        // Manual TIFF decoder: handles 8-bit/16-bit TIFFs that ImageDecoder rejected
        // (typically on API 26–32).
        if (isTiff) {
            return decodeTiff(context, uri)
        }

        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }

    /**
     * Decode any TIFF (including 16-bit scientific / microscopy) to an ARGB_8888 [Bitmap].
     *
     * Uses [Rasters.getPixel] which handles both planar (separate sample planes) and
     * interleaved (chunky) TIFF storage. 16-bit values are linearly mapped to [0, 255].
     * Grayscale images are replicated to all three channels.
     *
     * Exceptions are NOT swallowed — they propagate to [onImagePicked]'s catch block
     * so the user sees the actual error rather than a generic "unsupported format" message.
     * The one exception: a [ClassCastException] from tiff-java's internal Long/Integer
     * type mismatch (some TIFF writers use LONG type for SHORT-spec tags) is converted
     * to a clear user-facing message.
     */
    private fun decodeTiff(context: Context, uri: Uri): Bitmap? {
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        val tiffImage = try {
            stream.use { TiffReader.readTiff(it) }
        } catch (e: ClassCastException) {
            throw IllegalArgumentException(
                "This TIFF file uses non-standard tag encoding that cannot be read on " +
                "Android ${Build.VERSION.RELEASE}. Please convert to JPEG or PNG first.", e
            )
        }
        val directory = tiffImage.fileDirectories?.firstOrNull() ?: return null
        // Coerce any Long-typed SHORT tags to Integer before readRasters() accesses them.
        // Some TIFF writers (ImageJ, microscopy software) store SHORT-spec tags with
        // FieldType.LONG; tiff-java's unchecked generic cast then fails with
        // ClassCastException: Long cannot be cast to Integer.
        coerceTiffTagTypes(directory)
        val rasters = try {
            directory.readRasters()
        } catch (e: ClassCastException) {
            throw IllegalArgumentException(
                "This TIFF file uses non-standard tag encoding that cannot be read on " +
                "Android ${Build.VERSION.RELEASE}. Please convert to JPEG or PNG first.", e
            )
        }
        val width = rasters.width
        val height = rasters.height
        val samplesPerPixel = rasters.samplesPerPixel
        // getBitsPerSample() on Rasters derives values from fieldTypes (always Integer).
        val bitsPerSample = rasters.bitsPerSample.firstOrNull() ?: 8
        // maxVal = 255 for 8-bit, 65535 for 16-bit, etc.
        val maxVal = ((1L shl bitsPerSample) - 1L).toFloat().coerceAtLeast(1f)

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // getPixel() handles both interleaved and planar layouts correctly.
                val pixel = rasters.getPixel(x, y)
                val r: Int
                val g: Int
                val b: Int
                if (samplesPerPixel >= 3 && pixel.size >= 3) {
                    r = ((pixel[0].toFloat() / maxVal) * 255f).toInt().coerceIn(0, 255)
                    g = ((pixel[1].toFloat() / maxVal) * 255f).toInt().coerceIn(0, 255)
                    b = ((pixel[2].toFloat() / maxVal) * 255f).toInt().coerceIn(0, 255)
                } else {
                    val v = ((pixel[0].toFloat() / maxVal) * 255f).toInt().coerceIn(0, 255)
                    r = v; g = v; b = v
                }
                pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Fix IFD entries where SHORT-spec tags were written as FieldType.LONG by the TIFF producer.
     * tiff-java stores the raw values as Java [Long] objects in that case; the internal
     * unchecked generic cast in [FileDirectory.getBitsPerSample] / [FileDirectory.getSamplesPerPixel]
     * then throws [ClassCastException] when the JVM tries to unbox [Long] as [Int].
     *
     * Reads each raw entry value via [FileDirectory.get], detects [Long] values, and
     * writes them back via the typed setter methods on [FileDirectory].
     */
    private fun coerceTiffTagTypes(directory: FileDirectory) {
        directory.get(FieldTagType.BitsPerSample)?.values?.let { v ->
            when (v) {
                is Long -> directory.setBitsPerSample(v.toInt())
                is List<*> -> if (v.isNotEmpty() && v[0] is Long) {
                    @Suppress("UNCHECKED_CAST")
                    directory.setBitsPerSample((v as List<Long>).map { it.toInt() })
                }
            }
        }
        directory.get(FieldTagType.SamplesPerPixel)?.values?.let { v ->
            if (v is Long) directory.setSamplesPerPixel(v.toInt())
        }
        directory.get(FieldTagType.Compression)?.values?.let { v ->
            if (v is Long) directory.setCompression(v.toInt())
        }
        directory.get(FieldTagType.PhotometricInterpretation)?.values?.let { v ->
            if (v is Long) directory.setPhotometricInterpretation(v.toInt())
        }
        directory.get(FieldTagType.PlanarConfiguration)?.values?.let { v ->
            if (v is Long) directory.setPlanarConfiguration(v.toInt())
        }
        directory.get(FieldTagType.ResolutionUnit)?.values?.let { v ->
            if (v is Long) directory.setResolutionUnit(v.toInt())
        }
    }
}
