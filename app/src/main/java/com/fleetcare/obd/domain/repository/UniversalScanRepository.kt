package com.fleetcare.obd.domain.repository

import com.fleetcare.obd.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository para gestión de sesiones de escaneo universal de PIDs.
 *
 * Proporciona operaciones CRUD para sesiones de escaneo, incluyendo
 * persistencia de resultados, estadísticas y estado de sesión.
 */
interface UniversalScanRepository {

    /**
     * Crea una nueva sesión de escaneo.
     *
     * @param session Sesión a crear
     * @return ID de la sesión creada
     */
    suspend fun createSession(session: ScanSession): String

    /**
     * Obtiene una sesión de escaneo por ID.
     *
     * @param sessionId ID de la sesión
     * @return Sesión si existe, null en caso contrario
     */
    suspend fun getSession(sessionId: String): ScanSession?

    /**
     * Obtiene todas las sesiones de un vehículo.
     *
     * @param vehicleId ID del vehículo
     * @return Flow de sesiones del vehículo
     */
    fun getSessionsByVehicle(vehicleId: String): Flow<List<ScanSession>>

    /**
     * Obtiene la última sesión de un vehículo.
     *
     * @param vehicleId ID del vehículo
     * @return Última sesión si existe, null en caso contrario
     */
    suspend fun getLatestSession(vehicleId: String): ScanSession?

    /**
     * Actualiza una sesión de escaneo.
     *
     * @param session Sesión a actualizar
     */
    suspend fun updateSession(session: ScanSession)

    /**
     * Actualiza el estado de una sesión.
     *
     * @param sessionId ID de la sesión
     * @param state Nuevo estado
     */
    suspend fun updateSessionState(sessionId: String, state: ScannerState)

    /**
     * Agrega resultados a una sesión.
     *
     * @param sessionId ID de la sesión
     * @param results Resultados a agregar
     */
    suspend fun addResults(sessionId: String, results: List<ScanResult>)

    /**
     * Obtiene los resultados de una sesión.
     *
     * @param sessionId ID de la sesión
     * @return Flow de resultados
     */
    fun getResults(sessionId: String): Flow<List<ScanResult>>

    /**
     * Obtiene resultados filtrados de una sesión.
     *
     * @param sessionId ID de la sesión
     * @param mode Modo OBD (null = todos)
     * @param successOnly Solo PIDs exitosos
     * @return Flow de resultados filtrados
     */
    fun getFilteredResults(
        sessionId: String,
        mode: String? = null,
        successOnly: Boolean = false
    ): Flow<List<ScanResult>>

    /**
     * Obtiene estadísticas de una sesión.
     *
     * @param sessionId ID de la sesión
     * @return Estadísticas de la sesión
     */
    suspend fun getStatistics(sessionId: String): ScanStatistics?

    /**
     * Completa una sesión de escaneo.
     *
     * @param sessionId ID de la sesión
     * @param statistics Estadísticas finales
     */
    suspend fun completeSession(sessionId: String, statistics: ScanStatistics)

    /**
     * Marca una sesión como error.
     *
     * @param sessionId ID de la sesión
     * @param errorMessage Mensaje de error
     */
    suspend fun errorSession(sessionId: String, errorMessage: String)

    /**
     * Elimina una sesión de escaneo.
     *
     * @param sessionId ID de la sesión
     */
    suspend fun deleteSession(sessionId: String)

    /**
     * Elimina todas las sesiones de un vehículo.
     *
     * @param vehicleId ID del vehículo
     */
    suspend fun deleteSessionsByVehicle(vehicleId: String)

    /**
     * Obtiene el número total de sesiones de un vehículo.
     *
     * @param vehicleId ID del vehículo
     * @return Número de sesiones
     */
    suspend fun getSessionCount(vehicleId: String): Int

    /**
     * Verifica si existe una sesión activa para un vehículo.
     *
     * @param vehicleId ID del vehículo
     * @return true si hay una sesión activa
     */
    suspend fun hasActiveSession(vehicleId: String): Boolean

    /**
     * Obtiene la sesión activa de un vehículo (si existe).
     *
     * @param vehicleId ID del vehículo
     * @return Sesión activa o null
     */
    suspend fun getActiveSession(vehicleId: String): ScanSession?
}
