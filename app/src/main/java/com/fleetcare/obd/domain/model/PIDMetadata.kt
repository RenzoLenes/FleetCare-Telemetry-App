package com.fleetcare.obd.domain.model

/**
 * Metadata completa de un PID detectado durante el escaneo.
 *
 * Contiene información del PID, tipo de dato detectado, formula de interpretación,
 * y estadísticas de calidad de la respuesta.
 *
 * @property mode Modo OBD (01, 02, 09, 22)
 * @property pid PID en formato hexadecimal (ej: "0C" para RPM)
 * @property name Nombre del PID (puede ser detectado automáticamente)
 * @property description Descripción del PID
 * @property unit Unidad de medida (rpm, km/h, °C, %, V, etc.)
 * @property formula Formula de interpretación (ej: "A*256+B", "(A*256+B)/4", etc.)
 * @property detectedType Tipo de dato detectado por análisis
 * @property minValue Valor mínimo observado durante scans
 * @property maxValue Valor máximo observado durante scans
 * @property averageResponseTime Tiempo promedio de respuesta en ms
 * @property successRate Tasa de éxito (0.0 a 1.0)
 * @property responseLength Longitud típica de respuesta en bytes
 * @property isStandard Si es un PID estándar o manufacturer-specific
 * @property vehicleSpecific Si este PID es específico del vehículo
 */
data class PIDMetadata(
    val mode: String,  // "01", "02", "09", "22"
    val pid: String,   // "0C", "0D", "22F001", etc.
    val name: String = "Unknown PID",
    val description: String = "",
    val unit: String = "",
    val formula: String = "",
    val detectedType: PIDDataType = PIDDataType.UNKNOWN,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val averageResponseTime: Long = 0L,  // ms
    val successRate: Float = 1.0f,  // 0.0 to 1.0
    val responseLength: Int = 0,  // bytes
    val isStandard: Boolean = false,
    val vehicleSpecific: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Devuelve el identificador único del PID.
     * Formato: "MODE_PID" (ej: "01_0C", "22_F001")
     */
    fun getUniqueId(): String = "${mode}_${pid}"

    /**
     * Devuelve el comando completo OBD para este PID.
     * Ej: "010C" para Mode 01 PID 0C
     */
    fun getCommand(): String = when (mode) {
        "22" -> {
            // Mode 22 usa formato diferente: 22 [DID_HIGH] [DID_LOW]
            if (pid.length >= 4) {
                "22${pid.substring(0, 2)}${pid.substring(2, 4)}"
            } else {
                "22$pid"
            }
        }
        else -> "$mode$pid"
    }

    /**
     * Verifica si este PID es considerado "de calidad"
     * (responde rápido y consistentemente).
     */
    fun isHighQuality(): Boolean {
        return successRate >= 0.8f && averageResponseTime < 500L
    }

    /**
     * Sugiere si este PID podría ser útil para monitoreo en tiempo real.
     */
    fun isSuitableForRealTimeMonitoring(): Boolean {
        return isHighQuality() &&
               detectedType != PIDDataType.BITMAP &&
               detectedType != PIDDataType.STRING
    }
}

/**
 * Tipos de datos que pueden ser detectados en las respuestas OBD.
 */
enum class PIDDataType {
    /**
     * Entero sin signo (0-255 por byte).
     * Común en sensores: temperatura, velocidad, porcentajes.
     */
    UNSIGNED_INT,

    /**
     * Entero con signo (-128 a 127 por byte).
     * Común en: fuel trim, timing advance.
     */
    SIGNED_INT,

    /**
     * Valor flotante con decimales.
     * Común en: voltajes, presiones, ratios.
     */
    FLOAT,

    /**
     * Bitmap de flags (cada bit representa un estado).
     * Común en: PIDs 0x00, 0x20, 0x40, status flags.
     */
    BITMAP,

    /**
     * String ASCII (VIN, Calibration IDs).
     * Común en Mode 09.
     */
    STRING,

    /**
     * Múltiples valores en un solo PID.
     * Común en: fuel system status, oxygen sensors.
     */
    MULTI_BYTE,

    /**
     * Tipo desconocido, requiere análisis manual.
     */
    UNKNOWN;

    /**
     * Devuelve el icono recomendado para este tipo de dato.
     */
    fun getIcon(): String = when (this) {
        UNSIGNED_INT -> "📊"
        SIGNED_INT -> "📈"
        FLOAT -> "🔢"
        BITMAP -> "🔲"
        STRING -> "📝"
        MULTI_BYTE -> "📦"
        UNKNOWN -> "❓"
    }
}

