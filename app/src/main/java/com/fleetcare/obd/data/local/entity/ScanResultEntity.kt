package com.fleetcare.obd.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fleetcare.obd.domain.model.ScanResult

/**
 * Entity de Room para resultados de escaneo de PIDs.
 */
@Entity(
    tableName = "scan_results",
    foreignKeys = [
        ForeignKey(
            entity = ScanSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["vehicleId"]),
        Index(value = ["mode", "pid"]),
        Index(value = ["success"])
    ]
)
data class ScanResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val vehicleId: String,
    val mode: String,
    val pid: String,
    val command: String,
    val success: Boolean,
    val rawResponse: String,
    val dataBytes: ByteArray,
    val byteCount: Int,
    val interpretation: String?,
    val timestamp: Long,
    val responseTime: Long,
    val isStandardPID: Boolean
) {
    /**
     * Convierte a modelo de dominio.
     */
    fun toDomain(): ScanResult {
        return ScanResult(
            mode = mode,
            pid = pid,
            command = command,
            success = success,
            rawResponse = rawResponse,
            dataBytes = dataBytes,
            byteCount = byteCount,
            interpretation = interpretation,
            timestamp = timestamp,
            responseTime = responseTime,
            isStandardPID = isStandardPID,
            vehicleId = vehicleId,
            metadata = null  // La metadata se carga por separado si es necesaria
        )
    }

    companion object {
        /**
         * Convierte desde modelo de dominio.
         */
        fun fromDomain(result: ScanResult, sessionId: String): ScanResultEntity {
            return ScanResultEntity(
                sessionId = sessionId,
                vehicleId = result.vehicleId,
                mode = result.mode,
                pid = result.pid,
                command = result.command,
                success = result.success,
                rawResponse = result.rawResponse,
                dataBytes = result.dataBytes,
                byteCount = result.byteCount,
                interpretation = result.interpretation,
                timestamp = result.timestamp,
                responseTime = result.responseTime,
                isStandardPID = result.isStandardPID
            )
        }
    }

    // Override equals/hashCode para ByteArray
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScanResultEntity

        if (id != other.id) return false
        if (sessionId != other.sessionId) return false
        if (mode != other.mode) return false
        if (pid != other.pid) return false
        if (!dataBytes.contentEquals(other.dataBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + mode.hashCode()
        result = 31 * result + pid.hashCode()
        result = 31 * result + dataBytes.contentHashCode()
        return result
    }
}
