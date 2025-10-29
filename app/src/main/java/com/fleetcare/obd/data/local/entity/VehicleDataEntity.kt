package com.fleetcare.obd.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entidad de Room que representa un registro de telemetría del vehículo.
 *
 * Esta tabla almacena datos leídos del OBDII en caché local para:
 * - Mantener historial cuando no hay conexión a internet
 * - Permitir consultas rápidas sin depender de Firebase
 * - Sincronización posterior con Firebase
 *
 * Se implementará completamente en Sprint 3.
 */
@Entity(tableName = "vehicle_data")
data class VehicleDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Date,
    val vehicleId: String,
    val sessionId: String,
    val rpm: Int? = null,
    val speed: Double? = null,
    val coolantTemp: Double? = null,
    val intakeAirTemp: Double? = null,
    val throttlePosition: Double? = null,
    val engineLoad: Double? = null,
    val voltage: Double? = null,
    val fuelLevel: Double? = null,
    val oilTemp: Double? = null,
    val ambientTemp: Double? = null,
    val synced: Boolean = false // Indica si ya se sincronizó con Firebase
)
