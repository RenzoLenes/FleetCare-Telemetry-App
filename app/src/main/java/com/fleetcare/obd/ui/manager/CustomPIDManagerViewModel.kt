package com.fleetcare.obd.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fleetcare.obd.domain.model.CustomPID
import com.fleetcare.obd.domain.model.PIDCategory
import com.fleetcare.obd.domain.model.PIDSource
import com.fleetcare.obd.domain.repository.CustomPIDRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel para gestionar PIDs personalizados.
 *
 * Sprint 6: Gestión de PIDs Personalizados
 */
@HiltViewModel
class CustomPIDManagerViewModel @Inject constructor(
    private val customPIDRepository: CustomPIDRepository
) : ViewModel() {

    // ========== STATE FLOWS ==========

    private val _customPIDs = MutableStateFlow<List<CustomPID>>(emptyList())
    val customPIDs: StateFlow<List<CustomPID>> = _customPIDs.asStateFlow()

    private val _filteredPIDs = MutableStateFlow<List<CustomPID>>(emptyList())
    val filteredPIDs: StateFlow<List<CustomPID>> = _filteredPIDs.asStateFlow()

    private val _selectedCategory = MutableStateFlow<PIDCategory?>(null)
    val selectedCategory: StateFlow<PIDCategory?> = _selectedCategory.asStateFlow()

    private val _selectedSource = MutableStateFlow<PIDSource?>(null)
    val selectedSource: StateFlow<PIDSource?> = _selectedSource.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _pidCount = MutableStateFlow(0)
    val pidCount: StateFlow<Int> = _pidCount.asStateFlow()

    private val _enabledCount = MutableStateFlow(0)
    val enabledCount: StateFlow<Int> = _enabledCount.asStateFlow()

    // ========== INIT ==========

    init {
        loadCustomPIDs()
        loadCounts()
    }

    // ========== PUBLIC METHODS ==========

    /**
     * Carga todos los PIDs personalizados.
     */
    fun loadCustomPIDs() {
        viewModelScope.launch {
            customPIDRepository.getAllCustomPIDs()
                .catch { e ->
                    Timber.e(e, "Error al cargar PIDs personalizados")
                    _errorMessage.value = "Error al cargar PIDs: ${e.message}"
                }
                .collect { pids ->
                    _customPIDs.value = pids
                    applyFilters()
                }
        }
    }

    /**
     * Carga los conteos de PIDs.
     */
    fun loadCounts() {
        viewModelScope.launch {
            customPIDRepository.getCustomPIDCount().onSuccess { count ->
                _pidCount.value = count
            }
            customPIDRepository.getEnabledCustomPIDCount().onSuccess { count ->
                _enabledCount.value = count
            }
        }
    }

    /**
     * Filtra PIDs por categoría.
     */
    fun filterByCategory(category: PIDCategory?) {
        _selectedCategory.value = category
        _selectedSource.value = null // Reset source filter
        applyFilters()
    }

    /**
     * Filtra PIDs por origen.
     */
    fun filterBySource(source: PIDSource?) {
        _selectedSource.value = source
        _selectedCategory.value = null // Reset category filter
        applyFilters()
    }

    /**
     * Busca PIDs por query.
     */
    fun search(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    /**
     * Limpia filtros.
     */
    fun clearFilters() {
        _selectedCategory.value = null
        _selectedSource.value = null
        _searchQuery.value = ""
        applyFilters()
    }

    /**
     * Alterna el estado habilitado/deshabilitado de un PID.
     */
    fun toggleEnabled(pid: CustomPID) {
        viewModelScope.launch {
            _isLoading.value = true
            customPIDRepository.updateEnabled(pid.id, !pid.isEnabled)
                .onSuccess {
                    _successMessage.value = if (!pid.isEnabled) {
                        "${pid.name} habilitado"
                    } else {
                        "${pid.name} deshabilitado"
                    }
                    loadCounts()
                }
                .onFailure { e ->
                    _errorMessage.value = "Error al actualizar PID: ${e.message}"
                }
            _isLoading.value = false
        }
    }

    /**
     * Elimina un PID.
     */
    fun deletePID(pid: CustomPID) {
        viewModelScope.launch {
            _isLoading.value = true
            customPIDRepository.deleteCustomPID(pid)
                .onSuccess {
                    _successMessage.value = "${pid.name} eliminado"
                    loadCounts()
                }
                .onFailure { e ->
                    _errorMessage.value = "Error al eliminar PID: ${e.message}"
                }
            _isLoading.value = false
        }
    }

    /**
     * Elimina todos los PIDs deshabilitados.
     */
    fun deleteDisabledPIDs() {
        viewModelScope.launch {
            _isLoading.value = true
            customPIDRepository.deleteDisabledCustomPIDs()
                .onSuccess {
                    _successMessage.value = "PIDs deshabilitados eliminados"
                    loadCounts()
                }
                .onFailure { e ->
                    _errorMessage.value = "Error al eliminar PIDs: ${e.message}"
                }
            _isLoading.value = false
        }
    }

    /**
     * Importa PIDs desde JSON.
     */
    fun importFromJSON(json: String) {
        viewModelScope.launch {
            _isLoading.value = true
            customPIDRepository.importPIDsFromJSON(json)
                .onSuccess { count ->
                    _successMessage.value = "$count PIDs importados exitosamente"
                    loadCounts()
                }
                .onFailure { e ->
                    _errorMessage.value = "Error al importar PIDs: ${e.message}"
                }
            _isLoading.value = false
        }
    }

    /**
     * Exporta PIDs a JSON.
     */
    suspend fun exportToJSON(vins: List<String> = emptyList()): Result<String> {
        _isLoading.value = true
        val result = customPIDRepository.exportPIDsToJSON(vins)
        _isLoading.value = false

        result.onSuccess {
            _successMessage.value = "PIDs exportados exitosamente"
        }.onFailure { e ->
            _errorMessage.value = "Error al exportar PIDs: ${e.message}"
        }

        return result
    }

    /**
     * Exporta un PID individual.
     */
    suspend fun exportSinglePID(pidId: Long): Result<String> {
        return customPIDRepository.exportSinglePIDToJSON(pidId)
    }

    /**
     * Limpia mensajes.
     */
    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    /**
     * Obtiene estadísticas de PIDs por categoría.
     */
    fun getPIDsByCategory(): Map<PIDCategory, Int> {
        return _customPIDs.value.groupBy { it.category }.mapValues { it.value.size }
    }

    /**
     * Obtiene estadísticas de PIDs por origen.
     */
    fun getPIDsBySource(): Map<PIDSource, Int> {
        return _customPIDs.value.groupBy { it.source }.mapValues { it.value.size }
    }

    // ========== PRIVATE METHODS ==========

    /**
     * Aplica filtros y búsqueda.
     */
    private fun applyFilters() {
        var filtered = _customPIDs.value

        // Filtrar por categoría
        _selectedCategory.value?.let { category ->
            filtered = filtered.filter { it.category == category }
        }

        // Filtrar por origen
        _selectedSource.value?.let { source ->
            filtered = filtered.filter { it.source == source }
        }

        // Filtrar por búsqueda
        val query = _searchQuery.value
        if (query.isNotBlank()) {
            filtered = filtered.filter { pid ->
                pid.name.contains(query, ignoreCase = true) ||
                        pid.pid.contains(query, ignoreCase = true) ||
                        pid.command.contains(query, ignoreCase = true) ||
                        pid.notes.contains(query, ignoreCase = true)
            }
        }

        _filteredPIDs.value = filtered
    }
}
