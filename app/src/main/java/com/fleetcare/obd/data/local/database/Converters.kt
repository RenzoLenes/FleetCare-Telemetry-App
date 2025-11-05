package com.fleetcare.obd.data.local.database

import androidx.room.TypeConverter
import com.fleetcare.obd.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * TypeConverters para Room Database.
 *
 * Room solo puede persistir tipos primitivos. Para almacenar tipos complejos
 * como Date, UniversalScanConfig, etc., necesitamos convertirlos a JSON strings.
 *
 * Estos conversores se aplican automáticamente cuando Room lee/escribe datos.
 */
class Converters {

    // ========== Date Converters (existentes) ==========

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    companion object {
        // ========== UniversalScanConfig Converters ==========

        fun toScanConfigJson(config: UniversalScanConfig): String {
            val json = JSONObject()
            json.put("vehicleId", config.vehicleId)
            json.put("modes", JSONArray(config.modes.map { it.name }))

            val rangesJson = JSONObject()
            config.pidRanges.forEach { (mode, range) ->
                val rangeJson = JSONObject()
                rangeJson.put("first", range.first)
                rangeJson.put("last", range.last)
                rangesJson.put(mode.name, rangeJson)
            }
            json.put("pidRanges", rangesJson)

            json.put("timeout", config.timeout)
            json.put("skipKnownFailures", config.skipKnownFailures)
            json.put("parallelScanning", config.parallelScanning)
            json.put("retryFailedPIDs", config.retryFailedPIDs)
            json.put("intelligentSkipping", config.intelligentSkipping)

            return json.toString()
        }

        fun fromScanConfigJson(jsonStr: String): UniversalScanConfig {
            val json = JSONObject(jsonStr)

            val modes = mutableListOf<ScanMode>()
            val modesArray = json.getJSONArray("modes")
            for (i in 0 until modesArray.length()) {
                modes.add(ScanMode.valueOf(modesArray.getString(i)))
            }

            val pidRanges = mutableMapOf<ScanMode, IntRange>()
            val rangesJson = json.getJSONObject("pidRanges")
            rangesJson.keys().forEach { key ->
                val rangeJson = rangesJson.getJSONObject(key)
                val mode = ScanMode.valueOf(key)
                val range = rangeJson.getInt("first")..rangeJson.getInt("last")
                pidRanges[mode] = range
            }

            return UniversalScanConfig(
                vehicleId = json.getString("vehicleId"),
                modes = modes,
                pidRanges = pidRanges,
                timeout = json.getLong("timeout"),
                skipKnownFailures = json.getBoolean("skipKnownFailures"),
                parallelScanning = json.getBoolean("parallelScanning"),
                retryFailedPIDs = json.getInt("retryFailedPIDs"),
                intelligentSkipping = json.getBoolean("intelligentSkipping")
            )
        }

        // ========== ScanStatistics Converters ==========

        fun toStatisticsJson(stats: ScanStatistics): String {
            val json = JSONObject()
            json.put("totalPIDsTested", stats.totalPIDsTested)
            json.put("successfulPIDs", stats.successfulPIDs)
            json.put("failedPIDs", stats.failedPIDs)
            json.put("successRate", stats.successRate)
            json.put("averageResponseTime", stats.averageResponseTime)
            json.put("fastestResponse", stats.fastestResponse)
            json.put("slowestResponse", stats.slowestResponse)
            json.put("totalScanDuration", stats.totalScanDuration)
            json.put("timeoutCount", stats.timeoutCount)
            json.put("errorCount", stats.errorCount)
            json.put("qualityScore", stats.qualityScore)

            val pidsByModeJson = JSONObject()
            stats.pidsByMode.forEach { (mode, count) ->
                pidsByModeJson.put(mode, count)
            }
            json.put("pidsByMode", pidsByModeJson)

            val dataTypeDistJson = JSONObject()
            stats.dataTypeDistribution.forEach { (type, count) ->
                dataTypeDistJson.put(type.name, count)
            }
            json.put("dataTypeDistribution", dataTypeDistJson)

            return json.toString()
        }

        fun fromStatisticsJson(jsonStr: String): ScanStatistics {
            val json = JSONObject(jsonStr)

            val pidsByMode = mutableMapOf<String, Int>()
            val pidsByModeJson = json.getJSONObject("pidsByMode")
            pidsByModeJson.keys().forEach { key ->
                pidsByMode[key] = pidsByModeJson.getInt(key)
            }

            val dataTypeDist = mutableMapOf<PIDDataType, Int>()
            val dataTypeDistJson = json.getJSONObject("dataTypeDistribution")
            dataTypeDistJson.keys().forEach { key ->
                dataTypeDist[PIDDataType.valueOf(key)] = dataTypeDistJson.getInt(key)
            }

            return ScanStatistics(
                totalPIDsTested = json.getInt("totalPIDsTested"),
                successfulPIDs = json.getInt("successfulPIDs"),
                failedPIDs = json.getInt("failedPIDs"),
                successRate = json.getDouble("successRate").toFloat(),
                averageResponseTime = json.getLong("averageResponseTime"),
                fastestResponse = json.getLong("fastestResponse"),
                slowestResponse = json.getLong("slowestResponse"),
                totalScanDuration = json.getLong("totalScanDuration"),
                timeoutCount = json.getInt("timeoutCount"),
                errorCount = json.getInt("errorCount"),
                pidsByMode = pidsByMode,
                dataTypeDistribution = dataTypeDist,
                qualityScore = json.getInt("qualityScore")
            )
        }

        // ========== ECUInfo Converters ==========

        fun toECUInfoJson(ecuInfo: ECUInfo): String {
            val json = JSONObject()
            json.put("name", ecuInfo.name)
            json.put("calibrationId", ecuInfo.calibrationId)
            json.put("calibrationVerificationNumber", ecuInfo.calibrationVerificationNumber)
            json.put("softwareVersion", ecuInfo.softwareVersion)
            json.put("hardwareVersion", ecuInfo.hardwareVersion)
            return json.toString()
        }

        fun fromECUInfoJson(jsonStr: String): ECUInfo {
            val json = JSONObject(jsonStr)
            return ECUInfo(
                name = json.optString("name", ""),
                calibrationId = json.optString("calibrationId", ""),
                calibrationVerificationNumber = json.optString("calibrationVerificationNumber", ""),
                softwareVersion = json.optString("softwareVersion", ""),
                hardwareVersion = json.optString("hardwareVersion", "")
            )
        }

        // ========== List<String> Converters ==========

        fun toStringListJson(list: List<String>): String {
            val json = JSONArray()
            list.forEach { json.put(it) }
            return json.toString()
        }

        fun fromStringListJson(jsonStr: String): List<String> {
            val json = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until json.length()) {
                list.add(json.getString(i))
            }
            return list
        }
    }
}
