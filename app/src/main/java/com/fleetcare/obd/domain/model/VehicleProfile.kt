package com.fleetcare.obd.domain.model

/**
 * Perfil completo de un vehículo con información detectada y aprendida.
 *
 * Almacena información estática (VIN, marca, modelo) y dinámica (PIDs soportados,
 * protocolo óptimo, configuración recomendada) aprendida a través de escaneos.
 *
 * @property vehicleId ID único del vehículo
 * @property vin Vehicle Identification Number (17 caracteres)
 * @property make Marca del vehículo (ej: "Hyundai", "Toyota")
 * @property model Modelo del vehículo (ej: "H1", "Corolla")
 * @property year Año del vehículo
 * @property protocol Protocolo OBD detectado (3, 6, 7, etc.)
 * @property protocolName Nombre del protocolo ("ISO 9141-2", "ISO 15765-4 CAN", etc.)
 * @property ecuInfo Información del ECU (nombre, calibration ID, CVN)
 * @property supportedPIDsCount Número total de PIDs soportados
 * @property knownPIDs Lista de PIDs conocidos y verificados
 * @property failedPIDs Lista de PIDs que siempre fallan (para intelligent skipping)
 * @property optimalScanConfig Configuración óptima aprendida para este vehículo
 * @property isLegacyVehicle Si usa protocolo legacy (ISO 9141-2, KWP)
 * @property lastScanned Timestamp del último escaneo
 * @property totalScans Número total de escaneos realizados
 * @property averageQualityScore Score promedio de calidad de escaneos
 */
data class VehicleProfile(
    val vehicleId: String,
    val vin: String = "",
    val make: String = "",
    val model: String = "",
    val year: Int? = null,
    val protocol: String = "",
    val protocolName: String = "",
    val ecuInfo: ECUInfo = ECUInfo(),
    val supportedPIDsCount: Int = 0,
    val knownPIDs: List<String> = emptyList(),
    val failedPIDs: List<String> = emptyList(),
    val optimalScanConfig: UniversalScanConfig? = null,
    val isLegacyVehicle: Boolean = false,
    val lastScanned: Long = 0L,
    val totalScans: Int = 0,
    val averageQualityScore: Int = 0
) {
    /**
     * Devuelve el nombre completo del vehículo.
     * Ej: "2012 Hyundai H1"
     */
    fun getDisplayName(): String {
        return buildString {
            if (year != null) append("$year ")
            if (make.isNotEmpty()) append("$make ")
            if (model.isNotEmpty()) append(model)
        }.trim().ifEmpty { "Unknown Vehicle" }
    }

    /**
     * Verifica si el perfil está completo (tiene VIN y protocol).
     */
    fun isComplete(): Boolean {
        return vin.isNotEmpty() && protocol.isNotEmpty()
    }

    /**
     * Verifica si el vehículo ha sido escaneado recientemente (últimas 24h).
     */
    fun isRecentlyScanned(): Boolean {
        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        return lastScanned > oneDayAgo
    }

    /**
     * Devuelve la configuración de escaneo recomendada para este vehículo.
     */
    fun getRecommendedScanConfig(): UniversalScanConfig {
        return optimalScanConfig ?: if (isLegacyVehicle) {
            ScanPresets.legacyScan(vehicleId)
        } else {
            ScanPresets.fullStandardScan(vehicleId)
        }
    }

    /**
     * Verifica si un PID está en la lista de fallos conocidos.
     */
    fun isPIDKnownToFail(mode: String, pid: String): Boolean {
        val pidId = "${mode}_${pid.uppercase()}"
        return failedPIDs.contains(pidId)
    }

    /**
     * Verifica si un PID es conocido y soportado.
     */
    fun isPIDSupported(mode: String, pid: String): Boolean {
        val pidId = "${mode}_${pid.uppercase()}"
        return knownPIDs.contains(pidId)
    }

    /**
     * Genera un resumen del perfil del vehículo.
     */
    fun getSummary(): String = buildString {
        appendLine("Vehicle Profile:")
        appendLine("• Name: ${getDisplayName()}")
        if (vin.isNotEmpty()) appendLine("• VIN: $vin")
        appendLine("• Protocol: $protocolName ($protocol)")
        appendLine("• Type: ${if (isLegacyVehicle) "Legacy" else "Modern"}")
        appendLine("• Supported PIDs: $supportedPIDsCount")
        appendLine("• Total scans: $totalScans")
        appendLine("• Avg quality: $averageQualityScore/100")

        if (ecuInfo.hasData()) {
            appendLine("\nECU Info:")
            appendLine(ecuInfo.getSummary())
        }
    }

    companion object {
        /**
         * Crea un perfil básico solo con vehicleId.
         */
        fun createBasic(vehicleId: String): VehicleProfile {
            return VehicleProfile(vehicleId = vehicleId)
        }

        /**
         * Actualiza un perfil existente con nueva información de escaneo.
         */
        fun updateFromScan(
            existing: VehicleProfile,
            scanResults: List<ScanResult>,
            statistics: ScanStatistics,
            config: UniversalScanConfig
        ): VehicleProfile {
            val newKnownPIDs = scanResults
                .filter { it.success }
                .map { "${it.mode}_${it.pid.uppercase()}" }
                .toSet()

            val newFailedPIDs = scanResults
                .filter { !it.success }
                .map { "${it.mode}_${it.pid.uppercase()}" }
                .toSet()

            val allKnownPIDs = (existing.knownPIDs + newKnownPIDs).distinct()
            val allFailedPIDs = (existing.failedPIDs + newFailedPIDs).distinct()

            // Calcular nuevo quality score promedio
            val totalScans = existing.totalScans + 1
            val newAvgScore = ((existing.averageQualityScore * existing.totalScans) + statistics.qualityScore) / totalScans

            return existing.copy(
                supportedPIDsCount = allKnownPIDs.size,
                knownPIDs = allKnownPIDs,
                failedPIDs = allFailedPIDs,
                lastScanned = System.currentTimeMillis(),
                totalScans = totalScans,
                averageQualityScore = newAvgScore,
                optimalScanConfig = if (statistics.qualityScore > 70) config else existing.optimalScanConfig
            )
        }
    }
}

