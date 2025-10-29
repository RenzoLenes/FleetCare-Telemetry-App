package com.fleetcare.obd.ui.settings

import androidx.lifecycle.viewModelScope
import com.fleetcare.obd.domain.model.AppSettings
import com.fleetcare.obd.domain.model.TemperatureUnit
import com.fleetcare.obd.domain.model.UnitSystem
import com.fleetcare.obd.ui.common.BaseViewModel
import com.fleetcare.obd.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para SettingsFragment.
 *
 * Gestiona las preferencias y configuraciones de la aplicación.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    // TODO: Inyectar PreferencesRepository cuando se implemente
) : BaseViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

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
