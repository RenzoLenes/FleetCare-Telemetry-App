package com.fleetcare.obd.domain.usecase

import com.fleetcare.obd.bluetooth.BluetoothService
import com.fleetcare.obd.domain.model.PIDMetadataHelper
import com.fleetcare.obd.domain.model.ScanResult
import com.fleetcare.obd.domain.repository.VehicleProfileRepository
import com.fleetcare.obd.utils.Logger
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Use Case para escaneo de Mode 22 (Read Data By Identifier - Manufacturer Specific).
 *
 * Escanea DIDs (Data Identifiers) específicos del fabricante.
 * Mode 22 usa formato de 2 bytes (0x0000-0xFFFF) en lugar de 1 byte.
 */
class ScanMode22UseCase @Inject constructor(
    private val bluetoothService: BluetoothService,
    private val profileRepository: VehicleProfileRepository
) {
    companion object {
        private const val MODE = "22"
        private const val CONSECUTIVE_FAILURES_THRESHOLD = 10
        private const val SKIP_BLOCK_SIZE = 20
    }

    /**
     * Escanea DIDs del modo 22.
     *
     * @param vehicleId ID del vehículo
     * @param range Rango de DIDs a escanear (ej: 0x0000..0x00FF o 0xF000..0xFFFF)
     * @param timeout Timeout por DID en ms
     * @param skipKnownFailures Saltar DIDs conocidos como fallidos
     * @return Lista de resultados
     */
    suspend operator fun invoke(
        vehicleId: String,
        range: IntRange,
        timeout: Long = 400L,
        skipKnownFailures: Boolean = true
    ): List<ScanResult> {
        val results = mutableListOf<ScanResult>()
        var consecutiveFailures = 0

        for (didInt in range) {
            val did = String.format("%04X", didInt)

            // Skip DIDs conocidos como fallidos
            if (skipKnownFailures && profileRepository.isPIDKnownToFail(vehicleId, MODE, did)) {
                Logger.d("Skipping known failure: DID $MODE-$did")
                continue
            }

            // Intelligent skipping para Mode 22
            if (consecutiveFailures >= CONSECUTIVE_FAILURES_THRESHOLD) {
                Logger.d("Intelligent skip: $consecutiveFailures consecutive failures in Mode 22")
                // Saltar bloque
                val skipTo = (didInt + SKIP_BLOCK_SIZE).coerceAtMost(range.last)
                for (skipDid in didInt until skipTo) {
                    // Skip
                }
                consecutiveFailures = 0
                continue
            }

            val result = scanSingleDID(vehicleId, did, timeout)
            results.add(result)

            if (result.success) {
                consecutiveFailures = 0
            } else {
                consecutiveFailures++
            }

            delay(15)
        }

        Logger.d("Mode 22 scan completed: ${results.count { it.success }}/${results.size} successful")
        return results
    }

    private suspend fun scanSingleDID(
        vehicleId: String,
        did: String,
        timeout: Long
    ): ScanResult {
        // Mode 22 comando: 22 [DID_HIGH] [DID_LOW]
        val didHigh = did.substring(0, 2)
        val didLow = did.substring(2, 4)
        val command = "$MODE$didHigh$didLow"
        val startTime = System.currentTimeMillis()

        return try {
            val response = bluetoothService.sendCommand(command, timeout)
            val responseTime = System.currentTimeMillis() - startTime

            if (isValidResponse(response, did)) {
                val dataBytes = extractDataBytes(response)
                val metadata = PIDMetadataHelper.createAutoDetected(
                    mode = MODE,
                    pid = did,
                    rawResponse = response,
                    responseTime = responseTime
                )

                ScanResult(
                    mode = MODE,
                    pid = did,
                    command = command,
                    success = true,
                    rawResponse = response,
                    dataBytes = dataBytes,
                    byteCount = dataBytes.size,
                    responseTime = responseTime,
                    metadata = metadata.copy(
                        isStandard = false,
                        vehicleSpecific = true
                    ),
                    vehicleId = vehicleId,
                    isStandardPID = false
                )
            } else {
                ScanResult(
                    mode = MODE,
                    pid = did,
                    command = command,
                    success = false,
                    rawResponse = response,
                    responseTime = responseTime,
                    vehicleId = vehicleId,
                    isStandardPID = false
                )
            }
        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            ScanResult(
                mode = MODE,
                pid = did,
                command = command,
                success = false,
                rawResponse = "ERROR: ${e.message}",
                responseTime = responseTime,
                vehicleId = vehicleId,
                isStandardPID = false
            )
        }
    }

    private fun isValidResponse(response: String, did: String): Boolean {
        val normalized = response.trim().uppercase()

        // Respuestas inválidas
        if (normalized.contains("NO DATA") || normalized.contains("?") ||
            normalized.contains("ERROR") || normalized.contains("UNABLE") ||
            normalized.isEmpty()) {
            return false
        }

        // Mode 22 response: "62 [DID_HIGH] [DID_LOW] [DATA...]"
        if (!normalized.startsWith("62")) {
            return false
        }

        // Verificar que contiene el DID solicitado
        val didHigh = did.substring(0, 2)
        val didLow = did.substring(2, 4)
        return normalized.contains(didHigh) && normalized.contains(didLow)
    }

    private fun extractDataBytes(response: String): ByteArray {
        return try {
            val parts = response.trim().split("\\s+".toRegex())
            // Skip "62 DID_HIGH DID_LOW", tomar el resto
            if (parts.size < 4) return byteArrayOf()
            parts.drop(3).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
        } catch (e: Exception) {
            byteArrayOf()
        }
    }
}
