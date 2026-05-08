package uk.yaylali.cellseg.data.ml

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import uk.yaylali.cellseg.data.filestore.FileStore
import uk.yaylali.cellseg.domain.model.ModelMetadata
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads, verifies (SHA-256), and manages the on-device ONNX model file.
 * The model is never bundled in the APK — it is downloaded from HF Hub on demand.
 */
@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileStore: FileStore,
    @Named("basic_client") private val okHttpClient: OkHttpClient,
) {
    /**
     * Ensures the ONNX model file for [metadata] is present and valid.
     * Downloads if absent or corrupt.
     * Reports progress [0..100] via [onProgress].
     */
    suspend fun ensureModel(
        metadata: ModelMetadata,
        onProgress: (Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val dest = fileStore.modelFile(modelFilename(metadata))
        if (dest.exists() && verifySha256(dest, metadata.sha256)) {
            Timber.d("ModelDownloader: model already present and valid")
            return@withContext dest
        }
        if (dest.exists()) {
            Timber.w("ModelDownloader: model exists but SHA-256 mismatch — re-downloading")
            dest.delete()
        }
        download(metadata.sourceUrl, dest, onProgress)
        if (!verifySha256(dest, metadata.sha256)) {
            dest.delete()
            error("Model download integrity check failed for ${metadata.modelId}")
        }
        dest
    }

    fun modelFilename(metadata: ModelMetadata) =
        "${metadata.modelId}-v${metadata.version}.onnx"

    fun isModelPresent(metadata: ModelMetadata): Boolean =
        fileStore.modelFile(modelFilename(metadata)).let {
            it.exists() && verifySha256(it, metadata.sha256)
        }

    fun deleteModel(metadata: ModelMetadata) {
        fileStore.modelFile(modelFilename(metadata)).delete()
    }

    private suspend fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        Timber.d("ModelDownloader: downloading model")
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
            val body = response.body ?: error("Empty response body")
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: -1L
            var downloaded = 0L
            val tmp = File("${dest.absolutePath}.part")
            try {
                tmp.outputStream().buffered().use { out ->
                    body.byteStream().use { src ->
                        val buf = ByteArray(65_536)
                        var read: Int
                        while (src.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                onProgress(((downloaded * 100) / totalBytes).toInt().coerceIn(0, 99))
                            }
                        }
                    }
                }
                // File.renameTo() can silently return false on Android when src and dst are on
                // different filesystems (e.g. tmpfs → /data/data). Fall back to copy+delete so
                // the model file always ends up at dest even in that case.
                if (!tmp.renameTo(dest)) {
                    Timber.w("ModelDownloader: renameTo failed, falling back to copy")
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
                onProgress(100)
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
        }
    }

    private fun verifySha256(file: File, expectedHex: String): Boolean {
        if (expectedHex.isBlank()) return true   // not pinned yet (dev mode)
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { ins ->
                val buf = ByteArray(65_536)
                var read: Int
                while (ins.read(buf).also { read = it } != -1) {
                    digest.update(buf, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            actual.equals(expectedHex, ignoreCase = true)
        } catch (e: Exception) {
            Timber.w(e, "ModelDownloader: SHA-256 verification error")
            false
        }
    }
}
