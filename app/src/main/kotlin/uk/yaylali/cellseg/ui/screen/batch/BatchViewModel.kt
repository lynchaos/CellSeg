package uk.yaylali.cellseg.ui.screen.batch

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.yaylali.cellseg.data.datastore.SettingsRepository
import uk.yaylali.cellseg.data.filestore.FileStore
import uk.yaylali.cellseg.data.ml.ModelDownloader
import uk.yaylali.cellseg.domain.backend.SegmentationBackend
import uk.yaylali.cellseg.domain.backend.SegmentationProgress
import uk.yaylali.cellseg.domain.model.BackendTier
import uk.yaylali.cellseg.domain.model.CellMetrics
import uk.yaylali.cellseg.domain.model.ModelMetadata
import uk.yaylali.cellseg.domain.model.RunStatus
import uk.yaylali.cellseg.domain.model.Sample
import uk.yaylali.cellseg.domain.model.SegmentationParams
import uk.yaylali.cellseg.domain.model.SegmentationRun
import uk.yaylali.cellseg.domain.repo.SegmentationRepository
import uk.yaylali.cellseg.util.MaskDecoder
import uk.yaylali.cellseg.util.MetricsCalculator
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

const val BATCH_MAX_IMAGES = 50

data class BatchItem(
    val uri: String,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val cellCount: Int? = null,
    val confluencePct: Float? = null,
    val error: String? = null,
    val runId: String? = null,
)

enum class BatchItemStatus { PENDING, RUNNING, DONE, FAILED }

