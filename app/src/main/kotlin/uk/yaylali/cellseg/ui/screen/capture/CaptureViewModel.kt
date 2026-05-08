package uk.yaylali.cellseg.ui.screen.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.yaylali.cellseg.data.filestore.FileStore
import uk.yaylali.cellseg.domain.model.ImagingChannel
import uk.yaylali.cellseg.domain.model.Magnification
import uk.yaylali.cellseg.domain.model.Sample
import uk.yaylali.cellseg.domain.repo.SegmentationRepository
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class CaptureUiState(
    val isCapturing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val repository: SegmentationRepository,
    private val fileStore: FileStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun onPhotoCaptured(
        photoFile: File,
        onReady: (sampleId: String, imageUri: String) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.value = CaptureUiState(isCapturing = true)
            try {
                val sampleId = UUID.randomUUID().toString()
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
                // Copy to private storage under originals/<sampleId>/
                val destFile = fileStore.originalFile(sampleId, UUID.randomUUID().toString(), "jpg")
                photoFile.copyTo(destFile, overwrite = true)
                _uiState.value = CaptureUiState(isCapturing = false)
                onReady(sampleId, destFile.absolutePath)
            } catch (e: Exception) {
                _uiState.value = CaptureUiState(error = e.message ?: "Capture failed")
            }
        }
    }
}
