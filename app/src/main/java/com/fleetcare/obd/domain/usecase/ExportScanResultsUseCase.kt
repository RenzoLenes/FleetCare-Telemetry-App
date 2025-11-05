package com.fleetcare.obd.domain.usecase

import com.fleetcare.obd.domain.model.*
import com.fleetcare.obd.domain.repository.UniversalScanRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Use Case para exportar resultados de escaneo a diferentes formatos.
 *
 * Soporta exportación a JSON y CSV con diferentes opciones de filtrado.
 */
class ExportScanResultsUseCase @Inject constructor(
    private val scanRepository: UniversalScanRepository
) {
    /**
     * Exporta resultados de una sesión.
     *
     * @param sessionId ID de la sesión
     * @param exportType Tipo de exportación (exitosos, todos, fallidos, estadísticas)
     * @param format Formato (JSON, CSV)
     * @return Contenido exportado como String
     */
    suspend operator fun invoke(
        sessionId: String,
        exportType: ExportType = ExportType.SUCCESSFUL_ONLY,
        format: ExportFormat = ExportFormat.JSON
    ): String {
        // Obtener resultados del repositorio
        val results = scanRepository.getResults(sessionId).first()
        val statistics = scanRepository.getStatistics(sessionId)

        // Exportar usando el método interno
        return exportResults(results, exportType, format, statistics)
    }

    /**
     * Exporta resultados directamente (sin necesidad de sesión).
     *
     * @param results Lista de resultados
     * @param exportType Tipo de exportación
     * @param format Formato
     * @param statistics Estadísticas opcionales
     * @return Contenido exportado
     */
    fun exportResults(
        results: List<ScanResult>,
        exportType: ExportType = ExportType.SUCCESSFUL_ONLY,
        format: ExportFormat = ExportFormat.JSON,
        statistics: ScanStatistics? = null
    ): String {
        val filteredResults = when (exportType) {
            ExportType.SUCCESSFUL_ONLY -> results.filter { it.success }
            ExportType.FAILED_ONLY -> results.filter { !it.success }
            ExportType.ALL_RESULTS -> results
            ExportType.STATISTICS_ONLY -> emptyList()
        }

        return when (format) {
            ExportFormat.JSON -> exportToJSON(filteredResults, statistics, exportType)
            ExportFormat.CSV -> exportToCSV(filteredResults)
            ExportFormat.QR_CODE -> exportMetadata(filteredResults)
        }
    }

    /**
     * Exporta a formato JSON.
     */
    private fun exportToJSON(
        results: List<ScanResult>,
        statistics: ScanStatistics?,
        exportType: ExportType
    ): String {
        val json = JSONObject()

        // Metadata
        json.put("exportedAt", System.currentTimeMillis())
        json.put("exportType", exportType.name)
        json.put("totalResults", results.size)

        // Estadísticas
        if (statistics != null || exportType == ExportType.STATISTICS_ONLY) {
            val statsJson = JSONObject()
            statistics?.let {
                statsJson.put("totalPIDsTested", it.totalPIDsTested)
                statsJson.put("successfulPIDs", it.successfulPIDs)
                statsJson.put("failedPIDs", it.failedPIDs)
                statsJson.put("successRate", it.successRate)
                statsJson.put("averageResponseTime", it.averageResponseTime)
                statsJson.put("fastestResponse", it.fastestResponse)
                statsJson.put("slowestResponse", it.slowestResponse)
                statsJson.put("totalScanDuration", it.totalScanDuration)
                statsJson.put("timeoutCount", it.timeoutCount)
                statsJson.put("errorCount", it.errorCount)
                statsJson.put("qualityScore", it.qualityScore)

                // PIDs por modo
                val pidsByMode = JSONObject()
                it.pidsByMode.forEach { (mode, count) ->
                    pidsByMode.put("mode$mode", count)
                }
                statsJson.put("pidsByMode", pidsByMode)

                // Distribución de tipos de datos
                val dataTypeDistribution = JSONObject()
                it.dataTypeDistribution.forEach { (type, count) ->
                    dataTypeDistribution.put(type.name, count)
                }
                statsJson.put("dataTypeDistribution", dataTypeDistribution)
            }
            json.put("statistics", statsJson)
        }

        // Resultados
        if (exportType != ExportType.STATISTICS_ONLY) {
            val resultsArray = JSONArray()
            results.forEach { result ->
                resultsArray.put(JSONObject(result.toJsonMap()))
            }
            json.put("results", resultsArray)
        }

        return json.toString(2)  // Pretty print con indentación 2
    }

    /**
     * Exporta a formato CSV.
     */
    private fun exportToCSV(results: List<ScanResult>): String {
        val csv = StringBuilder()

        // Header
        csv.appendLine("Mode,PID,Command,Success,Response,Bytes,ByteCount,ResponseTime,IsStandard,Timestamp")

        // Rows
        results.forEach { result ->
            csv.append(result.mode).append(",")
            csv.append(result.pid).append(",")
            csv.append(result.command).append(",")
            csv.append(result.success).append(",")
            csv.append("\"${result.rawResponse.replace("\"", "\"\"")}\"").append(",")  // Escape quotes
            csv.append("\"${result.dataBytes.joinToString(" ") { "%02X".format(it.toUByte().toInt()) }}\"").append(",")
            csv.append(result.byteCount).append(",")
            csv.append(result.responseTime).append(",")
            csv.append(result.isStandardPID).append(",")
            csv.append(result.timestamp)
            csv.appendLine()
        }

        return csv.toString()
    }

    /**
     * Genera un resumen de texto plano de los resultados.
     */
    fun generateTextSummary(
        results: List<ScanResult>,
        statistics: ScanStatistics? = null
    ): String {
        return buildString {
            appendLine("=== OBD PID Scan Results ===")
            appendLine()

            if (statistics != null) {
                appendLine(statistics.getSummary())
                appendLine()
            }

            appendLine("Results by Mode:")
            val byMode = results.groupBy { it.mode }
            byMode.forEach { (mode, modeResults) ->
                val successful = modeResults.count { it.success }
                appendLine("  Mode $mode: $successful/${modeResults.size} PIDs")
            }
            appendLine()

            appendLine("Successful PIDs:")
            results.filter { it.success }.forEach { result ->
                appendLine("  ${result.mode}-${result.pid}: ${result.metadata?.name ?: "Unknown"} (${result.responseTime}ms)")
            }
        }
    }

    /**
     * Exporta solo metadata de PIDs (para compartir descubrimientos).
     */
    fun exportMetadata(results: List<ScanResult>): String {
        val json = JSONObject()
        val metadataArray = JSONArray()

        results.filter { it.success && it.metadata != null }.forEach { result ->
            val meta = result.metadata!!
            val metaJson = JSONObject()
            metaJson.put("mode", meta.mode)
            metaJson.put("pid", meta.pid)
            metaJson.put("name", meta.name)
            metaJson.put("unit", meta.unit)
            metaJson.put("formula", meta.formula)
            metaJson.put("detectedType", meta.detectedType.name)
            metaJson.put("responseLength", meta.responseLength)
            metaJson.put("isStandard", meta.isStandard)
            metadataArray.put(metaJson)
        }

        json.put("metadata", metadataArray)
        json.put("exportedAt", System.currentTimeMillis())
        json.put("totalPIDs", metadataArray.length())

        return json.toString(2)
    }
}
