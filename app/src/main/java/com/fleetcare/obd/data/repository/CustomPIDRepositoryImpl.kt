package com.fleetcare.obd.data.repository

import com.fleetcare.obd.data.local.dao.CustomPIDDao
import com.fleetcare.obd.data.local.entity.CustomPIDEntity
import com.fleetcare.obd.domain.model.CustomPID
import com.fleetcare.obd.domain.model.PIDCategory
import com.fleetcare.obd.domain.model.PIDSource
import com.fleetcare.obd.domain.repository.CustomPIDRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * Implementación del repositorio de PIDs personalizados.
 *
 * Sprint 6: Gestión de PIDs Personalizados
 */
class CustomPIDRepositoryImpl @Inject constructor(
    private val customPIDDao: CustomPIDDao,
    private val gson: Gson
) : CustomPIDRepository {

    // ========== CREATE ==========

    override suspend fun saveCustomPID(customPID: CustomPID): Result<Long> {
        return try {
            if (!customPID.isValid()) {
                return Result.failure(IllegalArgumentException("PID personalizado inválido"))
            }

            val entity = CustomPIDEntity.fromDomain(customPID)
            val id = customPIDDao.insertCustomPID(entity)
            Timber.d("PID personalizado guardado: ${customPID.name} (ID: $id)")
            Result.success(id)
        } catch (e: Exception) {
            Timber.e(e, "Error al guardar PID personalizado")
            Result.failure(e)
        }
    }

    override suspend fun saveCustomPIDs(customPIDs: List<CustomPID>): Result<List<Long>> {
        return try {
            val validPIDs = customPIDs.filter { it.isValid() }
            if (validPIDs.isEmpty()) {
                return Result.failure(IllegalArgumentException("No hay PIDs válidos para guardar"))
            }

            val entities = validPIDs.map { CustomPIDEntity.fromDomain(it) }
            val ids = customPIDDao.insertAll(entities)
            Timber.d("${ids.size} PIDs personalizados guardados")
            Result.success(ids)
        } catch (e: Exception) {
            Timber.e(e, "Error al guardar PIDs personalizados")
            Result.failure(e)
        }
    }

    // ========== READ ==========

    override fun getAllCustomPIDs(): Flow<List<CustomPID>> {
        return customPIDDao.getAllCustomPIDs()
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getCustomPIDById(id: Long): Result<CustomPID?> {
        return try {
            val entity = customPIDDao.getCustomPIDById(id)
            Result.success(entity?.toDomain())
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener PID por ID: $id")
            Result.failure(e)
        }
    }

    override suspend fun getCustomPIDByPID(pid: String): Result<CustomPID?> {
        return try {
            val entity = customPIDDao.getCustomPIDByPID(pid)
            Result.success(entity?.toDomain())
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener PID: $pid")
            Result.failure(e)
        }
    }

    override suspend fun getCustomPIDByCommand(command: String): Result<CustomPID?> {
        return try {
            val entity = customPIDDao.getCustomPIDByCommand(command)
            Result.success(entity?.toDomain())
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener PID por comando: $command")
            Result.failure(e)
        }
    }

    override fun getEnabledCustomPIDs(): Flow<List<CustomPID>> {
        return customPIDDao.getEnabledCustomPIDs()
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getCustomPIDsByCategory(category: PIDCategory): Flow<List<CustomPID>> {
        return customPIDDao.getCustomPIDsByCategory(category.name)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getCustomPIDsBySource(source: PIDSource): Flow<List<CustomPID>> {
        return customPIDDao.getCustomPIDsBySource(source.name)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun searchCustomPIDs(query: String): Flow<List<CustomPID>> {
        return customPIDDao.searchCustomPIDs(query)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getCustomPIDsForVehicle(vin: String): Flow<List<CustomPID>> {
        return customPIDDao.getCustomPIDsForVehicle(vin)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getRecentCustomPIDs(): Flow<List<CustomPID>> {
        return customPIDDao.getRecentCustomPIDs()
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getCustomPIDCount(): Result<Int> {
        return try {
            val count = customPIDDao.getCustomPIDCount()
            Result.success(count)
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener conteo de PIDs")
            Result.failure(e)
        }
    }

    override suspend fun getEnabledCustomPIDCount(): Result<Int> {
        return try {
            val count = customPIDDao.getEnabledCustomPIDCount()
            Result.success(count)
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener conteo de PIDs habilitados")
            Result.failure(e)
        }
    }

    // ========== UPDATE ==========

    override suspend fun updateCustomPID(customPID: CustomPID): Result<Unit> {
        return try {
            if (!customPID.isValid()) {
                return Result.failure(IllegalArgumentException("PID personalizado inválido"))
            }

            val entity = CustomPIDEntity.fromDomain(customPID)
            customPIDDao.updateCustomPID(entity)
            Timber.d("PID personalizado actualizado: ${customPID.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error al actualizar PID personalizado")
            Result.failure(e)
        }
    }

    override suspend fun updateLastUsed(id: Long): Result<Unit> {
        return try {
            customPIDDao.updateLastUsed(id, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error al actualizar último uso")
            Result.failure(e)
        }
    }

    override suspend fun updateEnabled(id: Long, isEnabled: Boolean): Result<Unit> {
        return try {
            customPIDDao.updateEnabled(id, isEnabled)
            Timber.d("PID $id ${if (isEnabled) "habilitado" else "deshabilitado"}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error al actualizar estado habilitado")
            Result.failure(e)
        }
    }

    override suspend fun updateConfidence(id: Long, confidence: Float): Result<Unit> {
        return try {
            if (confidence !in 0.0f..1.0f) {
                return Result.failure(IllegalArgumentException("Confianza debe estar entre 0.0 y 1.0"))
            }

            customPIDDao.updateConfidence(id, confidence)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error al actualizar confianza")
            Result.failure(e)
        }
    }

    // ========== DELETE ==========

    override suspend fun deleteCustomPID(customPID: CustomPID): Result<Unit> {
        return try {
            val entity = CustomPIDEntity.fromDomain(customPID)
            customPIDDao.deleteCustomPID(entity)
            Timber.d("PID personalizado eliminado: ${customPID.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error al eliminar PID personalizado")
            Result.failure(e)
        }
    }

    override suspend fun deleteCustomPIDById(id: Long): Result<Unit> {
        return try {
            customPIDDao.deleteCustomPIDById(id)
            Timber.d("PID personalizado eliminado (ID: $id)")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error al eliminar PID por ID")
            Result.failure(e)
        }
    }

    override suspend fun deleteAllCustomPIDs(): Result<Unit> {
        return try {
            customPIDDao.deleteAllCustomPIDs()
            Timber.d("Todos los PIDs personalizados eliminados")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error al eliminar todos los PIDs")
            Result.failure(e)
        }
    }

    override suspend fun deleteCustomPIDsBySource(source: PIDSource): Result<Unit> {
        return try {
            customPIDDao.deleteCustomPIDsBySource(source.name)
            Timber.d("PIDs de origen ${source.name} eliminados")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error al eliminar PIDs por origen")
            Result.failure(e)
        }
    }

    override suspend fun deleteDisabledCustomPIDs(): Result<Unit> {
        return try {
            customPIDDao.deleteDisabledCustomPIDs()
            Timber.d("PIDs deshabilitados eliminados")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error al eliminar PIDs deshabilitados")
            Result.failure(e)
        }
    }

    // ========== IMPORT/EXPORT ==========

    override suspend fun importPIDsFromJSON(json: String): Result<Int> {
        return try {
            val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
            val pidMaps: List<Map<String, Any?>> = gson.fromJson(json, type)

            val customPIDs = pidMaps.mapNotNull { CustomPID.fromJsonMap(it) }

            if (customPIDs.isEmpty()) {
                return Result.failure(IllegalArgumentException("No se encontraron PIDs válidos en el JSON"))
            }

            val result = saveCustomPIDs(customPIDs)
            if (result.isSuccess) {
                val ids = result.getOrNull() ?: emptyList()
                Timber.d("${ids.size} PIDs importados desde JSON")
                Result.success(ids.size)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Error desconocido"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error al importar PIDs desde JSON")
            Result.failure(e)
        }
    }

    override suspend fun exportPIDsToJSON(vins: List<String>): Result<String> {
        return try {
            val entities = if (vins.isEmpty()) {
                // Exportar todos
                customPIDDao.getAllCustomPIDs()
            } else {
                // Exportar solo los compatibles con los VINs especificados
                customPIDDao.getEnabledCustomPIDs()
            }

            // Obtener la primera emisión del Flow
            var allPIDs: List<CustomPID> = emptyList()
            entities.collect { pids ->
                allPIDs = pids.map { it.toDomain() }
            }

            // Filtrar por VINs si se especificaron
            val filteredPIDs = if (vins.isNotEmpty()) {
                allPIDs.filter { pid ->
                    vins.any { vin -> pid.isCompatibleWithVehicle(vin) }
                }
            } else {
                allPIDs
            }

            val pidMaps = filteredPIDs.map { it.toJsonMap() }
            val json = gson.toJson(pidMaps)

            Timber.d("${filteredPIDs.size} PIDs exportados a JSON")
            Result.success(json)
        } catch (e: Exception) {
            Timber.e(e, "Error al exportar PIDs a JSON")
            Result.failure(e)
        }
    }

    override suspend fun exportSinglePIDToJSON(id: Long): Result<String> {
        return try {
            val result = getCustomPIDById(id)
            if (result.isSuccess) {
                val pid = result.getOrNull()
                if (pid != null) {
                    val json = gson.toJson(pid.toJsonMap())
                    Result.success(json)
                } else {
                    Result.failure(Exception("PID no encontrado"))
                }
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Error desconocido"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error al exportar PID individual a JSON")
            Result.failure(e)
        }
    }
}
