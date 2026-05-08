package uk.yaylali.cellseg.ui.screen.run

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.yaylali.cellseg.domain.backend.SegmentationBackend
import uk.yaylali.cellseg.domain.backend.SegmentationError
import uk.yaylali.cellseg.domain.backend.SegmentationProgress
import uk.yaylali.cellseg.domain.model.BackendTier
import uk.yaylali.cellseg.domain.model.RunStatus
import uk.yaylali.cellseg.domain.repo.SegmentationRepository
import uk.yaylali.cellseg.util.MetricsCalculator
import java.io.File
import javax.inject.Inject
import javax.inject.Named

data class RunProgressUiState(
    val status: RunStatus = RunStatus.QUEUED,
    val progress: Float = 0f,
    val statusMessage: String = "Queued…",
    val error: String? = null,
    /** True while waiting to auto-retry after a SpaceCold error. */
    val isWakingUp: Boolean = false,
    /** Seconds remaining until the next auto-retry attempt. */
    val retryCountdown: Int = 0,
)

@HiltViewModel
class RunProgressViewModel @Inject constructor(
    private val repository: SegmentationRepository,
    private val metricsCalculator: MetricsCalculator,
    @Named("backend_local") private val localBackend: SegmentationBackend,
    @Named("backend_remote_public") private val remoteBackend: SegmentationBackend,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunProgressUiState())
    val uiState: StateFlow<RunProgressUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var segmentationJob: Job? = null
    private var currentRunId: String? = null

    companion object {
        private const val COLD_RETRY_DELAY_SECONDS = 30
    }

    fun startRun(runId: String) {
        if (currentRunId == runId && segmentationJob?.isActive == true) return
        currentRunId = runId
        segmentationJob = viewModelScope.launch {
            val run = repository.getRun(runId) ?: return@launch
            var backend: SegmentationBackend = when (run.backendTier) {
                BackendTier.LOCAL_CYTO3 -> localBackend
                else -> remoteBackend
            }

            var shouldRetry = true

            while (shouldRetry) {
                shouldRetry = false

                repository.updateRunStatus(runId, RunStatus.PREPROCESSING)
                _uiState.value = RunProgressUiState(
                    status = RunStatus.PREPROCESSING,
                    progress = 0.05f,
                    statusMessage = "Preprocessing…",
                )

                backend.segment(File(run.originalImagePath), run.params, runId).collect { progress ->
                    when (progress) {
                        is SegmentationProgress.StatusUpdate ->
                            _uiState.update { it.copy(statusMessage = progress.status) }

                        is SegmentationProgress.UploadProgress -> {
                            val fraction = if (progress.totalBytes > 0)
                                progress.bytesWritten.toFloat() / progress.totalBytes else 0f
                            _uiState.value = RunProgressUiState(
                                RunStatus.UPLOADING,
                                0.2f + 0.2f * fraction,
                                "Uploading… ${(fraction * 100).toInt()}%",
                            )
                        }

                        is SegmentationProgress.QueuePosition ->
                            _uiState.value = RunProgressUiState(
                                RunStatus.QUEUED, 0.4f,
                                "Waiting in queue (${progress.position})…",
                            )

                        is SegmentationProgress.Completed -> {
                            val result = progress.result
                            val runRecord = repository.getRun(runId) ?: return@collect
                            repository.completeRun(
                                runRecord.copy(
                                    outlineOverlayPath = result.outlineImagePath,
                                    maskTiffPath = result.maskTiffPath,
                                    flowsPath = result.flowsImagePath,
                                    status = RunStatus.COMPLETED,
                                    completedAt = java.time.Instant.now(),
                                )
                            )
                            metricsCalculator.compute(runId, result.maskLabelImage).let { metrics ->
                                repository.insertOrReplaceMetrics(metrics)
                            }
                            _uiState.value = RunProgressUiState(RunStatus.COMPLETED, 1f, "Complete!")
                        }

                        is SegmentationProgress.Failed -> {
                            if (progress.error is SegmentationError.QuotaExceeded) {
                                // Cloud GPU quota exceeded — transparently fall back to on-device.
                                backend = localBackend
                                repository.updateRunStatus(runId, RunStatus.PREPROCESSING)
                                _uiState.value = RunProgressUiState(
                                    status = RunStatus.PREPROCESSING,
                                    progress = 0.05f,
                                    statusMessage = "Cloud quota exceeded — running on-device instead…",
                                )
                                shouldRetry = true
                            } else if (progress.error is SegmentationError.SpaceCold) {
                                // No retry limit — keep trying until the Space wakes up
                                // or the user taps Give Up
                                _uiState.value = RunProgressUiState(
                                    status = RunStatus.QUEUED,
                                    progress = 0f,
                                    statusMessage = "Waking up HF Space…",
                                    isWakingUp = true,
                                    retryCountdown = COLD_RETRY_DELAY_SECONDS,
                                )
                                countdownJob = viewModelScope.launch {
                                    for (remaining in COLD_RETRY_DELAY_SECONDS downTo 1) {
                                        _uiState.update { it.copy(retryCountdown = remaining) }
                                        delay(1_000)
                                    }
                                }
                                countdownJob?.join()
                                _uiState.update { it.copy(isWakingUp = false, retryCountdown = 0) }
                                shouldRetry = true
                            } else {
                                repository.updateRunStatus(runId, RunStatus.FAILED, progress.error.message)
                                _uiState.value = RunProgressUiState(
                                    RunStatus.FAILED, 0f, "Failed", progress.error.message,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** Skip the waking-up countdown and retry immediately. */
    fun retryNow() {
        countdownJob?.cancel()
    }

    /** User chose to give up waiting — cancel the job and navigate back. */
    fun giveUp() {
        countdownJob?.cancel()
        segmentationJob?.cancel()
        val runId = currentRunId ?: return
        viewModelScope.launch {
            repository.updateRunStatus(runId, RunStatus.CANCELLED, "Cancelled by user")
        }
        _uiState.value = _uiState.value.copy(
            status = RunStatus.CANCELLED,
            isWakingUp = false,
            statusMessage = "Cancelled",
        )
    }
}
