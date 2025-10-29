package com.fleetcare.obd.domain.usecase

import com.fleetcare.obd.domain.model.ConnectionState
import com.fleetcare.obd.domain.model.DiagnosticTroubleCode
import com.fleetcare.obd.domain.repository.BluetoothRepository
import com.fleetcare.obd.domain.repository.DiagnosticRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use Case para leer códigos de diagnóstico (DTCs).
 *
 * Lee tanto códigos activos como pendientes y los combina.
 */
class ReadDTCsUseCase @Inject constructor(
    private val diagnosticRepository: DiagnosticRepository,
    private val bluetoothRepository: BluetoothRepository
) {

    /**
     * Lee todos los DTCs (activos y pendientes).
     *
     * @param includesPending Si true, también lee códigos pendientes
     * @return Result con lista combinada de DTCs
     */
    suspend operator fun invoke(includePending: Boolean = true): Result<List<DiagnosticTroubleCode>> {
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

            // Leer DTCs activos
            val activeDTCsResult = diagnosticRepository.readActiveDTCs()
            val activeDTCs = activeDTCsResult.getOrElse { emptyList() }

            // Leer DTCs pendientes si se solicita
            val pendingDTCs = if (includePending) {
                val pendingDTCsResult = diagnosticRepository.readPendingDTCs()
                pendingDTCsResult.getOrElse { emptyList() }
            } else {
                emptyList()
            }

            // Combinar ambas listas
            val allDTCs = activeDTCs + pendingDTCs

            if (allDTCs.isEmpty()) {
                // Sin errores detectados
                Result.success(listOf(DiagnosticTroubleCode.noErrors()))
            } else {
                Result.success(allDTCs)
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene solo el número de DTCs sin leerlos.
     */
    suspend fun getCount(): Result<Int> {
        return try {
            val connectionState = bluetoothRepository.connectionState.first()

            if (connectionState !is ConnectionState.Connected || !connectionState.isOBDInitialized) {
                return Result.failure(
                    IllegalStateException("Sin conexión OBDII")
                )
            }

            diagnosticRepository.getDTCCount()

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
