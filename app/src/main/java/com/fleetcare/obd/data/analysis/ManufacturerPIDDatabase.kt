package com.fleetcare.obd.data.analysis

import com.fleetcare.obd.domain.model.ManufacturerPID
import com.fleetcare.obd.utils.obd.Mode22Constants
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Base de datos de PIDs propietarios del fabricante.
 *
 * Proporciona acceso a PIDs conocidos del Modo 22 organizados por fabricante
 * y modelo de vehículo.
 *
 * Sprint 7: Modo 22 y PIDs del Fabricante - Tarea 7.2
 */
@Singleton
class ManufacturerPIDDatabase @Inject constructor() {

    /**
     * Caché de PIDs cargados.
     */
    private val pidsCache = mutableListOf<ManufacturerPID>()

    init {
        loadKnownPIDs()
    }

    /**
     * Carga los PIDs conocidos desde Mode22Constants.
     */
    private fun loadKnownPIDs() {
        pidsCache.clear()

        Mode22Constants.KnownMode22PIDs.ALL_KNOWN_PIDS.forEachIndexed { index, mode22PID ->
            val manufacturerPID = ManufacturerPID.fromMode22PID(mode22PID, id = index.toLong() + 1)
            pidsCache.add(manufacturerPID)
        }

        Timber.d("ManufacturerPIDDatabase cargada: ${pidsCache.size} PIDs")
    }

    /**
     * Obtiene todos los PIDs conocidos.
     */
    fun getAllPIDs(): List<ManufacturerPID> {
        return pidsCache.toList()
    }

    /**
     * Obtiene PIDs de un fabricante específico.
     */
    fun getPIDsForManufacturer(manufacturer: String): List<ManufacturerPID> {
        return pidsCache.filter {
            it.manufacturer.equals(manufacturer, ignoreCase = true) ||
                    it.manufacturer.contains(manufacturer, ignoreCase = true)
        }
    }

    /**
     * Obtiene PIDs aplicables a un modelo de vehículo.
     */
    fun getPIDsForModel(model: String): List<ManufacturerPID> {
        return pidsCache.filter { it.isApplicableToModel(model) }
    }

    /**
     * Busca un PID por su código hexadecimal.
     */
    fun findByPID(pid: String): ManufacturerPID? {
        return pidsCache.find { it.pid.equals(pid, ignoreCase = true) }
    }

    /**
     * Obtiene PIDs habilitados.
     */
    fun getEnabledPIDs(): List<ManufacturerPID> {
        return pidsCache.filter { it.isEnabled }
    }

    /**
     * Obtiene fabricantes disponibles.
     */
    fun getAvailableManufacturers(): List<String> {
        return pidsCache.map { it.manufacturer }.distinct().sorted()
    }

