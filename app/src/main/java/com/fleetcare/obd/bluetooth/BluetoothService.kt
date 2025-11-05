package com.fleetcare.obd.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.SharedPreferences
import com.fleetcare.obd.domain.model.AppSettings
import com.fleetcare.obd.domain.model.BluetoothDevice
import com.fleetcare.obd.domain.model.ConnectionState
import com.fleetcare.obd.domain.model.ConnectionErrorType
import com.fleetcare.obd.domain.repository.RawOBDResponseRepository
import com.fleetcare.obd.utils.Constants
import com.fleetcare.obd.utils.Logger
import com.fleetcare.obd.utils.obd.ELM327Commands
import com.fleetcare.obd.utils.obd.OBDCommandParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
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
 * - Captura de respuestas RAW para análisis (Sprint 1)
 *
 * Usa Kotlin Flow para comunicación reactiva y Coroutines para operaciones asíncronas.
 * Es un Singleton inyectado por Hilt para mantener estado durante toda la app.
 */
@Singleton
class BluetoothService @Inject constructor(
    private val bluetoothManager: BluetoothManager,
    private val rawOBDResponseRepository: RawOBDResponseRepository,
    private val sharedPreferences: SharedPreferences
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

    // ID de sesión actual para captura RAW (Sprint 1)
    private var currentSessionId: String? = null

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

            // Sprint 1: Generar nuevo sessionId para captura RAW
            currentSessionId = UUID.randomUUID().toString()

            // Sprint 1: Crear conector con soporte para captura RAW
            rfcommConnector = RFCOMMConnector(
                bluetoothAdapter = adapter,
                rawOBDResponseRepository = rawOBDResponseRepository,
                settingsProvider = { getAppSettings() },
                vehicleIdProvider = { currentDevice?.address },
                sessionIdProvider = { currentSessionId },
                captureScope = serviceScope
            )

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
            currentSessionId = null // Sprint 1: Limpiar sessionId
            _isOBDInitialized.value = false
            _connectionState.value = ConnectionState.Disconnected

            Logger.bluetooth("Desconexión completada")

        } catch (e: Exception) {
            Logger.bluetoothError("Error al desconectar", e)
        }
    }

    /**
     * Verifica si hay una conexión activa con el adaptador OBD-II.
     *
     * @return true si está conectado y el adaptador está inicializado
     */
    fun isConnected(): Boolean {
        return _connectionState.value is ConnectionState.Connected &&
               _isOBDInitialized.value &&
               rfcommConnector?.isConnected == true
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
    /**
     * Sprint 9.3/9.X: Intenta conectar usando todos los protocolos disponibles como fallback.
     *
     * Si ATSP0 (auto-detección) falla, este método prueba manualmente
     * todos los protocolos soportados (CAN, KWP, ISO 9141-2, J1850, etc.)
     * hasta encontrar uno que funcione.
     *
     * @return Result con el protocolo que funcionó o error si todos fallan
     */
    private suspend fun tryProtocolFallback(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Logger.i("Intentando fallback de protocolos (CAN + legacy)...")

                val connector = rfcommConnector ?: return@withContext Result.failure(
                    Exception("Conector RFCOMM no disponible")
                )

                // Probar cada protocolo en orden de probabilidad
                for ((protocolNumber, protocolDescription) in ELM327Commands.FALLBACK_PROTOCOLS) {
                    Logger.i("Probando protocolo $protocolNumber: $protocolDescription")

                    // Configurar adaptador según tipo de protocolo
                    val isLegacyProtocol = protocolNumber in listOf("1", "2", "3", "4", "5")

                    if (isLegacyProtocol) {
                        // Protocolos legacy: Headers OFF, timeout más largo
                        Logger.d("Configurando para protocolo legacy...")
                        connector.sendAndReceive(ELM327Commands.Initialization.HEADERS_OFF)
                        delay(100)
                        connector.sendAndReceive(ELM327Commands.Initialization.setTimeout(100)) // 400ms timeout
                        delay(100)
                    } else {
                        // Protocolos CAN: Headers ON
                        Logger.d("Configurando para protocolo CAN...")
                        connector.sendAndReceive(ELM327Commands.Initialization.HEADERS_ON)
                        delay(100)
                        connector.sendAndReceive(ELM327Commands.Initialization.SET_TIMEOUT_50) // 200ms timeout
                        delay(100)
                    }

                    // Establecer protocolo
                    val setProtocolCommand = ELM327Commands.setProtocol(protocolNumber)
                    val setResult = connector.sendAndReceive(setProtocolCommand)

                    if (setResult.isFailure) {
                        Logger.w("Fallo al establecer protocolo $protocolNumber")
                        continue
                    }

                    // Protocolos legacy necesitan más tiempo para inicialización
                    val initDelay = when (protocolNumber) {
                        "3", "4" -> 3000L  // ISO 9141-2 y KWP 5-baud necesitan 3s
                        "5" -> 1500L       // KWP fast init necesita 1.5s
                        else -> 500L       // CAN y otros son más rápidos
                    }

                    Logger.d("Esperando ${initDelay}ms para inicialización del protocolo...")
                    delay(initDelay)

                    // Probar con comando 0100 (PIDs soportados 01-20)
                    Logger.d("Verificando protocolo $protocolNumber con comando 0100...")
                    val testResult = connector.sendAndReceive(ELM327Commands.Mode01.SUPPORTED_PIDS_01_20)

                    if (testResult.isSuccess) {
                        val response = testResult.getOrNull() ?: ""

                        // Verificar que no sea error
                        if (!OBDCommandParser.isErrorResponse(response)) {
                            // Protocolo funciona!
                            Logger.i("✓ Protocolo $protocolNumber funciona: $protocolDescription")

                            // Guardar protocolo en connector
                            rfcommConnector?.protocolUsed = protocolNumber

                            return@withContext Result.success(protocolNumber)
                        } else {
                            Logger.d("Protocolo $protocolNumber retornó error: $response")
                        }
                    } else {
                        Logger.d("Protocolo $protocolNumber no responde")
                    }

                    delay(300) // Delay entre intentos
                }

                // Ningún protocolo funcionó
                Logger.e("Ningún protocolo funcionó (intentados: CAN, KWP, ISO 9141-2, J1850)")
                Result.failure(Exception("No se pudo establecer conexión con el ECU usando ningún protocolo"))

            } catch (e: Exception) {
                Logger.e(e, "Error en fallback de protocolos")
                Result.failure(e)
            }
        }
    }

    private suspend fun initializeOBDAdapter(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Logger.obd("Iniciando secuencia de inicialización ELM327...")

                val connector = rfcommConnector ?: return@withContext Result.failure(
                    Exception("Conector RFCOMM no disponible")
                )

                // Sprint 9.3: Cargar protocolo guardado para este dispositivo
                val savedProtocol = currentDevice?.address?.let { loadProtocolForDevice(it) }

                // Validar que el protocolo guardado sea válido antes de usarlo
                val validSavedProtocol = savedProtocol?.let { protocol ->
                    if (protocol.matches(Regex("[0-9A-C]"))) {
                        protocol
                    } else {
                        Logger.w("Protocolo guardado inválido: $protocol, será ignorado")
                        // Limpiar protocolo inválido
                        currentDevice?.address?.let { clearProtocolForDevice(it) }
                        null
                    }
                }

                if (validSavedProtocol != null) {
                    rfcommConnector?.protocolUsed = validSavedProtocol
                }

                // Ejecutar secuencia de inicialización
                for (command in ELM327Commands.INITIALIZATION_SEQUENCE) {
                    delay(Constants.OBD.COMMAND_DELAY_MS)

                    // Sprint 9.3: Si hay protocolo guardado válido, reemplazar ATSP0 con protocolo específico
                    val commandToSend = if (command == ELM327Commands.Initialization.AUTO_PROTOCOL && validSavedProtocol != null) {
                        Logger.i("Usando protocolo guardado $validSavedProtocol en lugar de auto-detección")
                        ELM327Commands.setProtocol(validSavedProtocol)
                    } else {
                        command
                    }

                    Logger.obd("Enviando comando de inicialización: $commandToSend")

                    val result = connector.sendAndReceive(commandToSend)

                    if (result.isFailure) {
                        Logger.obdError("Fallo en comando: $commandToSend")
                        return@withContext Result.failure(
                            result.exceptionOrNull() ?: Exception("Comando falló: $commandToSend")
                        )
                    }

                    val response = result.getOrNull()
                    Logger.obd("Respuesta: $response")

                    // Verificar respuesta OK
                    if (response?.contains("OK", ignoreCase = true) == false &&
                        response?.contains("ELM", ignoreCase = true) == false &&
                        command != ELM327Commands.Initialization.RESET
                    ) {
                        Logger.obdError("Respuesta inesperada para $commandToSend: $response")
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

                // Sprint 9.3: Verificar conexión con ECU usando comando 0100
                Logger.obd("Verificando conexión con ECU...")
                delay(500)

                val testResult = connector.sendAndReceive(ELM327Commands.Mode01.SUPPORTED_PIDS_01_20)

                if (testResult.isFailure || OBDCommandParser.isErrorResponse(testResult.getOrNull() ?: "")) {
                    Logger.w("Auto-detección de protocolo falló, intentando fallback manual...")

                    // Intentar fallback de protocolos CAN
                    val fallbackResult = tryProtocolFallback()

                    if (fallbackResult.isFailure) {
                        Logger.e("Fallback de protocolos falló")
                        return@withContext Result.failure(
                            Exception("No se pudo conectar con el ECU usando ningún protocolo")
                        )
                    }

                    val workingProtocol = fallbackResult.getOrNull()
                    Logger.i("✓ Conexión establecida usando protocolo $workingProtocol")

                    // Sprint 9.3: Guardar protocolo para futuras reconexiones
                    if (workingProtocol != null) {
                        currentDevice?.address?.let { deviceAddress ->
                            saveProtocolForDevice(deviceAddress, workingProtocol)
                        }
                    }
                } else {
                    Logger.i("✓ Conexión con ECU verificada exitosamente")

                    // Sprint 9.3: Si auto-detección funcionó, obtener y guardar el protocolo
                    delay(300)
                    val protocolResult = connector.sendAndReceive(ELM327Commands.Initialization.DESCRIBE_PROTOCOL_NUMBER)
                    if (protocolResult.isSuccess) {
                        val rawProtocol = protocolResult.getOrNull()?.trim()?.replace(">", "")
                        if (!rawProtocol.isNullOrBlank()) {
                            // ATDPN puede retornar "A6" cuando auto-detecta protocolo 6
                            // Necesitamos extraer solo el número/letra del protocolo real
                            val detectedProtocol = rawProtocol.replace("A", "").trim()
                            Logger.i("Protocolo auto-detectado (raw: $rawProtocol, parseado: $detectedProtocol)")

                            // Validar que el protocolo sea válido (0-9, A-C)
                            if (detectedProtocol.matches(Regex("[0-9A-C]"))) {
                                rfcommConnector?.protocolUsed = detectedProtocol
                                currentDevice?.address?.let { deviceAddress ->
                                    saveProtocolForDevice(deviceAddress, detectedProtocol)
                                }
                            } else {
                                Logger.w("Protocolo detectado inválido: $detectedProtocol (raw: $rawProtocol)")
                            }
                        }
                    }
                }

                // Sprint 9.6: Logging de diagnóstico adicional
                logDiagnosticInfo(connector)

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
     * Envía un comando OBD-II con timeout personalizado.
     * Método de conveniencia para UseCases que necesitan control de timeout.
     *
     * @param command Comando a enviar (ej: "010C" para RPM)
     * @param timeoutMs Timeout en milisegundos
     * @return Respuesta del comando o "NO DATA" en caso de error
     */
    suspend fun sendCommand(command: String, timeoutMs: Long): String {
        return withContext(Dispatchers.IO) {
            val connector = rfcommConnector

            if (connector == null || !connector.isConnected) {
                Logger.bluetooth("Error: No hay conexión activa")
                return@withContext "NO DATA"
            }

            if (!_isOBDInitialized.value) {
                Logger.bluetooth("Error: Adaptador OBDII no inicializado")
                return@withContext "NO DATA"
            }

            // Enviar comando y recibir respuesta con el timeout especificado
            connector.sendAndReceive(command).getOrElse { error ->
                Logger.bluetooth("Error enviando comando $command: ${error.message}")
                "NO DATA"
            }
        }
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
     * Obtiene la configuración actual de la aplicación.
     *
     * Sprint 1: Por ahora retorna configuración por defecto.
     * TODO: Inyectar SettingsRepository cuando se implemente.
     */
    private fun getAppSettings(): AppSettings {
        // TODO: Cargar desde DataStore/SharedPreferences
        return AppSettings()
    }

    // ========== SPRINT 9.6: LOGGING DE DIAGNÓSTICO ==========

    /**
     * Loguea información de diagnóstico del adaptador ELM327.
     *
     * Sprint 9.6: Obtiene y loguea información útil para diagnóstico:
     * - Versión del adaptador ELM327 (ATI)
     * - Protocolo detectado con nombre completo (ATDP)
     * - Voltaje del vehículo (ATRV)
     *
     * @param connector Conector RFCOMM activo
     */
    private suspend fun logDiagnosticInfo(connector: RFCOMMConnector) {
        try {
            Logger.i("========== DIAGNÓSTICO ELM327 ==========")

            // Obtener versión del adaptador (ATI)
            delay(200)
            val versionResult = connector.sendAndReceive(ELM327Commands.Initialization.GET_DEVICE_INFO)
            if (versionResult.isSuccess) {
                val version = versionResult.getOrNull()?.trim()?.replace(">", "")
                Logger.i("Versión ELM327: $version")
            }

            // Obtener descripción completa del protocolo (ATDP)
            delay(200)
            val protocolDescResult = connector.sendAndReceive(ELM327Commands.Initialization.DESCRIBE_PROTOCOL)
            if (protocolDescResult.isSuccess) {
                val protocolDesc = protocolDescResult.getOrNull()?.trim()?.replace(">", "")
                Logger.i("Protocolo: $protocolDesc")
            }

            // Obtener voltaje del vehículo (ATRV)
            delay(200)
            val voltageResult = connector.sendAndReceive(ELM327Commands.Initialization.GET_VOLTAGE)
            if (voltageResult.isSuccess) {
                val voltage = voltageResult.getOrNull()?.trim()?.replace(">", "")
                Logger.i("Voltaje vehículo: $voltage")
            }

            Logger.i("========================================")

        } catch (e: Exception) {
            Logger.w("Error al obtener información de diagnóstico", e)
        }
    }

    // ========== SPRINT 9.3: PROTOCOL PERSISTENCE ==========

    /**
     * Guarda el protocolo que funcionó para un dispositivo específico.
     *
     * Sprint 9.3: Almacena el protocolo en SharedPreferences usando la MAC
     * del dispositivo como clave. Esto permite cargar el protocolo correcto
     * en reconexiones futuras sin tener que probar todos los protocolos.
     *
     * @param deviceAddress Dirección MAC del dispositivo
     * @param protocol Número del protocolo (6, 7, 8, 9, etc.)
     */
    private fun saveProtocolForDevice(deviceAddress: String, protocol: String) {
        try {
            val key = "protocol_$deviceAddress"
            sharedPreferences.edit().putString(key, protocol).apply()
            Logger.i("Protocolo $protocol guardado para dispositivo $deviceAddress")
        } catch (e: Exception) {
            Logger.e(e, "Error al guardar protocolo")
        }
    }

    /**
     * Carga el protocolo guardado para un dispositivo específico.
     *
     * Sprint 9.3: Recupera el protocolo que funcionó previamente con este
     * dispositivo. Si no hay protocolo guardado, retorna null.
     *
     * @param deviceAddress Dirección MAC del dispositivo
     * @return Número del protocolo o null si no hay guardado
     */
    private fun loadProtocolForDevice(deviceAddress: String): String? {
        return try {
            val key = "protocol_$deviceAddress"
            val protocol = sharedPreferences.getString(key, null)
            if (protocol != null) {
                Logger.i("Protocolo guardado encontrado para $deviceAddress: $protocol")
            }
            protocol
        } catch (e: Exception) {
            Logger.e(e, "Error al cargar protocolo")
            null
        }
    }

    /**
     * Limpia el protocolo guardado para un dispositivo específico.
     *
     * Sprint 9.X: Útil cuando se detecta que un protocolo guardado es inválido.
     *
     * @param deviceAddress Dirección MAC del dispositivo
     */
    private fun clearProtocolForDevice(deviceAddress: String) {
        try {
            val key = "protocol_$deviceAddress"
            sharedPreferences.edit().remove(key).apply()
            Logger.i("Protocolo limpiado para dispositivo $deviceAddress")
        } catch (e: Exception) {
            Logger.e(e, "Error al limpiar protocolo")
        }
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
