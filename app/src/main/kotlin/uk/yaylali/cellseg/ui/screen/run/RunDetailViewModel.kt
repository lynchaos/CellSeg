package uk.yaylali.cellseg.ui.screen.run

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.yaylali.cellseg.data.filestore.FileStore
import uk.yaylali.cellseg.domain.model.CellMetrics
import uk.yaylali.cellseg.domain.model.RunStatus
import uk.yaylali.cellseg.domain.model.SegmentationRun
import uk.yaylali.cellseg.domain.repo.SegmentationRepository
import uk.yaylali.cellseg.util.MaskDecoder
import uk.yaylali.cellseg.util.TiffSupport
import java.io.File
import javax.inject.Inject

enum class ExportFormat { CSV, JSON }

data class RunDetailUiState(
    val run: SegmentationRun? = null,
    val metrics: CellMetrics? = null,
    val outlinePath: String? = null,
    val originalPath: String? = null,
    val flowsPath: String? = null,
    val overlayAlpha: Float = 1f,
    val isDeleted: Boolean = false,
    val deleteError: String? = null,
)

@HiltViewModel
class RunDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SegmentationRepository,
    private val fileStore: FileStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunDetailUiState())
    val uiState: StateFlow<RunDetailUiState> = _uiState.asStateFlow()

    fun load(runId: String) {
        viewModelScope.launch {
            val run = repository.getRun(runId) ?: return@launch
            val metrics = repository.getMetrics(runId)
            _uiState.value = RunDetailUiState(
                run = run,
                metrics = metrics,
                outlinePath = run.outlineOverlayPath,
                originalPath = run.originalImagePath,
                flowsPath = run.flowsPath,
            )
        }
    }

    fun setOverlayAlpha(alpha: Float) {
        _uiState.value = _uiState.value.copy(overlayAlpha = alpha)
    }

    fun deleteRun() {
        viewModelScope.launch {
            val run = _uiState.value.run ?: return@launch
            try {
                repository.deleteRun(run.id)
                fileStore.deleteRunFiles(run.id)
                _uiState.value = _uiState.value.copy(isDeleted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(deleteError = e.message ?: "Delete failed")
            }
        }
    }

    fun export(format: ExportFormat) {
        viewModelScope.launch(Dispatchers.IO) {
            val run = _uiState.value.run ?: return@launch
            val metrics = _uiState.value.metrics ?: return@launch
            val (content, ext, mime) = when (format) {
                ExportFormat.CSV -> Triple(buildPerCellCsv(run, metrics), "csv", "text/csv")
                ExportFormat.JSON -> Triple(buildJson(run, metrics), "json", "application/json")
            }
            val exportFile = fileStore.exportFile(run.id, ext)
            exportFile.writeText(content)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Export").also {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    // Per-cell CSV: one row per detected cell (cell_id, area_px, area_um2)
    private fun buildPerCellCsv(run: SegmentationRun, metrics: CellMetrics): String {
        val cellAreas: Map<Int, Int> = run.maskTiffPath
            ?.let { TiffSupport.decodeLabels(File(it)) }
            ?.let { (labels, _, _) -> MaskDecoder.cellAreas(labels) }
            ?: emptyMap()

        return buildString {
            // Header
            appendLine("# CellSeg export — run ${run.id}")
            appendLine("# sample_id,${run.sampleId}")
            appendLine("# backend,${run.backendVersionTag.ifBlank { run.backendTier.name }}")
            appendLine("# diameter_px,${run.params.diameter}")
            appendLine("# cell_count,${metrics.cellCount}")
            appendLine("# confluence_pct,${metrics.confluencePercent}")
            appendLine("# mean_area_px,${metrics.meanCellAreaPx}")
            appendLine("# median_area_px,${metrics.medianCellAreaPx}")
            appendLine("# stdev_area_px,${metrics.cellAreaPxStdev}")
            metrics.meanCellAreaUm2?.let { appendLine("# mean_area_um2,$it") }
            metrics.estimatedDensityCellsPerCm2?.let { appendLine("# density_cells_per_cm2,$it") }
            appendLine()
            if (cellAreas.isNotEmpty()) {
                appendLine("cell_id,area_px")
                cellAreas.entries.sortedBy { it.key }.forEach { (id, area) ->
                    appendLine("$id,$area")
                }
            } else {
                // Fall back to summary-only when mask is not available
                appendLine("run_id,sample_id,cell_count,confluence_pct,mean_area_px,median_area_px,stdev_area_px,mean_area_um2,density_cells_per_cm2")
                appendLine(
                    "${run.id},${run.sampleId},${metrics.cellCount}," +
                        "${metrics.confluencePercent},${metrics.meanCellAreaPx}," +
                        "${metrics.medianCellAreaPx},${metrics.cellAreaPxStdev}," +
                        "${metrics.meanCellAreaUm2 ?: ""},${metrics.estimatedDensityCellsPerCm2 ?: ""}"
                )
            }
        }
    }

    private fun buildJson(run: SegmentationRun, metrics: CellMetrics): String = buildString {
        appendLine("{")
        appendLine("  \"run_id\": \"${run.id}\",")
        appendLine("  \"sample_id\": \"${run.sampleId}\",")
        appendLine("  \"backend\": \"${run.backendVersionTag.ifBlank { run.backendTier.name }}\",")
        appendLine("  \"status\": \"${run.status.name}\",")
        appendLine("  \"started_at\": \"${run.startedAt}\",")
        appendLine("  \"completed_at\": \"${run.completedAt ?: ""}\",")
        appendLine("  \"params\": {")
        appendLine("    \"diameter\": ${run.params.diameter},")
        appendLine("    \"flow_threshold\": ${run.params.flowThreshold},")
        appendLine("    \"cell_prob_threshold\": ${run.params.cellProbThreshold},")
        appendLine("    \"max_resize\": ${run.params.maxResize}")
        appendLine("  },")
        appendLine("  \"metrics\": {")
        appendLine("    \"cell_count\": ${metrics.cellCount},")
        appendLine("    \"confluence_pct\": ${metrics.confluencePercent},")
        appendLine("    \"mean_area_px\": ${metrics.meanCellAreaPx},")
        appendLine("    \"median_area_px\": ${metrics.medianCellAreaPx},")
        appendLine("    \"stdev_area_px\": ${metrics.cellAreaPxStdev},")
        appendLine("    \"mean_area_um2\": ${metrics.meanCellAreaUm2 ?: "null"},")
        appendLine("    \"density_cells_per_cm2\": ${metrics.estimatedDensityCellsPerCm2 ?: "null"}")
        appendLine("  }")
        append("}")
    }
}
