package com.fleetcare.obd.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fleetcare.obd.domain.model.PIDDataType
import com.fleetcare.obd.domain.model.PIDMetadata

/**
 * Entity de Room para metadata de PIDs.
 */
@Entity(
    tableName = "pid_metadata",
    indices = [
        Index(value = ["mode", "pid", "vehicleId"], unique = true),
        Index(value = ["vehicleId"]),
        Index(value = ["mode"]),
        Index(value = ["detectedType"]),
        Index(value = ["isStandard"])
    ]
)
data class PIDMetadataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mode: String,
    val pid: String,
    val vehicleId: String?,  // null = metadata global (no específica del vehículo)
    val name: String,
    val description: String,
    val unit: String,
    val formula: String,
    val detectedType: String,  // PIDDataType as String
    val minValue: Double?,
    val maxValue: Double?,
    val averageResponseTime: Long,
    val successRate: Float,
    val responseLength: Int,
    val isStandard: Boolean,
    val vehicleSpecific: Boolean,
    val lastUpdated: Long
) {
    /**
     * Convierte a modelo de dominio.
     */
    fun toDomain(): PIDMetadata {
        return PIDMetadata(
            mode = mode,
            pid = pid,
            name = name,
            description = description,
            unit = unit,
            formula = formula,
            detectedType = try {
                PIDDataType.valueOf(detectedType)
            } catch (e: Exception) {
                PIDDataType.UNKNOWN
            },
            minValue = minValue,
            maxValue = maxValue,
            averageResponseTime = averageResponseTime,
            successRate = successRate,
            responseLength = responseLength,
            isStandard = isStandard,
            vehicleSpecific = vehicleSpecific,
            lastUpdated = lastUpdated
        )
    }

    companion object {
        /**
         * Convierte desde modelo de dominio.
         */
        fun fromDomain(metadata: PIDMetadata, vehicleId: String? = null): PIDMetadataEntity {
            return PIDMetadataEntity(
                mode = metadata.mode,
                pid = metadata.pid,
                vehicleId = vehicleId,
                name = metadata.name,
                description = metadata.description,
                unit = metadata.unit,
                formula = metadata.formula,
                detectedType = metadata.detectedType.name,
                minValue = metadata.minValue,
                maxValue = metadata.maxValue,
                averageResponseTime = metadata.averageResponseTime,
                successRate = metadata.successRate,
                responseLength = metadata.responseLength,
                isStandard = metadata.isStandard,
                vehicleSpecific = metadata.vehicleSpecific,
                lastUpdated = metadata.lastUpdated
            )
        }
    }
}
