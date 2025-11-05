package com.fleetcare.obd.domain.model

/**
 * Configuración para el Scanner Universal de PIDs.
 *
 * Permite escanear múltiples modos OBD (01, 02, 09, 22) con configuración
 * flexible de rangos, timeouts y opciones de optimización.
 *
 * @property vehicleId ID del vehículo a escanear
 * @property modes Lista de modos OBD a escanear
 * @property pidRanges Rangos de PIDs por modo (ej: Mode01 -> 0x00..0xFF)
 * @property timeout Timeout por PID en milisegundos
 * @property skipKnownFailures Si es true, skip PIDs que fallaron en scans anteriores
 * @property parallelScanning Escaneo paralelo (experimental, puede causar timeouts)
 * @property retryFailedPIDs Número de reintentos para PIDs fallidos
 * @property intelligentSkipping Si 5+ PIDs consecutivos fallan, skip siguiente bloque
 */
data class UniversalScanConfig(
    val vehicleId: String,
    val modes: List<ScanMode> = listOf(ScanMode.MODE_01_CURRENT_DATA),
    val pidRanges: Map<ScanMode, IntRange> = mapOf(
        ScanMode.MODE_01_CURRENT_DATA to 0x00..0xFF
    ),
    val timeout: Long = 300L,  // ms
    val skipKnownFailures: Boolean = true,
    val parallelScanning: Boolean = false,
    val retryFailedPIDs: Int = 0,
    val intelligentSkipping: Boolean = true
)

/**
 * Modos OBD soportados para escaneo.
 */
enum class ScanMode {
    /**
     * Mode 01: Current/live data
     * PIDs: 0x00-0xFF (256 PIDs)
     * Comando: 01 [PID]
     * Respuesta: 41 [PID] [Data]
     */
    MODE_01_CURRENT_DATA,

    /**
     * Mode 02: Freeze frame data
     * PIDs: 0x00-0xFF (256 PIDs)
     * Comando: 02 [PID] [Frame]
     * Respuesta: 42 [PID] [Data]
     */
    MODE_02_FREEZE_FRAME,

    /**
     * Mode 03: Diagnostic Trouble Codes
     * Comando: 03
     * Respuesta: 43 [DTC count] [DTCs]
     */
    MODE_03_DTCS,

    /**
     * Mode 09: Vehicle information
     * PIDs comunes: 0x02 (VIN), 0x04 (Calibration ID), 0x06 (CVN)
     * Comando: 09 [PID]
     * Respuesta: 49 [PID] [Data]
     */
    MODE_09_VEHICLE_INFO,

    /**
     * Mode 22: Manufacturer specific (Read Data By Identifier)
     * DIDs: 0x0000-0xFFFF
     * Comando: 22 [DID high] [DID low]
     * Respuesta: 62 [DID high] [DID low] [Data]
     */
    MODE_22_MANUFACTURER;

    /**
     * Obtiene el prefijo del comando para este modo.
     */
    fun getCommandPrefix(): String = when (this) {
        MODE_01_CURRENT_DATA -> "01"
        MODE_02_FREEZE_FRAME -> "02"
        MODE_03_DTCS -> "03"
        MODE_09_VEHICLE_INFO -> "09"
        MODE_22_MANUFACTURER -> "22"
    }

    /**
     * Obtiene el rango por defecto de PIDs para este modo.
     */
    fun getDefaultRange(): IntRange = when (this) {
        MODE_01_CURRENT_DATA -> 0x00..0xFF
        MODE_02_FREEZE_FRAME -> 0x00..0xFF
        MODE_03_DTCS -> 0x00..0x00  // Solo 1 comando
        MODE_09_VEHICLE_INFO -> 0x00..0x0F  // Solo primeros 16
        MODE_22_MANUFACTURER -> 0x0000..0x00FF  // Solo primeros 256
    }
}

/**
 * Presets de configuración predefinidos para Quick Scan, Full Scan y Deep Scan.
 */
object ScanPresets {

