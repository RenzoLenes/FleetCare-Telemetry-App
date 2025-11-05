package com.fleetcare.obd.domain.model

/**
 * Modelo de dominio para respuestas RAW de comandos OBD-II.
 *
 * Captura la respuesta completa sin procesar para permitir:
 * - Análisis posterior de patrones
 * - Inferencia de fórmulas
 * - Debugging de problemas de parseo
 * - Histórico completo de comunicación
 *
 * @property id ID único de la respuesta
 * @property timestamp Timestamp de captura (milisegundos desde epoch)
 * @property vehicleId Identificador del vehículo (MAC address)
 * @property sessionId ID de sesión de lectura
 * @property command Comando OBD enviado (ej: "010C" para RPM)
 * @property rawResponse Respuesta completa sin procesar (ej: "41 0C 1A F8\r\n>")
 * @property cleanResponse Respuesta limpiada (sin espacios, \r\n, >) (ej: "410C1AF8")
 * @property dataBytes Bytes de datos extraídos como array (ej: [0x1A, 0xF8])
 * @property parsedValue Valor parseado si fue exitoso (ej: 1726.0 RPM)
 * @property parseSuccess Indica si el parseo fue exitoso
 * @property errorMessage Mensaje de error si el parseo falló
 * @property latencyMs Latencia de la respuesta en milisegundos
 * @property attemptNumber Número de intento (para retry)
 * @property protocolUsed Protocolo detectado (ej: "ISO 15765-4 CAN")
 */
data class RawOBDResponse(
    val id: Long = 0,
    val timestamp: Long,
    val vehicleId: String,
    val sessionId: String,
    val command: String,
    val rawResponse: String,
    val cleanResponse: String,
    val dataBytes: ByteArray,
    val parsedValue: Double?,
    val parseSuccess: Boolean,
    val errorMessage: String?,
    val latencyMs: Long,
    val attemptNumber: Int = 1,
    val protocolUsed: String? = null
) {
    companion object {
        /**
         * Crea una instancia vacía para inicialización.
         */
        fun empty() = RawOBDResponse(
            timestamp = 0L,
            vehicleId = "",
            sessionId = "",
            command = "",
            rawResponse = "",
            cleanResponse = "",
            dataBytes = ByteArray(0),
            parsedValue = null,
            parseSuccess = false,
            errorMessage = null,
            latencyMs = 0L
        )
    }

    /**
     * Retorna una representación legible para debugging.
     */
    fun toDebugString(): String {
        return buildString {
            appendLine("RawOBDResponse {")
            appendLine("  timestamp: $timestamp (${java.util.Date(timestamp)})")
            appendLine("  command: $command")
            appendLine("  rawResponse: $rawResponse")
            appendLine("  cleanResponse: $cleanResponse")
            appendLine("  dataBytes: [${dataBytes.joinToString { "0x${it.toString(16).uppercase().padStart(2, '0')}" }}]")
            appendLine("  parsedValue: $parsedValue")
            appendLine("  parseSuccess: $parseSuccess")
            appendLine("  errorMessage: $errorMessage")
            appendLine("  latencyMs: ${latencyMs}ms")
            appendLine("  protocolUsed: $protocolUsed")
            appendLine("}")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawOBDResponse

        if (id != other.id) return false
        if (timestamp != other.timestamp) return false
        if (vehicleId != other.vehicleId) return false
        if (command != other.command) return false
        if (!dataBytes.contentEquals(other.dataBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + vehicleId.hashCode()
        result = 31 * result + command.hashCode()
        result = 31 * result + dataBytes.contentHashCode()
        return result
    }
}
