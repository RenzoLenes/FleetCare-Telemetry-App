package com.fleetcare.obd.domain.repository

import com.fleetcare.obd.domain.model.VehicleData
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz del Repository de datos del vehículo.
 *
 * Gestiona la lectura de datos OBDII, almacenamiento local y sincronización.
 */
interface VehicleRepository {

    /**
     * Flow de datos del vehículo en tiempo real.
     * Emite nuevos datos cada vez que se lee del OBDII.
     */
    val vehicleDataFlow: Flow<VehicleData>

    /**
     * Indica si la lectura continua está activa.
     */
    val isReading: Flow<Boolean>

    /**
     * Inicia la lectura continua de datos OBDII.
     *
     * Lee los parámetros configurados en intervalos regulares
     * y emite los datos a través de vehicleDataFlow.
     */
    fun startContinuousReading()

    /**
     * Detiene la lectura continua de datos.
     */
    fun stopContinuousReading()

    /**
     * Lee un valor único de un parámetro específico.
     *
     * @param command Comando PID a leer (ej: "010C" para RPM)
     * @return Result con el valor o error
     */
    suspend fun readSingleParameter(command: String): Result<Double>

    /**
     * Guarda datos del vehículo en caché local.
     *
     * @param data Datos a guardar
     * @param vehicleId ID del vehículo
     * @param sessionId ID de la sesión
     */
    suspend fun saveToCache(
        data: VehicleData,
        vehicleId: String,
        sessionId: String
    ): Result<Unit>

    /**
     * Obtiene datos históricos del caché local.
     *
     * @param vehicleId ID del vehículo
     * @param limit Número máximo de registros
     * @return Flow de datos históricos
     */
    fun getHistoricalData(vehicleId: String, limit: Int = 100): Flow<List<VehicleData>>
}
