package com.fleetcare.obd.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fleetcare.obd.domain.model.CustomPID
import com.fleetcare.obd.domain.model.PIDCategory
import com.fleetcare.obd.domain.model.PIDSource

/**
 * Entidad Room para PIDs personalizados.
 *
 * Sprint 6: Gestión de PIDs Personalizados
 *
 * Tabla: custom_pids
 * Índices:
 * - pid (búsquedas rápidas por PID)
 * - command (búsquedas por comando)
 * - category (filtrado por categoría)
 * - isEnabled (filtrado por habilitados)
 */
@Entity(
    tableName = "custom_pids",
    indices = [
        Index(value = ["pid"]),
        Index(value = ["command"]),
        Index(value = ["category"]),
        Index(value = ["is_enabled"]),
        Index(value = ["source"])
    ]
)
data class CustomPIDEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "pid")
    val pid: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "command")
    val command: String,

    @ColumnInfo(name = "formula")
    val formula: String,

    @ColumnInfo(name = "unit")
    val unit: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "vehicle_models_json")
    val vehicleModelsJson: String, // JSON array: ["VIN1", "VIN2"]

    @ColumnInfo(name = "discovery_date")
    val discoveryDate: Long,

    @ColumnInfo(name = "last_used")
    val lastUsed: Long,

    @ColumnInfo(name = "confidence")
    val confidence: Float,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "notes")
    val notes: String,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean,

    @ColumnInfo(name = "byte_count")
    val byteCount: Int,

    @ColumnInfo(name = "min_value")
    val minValue: Double?,

    @ColumnInfo(name = "max_value")
    val maxValue: Double?
) {
    /**
     * Convierte la entidad a modelo de dominio.
     */
    fun toDomain(): CustomPID {
        val vehicleModels = try {
            parseVehicleModelsJson(vehicleModelsJson)
        } catch (e: Exception) {
            emptyList()
        }

        return CustomPID(
            id = id,
            pid = pid,
            name = name,
            command = command,
            formula = formula,
            unit = unit,
            category = try {
                PIDCategory.valueOf(category)
            } catch (e: Exception) {
                PIDCategory.GENERAL
            },
            vehicleModels = vehicleModels,
            discoveryDate = discoveryDate,
            lastUsed = lastUsed,
            confidence = confidence,
            source = try {
                PIDSource.valueOf(source)
            } catch (e: Exception) {
                PIDSource.USER
            },
            notes = notes,
            isEnabled = isEnabled,
            byteCount = byteCount,
            minValue = minValue,
            maxValue = maxValue
        )
    }

    companion object {
        /**
         * Crea una entidad desde un modelo de dominio.
         */
        fun fromDomain(customPID: CustomPID): CustomPIDEntity {
            return CustomPIDEntity(
                id = customPID.id,
                pid = customPID.pid,
                name = customPID.name,
                command = customPID.command,
                formula = customPID.formula,
                unit = customPID.unit,
                category = customPID.category.name,
                vehicleModelsJson = vehicleModelsToJson(customPID.vehicleModels),
                discoveryDate = customPID.discoveryDate,
                lastUsed = customPID.lastUsed,
                confidence = customPID.confidence,
                source = customPID.source.name,
                notes = customPID.notes,
                isEnabled = customPID.isEnabled,
                byteCount = customPID.byteCount,
                minValue = customPID.minValue,
                maxValue = customPID.maxValue
            )
        }

        /**
         * Convierte lista de VINs a JSON.
         */
        private fun vehicleModelsToJson(vehicleModels: List<String>): String {
            if (vehicleModels.isEmpty()) return "[]"
            return vehicleModels.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]")
        }

        /**
         * Parsea JSON de VINs.
         */
        private fun parseVehicleModelsJson(json: String): List<String> {
            if (json.isBlank() || json == "[]") return emptyList()

            return try {
                json.trim()
                    .removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotBlank() }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
