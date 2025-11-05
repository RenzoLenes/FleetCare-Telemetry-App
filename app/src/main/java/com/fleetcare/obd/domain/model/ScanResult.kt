package com.fleetcare.obd.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Resultado del escaneo de un PID individual.
 *
 * Universal PID Scanner - Sprint 1
 *
 * Representa el resultado de intentar leer un PID específico durante
 * el escaneo multi-modo (01, 02, 09, 22).
 *
 * @property mode Modo OBD (01, 02, 09, 22)
 * @property pid PID en formato hexadecimal (ej: "0C" para RPM)
 * @property command Comando completo enviado (ej: "010C")
 * @property success Indica si el PID respondió correctamente
 * @property rawResponse Respuesta RAW del adaptador OBD-II
 * @property dataBytes Bytes de datos extraídos (sin header)
 * @property byteCount Número de bytes en la respuesta
 * @property interpretation Interpretación automática del valor (si es posible)
 * @property timestamp Momento del escaneo
 * @property responseTime Tiempo de respuesta en milisegundos
 * @property detectedType Tipo de dato detectado automáticamente (deprecated, use metadata.detectedType)
 * @property isStandardPID Indica si es un PID estándar OBD-II
 * @property metadata Metadata completa del PID (auto-generada si no se provee)
 * @property vehicleId ID del vehículo escaneado
 */
data class ScanResult(
    val mode: String = "01",
    val pid: String,
    val command: String,
    val success: Boolean,
    val rawResponse: String,
    val dataBytes: ByteArray = byteArrayOf(),
    val byteCount: Int = 0,
    val interpretation: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val responseTime: Long = 0,  // Renamed from latencyMs for consistency
    @Deprecated("Use metadata.detectedType instead")
    val detectedType: DetectedDataType? = null,
    val isStandardPID: Boolean = true,
    val metadata: PIDMetadata? = null,
    val vehicleId: String = ""
) {
    /**
     * Legacy compatibility: latencyMs alias.
     */
    @Deprecated("Use responseTime instead", ReplaceWith("responseTime"))
    val latencyMs: Long get() = responseTime
    /**
     * Retorna el PID en formato decimal.
     */
    fun getPIDDecimal(): Int {
        return pid.toIntOrNull(16) ?: 0
    }

    /**
     * Retorna una descripción legible del resultado.
     */
    fun getDescription(): String {
        return when {
            !success -> "Sin respuesta o error"
            interpretation != null -> interpretation
            byteCount > 0 -> {
                val hexBytes = dataBytes.joinToString(" ") { "%02X".format(it.toUByte().toInt()) }
                "$byteCount bytes: $hexBytes"
            }
            else -> "Respuesta vacía"
        }
    }

    /**
     * Retorna la categoría del PID basada en su rango.
     */
    fun getCategory(): String {
        val pidInt = getPIDDecimal()
        return when (pidInt) {
            in 0x00..0x20 -> "Control y Motor"
            in 0x21..0x40 -> "Combustible y Aire"
            in 0x41..0x60 -> "Temperatura y Presión"
            in 0x61..0x80 -> "Sensores Avanzados"
            in 0x81..0xA0 -> "Sistema de Emisiones"
            in 0xA1..0xC0 -> "Propietario del Fabricante"
            in 0xC1..0xFF -> "Propietario Extendido"
            else -> "Desconocido"
        }
    }

    /**
     * Convierte el resultado a formato JSON legible.
     */
    fun toJsonMap(): Map<String, Any?> {
        return mapOf(
            "pid" to pid,
            "pidDecimal" to getPIDDecimal(),
            "command" to command,
            "success" to success,
            "response" to rawResponse,
            "bytes" to dataBytes.map { it.toUByte().toInt() },
            "byteCount" to byteCount,
            "interpretation" to interpretation,
            "timestamp" to timestamp,
            "timestampFormatted" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date(timestamp)),
            "latencyMs" to latencyMs,
            "detectedType" to detectedType?.name,
            "category" to getCategory(),
            "isStandard" to isStandardPID
        )
    }

    /**
     * Retorna un resumen corto para logging.
     */
    fun toSummary(): String {
        val status = if (success) "✓" else "✗"
        val desc = if (success) {
            metadata?.name ?: interpretation ?: "$byteCount bytes"
        } else {
            "NO DATA"
        }
        return "$status Mode $mode PID $pid: $desc (${responseTime}ms)"
    }

    /**
     * Devuelve el identificador único del resultado (mode_pid).
     */
    fun getUniqueId(): String = "${mode}_${pid.uppercase()}"

    /**
     * Crea un PIDMetadata a partir de este resultado (si no existe).
     */
    fun toMetadata(): PIDMetadata {
        return metadata ?: PIDMetadataHelper.createAutoDetected(
            mode = mode,
            pid = pid,
            rawResponse = rawResponse,
            responseTime = responseTime
        )
    }

    // Override equals/hashCode para comparación correcta de ByteArray
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScanResult

        if (mode != other.mode) return false
        if (pid != other.pid) return false
        if (command != other.command) return false
        if (success != other.success) return false
        if (rawResponse != other.rawResponse) return false
        if (!dataBytes.contentEquals(other.dataBytes)) return false
        if (byteCount != other.byteCount) return false
        if (interpretation != other.interpretation) return false
        if (timestamp != other.timestamp) return false
        if (vehicleId != other.vehicleId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mode.hashCode()
        result = 31 * result + pid.hashCode()
        result = 31 * result + command.hashCode()
        result = 31 * result + success.hashCode()
        result = 31 * result + rawResponse.hashCode()
        result = 31 * result + dataBytes.contentHashCode()
        result = 31 * result + byteCount
        result = 31 * result + (interpretation?.hashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + vehicleId.hashCode()
        return result
    }
}

