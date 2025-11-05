package com.fleetcare.obd.ui.universal_scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fleetcare.obd.domain.model.*
import com.fleetcare.obd.domain.repository.UniversalScanRepository
import com.fleetcare.obd.domain.repository.VehicleProfileRepository
import com.fleetcare.obd.domain.usecase.ExportScanSessionUseCase
import com.fleetcare.obd.domain.usecase.ExportResult
import com.fleetcare.obd.domain.usecase.UniversalScanUseCase
import com.fleetcare.obd.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para el Universal PID Scanner.
 *
 * Gestiona el estado del escaneo, progreso y resultados.
 */
@HiltViewModel
class UniversalScannerViewModel @Inject constructor(
    private val universalScanUseCase: UniversalScanUseCase,
    private val scanRepository: UniversalScanRepository,
    private val profileRepository: VehicleProfileRepository,
    private val exportScanSessionUseCase: ExportScanSessionUseCase
) : ViewModel() {

    // ========== State ==========

    private val _uiState = MutableStateFlow<ScannerUIState>(ScannerUIState.Idle)
    val uiState: StateFlow<ScannerUIState> = _uiState.asStateFlow()

    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: StateFlow<ScanProgress?> = _scanProgress.asStateFlow()

    private val _currentSession = MutableStateFlow<ScanSession?>(null)
    val currentSession: StateFlow<ScanSession?> = _currentSession.asStateFlow()

    private val _selectedPreset = MutableStateFlow(ScanPresetType.QUICK)
    val selectedPreset: StateFlow<ScanPresetType> = _selectedPreset.asStateFlow()

    private val _vehicleProfile = MutableStateFlow<VehicleProfile?>(null)
    val vehicleProfile: StateFlow<VehicleProfile?> = _vehicleProfile.asStateFlow()

    // ========== Public Methods ==========

    /**
     * Inicia un escaneo con el preset seleccionado.
     */
    fun startScan(vehicleId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = ScannerUIState.Preparing

                // Obtener configuración
                val config = when (_selectedPreset.value) {
                    ScanPresetType.QUICK -> ScanPresets.quickScan(vehicleId)
                    ScanPresetType.FULL_STANDARD -> ScanPresets.fullStandardScan(vehicleId)
                    ScanPresetType.DEEP -> ScanPresets.deepScan(vehicleId)
                    ScanPresetType.LEGACY -> ScanPresets.legacyScan(vehicleId)
                    ScanPresetType.MANUFACTURER -> ScanPresets.manufacturerOnlyScan(vehicleId)
                    ScanPresetType.RECOMMENDED -> {
                        profileRepository.getRecommendedScanConfig(vehicleId)
                    }
                }

                Logger.d("Starting scan with config: ${_selectedPreset.value}")
                _uiState.value = ScannerUIState.Scanning

                // Ejecutar escaneo
                universalScanUseCase(config).collect { progress ->
                    _scanProgress.value = progress

                    // Actualizar session
                    if (progress.currentPhase == "Completed") {
                        val session = scanRepository.getActiveSession(vehicleId)
                        _currentSession.value = session
                        _uiState.value = ScannerUIState.Completed(session)
                        Logger.d("Scan completed: ${progress.successCount} PIDs found")
                    }
                }

            } catch (e: Exception) {
                Logger.e("Error during scan", e)
                _uiState.value = ScannerUIState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Pausa el escaneo actual.
     */
    fun pauseScan(vehicleId: String) {
        viewModelScope.launch {
            try {
                universalScanUseCase.pauseScan(vehicleId)
                _uiState.value = ScannerUIState.Paused
                Logger.d("Scan paused")
            } catch (e: Exception) {
                Logger.e("Error pausing scan", e)
            }
        }
    }

    /**
     * Reanuda el escaneo pausado.
     */
    fun resumeScan(vehicleId: String) {
        viewModelScope.launch {
            try {
                universalScanUseCase.resumeScan(vehicleId)
                _uiState.value = ScannerUIState.Scanning
                Logger.d("Scan resumed")
            } catch (e: Exception) {
                Logger.e("Error resuming scan", e)
            }
        }
    }

    /**
     * Cancela el escaneo actual.
     */
    fun cancelScan(vehicleId: String) {
        viewModelScope.launch {
            try {
                universalScanUseCase.cancelScan(vehicleId)
                _uiState.value = ScannerUIState.Idle
                _scanProgress.value = null
                _currentSession.value = null
                Logger.d("Scan cancelled")
            } catch (e: Exception) {
                Logger.e("Error cancelling scan", e)
            }
        }
    }

    /**
     * Selecciona un preset de configuración.
     */
    fun selectPreset(preset: ScanPresetType) {
        _selectedPreset.value = preset
        Logger.d("Preset selected: $preset")
    }

    /**
     * Carga el perfil del vehículo.
     */
    fun loadVehicleProfile(vehicleId: String) {
        viewModelScope.launch {
            try {
                val profile = profileRepository.getProfile(vehicleId)
                _vehicleProfile.value = profile

                // Si existe perfil, sugerir preset recomendado
                if (profile != null && profile.isComplete()) {
                    _selectedPreset.value = ScanPresetType.RECOMMENDED
                }

                Logger.d("Vehicle profile loaded: ${profile?.getDisplayName() ?: "No profile"}")
            } catch (e: Exception) {
                Logger.e("Error loading vehicle profile", e)
            }
        }
    }

    /**
     * Obtiene la sesión activa si existe.
     */
    fun checkActiveSession(vehicleId: String) {
        viewModelScope.launch {
            try {
                val activeSession = scanRepository.getActiveSession(vehicleId)
                if (activeSession != null) {
                    _currentSession.value = activeSession
                    _uiState.value = when (activeSession.state) {
                        ScannerState.SCANNING -> ScannerUIState.Scanning
                        ScannerState.PAUSED -> ScannerUIState.Paused
                        ScannerState.COMPLETED -> ScannerUIState.Completed(activeSession)
                        ScannerState.ERROR -> ScannerUIState.Error(activeSession.errorMessage ?: "Unknown error")
                        else -> ScannerUIState.Idle
                    }
                    Logger.d("Active session found: ${activeSession.sessionId}")
                }
            } catch (e: Exception) {
                Logger.e("Error checking active session", e)
            }
        }
    }

    /**
     * Reinicia el estado a Idle.
     */
    fun resetState() {
        _uiState.value = ScannerUIState.Idle
        _scanProgress.value = null
        _currentSession.value = null
    }

    /**
     * Exporta la sesión actual al formato especificado.
     */
    fun exportSession(format: ExportFormat) {
        viewModelScope.launch {
            try {
                val session = _currentSession.value
                if (session == null) {
                    Logger.e("Cannot export: No active session")
                    return@launch
                }

                _uiState.value = ScannerUIState.Exporting

                val result = exportScanSessionUseCase.execute(session, format)

                if (result.isSuccess) {
                    val exportResult = result.getOrNull()!!
                    _uiState.value = ScannerUIState.ExportCompleted(exportResult)
                    Logger.d("Session exported successfully to $format")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Export failed"
                    _uiState.value = ScannerUIState.Error(error)
                    Logger.e("Export failed: $error")
                }

            } catch (e: Exception) {
                Logger.e("Error exporting session", e)
                _uiState.value = ScannerUIState.Error(e.message ?: "Export error")
            }
        }
    }

    /**
     * Limpia el estado de exportación y vuelve al estado completado.
     */
    fun clearExportState() {
        val session = _currentSession.value
        if (session != null) {
            _uiState.value = ScannerUIState.Completed(session)
        } else {
            _uiState.value = ScannerUIState.Idle
        }
    }
}

/**
 * Estados del UI del scanner.
 */
sealed class ScannerUIState {
    object Idle : ScannerUIState()
    object Preparing : ScannerUIState()
    object Scanning : ScannerUIState()
    object Paused : ScannerUIState()
    data class Completed(val session: ScanSession?) : ScannerUIState()
    object Exporting : ScannerUIState()
    data class ExportCompleted(val exportResult: ExportResult) : ScannerUIState()
    data class Error(val message: String) : ScannerUIState()
}

/**
 * Tipos de presets disponibles.
 */
enum class ScanPresetType {
    QUICK,
    FULL_STANDARD,
    DEEP,
    LEGACY,
    MANUFACTURER,
    RECOMMENDED
}