/**
 * Utilidades para auto-detección de tipos de PID y sugerencias.
 */
object PIDMetadataHelper {

    /**
     * Intenta detectar el tipo de dato basándose en el contenido de la respuesta.
     *
     * @param rawResponse Respuesta cruda del ELM327 (ej: "41 0C 1A F8")
     * @param mode Modo OBD
     * @param pid PID consultado
     * @return Tipo de dato detectado
     */
    fun detectDataType(rawResponse: String, mode: String, pid: String): PIDDataType {
        val dataBytes = extractDataBytes(rawResponse, mode)
        if (dataBytes.isEmpty()) return PIDDataType.UNKNOWN

        return when {
            // Bitmaps conocidos (Mode 01)
            pid in listOf("00", "20", "40", "60", "80", "A0", "C0", "E0") -> PIDDataType.BITMAP

            // Strings ASCII (Mode 09)
            mode == "09" && isASCIIString(dataBytes) -> PIDDataType.STRING

            // Multi-byte conocidos
            pid in listOf("03", "13", "1C", "1D") -> PIDDataType.MULTI_BYTE

            // Valores negativos posibles (fuel trim, timing)
            pid in listOf("06", "07", "08", "09", "0E") -> PIDDataType.SIGNED_INT

            // Valores con decimales (voltajes, presiones)
            dataBytes.size >= 2 && pid in listOf("10", "23", "24", "2F", "33") -> PIDDataType.FLOAT

            // Por defecto: unsigned int
            dataBytes.size in 1..2 -> PIDDataType.UNSIGNED_INT

            else -> PIDDataType.UNKNOWN
        }
    }

    /**
     * Sugiere un nombre para el PID basándose en PIDs estándar conocidos.
     */
    fun suggestName(mode: String, pid: String): String {
        return when (mode) {
            "01" -> getMode01StandardName(pid)
            "09" -> getMode09StandardName(pid)
            else -> "PID $mode-$pid"
        }
    }

    /**
     * Sugiere una unidad de medida para el PID.
     */
    fun suggestUnit(mode: String, pid: String): String {
        if (mode != "01") return ""

        return when (pid.uppercase()) {
            "04" -> "%"           // Calculated engine load
            "05" -> "°C"          // Engine coolant temperature
            "06", "07", "08", "09" -> "%"  // Fuel trim
            "0A" -> "kPa"         // Fuel pressure
            "0B" -> "kPa"         // Intake manifold pressure
            "0C" -> "rpm"         // Engine RPM
            "0D" -> "km/h"        // Vehicle speed
            "0E" -> "°"           // Timing advance
            "0F" -> "°C"          // Intake air temperature
            "10" -> "g/s"         // MAF air flow rate
            "11" -> "%"           // Throttle position
            "14", "15", "16", "17", "18", "19", "1A", "1B" -> "V"  // Oxygen sensors
            "21" -> "km"          // Distance traveled with MIL on
            "23" -> "kPa"         // Fuel rail pressure
            "2F" -> "%"           // Fuel tank level
            "31" -> "km"          // Distance since codes cleared
            "33" -> "kPa"         // Absolute barometric pressure
            "42" -> "V"           // Control module voltage
            "46" -> "°C"          // Ambient air temperature
            "5C" -> "L"           // Engine oil temperature
            else -> ""
        }
    }

    /**
     * Sugiere una formula de interpretación para el PID.
     */
    fun suggestFormula(mode: String, pid: String, dataType: PIDDataType): String {
        if (mode != "01") return ""

        return when (pid.uppercase()) {
            "04", "11", "2F", "4E" -> "A*100/255"  // Porcentajes 0-100%
            "05", "0F", "46", "5C" -> "A-40"       // Temperaturas -40 a 215°C
            "06", "07", "08", "09" -> "(A-128)*100/128"  // Fuel trim
            "0A", "0B", "33" -> "A*3"              // Presiones
            "0C" -> "(A*256+B)/4"                  // RPM
            "0D" -> "A"                             // Speed
            "0E" -> "(A-128)/2"                    // Timing advance
            "10" -> "(A*256+B)/100"                // MAF
            "14", "15", "16", "17", "18", "19", "1A", "1B" -> "A*0.005"  // Voltaje O2
            "21", "31" -> "A*256+B"                // Distance
            "23" -> "(A*256+B)*10"                 // Fuel rail pressure
            "42" -> "(A*256+B)/1000"               // Voltaje
            else -> if (dataType == PIDDataType.UNSIGNED_INT) "A" else ""
        }
    }

