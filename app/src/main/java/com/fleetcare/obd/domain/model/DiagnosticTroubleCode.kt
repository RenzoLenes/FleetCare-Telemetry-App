package com.fleetcare.obd.domain.model

import java.util.Date

/**
 * Modelo de dominio para Diagnostic Trouble Codes (DTCs).
 *
 * Representa un código de error diagnosticado por el ECU del vehículo.
 */
data class DiagnosticTroubleCode(
    /**
     * Código DTC en formato estándar (ej: P0301, C0030, B0001, U0100).
     */
    val code: String,

    /**
     * Descripción del código de error.
     */
    val description: String,

    /**
     * Indica si es un código pendiente (no confirmado aún).
     * false = Código activo/confirmado (Mode 03)
     * true = Código pendiente (Mode 07)
     */
    val isPending: Boolean = false,

    /**
     * Timestamp de cuando se detectó el código.
     */
    val timestamp: Date = Date()
) {

    /**
     * Tipo de código basado en el primer carácter.
     */
    val type: DTCType
        get() = when (code.firstOrNull()) {
            'P' -> DTCType.POWERTRAIN
            'C' -> DTCType.CHASSIS
            'B' -> DTCType.BODY
            'U' -> DTCType.NETWORK
            else -> DTCType.UNKNOWN
        }

    /**
     * Indica si es un código genérico (SAE) o específico del fabricante.
     * Segundo carácter '0' o '2' = Genérico
     * Segundo carácter '1' o '3' = Específico del fabricante
     */
    val isGeneric: Boolean
        get() = code.length >= 2 && (code[1] == '0' || code[1] == '2')

    /**
     * Severidad estimada basada en el código.
     */
    val severity: DTCSeverity
        get() = when {
            // Fallos de encendido son críticos
            code.startsWith("P030") -> DTCSeverity.CRITICAL
            // Problemas de transmisión
            code.startsWith("P07") -> DTCSeverity.HIGH
            // Sistema de emisiones
            code.startsWith("P04") -> DTCSeverity.MEDIUM
            // Sensores
            code.startsWith("P01") -> DTCSeverity.MEDIUM
            // ABS
            code.startsWith("C0") -> DTCSeverity.HIGH
            // Airbag
            code.startsWith("B000") -> DTCSeverity.CRITICAL
            // Comunicación
            code.startsWith("U0") -> DTCSeverity.MEDIUM
            // Pendientes son de baja prioridad
            isPending -> DTCSeverity.LOW
            // Por defecto
            else -> DTCSeverity.MEDIUM
        }

    /**
     * Estado del código como texto legible.
     */
    val status: String
        get() = if (isPending) "Pendiente" else "Activo"

    /**
     * Descripción completa con código y tipo.
     */
    val fullDescription: String
        get() = "$code - ${type.displayName}: $description"

    companion object {
        /**
         * Crea un DTC vacío para estados sin errores.
         */
        fun noErrors(): DiagnosticTroubleCode {
            return DiagnosticTroubleCode(
                code = "P0000",
                description = "Sin errores detectados",
                isPending = false
            )
        }
    }
}

/**
 * Tipo de código DTC.
 */
enum class DTCType(val displayName: String) {
    POWERTRAIN("Tren Motriz"),
    CHASSIS("Chasis"),
    BODY("Carrocería"),
    NETWORK("Red"),
    UNKNOWN("Desconocido")
}

/**
 * Severidad del código DTC.
 */
enum class DTCSeverity(val displayName: String, val colorHex: String) {
    LOW("Baja", "#4CAF50"),        // Verde
    MEDIUM("Media", "#FF9800"),    // Naranja
    HIGH("Alta", "#FF5722"),       // Rojo claro
    CRITICAL("Crítica", "#D32F2F") // Rojo oscuro
}
