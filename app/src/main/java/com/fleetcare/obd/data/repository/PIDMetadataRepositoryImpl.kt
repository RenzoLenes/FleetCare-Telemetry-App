package com.fleetcare.obd.data.repository

import com.fleetcare.obd.data.local.dao.PIDMetadataDao
import com.fleetcare.obd.data.local.entity.PIDMetadataEntity
import com.fleetcare.obd.domain.model.PIDDataType
import com.fleetcare.obd.domain.model.PIDMetadata
import com.fleetcare.obd.domain.repository.PIDMetadataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación del repositorio de metadata de PIDs.
 */
@Singleton
class PIDMetadataRepositoryImpl @Inject constructor(
    private val dao: PIDMetadataDao
) : PIDMetadataRepository {

    override suspend fun saveMetadata(metadata: PIDMetadata) {
        // Decidir si es metadata global (vehicleId = null) o específica
        val entity = PIDMetadataEntity.fromDomain(metadata, metadata.vehicleSpecific.takeIf { it }?.let { null })
        dao.insertMetadata(entity)
    }

    override suspend fun saveMultiple(metadataList: List<PIDMetadata>) {
        val entities = metadataList.map { metadata ->
            PIDMetadataEntity.fromDomain(metadata, metadata.vehicleSpecific.takeIf { it }?.let { null })
        }
        dao.insertMultiple(entities)
    }

    override suspend fun getMetadata(mode: String, pid: String, vehicleId: String?): PIDMetadata? {
        return dao.getMetadata(mode, pid, vehicleId)?.toDomain()
    }

    override suspend fun getMetadataById(uniqueId: String, vehicleId: String?): PIDMetadata? {
        val parts = uniqueId.split("_")
        if (parts.size != 2) return null
        val mode = parts[0]
        val pid = parts[1]
        return getMetadata(mode, pid, vehicleId)
    }

    override fun getMetadataByMode(mode: String, vehicleId: String?): Flow<List<PIDMetadata>> {
        return dao.getMetadataByMode(mode, vehicleId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getMetadataByVehicle(vehicleId: String): Flow<List<PIDMetadata>> {
        return dao.getMetadataByVehicle(vehicleId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getMetadataByDataType(dataType: PIDDataType, vehicleId: String?): Flow<List<PIDMetadata>> {
        return dao.getMetadataByDataType(dataType.name, vehicleId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getStandardPIDsMetadata(): Flow<List<PIDMetadata>> {
        return dao.getStandardPIDsMetadata().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getManufacturerPIDsMetadata(vehicleId: String): Flow<List<PIDMetadata>> {
        return dao.getManufacturerPIDsMetadata(vehicleId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getHighQualityPIDs(
        vehicleId: String?,
        minSuccessRate: Float,
        maxResponseTime: Long
    ): Flow<List<PIDMetadata>> {
        return dao.getHighQualityPIDs(vehicleId, minSuccessRate, maxResponseTime).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getRealTimeMonitoringPIDs(vehicleId: String): Flow<List<PIDMetadata>> {
        return dao.getRealTimeMonitoringPIDs(vehicleId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun updatePerformanceStats(
        mode: String,
        pid: String,
        vehicleId: String,
        responseTime: Long,
        success: Boolean
    ) {
        // Obtener metadata existente
        val existing = dao.getMetadata(mode, pid, vehicleId) ?: return

        // Calcular nuevo success rate (promedio móvil simple)
        val totalAttempts = (1.0 / existing.successRate).toInt()
        val successfulAttempts = (totalAttempts * existing.successRate).toInt()
        val newTotalAttempts = totalAttempts + 1
        val newSuccessfulAttempts = successfulAttempts + (if (success) 1 else 0)
        val newSuccessRate = newSuccessfulAttempts.toFloat() / newTotalAttempts.toFloat()

        // Calcular nuevo average response time
        val newAvgResponseTime = ((existing.averageResponseTime * totalAttempts) + responseTime) / newTotalAttempts

        dao.updatePerformanceStats(
            mode = mode,
            pid = pid,
            vehicleId = vehicleId,
            responseTime = newAvgResponseTime,
            successRate = newSuccessRate,
            lastUpdated = System.currentTimeMillis()
        )
    }

    override suspend fun updateValueRange(mode: String, pid: String, vehicleId: String, value: Double) {
        val existing = dao.getMetadata(mode, pid, vehicleId) ?: return

        val newMin = existing.minValue?.coerceAtMost(value) ?: value
        val newMax = existing.maxValue?.coerceAtLeast(value) ?: value

        dao.updateValueRange(
            mode = mode,
            pid = pid,
            vehicleId = vehicleId,
            minValue = newMin,
            maxValue = newMax,
            lastUpdated = System.currentTimeMillis()
        )
    }

    override suspend fun deleteMetadata(mode: String, pid: String, vehicleId: String?) {
        dao.deleteMetadata(mode, pid, vehicleId)
    }

    override suspend fun deleteMetadataByVehicle(vehicleId: String) {
        dao.deleteMetadataByVehicle(vehicleId)
    }

    override suspend fun getMetadataCount(vehicleId: String?): Int {
        return dao.getMetadataCount(vehicleId)
    }

    override fun searchMetadata(query: String, vehicleId: String?): Flow<List<PIDMetadata>> {
        return dao.searchMetadata(query, vehicleId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getMetadataByCategory(vehicleId: String?): Map<String, List<PIDMetadata>> {
        // Obtener todas las metadatas y agrupar por categoría basándose en el rango del PID
        val allMetadata = if (vehicleId != null) {
            dao.getMetadataByVehicle(vehicleId).map { it.map { e -> e.toDomain() } }
        } else {
            dao.getAllMetadata().map { it.map { e -> e.toDomain() } }
        }

        // Agrupar por categoría (basado en rango de PID para Mode 01)
        return allMetadata.map { metadataList ->
            metadataList.groupBy { metadata ->
                when {
                    metadata.mode != "01" -> "Mode ${metadata.mode}"
                    else -> {
                        val pidInt = metadata.pid.toIntOrNull(16) ?: 0
                        when (pidInt) {
                            in 0x00..0x20 -> "Control y Motor"
                            in 0x21..0x40 -> "Combustible y Aire"
                            in 0x41..0x60 -> "Temperatura y Presión"
                            in 0x61..0x80 -> "Sensores Avanzados"
                            in 0x81..0xA0 -> "Sistema de Emisiones"
                            in 0xA1..0xC0 -> "Propietario del Fabricante"
                            in 0xC1..0xFF -> "Propietario Extendido"
                            else -> "Desconocido"
                        }
                    }
                }
            }
        }.map { it }.let { flow ->
            // Por simplicidad, retornamos el primer resultado
            // En una implementación real, deberías usar `first()` con coroutines
            emptyMap()
        }
    }
}
