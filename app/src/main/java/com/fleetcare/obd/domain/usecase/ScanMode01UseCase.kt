package com.fleetcare.obd.domain.usecase

import com.fleetcare.obd.bluetooth.BluetoothService
import com.fleetcare.obd.domain.model.PIDMetadata
import com.fleetcare.obd.domain.model.PIDMetadataHelper
import com.fleetcare.obd.domain.model.ScanResult
import com.fleetcare.obd.domain.repository.PIDMetadataRepository
import com.fleetcare.obd.domain.repository.VehicleProfileRepository
import com.fleetcare.obd.utils.Logger
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Use Case para escaneo de Mode 01 (Current/Live Data).
 *
 * Escanea PIDs del modo 01 de forma individual, con soporte para:
 * - Intelligent skipping (saltar bloques si muchos PIDs consecutivos fallan)
 * - Skip de PIDs conocidos como fallidos
 * - Detección automática de tipo de dato
 */
class ScanMode01UseCase @Inject constructor(
    private val bluetoothService: BluetoothService,
    private val metadataRepository: PIDMetadataRepository,
    private val profileRepository: VehicleProfileRepository
) {
    companion object {
        private const val MODE = "01"
        private const val CONSECUTIVE_FAILURES_THRESHOLD = 5
        private const val SKIP_BLOCK_SIZE = 10
    }

    /**
     * Escanea PIDs del modo 01.
     *
     * @param vehicleId ID del vehículo
     * @param range Rango de PIDs a escanear (ej: 0x00..0xFF)
     * @param timeout Timeout por PID en ms
     * @param skipKnownFailures Saltar PIDs que se sabe que fallan
     * @param intelligentSkipping Activar intelligent skipping
     * @return Lista de resultados
     */
    suspend operator fun invoke(
        vehicleId: String,
        range: IntRange,
        timeout: Long = 300L,
        skipKnownFailures: Boolean = true,
        intelligentSkipping: Boolean = true
    ): List<ScanResult> {
        val results = mutableListOf<ScanResult>()
        var consecutiveFailures = 0
        var skippedCount = 0

        for (pidInt in range) {
            val pid = String.format("%02X", pidInt)

            // Skip PIDs conocidos como fallidos
            if (skipKnownFailures && profileRepository.isPIDKnownToFail(vehicleId, MODE, pid)) {
                Logger.d("Skipping known failure: PID $MODE-$pid")
                skippedCount++
                continue
            }

            // Intelligent skipping: si 5+ PIDs consecutivos fallan, skip siguiente bloque
            if (intelligentSkipping && consecutiveFailures >= CONSECUTIVE_FAILURES_THRESHOLD) {
                Logger.d("Intelligent skip: $consecutiveFailures consecutive failures, skipping next $SKIP_BLOCK_SIZE PIDs")
                repeat(SKIP_BLOCK_SIZE) {
                    if (pidInt + it in range) {
                        skippedCount++
                    }
                }
                // Saltar bloque
                val skipTo = (pidInt + SKIP_BLOCK_SIZE).coerceAtMost(range.last)
                for (skipPid in pidInt until skipTo) {
                    // No hacer nada, solo avanzar
                }
                consecutiveFailures = 0
                continue
            }

            // Escanear PID
            val result = scanSinglePID(vehicleId, pid, timeout)
            results.add(result)

            // Actualizar contador de fallos consecutivos
            if (result.success) {
                consecutiveFailures = 0
            } else {
                consecutiveFailures++
            }

            // Pequeña pausa entre PIDs para no saturar el adaptador
            delay(10)
        }

        Logger.d("Mode 01 scan completed: ${results.count { it.success }}/${results.size} successful, $skippedCount skipped")
        return results
    }

    /**
     * Escanea un solo PID del modo 01.
     */
    private suspend fun scanSinglePID(
        vehicleId: String,
        pid: String,
        timeout: Long
    ): ScanResult {
        val command = "$MODE$pid"
        val startTime = System.currentTimeMillis()

        return try {
            val response = bluetoothService.sendCommand(command, timeout)
            val responseTime = System.currentTimeMillis() - startTime

            if (isValidResponse(response, pid)) {
                // Respuesta válida
                val dataBytes = extractDataBytes(response)
                val metadata = PIDMetadataHelper.createAutoDetected(
                    mode = MODE,
                    pid = pid,
                    rawResponse = response,
                    responseTime = responseTime
                )

                ScanResult(
                    mode = MODE,
                    pid = pid,
                    command = command,
                    success = true,
                    rawResponse = response,
                    dataBytes = dataBytes,
                    byteCount = dataBytes.size,
                    responseTime = responseTime,
                    metadata = metadata,
                    vehicleId = vehicleId
                )
            } else {
                // Respuesta inválida (NO DATA, ERROR, etc.)
                ScanResult(
                    mode = MODE,
                    pid = pid,
                    command = command,
                    success = false,
                    rawResponse = response,
                    responseTime = responseTime,
                    vehicleId = vehicleId
                )
            }
        } catch (e: Exception) {
            // Error al enviar comando
            val responseTime = System.currentTimeMillis() - startTime
            ScanResult(
                mode = MODE,
                pid = pid,
                command = command,
                success = false,
                rawResponse = "ERROR: ${e.message}",
                responseTime = responseTime,
                vehicleId = vehicleId
            )
        }
    }

    /**
     * Valida si una respuesta es válida para el PID.
     */
    private fun isValidResponse(response: String, pid: String): Boolean {
        val normalized = response.trim().uppercase()

        // Respuestas inválidas conocidas
        if (normalized.contains("NO DATA") ||
            normalized.contains("?") ||
            normalized.contains("ERROR") ||
            normalized.contains("UNABLE") ||
            normalized.contains("TIMEOUT") ||
            normalized.isEmpty()
        ) {
            return false
        }

        // Debe comenzar con "41" (response Mode 01)
        if (!normalized.startsWith("41")) {
            return false
        }

        // Debe contener el PID solicitado
        if (!normalized.contains(pid.uppercase())) {
            return false
        }

        return true
    }

    /**
     * Extrae los bytes de datos de una respuesta Mode 01.
     * Ejemplo: "41 0C 1A F8" -> [0x1A, 0xF8]
     */
    private fun extractDataBytes(response: String): ByteArray {
        return try {
            val parts = response.trim().split("\\s+".toRegex())
            if (parts.size < 3) return byteArrayOf()

            // Skip "41" y PID, tomar el resto
            parts.drop(2).mapNotNull { hex ->
                try {
                    hex.toInt(16).toByte()
                } catch (e: Exception) {
                    null
                }
            }.toByteArray()
        } catch (e: Exception) {
            byteArrayOf()
        }
    }
}
