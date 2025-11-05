package com.fleetcare.obd.domain.model

/**
 * Estadísticas detalladas de un proceso de escaneo de PIDs.
 *
 * Contiene métricas de rendimiento, calidad de respuestas, y análisis
 * de cobertura de PIDs detectados.
 *
 * @property totalPIDsTested Número total de PIDs probados
 * @property successfulPIDs PIDs que respondieron correctamente
 * @property failedPIDs PIDs que no respondieron o dieron error
 * @property successRate Tasa de éxito (0.0 a 1.0)
 * @property averageResponseTime Tiempo promedio de respuesta por PID en ms
 * @property fastestResponse Tiempo más rápido observado en ms
 * @property slowestResponse Tiempo más lento observado en ms
 * @property totalScanDuration Duración total del escaneo en ms
 * @property timeoutCount Número de timeouts ocurridos
 * @property errorCount Número de errores (NO DATA, ?, etc.)
 * @property pidsByMode PIDs encontrados agrupados por modo
 * @property dataTypeDistribution Distribución de tipos de datos detectados
 * @property qualityScore Score de calidad del escaneo (0-100)
 */
data class ScanStatistics(
    val totalPIDsTested: Int = 0,
    val successfulPIDs: Int = 0,
    val failedPIDs: Int = 0,
    val successRate: Float = 0f,
    val averageResponseTime: Long = 0L,  // ms
    val fastestResponse: Long = 0L,      // ms
    val slowestResponse: Long = 0L,      // ms
    val totalScanDuration: Long = 0L,    // ms
    val timeoutCount: Int = 0,
    val errorCount: Int = 0,
    val pidsByMode: Map<String, Int> = emptyMap(),
    val dataTypeDistribution: Map<PIDDataType, Int> = emptyMap(),
    val qualityScore: Int = 0
) {
    /**
     * Devuelve el porcentaje de éxito formateado.
     */
    fun getSuccessRatePercentage(): String {
        return "${(successRate * 100).toInt()}%"
    }

    /**
     * Devuelve la duración total formateada (mm:ss).
     */
    fun getFormattedDuration(): String {
        val totalSeconds = totalScanDuration / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Devuelve el modo con más PIDs detectados.
     */
    fun getMostProductiveMode(): String? {
        return pidsByMode.maxByOrNull { it.value }?.key
    }

    /**
     * Devuelve el tipo de dato más común.
     */
    fun getMostCommonDataType(): PIDDataType? {
        return dataTypeDistribution.maxByOrNull { it.value }?.key
    }

    /**
     * Verifica si el escaneo fue exitoso (>50% success rate).
     */
    fun isSuccessful(): Boolean {
        return successRate >= 0.5f && successfulPIDs > 0
    }

    /**
     * Verifica si el escaneo fue de alta calidad (>80% success, avg time <300ms).
     */
    fun isHighQuality(): Boolean {
        return successRate >= 0.8f && averageResponseTime < 300L
    }

    /**
     * Genera un resumen legible de las estadísticas.
     */
    fun getSummary(): String = buildString {
        appendLine("Scan Summary:")
        appendLine("• PIDs tested: $totalPIDsTested")
        appendLine("• Successful: $successfulPIDs (${getSuccessRatePercentage()})")
        appendLine("• Failed: $failedPIDs")
        appendLine("• Duration: ${getFormattedDuration()}")
        appendLine("• Avg response: ${averageResponseTime}ms")
        appendLine("• Quality score: $qualityScore/100")

        if (pidsByMode.isNotEmpty()) {
            appendLine("\nPIDs by mode:")
            pidsByMode.forEach { (mode, count) ->
                appendLine("  Mode $mode: $count PIDs")
            }
        }
    }

    companion object {
        /**
         * Crea estadísticas a partir de una lista de resultados de escaneo.
         */
        fun fromScanResults(results: List<ScanResult>, totalDuration: Long): ScanStatistics {
            if (results.isEmpty()) {
                return ScanStatistics()
            }

            val successful = results.filter { it.success }
            val failed = results.filter { !it.success }
            val successRate = successful.size.toFloat() / results.size.toFloat()

            val responseTimes = successful.map { it.responseTime }.filter { it > 0 }
            val avgResponseTime = if (responseTimes.isNotEmpty()) {
                responseTimes.average().toLong()
            } else 0L

            val fastestResponse = responseTimes.minOrNull() ?: 0L
            val slowestResponse = responseTimes.maxOrNull() ?: 0L

            val timeouts = failed.count { it.rawResponse.contains("TIMEOUT", ignoreCase = true) }
            val errors = failed.count {
                it.rawResponse.contains("NO DATA", ignoreCase = true) ||
                it.rawResponse.contains("?") ||
                it.rawResponse.contains("ERROR", ignoreCase = true)
            }

            // Agrupar por modo
            val pidsByMode = successful.groupBy { it.mode }.mapValues { it.value.size }

            // Distribución de tipos de datos (usando metadata si existe)
            val dataTypeDistribution = successful
                .mapNotNull { it.metadata?.detectedType }
                .groupingBy { it }
                .eachCount()

            // Calcular quality score (0-100)
            val qualityScore = calculateQualityScore(
                successRate = successRate,
                avgResponseTime = avgResponseTime,
                timeoutCount = timeouts,
                totalPIDs = successful.size
            )

            return ScanStatistics(
                totalPIDsTested = results.size,
                successfulPIDs = successful.size,
                failedPIDs = failed.size,
                successRate = successRate,
                averageResponseTime = avgResponseTime,
                fastestResponse = fastestResponse,
                slowestResponse = slowestResponse,
                totalScanDuration = totalDuration,
                timeoutCount = timeouts,
                errorCount = errors,
                pidsByMode = pidsByMode,
                dataTypeDistribution = dataTypeDistribution,
                qualityScore = qualityScore
            )
        }

        /**
         * Calcula un score de calidad basado en múltiples factores.
         */
        private fun calculateQualityScore(
            successRate: Float,
            avgResponseTime: Long,
            timeoutCount: Int,
            totalPIDs: Int
        ): Int {
            var score = 0

            // Success rate (0-50 puntos)
            score += (successRate * 50).toInt()

            // Response time (0-30 puntos)
            val timeScore = when {
                avgResponseTime < 200L -> 30
                avgResponseTime < 300L -> 25
                avgResponseTime < 500L -> 20
                avgResponseTime < 1000L -> 10
                else -> 5
            }
            score += timeScore

            // PIDs encontrados (0-10 puntos)
            val pidsScore = when {
                totalPIDs >= 50 -> 10
                totalPIDs >= 30 -> 8
                totalPIDs >= 20 -> 6
                totalPIDs >= 10 -> 4
                totalPIDs >= 5 -> 2
                else -> 0
            }
            score += pidsScore

            // Penalización por timeouts (0-10 puntos negativos)
            val timeoutPenalty = when {
                timeoutCount == 0 -> 10
                timeoutCount <= 5 -> 5
                timeoutCount <= 10 -> 2
                else -> 0
            }
            score += timeoutPenalty

            return score.coerceIn(0, 100)
        }
    }
}

/**
 * Comparación entre estadísticas de diferentes escaneos.
 * Útil para visualizar mejoras o degradaciones entre scans.
 */
data class ScanStatisticsComparison(
    val previous: ScanStatistics,
    val current: ScanStatistics
) {
    /**
     * Diferencia en PIDs encontrados.
     */
    val pidDifference: Int = current.successfulPIDs - previous.successfulPIDs

    /**
     * Diferencia en success rate.
     */
    val successRateDifference: Float = current.successRate - previous.successRate

    /**
     * Diferencia en tiempo promedio de respuesta.
     */
    val responseTimeDifference: Long = current.averageResponseTime - previous.averageResponseTime

    /**
     * Diferencia en quality score.
     */
    val qualityScoreDifference: Int = current.qualityScore - previous.qualityScore

    /**
     * Verifica si hubo mejora en el escaneo actual.
     */
    fun hasImproved(): Boolean {
        return pidDifference > 0 || qualityScoreDifference > 0
    }

    /**
     * Genera un resumen de la comparación.
     */
    fun getSummary(): String = buildString {
        appendLine("Scan Comparison:")
        appendLine("• PIDs: ${formatDifference(pidDifference)}")
        appendLine("• Success rate: ${formatPercentageDifference(successRateDifference)}")
        appendLine("• Avg response time: ${formatTimeDifference(responseTimeDifference)}")
        appendLine("• Quality score: ${formatDifference(qualityScoreDifference)}")

        if (hasImproved()) {
            appendLine("\n✓ Overall improvement detected")
        } else {
            appendLine("\n⚠ No significant improvement")
        }
    }

    private fun formatDifference(diff: Int): String {
        return when {
            diff > 0 -> "+$diff"
            diff < 0 -> "$diff"
            else -> "0 (no change)"
        }
    }

    private fun formatPercentageDifference(diff: Float): String {
        val percentage = (diff * 100).toInt()
        return when {
            percentage > 0 -> "+$percentage%"
            percentage < 0 -> "$percentage%"
            else -> "0% (no change)"
        }
    }

    private fun formatTimeDifference(diff: Long): String {
        return when {
            diff > 0 -> "+${diff}ms (slower)"
            diff < 0 -> "${diff}ms (faster)"
            else -> "0ms (no change)"
        }
    }
}
