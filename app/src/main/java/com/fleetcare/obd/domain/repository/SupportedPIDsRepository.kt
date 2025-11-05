package com.fleetcare.obd.domain.repository

import com.fleetcare.obd.data.local.dao.DetectionStats
import com.fleetcare.obd.domain.model.SupportedPIDsBitmap
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz del Repository para PIDs soportados.
 *
 * Gestiona el almacenamiento y caché de PIDs detectados por vehículo
 * para evitar ejecutar la detección completa cada vez que se conecta.
 *
 * Sprint 2: Detección y persistencia de PIDs soportados
 */
interface SupportedPIDsRepository {

    /**
     * Guarda o actualiza los PIDs soportados de un vehículo.
     *
     * @param supportedPIDs Bitmap de PIDs detectados
     * @return Result con ID del registro o error
     */
    suspend fun saveSupportedPIDs(supportedPIDs: SupportedPIDsBitmap): Result<Long>

    /**
     * Obtiene los PIDs soportados de un vehículo.
     *
     * @param vehicleId MAC del adaptador Bluetooth
     * @return Flow con el bitmap o null si no existe caché
     */
    fun getSupportedPIDs(vehicleId: String): Flow<SupportedPIDsBitmap?>

    /**
     * Obtiene los PIDs soportados de forma síncrona.
     *
     * @param vehicleId MAC del adaptador
     * @return Result con bitmap o null si no existe
     */
    suspend fun getSupportedPIDsSync(vehicleId: String): Result<SupportedPIDsBitmap?>

    /**
     * Obtiene PIDs por VIN del vehículo.
     *
     * @param vin VIN del vehículo
     * @return Flow con bitmap o null
     */
    fun getSupportedPIDsByVIN(vin: String): Flow<SupportedPIDsBitmap?>

    /**
     * Verifica si existe caché de PIDs para un vehículo.
     *
     * @param vehicleId MAC del adaptador
     * @return true si existe caché
     */
    suspend fun hasCachedPIDs(vehicleId: String): Result<Boolean>

    /**
     * Verifica si el caché necesita actualización.
     *
     * Se considera obsoleto si tiene más de X días.
     *
     * @param vehicleId MAC del adaptador
     * @param maxAgeDays Edad máxima en días (default: 30)
     * @return true si necesita re-detección
     */
    suspend fun needsRefresh(vehicleId: String, maxAgeDays: Int = 30): Result<Boolean>

    /**
     * Actualiza el VIN de un vehículo.
     *
     * @param vehicleId MAC del adaptador
     * @param vin Nuevo VIN
     * @return Result indicando éxito o error
     */
    suspend fun updateVIN(vehicleId: String, vin: String): Result<Unit>

    /**
     * Elimina el caché de PIDs de un vehículo.
     *
     * @param vehicleId MAC del adaptador
     * @return Result con número de registros eliminados
     */
    suspend fun deleteSupportedPIDs(vehicleId: String): Result<Int>

    /**
     * Elimina cachés antiguos.
     *
     * @param maxAgeDays Edad máxima en días
     * @return Result con número eliminado
     */
    suspend fun deleteOldCaches(maxAgeDays: Int): Result<Int>

    /**
     * Obtiene todos los vehículos con PIDs detectados.
     *
     * @return Flow con lista de bitmaps
     */
    fun getAllSupportedPIDs(): Flow<List<SupportedPIDsBitmap>>

    /**
     * Obtiene estadísticas de detección.
     *
     * @return Result con estadísticas
     */
    suspend fun getDetectionStats(): Result<DetectionStats?>
}
