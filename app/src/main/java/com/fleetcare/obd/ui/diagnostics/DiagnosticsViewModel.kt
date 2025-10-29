package com.fleetcare.obd.ui.diagnostics

import androidx.lifecycle.viewModelScope
import com.fleetcare.obd.domain.model.DiagnosticTroubleCode
import com.fleetcare.obd.domain.usecase.ClearDTCsUseCase
import com.fleetcare.obd.domain.usecase.ReadDTCsUseCase
import com.fleetcare.obd.ui.common.BaseViewModel
import com.fleetcare.obd.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para DiagnosticsFragment.
 *
 * Gestiona la lectura y limpieza de códigos de diagnóstico (DTCs).
 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val readDTCsUseCase: ReadDTCsUseCase,
    private val clearDTCsUseCase: ClearDTCsUseCase
) : BaseViewModel() {

    private val _diagnosticsState = MutableStateFlow<DiagnosticsState>(DiagnosticsState.Idle)
    val diagnosticsState: StateFlow<DiagnosticsState> = _diagnosticsState.asStateFlow()

    private val _dtcList = MutableStateFlow<List<DiagnosticTroubleCode>>(emptyList())
    val dtcList: StateFlow<List<DiagnosticTroubleCode>> = _dtcList.asStateFlow()

    /**
     * Lee los códigos de diagnóstico del vehículo.
     */
    fun readDTCs() {
        viewModelScope.launch {
            try {
                _diagnosticsState.value = DiagnosticsState.Reading
                Logger.d("Leyendo DTCs...")

                val result = readDTCsUseCase()

                if (result.isSuccess) {
                    val dtcs = result.getOrNull() ?: emptyList()
                    _dtcList.value = dtcs

                    _diagnosticsState.value = if (dtcs.isEmpty()) {
                        DiagnosticsState.NoCodes
                    } else {
                        DiagnosticsState.CodesAvailable(dtcs.size)
                    }

                    Logger.d("DTCs leídos exitosamente: ${dtcs.size} código(s)")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Error al leer DTCs"
                    Logger.e("Error al leer DTCs: $error")
                    _diagnosticsState.value = DiagnosticsState.Error(error)
                    emitError(error)
                }
            } catch (e: Exception) {
                Logger.e("Error inesperado al leer DTCs", e)
                _diagnosticsState.value = DiagnosticsState.Error(e.message ?: "Error desconocido")
                emitError(e.message ?: "Error desconocido")
            }
        }
    }

    /**
     * Limpia todos los códigos de diagnóstico del vehículo.
     */
    fun clearDTCs() {
        viewModelScope.launch {
            try {
                _diagnosticsState.value = DiagnosticsState.Clearing
                Logger.d("Limpiando DTCs...")

                val result = clearDTCsUseCase()

                if (result.isSuccess) {
                    _dtcList.value = emptyList()
                    _diagnosticsState.value = DiagnosticsState.Cleared
                    Logger.d("DTCs limpiados exitosamente")
                    emitSuccess("DTCs limpiados exitosamente")

                    // Volver a estado idle después de un tiempo
                    kotlinx.coroutines.delay(2000)
                    _diagnosticsState.value = DiagnosticsState.Idle
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Error al limpiar DTCs"
                    Logger.e("Error al limpiar DTCs: $error")
                    _diagnosticsState.value = DiagnosticsState.Error(error)
                    emitError(error)
                }
            } catch (e: Exception) {
                Logger.e("Error inesperado al limpiar DTCs", e)
                _diagnosticsState.value = DiagnosticsState.Error(e.message ?: "Error desconocido")
                emitError(e.message ?: "Error desconocido")
            }
        }
    }

    /**
     * Filtra los DTCs por tipo.
     */
    fun filterDTCs(showOnlyActive: Boolean) {
        viewModelScope.launch {
            val allDTCs = _dtcList.value
            // Nota: Por ahora no tenemos distinción entre activos/pendientes
            // Esta funcionalidad se puede expandir en el futuro
            Logger.d("Filtrando DTCs: showOnlyActive=$showOnlyActive")
        }
    }
}

/**
 * Estados posibles para la pantalla de diagnóstico.
 */
sealed class DiagnosticsState {
    object Idle : DiagnosticsState()
    object Reading : DiagnosticsState()
    object Clearing : DiagnosticsState()
    object NoCodes : DiagnosticsState()
    object Cleared : DiagnosticsState()
    data class CodesAvailable(val count: Int) : DiagnosticsState()
    data class Error(val message: String) : DiagnosticsState()
}
