package com.fleetcare.obd.ui.universal_scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fleetcare.obd.domain.model.*
import com.fleetcare.obd.domain.repository.PIDMetadataRepository
import com.fleetcare.obd.domain.repository.UniversalScanRepository
import com.fleetcare.obd.domain.usecase.ExportScanResultsUseCase
import com.fleetcare.obd.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para visualización de resultados de escaneo.
 */
@HiltViewModel
class ScanResultsViewModel @Inject constructor(
    private val scanRepository: UniversalScanRepository,
    private val metadataRepository: PIDMetadataRepository,
    private val exportUseCase: ExportScanResultsUseCase
) : ViewModel() {

    // ========== State ==========

    private val _session = MutableStateFlow<ScanSession?>(null)
    val session: StateFlow<ScanSession?> = _session.asStateFlow()

    private val _results = MutableStateFlow<List<ScanResult>>(emptyList())
    val results: StateFlow<List<ScanResult>> = _results.asStateFlow()

    private val _filteredResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val filteredResults: StateFlow<List<ScanResult>> = _filteredResults.asStateFlow()

    private val _filter = MutableStateFlow(ResultsFilter())
    val filter: StateFlow<ResultsFilter> = _filter.asStateFlow()

    private val _groupBy = MutableStateFlow(GroupByOption.NONE)
    val groupBy: StateFlow<GroupByOption> = _groupBy.asStateFlow()

    private val _sortBy = MutableStateFlow(SortByOption.PID_ASC)
    val sortBy: StateFlow<SortByOption> = _sortBy.asStateFlow()

    // ========== Computed Properties ==========

    val groupedResults: StateFlow<Map<String, List<ScanResult>>> = combine(
        _filteredResults,
        _groupBy
    ) { results, groupOption ->
        when (groupOption) {
            GroupByOption.NONE -> mapOf("All Results" to results)
            GroupByOption.MODE -> results.groupBy { "Mode ${it.mode}" }
            GroupByOption.DATA_TYPE -> results.groupBy {
                it.metadata?.detectedType?.name ?: "Unknown"
            }
            GroupByOption.SUCCESS -> results.groupBy {
                if (it.success) "Successful" else "Failed"
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val statistics: StateFlow<ScanStatistics?> = _session.map {
        it?.statistics
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    // ========== Public Methods ==========

    /**
     * Carga una sesión y sus resultados.
     */
    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val session = scanRepository.getSession(sessionId)
                _session.value = session

                if (session != null) {
                    loadResults(sessionId)
                    Logger.d("Session loaded: ${session.sessionId}, ${session.results.size} results")
                }
            } catch (e: Exception) {
                Logger.e("Error loading session", e)
            }
        }
    }

    /**
     * Carga los resultados de una sesión.
     */
    private fun loadResults(sessionId: String) {
        viewModelScope.launch {
            scanRepository.getResults(sessionId).collect { results ->
                _results.value = results
                applyFiltersAndSort()
            }
        }
    }

    /**
     * Aplica un filtro.
     */
    fun applyFilter(filter: ResultsFilter) {
        _filter.value = filter
        applyFiltersAndSort()
    }

    /**
     * Cambia la agrupación.
     */
    fun setGroupBy(groupBy: GroupByOption) {
        _groupBy.value = groupBy
    }

    /**
     * Cambia el ordenamiento.
     */
    fun setSortBy(sortBy: SortByOption) {
        _sortBy.value = sortBy
        applyFiltersAndSort()
    }

    /**
     * Aplica filtros y ordenamiento.
     */
    private fun applyFiltersAndSort() {
        val currentFilter = _filter.value
        val currentSort = _sortBy.value

        var filtered = _results.value

        // Aplicar filtros
        if (currentFilter.mode != null) {
            filtered = filtered.filter { it.mode == currentFilter.mode }
        }

        if (currentFilter.successOnly) {
            filtered = filtered.filter { it.success }
        }

        if (currentFilter.dataType != null) {
            filtered = filtered.filter { it.metadata?.detectedType == currentFilter.dataType }
        }

        if (currentFilter.searchQuery.isNotEmpty()) {
            filtered = filtered.filter { result ->
                result.pid.contains(currentFilter.searchQuery, ignoreCase = true) ||
                result.metadata?.name?.contains(currentFilter.searchQuery, ignoreCase = true) == true ||
                result.metadata?.description?.contains(currentFilter.searchQuery, ignoreCase = true) == true
            }
        }

        // Aplicar ordenamiento
        filtered = when (currentSort) {
            SortByOption.PID_ASC -> filtered.sortedBy { it.pid }
            SortByOption.PID_DESC -> filtered.sortedByDescending { it.pid }
            SortByOption.MODE_ASC -> filtered.sortedBy { it.mode }
            SortByOption.RESPONSE_TIME_ASC -> filtered.sortedBy { it.responseTime }
            SortByOption.RESPONSE_TIME_DESC -> filtered.sortedByDescending { it.responseTime }
            SortByOption.TIMESTAMP -> filtered.sortedBy { it.timestamp }
        }

        _filteredResults.value = filtered
    }

    /**
     * Exporta los resultados filtrados.
     */
    fun exportResults(format: ExportFormat): Flow<String> = flow {
        try {
            val sessionId = _session.value?.sessionId ?: throw IllegalStateException("No session loaded")

            val exportType = if (_filter.value.successOnly) {
                ExportType.SUCCESSFUL_ONLY
            } else {
                ExportType.ALL_RESULTS
            }

            val exported = exportUseCase.invoke(sessionId, exportType, format)
            emit(exported)
            Logger.d("Results exported: ${format.name}")
        } catch (e: Exception) {
            Logger.e("Error exporting results", e)
            throw e
        }
    }

    /**
     * Reinicia filtros.
     */
    fun resetFilters() {
        _filter.value = ResultsFilter()
        applyFiltersAndSort()
    }
}

/**
 * Filtro de resultados.
 */
data class ResultsFilter(
    val mode: String? = null,
    val successOnly: Boolean = false,
    val dataType: PIDDataType? = null,
    val searchQuery: String = ""
)

/**
 * Opciones de agrupación.
 */
enum class GroupByOption {
    NONE,
    MODE,
    DATA_TYPE,
    SUCCESS
}

/**
 * Opciones de ordenamiento.
 */
enum class SortByOption {
    PID_ASC,
    PID_DESC,
    MODE_ASC,
    RESPONSE_TIME_ASC,
    RESPONSE_TIME_DESC,
    TIMESTAMP
}
