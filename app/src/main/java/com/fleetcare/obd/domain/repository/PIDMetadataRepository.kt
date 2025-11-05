package com.fleetcare.obd.domain.repository

import com.fleetcare.obd.domain.model.PIDDataType
import com.fleetcare.obd.domain.model.PIDMetadata
import kotlinx.coroutines.flow.Flow

/**
 * Repository para gestión de metadata de PIDs.
 *
 * Almacena y recupera información aprendida sobre PIDs: tipos de datos,
 * formulas, nombres, estadísticas de rendimiento, etc.
 */
interface PIDMetadataRepository {

    /**
     * Guarda o actualiza metadata de un PID.
     *
     * @param metadata Metadata a guardar
     */
    suspend fun saveMetadata(metadata: PIDMetadata)

    /**
     * Guarda múltiples metadatas de PIDs.
     *
     * @param metadataList Lista de metadatas a guardar
     */
    suspend fun saveMultiple(metadataList: List<PIDMetadata>)

    /**
     * Obtiene metadata de un PID específico.
     *
     * @param mode Modo OBD
     * @param pid PID
     * @param vehicleId ID del vehículo (opcional, para metadata específica del vehículo)
     * @return Metadata del PID o null si no existe
     */
    suspend fun getMetadata(mode: String, pid: String, vehicleId: String? = null): PIDMetadata?

    /**
     * Obtiene metadata de un PID por su ID único.
     *
     * @param uniqueId ID único del PID (formato: "MODE_PID")
     * @param vehicleId ID del vehículo (opcional)
     * @return Metadata del PID o null si no existe
     */
    suspend fun getMetadataById(uniqueId: String, vehicleId: String? = null): PIDMetadata?

    /**
     * Obtiene todas las metadatas de un modo específico.
     *
     * @param mode Modo OBD
     * @param vehicleId ID del vehículo (opcional)
     * @return Flow de metadatas del modo
     */
    fun getMetadataByMode(mode: String, vehicleId: String? = null): Flow<List<PIDMetadata>>

    /**
     * Obtiene todas las metadatas de un vehículo.
     *
     * @param vehicleId ID del vehículo
     * @return Flow de metadatas del vehículo
     */
    fun getMetadataByVehicle(vehicleId: String): Flow<List<PIDMetadata>>

    /**
     * Obtiene metadatas filtradas por tipo de dato.
     *
     * @param dataType Tipo de dato
     * @param vehicleId ID del vehículo (opcional)
     * @return Flow de metadatas del tipo especificado
     */
    fun getMetadataByDataType(dataType: PIDDataType, vehicleId: String? = null): Flow<List<PIDMetadata>>

    /**
     * Obtiene solo PIDs estándar (no manufacturer-specific).
     *
     * @return Flow de metadatas de PIDs estándar
     */
    fun getStandardPIDsMetadata(): Flow<List<PIDMetadata>>

    /**
     * Obtiene solo PIDs manufacturer-specific.
     *
     * @param vehicleId ID del vehículo
     * @return Flow de metadatas de PIDs manufacturer
     */
    fun getManufacturerPIDsMetadata(vehicleId: String): Flow<List<PIDMetadata>>

    /**
     * Obtiene PIDs de alta calidad (alto success rate, bajo response time).
     *
     * @param vehicleId ID del vehículo (opcional)
     * @param minSuccessRate Tasa mínima de éxito (default 0.8)
     * @param maxResponseTime Tiempo máximo de respuesta en ms (default 500)
     * @return Flow de metadatas de PIDs de alta calidad
     */
    fun getHighQualityPIDs(
        vehicleId: String? = null,
        minSuccessRate: Float = 0.8f,
        maxResponseTime: Long = 500L
    ): Flow<List<PIDMetadata>>

    /**
     * Obtiene PIDs aptos para monitoreo en tiempo real.
     *
     * @param vehicleId ID del vehículo
     * @return Flow de metadatas de PIDs aptos para tiempo real
     */
    fun getRealTimeMonitoringPIDs(vehicleId: String): Flow<List<PIDMetadata>>

    /**
     * Actualiza estadísticas de rendimiento de un PID.
     *
     * @param mode Modo OBD
     * @param pid PID
     * @param vehicleId ID del vehículo
     * @param responseTime Tiempo de respuesta
     * @param success Si la consulta fue exitosa
     */
    suspend fun updatePerformanceStats(
        mode: String,
        pid: String,
        vehicleId: String,
        responseTime: Long,
        success: Boolean
    )

    /**
     * Actualiza rango de valores observados (min/max).
     *
     * @param mode Modo OBD
     * @param pid PID
     * @param vehicleId ID del vehículo
     * @param value Valor observado
     */
    suspend fun updateValueRange(
        mode: String,
        pid: String,
        vehicleId: String,
        value: Double
    )

    /**
     * Elimina metadata de un PID.
     *
     * @param mode Modo OBD
     * @param pid PID
     * @param vehicleId ID del vehículo (null = eliminar metadata global)
     */
    suspend fun deleteMetadata(mode: String, pid: String, vehicleId: String? = null)

    /**
     * Elimina todas las metadatas de un vehículo.
     *
     * @param vehicleId ID del vehículo
     */
    suspend fun deleteMetadataByVehicle(vehicleId: String)

    /**
     * Obtiene el número total de PIDs con metadata.
     *
     * @param vehicleId ID del vehículo (opcional)
     * @return Número de PIDs con metadata
     */
    suspend fun getMetadataCount(vehicleId: String? = null): Int

    /**
     * Busca PIDs por nombre o descripción.
     *
     * @param query Texto a buscar
     * @param vehicleId ID del vehículo (opcional)
     * @return Flow de metadatas que coinciden con la búsqueda
     */
    fun searchMetadata(query: String, vehicleId: String? = null): Flow<List<PIDMetadata>>

    /**
     * Obtiene PIDs agrupados por categoría/rango.
     *
     * @param vehicleId ID del vehículo (opcional)
     * @return Mapa de categoría a lista de metadatas
     */
    suspend fun getMetadataByCategory(vehicleId: String? = null): Map<String, List<PIDMetadata>>
}