data class BatchUiState(
    val items: List<BatchItem> = emptyList(),
    val params: SegmentationParams = SegmentationParams(),
    val selectedTier: BackendTier = BackendTier.LOCAL_CYTO3,
    val modelNotDownloaded: Boolean = false,
    val isRunning: Boolean = false,
    val currentIndex: Int = -1,
    val exportDone: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BatchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val segmentationRepository: SegmentationRepository,
    private val settingsRepository: SettingsRepository,
    private val fileStore: FileStore,
    private val modelDownloader: ModelDownloader,
    private val metricsCalculator: MetricsCalculator,
    @Named("backend_local") private val localBackend: SegmentationBackend,
    @Named("backend_remote_public") private val remoteBackend: SegmentationBackend,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchUiState())
    val uiState: StateFlow<BatchUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val tier = settingsRepository.defaultBackendTier.first().let {
                if (it == BackendTier.REMOTE_PRIVATE_HF) BackendTier.REMOTE_PUBLIC_HF else it
            }
            val params = settingsRepository.defaultParams.first()
            val modelNotDownloaded = !fileStore.modelFile(
                modelDownloader.modelFilename(ModelMetadata.CYTO3_DEFAULT)
            ).exists()
            _uiState.value = _uiState.value.copy(
                params = params,
                selectedTier = tier,
                modelNotDownloaded = modelNotDownloaded,
            )
        }
    }

    fun addImages(uris: List<String>) {
        val current = _uiState.value.items.map { it.uri }.toSet()
        val toAdd = uris.filter { it !in current }
            .take(BATCH_MAX_IMAGES - _uiState.value.items.size)
            .map { BatchItem(uri = it) }
        _uiState.value = _uiState.value.copy(items = _uiState.value.items + toAdd)
    }

    fun removeImage(uri: String) {
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items.filter { it.uri != uri }
        )
    }

    fun setTier(tier: BackendTier) {
        _uiState.value = _uiState.value.copy(selectedTier = tier)
    }

    fun setDiameter(v: Double) {
        _uiState.value = _uiState.value.copy(params = _uiState.value.params.copy(diameter = v))
    }

    fun runBatch() {
        val s = _uiState.value
        if (s.items.isEmpty()) return
        if (s.selectedTier == BackendTier.LOCAL_CYTO3 && s.modelNotDownloaded) {
            _uiState.value = s.copy(error = "Local model not downloaded.")
            return
        }
        _uiState.value = s.copy(
            isRunning = true,
            error = null,
            items = s.items.map { it.copy(status = BatchItemStatus.PENDING, error = null) },
        )
        viewModelScope.launch(Dispatchers.IO) {
            val backend = if (_uiState.value.selectedTier == BackendTier.LOCAL_CYTO3) localBackend else remoteBackend
            _uiState.value.items.forEachIndexed { idx, item ->
                _uiState.value = _uiState.value.copy(currentIndex = idx).updateItem(idx) {
                    it.copy(status = BatchItemStatus.RUNNING)
                }
                val runId = UUID.randomUUID().toString()
                val sampleId = UUID.randomUUID().toString()
                // Minimal sample
                segmentationRepository.insertSample(
                    Sample(
                        id = sampleId,
                        sampleLabel = null,
                        timepoint = null,
                        passage = null,
                        wellId = null,
                        channel = uk.yaylali.cellseg.domain.model.ImagingChannel.BRIGHTFIELD,
                        magnification = uk.yaylali.cellseg.domain.model.Magnification.OTHER,
                        cellLineLabel = null,
                        createdAt = Instant.now(),
                    )
                )
                val run = SegmentationRun(
                    id = runId,
                    sampleId = sampleId,
                    originalImagePath = item.uri,
                    params = _uiState.value.params,
                    backendTier = _uiState.value.selectedTier,
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

                var result: SegmentationProgress.Completed? = null
                var failure: String? = null
                try {
                    backend.segment(
                        java.io.File(item.uri),
                        _uiState.value.params,
                        runId,
                    ).collect { progress ->
                        when (progress) {
                            is SegmentationProgress.Completed -> result = progress
                            is SegmentationProgress.Failed -> failure = progress.error.message
                            else -> Unit
                        }
                    }
                } catch (e: Exception) {
                    failure = e.message ?: "Unknown error"
                }

                if (failure != null) {
                    segmentationRepository.updateRunStatus(runId, RunStatus.FAILED, failure)
                    _uiState.value = _uiState.value.updateItem(idx) {
                        it.copy(status = BatchItemStatus.FAILED, error = failure, runId = runId)
                    }
                    return@forEachIndexed
                }

                val r = result!!
                segmentationRepository.completeRun(
                    run.copy(
                        status = RunStatus.COMPLETED,
                        outlineOverlayPath = r.result.outlineImagePath,
                        flowsPath = r.result.flowsImagePath,
                        maskTiffPath = r.result.maskTiffPath,
                        completedAt = Instant.now(),
                    )
                )
                val metrics = metricsCalculator.compute(runId, r.result.maskLabelImage)
                segmentationRepository.insertOrReplaceMetrics(metrics)
                _uiState.value = _uiState.value.updateItem(idx) {
                    it.copy(
                        status = BatchItemStatus.DONE,
                        cellCount = metrics.cellCount,
                        confluencePct = metrics.confluencePercent,
                        runId = runId,
                    )
                }
            }
            _uiState.value = _uiState.value.copy(isRunning = false, currentIndex = -1)
        }
    }

    fun exportBatchCsv() {
        viewModelScope.launch(Dispatchers.IO) {
            val batchId = UUID.randomUUID().toString().take(8)
            val csvFile = fileStore.exportFile("batch_$batchId", "csv")
            csvFile.writeText(buildBatchCsv())
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Export batch CSV").also {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun buildBatchCsv(): String = buildString {
        appendLine("image_path,run_id,status,cell_count,confluence_pct,error")
        _uiState.value.items.forEach { item ->
            val status = item.status.name
            appendLine("${item.uri},${item.runId ?: ""},${status},${item.cellCount ?: ""},${item.confluencePct ?: ""},${item.error ?: ""}")
        }
    }

    private fun BatchUiState.updateItem(idx: Int, transform: (BatchItem) -> BatchItem): BatchUiState {
        val newItems = items.toMutableList()
        if (idx in newItems.indices) newItems[idx] = transform(newItems[idx])
        return copy(items = newItems)
    }
}
