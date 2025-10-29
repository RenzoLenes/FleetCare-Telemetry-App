package com.fleetcare.obd.domain.usecase

import com.fleetcare.obd.domain.model.ConnectionState
import com.fleetcare.obd.domain.repository.BluetoothRepository
import com.fleetcare.obd.domain.repository.DiagnosticRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use Case para limpiar códigos de diagnóstico (DTCs).
 *
 * IMPORTANTE: Solo usar cuando los problemas se han resuelto.
 * Borrar códigos sin resolver problemas puede:
 * - Hacer que el vehículo no pase inspecciones técnicas
 * - Ocultar problemas graves del motor
 * - Reiniciar contadores de emisiones
 */
class ClearDTCsUseCase @Inject constructor(
    private val diagnosticRepository: DiagnosticRepository,
    private val bluetoothRepository: BluetoothRepository
) {

    /**
     * Limpia todos los DTCs almacenados.
     *
     * Efectos secundarios:
     * - Borra DTCs activos y pendientes
     * - Reinicia el estado de los monitores de emisiones
     * - Apaga el MIL (Check Engine Light)
     * - Reinicia contadores de freeze frame
     *
     * @return Result indicando éxito o error
     */
    suspend operator fun invoke(): Result<Unit> {
        return try {
            // Verificar conexión
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

            // Limpiar DTCs
            diagnosticRepository.clearDTCs()

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
