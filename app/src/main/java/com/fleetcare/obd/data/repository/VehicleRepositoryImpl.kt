package com.fleetcare.obd.data.repository

import android.content.SharedPreferences
import com.fleetcare.obd.data.analysis.ManufacturerPIDDatabase
import com.fleetcare.obd.data.local.dao.VehicleDataDao
import com.fleetcare.obd.data.mapper.VehicleDataMapper
import com.fleetcare.obd.domain.model.CustomPID
import com.fleetcare.obd.domain.model.ManufacturerPID
import com.fleetcare.obd.domain.model.VehicleData
import com.fleetcare.obd.domain.repository.BluetoothRepository
import com.fleetcare.obd.domain.repository.CustomPIDRepository
import com.fleetcare.obd.domain.repository.SupportedPIDsRepository
import com.fleetcare.obd.domain.repository.VehicleRepository
import com.fleetcare.obd.utils.Constants
import com.fleetcare.obd.utils.Logger
import com.fleetcare.obd.utils.obd.OBDCommandParser
import com.fleetcare.obd.utils.obd.PIDConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación del VehicleRepository.
 *
 * Coordina la lectura continua de datos OBDII, el parsing de respuestas,
 * y el almacenamiento en caché local.
 *
 * Sprint 6: Integración de PIDs personalizados (tarea 6.6)
 * Sprint 7: Integración de PIDs del fabricante (tarea 7.6)
 */
