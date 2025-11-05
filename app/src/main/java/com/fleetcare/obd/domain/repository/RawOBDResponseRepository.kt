package com.fleetcare.obd.domain.repository

import com.fleetcare.obd.data.local.dao.RawResponseTableStats
import com.fleetcare.obd.data.local.dao.SuccessFailCount
import com.fleetcare.obd.data.local.dao.VehicleRecordCount
import com.fleetcare.obd.domain.model.RawOBDResponse
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz del Repository para respuestas RAW de comandos OBD-II.
 *
 * Gestiona el almacenamiento y consulta de respuestas sin procesar
 * para análisis de patrones e inferencia de fórmulas.
 *
 * Sprint 1: Sistema de captura y almacenamiento de respuestas RAW
 */
interface RawOBDResponseRepository {

    /**
     * Guarda una respuesta RAW en la base de datos.
     *
     * @param response Respuesta RAW a guardar
     * @return Result con ID del registro insertado o error
     */
    suspend fun saveRawResponse(response: RawOBDResponse): Result<Long>

    /**
     * Guarda múltiples respuestas en una transacción (batch insert).
     *
     * @param responses Lista de respuestas a guardar
     * @return Result indicando éxito o error
     */
    suspend fun saveMultipleResponses(responses: List<RawOBDResponse>): Result<Unit>

    /**
     * Obtiene todas las respuestas para un comando específico.
     * Emisión reactiva cuando hay cambios.
     *
     * @param command Comando OBD (ej: "010C")
     * @return Flow de lista de respuestas
     */
    fun getResponsesForCommand(command: String): Flow<List<RawOBDResponse>>

    /**
     * Obtiene respuestas en un rango de tiempo.
     *
     * @param startTime Timestamp inicio (millis)
     * @param endTime Timestamp fin (millis)
     * @param command Comando opcional para filtrar
     * @return Flow de respuestas en el rango
     */
    fun getResponsesInTimeRange(
        startTime: Long,
        endTime: Long,
        command: String? = null
    ): Flow<List<RawOBDResponse>>

    /**
     * Obtiene todas las respuestas de una sesión.
     *
     * @param sessionId ID de sesión (UUID)
     * @return Result con lista de respuestas
     */
    suspend fun getResponsesBySession(sessionId: String): Result<List<RawOBDResponse>>

    /**
     * Obtiene lista de comandos únicos ejecutados.
     *
     * @return Flow de comandos disponibles
     */
    fun getAllCommands(): Flow<List<String>>

    /**
     * Obtiene comandos ejecutados para un vehículo.
     *
     * @param vehicleId MAC address del adaptador
     * @return Flow de comandos
     */
    fun getCommandsForVehicle(vehicleId: String): Flow<List<String>>

    /**
     * Obtiene conteo de éxito/fallo para un comando.
     *
     * @param command Comando OBD
     * @return Result con conteo
     */
    suspend fun getSuccessFailCount(command: String): Result<SuccessFailCount>

    /**
     * Obtiene las últimas N respuestas de un comando.
     *
     * @param command Comando OBD
     * @param limit Número máximo (default: 100)
     * @return Result con lista
     */
    suspend fun getLatestResponses(command: String, limit: Int = 100): Result<List<RawOBDResponse>>

    /**
     * Obtiene solo respuestas exitosas en un rango de tiempo.
     * Útil para análisis de patrones con datos válidos.
     *
     * @param command Comando OBD
     * @param startTime Timestamp inicio
     * @param endTime Timestamp fin
     * @return Result con lista de respuestas exitosas
     */
    suspend fun getSuccessfulResponsesInRange(
        command: String,
        startTime: Long,
        endTime: Long
    ): Result<List<RawOBDResponse>>

    /**
     * Elimina respuestas más antiguas que un timestamp.
     * Útil para limpieza automática.
     *
     * @param timestamp Timestamp de corte
     * @return Result con número de registros eliminados
     */
    suspend fun deleteOlderThan(timestamp: Long): Result<Int>

    /**
     * Elimina respuestas de un vehículo específico.
     *
     * @param vehicleId MAC address
     * @return Result con número eliminado
     */
    suspend fun deleteByVehicleId(vehicleId: String): Result<Int>

    /**
     * Elimina todas las respuestas (usar con precaución).
     *
     * @return Result con número eliminado
     */
    suspend fun deleteAll(): Result<Int>

    /**
     * Obtiene estadísticas generales de la tabla.
     *
     * @return Result con estadísticas
     */
    suspend fun getTableStats(): Result<RawResponseTableStats>

    /**
     * Obtiene conteo de respuestas por vehículo.
     *
     * @return Flow de conteos
     */
    fun getRecordCountByVehicle(): Flow<List<VehicleRecordCount>>

    /**
     * Obtiene tamaño estimado de almacenamiento en bytes.
     *
     * @return Result con bytes usados
     */
    suspend fun getEstimatedStorageSize(): Result<Long>

    /**
     * Obtiene el total de registros almacenados.
     *
     * @return Result con count
     */
    suspend fun getRecordCount(): Result<Int>
}
