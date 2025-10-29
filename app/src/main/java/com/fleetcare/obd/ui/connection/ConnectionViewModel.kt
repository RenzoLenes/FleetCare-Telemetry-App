package com.fleetcare.obd.ui.connection

import androidx.lifecycle.viewModelScope
import com.fleetcare.obd.domain.model.BluetoothDevice
import com.fleetcare.obd.domain.model.ConnectionState
import com.fleetcare.obd.domain.repository.BluetoothRepository
import com.fleetcare.obd.domain.usecase.ConnectToDeviceUseCase
import com.fleetcare.obd.domain.usecase.DisconnectDeviceUseCase
import com.fleetcare.obd.domain.usecase.ScanBluetoothDevicesUseCase
import com.fleetcare.obd.ui.common.BaseViewModel
import com.fleetcare.obd.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para la pantalla de conexión Bluetooth.
 *
 * Gestiona:
 * - Escaneo de dispositivos
 * - Conexión/desconexión
 * - Estado de permisos
 * - Lista de dispositivos disponibles
 */
@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
    private val scanDevicesUseCase: ScanBluetoothDevicesUseCase,
    private val connectToDeviceUseCase: ConnectToDeviceUseCase,
    private val disconnectDeviceUseCase: DisconnectDeviceUseCase
) : BaseViewModel() {

    /**
     * Lista de dispositivos Bluetooth disponibles.
     */
    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    /**
     * Estado del escaneo de dispositivos.
     */
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /**
     * Estado de la conexión Bluetooth.
     * Observa directamente el repository para actualizaciones en tiempo real.
     */
    val connectionState: StateFlow<ConnectionState> =
        bluetoothRepository.connectionState as StateFlow

    /**
     * Estado de permisos de Bluetooth.
     */
    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    /**
     * Permisos faltantes.
     */
    private val _missingPermissions = MutableStateFlow<List<String>>(emptyList())
    val missingPermissions: StateFlow<List<String>> = _missingPermissions.asStateFlow()

    init {
        checkPermissions()
    }

    /**
     * Verifica el estado de los permisos de Bluetooth.
     */
    fun checkPermissions() {
        _permissionsGranted.value = bluetoothRepository.hasRequiredPermissions()
        _missingPermissions.value = bluetoothRepository.getMissingPermissions()

        Logger.bluetooth("Permisos otorgados: ${_permissionsGranted.value}")
        if (!_permissionsGranted.value) {
            Logger.bluetooth("Permisos faltantes: ${_missingPermissions.value}")
        }
    }

    /**
     * Verifica si Bluetooth está disponible y habilitado.
     */
    fun checkBluetoothStatus(): BluetoothStatus {
        if (!bluetoothRepository.isBluetoothAvailable()) {
            return BluetoothStatus.NOT_AVAILABLE
        }

        if (!bluetoothRepository.isBluetoothEnabled()) {
            return BluetoothStatus.NOT_ENABLED
        }

        return BluetoothStatus.READY
    }

    /**
     * Carga los dispositivos Bluetooth emparejados.
     */
    fun loadPairedDevices() {
        launchWithLoading {
            Logger.bluetooth("Cargando dispositivos emparejados...")

            val result = scanDevicesUseCase.getPairedDevices()

            result.onSuccess { deviceList ->
                _devices.value = deviceList
                Logger.bluetooth("${deviceList.size} dispositivos emparejados encontrados")

                // Filtrar y resaltar dispositivos OBDII
                val obdDevices = deviceList.filter { it.isOBDII }
                if (obdDevices.isNotEmpty()) {
                    Logger.bluetooth("${obdDevices.size} dispositivos OBDII identificados")
                }

                if (deviceList.isEmpty()) {
                    emitError("No se encontraron dispositivos emparejados")
                }
            }.onFailure { exception ->
                Logger.bluetoothError("Error al cargar dispositivos", exception)
                emitError(exception.message ?: "Error al cargar dispositivos")
                _devices.value = emptyList()
            }
        }
    }

    /**
     * Conecta a un dispositivo Bluetooth.
     */
    fun connectToDevice(device: BluetoothDevice) {
        launchWithLoading {
            Logger.bluetooth("Intentando conectar a ${device.displayName}")

            val result = connectToDeviceUseCase(device)

            result.onSuccess {
                Logger.bluetooth("Conexión iniciada exitosamente")
                emitSuccess("Conectando a ${device.displayName}...")
            }.onFailure { exception ->
                Logger.bluetoothError("Error al conectar", exception)
                emitError("Error al conectar: ${exception.message}")
            }
        }
    }

    /**
     * Desconecta del dispositivo actual.
     */
    fun disconnect() {
        Logger.bluetooth("Desconectando dispositivo...")
        disconnectDeviceUseCase()
        emitSuccess("Desconectado")
    }

    /**
     * Inicia reconexión automática.
     */
    fun startAutoReconnection() {
        Logger.bluetooth("Iniciando reconexión automática")
        bluetoothRepository.startAutoReconnection()
    }

    /**
     * Detiene reconexión automática.
     */
    fun stopAutoReconnection() {
        Logger.bluetooth("Deteniendo reconexión automática")
        bluetoothRepository.stopAutoReconnection()
    }

    /**
     * Cancela el escaneo en progreso.
     */
    fun cancelScan() {
        _isScanning.value = false
        scanDevicesUseCase.cancelScan()
        Logger.bluetooth("Escaneo cancelado")
    }

    override fun onCleared() {
        super.onCleared()
        cancelScan()
    }
}

/**
 * Estado del Bluetooth del dispositivo.
 */
enum class BluetoothStatus {
    /**
     * Bluetooth no está disponible en el dispositivo.
     */
    NOT_AVAILABLE,

    /**
     * Bluetooth está disponible pero no habilitado.
     */
    NOT_ENABLED,

    /**
     * Bluetooth disponible y habilitado, listo para usar.
     */
    READY
}
