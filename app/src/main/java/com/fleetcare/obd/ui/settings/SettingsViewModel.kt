package com.fleetcare.obd.ui.settings

import androidx.lifecycle.viewModelScope
import com.fleetcare.obd.domain.model.AppSettings
import com.fleetcare.obd.domain.model.TemperatureUnit
import com.fleetcare.obd.domain.model.UnitSystem
import com.fleetcare.obd.domain.repository.RawOBDResponseRepository
import com.fleetcare.obd.ui.common.BaseViewModel
import com.fleetcare.obd.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * ViewModel para SettingsFragment.
 *
 * Gestiona las preferencias y configuraciones de la aplicación.
 * Sprint 1: Añadido soporte para captura RAW de datos OBD-II.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val rawOBDResponseRepository: RawOBDResponseRepository
    // TODO: Inyectar PreferencesRepository cuando se implemente
) : BaseViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // Sprint 1: Estado de almacenamiento RAW
    private val _storageInfo = MutableStateFlow("")
    val storageInfo: StateFlow<String> = _storageInfo.asStateFlow()

    init {
        loadSettings()
    }

    /**
     * Carga las configuraciones guardadas.
     */
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                // TODO: Cargar desde DataStore/SharedPreferences
                Logger.d("Configuraciones cargadas")

                // Sprint 1: Cargar info de almacenamiento si RAW capture está habilitado
                if (_settings.value.enableRawCapture) {
                    loadStorageInfo()
                }
            } catch (e: Exception) {
                Logger.e("Error al cargar configuraciones", e)
                emitError("Error al cargar configuraciones")
            }
        }
    }

    /**
     * Actualiza el sistema de unidades.
     */
    fun setUnitSystem(unitSystem: UnitSystem) {
        viewModelScope.launch {
            try {
                _settings.value = _settings.value.copy(unitSystem = unitSystem)
                saveSettings()
                Logger.d("Sistema de unidades actualizado: $unitSystem")
                emitSuccess("Sistema de unidades actualizado")
            } catch (e: Exception) {
                Logger.e("Error al actualizar sistema de unidades", e)
                emitError("Error al actualizar configuración")
            }
        }
    }

    /**
     * Actualiza la unidad de temperatura.
     */
    fun setTemperatureUnit(temperatureUnit: TemperatureUnit) {
        viewModelScope.launch {
            try {
                _settings.value = _settings.value.copy(temperatureUnit = temperatureUnit)
                saveSettings()
                Logger.d("Unidad de temperatura actualizada: $temperatureUnit")
                emitSuccess("Unidad de temperatura actualizada")
            } catch (e: Exception) {
                Logger.e("Error al actualizar unidad de temperatura", e)
                emitError("Error al actualizar configuración")
            }
        }
    }

    /**
     * Actualiza la opción de reconexión automática.
     */
    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch {
            try {
                _settings.value = _settings.value.copy(autoReconnect = enabled)
                saveSettings()
                Logger.d("Reconexión automática actualizada: $enabled")
                emitSuccess(if (enabled) "Reconexión automática activada" else "Reconexión automática desactivada")
            } catch (e: Exception) {
                Logger.e("Error al actualizar reconexión automática", e)
                emitError("Error al actualizar configuración")
            }
        }
    }

    /**
     * Actualiza el intervalo de lectura de datos.
     */
    fun setReadInterval(intervalMs: Int) {
        viewModelScope.launch {
            try {
                if (intervalMs < 500 || intervalMs > 5000) {
                    emitError("El intervalo debe estar entre 500ms y 5000ms")
                    return@launch
                }

                _settings.value = _settings.value.copy(readInterval = intervalMs)
                saveSettings()
                Logger.d("Intervalo de lectura actualizado: ${intervalMs}ms")
                emitSuccess("Intervalo de lectura actualizado")
            } catch (e: Exception) {
                Logger.e("Error al actualizar intervalo de lectura", e)
                emitError("Error al actualizar configuración")
            }
        }
    }

    /**
     * Resetea todas las configuraciones a valores por defecto.
     */
    fun resetToDefaults() {
        viewModelScope.launch {
            try {
                _settings.value = AppSettings()
                saveSettings()
                Logger.d("Configuraciones reseteadas a valores por defecto")
                emitSuccess("Configuraciones reseteadas")
            } catch (e: Exception) {
                Logger.e("Error al resetear configuraciones", e)
                emitError("Error al resetear configuraciones")
            }
        }
    }

    /**
     * Sprint 1: Actualiza la opción de captura RAW.
     */
    fun setEnableRawCapture(enabled: Boolean) {
        viewModelScope.launch {
            try {
                _settings.value = _settings.value.copy(enableRawCapture = enabled)
                saveSettings()
                Logger.d("Captura RAW actualizada: $enabled")
                emitSuccess(if (enabled) "Captura RAW activada" else "Captura RAW desactivada")

                // Actualizar info de almacenamiento
                if (enabled) {
                    loadStorageInfo()
                }
            } catch (e: Exception) {
                Logger.e("Error al actualizar captura RAW", e)
                emitError("Error al actualizar configuración")
            }
        }
    }

    /**
     * Sprint 1: Actualiza los días de retención de datos RAW.
     */
    fun setRawCaptureRetentionDays(days: Int) {
        viewModelScope.launch {
            try {
                if (days < 7 || days > 90) {
                    emitError("Los días de retención deben estar entre 7 y 90")
                    return@launch
                }

                _settings.value = _settings.value.copy(rawCaptureRetentionDays = days)
                saveSettings()
                Logger.d("Días de retención actualizados: $days")
            } catch (e: Exception) {
                Logger.e("Error al actualizar días de retención", e)
                emitError("Error al actualizar configuración")
            }
        }
    }

    /**
     * Sprint 1: Carga información de almacenamiento RAW.
     */
    fun loadStorageInfo() {
        viewModelScope.launch {
            try {
                val recordCountResult = rawOBDResponseRepository.getRecordCount()
                val storageSizeResult = rawOBDResponseRepository.getEstimatedStorageSize()

                val recordCount = recordCountResult.getOrNull() ?: 0
                val storageSize = storageSizeResult.getOrNull() ?: 0L

                val sizeKB = storageSize / 1024.0
                val sizeMB = sizeKB / 1024.0

                val sizeStr = when {
                    sizeMB >= 1.0 -> String.format("%.2f MB", sizeMB)
                    sizeKB >= 1.0 -> String.format("%.2f KB", sizeKB)
                    else -> "$storageSize bytes"
                }

                _storageInfo.value = "$recordCount registros • $sizeStr"
                Logger.d("Información de almacenamiento cargada: ${_storageInfo.value}")
            } catch (e: Exception) {
                Logger.e("Error al cargar información de almacenamiento", e)
                _storageInfo.value = "Error al cargar información"
            }
        }
    }

    /**
     * Sprint 1: Limpia datos RAW antiguos según días de retención.
     */
    fun cleanOldData() {
        viewModelScope.launch {
            try {
                val retentionDays = _settings.value.rawCaptureRetentionDays
                val cutoffTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())

                val result = rawOBDResponseRepository.deleteOlderThan(cutoffTimestamp)

                if (result.isSuccess) {
                    val deletedCount = result.getOrNull() ?: 0
                    Logger.d("Datos RAW limpiados: $deletedCount registros eliminados")
                    emitSuccess("$deletedCount registros eliminados")
                    loadStorageInfo() // Actualizar info
                } else {
                    Logger.e("Error al limpiar datos RAW")
                    emitError("Error al limpiar datos")
                }
            } catch (e: Exception) {
                Logger.e("Error al limpiar datos RAW", e)
                emitError("Error al limpiar datos")
            }
        }
    }

    /**
     * Guarda las configuraciones.
     */
    private suspend fun saveSettings() {
        try {
            // TODO: Guardar en DataStore/SharedPreferences
            Logger.d("Configuraciones guardadas: ${_settings.value}")
        } catch (e: Exception) {
            Logger.e("Error al guardar configuraciones", e)
            throw e
        }
    }
}
