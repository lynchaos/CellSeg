package uk.yaylali.cellseg.ui.screen.settings

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
import uk.yaylali.cellseg.BuildConfig
import uk.yaylali.cellseg.data.datastore.SettingsRepository
import uk.yaylali.cellseg.data.filestore.FileStore
import uk.yaylali.cellseg.data.local.TokenStore
import uk.yaylali.cellseg.domain.model.BackendTier
import uk.yaylali.cellseg.domain.model.CalibrationEntry
import uk.yaylali.cellseg.domain.repo.SegmentationRepository
import javax.inject.Inject

data class SettingsUiState(
    val autoFallback: Boolean = true,
    val hasToken: Boolean = false,
    val defaultTier: BackendTier = BackendTier.LOCAL_CYTO3,
    val diameter: Double = 30.0,
    val flowThreshold: Float = 0.4f,
    val cellProbThreshold: Float = 0.0f,
    val customSpaceSlug: String = "",
    val calibrations: List<CalibrationEntry> = emptyList(),
    val storageUsedMb: Float = 0f,
    val exportMessage: String? = null,
    val deleteAllDone: Boolean = false,
    val versionName: String = BuildConfig.VERSION_NAME,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val tokenStore: TokenStore,
    private val fileStore: FileStore,
    private val segmentationRepository: SegmentationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val autoFallback = settingsRepository.autoFallbackToLocal.first()
            val hasToken = tokenStore.hasToken()
            val params = settingsRepository.defaultParams.first()
            val tier = settingsRepository.defaultBackendTier.first().let {
                if (it == BackendTier.REMOTE_PRIVATE_HF) BackendTier.REMOTE_PUBLIC_HF else it
            }
            val slug = settingsRepository.customSpaceSlug.first()
            val calibrations = settingsRepository.calibrations.first()
            val runDirs = listOf("originals", "outlines", "flows", "masks", "exports").map {
                context.filesDir.resolve(it)
            }
            val storageUsedMb = runDirs.sumOf { dir ->
                if (dir.exists()) dir.walkTopDown().sumOf { it.length() } else 0L
            }.toFloat() / (1024 * 1024)
            _uiState.value = SettingsUiState(
                autoFallback = autoFallback,
                hasToken = hasToken,
                defaultTier = tier,
                diameter = params.diameter,
                flowThreshold = params.flowThreshold,
                cellProbThreshold = params.cellProbThreshold,
                customSpaceSlug = slug,
                calibrations = calibrations,
                storageUsedMb = storageUsedMb,
            )
        }
    }

    fun setDefaultTier(tier: BackendTier) {
        viewModelScope.launch {
            settingsRepository.setDefaultBackendTier(tier)
            _uiState.value = _uiState.value.copy(defaultTier = tier)
        }
    }

    fun setAutoFallback(v: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoFallbackToLocal(v)
            _uiState.value = _uiState.value.copy(autoFallback = v)
        }
    }

    fun setCustomSpaceSlug(slug: String) {
        viewModelScope.launch {
            settingsRepository.setCustomSpaceSlug(slug)
            _uiState.value = _uiState.value.copy(customSpaceSlug = slug)
        }
    }

    fun setDiameter(value: Double) {
        viewModelScope.launch {
            val current = settingsRepository.defaultParams.first()
            settingsRepository.setDefaultParams(current.copy(diameter = value))
            _uiState.value = _uiState.value.copy(diameter = value)
        }
    }

    fun setFlowThreshold(v: Float) {
        viewModelScope.launch {
            val current = settingsRepository.defaultParams.first()
            settingsRepository.setDefaultParams(current.copy(flowThreshold = v))
            _uiState.value = _uiState.value.copy(flowThreshold = v)
        }
    }

    fun setCellProbThreshold(v: Float) {
        viewModelScope.launch {
            val current = settingsRepository.defaultParams.first()
            settingsRepository.setDefaultParams(current.copy(cellProbThreshold = v))
            _uiState.value = _uiState.value.copy(cellProbThreshold = v)
        }
    }

    fun saveToken(token: String) {
        viewModelScope.launch {
            tokenStore.setToken(token)
            _uiState.value = _uiState.value.copy(hasToken = true)
        }
    }

    fun clearToken() {
        viewModelScope.launch {
            tokenStore.clearToken()
            _uiState.value = _uiState.value.copy(hasToken = false)
        }
    }

    // ── Calibration ──────────────────────────────────────────────────────────

    fun addCalibration(entry: CalibrationEntry) {
        viewModelScope.launch {
            settingsRepository.upsertCalibration(entry)
            val updated = settingsRepository.calibrations.first()
            _uiState.value = _uiState.value.copy(calibrations = updated)
        }
    }

    fun removeCalibration(entry: CalibrationEntry) {
        viewModelScope.launch {
            val current = settingsRepository.calibrations.first().filter {
                it.magnificationLabel != entry.magnificationLabel
            }
            settingsRepository.setCalibrations(current)
            _uiState.value = _uiState.value.copy(calibrations = current)
        }
    }

    // ── Data management ──────────────────────────────────────────────────────

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete run output files but keep models
            listOf("outlines", "flows", "masks", "exports").forEach { dir ->
                context.filesDir.resolve(dir).deleteRecursively()
            }
            context.cacheDir.deleteRecursively()
            _uiState.value = _uiState.value.copy(storageUsedMb = 0f)
        }
    }

    fun exportAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            val runs = segmentationRepository.observeAllRuns().first()
            val json = buildString {
                appendLine("[")
                runs.forEachIndexed { i, run ->
                    append("  {\"id\":\"${run.id}\",\"sampleId\":\"${run.sampleId}\",\"status\":\"${run.status}\"}")
                    if (i < runs.size - 1) appendLine(",") else appendLine()
                }
                appendLine("]")
            }
            val file = fileStore.exportFile("all_runs_${System.currentTimeMillis()}", "json")
            file.writeText(json)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Export all data").also {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun deleteAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            val runs = segmentationRepository.observeAllRuns().first()
            runs.forEach { run ->
                segmentationRepository.deleteRun(run.id)
                fileStore.deleteRunFiles(run.id)
            }
            _uiState.value = _uiState.value.copy(deleteAllDone = true, storageUsedMb = 0f)
        }
    }
}
