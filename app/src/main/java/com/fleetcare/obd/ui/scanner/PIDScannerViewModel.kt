package com.fleetcare.obd.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fleetcare.obd.domain.model.ExportFormat
import com.fleetcare.obd.domain.model.ScanFilter
import com.fleetcare.obd.domain.model.ScanProgress
import com.fleetcare.obd.domain.model.ScanResult
import com.fleetcare.obd.domain.model.ScannerState
import com.fleetcare.obd.domain.usecase.ScanAllPIDsUseCase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel para el escáner de PIDs.
 *
 * Sprint 5: Escáner de PIDs Completo
 *
 * Maneja el estado del escaneo de 255 PIDs y proporciona métodos
 * para iniciar, pausar, cancelar y exportar resultados.
 *
 * @property scanAllPIDsUseCase Use case para ejecutar el escaneo
 */
@HiltViewModel
class PIDScannerViewModel @Inject constructor(
    private val scanAllPIDsUseCase: ScanAllPIDsUseCase
) : ViewModel() {

    // Estado del escáner
    private val _scannerState = MutableStateFlow(ScannerState.IDLE)
    val scannerState: StateFlow<ScannerState> = _scannerState.asStateFlow()

    // Progreso del escaneo
    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: StateFlow<ScanProgress?> = _scanProgress.asStateFlow()

    // Resultados del escaneo
    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    // Filtro actual
    private val _currentFilter = MutableStateFlow(ScanFilter.ALL)
    val currentFilter: StateFlow<ScanFilter> = _currentFilter.asStateFlow()

    // Resultados filtrados
    private val _filteredResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val filteredResults: StateFlow<List<ScanResult>> = _filteredResults.asStateFlow()

    // Mensaje de error
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Job del escaneo (para cancelar)
    private var scanJob: Job? = null

    /**
     * Inicia el escaneo completo de PIDs.
     */
    fun startScan() {
        if (_scannerState.value == ScannerState.SCANNING) {
            Timber.w("Ya hay un escaneo en progreso")
            return
        }

        Timber.i("Iniciando escaneo de PIDs...")

        // Limpiar resultados previos
        _scanResults.value = emptyList()
        _filteredResults.value = emptyList()
        _scanProgress.value = null
        _errorMessage.value = null

        // Cambiar estado
        _scannerState.value = ScannerState.SCANNING

        // Iniciar escaneo
        scanJob = viewModelScope.launch {
            try {
                scanAllPIDsUseCase.execute()
                    .catch { exception ->
                        Timber.e(exception, "Error durante el escaneo")
                        _errorMessage.value = "Error: ${exception.message}"
                        _scannerState.value = ScannerState.ERROR
                    }
                    .collect { progress ->
                        // Actualizar progreso
                        _scanProgress.value = progress

                        // Agregar resultado a la lista
                        progress.currentResult?.let { result ->
                            val updatedResults = _scanResults.value + result
                            _scanResults.value = updatedResults
                            updateFilteredResults()
                        }

                        // Verificar si terminó
                        if (progress.currentPID >= progress.totalPIDs) {
                            _scannerState.value = ScannerState.COMPLETED
                            Timber.i("Escaneo completado: ${progress.successCount} éxitos, ${progress.failedCount} fallos")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error al iniciar escaneo")
                _errorMessage.value = "Error al iniciar escaneo: ${e.message}"
                _scannerState.value = ScannerState.ERROR
            }
        }
    }

    /**
     * Cancela el escaneo actual.
     */
    fun cancelScan() {
        Timber.i("Cancelando escaneo...")
        scanJob?.cancel()
        scanJob = null
        _scannerState.value = ScannerState.IDLE
    }

    /**
     * Pausa el escaneo (no implementado completamente, requiere lógica adicional).
     */
    fun pauseScan() {
        // TODO: Implementar pausa real (requiere modificar el Flow del UseCase)
        Timber.w("Pausa no implementada completamente, cancelando escaneo...")
        cancelScan()
        _scannerState.value = ScannerState.PAUSED
    }

    /**
     * Reanuda el escaneo pausado (no implementado).
     */
    fun resumeScan() {
        // TODO: Implementar reanudación
        Timber.w("Reanudar no implementado, reiniciando escaneo...")
        startScan()
    }

    /**
     * Cambia el filtro de resultados.
     */
    fun setFilter(filter: ScanFilter) {
        if (_currentFilter.value != filter) {
            _currentFilter.value = filter
            updateFilteredResults()
        }
    }

    /**
     * Actualiza los resultados filtrados según el filtro actual.
     */
    private fun updateFilteredResults() {
        val allResults = _scanResults.value
        val filtered = when (_currentFilter.value) {
            ScanFilter.ALL -> allResults
            ScanFilter.SUCCESS_ONLY -> allResults.filter { it.success }
            ScanFilter.FAILED_ONLY -> allResults.filter { !it.success }
        }
        _filteredResults.value = filtered
    }

    /**
     * Exporta los resultados en el formato especificado.
     *
     * @param format Formato de exportación (JSON o CSV)
     * @param vehicleId ID del vehículo (opcional)
     * @param vin VIN del vehículo (opcional)
     * @return String con los datos exportados
     */
    fun exportResults(
        format: ExportFormat,
        vehicleId: String? = null,
        vin: String? = null
    ): String {
        val results = _scanResults.value

        return when (format) {
            ExportFormat.JSON -> exportToJSON(results, vehicleId, vin)
            ExportFormat.CSV -> exportToCSV(results)
            ExportFormat.QR_CODE -> exportToQRData(results, vehicleId, vin)
        }
    }

    /**
     * Exporta resultados a formato JSON.
     */
    private fun exportToJSON(
        results: List<ScanResult>,
        vehicleId: String?,
        vin: String?
    ): String {
        val scanData = mapOf(
            "vehicleId" to vehicleId,
            "vin" to vin,
            "scanDate" to java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                java.util.Locale.US
            ).format(java.util.Date()),
            "totalPIDs" to results.size,
            "successfulPIDs" to results.count { it.success },
            "failedPIDs" to results.count { !it.success },
            "results" to results.map { it.toJsonMap() }
        )

        val gson = GsonBuilder()
            .setPrettyPrinting()
            .create()

        return gson.toJson(scanData)
    }

    /**
     * Exporta resultados a formato CSV.
     */
    private fun exportToCSV(results: List<ScanResult>): String {
        val csv = StringBuilder()

        // Header
        csv.appendLine("PID,PID_Decimal,Command,Success,Response,ByteCount,Interpretation,Latency_ms,Category,IsStandard")

        // Rows
        results.forEach { result ->
            csv.appendLine(
                listOf(
                    result.pid,
                    result.getPIDDecimal().toString(),
                    result.command,
                    result.success.toString(),
                    "\"${result.rawResponse}\"", // Quoted para evitar problemas con comas
                    result.byteCount.toString(),
                    "\"${result.interpretation ?: ""}\"",
                    result.latencyMs.toString(),
                    "\"${result.getCategory()}\"",
                    result.isStandardPID.toString()
                ).joinToString(",")
            )
        }

        return csv.toString()
    }

    /**
     * Exporta resultados a formato compacto para QR code.
     * Solo incluye PIDs exitosos con información mínima.
     */
    private fun exportToQRData(
        results: List<ScanResult>,
        vehicleId: String?,
        vin: String?
    ): String {
        // Solo PIDs exitosos
        val successfulResults = results.filter { it.success }

        val qrData = mapOf(
            "v" to vehicleId,  // Vehicle ID (compacto)
            "vin" to vin,
            "d" to java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()), // Date
            "c" to successfulResults.size,  // Count
            "p" to successfulResults.map { result ->
                mapOf(
                    "m" to result.mode,  // Mode
                    "p" to result.pid,   // PID
                    "n" to (result.metadata?.name ?: "")  // Name (si existe)
                )
            }
        )

        val gson = Gson()
        return gson.toJson(qrData)
    }

    /**
     * Obtiene estadísticas del escaneo.
     */
    fun getStatistics(): Map<String, Any> {
        val results = _scanResults.value
        val successResults = results.filter { it.success }

        return mapOf(
            "total" to results.size,
            "successful" to successResults.size,
            "failed" to (results.size - successResults.size),
            "avgLatency" to if (successResults.isNotEmpty()) {
                successResults.map { it.latencyMs }.average()
            } else 0.0,
            "standardPIDs" to results.count { it.isStandardPID && it.success },
            "proprietaryPIDs" to results.count { !it.isStandardPID && it.success }
        )
    }

    /**
     * Limpia el mensaje de error.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Reinicia el escáner.
     */
    fun reset() {
        cancelScan()
        _scanResults.value = emptyList()
        _filteredResults.value = emptyList()
        _scanProgress.value = null
        _errorMessage.value = null
        _currentFilter.value = ScanFilter.ALL
        _scannerState.value = ScannerState.IDLE
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