@Singleton
class VehicleRepositoryImpl @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
    private val vehicleDataDao: VehicleDataDao,
    private val customPIDRepository: CustomPIDRepository,
    private val manufacturerPIDDatabase: ManufacturerPIDDatabase,
    private val sharedPreferences: SharedPreferences,
    private val supportedPIDsRepository: SupportedPIDsRepository // Sprint 9.5
) : VehicleRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // StateFlow de datos del vehículo
    private val _vehicleDataFlow = MutableStateFlow(VehicleData.empty())
    override val vehicleDataFlow: StateFlow<VehicleData> = _vehicleDataFlow.asStateFlow()

    // StateFlow de estado de lectura
    private val _isReading = MutableStateFlow(false)
    override val isReading: StateFlow<Boolean> = _isReading.asStateFlow()

    // Job de lectura continua
    private var readingJob: Job? = null

    // Sprint 6: Caché de PIDs personalizados habilitados (por comando)
    private val customPIDsCache = mutableMapOf<String, CustomPID>()
    private var customPIDsLoaded = false

    // Sprint 7: Caché de PIDs del fabricante (por comando)
    private val manufacturerPIDsCache = mutableMapOf<String, ManufacturerPID>()
    private var manufacturerPIDsLoaded = false
    private var detectedVIN: String? = null

    // Sprint 9.4: Caché de PIDs no soportados y contador de fallos
    private val unsupportedPIDsCache = mutableSetOf<String>()
    private val pidFailureCountMap = mutableMapOf<String, Int>()
    private val maxFailuresBeforeCache = 3

    // Sprint 9.4: Flow de PIDs no soportados para observación en UI
    private val _unsupportedPIDsFlow = MutableStateFlow<Set<String>>(emptySet())
    override val unsupportedPIDsFlow: StateFlow<Set<String>> = _unsupportedPIDsFlow.asStateFlow()

    // Sprint 9.5: Caché del bitmap de PIDs soportados
    private var supportedPIDsBitmap: com.fleetcare.obd.domain.model.SupportedPIDsBitmap? = null

    init {
        // Cargar PIDs personalizados al iniciar
        scope.launch {
            loadCustomPIDs()
            loadManufacturerPIDs()
        }
    }

    override fun startContinuousReading() {
        if (_isReading.value) {
            Logger.obd("La lectura continua ya está activa")
            return
        }

        Logger.obd("Iniciando lectura continua de datos OBDII...")
        _isReading.value = true

        readingJob = scope.launch {
            while (isActive && _isReading.value) {
                try {
                    // Leer todos los PIDs básicos
                    val vehicleData = readAllBasicParameters()

                    // Emitir datos si hay al menos un valor
                    if (vehicleData.hasData) {
                        _vehicleDataFlow.value = vehicleData
                        Logger.obd("Datos emitidos: ${vehicleData.availableParametersCount} parámetros")
                    } else {
                        Logger.w("No se pudieron leer datos del vehículo")
                    }

                    // Esperar intervalo configurado antes de la siguiente lectura
                    delay(Constants.OBD.DATA_READ_INTERVAL_MS)

                } catch (e: CancellationException) {
                    Logger.obd("Lectura continua cancelada")
                    break
                } catch (e: Exception) {
                    Logger.obdError("Error en lectura continua", e)
                    delay(Constants.OBD.DATA_READ_INTERVAL_MS)
                }
            }
        }
    }

    override fun stopContinuousReading() {
        Logger.obd("Deteniendo lectura continua...")
        _isReading.value = false
        readingJob?.cancel()
        readingJob = null
    }

    /**
     * Lee todos los parámetros básicos definidos en PIDConstants.
     */
    private suspend fun readAllBasicParameters(): VehicleData {
        val timestamp = Date()
        var rpm: Int? = null
        var speed: Double? = null
        var coolantTemp: Double? = null
        var intakeAirTemp: Double? = null
        var throttlePosition: Double? = null
        var engineLoad: Double? = null
        var voltage: Double? = null
        var fuelLevel: Double? = null
        var oilTemp: Double? = null
        var ambientTemp: Double? = null

        // Sprint 10: Auto-aprendizaje de PIDs - Lista de PIDs leídos exitosamente
        val successfulPIDs = mutableSetOf<Int>()

        // Sprint 9.5: Cargar bitmap de PIDs soportados si aún no está cargado
        if (supportedPIDsBitmap == null) {
            loadSupportedPIDsBitmap()
        }

        // Leer cada PID secuencialmente
        for (pid in PIDConstants.BASIC_PIDS) {
            try {
                // Sprint 9.4: Filtrar PIDs no soportados (caché de fallos)
                if (unsupportedPIDsCache.contains(pid.command)) {
                    Logger.d("Saltando PID no soportado: ${pid.name} (${pid.command})")
                    continue
                }

                // Sprint 9.5: Filtrar usando bitmap de PIDs soportados
                if (supportedPIDsBitmap != null && !isPIDSupportedByBitmap(pid.command)) {
                    Logger.d("Saltando PID no detectado en bitmap: ${pid.name} (${pid.command})")
                    continue
                }

                val result = readSingleParameter(pid.command)

                if (result.isSuccess) {
                    val value = result.getOrNull()

                    // Asignar valor al campo correspondiente
                    when (pid.command) {
                        PIDConstants.ENGINE_RPM.command -> rpm = value?.toInt()
                        PIDConstants.VEHICLE_SPEED.command -> speed = value
                        PIDConstants.COOLANT_TEMP.command -> coolantTemp = value
                        PIDConstants.INTAKE_AIR_TEMP.command -> intakeAirTemp = value
                        PIDConstants.THROTTLE_POSITION.command -> throttlePosition = value
                        PIDConstants.ENGINE_LOAD.command -> engineLoad = value
                        PIDConstants.CONTROL_MODULE_VOLTAGE.command -> voltage = value
                        PIDConstants.FUEL_LEVEL.command -> fuelLevel = value
                        PIDConstants.ENGINE_OIL_TEMP.command -> oilTemp = value
                        PIDConstants.AMBIENT_AIR_TEMP.command -> ambientTemp = value
                    }

                    // Sprint 9.4: Reset contador de fallos si fue exitoso
                    pidFailureCountMap.remove(pid.command)

                    // Sprint 10: Registrar PID exitoso para auto-aprendizaje
                    extractPIDFromCommand(pid.command)?.let { pidHex ->
                        successfulPIDs.add(pidHex)
                    }
                }

                // Pequeño delay entre comandos para no saturar el adaptador
                delay(Constants.OBD.COMMAND_DELAY_MS)

            } catch (e: Exception) {
                Logger.obdError("Error al leer ${pid.name}", e)
            }
        }

        // Sprint 10: Actualizar bitmap con PIDs aprendidos
        if (successfulPIDs.isNotEmpty()) {
            updateSupportedPIDsBitmap(successfulPIDs)
        }

        return VehicleData(
            timestamp = timestamp,
            rpm = rpm,
            speed = speed,
            coolantTemp = coolantTemp,
            intakeAirTemp = intakeAirTemp,
            throttlePosition = throttlePosition,
            engineLoad = engineLoad,
            voltage = voltage,
            fuelLevel = fuelLevel,
            oilTemp = oilTemp,
            ambientTemp = ambientTemp
        )
    }

    override suspend fun readSingleParameter(command: String): Result<Double> {
        return withContext(Dispatchers.IO) {
            try {
                // Enviar comando OBDII
                val result = bluetoothRepository.sendOBDCommand(command)

                if (result.isFailure) {
                    return@withContext Result.failure(
                        result.exceptionOrNull() ?: Exception("Error al enviar comando")
                    )
                }

                val response = result.getOrNull() ?: return@withContext Result.failure(
                    Exception("Respuesta vacía")
                )

                // Verificar si es una respuesta de error
                if (OBDCommandParser.isErrorResponse(response)) {
                    val errorMsg = OBDCommandParser.getErrorMessage(response)

                    // Sprint 9.4: Manejar "NO DATA" de manera especial
                    if (errorMsg.contains("NO DATA", ignoreCase = true)) {
                        handleNoDataError(command, errorMsg)
                    } else {
                        Logger.obdError("Error en respuesta: $errorMsg")
                    }

                    return@withContext Result.failure(Exception(errorMsg))
                }

                // Prioridad de parseo: Manufacturer > Custom > Estándar
                // Sprint 7: Intentar con PID del fabricante primero
                val value = parseWithManufacturerPID(command, response) ?:
                        // Sprint 6: Intentar con PID personalizado
                        parseWithCustomPID(command, response) ?:
                        // Fallback a parser estándar
                        OBDCommandParser.parseResponse(command, response)

                if (value == null) {
                    return@withContext Result.failure(
                        Exception("No se pudo parsear la respuesta: $response")
                    )
                }

                Result.success(value)

            } catch (e: Exception) {
                Logger.obdError("Error al leer parámetro $command", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun saveToCache(
        data: VehicleData,
        vehicleId: String,
        sessionId: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val entity = VehicleDataMapper.domainToEntity(data, vehicleId, sessionId)
                vehicleDataDao.insert(entity)
                Logger.d("Datos guardados en caché: $vehicleId")
                Result.success(Unit)
            } catch (e: Exception) {
                Logger.e(e, "Error al guardar en caché")
                Result.failure(e)
            }
        }
    }

    override fun getHistoricalData(vehicleId: String, limit: Int): Flow<List<VehicleData>> {
        return vehicleDataDao.getLatestDataForVehicle(vehicleId, limit)
            .map { entities ->
                VehicleDataMapper.entitiesToDomain(entities)
            }
            .flowOn(Dispatchers.IO)
    }

    // ========== SPRINT 6: MÉTODOS DE PIDs PERSONALIZADOS ==========

    /**
     * Carga PIDs personalizados habilitados en el caché.
     */
    private suspend fun loadCustomPIDs() {
        try {
            customPIDRepository.getEnabledCustomPIDs()
                .firstOrNull()
                ?.let { pids ->
                    customPIDsCache.clear()
                    pids.forEach { pid ->
                        customPIDsCache[pid.command.uppercase()] = pid
                    }
                    customPIDsLoaded = true
                    Timber.d("PIDs personalizados cargados: ${customPIDsCache.size}")
                    Logger.obd("PIDs personalizados cargados: ${customPIDsCache.size}")
                }
        } catch (e: Exception) {
            Timber.e(e, "Error al cargar PIDs personalizados")
            Logger.e(e, "Error al cargar PIDs personalizados")
        }
    }

    /**
     * Refresca el caché de PIDs personalizados.
     * Útil cuando se agregan/editan PIDs durante el uso de la app.
     */
    suspend fun refreshCustomPIDs() {
        loadCustomPIDs()
    }

    /**
     * Intenta parsear una respuesta OBD usando un PID personalizado.
     * Prioridad: Custom > Estándar
     *
     * @param command Comando OBD enviado
     * @param response Respuesta RAW del adaptador
     * @return Valor parseado o null si no se puede parsear
     */
    private fun parseWithCustomPID(command: String, response: String): Double? {
        // Verificar si hay PIDs personalizados cargados
        if (!customPIDsLoaded || customPIDsCache.isEmpty()) {
            return null
        }

        // Buscar PID personalizado por comando
        val customPID = customPIDsCache[command.uppercase()] ?: return null

        return try {
            // Extraer bytes de datos de la respuesta
            val dataBytes = extractDataBytes(response, customPID.byteCount)

            if (dataBytes == null || dataBytes.size < customPID.byteCount) {
                Timber.w("No hay suficientes bytes en la respuesta para PID ${customPID.pid}")
                return null
            }

            // Aplicar fórmula personalizada
            val result = customPID.applyFormula(dataBytes)

            if (result != null) {
                Timber.d("PID personalizado ${customPID.pid} parseado: $result ${customPID.unit}")
                Logger.obd("PID custom ${customPID.name}: $result ${customPID.unit}")

                // Actualizar fecha de último uso
                scope.launch {
                    customPIDRepository.updateLastUsed(customPID.id)
                }
            }

            result
        } catch (e: Exception) {
            Timber.e(e, "Error al parsear con PID personalizado ${customPID.pid}")
            null
        }
    }

    /**
     * Extrae los bytes de datos de una respuesta OBD.
     *
     * Formato típico de respuesta: "41 0C 1A F8" → bytes [1A, F8]
     * - 41: Respuesta modo 01
     * - 0C: PID
     * - 1A F8: Datos
     *
     * @param response Respuesta RAW
     * @param expectedByteCount Número de bytes esperados
     * @return Array de bytes o null si hay error
     */
    private fun extractDataBytes(response: String, expectedByteCount: Int): ByteArray? {
        return try {
            // Limpiar respuesta: eliminar espacios, '>', prompts, etc.
            val cleaned = response
                .replace(">", "")
                .replace("\r", "")
                .replace("\n", "")
                .trim()

            // Dividir en tokens hex
            val tokens = cleaned.split(" ").filter { it.isNotBlank() }

            if (tokens.size < 3) {
                // Respuesta muy corta (necesitamos al menos: modo + PID + 1 byte)
                return null
            }

            // Tokens[0] = modo de respuesta (41, 62, etc.)
            // Tokens[1] = PID
            // Tokens[2+] = datos

            val dataTokens = tokens.drop(2) // Saltar modo y PID

            if (dataTokens.size < expectedByteCount) {
                Timber.w("Respuesta tiene menos bytes de los esperados: ${dataTokens.size} < $expectedByteCount")
                // Intentar de todas formas con los bytes disponibles
            }

            // Convertir tokens hex a bytes
            val bytes = dataTokens.take(expectedByteCount).mapNotNull { token ->
                try {
                    token.toInt(16).toByte()
                } catch (e: NumberFormatException) {
                    null
                }
            }.toByteArray()

            if (bytes.isEmpty()) null else bytes

        } catch (e: Exception) {
            Timber.e(e, "Error al extraer bytes de datos")
            null
        }
    }

    // ========== SPRINT 7: MÉTODOS DE PIDs DEL FABRICANTE ==========

    /**
     * Carga PIDs del fabricante en el caché.
     * Sprint 7: Tarea 7.6
     */
    private suspend fun loadManufacturerPIDs() {
        try {
            // Intentar detectar VIN primero
            detectVINIfNeeded()

            // Cargar PIDs recomendados si tenemos VIN, o todos los PIDs si no
            val pids = if (detectedVIN != null) {
                manufacturerPIDDatabase.getRecommendedPIDsForVIN(detectedVIN!!)
            } else {
                manufacturerPIDDatabase.getEnabledPIDs()
            }

            manufacturerPIDsCache.clear()
            pids.forEach { pid ->
                manufacturerPIDsCache[pid.buildCommand().uppercase()] = pid
            }

            manufacturerPIDsLoaded = true
            Timber.d("PIDs del fabricante cargados: ${manufacturerPIDsCache.size}")
            Logger.obd("PIDs del fabricante cargados: ${manufacturerPIDsCache.size}")

        } catch (e: Exception) {
            Timber.e(e, "Error al cargar PIDs del fabricante")
            Logger.e(e, "Error al cargar PIDs del fabricante")
        }
    }

    /**
     * Detecta el VIN del vehículo si no está ya detectado.
     */
    private suspend fun detectVINIfNeeded() {
        if (detectedVIN != null) return

        try {
            // Leer VIN (Modo 09, PID 02)
            val vinResult = bluetoothRepository.sendOBDCommand("09 02")

            if (vinResult.isSuccess) {
                val vinResponse = vinResult.getOrNull()
                detectedVIN = parseVIN(vinResponse ?: "")

                if (detectedVIN != null) {
                    val manufacturer = manufacturerPIDDatabase.detectManufacturerFromVIN(detectedVIN!!)
                    Timber.d("VIN detectado: $detectedVIN, Fabricante: $manufacturer")
                    Logger.obd("VIN detectado: $detectedVIN, Fabricante: $manufacturer")

                    // Sprint 9.4: Verificar si cambió el VIN y limpiar caché
                    checkVINChangeAndClearCache()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error al detectar VIN")
        }
    }

    /**
     * Parsea VIN de la respuesta Modo 09.
     */
    private fun parseVIN(response: String): String? {
        try {
            val tokens = response.trim().split(" ").filter { it.isNotBlank() }

            val vinStartIndex = tokens.indexOfFirst { it == "49" }
            if (vinStartIndex == -1 || tokens.size < vinStartIndex + 3) {
                return null
            }

            val vinBytes = tokens.drop(vinStartIndex + 3).take(17)

            if (vinBytes.size < 17) {
                return null
            }

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
     * Refresca el caché de PIDs del fabricante.
     */
    suspend fun refreshManufacturerPIDs() {
        loadManufacturerPIDs()
    }

    /**
     * Intenta parsear una respuesta usando PIDs del fabricante.
     * Prioridad: Manufacturer > Custom > Estándar
     *
     * @param command Comando OBD enviado
     * @param response Respuesta RAW del adaptador
     * @return Valor parseado o null si no se puede parsear
     */
    private fun parseWithManufacturerPID(command: String, response: String): Double? {
        // Verificar si hay PIDs del fabricante cargados
        if (!manufacturerPIDsLoaded || manufacturerPIDsCache.isEmpty()) {
            return null
        }

        // Buscar PID del fabricante por comando
        val manufacturerPID = manufacturerPIDsCache[command.uppercase()] ?: return null

        return try {
            // Usar el parser del OBDCommandParser para Modo 22
            val result = OBDCommandParser.parseMode22WithPID(manufacturerPID, response)

            if (result != null) {
                Timber.d("PID del fabricante ${manufacturerPID.pid} parseado: $result ${manufacturerPID.unit}")
                Logger.obd("PID fabricante ${manufacturerPID.name}: $result ${manufacturerPID.unit}")
            }

            result
        } catch (e: Exception) {
            Timber.e(e, "Error al parsear con PID del fabricante ${manufacturerPID.pid}")
            null
        }
    }

    // ========== SPRINT 9.4: MANEJO DE PIDs NO SOPORTADOS ==========

    /**
     * Maneja errores "NO DATA" con logging inteligente y caché.
     *
     * Sprint 9.4: En lugar de loguear cada "NO DATA" como ERROR,
     * este método:
     * - Primera vez: loguea WARNING
     * - Incrementa contador de fallos
     * - Después de 3 fallos: agrega a caché de no soportados
     * - Subsecuentes: solo DEBUG
     *
     * @param command Comando PID que falló
     * @param errorMsg Mensaje de error
     */
    private fun handleNoDataError(command: String, errorMsg: String) {
        // Incrementar contador de fallos
        val currentFailures = pidFailureCountMap[command] ?: 0
        val newFailureCount = currentFailures + 1
        pidFailureCountMap[command] = newFailureCount

        when {
            newFailureCount == 1 -> {
                // Primera vez: WARNING
                Logger.w("PID $command no soportado: $errorMsg (intento 1/$maxFailuresBeforeCache)")
            }
            newFailureCount < maxFailuresBeforeCache -> {
                // Intentos intermedios: DEBUG
                Logger.d("PID $command no soportado: $errorMsg (intento $newFailureCount/$maxFailuresBeforeCache)")
            }
            newFailureCount == maxFailuresBeforeCache -> {
                // Tercer fallo: agregar a caché y loguear INFO
                unsupportedPIDsCache.add(command)
                _unsupportedPIDsFlow.value = unsupportedPIDsCache.toSet()
                Logger.i("PID $command agregado a caché de no soportados después de $maxFailuresBeforeCache fallos")
            }
            else -> {
                // Después de agregado a caché: solo DEBUG
                Logger.d("PID $command en caché de no soportados")
            }
        }
    }

    /**
     * Limpia la caché de PIDs no soportados.
     *
     * Sprint 9.4: Se debe llamar cuando se cambia de vehículo (VIN diferente)
     * para permitir re-detectar PIDs en el nuevo vehículo.
     */
    fun clearUnsupportedPIDsCache() {
        val previousSize = unsupportedPIDsCache.size
        unsupportedPIDsCache.clear()
        pidFailureCountMap.clear()
        _unsupportedPIDsFlow.value = emptySet()
        Logger.i("Caché de PIDs no soportados limpiada (${previousSize} PIDs)")
    }

    /**
     * Verifica si el VIN cambió y limpia caché si es necesario.
     *
     * Sprint 9.4: Tarea 9.4.5
     * Debe llamarse cuando se detecta un nuevo VIN.
     */
    private suspend fun checkVINChangeAndClearCache() {
        val currentVIN = detectedVIN ?: return

        try {
            // Obtener VIN anterior de SharedPreferences
            val previousVIN = sharedPreferences.getString(PREF_KEY_LAST_VIN, null)

            if (previousVIN == null) {
                // Primera vez que se detecta VIN, guardarlo
                sharedPreferences.edit().putString(PREF_KEY_LAST_VIN, currentVIN).apply()
                Logger.i("VIN inicial guardado: $currentVIN")
            } else if (previousVIN != currentVIN) {
                // VIN cambió, limpiar caché de PIDs no soportados
                Logger.i("Cambio de vehículo detectado: $previousVIN → $currentVIN")
                clearUnsupportedPIDsCache()

                // Guardar nuevo VIN
                sharedPreferences.edit().putString(PREF_KEY_LAST_VIN, currentVIN).apply()
                Logger.i("Nuevo VIN guardado: $currentVIN")
            } else {
                Logger.d("VIN sin cambios: $currentVIN")
            }
        } catch (e: Exception) {
            Logger.e(e, "Error al verificar cambio de VIN")
        }
    }

    // ========== SPRINT 9.5: FILTRADO CON BITMAP DE PIDs SOPORTADOS ==========

    /**
     * Carga el bitmap de PIDs soportados desde el caché.
     *
     * Sprint 9.5: Se carga una vez y se mantiene en memoria durante la sesión.
     */
    private suspend fun loadSupportedPIDsBitmap() {
        try {
            Logger.d("🔄 Intentando cargar bitmap de PIDs soportados...")

            // Obtener MAC del dispositivo conectado (vehicleId)
            val currentState = bluetoothRepository.connectionState.first()
            val vehicleId = if (currentState is com.fleetcare.obd.domain.model.ConnectionState.Connected) {
                currentState.device.address
            } else {
                null
            }

            if (vehicleId == null) {
                Logger.w("⚠️ No hay dispositivo conectado, no se puede cargar bitmap de PIDs")
                return
            }

            Logger.d("   VehicleId: $vehicleId")

            // Cargar bitmap del caché
            val bitmapResult = supportedPIDsRepository.getSupportedPIDsSync(vehicleId)
            supportedPIDsBitmap = bitmapResult.getOrNull()

            if (supportedPIDsBitmap != null) {
                Logger.i("✅ Bitmap de PIDs cargado exitosamente: ${supportedPIDsBitmap!!.getTotalSupportedCount()} PIDs soportados")
                Logger.d("   PIDs: ${supportedPIDsBitmap!!.allSupportedPIDs.take(10).joinToString(", ") { "0x${it.toString(16).uppercase().padStart(2, '0')}" }}${if (supportedPIDsBitmap!!.allSupportedPIDs.size > 10) "..." else ""}")
            } else {
                Logger.w("⚠️ No hay bitmap de PIDs en caché para vehicleId: $vehicleId")
                Logger.w("   Se intentará detectar PIDs automáticamente al conectar")
            }
        } catch (e: Exception) {
            Logger.e(e, "❌ Error al cargar bitmap de PIDs soportados")
        }
    }

    /**
     * Verifica si un PID está soportado según el bitmap.
     *
     * Sprint 9.5: Extrae el PID hex del comando y verifica en el bitmap.
     *
     * @param command Comando OBD (ej: "010C")
     * @return true si está soportado, false si no
     */
    private fun isPIDSupportedByBitmap(command: String): Boolean {
        val bitmap = supportedPIDsBitmap ?: return true // Si no hay bitmap, no filtrar

        return try {
            // Extraer PID del comando (ej: "010C" → 0x0C)
            val pidHex = command.takeLast(2)
            val pid = pidHex.toInt(16)

            bitmap.isPIDSupported(pid)
        } catch (e: Exception) {
            Logger.w("Error al verificar PID en bitmap: $command", e)
            true // En caso de error, no filtrar
        }
    }

    // ========== SPRINT 10: AUTO-APRENDIZAJE DE PIDs ==========

    /**
     * Extrae el PID hexadecimal de un comando OBD.
     *
     * Sprint 10: Helper para auto-aprendizaje de PIDs.
     *
     * @param command Comando OBD (ej: "010C", "0C")
     * @return PID en formato Int (ej: 12 para "010C") o null si hay error
     */
    private fun extractPIDFromCommand(command: String): Int? {
        return try {
            // Extraer últimos 2 caracteres (el PID)
            val pidHex = command.takeLast(2)
            pidHex.toInt(16)
        } catch (e: Exception) {
            Logger.w("Error al extraer PID de comando: $command", e)
            null
        }
    }

    /**
     * Actualiza el bitmap de PIDs soportados con PIDs leídos exitosamente.
     *
     * Sprint 10: Auto-aprendizaje de PIDs.
     * Cuando un PID se lee exitosamente, se agrega al bitmap para que
     * en futuras lecturas no sea filtrado como "no detectado".
     *
     * @param successfulPIDs Set de PIDs que se leyeron exitosamente
     */
    private suspend fun updateSupportedPIDsBitmap(successfulPIDs: Set<Int>) {
        try {
            // Obtener vehicleId del dispositivo conectado
            val currentState = bluetoothRepository.connectionState.first()
            val vehicleId = if (currentState is com.fleetcare.obd.domain.model.ConnectionState.Connected) {
                currentState.device.address
            } else {
                Logger.w("🔧 No se puede actualizar bitmap: no hay dispositivo conectado")
                return
            }

            // Cargar bitmap actual
            val currentBitmap = supportedPIDsRepository.getSupportedPIDsSync(vehicleId).getOrNull()

            // Crear o actualizar bitmap
            val updatedBitmap = if (currentBitmap != null) {
                // Agregar nuevos PIDs al bitmap existente
                addPIDsToBitmap(currentBitmap, successfulPIDs)
            } else {
                // Crear nuevo bitmap con los PIDs aprendidos
                val pidRanges = organizePIDsIntoRanges(successfulPIDs)
                com.fleetcare.obd.domain.model.SupportedPIDsBitmap(
                    pidRanges = pidRanges,
                    vehicleId = vehicleId,
                    vin = detectedVIN,
                    detectionTimestamp = System.currentTimeMillis()
                )
            }

            // Guardar bitmap actualizado
            val saveResult = supportedPIDsRepository.saveSupportedPIDs(updatedBitmap)

            if (saveResult.isSuccess) {
                // Actualizar bitmap en memoria
                supportedPIDsBitmap = updatedBitmap

                Logger.i("🎓 Auto-aprendizaje: Bitmap actualizado con ${successfulPIDs.size} PIDs")
                Logger.d("   PIDs aprendidos: ${successfulPIDs.joinToString(", ") { "0x${it.toString(16).uppercase().padStart(2, '0')}" }}")
                Logger.d("   Total PIDs en bitmap: ${updatedBitmap.getTotalSupportedCount()}")
            } else {
                Logger.e("❌ Error al guardar bitmap actualizado: ${saveResult.exceptionOrNull()?.message}")
            }

        } catch (e: Exception) {
            Logger.e(e, "❌ Error en auto-aprendizaje de PIDs")
        }
    }

    /**
     * Agrega nuevos PIDs a un bitmap existente sin duplicar.
     *
     * Sprint 10: Helper para auto-aprendizaje.
     *
     * @param currentBitmap Bitmap actual
     * @param newPIDs Set de PIDs nuevos a agregar
     * @return Bitmap actualizado con los nuevos PIDs
     */
    private fun addPIDsToBitmap(
        currentBitmap: com.fleetcare.obd.domain.model.SupportedPIDsBitmap,
        newPIDs: Set<Int>
    ): com.fleetcare.obd.domain.model.SupportedPIDsBitmap {
        // Combinar PIDs existentes con nuevos
        val allPIDs = currentBitmap.allSupportedPIDs.toMutableSet()
        val addedPIDs = mutableSetOf<Int>()

        newPIDs.forEach { pid ->
            // No agregar PIDs de control (0x00, 0x20, 0x40, etc.)
            if (pid % 0x20 != 0) {
                if (allPIDs.add(pid)) {
                    addedPIDs.add(pid)
                }
            }
        }

        if (addedPIDs.isEmpty()) {
            Logger.d("🔧 No hay PIDs nuevos para agregar al bitmap")
            return currentBitmap
        }

        // Reorganizar todos los PIDs en rangos
        val newPidRanges = organizePIDsIntoRanges(allPIDs)

        // Crear nuevo bitmap con timestamp actualizado
        return currentBitmap.copy(
            pidRanges = newPidRanges,
            detectionTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Organiza PIDs en rangos según el estándar OBD-II.
     *
     * Sprint 10: Helper para crear estructura pidRanges.
     *
     * Rangos OBD-II:
     * - 0x00: Control PID para rango 0x01-0x1F
     * - 0x20: Control PID para rango 0x21-0x3F
     * - 0x40: Control PID para rango 0x41-0x5F
     * - etc.
     *
     * @param pids Set de PIDs a organizar
     * @return Map de rangos (Key: Control PID, Value: Lista de PIDs en ese rango)
     */
    private fun organizePIDsIntoRanges(pids: Set<Int>): Map<Int, List<Int>> {
        val ranges = mutableMapOf<Int, MutableList<Int>>()

        pids.forEach { pid ->
            // Calcular PID de control para este rango
            val controlPID = (pid / 0x20) * 0x20

            // Agregar PID a su rango correspondiente
            if (!ranges.containsKey(controlPID)) {
                ranges[controlPID] = mutableListOf()
            }
            ranges[controlPID]!!.add(pid)
        }

        // Ordenar PIDs dentro de cada rango
        return ranges.mapValues { (_, pidList) ->
            pidList.sorted()
        }
    }

    companion object {
        private const val PREF_KEY_LAST_VIN = "last_detected_vin"
    }

    /**
     * Limpia recursos cuando el repository ya no se usa.
     */
    fun cleanup() {
        stopContinuousReading()
        scope.cancel()
    }
}
