package com.fleetcare.obd.domain.usecase

import com.fleetcare.obd.domain.model.ConnectionState
import com.fleetcare.obd.domain.repository.BluetoothRepository
import com.fleetcare.obd.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use Case para iniciar y gestionar la lectura continua de datos del vehículo.
 *
 * Valida precondiciones antes de iniciar la lectura:
 * - Conexión Bluetooth activa
 * - OBDII inicializado
 */
class ReadVehicleDataUseCase @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
    private val vehicleRepository: VehicleRepository
) {

    /**
     * Inicia la lectura continua de datos OBDII.
     *
     * @return Result indicando éxito o fallo con mensaje de error
     */
    suspend operator fun invoke(): Result<Unit> {
        return try {
            // Verificar estado de conexión
            val connectionState = bluetoothRepository.connectionState.first()

            if (connectionState !is ConnectionState.Connected) {
                return Result.failure(
                    IllegalStateException("No hay conexión Bluetooth activa")
                )
            }

            if (!connectionState.isOBDInitialized) {
                return Result.failure(
                    IllegalStateException("Adaptador OBDII no inicializado")
                )
            }

            // Iniciar lectura continua
            vehicleRepository.startContinuousReading()

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Detiene la lectura continua.
     */
    fun stop() {
        vehicleRepository.stopContinuousReading()
    }
}
