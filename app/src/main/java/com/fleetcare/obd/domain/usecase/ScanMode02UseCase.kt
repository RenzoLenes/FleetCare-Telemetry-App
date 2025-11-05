package com.fleetcare.obd.domain.usecase

import com.fleetcare.obd.bluetooth.BluetoothService
import com.fleetcare.obd.domain.model.PIDMetadataHelper
import com.fleetcare.obd.domain.model.ScanResult
import com.fleetcare.obd.utils.Logger
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Use Case para escaneo de Mode 02 (Freeze Frame Data).
 *
 * Escanea datos congelados del momento en que se activó el MIL (Check Engine).
 * Similar a Mode 01 pero con datos históricos.
 */
class ScanMode02UseCase @Inject constructor(
    private val bluetoothService: BluetoothService
) {
    companion object {
        private const val MODE = "02"
        private const val FRAME = "00"  // Frame 0 (más reciente)
    }

    /**
     * Escanea PIDs del modo 02.
     *
     * @param vehicleId ID del vehículo
     * @param range Rango de PIDs a escanear
     * @param timeout Timeout por PID en ms
     * @return Lista de resultados
     */
    suspend operator fun invoke(
        vehicleId: String,
        range: IntRange,
        timeout: Long = 300L
    ): List<ScanResult> {
        val results = mutableListOf<ScanResult>()

        for (pidInt in range) {
            val pid = String.format("%02X", pidInt)
            val result = scanSinglePID(vehicleId, pid, timeout)
            results.add(result)
            delay(10)
        }

        Logger.d("Mode 02 scan completed: ${results.count { it.success }}/${results.size} successful")
        return results
    }

    private suspend fun scanSinglePID(
        vehicleId: String,
        pid: String,
        timeout: Long
    ): ScanResult {
        // Mode 02 comando: 02 [PID] [FRAME]
        val command = "$MODE$pid$FRAME"
        val startTime = System.currentTimeMillis()

        return try {
            val response = bluetoothService.sendCommand(command, timeout)
            val responseTime = System.currentTimeMillis() - startTime

            if (isValidResponse(response, pid)) {
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

    private fun isValidResponse(response: String, pid: String): Boolean {
        val normalized = response.trim().uppercase()
        if (normalized.contains("NO DATA") || normalized.contains("?") ||
            normalized.contains("ERROR") || normalized.isEmpty()) {
            return false
        }
        return normalized.startsWith("42") && normalized.contains(pid.uppercase())
    }

    private fun extractDataBytes(response: String): ByteArray {
        return try {
            val parts = response.trim().split("\\s+".toRegex())
            if (parts.size < 4) return byteArrayOf()
            parts.drop(3).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
        } catch (e: Exception) {
            byteArrayOf()
        }
    }
}