    /**
     * Busca PIDs por nombre o descripción.
     */
    fun searchPIDs(query: String): List<ManufacturerPID> {
        if (query.isBlank()) return emptyList()

        val lowerQuery = query.lowercase()
        return pidsCache.filter {
            it.name.lowercase().contains(lowerQuery) ||
                    it.description.lowercase().contains(lowerQuery) ||
                    it.pid.lowercase().contains(lowerQuery) ||
                    it.manufacturer.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Detecta el fabricante basándose en un VIN.
     *
     * El VIN tiene 17 caracteres, los primeros 3 son el WMI (World Manufacturer Identifier).
     */
    fun detectManufacturerFromVIN(vin: String): String? {
        if (vin.length < 3) return null

        val wmi = vin.substring(0, 3).uppercase()

        return when {
            // General Motors
            wmi.startsWith("1G") -> "General Motors"
            wmi.startsWith("1GC") -> "Chevrolet Truck"
            wmi.startsWith("1GY") -> "Cadillac"
            wmi in listOf("1GB", "1GD", "1GE") -> "GMC"

            // Ford
            wmi.startsWith("1F") -> "Ford"
            wmi.startsWith("2F") -> "Ford Canada"
            wmi.startsWith("3FA") -> "Ford Mexico"

            // Toyota
            wmi.startsWith("4T") || wmi.startsWith("5T") -> "Toyota"
            wmi.startsWith("JT") -> "Toyota Japan"
            wmi.startsWith("5YJ") -> "Toyota Lexus"

            // Honda
            wmi.startsWith("1H") || wmi.startsWith("2H") -> "Honda"
            wmi.startsWith("JH") -> "Honda Japan"
            wmi.startsWith("19U") -> "Honda Acura"

            // Volkswagen Group
            wmi.startsWith("WVW") || wmi.startsWith("3VW") -> "Volkswagen"
            wmi.startsWith("WAU") || wmi.startsWith("WA1") -> "Audi"
            wmi.startsWith("WBA") || wmi.startsWith("WBS") -> "BMW"
            wmi.startsWith("VSS") -> "Seat"
            wmi.startsWith("TMB") -> "Skoda"

            // Mercedes-Benz
            wmi.startsWith("WDD") || wmi.startsWith("WDB") -> "Mercedes-Benz"

            // Nissan
            wmi.startsWith("1N") || wmi.startsWith("3N") -> "Nissan"
            wmi.startsWith("JN") -> "Nissan Japan"
            wmi.startsWith("5N1") -> "Nissan Infiniti"

            // Mazda
            wmi.startsWith("JM") -> "Mazda"
            wmi.startsWith("1YV") -> "Mazda USA"

            // Subaru
            wmi.startsWith("JF") || wmi.startsWith("4S") -> "Subaru"

            // Hyundai/Kia
            wmi.startsWith("KM") || wmi.startsWith("5NP") -> "Hyundai"
            wmi.startsWith("KN") || wmi.startsWith("5XX") -> "Kia"

            // Chrysler/Dodge/Jeep
            wmi.startsWith("1C") || wmi.startsWith("2C") || wmi.startsWith("3C") -> "Chrysler"
            wmi.startsWith("1D") || wmi.startsWith("2D") -> "Dodge"
            wmi.startsWith("1J") -> "Jeep"

            // PSA Group
            wmi.startsWith("VF3") -> "Peugeot"
            wmi.startsWith("VF7") -> "Citroën"

            // Renault
            wmi.startsWith("VF1") -> "Renault"

            // Fiat
            wmi.startsWith("ZFA") -> "Fiat"
            wmi.startsWith("ZAR") -> "Alfa Romeo"

            // Volvo
            wmi.startsWith("YV1") || wmi.startsWith("YV4") -> "Volvo"

            else -> null
        }
    }

    /**
     * Obtiene PIDs recomendados para un VIN específico.
     */
    fun getRecommendedPIDsForVIN(vin: String): List<ManufacturerPID> {
        val manufacturer = detectManufacturerFromVIN(vin) ?: return emptyList()

        Timber.d("Fabricante detectado: $manufacturer para VIN: $vin")

        return getPIDsForManufacturer(manufacturer)
    }

    /**
     * Obtiene el nombre completo del fabricante desde el WMI.
     */
    fun getManufacturerNameFromWMI(wmi: String): String? {
        if (wmi.length < 3) return null
        return detectManufacturerFromVIN(wmi + "00000000000000")
    }

    /**
     * Estadísticas de la base de datos.
     */
    data class DatabaseStats(
        val totalPIDs: Int,
        val manufacturersCount: Int,
        val verifiedPIDs: Int,
        val enabledPIDs: Int
    )

    /**
     * Obtiene estadísticas de la base de datos.
     */
    fun getStats(): DatabaseStats {
        return DatabaseStats(
            totalPIDs = pidsCache.size,
            manufacturersCount = getAvailableManufacturers().size,
            verifiedPIDs = pidsCache.count { it.isVerified },
            enabledPIDs = pidsCache.count { it.isEnabled }
        )
    }

    /**
     * Recarga la base de datos desde las constantes.
     */
    fun reload() {
        loadKnownPIDs()
    }
}
