package com.fleetcare.obd.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fleetcare.obd.data.local.entity.RawOBDResponseEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) para RawOBDResponseEntity.
 *
 * Define operaciones de base de datos para respuestas RAW de OBD-II.
 * Optimizado para consultas frecuentes de análisis de patrones.
 */
@Dao
interface RawOBDResponseDao {

    /**
     * Inserta una nueva respuesta RAW.
     * Si hay conflicto (mismo ID), reemplaza el registro existente.
     *
     * @return ID del registro insertado
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawResponse(response: RawOBDResponseEntity): Long

    /**
     * Inserta múltiples respuestas en una sola transacción (batch insert).
     * Útil para importar datos históricos.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(responses: List<RawOBDResponseEntity>)

    /**
     * Obtiene todas las respuestas para un comando específico.
     * Ordenadas por timestamp descendente (más recientes primero).
     *
     * Caso de uso: Analizar histórico de un PID específico (ej: "010C" para RPM)
     *
     * @param command Comando OBD (ej: "010C")
     * @return Flow que emite lista de respuestas cuando hay cambios
     */
    @Query("SELECT * FROM raw_obd_responses WHERE command = :command ORDER BY timestamp DESC")
    fun getRawResponsesForCommand(command: String): Flow<List<RawOBDResponseEntity>>

    /**
     * Obtiene respuestas en un rango de tiempo específico.
     * Útil para análisis de sesiones o periodos concretos.
     *
     * @param startTime Timestamp de inicio (milisegundos)
     * @param endTime Timestamp de fin (milisegundos)
     * @param command Comando opcional para filtrar (null = todos)
     * @return Flow con respuestas en el rango
     */
    @Query("""
        SELECT * FROM raw_obd_responses
        WHERE timestamp BETWEEN :startTime AND :endTime
        AND (:command IS NULL OR command = :command)
        ORDER BY timestamp ASC
    """)
    fun getRawResponsesInTimeRange(
        startTime: Long,
        endTime: Long,
        command: String? = null
    ): Flow<List<RawOBDResponseEntity>>

