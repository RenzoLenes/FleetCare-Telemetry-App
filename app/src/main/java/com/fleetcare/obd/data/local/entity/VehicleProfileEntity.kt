package com.fleetcare.obd.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.fleetcare.obd.data.local.database.Converters
import com.fleetcare.obd.domain.model.VehicleProfile

/**
 * Entity de Room para perfiles de vehículos.
 */
@Entity(tableName = "vehicle_profiles")
@TypeConverters(Converters::class)
data class VehicleProfileEntity(
    @PrimaryKey
    val vehicleId: String,
    val vin: String,
    val make: String,
    val model: String,
    val year: Int?,
    val protocol: String,
    val protocolName: String,
    val ecuInfo: String,  // ECUInfo serializado como JSON
    val supportedPIDsCount: Int,
    val knownPIDs: String,  // List<String> serializado como JSON
    val failedPIDs: String,  // List<String> serializado como JSON
    val optimalScanConfig: String?,  // UniversalScanConfig serializado como JSON
    val isLegacyVehicle: Boolean,
    val lastScanned: Long,
    val totalScans: Int,
    val averageQualityScore: Int
) {
    /**
     * Convierte a modelo de dominio.
     */
    fun toDomain(): VehicleProfile {
        return VehicleProfile(
            vehicleId = vehicleId,
            vin = vin,
            make = make,
            model = model,
            year = year,
            protocol = protocol,
            protocolName = protocolName,
            ecuInfo = Converters.fromECUInfoJson(ecuInfo),
            supportedPIDsCount = supportedPIDsCount,
            knownPIDs = Converters.fromStringListJson(knownPIDs),
            failedPIDs = Converters.fromStringListJson(failedPIDs),
            optimalScanConfig = optimalScanConfig?.let { Converters.fromScanConfigJson(it) },
            isLegacyVehicle = isLegacyVehicle,
            lastScanned = lastScanned,
            totalScans = totalScans,
            averageQualityScore = averageQualityScore
        )
    }

    companion object {
        /**
         * Convierte desde modelo de dominio.
         */
        fun fromDomain(profile: VehicleProfile): VehicleProfileEntity {
            return VehicleProfileEntity(
                vehicleId = profile.vehicleId,
                vin = profile.vin,
                make = profile.make,
                model = profile.model,
                year = profile.year,
                protocol = profile.protocol,
                protocolName = profile.protocolName,
                ecuInfo = Converters.toECUInfoJson(profile.ecuInfo),
                supportedPIDsCount = profile.supportedPIDsCount,
                knownPIDs = Converters.toStringListJson(profile.knownPIDs),
                failedPIDs = Converters.toStringListJson(profile.failedPIDs),
                optimalScanConfig = profile.optimalScanConfig?.let { Converters.toScanConfigJson(it) },
                isLegacyVehicle = profile.isLegacyVehicle,
                lastScanned = profile.lastScanned,
                totalScans = profile.totalScans,
                averageQualityScore = profile.averageQualityScore
            )
        }
    }
}
