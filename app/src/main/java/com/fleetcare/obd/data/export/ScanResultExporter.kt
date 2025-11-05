package com.fleetcare.obd.data.export

import com.fleetcare.obd.domain.model.ExportFormat
import com.fleetcare.obd.domain.model.ScanResult
import com.fleetcare.obd.domain.model.ScanSession
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Exportador de resultados de scan a diferentes formatos.
 */
class ScanResultExporter @Inject constructor() {

    /**
     * Exporta una sesión de scan al formato especificado.
     */
    fun exportSession(session: ScanSession, format: ExportFormat): String {
        return when (format) {
            ExportFormat.JSON -> exportToJson(session)
            ExportFormat.CSV -> exportToCsv(session)
            ExportFormat.QR_CODE -> exportToQrData(session)
        }
    }

    /**
     * Exporta a formato JSON completo con toda la metadata.
     */
    private fun exportToJson(session: ScanSession): String {
        val json = JSONObject()

        // Session metadata
        json.put("sessionId", session.sessionId)
        json.put("vehicleId", session.vehicleId)
        json.put("startTime", session.startTime)
        json.put("endTime", session.endTime)
        json.put("duration", session.getDuration())
        json.put("state", session.state.name)

        // Configuration
        val configJson = JSONObject()
        configJson.put("modes", JSONArray(session.config.modes.map { it.name }))
        configJson.put("timeout", session.config.timeout)
        configJson.put("intelligentSkipping", session.config.intelligentSkipping)
        json.put("config", configJson)

        // Statistics
        session.statistics?.let { stats ->
            val statsJson = JSONObject()
            statsJson.put("totalPIDs", stats.totalPIDsTested)
            statsJson.put("successfulPIDs", stats.successfulPIDs)
            statsJson.put("failedPIDs", stats.failedPIDs)
            statsJson.put("qualityScore", stats.qualityScore)
            statsJson.put("averageLatency", stats.averageResponseTime)
            statsJson.put("maxLatency", stats.slowestResponse)
            statsJson.put("minLatency", stats.fastestResponse)
            json.put("statistics", statsJson)
        }

        // Results
        val resultsArray = JSONArray()
        session.results.forEach { result ->
            val resultJson = JSONObject()
            resultJson.put("mode", result.mode)
            resultJson.put("pid", result.pid)
            resultJson.put("command", result.command)
            resultJson.put("success", result.success)
            resultJson.put("rawResponse", result.rawResponse)
            resultJson.put("dataBytes", JSONArray(result.dataBytes.map { it.toInt() }))
            resultJson.put("byteCount", result.byteCount)
            resultJson.put("timestamp", result.timestamp)
            resultJson.put("latencyMs", result.latencyMs)

            result.interpretation?.let { resultJson.put("interpretation", it) }
            session.errorMessage?.let { resultJson.put("errorMessage", it) }

            result.metadata?.let { metadata ->
                val metadataJson = JSONObject()
                metadataJson.put("name", metadata.name)
                metadataJson.put("description", metadata.description)
                metadataJson.put("unit", metadata.unit)
                metadataJson.put("detectedType", metadata.detectedType)
                metadataJson.put("responseLength", metadata.responseLength)
                metadataJson.put("isStandard", metadata.isStandard)
                metadata.formula?.let { metadataJson.put("formula", it) }
                metadata.minValue?.let { metadataJson.put("minValue", it) }
                metadata.maxValue?.let { metadataJson.put("maxValue", it) }
                resultJson.put("metadata", metadataJson)
            }

            resultsArray.put(resultJson)
        }
        json.put("results", resultsArray)

        return json.toString(2) // Pretty print with 2 space indentation
    }

    /**
     * Exporta a formato CSV simple para Excel.
     */
    private fun exportToCsv(session: ScanSession): String {
        val csv = StringBuilder()

        // Header
        csv.appendLine("Mode,PID,Command,Success,Response,Data Bytes,Byte Count,Latency (ms),Interpretation,Name,Unit,Type,Error")

        // Data rows
        session.results.forEach { result ->
            val dataBytes = result.dataBytes.joinToString(";") { "%02X".format(it) }
            val name = result.metadata?.name ?: ""
            val unit = result.metadata?.unit ?: ""
            val type = result.metadata?.detectedType ?: ""
            val interpretation = result.interpretation ?: ""
            val error = session.errorMessage ?: ""

            csv.appendLine(
                "${result.mode}," +
                "${result.pid}," +
                "\"${result.command}\"," +
                "${result.success}," +
                "\"${result.rawResponse}\"," +
                "\"${dataBytes}\"," +
                "${result.byteCount}," +
                "${result.latencyMs}," +
                "\"${interpretation}\"," +
                "\"${name}\"," +
                "\"${unit}\"," +
                "\"${type}\"," +
                "\"${error}\""
            )
        }

        return csv.toString()
    }

    /**
     * Exporta datos resumidos para QR code.
     * Solo incluye PIDs exitosos con metadata básica.
     */
    private fun exportToQrData(session: ScanSession): String {
        val qrData = JSONObject()

        qrData.put("vehicleId", session.vehicleId)
        qrData.put("scanDate", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(session.startTime)))

        // Solo PIDs exitosos
        val successfulResults = session.results.filter { it.success }
        val pidsArray = JSONArray()

        successfulResults.forEach { result ->
            val pidJson = JSONObject()
            pidJson.put("m", result.mode) // mode
            pidJson.put("p", result.pid) // pid
            result.metadata?.name?.let { pidJson.put("n", it) } // name
            pidsArray.put(pidJson)
        }

        qrData.put("pids", pidsArray)
        qrData.put("count", successfulResults.size)

        return qrData.toString() // Compact format for QR
    }

    /**
     * Genera el nombre de archivo sugerido según el formato.
     */
    fun getSuggestedFileName(session: ScanSession, format: ExportFormat): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date(session.startTime))

        return when (format) {
            ExportFormat.JSON -> "scan_${session.vehicleId}_${timestamp}.json"
            ExportFormat.CSV -> "scan_${session.vehicleId}_${timestamp}.csv"
            ExportFormat.QR_CODE -> "scan_${session.vehicleId}_${timestamp}_qr.txt"
        }
    }

    /**
     * Obtiene el MIME type según el formato.
     */
    fun getMimeType(format: ExportFormat): String {
        return when (format) {
            ExportFormat.JSON -> "application/json"
            ExportFormat.CSV -> "text/csv"
            ExportFormat.QR_CODE -> "text/plain"
        }
    }
}
