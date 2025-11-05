package com.fleetcare.obd.data.repository

import com.fleetcare.obd.data.local.dao.VehicleProfileDao
import com.fleetcare.obd.data.local.database.Converters
import com.fleetcare.obd.data.local.entity.VehicleProfileEntity
import com.fleetcare.obd.domain.model.*
import com.fleetcare.obd.domain.repository.VehicleProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación del repositorio de perfiles de vehículos.
 */
@Singleton
class VehicleProfileRepositoryImpl @Inject constructor(
    private val dao: VehicleProfileDao
) : VehicleProfileRepository {

    override suspend fun saveProfile(profile: VehicleProfile) {
        val entity = VehicleProfileEntity.fromDomain(profile)
        dao.insertProfile(entity)
    }

    override suspend fun getProfile(vehicleId: String): VehicleProfile? {
        return dao.getProfileById(vehicleId)?.toDomain()
    }

    override suspend fun getProfileByVIN(vin: String): VehicleProfile? {
        return dao.getProfileByVIN(vin)?.toDomain()
    }

    override fun getAllProfiles(): Flow<List<VehicleProfile>> {
        return dao.getAllProfiles().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getLegacyVehicles(): Flow<List<VehicleProfile>> {
        return dao.getLegacyVehicles().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getModernVehicles(): Flow<List<VehicleProfile>> {
        return dao.getModernVehicles().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun updateVehicleInfo(
        vehicleId: String,
        vin: String,
        make: String,
        model: String,
        year: Int?
    ) {
        dao.updateVehicleInfo(vehicleId, vin, make, model, year)
    }

    override suspend fun updateProtocol(
        vehicleId: String,
        protocol: String,
        protocolName: String,
        isLegacy: Boolean
    ) {
        dao.updateProtocol(vehicleId, protocol, protocolName, isLegacy)
    }

    override suspend fun updateECUInfo(vehicleId: String, ecuInfo: ECUInfo) {
        val ecuInfoJson = Converters.toECUInfoJson(ecuInfo)
        dao.updateECUInfo(vehicleId, ecuInfoJson)
    }

    override suspend fun addKnownPIDs(vehicleId: String, pids: List<String>) {
        val profile = dao.getProfileById(vehicleId) ?: return
        val existingPIDs = Converters.fromStringListJson(profile.knownPIDs)
        val updatedPIDs = (existingPIDs + pids).distinct()

        val updatedProfile = profile.copy(
            knownPIDs = Converters.toStringListJson(updatedPIDs),
            supportedPIDsCount = updatedPIDs.size
        )
        dao.updateProfile(updatedProfile)
    }

    override suspend fun addFailedPIDs(vehicleId: String, pids: List<String>) {
        val profile = dao.getProfileById(vehicleId) ?: return
        val existingFailedPIDs = Converters.fromStringListJson(profile.failedPIDs)
        val updatedFailedPIDs = (existingFailedPIDs + pids).distinct()

        val updatedProfile = profile.copy(
            failedPIDs = Converters.toStringListJson(updatedFailedPIDs)
        )
        dao.updateProfile(updatedProfile)
    }

    override suspend fun updateOptimalConfig(vehicleId: String, config: UniversalScanConfig) {
        val configJson = Converters.toScanConfigJson(config)
        dao.updateOptimalConfig(vehicleId, configJson)
    }

    override suspend fun updateScanStatistics(
        vehicleId: String,
        totalScans: Int,
        averageQualityScore: Int
    ) {
        dao.updateScanStatistics(vehicleId, totalScans, averageQualityScore, System.currentTimeMillis())
    }

    override suspend fun updateFromScan(
        vehicleId: String,
        scanResults: List<ScanResult>,
        statistics: ScanStatistics,
        config: UniversalScanConfig
    ) {
        val profile = dao.getProfileById(vehicleId) ?: run {
            // Crear perfil básico si no existe
            val newProfile = VehicleProfile.createBasic(vehicleId)
            saveProfile(newProfile)
            dao.getProfileById(vehicleId)!!
        }

        // Actualizar con VehicleProfile.updateFromScan
        val domainProfile = profile.toDomain()
        val updated = VehicleProfile.updateFromScan(domainProfile, scanResults, statistics, config)

        saveProfile(updated)
    }

    override suspend fun deleteProfile(vehicleId: String) {
        dao.deleteProfileById(vehicleId)
    }

    override suspend fun hasProfile(vehicleId: String): Boolean {
        return dao.hasProfile(vehicleId)
    }

    override suspend fun getVehicleCount(): Int {
        return dao.getVehicleCount()
    }

    override fun getRecentlyScannedProfiles(): Flow<List<VehicleProfile>> {
        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        return dao.getRecentlyScannedProfiles(oneDayAgo).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getHighQualityProfiles(minQualityScore: Int): Flow<List<VehicleProfile>> {
        return dao.getHighQualityProfiles(minQualityScore).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun searchVehicles(query: String): Flow<List<VehicleProfile>> {
        return dao.searchVehicles(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getProfilesByMake(): Map<String, List<VehicleProfile>> {
        val profiles = dao.getProfilesSortedByMake().map { it.toDomain() }
        return profiles.groupBy { it.make }
    }

    override suspend fun getProfilesByYear(): Map<Int, List<VehicleProfile>> {
        val profiles = dao.getProfilesSortedByYear().map { it.toDomain() }
        return profiles.groupBy { it.year ?: 0 }.filterKeys { it != 0 }
    }

    override suspend fun getProfilesByProtocol(): Map<String, List<VehicleProfile>> {
        val profiles = dao.getProfilesSortedByProtocol().map { it.toDomain() }
        return profiles.groupBy { it.protocol }
    }

    override suspend fun isPIDKnownToFail(vehicleId: String, mode: String, pid: String): Boolean {
        val profile = dao.getProfileById(vehicleId) ?: return false
        val pidId = "${mode}_${pid.uppercase()}"
        val failedPIDs = Converters.fromStringListJson(profile.failedPIDs)
        return failedPIDs.contains(pidId)
    }

    override suspend fun isPIDSupported(vehicleId: String, mode: String, pid: String): Boolean {
        val profile = dao.getProfileById(vehicleId) ?: return false
        val pidId = "${mode}_${pid.uppercase()}"
        val knownPIDs = Converters.fromStringListJson(profile.knownPIDs)
        return knownPIDs.contains(pidId)
    }

    override suspend fun getRecommendedScanConfig(vehicleId: String): UniversalScanConfig {
        val profile = dao.getProfileById(vehicleId)?.toDomain()
        return profile?.getRecommendedScanConfig() ?: ScanPresets.fullStandardScan(vehicleId)
    }
}
