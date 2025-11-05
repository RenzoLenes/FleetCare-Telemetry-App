package com.fleetcare.obd.utils.obd

/**
 * Constantes y utilidades para comandos OBD-II Modo 22.
 *
 * El Modo 22 permite acceder a PIDs propietarios del fabricante que no están
 * disponibles en el Modo 01 estándar. Los PIDs del Modo 22 son de 2 bytes.
 *
 * Formato de comando: "22 XX XX" (XX XX = PID de 2 bytes)
 * Formato de respuesta: "62 XX XX [datos]"
 *
 * Sprint 7: Modo 22 y PIDs del Fabricante
 */
object Mode22Constants {

    /**
     * Prefijo para comandos Modo 22.
     */
    const val MODE_22_PREFIX = "22"

    /**
     * Prefijo de respuesta para Modo 22.
     */
    const val MODE_22_RESPONSE_PREFIX = "62"

    /**
     * Categorías de PIDs Modo 22 por fabricante.
     */
    enum class ManufacturerPIDCategory(val displayName: String) {
        GENERAL_MOTORS("General Motors / GM"),
        FORD("Ford Motor Company"),
        TOYOTA("Toyota / Lexus"),
        HONDA("Honda / Acura"),
        VOLKSWAGEN("Volkswagen / Audi / Seat / Skoda"),
        BMW("BMW / Mini"),
        MERCEDES("Mercedes-Benz"),
        NISSAN("Nissan / Infiniti"),
        MAZDA("Mazda"),
        SUBARU("Subaru"),
        HYUNDAI("Hyundai / Kia"),
        CHRYSLER("Chrysler / Dodge / Jeep / Ram"),
        PSA("Peugeot / Citroën / Opel"),
        RENAULT("Renault / Dacia"),
        FIAT("Fiat / Alfa Romeo"),
        VOLVO("Volvo"),
        GENERIC("Genérico / Común")
    }

    /**
     * Tipos de datos que pueden retornar los PIDs Modo 22.
     */
    enum class Mode22DataType(val description: String, val byteCount: Int) {
        SINGLE_BYTE("8-bit unsigned (0-255)", 1),
        SIGNED_BYTE("8-bit signed (-128 to 127)", 1),
        TWO_BYTE_BIG_ENDIAN("16-bit big endian", 2),
        TWO_BYTE_LITTLE_ENDIAN("16-bit little endian", 2),
        FOUR_BYTE_BIG_ENDIAN("32-bit big endian", 4),
        FOUR_BYTE_LITTLE_ENDIAN("32-bit little endian", 4),
        BCD("Binary Coded Decimal", -1),
        ASCII("ASCII String", -1),
        BITMAP("Bitmap / Flags", -1)
    }

    /**
     * PIDs comunes del Modo 22 documentados por fabricante.
     */
    data class Mode22PID(
        val pid: String,                          // PID en hex (ej: "1234")
        val manufacturer: ManufacturerPIDCategory,
        val name: String,
        val description: String,
        val dataType: Mode22DataType,
        val unit: String,
        val formula: String,
        val minValue: Double? = null,
        val maxValue: Double? = null,
        val applicableModels: List<String> = emptyList(),
        val notes: String = ""
    ) {
        /**
         * Construye el comando completo para este PID.
         */
        fun buildCommand(): String {
            return "$MODE_22_PREFIX $pid"
        }

        /**
         * Verifica si el PID es aplicable a un modelo de vehículo específico.
         */
        fun isApplicableToModel(model: String): Boolean {
            if (applicableModels.isEmpty()) return true
            return applicableModels.any { model.contains(it, ignoreCase = true) }
        }
    }

    /**
     * Base de datos de PIDs Modo 22 conocidos.
     */
    object KnownMode22PIDs {

        // ========== GENERAL MOTORS / GM ==========

        val GM_ENGINE_OIL_LIFE = Mode22PID(
            pid = "1106",
            manufacturer = ManufacturerPIDCategory.GENERAL_MOTORS,
            name = "Vida útil del aceite de motor",
            description = "Porcentaje de vida útil restante del aceite de motor calculado por la ECU",
            dataType = Mode22DataType.SINGLE_BYTE,
            unit = "%",
            formula = "A",
            minValue = 0.0,
            maxValue = 100.0,
            applicableModels = listOf("Chevrolet", "Cadillac", "GMC", "Buick"),
            notes = "Reiniciar después de cambio de aceite"
        )

