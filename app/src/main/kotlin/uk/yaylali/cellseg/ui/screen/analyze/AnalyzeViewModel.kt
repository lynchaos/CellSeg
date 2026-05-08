package uk.yaylali.cellseg.ui.screen.analyze

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.yaylali.cellseg.data.datastore.SettingsRepository
import uk.yaylali.cellseg.data.filestore.FileStore
import uk.yaylali.cellseg.data.ml.ModelDownloader
import uk.yaylali.cellseg.domain.backend.SegmentationBackend
import uk.yaylali.cellseg.domain.model.BackendTier
import uk.yaylali.cellseg.domain.model.ModelMetadata
import uk.yaylali.cellseg.domain.model.NamedPreset
import uk.yaylali.cellseg.domain.model.SegmentationParams
import uk.yaylali.cellseg.domain.model.SegmentationRun
import uk.yaylali.cellseg.domain.model.RunStatus
import uk.yaylali.cellseg.domain.repo.SegmentationRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

data class AnalyzeUiState(
    val sampleId: String = "",
    val imageUri: String = "",
    val params: SegmentationParams = SegmentationParams(),
    val selectedTier: BackendTier = BackendTier.LOCAL_CYTO3,
    val modelNotDownloaded: Boolean = false,
    val presets: List<NamedPreset> = emptyList(),
    val isStarting: Boolean = false,
    val startedRunId: String? = null,
    val error: String? = null,
)

@HiltViewModel
class AnalyzeViewModel @Inject constructor(
    private val segmentationRepository: SegmentationRepository,
    private val settingsRepository: SettingsRepository,
    private val fileStore: FileStore,
    private val modelDownloader: ModelDownloader,
    @Named("backend_local") private val localBackend: SegmentationBackend,
    @Named("backend_remote_public") private val remoteBackend: SegmentationBackend,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyzeUiState())
    val uiState: StateFlow<AnalyzeUiState> = _uiState.asStateFlow()

    fun load(sampleId: String, imageUri: String) {
        viewModelScope.launch {
            val tier = settingsRepository.defaultBackendTier.first().let {
                if (it == BackendTier.REMOTE_PRIVATE_HF) BackendTier.REMOTE_PUBLIC_HF else it
            }
            val params = settingsRepository.defaultParams.first()
            val presets = settingsRepository.presets.first()
            val modelNotDownloaded = !fileStore.modelFile(
                modelDownloader.modelFilename(ModelMetadata.CYTO3_DEFAULT)
            ).exists()
            _uiState.value = _uiState.value.copy(
                sampleId = sampleId,
                imageUri = imageUri,
                params = params,
                selectedTier = tier,
                presets = presets,
                modelNotDownloaded = modelNotDownloaded,
            )
        }
    }

    fun setDiameter(v: Double) {
        _uiState.value = _uiState.value.copy(params = _uiState.value.params.copy(diameter = v))
    }

    fun setFlowThreshold(v: Double) {
        _uiState.value = _uiState.value.copy(params = _uiState.value.params.copy(flowThreshold = v.toFloat()))
    }

    fun setCellProbThreshold(v: Double) {
        _uiState.value = _uiState.value.copy(params = _uiState.value.params.copy(cellProbThreshold = v.toFloat()))
    }

    fun setMaxResize(v: Int) {
        _uiState.value = _uiState.value.copy(params = _uiState.value.params.copy(maxResize = v))
    }

    fun resetParams() {
        viewModelScope.launch {
            val defaults = SegmentationParams.DEFAULT
            _uiState.value = _uiState.value.copy(params = defaults)
        }
    }

    fun applyPreset(preset: NamedPreset) {
        _uiState.value = _uiState.value.copy(params = preset.params)
    }

    fun setTier(tier: BackendTier) {
        _uiState.value = _uiState.value.copy(selectedTier = tier)
    }

    fun startRun() {
        val s = _uiState.value
        if (s.selectedTier == BackendTier.LOCAL_CYTO3 && s.modelNotDownloaded) {
            _uiState.value = s.copy(error = "Local model not downloaded. Download it in Settings → Local model.")
            return
        }
        viewModelScope.launch {
            _uiState.value = s.copy(isStarting = true, error = null)
            try {
                val runId = UUID.randomUUID().toString()
                val run = SegmentationRun(
                    id = runId,
                    sampleId = s.sampleId,
                    originalImagePath = s.imageUri,
                    params = s.params,
                    backendTier = s.selectedTier,
                    backendSpaceSlug = "",
                    backendVersionTag = "",
                    outlineOverlayPath = null,
                    flowsPath = null,
                    maskTiffPath = null,
                    status = RunStatus.QUEUED,
                    startedAt = Instant.now(),
                    completedAt = null,
                    error = null,
                )
                segmentationRepository.insertRun(run)
                _uiState.value = _uiState.value.copy(startedRunId = runId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isStarting = false, error = e.message ?: "Failed to start run")
            }
        }
    }
}