    /**
     * Crea un PIDMetadata con detección automática.
     */
    fun createAutoDetected(
        mode: String,
        pid: String,
        rawResponse: String,
        responseTime: Long
    ): PIDMetadata {
        val detectedType = detectDataType(rawResponse, mode, pid)
        val name = suggestName(mode, pid)
        val unit = suggestUnit(mode, pid)
        val formula = suggestFormula(mode, pid, detectedType)
        val dataBytes = extractDataBytes(rawResponse, mode)
        val isStandard = isStandardPID(mode, pid)

        return PIDMetadata(
            mode = mode,
            pid = pid.uppercase(),
            name = name,
            description = "",
            unit = unit,
            formula = formula,
            detectedType = detectedType,
            averageResponseTime = responseTime,
            successRate = 1.0f,
            responseLength = dataBytes.size,
            isStandard = isStandard,
            vehicleSpecific = !isStandard
        )
    }

    /**
     * Extrae los bytes de datos de una respuesta OBD.
     * Ejemplo: "41 0C 1A F8" -> ["1A", "F8"]
     */
    private fun extractDataBytes(rawResponse: String, mode: String): List<String> {
        val parts = rawResponse.trim().split("\\s+".toRegex())
        if (parts.size < 2) return emptyList()

        // Respuesta Mode 01: 41 [PID] [DATA...]
        // Respuesta Mode 09: 49 [PID] [DATA...]
        // Respuesta Mode 22: 62 [DID_HIGH] [DID_LOW] [DATA...]
        return when (mode) {
            "01", "02" -> parts.drop(2)  // Skip "41 PID" o "42 PID"
            "09" -> parts.drop(2)        // Skip "49 PID"
            "22" -> parts.drop(3)        // Skip "62 DID_HIGH DID_LOW"
            else -> parts.drop(2)
        }
    }

    /**
     * Verifica si los bytes forman un string ASCII válido.
     */
    private fun isASCIIString(bytes: List<String>): Boolean {
        if (bytes.isEmpty()) return false

        return bytes.all { byte ->
            try {
                val value = byte.toInt(16)
                value in 32..126  // Rango ASCII imprimible
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Verifica si un PID es estándar según SAE J1979.
     */
    private fun isStandardPID(mode: String, pid: String): Boolean {
        return when (mode) {
            "01", "02" -> true  // Mode 01/02 son estándar
            "09" -> pid.uppercase() in listOf("00", "02", "04", "06", "08", "0A")
            "22" -> false  // Mode 22 es manufacturer-specific
            else -> false
        }
    }

    /**
     * Nombres estándar de PIDs Mode 01.
     */
    private fun getMode01StandardName(pid: String): String {
        return when (pid.uppercase()) {
            "00" -> "Supported PIDs [01-20]"
            "01" -> "Monitor status since DTCs cleared"
            "03" -> "Fuel system status"
            "04" -> "Calculated engine load"
            "05" -> "Engine coolant temperature"
            "06" -> "Short term fuel trim—Bank 1"
            "07" -> "Long term fuel trim—Bank 1"
            "08" -> "Short term fuel trim—Bank 2"
            "09" -> "Long term fuel trim—Bank 2"
            "0A" -> "Fuel pressure"
            "0B" -> "Intake manifold absolute pressure"
            "0C" -> "Engine RPM"
            "0D" -> "Vehicle speed"
            "0E" -> "Timing advance"
            "0F" -> "Intake air temperature"
            "10" -> "MAF air flow rate"
            "11" -> "Throttle position"
            "20" -> "Supported PIDs [21-40]"
            "21" -> "Distance traveled with MIL on"
            "23" -> "Fuel rail pressure"
            "2F" -> "Fuel tank level input"
            "31" -> "Distance traveled since codes cleared"
            "33" -> "Absolute barometric pressure"
            "40" -> "Supported PIDs [41-60]"
            "42" -> "Control module voltage"
            "46" -> "Ambient air temperature"
            "5C" -> "Engine oil temperature"
            "60" -> "Supported PIDs [61-80]"
            else -> "PID 01-$pid"
        }
    }

    /**
     * Nombres estándar de PIDs Mode 09.
     */
    private fun getMode09StandardName(pid: String): String {
        return when (pid.uppercase()) {
            "00" -> "Supported PIDs"
            "02" -> "Vehicle Identification Number (VIN)"
            "04" -> "Calibration ID"
            "06" -> "Calibration Verification Numbers (CVN)"
            "08" -> "In-use performance tracking"
            "0A" -> "ECU name"
            else -> "PID 09-$pid"
        }
    }
}