/**
 * Progreso del escaneo universal multi-modo.
 *
 * @property currentMode Modo que se está escaneando actualmente
 * @property currentPID PID que se está escaneando actualmente
 * @property totalPIDs Total de PIDs a escanear
 * @property scannedPIDs PIDs escaneados hasta ahora
 * @property currentResult Resultado del PID actual
 * @property successCount Cantidad de PIDs exitosos hasta ahora
 * @property failedCount Cantidad de PIDs fallidos hasta ahora
 * @property skippedCount Cantidad de PIDs saltados (intelligent skipping)
 * @property elapsedTimeMs Tiempo transcurrido desde el inicio
 * @property estimatedTimeRemainingMs Tiempo estimado restante
 * @property currentPhase Fase actual del escaneo
 */
data class ScanProgress(
    val currentMode: String = "01",
    val currentPID: Int,
    val totalPIDs: Int = 255,
    val scannedPIDs: Int = 0,
    val currentResult: ScanResult? = null,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val skippedCount: Int = 0,
    val elapsedTimeMs: Long = 0,
    val estimatedTimeRemainingMs: Long = 0,
    val currentPhase: String = "Scanning"
) {
    /**
     * Retorna el porcentaje de progreso (0-100).
     */
    fun getProgressPercent(): Int {
        if (totalPIDs == 0) return 0
        return ((scannedPIDs.toFloat() / totalPIDs.toFloat()) * 100).toInt()
    }

    /**
     * Retorna el progreso en formato "N/total".
     */
    fun getProgressText(): String {
        return "$scannedPIDs/$totalPIDs"
    }

    /**
     * Retorna la tasa de éxito actual.
     */
    fun getSuccessRate(): Float {
        val tested = successCount + failedCount
        if (tested == 0) return 0f
        return successCount.toFloat() / tested.toFloat()
    }

    /**
     * Devuelve un resumen del progreso.
     */
    fun getSummary(): String {
        return buildString {
            append("Mode $currentMode | ")
            append("${getProgressPercent()}% | ")
            append("Success: $successCount | ")
            append("Failed: $failedCount")
            if (skippedCount > 0) {
                append(" | Skipped: $skippedCount")
            }
        }
    }

    /**
     * Retorna el tiempo transcurrido en formato legible.
     */
    fun getElapsedTimeFormatted(): String {
        val seconds = elapsedTimeMs / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, remainingSeconds)
    }

    /**
     * Retorna el tiempo estimado restante en formato legible.
     */
    fun getEstimatedTimeFormatted(): String {
        val seconds = estimatedTimeRemainingMs / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, remainingSeconds)
    }
}

/**
 * Filtro para resultados del escaneo.
 */
enum class ScanFilter {
    ALL,
    SUCCESS_ONLY,
    FAILED_ONLY
}

/**
 * Estado del escáner.
 */
enum class ScannerState {
    IDLE,
    SCANNING,
    PAUSED,
    COMPLETED,
    ERROR
}
