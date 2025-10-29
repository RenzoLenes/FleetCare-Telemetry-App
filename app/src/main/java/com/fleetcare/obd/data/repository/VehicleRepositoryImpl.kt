package com.fleetcare.obd.data.repository

import com.fleetcare.obd.data.local.dao.VehicleDataDao
import com.fleetcare.obd.data.mapper.VehicleDataMapper
import com.fleetcare.obd.domain.model.VehicleData
import com.fleetcare.obd.domain.repository.BluetoothRepository
import com.fleetcare.obd.domain.repository.VehicleRepository
import com.fleetcare.obd.utils.Constants
import com.fleetcare.obd.utils.Logger
import com.fleetcare.obd.utils.obd.OBDCommandParser
import com.fleetcare.obd.utils.obd.PIDConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación del VehicleRepository.
 *
 * Coordina la lectura continua de datos OBDII, el parsing de respuestas,
 * y el almacenamiento en caché local.
 */
@Singleton
class VehicleRepositoryImpl @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
    private val vehicleDataDao: VehicleDataDao
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

        // Leer cada PID secuencialmente
        for (pid in PIDConstants.BASIC_PIDS) {
            try {
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
                }

                // Pequeño delay entre comandos para no saturar el adaptador
                delay(Constants.OBD.COMMAND_DELAY_MS)

            } catch (e: Exception) {
                Logger.obdError("Error al leer ${pid.name}", e)
            }
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
                    Logger.obdError("Error en respuesta: $errorMsg")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                // Parsear respuesta
                val value = OBDCommandParser.parseResponse(command, response)

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

    /**
     * Limpia recursos cuando el repository ya no se usa.
     */
    fun cleanup() {
        stopContinuousReading()
        scope.cancel()
    }
}
