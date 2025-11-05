package com.fleetcare.obd.domain.repository

import com.fleetcare.obd.domain.model.CustomPID
import com.fleetcare.obd.domain.model.PIDCategory
import com.fleetcare.obd.domain.model.PIDSource
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para gestionar PIDs personalizados.
 *
 * Sprint 6: Gestión de PIDs Personalizados
 */
interface CustomPIDRepository {

    // ========== CREATE ==========

    /**
     * Guarda un nuevo PID personalizado.
     * @return ID del PID guardado
     */
    suspend fun saveCustomPID(customPID: CustomPID): Result<Long>

    /**
     * Guarda múltiples PIDs personalizados.
     * @return Lista de IDs guardados
     */
    suspend fun saveCustomPIDs(customPIDs: List<CustomPID>): Result<List<Long>>

    // ========== READ ==========

    /**
     * Obtiene todos los PIDs personalizados.
     */
    fun getAllCustomPIDs(): Flow<List<CustomPID>>

    /**
     * Obtiene un PID personalizado por ID.
     */
    suspend fun getCustomPIDById(id: Long): Result<CustomPID?>

    /**
     * Obtiene un PID personalizado por PID hex.
     */
    suspend fun getCustomPIDByPID(pid: String): Result<CustomPID?>

    /**
     * Obtiene un PID personalizado por comando completo.
     */
    suspend fun getCustomPIDByCommand(command: String): Result<CustomPID?>

    /**
     * Obtiene todos los PIDs habilitados.
     */
    fun getEnabledCustomPIDs(): Flow<List<CustomPID>>

    /**
     * Obtiene PIDs por categoría.
     */
    fun getCustomPIDsByCategory(category: PIDCategory): Flow<List<CustomPID>>

    /**
     * Obtiene PIDs por origen.
     */
    fun getCustomPIDsBySource(source: PIDSource): Flow<List<CustomPID>>

    /**
     * Busca PIDs por nombre, PID o comando.
     */
    fun searchCustomPIDs(query: String): Flow<List<CustomPID>>

    /**
     * Obtiene PIDs compatibles con un vehículo específico.
     */
    fun getCustomPIDsForVehicle(vin: String): Flow<List<CustomPID>>

    /**
     * Obtiene PIDs recientes (últimos 10 usados).
     */
    fun getRecentCustomPIDs(): Flow<List<CustomPID>>

    /**
     * Obtiene el conteo total de PIDs personalizados.
     */
    suspend fun getCustomPIDCount(): Result<Int>

    /**
     * Obtiene el conteo de PIDs habilitados.
     */
    suspend fun getEnabledCustomPIDCount(): Result<Int>

    // ========== UPDATE ==========

    /**
     * Actualiza un PID personalizado.
     */
    suspend fun updateCustomPID(customPID: CustomPID): Result<Unit>

    /**
     * Actualiza la fecha de último uso de un PID.
     */
    suspend fun updateLastUsed(id: Long): Result<Unit>

    /**
     * Actualiza el estado habilitado/deshabilitado de un PID.
     */
    suspend fun updateEnabled(id: Long, isEnabled: Boolean): Result<Unit>

    /**
     * Actualiza la confianza de un PID.
     */
    suspend fun updateConfidence(id: Long, confidence: Float): Result<Unit>

    // ========== DELETE ==========

    /**
     * Elimina un PID personalizado.
     */
    suspend fun deleteCustomPID(customPID: CustomPID): Result<Unit>

    /**
     * Elimina un PID por ID.
     */
    suspend fun deleteCustomPIDById(id: Long): Result<Unit>

    /**
     * Elimina todos los PIDs personalizados.
     */
    suspend fun deleteAllCustomPIDs(): Result<Unit>

    /**
     * Elimina PIDs por origen.
     */
    suspend fun deleteCustomPIDsBySource(source: PIDSource): Result<Unit>

    /**
     * Elimina PIDs deshabilitados.
     */
    suspend fun deleteDisabledCustomPIDs(): Result<Unit>

    // ========== IMPORT/EXPORT ==========

    /**
     * Importa PIDs desde JSON.
     * @param json JSON string con array de PIDs
     * @return Cantidad de PIDs importados exitosamente
     */
    suspend fun importPIDsFromJSON(json: String): Result<Int>

    /**
     * Exporta PIDs a JSON.
     * @param vins Lista de VINs para filtrar (vacío = todos)
     * @return JSON string
     */
    suspend fun exportPIDsToJSON(vins: List<String> = emptyList()): Result<String>

    /**
     * Exporta un PID individual a JSON.
     */
    suspend fun exportSinglePIDToJSON(id: Long): Result<String>
}
