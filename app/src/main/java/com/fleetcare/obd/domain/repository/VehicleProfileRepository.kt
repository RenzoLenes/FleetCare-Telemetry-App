package com.fleetcare.obd.domain.repository

import com.fleetcare.obd.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository para gestión de perfiles de vehículos.
 *
 * Almacena información estática y dinámica de vehículos: VIN, protocolo,
 * PIDs soportados, configuración óptima, etc.
 */
interface VehicleProfileRepository {

    /**
     * Crea o actualiza un perfil de vehículo.
     *
     * @param profile Perfil a guardar
     */
    suspend fun saveProfile(profile: VehicleProfile)

    /**
     * Obtiene un perfil de vehículo por ID.
     *
     * @param vehicleId ID del vehículo
     * @return Perfil del vehículo o null si no existe
     */
    suspend fun getProfile(vehicleId: String): VehicleProfile?

    /**
     * Obtiene un perfil de vehículo por VIN.
     *
     * @param vin Vehicle Identification Number
     * @return Perfil del vehículo o null si no existe
     */
    suspend fun getProfileByVIN(vin: String): VehicleProfile?

    /**
     * Obtiene todos los perfiles de vehículos.
     *
     * @return Flow de perfiles
     */
    fun getAllProfiles(): Flow<List<VehicleProfile>>

    /**
     * Obtiene perfiles de vehículos legacy (ISO 9141-2, KWP).
     *
     * @return Flow de perfiles legacy
     */
    fun getLegacyVehicles(): Flow<List<VehicleProfile>>

    /**
     * Obtiene perfiles de vehículos modernos (CAN bus).
     *
     * @return Flow de perfiles modernos
     */
    fun getModernVehicles(): Flow<List<VehicleProfile>>

    /**
     * Actualiza información estática del vehículo (VIN, marca, modelo, año).
     *
     * @param vehicleId ID del vehículo
     * @param vin VIN
     * @param make Marca
     * @param model Modelo
     * @param year Año
     */
    suspend fun updateVehicleInfo(
        vehicleId: String,
        vin: String,
        make: String,
        model: String,
        year: Int?
    )

    /**
     * Actualiza protocolo detectado.
     *
     * @param vehicleId ID del vehículo
     * @param protocol Protocolo (ej: "3", "6")
     * @param protocolName Nombre del protocolo
     * @param isLegacy Si es protocolo legacy
     */
    suspend fun updateProtocol(
        vehicleId: String,
        protocol: String,
        protocolName: String,
        isLegacy: Boolean
    )

    /**
     * Actualiza información del ECU.
     *
     * @param vehicleId ID del vehículo
     * @param ecuInfo Información del ECU
     */
    suspend fun updateECUInfo(vehicleId: String, ecuInfo: ECUInfo)

    /**
     * Agrega PIDs conocidos al perfil.
     *
     * @param vehicleId ID del vehículo
     * @param pids Lista de PIDs (formato "MODE_PID")
     */
    suspend fun addKnownPIDs(vehicleId: String, pids: List<String>)

    /**
     * Agrega PIDs fallidos al perfil.
     *
     * @param vehicleId ID del vehículo
     * @param pids Lista de PIDs (formato "MODE_PID")
     */
    suspend fun addFailedPIDs(vehicleId: String, pids: List<String>)

    /**
     * Actualiza configuración óptima de escaneo.
     *
     * @param vehicleId ID del vehículo
     * @param config Configuración óptima
     */
    suspend fun updateOptimalConfig(vehicleId: String, config: UniversalScanConfig)

    /**
     * Actualiza estadísticas de escaneo.
     *
     * @param vehicleId ID del vehículo
     * @param totalScans Número total de escaneos
     * @param averageQualityScore Score promedio de calidad
     */
    suspend fun updateScanStatistics(
        vehicleId: String,
        totalScans: Int,
        averageQualityScore: Int
    )

    /**
     * Actualiza perfil después de un escaneo.
     *
     * @param vehicleId ID del vehículo
     * @param scanResults Resultados del escaneo
     * @param statistics Estadísticas del escaneo
     * @param config Configuración utilizada
     */
    suspend fun updateFromScan(
        vehicleId: String,
        scanResults: List<ScanResult>,
        statistics: ScanStatistics,
        config: UniversalScanConfig
    )

    /**
     * Elimina un perfil de vehículo.
     *
     * @param vehicleId ID del vehículo
     */
    suspend fun deleteProfile(vehicleId: String)

    /**
     * Verifica si existe un perfil para un vehículo.
     *
     * @param vehicleId ID del vehículo
     * @return true si existe el perfil
     */
    suspend fun hasProfile(vehicleId: String): Boolean

    /**
     * Obtiene el número total de vehículos registrados.
     *
     * @return Número de vehículos
     */
    suspend fun getVehicleCount(): Int

    /**
     * Obtiene perfiles escaneados recientemente (últimas 24h).
     *
     * @return Flow de perfiles recientes
     */
    fun getRecentlyScannedProfiles(): Flow<List<VehicleProfile>>

    /**
     * Obtiene perfiles con mejor calidad de escaneo.
     *
     * @param minQualityScore Score mínimo de calidad (default 70)
     * @return Flow de perfiles de alta calidad
     */
    fun getHighQualityProfiles(minQualityScore: Int = 70): Flow<List<VehicleProfile>>

    /**
     * Busca vehículos por marca, modelo o VIN.
     *
     * @param query Texto a buscar
     * @return Flow de perfiles que coinciden
     */
    fun searchVehicles(query: String): Flow<List<VehicleProfile>>

    /**
     * Obtiene perfiles agrupados por marca.
     *
     * @return Mapa de marca a lista de perfiles
     */
    suspend fun getProfilesByMake(): Map<String, List<VehicleProfile>>

    /**
     * Obtiene perfiles agrupados por año.
     *
     * @return Mapa de año a lista de perfiles
     */
    suspend fun getProfilesByYear(): Map<Int, List<VehicleProfile>>

    /**
     * Obtiene perfiles agrupados por protocolo.
     *
     * @return Mapa de protocolo a lista de perfiles
     */
    suspend fun getProfilesByProtocol(): Map<String, List<VehicleProfile>>

    /**
     * Verifica si un PID está en la lista de fallos conocidos del vehículo.
     *
     * @param vehicleId ID del vehículo
     * @param mode Modo OBD
     * @param pid PID
     * @return true si el PID está en la lista de fallos
     */
    suspend fun isPIDKnownToFail(vehicleId: String, mode: String, pid: String): Boolean

    /**
     * Verifica si un PID es conocido y soportado por el vehículo.
     *
     * @param vehicleId ID del vehículo
     * @param mode Modo OBD
     * @param pid PID
     * @return true si el PID es soportado
     */
    suspend fun isPIDSupported(vehicleId: String, mode: String, pid: String): Boolean

    /**
     * Obtiene la configuración de escaneo recomendada para un vehículo.
     *
     * @param vehicleId ID del vehículo
     * @return Configuración recomendada
     */
    suspend fun getRecommendedScanConfig(vehicleId: String): UniversalScanConfig
}
