package com.fleetcare.obd.data.repository

import com.fleetcare.obd.data.local.dao.DetectionStats
import com.fleetcare.obd.data.local.dao.SupportedPIDsDao
import com.fleetcare.obd.data.local.entity.SupportedPIDsEntity
import com.fleetcare.obd.domain.model.SupportedPIDsBitmap
import com.fleetcare.obd.domain.repository.SupportedPIDsRepository
import com.fleetcare.obd.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación del Repository para PIDs soportados.
 *
 * Gestiona la persistencia de PIDs detectados usando Room Database.
 * Incluye mapeo entre entidades Room y modelos de dominio, con
 * serialización JSON para el mapa de rangos.
 *
 * Sprint 2: Detección y caché de PIDs soportados
 */
@Singleton
class SupportedPIDsRepositoryImpl @Inject constructor(
    private val supportedPIDsDao: SupportedPIDsDao
) : SupportedPIDsRepository {

    override suspend fun saveSupportedPIDs(supportedPIDs: SupportedPIDsBitmap): Result<Long> {
        return try {
            val entity = supportedPIDs.toEntity()
            val id = supportedPIDsDao.insertOrUpdate(entity)
            Logger.d("PIDs soportados guardados para vehicleId: ${supportedPIDs.vehicleId}, total: ${supportedPIDs.getTotalSupportedCount()}")
            Result.success(id)
        } catch (e: Exception) {
            Logger.e(e, "Error al guardar PIDs soportados")
            Result.failure(e)
        }
    }

    override fun getSupportedPIDs(vehicleId: String): Flow<SupportedPIDsBitmap?> {
        return supportedPIDsDao.getSupportedPIDsByVehicleId(vehicleId)
            .map { entity -> entity?.toDomain() }
    }

    override suspend fun getSupportedPIDsSync(vehicleId: String): Result<SupportedPIDsBitmap?> {
        return try {
            val entity = supportedPIDsDao.getSupportedPIDsByVehicleIdSync(vehicleId)
            val bitmap = entity?.toDomain()
            Result.success(bitmap)
        } catch (e: Exception) {
            Logger.e(e, "Error al obtener PIDs soportados")
            Result.failure(e)
        }
    }

    override fun getSupportedPIDsByVIN(vin: String): Flow<SupportedPIDsBitmap?> {
        return supportedPIDsDao.getSupportedPIDsByVIN(vin)
            .map { entity -> entity?.toDomain() }
    }

    override suspend fun hasCachedPIDs(vehicleId: String): Result<Boolean> {
        return try {
            val hasPIDs = supportedPIDsDao.hasCachedPIDs(vehicleId)
            Result.success(hasPIDs)
        } catch (e: Exception) {
            Logger.e(e, "Error al verificar caché de PIDs")
            Result.failure(e)
        }
    }

    override suspend fun needsRefresh(vehicleId: String, maxAgeDays: Int): Result<Boolean> {
        return try {
            val ageThresholdMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(maxAgeDays.toLong())
            val needs = supportedPIDsDao.needsRefresh(vehicleId, ageThresholdMs)
            Result.success(needs)
        } catch (e: Exception) {
            Logger.e(e, "Error al verificar necesidad de actualización")
            Result.failure(e)
        }
    }

    override suspend fun updateVIN(vehicleId: String, vin: String): Result<Unit> {
        return try {
            supportedPIDsDao.updateVIN(vehicleId, vin)
            Logger.d("VIN actualizado para vehicleId: $vehicleId")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(e, "Error al actualizar VIN")
            Result.failure(e)
        }
    }

    override suspend fun deleteSupportedPIDs(vehicleId: String): Result<Int> {
        return try {
            val deleted = supportedPIDsDao.deleteSupportedPIDs(vehicleId)
            Logger.d("PIDs eliminados para vehicleId: $vehicleId")
            Result.success(deleted)
        } catch (e: Exception) {
            Logger.e(e, "Error al eliminar PIDs soportados")
            Result.failure(e)
        }
    }

    override suspend fun deleteOldCaches(maxAgeDays: Int): Result<Int> {
        return try {
            val cutoffTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(maxAgeDays.toLong())
            val deleted = supportedPIDsDao.deleteOlderThan(cutoffTimestamp)
            Logger.d("Cachés antiguos eliminados: $deleted registros")
            Result.success(deleted)
        } catch (e: Exception) {
            Logger.e(e, "Error al eliminar cachés antiguos")
            Result.failure(e)
        }
    }

    override fun getAllSupportedPIDs(): Flow<List<SupportedPIDsBitmap>> {
        return supportedPIDsDao.getAllSupportedPIDs()
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getDetectionStats(): Result<DetectionStats?> {
        return try {
            val stats = supportedPIDsDao.getDetectionStats()
            Result.success(stats)
        } catch (e: Exception) {
            Logger.e(e, "Error al obtener estadísticas de detección")
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
private fun SupportedPIDsBitmap.toEntity(): SupportedPIDsEntity {
    // Serializar el mapa de rangos a JSON
    val jsonObject = JSONObject()
    pidRanges.forEach { (controlPID, pidList) ->
        val jsonArray = JSONArray()
        pidList.forEach { pid -> jsonArray.put(pid) }
        jsonObject.put(controlPID.toString(), jsonArray)
    }

    return SupportedPIDsEntity(
        vehicleId = vehicleId,
        vin = vin,
        pidRangesJson = jsonObject.toString(),
        detectionTimestamp = detectionTimestamp,
        totalPIDsCount = getTotalSupportedCount()
    )
}

/**
 * Convierte entidad Room a modelo de dominio.
 */
private fun SupportedPIDsEntity.toDomain(): SupportedPIDsBitmap {
    // Deserializar JSON a mapa de rangos
    val pidRangesMap = mutableMapOf<Int, List<Int>>()

    try {
        val jsonObject = JSONObject(pidRangesJson)
        val keys = jsonObject.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val controlPID = key.toInt()
            val jsonArray = jsonObject.getJSONArray(key)
            val pidList = mutableListOf<Int>()

            for (i in 0 until jsonArray.length()) {
                pidList.add(jsonArray.getInt(i))
            }

            pidRangesMap[controlPID] = pidList
        }
    } catch (e: Exception) {
        Logger.e(e, "Error al parsear JSON de PIDs soportados")
        // Retornar mapa vacío en caso de error
    }

    return SupportedPIDsBitmap(
        pidRanges = pidRangesMap,
        vehicleId = vehicleId,
        vin = vin,
        detectionTimestamp = detectionTimestamp
    )
}
