package com.fleetcare.obd.data.local.dao

import androidx.room.*
import com.fleetcare.obd.data.local.entity.PIDMetadataEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones de metadata de PIDs.
 */
@Dao
interface PIDMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: PIDMetadataEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMultiple(metadataList: List<PIDMetadataEntity>)

    @Update
    suspend fun updateMetadata(metadata: PIDMetadataEntity)

    @Query("SELECT * FROM pid_metadata WHERE mode = :mode AND pid = :pid AND (vehicleId = :vehicleId OR vehicleId IS NULL) LIMIT 1")
    suspend fun getMetadata(mode: String, pid: String, vehicleId: String?): PIDMetadataEntity?

    @Query("SELECT * FROM pid_metadata WHERE mode = :mode AND (vehicleId = :vehicleId OR vehicleId IS NULL)")
    fun getMetadataByMode(mode: String, vehicleId: String?): Flow<List<PIDMetadataEntity>>

    @Query("SELECT * FROM pid_metadata WHERE vehicleId = :vehicleId")
    fun getMetadataByVehicle(vehicleId: String): Flow<List<PIDMetadataEntity>>

    @Query("SELECT * FROM pid_metadata WHERE detectedType = :dataType AND (vehicleId = :vehicleId OR vehicleId IS NULL)")
    fun getMetadataByDataType(dataType: String, vehicleId: String?): Flow<List<PIDMetadataEntity>>

    @Query("SELECT * FROM pid_metadata WHERE isStandard = 1")
    fun getStandardPIDsMetadata(): Flow<List<PIDMetadataEntity>>

    @Query("SELECT * FROM pid_metadata WHERE isStandard = 0 AND vehicleId = :vehicleId")
    fun getManufacturerPIDsMetadata(vehicleId: String): Flow<List<PIDMetadataEntity>>

    @Query("SELECT * FROM pid_metadata WHERE successRate >= :minSuccessRate AND averageResponseTime <= :maxResponseTime AND (vehicleId = :vehicleId OR vehicleId IS NULL)")
    fun getHighQualityPIDs(vehicleId: String?, minSuccessRate: Float, maxResponseTime: Long): Flow<List<PIDMetadataEntity>>

    @Query("SELECT * FROM pid_metadata WHERE successRate >= 0.8 AND averageResponseTime < 500 AND detectedType NOT IN ('BITMAP', 'STRING') AND vehicleId = :vehicleId")
    fun getRealTimeMonitoringPIDs(vehicleId: String): Flow<List<PIDMetadataEntity>>

    @Query("UPDATE pid_metadata SET averageResponseTime = :responseTime, successRate = :successRate, lastUpdated = :lastUpdated WHERE mode = :mode AND pid = :pid AND vehicleId = :vehicleId")
    suspend fun updatePerformanceStats(mode: String, pid: String, vehicleId: String, responseTime: Long, successRate: Float, lastUpdated: Long)

    @Query("UPDATE pid_metadata SET minValue = :minValue, maxValue = :maxValue, lastUpdated = :lastUpdated WHERE mode = :mode AND pid = :pid AND vehicleId = :vehicleId")
    suspend fun updateValueRange(mode: String, pid: String, vehicleId: String, minValue: Double, maxValue: Double, lastUpdated: Long)

    @Query("DELETE FROM pid_metadata WHERE mode = :mode AND pid = :pid AND (vehicleId = :vehicleId OR (:vehicleId IS NULL AND vehicleId IS NULL))")
    suspend fun deleteMetadata(mode: String, pid: String, vehicleId: String?)

    @Query("DELETE FROM pid_metadata WHERE vehicleId = :vehicleId")
    suspend fun deleteMetadataByVehicle(vehicleId: String)

    @Query("SELECT COUNT(*) FROM pid_metadata WHERE vehicleId = :vehicleId OR (:vehicleId IS NULL AND vehicleId IS NULL)")
    suspend fun getMetadataCount(vehicleId: String?): Int

    @Query("SELECT * FROM pid_metadata WHERE (name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') AND (vehicleId = :vehicleId OR vehicleId IS NULL)")
    fun searchMetadata(query: String, vehicleId: String?): Flow<List<PIDMetadataEntity>>

    @Query("SELECT * FROM pid_metadata")
    fun getAllMetadata(): Flow<List<PIDMetadataEntity>>
}