    /**
     * Quick Scan: Escaneo rápido de PIDs estándar más comunes.
     * Tiempo estimado: 1-2 minutos
     */
    fun quickScan(vehicleId: String) = UniversalScanConfig(
        vehicleId = vehicleId,
        modes = listOf(ScanMode.MODE_01_CURRENT_DATA),
        pidRanges = mapOf(
            ScanMode.MODE_01_CURRENT_DATA to 0x00..0x4F  // Solo primeros 80 PIDs
        ),
        timeout = 200L,
        intelligentSkipping = true,
        skipKnownFailures = true
    )

    /**
     * Full Standard Scan: Escaneo completo de PIDs estándar Mode 01 + Vehicle Info.
     * Tiempo estimado: 3-5 minutos
     */
    fun fullStandardScan(vehicleId: String) = UniversalScanConfig(
        vehicleId = vehicleId,
        modes = listOf(
            ScanMode.MODE_01_CURRENT_DATA,
            ScanMode.MODE_09_VEHICLE_INFO
        ),
        pidRanges = mapOf(
            ScanMode.MODE_01_CURRENT_DATA to 0x00..0xFF,  // Todos Mode 01
            ScanMode.MODE_09_VEHICLE_INFO to 0x00..0x0F   // Info básica
        ),
        timeout = 300L,
        intelligentSkipping = true,
        skipKnownFailures = true
    )

    /**
     * Deep Scan: Escaneo exhaustivo de todos los modos.
     * Tiempo estimado: 10-15 minutos
     */
    fun deepScan(vehicleId: String) = UniversalScanConfig(
        vehicleId = vehicleId,
        modes = listOf(
            ScanMode.MODE_01_CURRENT_DATA,
            ScanMode.MODE_02_FREEZE_FRAME,
            ScanMode.MODE_09_VEHICLE_INFO,
            ScanMode.MODE_22_MANUFACTURER
        ),
        pidRanges = mapOf(
            ScanMode.MODE_01_CURRENT_DATA to 0x00..0xFF,
            ScanMode.MODE_02_FREEZE_FRAME to 0x00..0xFF,
            ScanMode.MODE_09_VEHICLE_INFO to 0x00..0xFF,
            ScanMode.MODE_22_MANUFACTURER to 0x0000..0x00FF  // Limitado a primeros 256
        ),
        timeout = 500L,
        intelligentSkipping = true,
        skipKnownFailures = false,  // Deep scan intenta todo
        retryFailedPIDs = 1
    )

    /**
     * Legacy Vehicle Scan: Optimizado para vehículos legacy (ISO 9141-2, KWP).
     * Timeouts más largos, sin parallel scanning.
     */
    fun legacyScan(vehicleId: String) = UniversalScanConfig(
        vehicleId = vehicleId,
        modes = listOf(
            ScanMode.MODE_01_CURRENT_DATA,
            ScanMode.MODE_09_VEHICLE_INFO
        ),
        pidRanges = mapOf(
            ScanMode.MODE_01_CURRENT_DATA to 0x00..0xFF,
            ScanMode.MODE_09_VEHICLE_INFO to 0x00..0x0F
        ),
        timeout = 500L,  // Timeout más largo para protocolos legacy
        intelligentSkipping = true,
        skipKnownFailures = true,
        parallelScanning = false,
        retryFailedPIDs = 1
    )

    /**
     * Manufacturer PIDs Only: Solo escanear PIDs del fabricante (Mode 22).
     * Útil cuando ya se conocen los PIDs estándar.
     */
    fun manufacturerOnlyScan(vehicleId: String) = UniversalScanConfig(
        vehicleId = vehicleId,
        modes = listOf(ScanMode.MODE_22_MANUFACTURER),
        pidRanges = mapOf(
            ScanMode.MODE_22_MANUFACTURER to 0xF000..0xFFFF  // Rango común manufacturer
        ),
        timeout = 400L,
        intelligentSkipping = true,
        skipKnownFailures = true
    )
}
