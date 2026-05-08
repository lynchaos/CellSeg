package uk.yaylali.cellseg.ui.screen.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.yaylali.cellseg.data.datastore.SettingsRepository
import uk.yaylali.cellseg.data.local.TokenStore
import uk.yaylali.cellseg.data.ml.ModelDownloader
import uk.yaylali.cellseg.domain.model.BackendTier
import uk.yaylali.cellseg.domain.model.ModelMetadata
import javax.inject.Inject

data class OnboardingUiState(
    val downloadModel: Boolean = true,
    val hfToken: String = "",
    val selectedTier: BackendTier = BackendTier.LOCAL_CYTO3,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadError: String? = null,
    val isComplete: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val tokenStore: TokenStore,
    private val modelDownloader: ModelDownloader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun setDownloadModel(v: Boolean) {
        _uiState.value = _uiState.value.copy(downloadModel = v)
    }

    fun setHfToken(token: String) {
        _uiState.value = _uiState.value.copy(hfToken = token)
    }

    fun setTier(tier: BackendTier) {
        _uiState.value = _uiState.value.copy(selectedTier = tier)
    }

    fun complete() {
        viewModelScope.launch {
            val s = _uiState.value

            // Persist backend tier
            settingsRepository.setDefaultBackendTier(s.selectedTier)

            // Persist HF token if provided
            if (s.hfToken.isNotBlank()) {
                tokenStore.setToken(s.hfToken.trim())
            }

            settingsRepository.completeOnboarding()

            if (s.downloadModel) {
                _uiState.value = _uiState.value.copy(isDownloading = true, downloadError = null)
                try {
                    modelDownloader.ensureModel(ModelMetadata.CYTO3_DEFAULT) { progress ->
                        _uiState.value = _uiState.value.copy(downloadProgress = progress / 100f)
                    }
                } catch (e: Exception) {
                    // Non-blocking — user can download later in Model Management
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        downloadError = e.message ?: "Download failed",
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(isDownloading = false)
            }

            _uiState.value = _uiState.value.copy(isComplete = true)
        }
    }
}
