package com.fleetcare.obd.data.local.dao

import androidx.room.*
import com.fleetcare.obd.data.local.entity.VehicleProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones de perfiles de vehículos.
 */
@Dao
interface VehicleProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: VehicleProfileEntity)

    @Update
    suspend fun updateProfile(profile: VehicleProfileEntity)

    @Query("SELECT * FROM vehicle_profiles WHERE vehicleId = :vehicleId")
    suspend fun getProfileById(vehicleId: String): VehicleProfileEntity?

    @Query("SELECT * FROM vehicle_profiles WHERE vin = :vin LIMIT 1")
    suspend fun getProfileByVIN(vin: String): VehicleProfileEntity?

    @Query("SELECT * FROM vehicle_profiles ORDER BY lastScanned DESC")
    fun getAllProfiles(): Flow<List<VehicleProfileEntity>>

    @Query("SELECT * FROM vehicle_profiles WHERE isLegacyVehicle = 1 ORDER BY lastScanned DESC")
    fun getLegacyVehicles(): Flow<List<VehicleProfileEntity>>

    @Query("SELECT * FROM vehicle_profiles WHERE isLegacyVehicle = 0 ORDER BY lastScanned DESC")
    fun getModernVehicles(): Flow<List<VehicleProfileEntity>>

    @Query("UPDATE vehicle_profiles SET vin = :vin, make = :make, model = :model, year = :year WHERE vehicleId = :vehicleId")
    suspend fun updateVehicleInfo(vehicleId: String, vin: String, make: String, model: String, year: Int?)

    @Query("UPDATE vehicle_profiles SET protocol = :protocol, protocolName = :protocolName, isLegacyVehicle = :isLegacy WHERE vehicleId = :vehicleId")
    suspend fun updateProtocol(vehicleId: String, protocol: String, protocolName: String, isLegacy: Boolean)

    @Query("UPDATE vehicle_profiles SET ecuInfo = :ecuInfo WHERE vehicleId = :vehicleId")
    suspend fun updateECUInfo(vehicleId: String, ecuInfo: String)

    @Query("UPDATE vehicle_profiles SET optimalScanConfig = :config WHERE vehicleId = :vehicleId")
    suspend fun updateOptimalConfig(vehicleId: String, config: String)

    @Query("UPDATE vehicle_profiles SET totalScans = :totalScans, averageQualityScore = :averageQualityScore, lastScanned = :lastScanned WHERE vehicleId = :vehicleId")
    suspend fun updateScanStatistics(vehicleId: String, totalScans: Int, averageQualityScore: Int, lastScanned: Long)

    @Delete
    suspend fun deleteProfile(profile: VehicleProfileEntity)

    @Query("DELETE FROM vehicle_profiles WHERE vehicleId = :vehicleId")
    suspend fun deleteProfileById(vehicleId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM vehicle_profiles WHERE vehicleId = :vehicleId)")
    suspend fun hasProfile(vehicleId: String): Boolean

    @Query("SELECT COUNT(*) FROM vehicle_profiles")
    suspend fun getVehicleCount(): Int

    @Query("SELECT * FROM vehicle_profiles WHERE lastScanned > :timestamp ORDER BY lastScanned DESC")
    fun getRecentlyScannedProfiles(timestamp: Long): Flow<List<VehicleProfileEntity>>

    @Query("SELECT * FROM vehicle_profiles WHERE averageQualityScore >= :minScore ORDER BY averageQualityScore DESC")
    fun getHighQualityProfiles(minScore: Int): Flow<List<VehicleProfileEntity>>

    @Query("SELECT * FROM vehicle_profiles WHERE make LIKE '%' || :query || '%' OR model LIKE '%' || :query || '%' OR vin LIKE '%' || :query || '%'")
    fun searchVehicles(query: String): Flow<List<VehicleProfileEntity>>

    @Query("SELECT * FROM vehicle_profiles ORDER BY make, model")
    suspend fun getProfilesSortedByMake(): List<VehicleProfileEntity>

    @Query("SELECT * FROM vehicle_profiles WHERE year IS NOT NULL ORDER BY year DESC, make, model")
    suspend fun getProfilesSortedByYear(): List<VehicleProfileEntity>

    @Query("SELECT * FROM vehicle_profiles ORDER BY protocol, make, model")
    suspend fun getProfilesSortedByProtocol(): List<VehicleProfileEntity>
}
