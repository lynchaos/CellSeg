package uk.yaylali.cellseg.ui.screen.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.yaylali.cellseg.data.datastore.SettingsRepository
import uk.yaylali.cellseg.data.filestore.FileStore
import uk.yaylali.cellseg.data.ml.ModelDownloader
import uk.yaylali.cellseg.data.ml.OnnxRuntimeWrapper
import uk.yaylali.cellseg.domain.model.ModelMetadata
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ModelManagementUiState(
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val sha256: String = "",
    val lastVerified: String = "",
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ModelManagementViewModel @Inject constructor(
    private val modelDownloader: ModelDownloader,
    private val fileStore: FileStore,
    private val settingsRepository: SettingsRepository,
    private val onnxRuntime: OnnxRuntimeWrapper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelManagementUiState())
    val uiState: StateFlow<ModelManagementUiState> = _uiState.asStateFlow()

    private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val modelFile = fileStore.modelFile(modelDownloader.modelFilename(ModelMetadata.CYTO3_DEFAULT))
            val isDownloaded = modelFile.exists()
            var sha256 = ""
            var lastVerified = ""
            if (isDownloaded) {
                sha256 = computeSha256(modelFile)
                val meta = settingsRepository.cyto3ModelMetadata.first()
                if (meta != null && meta.downloadedAt != Instant.EPOCH) {
                    lastVerified = dtf.format(meta.downloadedAt)
                }
            }
            _uiState.value = ModelManagementUiState(
                isDownloaded = isDownloaded,
                sha256 = sha256,
                lastVerified = lastVerified,
            )
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDownloading = true, error = null)
            try {
                modelDownloader.ensureModel(ModelMetadata.CYTO3_DEFAULT) { progress ->
                    _uiState.value = _uiState.value.copy(downloadProgress = progress / 100f)
                }
                val modelFile = fileStore.modelFile(modelDownloader.modelFilename(ModelMetadata.CYTO3_DEFAULT))
                val sha256 = computeSha256(modelFile)
                val now = Instant.now()
                settingsRepository.setCyto3ModelMetadata(
                    ModelMetadata.CYTO3_DEFAULT.copy(downloadedAt = now)
                )
                _uiState.value = ModelManagementUiState(
                    isDownloaded = true,
                    sha256 = sha256,
                    lastVerified = dtf.format(now),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    error = e.message ?: "Download failed",
                )
            }
        }
    }

    fun reDownloadModel() {
        modelDownloader.deleteModel(ModelMetadata.CYTO3_DEFAULT)
        _uiState.value = _uiState.value.copy(isDownloaded = false, sha256 = "", lastVerified = "")
        downloadModel()
    }

    fun deleteModel() {
        modelDownloader.deleteModel(ModelMetadata.CYTO3_DEFAULT)
        _uiState.value = ModelManagementUiState(isDownloaded = false)
    }

    fun testInference() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.value = _uiState.value.copy(isTesting = true, testResult = null, error = null)
            try {
                val modelFile = fileStore.modelFile(modelDownloader.modelFilename(ModelMetadata.CYTO3_DEFAULT))
                onnxRuntime.loadModel(modelFile)
                val size = 256
                val dummyInput = FloatArray(2 * size * size) { 0.5f }
                val startMs = System.currentTimeMillis()
                val output = onnxRuntime.run(dummyInput, size, size)
                val elapsedMs = System.currentTimeMillis() - startMs
                val isValid = output.isNotEmpty()
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    testResult = if (isValid) "OK — ${elapsedMs}ms for ${size}×${size}" else "Unexpected output shape",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    error = "Test failed: ${e.message}",
                )
            }
        }
    }

    private fun computeSha256(file: java.io.File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(65536).use { stream ->
            val buffer = ByteArray(65536)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
