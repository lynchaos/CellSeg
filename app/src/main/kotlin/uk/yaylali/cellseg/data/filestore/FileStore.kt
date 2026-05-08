package uk.yaylali.cellseg.data.filestore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages well-known file paths under [Context.getFilesDir].
 *
 * Directory layout (spec §10.3):
 * ```
 * <filesDir>/
 *   originals/<sampleId>/<runId>.<ext>
 *   outlines/<runId>.png
 *   flows/<runId>.png
 *   masks/<runId>.tiff
 *   models/
 *   logs/
 * ```
 */
@Singleton
class FileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val base: File get() = context.filesDir

    val modelsDir: File get() = base.resolve("models").also { it.mkdirs() }
    val logsDir: File get() = base.resolve("logs").also { it.mkdirs() }
    private val outlinesDir: File get() = base.resolve("outlines").also { it.mkdirs() }
    private val flowsDir: File get() = base.resolve("flows").also { it.mkdirs() }
    private val masksDir: File get() = base.resolve("masks").also { it.mkdirs() }

    fun originalsDir(sampleId: String): File =
        base.resolve("originals/$sampleId").also { it.mkdirs() }

    /** Copies or moves the captured/picked image into the app's private originals dir. */
    fun originalFile(sampleId: String, runId: String, extension: String = "jpg"): File =
        originalsDir(sampleId).resolve("$runId.$extension")

    fun outlineFile(runId: String): File = outlinesDir.resolve("$runId.png")
    fun flowsFile(runId: String): File = flowsDir.resolve("$runId.png")
    fun maskTiffFile(runId: String): File = masksDir.resolve("$runId.tiff")

    /** Local ONNX model file. */
    fun modelFile(filename: String): File = modelsDir.resolve(filename)

    /** Temporary scratch file for upload / processing. */
    fun tempFile(suffix: String = ".tmp"): File =
        File.createTempFile("cellseg_", suffix, context.cacheDir)

    fun deleteRunFiles(runId: String) {
        outlineFile(runId).delete()
        flowsFile(runId).delete()
        maskTiffFile(runId).delete()
    }

    /** Export file (CSV, JSON) under <filesDir>/exports/ for sharing via FileProvider. */
    fun exportFile(runId: String, extension: String): File =
        base.resolve("exports").also { it.mkdirs() }.resolve("${runId}.$extension")
}
