package com.fleetcare.obd.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad Room para almacenar PIDs soportados por vehículo.
 *
 * Cachea la lista de PIDs detect

ados para evitar hacer la detección
 * completa cada vez que se conecta el mismo vehículo.
 *
 * Sprint 2: Detección y almacenamiento de PIDs soportados
 */
@Entity(
    tableName = "supported_pids",
    indices = [
        Index(value = ["vehicleId"], unique = true),
        Index(value = ["vin"]),
        Index(value = ["detectionTimestamp"])
    ]
)
data class SupportedPIDsEntity(
    /**
     * Primary key auto-generada.
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * ID del vehículo (MAC del adaptador Bluetooth).
     * Único por vehículo.
     */
    val vehicleId: String,

    /**
     * VIN del vehículo (Vehicle Identification Number).
     * Puede ser null si no está disponible.
     */
    val vin: String? = null,

    /**
     * Lista de PIDs soportados en formato JSON.
     * Estructura: Map de control PID a lista de PIDs
     * Ejemplo: {"0":"[1,3,4,5,6,7,12,13,15,17,19,20]","32":"[33,34,35,36]"}
     */
    val pidRangesJson: String,

    /**
     * Timestamp de cuándo se realizó la detección.
     */
    val detectionTimestamp: Long,

    /**
     * Total de PIDs soportados (para consultas rápidas).
     */
    val totalPIDsCount: Int,

    /**
     * Versión del algoritmo de detección (para futuras actualizaciones).
     */
    val detectionVersion: Int = 1
)
