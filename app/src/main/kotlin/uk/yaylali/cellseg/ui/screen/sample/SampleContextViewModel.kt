package uk.yaylali.cellseg.ui.screen.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.yaylali.cellseg.domain.model.ImagingChannel
import uk.yaylali.cellseg.domain.model.Magnification
import uk.yaylali.cellseg.domain.repo.SegmentationRepository
import javax.inject.Inject

data class SampleContextUiState(
    val sampleId: String = "",
    val imageUri: String = "",
    val channel: ImagingChannel = ImagingChannel.BRIGHTFIELD,
    val magnification: Magnification = Magnification.X20,
    val canContinue: Boolean = false,
)

data class SampleContextResult(val sampleId: String, val imageUri: String)

@HiltViewModel
class SampleContextViewModel @Inject constructor(
    private val repository: SegmentationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SampleContextUiState())
    val uiState: StateFlow<SampleContextUiState> = _uiState.asStateFlow()

    fun load(sampleId: String, imageUri: String = "") {
        viewModelScope.launch {
            val sample = repository.getSample(sampleId) ?: return@launch
            _uiState.value = _uiState.value.copy(
                sampleId = sample.id,
                imageUri = imageUri,
                channel = sample.channel,
                magnification = sample.magnification,
                canContinue = true,
            )
        }
    }

    fun setChannel(ch: ImagingChannel) {
        _uiState.value = _uiState.value.copy(channel = ch)
    }

    fun setMagnification(mag: Magnification) {
        _uiState.value = _uiState.value.copy(magnification = mag)
    }

    fun save(onReady: (SampleContextResult) -> Unit) {
        viewModelScope.launch {
            val s = _uiState.value
            val sample = repository.getSample(s.sampleId) ?: return@launch
            repository.updateSample(
                sample.copy(
                    channel = s.channel,
                    magnification = s.magnification,
                )
            )
            onReady(SampleContextResult(s.sampleId, s.imageUri))
        }
    }
}