/**
 * Información del ECU (Engine Control Unit).
 */
data class ECUInfo(
    val name: String = "",
    val calibrationId: String = "",
    val calibrationVerificationNumber: String = "",
    val softwareVersion: String = "",
    val hardwareVersion: String = ""
) {
    /**
     * Verifica si hay datos del ECU.
     */
    fun hasData(): Boolean {
        return name.isNotEmpty() || calibrationId.isNotEmpty() || calibrationVerificationNumber.isNotEmpty()
    }

    /**
     * Genera un resumen del ECU.
     */
    fun getSummary(): String = buildString {
        if (name.isNotEmpty()) appendLine("  Name: $name")
        if (calibrationId.isNotEmpty()) appendLine("  Calibration ID: $calibrationId")
        if (calibrationVerificationNumber.isNotEmpty()) appendLine("  CVN: $calibrationVerificationNumber")
        if (softwareVersion.isNotEmpty()) appendLine("  Software: $softwareVersion")
        if (hardwareVersion.isNotEmpty()) appendLine("  Hardware: $hardwareVersion")
    }.trim()

    companion object {
        /**
         * Extrae información del ECU desde resultados de Mode 09.
         */
        fun fromMode09Results(results: List<ScanResult>): ECUInfo {
            val mode09Results = results.filter { it.mode == "09" && it.success }

            val name = mode09Results
                .find { it.pid.uppercase() == "0A" }
                ?.rawResponse
                ?.let { parseASCIIString(it) } ?: ""

            val calibrationId = mode09Results
                .find { it.pid.uppercase() == "04" }
                ?.rawResponse
                ?.let { parseASCIIString(it) } ?: ""

            val cvn = mode09Results
                .find { it.pid.uppercase() == "06" }
                ?.rawResponse
                ?.substringAfter("49 06")
                ?.trim() ?: ""

            return ECUInfo(
                name = name,
                calibrationId = calibrationId,
                calibrationVerificationNumber = cvn
            )
        }

        /**
         * Convierte una respuesta OBD a string ASCII.
         * Ej: "49 02 01 35 41 42 43" -> "ABC"
         */
        private fun parseASCIIString(response: String): String {
            val bytes = response.trim()
                .split("\\s+".toRegex())
                .drop(2)  // Skip "49 PID"
                .mapNotNull {
                    try {
                        it.toInt(16).toChar()
                    } catch (e: Exception) {
                        null
                    }
                }
                .filter { it.code in 32..126 }  // Solo ASCII imprimible

            return String(bytes.toCharArray()).trim()
        }
    }
}

/**
 * Categoría de vehículo basada en año y protocolo.
 */
enum class VehicleCategory {
    /**
     * Vehículos muy antiguos (pre-1996, sin OBD-II estándar).
     */
    PRE_OBDII,

    /**
     * Vehículos legacy (1996-2007, ISO 9141-2 o KWP).
     */
    LEGACY,

    /**
     * Vehículos modernos (2008+, CAN bus).
     */
    MODERN,

    /**
     * Vehículos muy modernos (2016+, CAN FD posible).
     */
    LATEST;

    companion object {
        /**
         * Determina la categoría basándose en año y protocolo.
         */
        fun fromVehicle(year: Int?, protocol: String): VehicleCategory {
            val isLegacyProtocol = protocol in listOf("1", "2", "3", "4", "5")

            return when {
                year == null -> MODERN
                year < 1996 -> PRE_OBDII
                year < 2008 && isLegacyProtocol -> LEGACY
                year < 2016 -> MODERN
                else -> LATEST
            }
        }
    }
}
