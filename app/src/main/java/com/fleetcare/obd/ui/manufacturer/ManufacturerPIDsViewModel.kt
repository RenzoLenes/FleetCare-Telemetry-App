package com.fleetcare.obd.ui.manufacturer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fleetcare.obd.data.analysis.ManufacturerPIDDatabase
import com.fleetcare.obd.domain.model.ManufacturerPID
import com.fleetcare.obd.domain.repository.BluetoothRepository
import com.fleetcare.obd.domain.repository.CustomPIDRepository
import com.fleetcare.obd.utils.Logger
import com.fleetcare.obd.utils.obd.OBDCommandParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel para gestionar PIDs propietarios del fabricante.
 *
 * Sprint 7: Modo 22 y PIDs del Fabricante - Tarea 7.5
 */
@HiltViewModel
class ManufacturerPIDsViewModel @Inject constructor(
    private val manufacturerDatabase: ManufacturerPIDDatabase,
    private val bluetoothRepository: BluetoothRepository,
    private val customPIDRepository: CustomPIDRepository
) : ViewModel() {

    // StateFlows
    private val _manufacturerPIDs = MutableStateFlow<List<ManufacturerPID>>(emptyList())
    val manufacturerPIDs: StateFlow<List<ManufacturerPID>> = _manufacturerPIDs.asStateFlow()

    private val _filteredPIDs = MutableStateFlow<List<ManufacturerPID>>(emptyList())
    val filteredPIDs: StateFlow<List<ManufacturerPID>> = _filteredPIDs.asStateFlow()

    private val _availableManufacturers = MutableStateFlow<List<String>>(emptyList())
    val availableManufacturers: StateFlow<List<String>> = _availableManufacturers.asStateFlow()

    private val _selectedManufacturer = MutableStateFlow<String?>(null)
    val selectedManufacturer: StateFlow<String?> = _selectedManufacturer.asStateFlow()

    private val _detectedVIN = MutableStateFlow<String?>(null)
    val detectedVIN: StateFlow<String?> = _detectedVIN.asStateFlow()

    private val _detectedManufacturer = MutableStateFlow<String?>(null)
    val detectedManufacturer: StateFlow<String?> = _detectedManufacturer.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testResults = MutableStateFlow<Map<String, TestResult>>(emptyMap())
    val testResults: StateFlow<Map<String, TestResult>> = _testResults.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Stats
    private val _stats = MutableStateFlow(ManufacturerPIDDatabase.DatabaseStats(0, 0, 0, 0))
    val stats: StateFlow<ManufacturerPIDDatabase.DatabaseStats> = _stats.asStateFlow()

    /**
     * Resultado de prueba de un PID.
     */
    data class TestResult(
        val success: Boolean,
        val value: Double? = null,
        val unit: String = "",
        val error: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    init {
        loadManufacturerPIDs()
        loadAvailableManufacturers()
        loadStats()
    }

    /**
     * Carga todos los PIDs del fabricante.
     */
    private fun loadManufacturerPIDs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val pids = manufacturerDatabase.getAllPIDs()
                _manufacturerPIDs.value = pids
                _filteredPIDs.value = pids
                Timber.d("PIDs de fabricante cargados: ${pids.size}")
            } catch (e: Exception) {
                Timber.e(e, "Error al cargar PIDs de fabricante")
                _errorMessage.value = "Error al cargar PIDs: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Carga fabricantes disponibles.
     */
    private fun loadAvailableManufacturers() {
        viewModelScope.launch {
            val manufacturers = manufacturerDatabase.getAvailableManufacturers()
            _availableManufacturers.value = manufacturers
            Timber.d("Fabricantes disponibles: $manufacturers")
        }
    }

    /**
     * Carga estadísticas.
     */
    private fun loadStats() {
        viewModelScope.launch {
            val stats = manufacturerDatabase.getStats()
            _stats.value = stats
        }
    }

    /**
     * Filtra PIDs por fabricante.
     */
    fun filterByManufacturer(manufacturer: String?) {
        _selectedManufacturer.value = manufacturer

        viewModelScope.launch {
            val pids = if (manufacturer == null) {
                manufacturerDatabase.getAllPIDs()
            } else {
                manufacturerDatabase.getPIDsForManufacturer(manufacturer)
            }

            _filteredPIDs.value = applySearch(pids, _searchQuery.value)
        }
    }

    /**
     * Busca PIDs.
     */
    fun search(query: String) {
        _searchQuery.value = query

        viewModelScope.launch {
            val basePIDs = if (_selectedManufacturer.value == null) {
                manufacturerDatabase.getAllPIDs()
            } else {
                manufacturerDatabase.getPIDsForManufacturer(_selectedManufacturer.value!!)
            }

            _filteredPIDs.value = applySearch(basePIDs, query)
        }
    }

    private fun applySearch(pids: List<ManufacturerPID>, query: String): List<ManufacturerPID> {
        if (query.isBlank()) return pids

        val lowerQuery = query.lowercase()
        return pids.filter {
            it.name.lowercase().contains(lowerQuery) ||
                    it.description.lowercase().contains(lowerQuery) ||
                    it.pid.lowercase().contains(lowerQuery) ||
                    it.manufacturer.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Detecta VIN y fabricante automáticamente.
     */
    fun detectVehicle() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Leer VIN (Modo 09, PID 02)
                val vinResult = bluetoothRepository.sendOBDCommand("09 02")

                if (vinResult.isSuccess) {
                    val vinResponse = vinResult.getOrNull()
                    val vin = parseVIN(vinResponse ?: "")

                    if (vin != null) {
                        _detectedVIN.value = vin
                        val manufacturer = manufacturerDatabase.detectManufacturerFromVIN(vin)
                        _detectedManufacturer.value = manufacturer

                        if (manufacturer != null) {
                            _successMessage.value = "Vehículo detectado: $manufacturer"
                            filterByManufacturer(manufacturer)
                        } else {
                            _errorMessage.value = "Fabricante no reconocido en VIN: $vin"
                        }

                        Timber.d("VIN detectado: $vin, Fabricante: $manufacturer")
                    } else {
                        _errorMessage.value = "No se pudo leer el VIN del vehículo"
                    }
                } else {
                    _errorMessage.value = "Error al leer VIN: ${vinResult.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                Timber.e(e, "Error al detectar vehículo")
                _errorMessage.value = "Error al detectar vehículo: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Parsea VIN de la respuesta Modo 09.
     */
    private fun parseVIN(response: String): String? {
        // Modo 09 PID 02 retorna: "49 02 01 [VIN en ASCII]"
        // VIN tiene 17 caracteres
        try {
            val tokens = response.trim().split(" ").filter { it.isNotBlank() }

            // Buscar inicio de VIN (después de 49 02 XX)
            val vinStartIndex = tokens.indexOfFirst { it == "49" }
            if (vinStartIndex == -1 || tokens.size < vinStartIndex + 3) {
                return null
            }

            // Saltar 49 02 01, tomar los siguientes bytes como VIN
            val vinBytes = tokens.drop(vinStartIndex + 3).take(17)

            if (vinBytes.size < 17) {
                return null
            }

            // Convertir hex a ASCII
            val vin = vinBytes.mapNotNull { hexByte ->
                try {
                    val asciiValue = hexByte.toInt(16)
                    if (asciiValue in 32..126) asciiValue.toChar() else null
                } catch (e: NumberFormatException) {
                    null
                }
            }.joinToString("")

            return if (vin.length == 17) vin else null

        } catch (e: Exception) {
            Timber.e(e, "Error al parsear VIN")
            return null
        }
    }

    /**
     * Prueba un PID específico.
     */
    fun testPID(pid: ManufacturerPID) {
        viewModelScope.launch {
            _isTesting.value = true
            try {
                val command = pid.buildCommand()
                Timber.d("Probando PID del fabricante: $command (${pid.name})")

                val result = bluetoothRepository.sendOBDCommand(command)

                if (result.isSuccess) {
                    val response = result.getOrNull() ?: ""

                    if (OBDCommandParser.isErrorResponse(response)) {
                        val error = OBDCommandParser.getErrorMessage(response)
                        _testResults.value = _testResults.value + (pid.pid to TestResult(
                            success = false,
                            error = error
                        ))
                        Logger.w("PID ${pid.pid} retornó error: $error")
                    } else {
                        val value = OBDCommandParser.parseMode22WithPID(pid, response)

                        if (value != null) {
                            _testResults.value = _testResults.value + (pid.pid to TestResult(
                                success = true,
                                value = value,
                                unit = pid.unit
                            ))
                            Logger.obd("PID ${pid.pid} testeado exitosamente: $value ${pid.unit}")
                            _successMessage.value = "${pid.name}: $value ${pid.unit}"
                        } else {
                            _testResults.value = _testResults.value + (pid.pid to TestResult(
                                success = false,
                                error = "No se pudo parsear respuesta"
                            ))
                        }
                    }
                } else {
                    _testResults.value = _testResults.value + (pid.pid to TestResult(
                        success = false,
                        error = result.exceptionOrNull()?.message ?: "Error desconocido"
                    ))
                }
            } catch (e: Exception) {
                Timber.e(e, "Error al probar PID")
                _testResults.value = _testResults.value + (pid.pid to TestResult(
                    success = false,
                    error = e.message ?: "Error desconocido"
                ))
            } finally {
                _isTesting.value = false
            }
        }
    }

    /**
     * Guarda un PID del fabricante como PID personalizado.
     */
    fun saveAsCustomPID(manufacturerPID: ManufacturerPID) {
        viewModelScope.launch {
            try {
                val customPID = manufacturerPID.toCustomPID()
                val result = customPIDRepository.saveCustomPID(customPID)

                if (result.isSuccess) {
                    _successMessage.value = "PID guardado como personalizado"
                    Timber.d("PID ${manufacturerPID.pid} guardado como CustomPID")
                } else {
                    _errorMessage.value = "Error al guardar PID: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                Timber.e(e, "Error al guardar como CustomPID")
                _errorMessage.value = "Error: ${e.message}"
            }
        }
    }

    /**
     * Limpia mensajes.
     */
    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    /**
     * Limpia resultados de prueba.
     */
    fun clearTestResults() {
        _testResults.value = emptyMap()
    }
}
