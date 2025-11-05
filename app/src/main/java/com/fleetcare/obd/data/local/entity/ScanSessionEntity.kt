package com.fleetcare.obd.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.fleetcare.obd.data.local.database.Converters
import com.fleetcare.obd.domain.model.*

/**
 * Entity de Room para sesiones de escaneo.
 */
@Entity(tableName = "scan_sessions")
@TypeConverters(Converters::class)
data class ScanSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val vehicleId: String,
    val state: String,  // ScannerState as String
    val config: String,  // UniversalScanConfig serializado como JSON
    val statistics: String?,  // ScanStatistics serializado como JSON
    val startTime: Long,
    val endTime: Long?,
    val errorMessage: String?
) {
    /**
     * Convierte a modelo de dominio.
     */
    fun toDomain(results: List<ScanResult> = emptyList()): ScanSession {
        return ScanSession(
            sessionId = sessionId,
            vehicleId = vehicleId,
            config = Converters.fromScanConfigJson(config),
            state = ScannerState.valueOf(state),
            results = results,
            statistics = statistics?.let { Converters.fromStatisticsJson(it) },
            startTime = startTime,
            endTime = endTime,
            errorMessage = errorMessage
        )
    }

    companion object {
        /**
         * Convierte desde modelo de dominio.
         */
        fun fromDomain(session: ScanSession): ScanSessionEntity {
            return ScanSessionEntity(
                sessionId = session.sessionId,
                vehicleId = session.vehicleId,
                state = session.state.name,
                config = Converters.toScanConfigJson(session.config),
                statistics = session.statistics?.let { Converters.toStatisticsJson(it) },
                startTime = session.startTime,
                endTime = session.endTime,
                errorMessage = session.errorMessage
            )
        }
    }
}
