package com.fleetcare.obd.domain.usecase

import com.fleetcare.obd.bluetooth.BluetoothService
import com.fleetcare.obd.domain.model.ECUInfo
import com.fleetcare.obd.domain.model.PIDMetadataHelper
import com.fleetcare.obd.domain.model.ScanResult
import com.fleetcare.obd.domain.repository.VehicleProfileRepository
import com.fleetcare.obd.utils.Logger
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Use Case para escaneo de Mode 09 (Vehicle Information).
 *
 * Obtiene información del vehículo: VIN, Calibration ID, CVN, ECU name, etc.
 * Esta información es crucial para identificar el vehículo y actualizar su perfil.
 */
class ScanMode09UseCase @Inject constructor(
    private val bluetoothService: BluetoothService,
    private val profileRepository: VehicleProfileRepository
) {
    companion object {
        private const val MODE = "09"

        // PIDs importantes de Mode 09
        private const val PID_VIN = "02"                  // Vehicle Identification Number
        private const val PID_CALIBRATION_ID = "04"       // Calibration ID
        private const val PID_CVN = "06"                  // Calibration Verification Number
        private const val PID_ECU_NAME = "0A"             // ECU Name
    }

    /**
     * Escanea PIDs del modo 09 y actualiza el perfil del vehículo.
     *
     * @param vehicleId ID del vehículo
     * @param range Rango de PIDs a escanear
     * @param timeout Timeout por PID en ms
     * @return Lista de resultados
     */
    suspend operator fun invoke(
        vehicleId: String,
        range: IntRange,
        timeout: Long = 500L  // Mode 09 necesita más tiempo
    ): List<ScanResult> {
        val results = mutableListOf<ScanResult>()

        for (pidInt in range) {
            val pid = String.format("%02X", pidInt)
            val result = scanSinglePID(vehicleId, pid, timeout)
            results.add(result)
            delay(20)  // Mode 09 necesita más pausa
        }

        // Actualizar perfil del vehículo con información extraída
        updateVehicleProfile(vehicleId, results)

        Logger.d("Mode 09 scan completed: ${results.count { it.success }}/${results.size} successful")
        return results
    }

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
                val dataBytes = extractDataBytes(response)
                val metadata = PIDMetadataHelper.createAutoDetected(
                    mode = MODE,
                    pid = pid,
                    rawResponse = response,
                    responseTime = responseTime
                )

                // Interpretación especial para Mode 09
                val interpretation = interpretMode09(pid, response)

                ScanResult(
                    mode = MODE,
                    pid = pid,
                    command = command,
                    success = true,
                    rawResponse = response,
                    dataBytes = dataBytes,
                    byteCount = dataBytes.size,
                    interpretation = interpretation,
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
        return normalized.startsWith("49") && normalized.contains(pid.uppercase())
    }

    private fun extractDataBytes(response: String): ByteArray {
        return try {
            val parts = response.trim().split("\\s+".toRegex())
            if (parts.size < 3) return byteArrayOf()
            parts.drop(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
        } catch (e: Exception) {
            byteArrayOf()
        }
    }

    /**
     * Interpreta respuestas específicas de Mode 09.
     */
    private fun interpretMode09(pid: String, response: String): String? {
        return when (pid.uppercase()) {
            PID_VIN, PID_CALIBRATION_ID, PID_ECU_NAME -> {
                // Convertir bytes a ASCII
                parseASCIIString(response)
            }
            PID_CVN -> {
                // CVN es hexadecimal, no ASCII
                response.substringAfter("49 $pid").trim()
            }
            else -> null
        }
    }

    /**
     * Convierte respuesta a string ASCII.
     */
    private fun parseASCIIString(response: String): String {
        return try {
            val bytes = response.trim()
                .split("\\s+".toRegex())
                .drop(2)  // Skip "49 PID"
                .mapNotNull {
                    try {
                        val value = it.toInt(16)
                        if (value in 32..126) value.toChar() else null
                    } catch (e: Exception) {
                        null
                    }
                }
            String(bytes.toCharArray()).trim()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Actualiza el perfil del vehículo con información de Mode 09.
     */
    private suspend fun updateVehicleProfile(vehicleId: String, results: List<ScanResult>) {
        val successfulResults = results.filter { it.success }

        // Extraer VIN
        val vinResult = successfulResults.find { it.pid.uppercase() == PID_VIN }
        val vin = vinResult?.interpretation ?: ""

        // Extraer ECU Info
        val ecuInfo = ECUInfo.fromMode09Results(successfulResults)

        // Actualizar perfil
        if (vin.isNotEmpty()) {
            val profile = profileRepository.getProfile(vehicleId)
            if (profile != null) {
                profileRepository.updateVehicleInfo(
                    vehicleId = vehicleId,
                    vin = vin,
                    make = profile.make,
                    model = profile.model,
                    year = profile.year
                )
            }
        }

        if (ecuInfo.hasData()) {
            profileRepository.updateECUInfo(vehicleId, ecuInfo)
        }

        Logger.d("Vehicle profile updated with Mode 09 data")
    }
}
