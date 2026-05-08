package uk.yaylali.cellseg.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.yaylali.cellseg.data.filestore.FileStore
import uk.yaylali.cellseg.domain.model.BackendTier
import uk.yaylali.cellseg.domain.model.SegmentationRun
import uk.yaylali.cellseg.domain.repo.SegmentationRepository
import javax.inject.Inject

enum class HistoryFilter { ALL, LOCAL, CLOUD }

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: SegmentationRepository,
    private val fileStore: FileStore,
) : ViewModel() {

    private val _filter = MutableStateFlow(HistoryFilter.ALL)
    val filter: StateFlow<HistoryFilter> = _filter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val runs: StateFlow<List<SegmentationRun>> = combine(
        repository.observeAllRuns(),
        _filter,
        _searchQuery,
    ) { all, filter, query ->
        var filtered = all
        filtered = when (filter) {
            HistoryFilter.ALL -> filtered
            HistoryFilter.LOCAL -> filtered.filter { it.backendTier == BackendTier.LOCAL_CYTO3 }
            HistoryFilter.CLOUD -> filtered.filter { it.backendTier != BackendTier.LOCAL_CYTO3 }
        }
        if (query.isNotBlank()) {
            filtered = filtered.filter { run ->
                run.sampleId.contains(query, ignoreCase = true) ||
                    run.id.contains(query, ignoreCase = true)
            }
        }
        filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    val isSelecting get() = _selectedIds.value.isNotEmpty()

    fun setFilter(f: HistoryFilter) { _filter.value = f }
    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun toggleSelection(id: String) {
        _selectedIds.update { current ->
            if (id in current) current - id else current + id
        }
    }

    fun selectAll() {
        _selectedIds.value = runs.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteRun(id: String) {
        viewModelScope.launch {
            repository.deleteRun(id)
            fileStore.deleteRunFiles(id)
            _selectedIds.update { it - id }
        }
    }

    fun deleteSelected() {
        val toDelete = _selectedIds.value.toSet()
        viewModelScope.launch {
            toDelete.forEach { id ->
                repository.deleteRun(id)
                fileStore.deleteRunFiles(id)
            }
            _selectedIds.value = emptySet()
        }
    }
}

