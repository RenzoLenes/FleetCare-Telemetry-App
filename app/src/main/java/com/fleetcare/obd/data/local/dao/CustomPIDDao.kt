package com.fleetcare.obd.data.local.dao

import androidx.room.*
import com.fleetcare.obd.data.local.entity.CustomPIDEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para PIDs personalizados.
 *
 * Sprint 6: Gestión de PIDs Personalizados
 */
@Dao
interface CustomPIDDao {

    // ========== CREATE ==========

    /**
     * Inserta un nuevo PID personalizado.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomPID(customPID: CustomPIDEntity): Long

    /**
     * Inserta múltiples PIDs personalizados.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customPIDs: List<CustomPIDEntity>): List<Long>

    // ========== READ ==========

    /**
     * Obtiene todos los PIDs personalizados.
     */
    @Query("SELECT * FROM custom_pids ORDER BY last_used DESC")
    fun getAllCustomPIDs(): Flow<List<CustomPIDEntity>>

    /**
     * Obtiene un PID personalizado por ID.
     */
    @Query("SELECT * FROM custom_pids WHERE id = :id")
    suspend fun getCustomPIDById(id: Long): CustomPIDEntity?

    /**
     * Obtiene un PID personalizado por PID hex.
     */
    @Query("SELECT * FROM custom_pids WHERE pid = :pid LIMIT 1")
    suspend fun getCustomPIDByPID(pid: String): CustomPIDEntity?

    /**
     * Obtiene un PID personalizado por comando.
     */
    @Query("SELECT * FROM custom_pids WHERE command = :command LIMIT 1")
    suspend fun getCustomPIDByCommand(command: String): CustomPIDEntity?

    /**
     * Obtiene todos los PIDs habilitados.
     */
    @Query("SELECT * FROM custom_pids WHERE is_enabled = 1 ORDER BY last_used DESC")
    fun getEnabledCustomPIDs(): Flow<List<CustomPIDEntity>>

    /**
     * Obtiene PIDs por categoría.
     */
    @Query("SELECT * FROM custom_pids WHERE category = :category ORDER BY name ASC")
    fun getCustomPIDsByCategory(category: String): Flow<List<CustomPIDEntity>>

    /**
     * Obtiene PIDs por origen.
     */
    @Query("SELECT * FROM custom_pids WHERE source = :source ORDER BY discovery_date DESC")
    fun getCustomPIDsBySource(source: String): Flow<List<CustomPIDEntity>>

    /**
     * Busca PIDs por nombre.
     */
    @Query("""
        SELECT * FROM custom_pids
        WHERE name LIKE '%' || :query || '%'
        OR pid LIKE '%' || :query || '%'
        OR command LIKE '%' || :query || '%'
        ORDER BY name ASC
    """)
    fun searchCustomPIDs(query: String): Flow<List<CustomPIDEntity>>

    /**
     * Obtiene PIDs compatibles con un vehículo.
     * Busca PIDs donde vehicleModelsJson contenga el VIN o sea vacío (compatibilidad universal).
     */
    @Query("""
        SELECT * FROM custom_pids
        WHERE (vehicle_models_json = '[]' OR vehicle_models_json LIKE '%' || :vin || '%')
        AND is_enabled = 1
        ORDER BY last_used DESC
    """)
    fun getCustomPIDsForVehicle(vin: String): Flow<List<CustomPIDEntity>>

    /**
     * Obtiene PIDs recientes (últimos 10).
     */
    @Query("SELECT * FROM custom_pids ORDER BY last_used DESC LIMIT 10")
    fun getRecentCustomPIDs(): Flow<List<CustomPIDEntity>>

    /**
     * Obtiene el conteo total de PIDs personalizados.
     */
    @Query("SELECT COUNT(*) FROM custom_pids")
    suspend fun getCustomPIDCount(): Int

    /**
     * Obtiene el conteo de PIDs habilitados.
     */
    @Query("SELECT COUNT(*) FROM custom_pids WHERE is_enabled = 1")
    suspend fun getEnabledCustomPIDCount(): Int

    /**
     * Obtiene conteo por categoría.
     */
    @Query("SELECT category, COUNT(*) as count FROM custom_pids GROUP BY category")
    suspend fun getCustomPIDCountByCategory(): List<CategoryCount>

    // ========== UPDATE ==========

    /**
     * Actualiza un PID personalizado.
     */
    @Update
    suspend fun updateCustomPID(customPID: CustomPIDEntity)

    /**
     * Actualiza la fecha de último uso.
     */
    @Query("UPDATE custom_pids SET last_used = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * Actualiza el estado habilitado/deshabilitado.
     */
    @Query("UPDATE custom_pids SET is_enabled = :isEnabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, isEnabled: Boolean)

    /**
     * Actualiza la confianza de un PID.
     */
    @Query("UPDATE custom_pids SET confidence = :confidence WHERE id = :id")
    suspend fun updateConfidence(id: Long, confidence: Float)

    // ========== DELETE ==========

    /**
     * Elimina un PID personalizado.
     */
    @Delete
    suspend fun deleteCustomPID(customPID: CustomPIDEntity)

    /**
     * Elimina un PID por ID.
     */
    @Query("DELETE FROM custom_pids WHERE id = :id")
    suspend fun deleteCustomPIDById(id: Long)

    /**
     * Elimina todos los PIDs personalizados.
     */
    @Query("DELETE FROM custom_pids")
    suspend fun deleteAllCustomPIDs()

    /**
     * Elimina PIDs por origen.
     */
    @Query("DELETE FROM custom_pids WHERE source = :source")
    suspend fun deleteCustomPIDsBySource(source: String)

    /**
     * Elimina PIDs deshabilitados.
     */
    @Query("DELETE FROM custom_pids WHERE is_enabled = 0")
    suspend fun deleteDisabledCustomPIDs()

    // ========== STATS ==========

    /**
     * Obtiene estadísticas de la tabla.
     */
    @Query("""
        SELECT
            COUNT(*) as totalPIDs,
            SUM(CASE WHEN is_enabled = 1 THEN 1 ELSE 0 END) as enabledPIDs,
            COUNT(DISTINCT category) as categoryCount,
            AVG(confidence) as avgConfidence,
            MAX(last_used) as lastActivity
        FROM custom_pids
    """)
    suspend fun getCustomPIDStats(): CustomPIDStats?

    /**
     * Obtiene PIDs con baja confianza (< 0.5).
     */
    @Query("SELECT * FROM custom_pids WHERE confidence < 0.5 ORDER BY confidence ASC")
    fun getLowConfidencePIDs(): Flow<List<CustomPIDEntity>>
}

/**
 * Estadísticas de PIDs personalizados.
 */
data class CustomPIDStats(
    val totalPIDs: Int,
    val enabledPIDs: Int,
    val categoryCount: Int,
    val avgConfidence: Float,
    val lastActivity: Long
)

/**
 * Conteo de PIDs por categoría.
 */
data class CategoryCount(
    val category: String,
    val count: Int
)