        val GM_TRANSMISSION_TEMP = Mode22PID(
            pid = "11A0",
            manufacturer = ManufacturerPIDCategory.GENERAL_MOTORS,
            name = "Temperatura de transmisión",
            description = "Temperatura del fluido de transmisión automática",
            dataType = Mode22DataType.SINGLE_BYTE,
            unit = "°C",
            formula = "A - 40",
            minValue = -40.0,
            maxValue = 215.0,
            applicableModels = listOf("Chevrolet", "Cadillac", "GMC")
        )

        val GM_FUEL_RAIL_PRESSURE = Mode22PID(
            pid = "1107",
            manufacturer = ManufacturerPIDCategory.GENERAL_MOTORS,
            name = "Presión de riel de combustible",
            description = "Presión del sistema de inyección directa",
            dataType = Mode22DataType.TWO_BYTE_BIG_ENDIAN,
            unit = "kPa",
            formula = "(A * 256 + B) * 10",
            minValue = 0.0,
            maxValue = 655350.0,
            applicableModels = listOf("Chevrolet Silverado", "GMC Sierra", "Cadillac CTS")
        )

        // ========== FORD ==========

        val FORD_DPF_SOOT_LEVEL = Mode22PID(
            pid = "12F4",
            manufacturer = ManufacturerPIDCategory.FORD,
            name = "Nivel de hollín DPF",
            description = "Porcentaje de saturación del filtro de partículas diésel",
            dataType = Mode22DataType.SINGLE_BYTE,
            unit = "%",
            formula = "A * 0.39",
            minValue = 0.0,
            maxValue = 100.0,
            applicableModels = listOf("Ford F-250", "Ford F-350", "Ford Transit"),
            notes = "Requiere regeneración cerca de 100%"
        )

        val FORD_TURBO_BOOST = Mode22PID(
            pid = "F40E",
            manufacturer = ManufacturerPIDCategory.FORD,
            name = "Presión de turbo",
            description = "Presión de sobrealimentación del turbocompresor",
            dataType = Mode22DataType.TWO_BYTE_BIG_ENDIAN,
            unit = "kPa",
            formula = "((A * 256 + B) - 32768) * 0.03125",
            minValue = -100.0,
            maxValue = 300.0,
            applicableModels = listOf("Ford Mustang EcoBoost", "Ford F-150 EcoBoost")
        )

        // ========== TOYOTA / LEXUS ==========

        val TOYOTA_HYBRID_BATTERY_SOC = Mode22PID(
            pid = "1227",
            manufacturer = ManufacturerPIDCategory.TOYOTA,
            name = "Carga batería híbrida",
            description = "Estado de carga (SOC) de la batería híbrida principal",
            dataType = Mode22DataType.SINGLE_BYTE,
            unit = "%",
            formula = "A * 0.5",
            minValue = 0.0,
            maxValue = 100.0,
            applicableModels = listOf("Toyota Prius", "Toyota Camry Hybrid", "Lexus RX 450h"),
            notes = "Rango normal: 40-80%"
        )

        val TOYOTA_HYBRID_BATTERY_TEMP = Mode22PID(
            pid = "1228",
            manufacturer = ManufacturerPIDCategory.TOYOTA,
            name = "Temperatura batería híbrida",
            description = "Temperatura promedio de la batería de alto voltaje",
            dataType = Mode22DataType.SINGLE_BYTE,
            unit = "°C",
            formula = "A - 40",
            minValue = -40.0,
            maxValue = 100.0,
            applicableModels = listOf("Toyota Prius", "Toyota RAV4 Hybrid")
        )

        // ========== VOLKSWAGEN / AUDI ==========

        val VW_DPF_REGENERATION_STATUS = Mode22PID(
            pid = "2003",
            manufacturer = ManufacturerPIDCategory.VOLKSWAGEN,
            name = "Estado regeneración DPF",
            description = "Estado del proceso de regeneración del filtro de partículas",
            dataType = Mode22DataType.SINGLE_BYTE,
            unit = "",
            formula = "A",
            minValue = 0.0,
            maxValue = 255.0,
            applicableModels = listOf("VW TDI", "Audi TDI"),
            notes = "0=Inactivo, 1=Activo, 2=Solicitado"
        )

        val VW_OIL_TEMP = Mode22PID(
            pid = "2004",
            manufacturer = ManufacturerPIDCategory.VOLKSWAGEN,
            name = "Temperatura aceite motor",
            description = "Temperatura del aceite del motor",
            dataType = Mode22DataType.SINGLE_BYTE,
            unit = "°C",
            formula = "A - 40",
            minValue = -40.0,
            maxValue = 215.0,
            applicableModels = listOf("VW", "Audi", "Seat", "Skoda")
        )

        // ========== BMW ==========