    /**
     * Obtiene todas las respuestas de una sesión específica.
     * Útil para análisis de sesión completa.
     *
     * @param sessionId ID de sesión (UUID)
     * @return Lista de respuestas de la sesión
     */
    @Query("SELECT * FROM raw_obd_responses WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getResponsesBySession(sessionId: String): List<RawOBDResponseEntity>

    /**
     * Obtiene lista de comandos únicos que han sido ejecutados.
     * Útil para UI de selector de PIDs analizables.
     *
     * @return Flow con lista de comandos únicos
     */
    @Query("SELECT DISTINCT command FROM raw_obd_responses ORDER BY command ASC")
    fun getAllCommandsWithResponses(): Flow<List<String>>

    /**
     * Obtiene lista de comandos únicos para un vehículo específico.
     *
     * @param vehicleId MAC address del adaptador
     * @return Flow con comandos ejecutados en ese vehículo
     */
    @Query("SELECT DISTINCT command FROM raw_obd_responses WHERE vehicleId = :vehicleId ORDER BY command ASC")
    fun getCommandsForVehicle(vehicleId: String): Flow<List<String>>

    /**
     * Cuenta respuestas exitosas vs fallidas para un comando.
     * Útil para estadísticas de confiabilidad de PIDs.
     *
     * @param command Comando OBD
     * @return Par (exitosas, fallidas)
     */
    @Query("""
        SELECT
            SUM(CASE WHEN parseSuccess = 1 THEN 1 ELSE 0 END) as successful,
            SUM(CASE WHEN parseSuccess = 0 THEN 1 ELSE 0 END) as failed
        FROM raw_obd_responses
        WHERE command = :command
    """)
    suspend fun getSuccessFailCountForCommand(command: String): SuccessFailCount

    /**
     * Obtiene las últimas N respuestas de un comando.
     * Útil para análisis de patrón reciente.
     *
     * @param command Comando OBD
     * @param limit Número máximo de respuestas (default: 100)
     * @return Lista de respuestas más recientes
     */
    @Query("""
        SELECT * FROM raw_obd_responses
        WHERE command = :command
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getLatestResponsesForCommand(command: String, limit: Int = 100): List<RawOBDResponseEntity>

    /**
     * Obtiene respuestas exitosas de un comando en rango de tiempo.
     * Útil para análisis de patrones con datos válidos.
     *
     * @param command Comando OBD
     * @param startTime Timestamp inicio
     * @param endTime Timestamp fin
     * @return Lista de respuestas exitosas
     */
    @Query("""
        SELECT * FROM raw_obd_responses
        WHERE command = :command
        AND parseSuccess = 1
        AND timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp ASC
    """)
    suspend fun getSuccessfulResponsesInRange(
        command: String,
        startTime: Long,
        endTime: Long
    ): List<RawOBDResponseEntity>

    /**
     * Elimina respuestas más antiguas que un timestamp dado.
     * Útil para limpieza automática (ej: eliminar > 30 días).
     *
     * @param timestamp Timestamp de corte (milisegundos)
     * @return Número de registros eliminados
     */
    @Query("DELETE FROM raw_obd_responses WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    /**
     * Elimina todas las respuestas de un vehículo específico.
     * Útil para limpiar datos de un dispositivo antiguo.
     *
     * @param vehicleId MAC address del adaptador
     * @return Número de registros eliminados
     */
    @Query("DELETE FROM raw_obd_responses WHERE vehicleId = :vehicleId")
    suspend fun deleteByVehicleId(vehicleId: String): Int

    /**
     * Elimina todas las respuestas (usar con precaución).
     *
     * @return Número de registros eliminados
     */
    @Query("DELETE FROM raw_obd_responses")
    suspend fun deleteAll(): Int

    /**
     * Cuenta el número total de respuestas almacenadas.
     *
     * @return Número total de registros
     */
    @Query("SELECT COUNT(*) FROM raw_obd_responses")
    suspend fun getRecordCount(): Int

    /**
     * Cuenta respuestas por vehículo.
     * Útil para ver qué dispositivos tienen más datos.
     *
     * @return Flow con mapa vehicleId -> count
     */
    @Query("SELECT vehicleId, COUNT(*) as count FROM raw_obd_responses GROUP BY vehicleId")
    fun getRecordCountByVehicle(): Flow<List<VehicleRecordCount>>

    /**
     * Obtiene tamaño estimado de almacenamiento en bytes.
     * Aproximación basada en longitud de campos string.
     *
     * @return Tamaño estimado en bytes
     */
    @Query("""
        SELECT SUM(
            LENGTH(rawResponse) +
            LENGTH(cleanResponse) +
            LENGTH(dataBytesHex)
        ) as totalBytes
        FROM raw_obd_responses
    """)
    suspend fun getEstimatedStorageSize(): Long?

    /**
     * Obtiene estadísticas generales de la tabla.
     * Útil para pantalla de configuración.
     */
    @Query("""
        SELECT
            COUNT(*) as total,
            SUM(CASE WHEN parseSuccess = 1 THEN 1 ELSE 0 END) as successful,
            MIN(timestamp) as oldestTimestamp,
            MAX(timestamp) as newestTimestamp,
            COUNT(DISTINCT command) as uniqueCommands,
            COUNT(DISTINCT vehicleId) as uniqueVehicles
        FROM raw_obd_responses
    """)
    suspend fun getTableStats(): RawResponseTableStats
}

/**
 * Clase de datos para resultado de conteo de éxito/fallo.
 */
data class SuccessFailCount(
    val successful: Int,
    val failed: Int
)

/**
 * Clase de datos para conteo por vehículo.
 */
data class VehicleRecordCount(
    val vehicleId: String,
    val count: Int
)

/**
 * Clase de datos para estadísticas generales de la tabla.
 */
data class RawResponseTableStats(
    val total: Int,
    val successful: Int,
    val oldestTimestamp: Long?,
    val newestTimestamp: Long?,
    val uniqueCommands: Int,
    val uniqueVehicles: Int
) {
    val successRate: Float
        get() = if (total > 0) (successful.toFloat() / total.toFloat()) * 100f else 0f

    val ageInDays: Long
        get() = if (oldestTimestamp != null && newestTimestamp != null) {
            (newestTimestamp - oldestTimestamp) / (1000 * 60 * 60 * 24)
        } else 0L
}
