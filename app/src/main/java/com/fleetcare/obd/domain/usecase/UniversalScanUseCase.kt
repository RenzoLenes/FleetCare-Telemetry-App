package com.fleetcare.obd.domain.usecase

import com.fleetcare.obd.bluetooth.BluetoothService
import com.fleetcare.obd.domain.model.*
import com.fleetcare.obd.domain.repository.PIDMetadataRepository
import com.fleetcare.obd.domain.repository.UniversalScanRepository
import com.fleetcare.obd.domain.repository.VehicleProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Use Case principal para el escaneo universal de PIDs.
 *
 * Orquesta el escaneo multi-modo (01, 02, 09, 22) utilizando la configuración
 * proporcionada y emitiendo progreso en tiempo real.
 */
class UniversalScanUseCase @Inject constructor(
    private val bluetoothService: BluetoothService,
    private val scanRepository: UniversalScanRepository,
    private val metadataRepository: PIDMetadataRepository,
    private val profileRepository: VehicleProfileRepository,
    private val scanMode01UseCase: ScanMode01UseCase,
    private val scanMode02UseCase: ScanMode02UseCase,
    private val scanMode09UseCase: ScanMode09UseCase,
    private val scanMode22UseCase: ScanMode22UseCase
) {
    /**
     * Ejecuta un escaneo universal completo.
     *
     * @param config Configuración del escaneo
     * @return Flow de progreso del escaneo
     */
    suspend operator fun invoke(config: UniversalScanConfig): Flow<ScanProgress> = flow {
        // Crear sesión
        val session = ScanSession.create(config.vehicleId, config)
        val sessionId = scanRepository.createSession(session)

        // Verificar conexión Bluetooth
        if (!bluetoothService.isConnected()) {
            scanRepository.errorSession(sessionId, "No hay conexión Bluetooth")
            throw IllegalStateException("No hay conexión Bluetooth con el adaptador OBD-II")
        }

        val startTime = System.currentTimeMillis()
        val allResults = mutableListOf<ScanResult>()
        var totalPIDs = 0
        var scannedPIDs = 0

        try {
            // Calcular total de PIDs a escanear
            config.modes.forEach { mode ->
                val range = config.pidRanges[mode] ?: mode.getDefaultRange()
                totalPIDs += range.count()
            }

            // Actualizar estado de sesión
            scanRepository.updateSessionState(sessionId, ScannerState.SCANNING)

            // Escanear cada modo
            config.modes.forEach { mode ->
                val range = config.pidRanges[mode] ?: mode.getDefaultRange()

                // Emitir progreso de inicio de modo
                emit(ScanProgress(
                    currentMode = mode.getCommandPrefix(),
                    currentPID = 0,
                    totalPIDs = totalPIDs,
                    scannedPIDs = scannedPIDs,
                    successCount = allResults.count { it.success },
                    failedCount = allResults.count { !it.success },
                    elapsedTimeMs = System.currentTimeMillis() - startTime,
                    currentPhase = "Scanning Mode ${mode.getCommandPrefix()}"
                ))

                // Delegar escaneo al use case específico del modo
                val modeResults = when (mode) {
                    ScanMode.MODE_01_CURRENT_DATA -> scanMode01UseCase(
                        vehicleId = config.vehicleId,
                        range = range,
                        timeout = config.timeout,
                        skipKnownFailures = config.skipKnownFailures,
                        intelligentSkipping = config.intelligentSkipping
                    )
                    ScanMode.MODE_02_FREEZE_FRAME -> scanMode02UseCase(
                        vehicleId = config.vehicleId,
                        range = range,
                        timeout = config.timeout
                    )
                    ScanMode.MODE_09_VEHICLE_INFO -> scanMode09UseCase(
                        vehicleId = config.vehicleId,
                        range = range,
                        timeout = config.timeout
                    )
                    ScanMode.MODE_22_MANUFACTURER -> scanMode22UseCase(
                        vehicleId = config.vehicleId,
                        range = range,
                        timeout = config.timeout,
                        skipKnownFailures = config.skipKnownFailures
                    )
                    ScanMode.MODE_03_DTCS -> {
                        // Mode 03 es especial, solo lee DTCs
                        emptyList()
                    }
                }

                allResults.addAll(modeResults)
                scannedPIDs += modeResults.size

                // Guardar resultados parciales
                scanRepository.addResults(sessionId, modeResults)

                // Guardar metadata de PIDs exitosos
                val metadataList = modeResults
                    .filter { it.success }
                    .map { it.toMetadata() }
                metadataRepository.saveMultiple(metadataList)

                // Emitir progreso actualizado
                val elapsedMs = System.currentTimeMillis() - startTime
                val avgTimePerPID = if (scannedPIDs > 0) elapsedMs / scannedPIDs else config.timeout
                val estimatedRemaining = (totalPIDs - scannedPIDs) * avgTimePerPID

                emit(ScanProgress(
                    currentMode = mode.getCommandPrefix(),
                    currentPID = scannedPIDs,
                    totalPIDs = totalPIDs,
                    scannedPIDs = scannedPIDs,
                    successCount = allResults.count { it.success },
                    failedCount = allResults.count { !it.success },
                    elapsedTimeMs = elapsedMs,
                    estimatedTimeRemainingMs = estimatedRemaining,
                    currentPhase = "Completed Mode ${mode.getCommandPrefix()}"
                ))
            }

            // Calcular estadísticas finales
            val totalDuration = System.currentTimeMillis() - startTime
            val statistics = ScanStatistics.fromScanResults(allResults, totalDuration)

            // Completar sesión
            scanRepository.completeSession(sessionId, statistics)

            // Actualizar perfil del vehículo
            profileRepository.updateFromScan(
                vehicleId = config.vehicleId,
                scanResults = allResults,
                statistics = statistics,
                config = config
            )

            // Emitir progreso final
            emit(ScanProgress(
                currentMode = "Completed",
                currentPID = totalPIDs,
                totalPIDs = totalPIDs,
                scannedPIDs = scannedPIDs,
                successCount = allResults.count { it.success },
                failedCount = allResults.count { !it.success },
                elapsedTimeMs = totalDuration,
                estimatedTimeRemainingMs = 0,
                currentPhase = "Completed"
            ))

        } catch (e: Exception) {
            // Marcar sesión como error
            scanRepository.errorSession(sessionId, e.message ?: "Unknown error")
            throw e
        }
    }

    /**
     * Cancela el escaneo activo de un vehículo.
     *
     * @param vehicleId ID del vehículo
     */
    suspend fun cancelScan(vehicleId: String) {
        val activeSession = scanRepository.getActiveSession(vehicleId)
        if (activeSession != null) {
            scanRepository.updateSessionState(activeSession.sessionId, ScannerState.ERROR)
            scanRepository.errorSession(activeSession.sessionId, "Cancelled by user")
        }
    }

    /**
     * Pausa el escaneo activo de un vehículo.
     *
     * @param vehicleId ID del vehículo
     */
    suspend fun pauseScan(vehicleId: String) {
        val activeSession = scanRepository.getActiveSession(vehicleId)
        if (activeSession != null) {
            scanRepository.updateSessionState(activeSession.sessionId, ScannerState.PAUSED)
        }
    }

    /**
     * Reanuda el escaneo pausado de un vehículo.
     *
     * @param vehicleId ID del vehículo
     */
    suspend fun resumeScan(vehicleId: String) {
        val activeSession = scanRepository.getActiveSession(vehicleId)
        if (activeSession != null && activeSession.state == ScannerState.PAUSED) {
            scanRepository.updateSessionState(activeSession.sessionId, ScannerState.SCANNING)
        }
    }
}