        val BMW_COOLANT_TEMP_EXTENDED = Mode22PID(
            pid = "1001",
            manufacturer = ManufacturerPIDCategory.BMW,
            name = "Temperatura refrigerante extendida",
            description = "Temperatura del refrigerante con mayor precisión",
            dataType = Mode22DataType.TWO_BYTE_BIG_ENDIAN,
            unit = "°C",
            formula = "((A * 256 + B) * 0.1) - 40",
            minValue = -40.0,
            maxValue = 215.0,
            applicableModels = listOf("BMW")
        )

        // ========== HONDA ==========

        val HONDA_CVT_TEMP = Mode22PID(
            pid = "F441",
            manufacturer = ManufacturerPIDCategory.HONDA,
            name = "Temperatura CVT",
            description = "Temperatura de la transmisión variable continua",
            dataType = Mode22DataType.SINGLE_BYTE,
            unit = "°C",
            formula = "A - 40",
            minValue = -40.0,
            maxValue = 150.0,
            applicableModels = listOf("Honda Civic CVT", "Honda Accord CVT")
        )

        /**
         * Lista completa de PIDs conocidos.
         */
        val ALL_KNOWN_PIDS = listOf(
            // GM
            GM_ENGINE_OIL_LIFE,
            GM_TRANSMISSION_TEMP,
            GM_FUEL_RAIL_PRESSURE,

            // Ford
            FORD_DPF_SOOT_LEVEL,
            FORD_TURBO_BOOST,

            // Toyota
            TOYOTA_HYBRID_BATTERY_SOC,
            TOYOTA_HYBRID_BATTERY_TEMP,

            // VW
            VW_DPF_REGENERATION_STATUS,
            VW_OIL_TEMP,

            // BMW
            BMW_COOLANT_TEMP_EXTENDED,

            // Honda
            HONDA_CVT_TEMP
        )

        /**
         * Obtiene PIDs aplicables a un fabricante específico.
         */
        fun getPIDsForManufacturer(manufacturer: ManufacturerPIDCategory): List<Mode22PID> {
            return ALL_KNOWN_PIDS.filter { it.manufacturer == manufacturer }
        }

        /**
         * Obtiene PIDs aplicables a un modelo de vehículo específico.
         */
        fun getPIDsForModel(model: String): List<Mode22PID> {
            return ALL_KNOWN_PIDS.filter { it.isApplicableToModel(model) }
        }

        /**
         * Busca un PID por su código hexadecimal.
         */
        fun findByPID(pid: String): Mode22PID? {
            return ALL_KNOWN_PIDS.find { it.pid.equals(pid, ignoreCase = true) }
        }
    }

    /**
     * Construye un comando Modo 22 a partir de un PID de 2 bytes.
     */
    fun buildMode22Command(pidHex: String): String {
        val cleanPid = pidHex.replace(" ", "").uppercase()

        // Asegurar que el PID tenga 4 dígitos hex (2 bytes)
        if (cleanPid.length != 4) {
            throw IllegalArgumentException("PID Modo 22 debe tener 4 dígitos hex (2 bytes)")
        }

        // Insertar espacio cada 2 caracteres
        val byte1 = cleanPid.substring(0, 2)
        val byte2 = cleanPid.substring(2, 4)

        return "$MODE_22_PREFIX $byte1 $byte2"
    }

    /**
     * Verifica si una respuesta es del Modo 22.
     */
    fun isMode22Response(response: String): Boolean {
        val cleaned = response.trim().uppercase()
        return cleaned.startsWith(MODE_22_RESPONSE_PREFIX)
    }

    /**
     * Extrae el PID de una respuesta Modo 22.
     *
     * Formato: "62 XX XX [datos]"
     * Retorna: "XXXX"
     */
    fun extractPIDFromResponse(response: String): String? {
        val tokens = response.trim().split(" ")
        if (tokens.size < 3 || tokens[0] != MODE_22_RESPONSE_PREFIX) {
            return null
        }

        return "${tokens[1]}${tokens[2]}"
    }

    /**
     * Extrae los bytes de datos de una respuesta Modo 22.
     *
     * Formato: "62 XX XX [datos]"
     * Retorna: Array de bytes de datos
     */
    fun extractDataBytesFromResponse(response: String): ByteArray? {
        val tokens = response.trim().split(" ").filter { it.isNotBlank() }

        if (tokens.size < 4 || tokens[0] != MODE_22_RESPONSE_PREFIX) {
            return null
        }

        // Saltar modo (62) y PID (2 bytes), tomar el resto como datos
        return tokens.drop(3).mapNotNull { token ->
            try {
                token.toInt(16).toByte()
            } catch (e: NumberFormatException) {
                null
            }
        }.toByteArray()
    }
}
