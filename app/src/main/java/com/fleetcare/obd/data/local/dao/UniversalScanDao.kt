package com.fleetcare.obd.data.local.dao

import androidx.room.*
import com.fleetcare.obd.data.local.entity.ScanResultEntity
import com.fleetcare.obd.data.local.entity.ScanSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data class para el resultado de PIDs por modo.
 */
data class PIDsByModeResult(
    val mode: String,
    val count: Int
)

/**
 * DAO para operaciones de escaneo universal.
 */
@Dao
interface UniversalScanDao {

    // ========== Scan Session Operations ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ScanSessionEntity): Long

    @Update
    suspend fun updateSession(session: ScanSessionEntity)

    @Query("SELECT * FROM scan_sessions WHERE sessionId = :sessionId")
    suspend fun getSessionById(sessionId: String): ScanSessionEntity?

    @Query("SELECT * FROM scan_sessions WHERE vehicleId = :vehicleId ORDER BY startTime DESC")
    fun getSessionsByVehicle(vehicleId: String): Flow<List<ScanSessionEntity>>

    @Query("SELECT * FROM scan_sessions WHERE vehicleId = :vehicleId ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestSession(vehicleId: String): ScanSessionEntity?

    @Query("UPDATE scan_sessions SET state = :state WHERE sessionId = :sessionId")
    suspend fun updateSessionState(sessionId: String, state: String)

    @Query("UPDATE scan_sessions SET statistics = :statistics, endTime = :endTime, state = 'COMPLETED' WHERE sessionId = :sessionId")
    suspend fun completeSession(sessionId: String, statistics: String, endTime: Long)

    @Query("UPDATE scan_sessions SET errorMessage = :errorMessage, endTime = :endTime, state = 'ERROR' WHERE sessionId = :sessionId")
    suspend fun errorSession(sessionId: String, errorMessage: String, endTime: Long)

    @Delete
    suspend fun deleteSession(session: ScanSessionEntity)

    @Query("DELETE FROM scan_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("DELETE FROM scan_sessions WHERE vehicleId = :vehicleId")
    suspend fun deleteSessionsByVehicle(vehicleId: String)

    @Query("SELECT COUNT(*) FROM scan_sessions WHERE vehicleId = :vehicleId")
    suspend fun getSessionCount(vehicleId: String): Int

    @Query("SELECT * FROM scan_sessions WHERE vehicleId = :vehicleId AND state IN ('SCANNING', 'PAUSED') LIMIT 1")
    suspend fun getActiveSession(vehicleId: String): ScanSessionEntity?

    // ========== Scan Result Operations ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ScanResultEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<ScanResultEntity>)

    @Query("SELECT * FROM scan_results WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getResultsBySession(sessionId: String): Flow<List<ScanResultEntity>>

    @Query("SELECT * FROM scan_results WHERE sessionId = :sessionId AND success = :success ORDER BY timestamp ASC")
    fun getResultsBySessionFiltered(sessionId: String, success: Boolean): Flow<List<ScanResultEntity>>

    @Query("SELECT * FROM scan_results WHERE sessionId = :sessionId AND mode = :mode ORDER BY timestamp ASC")
    fun getResultsBySessionAndMode(sessionId: String, mode: String): Flow<List<ScanResultEntity>>

    @Query("SELECT * FROM scan_results WHERE sessionId = :sessionId AND mode = :mode AND success = :success ORDER BY timestamp ASC")
    fun getResultsBySessionModeAndSuccess(sessionId: String, mode: String, success: Boolean): Flow<List<ScanResultEntity>>

    @Query("SELECT * FROM scan_results WHERE vehicleId = :vehicleId AND success = 1 ORDER BY timestamp DESC")
    fun getSuccessfulResultsByVehicle(vehicleId: String): Flow<List<ScanResultEntity>>

    @Query("DELETE FROM scan_results WHERE sessionId = :sessionId")
    suspend fun deleteResultsBySession(sessionId: String)

    // ========== Statistics Queries ==========

    @Query("SELECT COUNT(*) FROM scan_results WHERE sessionId = :sessionId")
    suspend fun getResultCount(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM scan_results WHERE sessionId = :sessionId AND success = 1")
    suspend fun getSuccessfulCount(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM scan_results WHERE sessionId = :sessionId AND success = 0")
    suspend fun getFailedCount(sessionId: String): Int

    @Query("SELECT AVG(responseTime) FROM scan_results WHERE sessionId = :sessionId AND success = 1")
    suspend fun getAverageResponseTime(sessionId: String): Long

    @Query("SELECT mode, COUNT(*) as count FROM scan_results WHERE sessionId = :sessionId AND success = 1 GROUP BY mode")
    suspend fun getPIDsByMode(sessionId: String): List<PIDsByModeResult>
}
