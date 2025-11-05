package com.fleetcare.obd.domain.model

/**
 * Sesión completa de escaneo universal de PIDs.
 *
 * Representa un escaneo completo de un vehículo, incluyendo configuración,
 * resultados, estadísticas y estado.
 *
 * @property sessionId ID único de la sesión
 * @property vehicleId ID del vehículo escaneado
 * @property config Configuración utilizada para el escaneo
 * @property state Estado actual de la sesión
 * @property results Lista de resultados de PIDs escaneados
 * @property statistics Estadísticas del escaneo
 * @property startTime Timestamp de inicio del escaneo
 * @property endTime Timestamp de fin del escaneo (null si aún está en progreso)
 * @property errorMessage Mensaje de error si el escaneo falló
 */
data class ScanSession(
    val sessionId: String,
    val vehicleId: String,
    val config: UniversalScanConfig,
    val state: ScannerState = ScannerState.IDLE,
    val results: List<ScanResult> = emptyList(),
    val statistics: ScanStatistics? = null,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val errorMessage: String? = null
) {
    /**
     * Devuelve la duración total de la sesión en milisegundos.
     */
    fun getDuration(): Long {
        val end = endTime ?: System.currentTimeMillis()
        return end - startTime
    }

    /**
     * Devuelve la duración total formateada (mm:ss).
     */
    fun getFormattedDuration(): String {
        val totalSeconds = getDuration() / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Verifica si la sesión está activa.
     */
    fun isActive(): Boolean {
        return state in listOf(ScannerState.SCANNING, ScannerState.PAUSED)
    }

    /**
     * Verifica si la sesión está completa.
     */
    fun isCompleted(): Boolean {
        return state == ScannerState.COMPLETED
    }

    /**
     * Verifica si la sesión tiene error.
     */
    fun hasError(): Boolean {
        return state == ScannerState.ERROR || errorMessage != null
    }

    /**
     * Devuelve los resultados exitosos.
     */
    fun getSuccessfulResults(): List<ScanResult> {
        return results.filter { it.success }
    }

    /**
     * Devuelve los resultados fallidos.
     */
    fun getFailedResults(): List<ScanResult> {
        return results.filter { !it.success }
    }

    /**
     * Devuelve los resultados agrupados por modo.
     */
    fun getResultsByMode(): Map<String, List<ScanResult>> {
        return results.groupBy { it.mode }
    }

    /**
     * Devuelve el número de PIDs soportados encontrados.
     */
    fun getSupportedPIDsCount(): Int {
        return getSuccessfulResults().size
    }

    /**
     * Genera un resumen de la sesión.
     */
    fun getSummary(): String = buildString {
        appendLine("Scan Session $sessionId")
        appendLine("Vehicle: $vehicleId")
        appendLine("State: $state")
        appendLine("Duration: ${getFormattedDuration()}")
        appendLine("Results: ${results.size} PIDs scanned")
        appendLine("Successful: ${getSuccessfulResults().size}")
        appendLine("Failed: ${getFailedResults().size}")

        if (statistics != null) {
            appendLine("\nStatistics:")
            appendLine(statistics.getSummary())
        }

        if (hasError() && errorMessage != null) {
            appendLine("\nError: $errorMessage")
        }

        val byMode = getResultsByMode()
        if (byMode.isNotEmpty()) {
            appendLine("\nResults by mode:")
            byMode.forEach { (mode, modeResults) ->
                val successful = modeResults.count { it.success }
                appendLine("  Mode $mode: $successful/${modeResults.size} PIDs")
            }
        }
    }

    /**
     * Crea una nueva sesión con resultados actualizados.
     */
    fun withResults(newResults: List<ScanResult>): ScanSession {
        return copy(results = newResults)
    }

    /**
     * Crea una nueva sesión con estado actualizado.
     */
    fun withState(newState: ScannerState): ScanSession {
        return copy(state = newState)
    }

    /**
     * Crea una nueva sesión marcada como completa.
     */
    fun complete(): ScanSession {
        val stats = ScanStatistics.fromScanResults(results, getDuration())
        return copy(
            state = ScannerState.COMPLETED,
            endTime = System.currentTimeMillis(),
            statistics = stats
        )
    }

    /**
     * Crea una nueva sesión marcada como error.
     */
    fun error(message: String): ScanSession {
        return copy(
            state = ScannerState.ERROR,
            endTime = System.currentTimeMillis(),
            errorMessage = message
        )
    }

    companion object {
        /**
         * Crea una nueva sesión de escaneo.
         */
        fun create(
            vehicleId: String,
            config: UniversalScanConfig
        ): ScanSession {
            val sessionId = generateSessionId(vehicleId)
            return ScanSession(
                sessionId = sessionId,
                vehicleId = vehicleId,
                config = config,
                state = ScannerState.IDLE,
                startTime = System.currentTimeMillis()
            )
        }

        /**
         * Genera un ID único de sesión.
         */
        private fun generateSessionId(vehicleId: String): String {
            val timestamp = System.currentTimeMillis()
            return "${vehicleId}_${timestamp}"
        }
    }
}

/**
 * Tipo de exportación de resultados.
 */
enum class ExportType {
    /**
     * Exportar solo PIDs exitosos.
     */
    SUCCESSFUL_ONLY,

    /**
     * Exportar todos los PIDs (exitosos y fallidos).
     */
    ALL_RESULTS,

    /**
     * Exportar solo PIDs fallidos (para debugging).
     */
    FAILED_ONLY,

    /**
     * Exportar resumen estadístico.
     */
    STATISTICS_ONLY
}
