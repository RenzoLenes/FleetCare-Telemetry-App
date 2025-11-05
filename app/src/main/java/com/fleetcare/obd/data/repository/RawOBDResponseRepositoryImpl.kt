package com.fleetcare.obd.data.repository

import com.fleetcare.obd.data.local.dao.RawOBDResponseDao
import com.fleetcare.obd.data.local.dao.RawResponseTableStats
import com.fleetcare.obd.data.local.dao.SuccessFailCount
import com.fleetcare.obd.data.local.dao.VehicleRecordCount
import com.fleetcare.obd.data.local.entity.RawOBDResponseEntity
import com.fleetcare.obd.domain.model.RawOBDResponse
import com.fleetcare.obd.domain.repository.RawOBDResponseRepository
import com.fleetcare.obd.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación del Repository para respuestas RAW de OBD-II.
 *
 * Gestiona la persistencia de respuestas sin procesar usando Room Database.
 * Incluye mapeo entre entidades Room y modelos de dominio.
 *
 * Sprint 1: Captura y almacenamiento de respuestas RAW
 */
@Singleton
class RawOBDResponseRepositoryImpl @Inject constructor(
    private val rawOBDResponseDao: RawOBDResponseDao
) : RawOBDResponseRepository {

    override suspend fun saveRawResponse(response: RawOBDResponse): Result<Long> {
        return try {
            val entity = response.toEntity()
            val id = rawOBDResponseDao.insertRawResponse(entity)
            Result.success(id)
        } catch (e: Exception) {
            Logger.e(e, "Error al guardar respuesta RAW")
            Result.failure(e)
        }
    }

    override suspend fun saveMultipleResponses(responses: List<RawOBDResponse>): Result<Unit> {
        return try {
            val entities = responses.map { it.toEntity() }
            rawOBDResponseDao.insertAll(entities)
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(e, "Error al guardar múltiples respuestas RAW")
            Result.failure(e)
        }
    }

    override fun getResponsesForCommand(command: String): Flow<List<RawOBDResponse>> {
        return rawOBDResponseDao.getRawResponsesForCommand(command)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getResponsesInTimeRange(
        startTime: Long,
        endTime: Long,
        command: String?
    ): Flow<List<RawOBDResponse>> {
        return rawOBDResponseDao.getRawResponsesInTimeRange(startTime, endTime, command)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getResponsesBySession(sessionId: String): Result<List<RawOBDResponse>> {
        return try {
            val entities = rawOBDResponseDao.getResponsesBySession(sessionId)
            val responses = entities.map { it.toDomain() }
            Result.success(responses)
        } catch (e: Exception) {
            Logger.e(e, "Error al obtener respuestas por sesión")
            Result.failure(e)
        }
    }

    override fun getAllCommands(): Flow<List<String>> {
        return rawOBDResponseDao.getAllCommandsWithResponses()
    }

    override fun getCommandsForVehicle(vehicleId: String): Flow<List<String>> {
        return rawOBDResponseDao.getCommandsForVehicle(vehicleId)
    }

    override suspend fun getSuccessFailCount(command: String): Result<SuccessFailCount> {
        return try {
            val count = rawOBDResponseDao.getSuccessFailCountForCommand(command)
            Result.success(count)
        } catch (e: Exception) {
            Logger.e(e, "Error al obtener conteo éxito/fallo")
            Result.failure(e)
        }
    }

    override suspend fun getLatestResponses(command: String, limit: Int): Result<List<RawOBDResponse>> {
        return try {
            val entities = rawOBDResponseDao.getLatestResponsesForCommand(command, limit)
            val responses = entities.map { it.toDomain() }
            Result.success(responses)
        } catch (e: Exception) {
            Logger.e(e, "Error al obtener últimas respuestas")
            Result.failure(e)
        }
    }

    override suspend fun getSuccessfulResponsesInRange(
        command: String,
        startTime: Long,
        endTime: Long
    ): Result<List<RawOBDResponse>> {
        return try {
            val entities = rawOBDResponseDao.getSuccessfulResponsesInRange(command, startTime, endTime)
            val responses = entities.map { it.toDomain() }
            Result.success(responses)
        } catch (e: Exception) {
            Logger.e(e, "Error al obtener respuestas exitosas en rango")
            Result.failure(e)
        }
    }

    override suspend fun deleteOlderThan(timestamp: Long): Result<Int> {
        return try {
            val deleted = rawOBDResponseDao.deleteOlderThan(timestamp)
            Logger.d("Eliminadas $deleted respuestas RAW antiguas")
            Result.success(deleted)
        } catch (e: Exception) {
            Logger.e(e, "Error al eliminar respuestas antiguas")
            Result.failure(e)
        }
    }

    override suspend fun deleteByVehicleId(vehicleId: String): Result<Int> {
        return try {
            val deleted = rawOBDResponseDao.deleteByVehicleId(vehicleId)
            Logger.d("Eliminadas $deleted respuestas RAW del vehículo $vehicleId")
            Result.success(deleted)
        } catch (e: Exception) {
            Logger.e(e, "Error al eliminar respuestas por vehículo")
            Result.failure(e)
        }
    }

    override suspend fun deleteAll(): Result<Int> {
        return try {
            val deleted = rawOBDResponseDao.deleteAll()
            Logger.w("Eliminadas TODAS las respuestas RAW ($deleted registros)")
            Result.success(deleted)
        } catch (e: Exception) {
            Logger.e(e, "Error al eliminar todas las respuestas")
            Result.failure(e)
        }
    }

    override suspend fun getTableStats(): Result<RawResponseTableStats> {
        return try {
            val stats = rawOBDResponseDao.getTableStats()
            Result.success(stats)
        } catch (e: Exception) {
            Logger.e(e, "Error al obtener estadísticas de tabla")
            Result.failure(e)
        }
    }

    override fun getRecordCountByVehicle(): Flow<List<VehicleRecordCount>> {
        return rawOBDResponseDao.getRecordCountByVehicle()
    }

    override suspend fun getEstimatedStorageSize(): Result<Long> {
        return try {
            val size = rawOBDResponseDao.getEstimatedStorageSize() ?: 0L
            Result.success(size)
        } catch (e: Exception) {
            Logger.e(e, "Error al calcular tamaño de almacenamiento")
            Result.failure(e)
        }
    }

    override suspend fun getRecordCount(): Result<Int> {
        return try {
            val count = rawOBDResponseDao.getRecordCount()
            Result.success(count)
        } catch (e: Exception) {
            Logger.e(e, "Error al contar registros")
            Result.failure(e)
        }
    }
}

/**
 * Funciones de extensión para mapeo entre entidades y modelos de dominio.
 */

/**
 * Convierte modelo de dominio a entidad Room.
 */
private fun RawOBDResponse.toEntity(): RawOBDResponseEntity {
    return RawOBDResponseEntity(
        id = id,
        timestamp = timestamp,
        vehicleId = vehicleId,
        sessionId = sessionId,
        command = command,
        rawResponse = rawResponse,
        cleanResponse = cleanResponse,
        dataBytesHex = dataBytes.joinToString(",") { it.toString(16).uppercase().padStart(2, '0') },
        parsedValue = parsedValue,
        parseSuccess = parseSuccess,
        errorMessage = errorMessage,
        latencyMs = latencyMs,
        attemptNumber = attemptNumber,
        protocolUsed = protocolUsed
    )
}

/**
 * Convierte entidad Room a modelo de dominio.
 */
private fun RawOBDResponseEntity.toDomain(): RawOBDResponse {
    // Parsear dataBytesHex de vuelta a ByteArray
    val bytes = if (dataBytesHex.isNotEmpty()) {
        dataBytesHex.split(",")
            .map { it.toInt(16).toByte() }
            .toByteArray()
    } else {
        ByteArray(0)
    }

    return RawOBDResponse(
        id = id,
        timestamp = timestamp,
        vehicleId = vehicleId,
        sessionId = sessionId,
        command = command,
        rawResponse = rawResponse,
        cleanResponse = cleanResponse,
        dataBytes = bytes,
        parsedValue = parsedValue,
        parseSuccess = parseSuccess,
        errorMessage = errorMessage,
        latencyMs = latencyMs,
        attemptNumber = attemptNumber,
        protocolUsed = protocolUsed
    )
}
