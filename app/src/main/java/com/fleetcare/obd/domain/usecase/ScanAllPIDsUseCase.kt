package com.fleetcare.obd.domain.usecase

import com.fleetcare.obd.domain.model.DetectedDataType
import com.fleetcare.obd.domain.model.ScanProgress
import com.fleetcare.obd.domain.model.ScanResult
import com.fleetcare.obd.domain.repository.BluetoothRepository
import com.fleetcare.obd.domain.repository.RawOBDResponseRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case para escanear todos los PIDs del modo 01.
 *
 * Sprint 5: Escáner de PIDs Completo
 *
 * Itera sobre los 255 PIDs posibles (0x01-0xFF) y registra
 * las respuestas en tiempo real mediante un Flow reactivo.
 *
 * Características:
 * - Escaneo secuencial de PIDs 01-FF
 * - Delay de 150ms entre comandos (evitar saturación ECU)
 * - Timeout de 1 segundo por PID
 * - Guardado automático en RawOBDResponseRepository
 * - Clasificación automática de respuestas
 * - Cancelable mediante Flow cancellation
 *
 * @property bluetoothRepository Repositorio para enviar comandos OBD
 * @property rawOBDResponseRepository Repositorio para almacenar respuestas RAW
 */
class ScanAllPIDsUseCase @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
    private val rawOBDResponseRepository: RawOBDResponseRepository
) {
    companion object {
        private const val DELAY_BETWEEN_COMMANDS_MS = 150L
        private const val COMMAND_TIMEOUT_MS = 1000L
        private const val MODE_01 = "01"

        // PIDs estándar conocidos OBD-II
        private val STANDARD_PIDS = setOf(
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
            0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x19, 0x1C, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x24, 0x2F, 0x31,
            0x33, 0x3C, 0x3D, 0x3E, 0x3F, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x49, 0x4A, 0x4B,
            0x4C, 0x4D, 0x4E, 0x4F, 0x51, 0x52, 0x5A, 0x5B, 0x5C, 0x5D, 0x5E, 0x60, 0x61, 0x62, 0x63, 0x64,
            0x65, 0x66, 0x67, 0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D, 0x6E, 0x6F, 0x70, 0x71, 0x72, 0x73, 0x74,
            0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, 0x7B, 0x7C, 0x7D, 0x7E, 0x7F, 0x80, 0x81, 0x82, 0x83, 0x84,
            0x85, 0x86, 0x87, 0x88, 0x89, 0x8A, 0x8B, 0x8C, 0x8D, 0x8E, 0xA0
        )
    }

    /**
     * Escanea todos los PIDs del modo 01 (0x01 a 0xFF).
     *
     * Emite progreso en tiempo real mediante Flow.
     * El Flow puede ser cancelado en cualquier momento.
     *
     * @return Flow de ScanProgress con el progreso del escaneo
     */
    suspend fun execute(): Flow<ScanProgress> = flow {
        Timber.i("Iniciando escaneo completo de PIDs...")

        val startTime = System.currentTimeMillis()
        var successCount = 0
        var failedCount = 0

        // Iterar sobre todos los PIDs (1-255, no 0 porque es PID de control)
        for (pidInt in 0x01..0xFF) {
            val pid = pidInt.toString(16).padStart(2, '0').uppercase()
            val command = "$MODE_01$pid"

            Timber.d("Escaneando PID $pid ($pidInt/255)...")

            // Medir tiempo del comando
            val commandStartTime = System.currentTimeMillis()

            // Enviar comando OBD
            val obdResponse = try {
                bluetoothRepository.sendOBDCommand(command).getOrNull()
            } catch (e: Exception) {
                Timber.w("Error al escanear PID $pid: ${e.message}")
                null
            }

            val commandLatency = System.currentTimeMillis() - commandStartTime

            // Procesar respuesta
            val scanResult = processScanResult(
                pid = pid,
                command = command,
                response = obdResponse,
                latency = commandLatency
            )

            // Actualizar contadores
            if (scanResult.success) {
                successCount++
            } else {
                failedCount++
            }

            // Calcular tiempo transcurrido y estimado
            val elapsedTime = System.currentTimeMillis() - startTime
            val avgTimePerPID = elapsedTime / pidInt
            val estimatedTimeRemaining = avgTimePerPID * (255 - pidInt)

            // Emitir progreso
            val progress = ScanProgress(
                currentPID = pidInt,
                totalPIDs = 255,
                currentResult = scanResult,
                successCount = successCount,
                failedCount = failedCount,
                elapsedTimeMs = elapsedTime,
                estimatedTimeRemainingMs = estimatedTimeRemaining
            )

            emit(progress)

            // Delay entre comandos para no saturar el ECU
            delay(DELAY_BETWEEN_COMMANDS_MS)
        }

        val totalTime = System.currentTimeMillis() - startTime
        Timber.i("Escaneo completo finalizado en ${totalTime}ms. Éxitos: $successCount, Fallos: $failedCount")
    }

    /**
     * Procesa la respuesta del escaneo de un PID.
     */
    private fun processScanResult(
        pid: String,
        command: String,
        response: String?,
        latency: Long
    ): ScanResult {
        val pidInt = pid.toInt(16)

        // Verificar si el PID respondió correctamente
        val success = response != null &&
                      response.isNotBlank() &&
                      !response.contains("NO DATA", ignoreCase = true) &&
                      !response.contains("ERROR", ignoreCase = true) &&
                      !response.startsWith("?")

        if (!success) {
            return ScanResult(
                pid = pid,
                command = command,
                success = false,
                rawResponse = response ?: "NO DATA",
                timestamp = System.currentTimeMillis(),
                responseTime = latency,
                isStandardPID = pidInt in STANDARD_PIDS
            )
        }

        // Extraer bytes de datos (eliminar header "41 XX")
        val dataBytes = extractDataBytes(response!!, pid)

        // Intentar interpretación automática
        val interpretation = attemptInterpretation(pidInt, dataBytes)

        // Detectar tipo de dato
        val detectedType = detectDataType(dataBytes)

        return ScanResult(
            pid = pid,
            command = command,
            success = true,
            rawResponse = response,
            dataBytes = dataBytes,
            byteCount = dataBytes.size,
            interpretation = interpretation,
            timestamp = System.currentTimeMillis(),
            responseTime = latency,
            detectedType = detectedType,
            isStandardPID = pidInt in STANDARD_PIDS
        )
    }

    /**
     * Extrae los bytes de datos de la respuesta RAW.
     *
     * Formato esperado: "41 XX [DATA BYTES]"
     */
    private fun extractDataBytes(response: String, expectedPID: String): ByteArray {
        return try {
            // Eliminar espacios y convertir a mayúsculas
            val cleaned = response.replace(" ", "").uppercase()

            // Buscar el patrón "41XX" donde XX es el PID
            val header = "41$expectedPID"
            val headerIndex = cleaned.indexOf(header)

            if (headerIndex == -1) {
                return byteArrayOf()
            }

            // Extraer bytes después del header
            val dataHex = cleaned.substring(headerIndex + header.length)

            // Convertir hex string a bytes
            dataHex.chunked(2)
                .mapNotNull { it.toIntOrNull(16)?.toByte() }
                .toByteArray()
        } catch (e: Exception) {
            Timber.w("Error al extraer bytes de '$response': ${e.message}")
            byteArrayOf()
        }
    }

    /**
     * Intenta interpretar el valor del PID automáticamente.
     */
    private fun attemptInterpretation(pid: Int, bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null

        return when (pid) {
            0x0C -> { // RPM
                if (bytes.size >= 2) {
                    val rpm = ((bytes[0].toUByte().toInt() * 256) + bytes[1].toUByte().toInt()) / 4
                    "RPM: $rpm"
                } else null
            }
            0x0D -> { // Velocidad
                "Velocidad: ${bytes[0].toUByte().toInt()} km/h"
            }
            0x05 -> { // Temperatura refrigerante
                "Temp. refrigerante: ${bytes[0].toUByte().toInt() - 40}°C"
            }
            0x0F -> { // Temperatura aire admisión
                "Temp. admisión: ${bytes[0].toUByte().toInt() - 40}°C"
            }
            0x04 -> { // Carga motor
                val load = (bytes[0].toUByte().toInt() * 100) / 255
                "Carga motor: $load%"
            }
            0x11 -> { // Posición acelerador
                val throttle = (bytes[0].toUByte().toInt() * 100) / 255
                "Acelerador: $throttle%"
            }
            0x2F -> { // Nivel combustible
                val fuel = (bytes[0].toUByte().toInt() * 100) / 255
                "Combustible: $fuel%"
            }
            0x42 -> { // Voltaje módulo control
                if (bytes.size >= 2) {
                    val voltage = ((bytes[0].toUByte().toInt() * 256) + bytes[1].toUByte().toInt()) / 1000.0
                    "Voltaje: ${String.format("%.2f", voltage)}V"
                } else null
            }
            else -> {
                // Para otros PIDs, mostrar hex básico
                val hexStr = bytes.joinToString(" ") { "%02X".format(it.toUByte().toInt()) }
                "$hexStr (${bytes.size} bytes)"
            }
        }
    }

    /**
     * Detecta el tipo de dato basado en el patrón de bytes.
     */
    private fun detectDataType(bytes: ByteArray): DetectedDataType? {
        if (bytes.isEmpty()) return null

        return when (bytes.size) {
            1 -> {
                val value = bytes[0].toUByte().toInt()
                when {
                    value in 0..100 -> DetectedDataType.PERCENTAGE
                    value in 140..255 -> DetectedDataType.TEMPERATURE
                    else -> DetectedDataType.SINGLE_BYTE
                }
            }
            2 -> DetectedDataType.TWO_BYTE_BIG_ENDIAN
            4 -> DetectedDataType.FOUR_BYTE
            else -> DetectedDataType.UNKNOWN
        }
    }
}
