package com.fleetcare.obd.data.local.dao

import androidx.room.*
import com.fleetcare.obd.data.local.entity.SupportedPIDsEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para acceso a datos de PIDs soportados.
 *
 * Gestiona el caché de PIDs detectados por vehículo para evitar
 * ejecutar la detección completa cada vez que se conecta.
 *
 * Sprint 2: Persistencia de PIDs soportados
 */
@Dao
interface SupportedPIDsDao {

    /**
     * Inserta o actualiza los PIDs soportados de un vehículo.
     *
     * Si el vehicleId ya existe, reemplaza el registro completo.
     *
     * @param entity Entidad con PIDs soportados
     * @return ID del registro insertado/actualizado
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: SupportedPIDsEntity): Long

    /**
     * Obtiene los PIDs soportados de un vehículo por su ID.
     *
     * @param vehicleId MAC del adaptador Bluetooth
     * @return Flow con la entidad o null si no existe
     */
    @Query("SELECT * FROM supported_pids WHERE vehicleId = :vehicleId LIMIT 1")
    fun getSupportedPIDsByVehicleId(vehicleId: String): Flow<SupportedPIDsEntity?>

    /**
     * Obtiene los PIDs soportados de un vehículo de forma síncrona.
     *
     * @param vehicleId MAC del adaptador Bluetooth
     * @return Entidad o null si no existe
     */
    @Query("SELECT * FROM supported_pids WHERE vehicleId = :vehicleId LIMIT 1")
    suspend fun getSupportedPIDsByVehicleIdSync(vehicleId: String): SupportedPIDsEntity?

    /**
     * Obtiene los PIDs soportados por VIN.
     *
     * @param vin VIN del vehículo
     * @return Flow con la entidad o null si no existe
     */
    @Query("SELECT * FROM supported_pids WHERE vin = :vin LIMIT 1")
    fun getSupportedPIDsByVIN(vin: String): Flow<SupportedPIDsEntity?>

    /**
     * Verifica si existen PIDs almacenados para un vehículo.
     *
     * @param vehicleId MAC del adaptador
     * @return true si existe caché para este vehículo
     */
    @Query("SELECT COUNT(*) > 0 FROM supported_pids WHERE vehicleId = :vehicleId")
    suspend fun hasCachedPIDs(vehicleId: String): Boolean

    /**
     * Obtiene todos los vehículos con PIDs detectados.
     *
     * @return Flow con lista de todas las entidades
     */
    @Query("SELECT * FROM supported_pids ORDER BY detectionTimestamp DESC")
    fun getAllSupportedPIDs(): Flow<List<SupportedPIDsEntity>>

    /**
     * Obtiene el timestamp de la última detección para un vehículo.
     *
     * @param vehicleId MAC del adaptador
     * @return Timestamp o null si no existe
     */
    @Query("SELECT detectionTimestamp FROM supported_pids WHERE vehicleId = :vehicleId LIMIT 1")
    suspend fun getLastDetectionTimestamp(vehicleId: String): Long?

    /**
     * Verifica si el caché necesita actualización.
     *
     * Se considera que necesita actualización si:
     * - No existe caché
     * - El caché tiene más de X días (parámetro ageThreshold)
     *
     * @param vehicleId MAC del adaptador
     * @param ageThresholdMs Edad máxima en milisegundos (ej: 30 días)
     * @return true si necesita re-detección
     */
    @Query("""
        SELECT (
            SELECT COUNT(*) FROM supported_pids
            WHERE vehicleId = :vehicleId
            AND detectionTimestamp > :ageThresholdMs
        ) = 0
    """)
    suspend fun needsRefresh(vehicleId: String, ageThresholdMs: Long): Boolean

    /**
     * Actualiza el VIN de un registro existente.
     *
     * @param vehicleId MAC del adaptador
     * @param vin Nuevo VIN
     */
    @Query("UPDATE supported_pids SET vin = :vin WHERE vehicleId = :vehicleId")
    suspend fun updateVIN(vehicleId: String, vin: String)

    /**
     * Elimina el caché de PIDs de un vehículo.
     *
     * @param vehicleId MAC del adaptador
     * @return Número de registros eliminados
     */
    @Query("DELETE FROM supported_pids WHERE vehicleId = :vehicleId")
    suspend fun deleteSupportedPIDs(vehicleId: String): Int

    /**
     * Elimina todos los cachés de PIDs más antiguos que un timestamp.
     *
     * @param timestamp Timestamp de corte
     * @return Número de registros eliminados
     */
    @Query("DELETE FROM supported_pids WHERE detectionTimestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    /**
     * Elimina todos los cachés de PIDs.
     *
     * @return Número de registros eliminados
     */
    @Query("DELETE FROM supported_pids")
    suspend fun deleteAll(): Int

    /**
     * Obtiene el número total de vehículos con PIDs detectados.
     *
     * @return Conteo de vehículos
     */
    @Query("SELECT COUNT(*) FROM supported_pids")
    suspend fun getVehicleCount(): Int

    /**
     * Obtiene estadísticas de detección.
     *
     * @return Estadísticas agregadas
     */
    @Query("""
        SELECT
            COUNT(*) as vehicleCount,
            AVG(totalPIDsCount) as avgPIDsPerVehicle,
            MAX(totalPIDsCount) as maxPIDsDetected,
            MIN(totalPIDsCount) as minPIDsDetected
        FROM supported_pids
    """)
    suspend fun getDetectionStats(): DetectionStats?
}

/**
 * Clase de datos para estadísticas de detección.
 */
data class DetectionStats(
    val vehicleCount: Int,
    val avgPIDsPerVehicle: Double,
    val maxPIDsDetected: Int,
    val minPIDsDetected: Int
)
