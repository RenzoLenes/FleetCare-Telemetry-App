package com.fleetcare.obd.domain.model

/**
 * Representa el bitmap de PIDs soportados por un vehículo OBD-II.
 *
 * Los PIDs de control (00, 20, 40, 60, 80, A0, C0, E0) retornan bitmaps
 * de 32 bits que indican qué PIDs están disponibles en cada rango.
 *
 * Sprint 2: Detección de PIDs soportados
 *
 * Ejemplo:
 * - PID 00 responde: "BE1FA813" (bitmap de PIDs 01-20)
 * - Bit 31 (MSB) = PID 01, Bit 30 = PID 02, ..., Bit 0 = PID 20
 * - Si bit está en 1, el PID está soportado
 */
data class SupportedPIDsBitmap(
    /**
     * Mapa de rangos de PIDs a lista de PIDs soportados.
     * Key: PID de control (0x00, 0x20, 0x40, etc.)
     * Value: Lista de PIDs soportados en ese rango
     */
    val pidRanges: Map<Int, List<Int>>,

    /**
     * ID del vehículo (MAC del adaptador)
     */
    val vehicleId: String,

    /**
     * VIN del vehículo (si está disponible)
     */
    val vin: String? = null,

    /**
     * Timestamp de detección
     */
    val detectionTimestamp: Long = System.currentTimeMillis()
) {

    /**
     * Lista plana de todos los PIDs soportados.
     */
    val allSupportedPIDs: List<Int> by lazy {
        pidRanges.values.flatten().sorted()
    }

    /**
     * Verifica si un PID específico está soportado.
     *
     * @param pid PID a verificar (formato decimal, ej: 0x0C = 12)
     * @return true si el PID está soportado
     */
    fun isPIDSupported(pid: Int): Boolean {
        return allSupportedPIDs.contains(pid)
    }

    /**
     * Obtiene el PID de control correspondiente a un PID dado.
     *
     * @param pid PID a consultar
     * @return PID de control (0x00, 0x20, etc.) o null si está fuera de rango
     */
    fun getControlPIDFor(pid: Int): Int? {
        return when (pid) {
            in 0x01..0x20 -> 0x00
            in 0x21..0x40 -> 0x20
            in 0x41..0x60 -> 0x40
            in 0x61..0x80 -> 0x60
            in 0x81..0xA0 -> 0x80
            in 0xA1..0xC0 -> 0xA0
            in 0xC1..0xE0 -> 0xC0
            else -> null
        }
    }

    /**
     * Obtiene la lista de PIDs soportados en un rango específico.
     *
     * @param controlPID PID de control (0x00, 0x20, etc.)
     * @return Lista de PIDs en ese rango, o lista vacía si no existe
     */
    fun getPIDsInRange(controlPID: Int): List<Int> {
        return pidRanges[controlPID] ?: emptyList()
    }

    /**
     * Retorna cantidad total de PIDs soportados.
     */
    fun getTotalSupportedCount(): Int {
        return allSupportedPIDs.size
    }

    /**
     * Agrupa PIDs por categoría según estándar OBD-II.
     */
    fun groupByCategory(): Map<PIDRangeCategory, List<Int>> {
        return allSupportedPIDs.groupBy { pid ->
            when (pid) {
                in 0x00..0x1F -> PIDRangeCategory.ENGINE
                in 0x20..0x3F -> PIDRangeCategory.FUEL
                in 0x40..0x5F -> PIDRangeCategory.EMISSIONS
                in 0x60..0x7F -> PIDRangeCategory.TRANSMISSION
                in 0x80..0x9F -> PIDRangeCategory.HYBRID
                in 0xA0..0xBF -> PIDRangeCategory.EXTENDED
                in 0xC0..0xDF -> PIDRangeCategory.MANUFACTURER
                else -> PIDRangeCategory.UNKNOWN
            }
        }
    }

    companion object {
        /**
         * PIDs de control estándar para detección.
         */
        val CONTROL_PIDS = listOf(0x00, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0, 0xE0)

        /**
         * Crea un bitmap vacío.
         */
        fun empty(vehicleId: String, vin: String? = null): SupportedPIDsBitmap {
            return SupportedPIDsBitmap(
                pidRanges = emptyMap(),
                vehicleId = vehicleId,
                vin = vin
            )
        }
    }
}

/**
 * Categorías de rangos de PIDs según estándar OBD-II.
 *
 * Nota: Este enum es diferente de PIDCategory en CustomPID.kt
 * Este se usa para clasificar rangos de PIDs por su posición hex,
 * mientras que PIDCategory clasifica PIDs por su función.
 */
enum class PIDRangeCategory {
    ENGINE,         // 0x00-0x1F: Motor (RPM, carga, etc.)
    FUEL,           // 0x20-0x3F: Combustible y aire
    EMISSIONS,      // 0x40-0x5F: Emisiones y catalizador
    TRANSMISSION,   // 0x60-0x7F: Transmisión y híbrido
    HYBRID,         // 0x80-0x9F: Sistemas híbridos
    EXTENDED,       // 0xA0-0xBF: Extendido
    MANUFACTURER,   // 0xC0-0xDF: Específico del fabricante
    UNKNOWN         // Fuera de rangos conocidos
}
