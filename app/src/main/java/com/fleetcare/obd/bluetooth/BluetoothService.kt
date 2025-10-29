package com.fleetcare.obd.bluetooth

import android.bluetooth.BluetoothAdapter
import com.fleetcare.obd.domain.model.BluetoothDevice
import com.fleetcare.obd.domain.model.ConnectionState
import com.fleetcare.obd.domain.model.ConnectionErrorType
import com.fleetcare.obd.utils.Constants
import com.fleetcare.obd.utils.Logger
import com.fleetcare.obd.utils.obd.ELM327Commands
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Servicio principal para gestionar la conexión Bluetooth con dispositivos OBDII.
 *
 * Este servicio es el punto de entrada para todas las operaciones Bluetooth:
 * - Escaneo y emparejamiento
 * - Conexión y desconexión
 * - Inicialización del adaptador ELM327
 * - Envío de comandos OBDII
 * - Reconexión automática
 *
 * Usa Kotlin Flow para comunicación reactiva y Coroutines para operaciones asíncronas.
 * Es un Singleton inyectado por Hilt para mantener estado durante toda la app.
 */
@Singleton
class BluetoothService @Inject constructor(
    private val bluetoothManager: BluetoothManager
) {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Adaptador Bluetooth del sistema
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    // Conector RFCOMM para la comunicación
    private var rfcommConnector: RFCOMMConnector? = null

    // Dispositivo actualmente conectado
    private var currentDevice: BluetoothDevice? = null

    // Job de reconexión automática
    private var reconnectionJob: Job? = null

    /**
     * StateFlow del estado de conexión.
     * UI puede observar este Flow para actualizar la interfaz.
     */
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /**
     * StateFlow indicando si el adaptador OBDII está inicializado.
     */
    private val _isOBDInitialized = MutableStateFlow(false)
    val isOBDInitialized: StateFlow<Boolean> = _isOBDInitialized.asStateFlow()

    /**
     * Conecta a un dispositivo Bluetooth OBDII.
     *
     * Proceso:
     * 1. Verificar permisos y Bluetooth habilitado
     * 2. Crear conector RFCOMM
     * 3. Establecer conexión
     * 4. Inicializar adaptador ELM327
     * 5. Actualizar estado
     *
     * @param device Dispositivo al que conectar
     */
    @Suppress("MissingPermission")
    suspend fun connect(device: BluetoothDevice) {
        try {
            // Cancelar reconexión automática si está activa
            reconnectionJob?.cancel()

            // Verificar permisos
            if (!bluetoothManager.hasRequiredPermissions()) {
                _connectionState.value = ConnectionState.Error(
                    message = Constants.ErrorMessages.NO_PERMISSIONS,
                    errorType = ConnectionErrorType.PERMISSION_DENIED
                )
                return
            }

            // Verificar Bluetooth habilitado
            if (!bluetoothManager.isBluetoothEnabled) {
                _connectionState.value = ConnectionState.Error(
                    message = Constants.ErrorMessages.BLUETOOTH_NOT_ENABLED,
                    errorType = ConnectionErrorType.BLUETOOTH_NOT_ENABLED
                )
                return
            }

            // Actualizar estado a conectando
            _connectionState.value = ConnectionState.Connecting(device.displayName)
            Logger.bluetooth("Iniciando conexión a ${device.displayName} (${device.address})")

            // Desconectar si hay conexión previa
            disconnect()

            // Crear nuevo conector RFCOMM
            val adapter = bluetoothAdapter ?: run {
                _connectionState.value = ConnectionState.Error(
                    message = Constants.ErrorMessages.BLUETOOTH_NOT_AVAILABLE,
                    errorType = ConnectionErrorType.BLUETOOTH_NOT_AVAILABLE
                )
                return
            }

            rfcommConnector = RFCOMMConnector(adapter)

            // Establecer conexión RFCOMM
            val connectionResult = rfcommConnector?.connect(device.address)

            if (connectionResult?.isFailure == true) {
                _connectionState.value = ConnectionState.Error(
                    message = "Error al conectar: ${connectionResult.exceptionOrNull()?.message}",
                    errorType = ConnectionErrorType.CONNECTION_FAILED
                )
                return
            }

            Logger.bluetooth("Socket RFCOMM conectado exitosamente")

            // Guardar dispositivo actual
            currentDevice = device

            // Inicializar adaptador OBDII
            val initResult = initializeOBDAdapter()

            if (initResult.isFailure) {
                _connectionState.value = ConnectionState.Error(
                    message = "Error al inicializar OBDII: ${initResult.exceptionOrNull()?.message}",
                    errorType = ConnectionErrorType.OBD_INIT_FAILED
                )
                disconnect()
                return
            }

            // Conexión exitosa
            _isOBDInitialized.value = true
            _connectionState.value = ConnectionState.Connected(
                device = device,
                isOBDInitialized = true
            )

            Logger.bluetooth("Conexión establecida y OBDII inicializado")

        } catch (e: Exception) {
            Logger.bluetoothError("Error inesperado al conectar", e)
            _connectionState.value = ConnectionState.Error(
                message = e.message ?: Constants.ErrorMessages.CONNECTION_FAILED,
                errorType = ConnectionErrorType.UNKNOWN
            )
            disconnect()
        }
    }

    /**
     * Desconecta del dispositivo actual.
     */
    fun disconnect() {
        try {
            Logger.bluetooth("Desconectando...")

            // Cancelar reconexión automática
            reconnectionJob?.cancel()
            reconnectionJob = null

            // Cerrar conector RFCOMM
            rfcommConnector?.disconnect()
            rfcommConnector = null

            // Limpiar estado
            currentDevice = null
            _isOBDInitialized.value = false
            _connectionState.value = ConnectionState.Disconnected

            Logger.bluetooth("Desconexión completada")

        } catch (e: Exception) {
            Logger.bluetoothError("Error al desconectar", e)
        }
    }

    /**
     * Inicializa el adaptador ELM327 con la secuencia de comandos AT.
     *
     * Esta secuencia configura el adaptador en modo óptimo:
     * - Reset del dispositivo
     * - Desactivar eco y formato innecesario
     * - Auto-detectar protocolo del vehículo
     * - Verificar que responde correctamente
     *
     * @return Result indicando éxito o fallo
     */
    private suspend fun initializeOBDAdapter(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Logger.obd("Iniciando secuencia de inicialización ELM327...")

                val connector = rfcommConnector ?: return@withContext Result.failure(
                    Exception("Conector RFCOMM no disponible")
                )

                // Ejecutar secuencia de inicialización
                for (command in ELM327Commands.INITIALIZATION_SEQUENCE) {
                    delay(Constants.OBD.COMMAND_DELAY_MS)

                    Logger.obd("Enviando comando de inicialización: $command")

                    val result = connector.sendAndReceive(command)

                    if (result.isFailure) {
                        Logger.obdError("Fallo en comando: $command")
                        return@withContext Result.failure(
                            result.exceptionOrNull() ?: Exception("Comando falló: $command")
                        )
                    }

                    val response = result.getOrNull()
                    Logger.obd("Respuesta: $response")

                    // Verificar respuesta OK
                    if (response?.contains("OK", ignoreCase = true) == false &&
                        response?.contains("ELM", ignoreCase = true) == false &&
                        command != ELM327Commands.Initialization.RESET
                    ) {
                        Logger.obdError("Respuesta inesperada para $command: $response")
                    }

                    // Delay más largo después del reset
                    if (command == ELM327Commands.Initialization.RESET) {
                        delay(2000)
                    }
                }

                // Verificar que el adaptador responde
                Logger.obd("Verificando adaptador...")

                val infoResult = connector.sendAndReceive(ELM327Commands.Initialization.GET_DEVICE_INFO)
                if (infoResult.isSuccess) {
                    val info = infoResult.getOrNull()
                    Logger.obd("Información del dispositivo: $info")
                }

                Logger.obd("Adaptador ELM327 inicializado correctamente")
                Result.success(Unit)

            } catch (e: Exception) {
                Logger.obdError("Error al inicializar adaptador ELM327", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Envía un comando OBDII y espera la respuesta.
     *
     * @param command Comando a enviar (ej: "010C" para RPM)
     * @return Result con la respuesta o error
     */
    suspend fun sendOBDCommand(command: String): Result<String> {
        val connector = rfcommConnector

        if (connector == null || !connector.isConnected) {
            return Result.failure(Exception("No hay conexión activa"))
        }

        if (!_isOBDInitialized.value) {
            return Result.failure(Exception("Adaptador OBDII no inicializado"))
        }

        return connector.sendAndReceive(command)
    }

    /**
     * Inicia la reconexión automática si se pierde la conexión.
     *
     * Intentará reconectar cada 5 segundos hasta que se restablezca
     * la conexión o se cancele manualmente.
     */
    fun startAutoReconnection() {
        val device = currentDevice ?: run {
            Logger.bluetooth("No hay dispositivo para reconectar")
            return
        }

        reconnectionJob?.cancel()

        reconnectionJob = serviceScope.launch {
            var attempt = 1
            val maxAttempts = 10

            while (attempt <= maxAttempts && isActive) {
                _connectionState.value = ConnectionState.Reconnecting(attempt, maxAttempts)
                Logger.bluetooth("Intento de reconexión $attempt/$maxAttempts")

                connect(device)

                if (_connectionState.value is ConnectionState.Connected) {
                    Logger.bluetooth("Reconexión exitosa")
                    return@launch
                }

                attempt++
                delay(Constants.Bluetooth.RECONNECTION_INTERVAL_MS)
            }

            Logger.bluetoothError("Reconexión fallida después de $maxAttempts intentos")
            _connectionState.value = ConnectionState.Error(
                message = "No se pudo reconectar después de $maxAttempts intentos",
                errorType = ConnectionErrorType.CONNECTION_LOST
            )
        }
    }

    /**
     * Detiene la reconexión automática.
     */
    fun stopAutoReconnection() {
        reconnectionJob?.cancel()
        reconnectionJob = null
        Logger.bluetooth("Reconexión automática detenida")
    }

    /**
     * Limpia recursos cuando el servicio ya no se usa.
     */
    fun cleanup() {
        disconnect()
        serviceScope.cancel()
        Logger.bluetooth("BluetoothService limpiado")
    }
}
