package com.fleetcare.obd.data.obd

import com.fleetcare.obd.bluetooth.BluetoothService
import com.fleetcare.obd.domain.model.SupportedPIDsBitmap
import com.fleetcare.obd.utils.Logger
import com.fleetcare.obd.utils.obd.OBDCommandParser
import kotlinx.coroutines.delay
import java.util.BitSet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detector de PIDs soportados mediante lectura de bitmaps OBD-II.
 *
 * Los PIDs de control (00, 20, 40, 60, 80, A0, C0, E0) retornan bitmaps
 * que indican qué PIDs están disponibles en cada rango de 32 PIDs.
 *
 * Sprint 2: Detección automática de PIDs soportados
 *
 * Ejemplo de funcionamiento:
 * 1. Enviar comando "0100" (PID 00 en Mode 01)
 * 2. Recibir respuesta: "41 00 BE 1F A8 13"
 *    - 41 = Mode 01 response
 *    - 00 = PID 00
 *    - BE 1F A8 13 = bitmap de 4 bytes (32 bits)
 * 3. Parsear bitmap: cada bit representa un PID (bit 31 = PID 01, ..., bit 0 = PID 20)
 * 4. Si bit 20 (PID 20) está en 1, hay más PIDs en el rango 21-40
 */
@Singleton
class SupportedPIDsDetector @Inject constructor(
    private val bluetoothService: BluetoothService
) {

    /**
     * Detecta todos los PIDs soportados por el vehículo.
     *
     * Proceso:
     * 1. Lee PID 00 para obtener PIDs 01-20
     * 2. Si PID 20 está soportado, lee PID 20 para obtener PIDs 21-40
     * 3. Continúa hasta que no haya más PIDs de control disponibles
     *
     * @param vehicleId ID del vehículo (MAC del adaptador)
     * @param vin VIN del vehículo (opcional)
     * @return Result con SupportedPIDsBitmap o error
     */
    suspend fun detectSupportedPIDs(vehicleId: String, vin: String? = null): Result<SupportedPIDsBitmap> {
        return try {
            Logger.i("🔍 ========== INICIANDO DETECCIÓN DE PIDs ==========")
            Logger.d("   VehicleId: $vehicleId")
            Logger.d("   VIN: ${vin ?: "N/A"}")

            val pidRanges = mutableMapOf<Int, List<Int>>()
            val controlPIDsToCheck = mutableListOf(0x00) // Empezar con PID 00

            while (controlPIDsToCheck.isNotEmpty()) {
                val controlPID = controlPIDsToCheck.removeAt(0)

                Logger.i("   📡 Consultando PID de control: 0x${controlPID.toString(16).uppercase().padStart(2, '0')}")

                // Leer bitmap del PID de control
                val bitmapResult = readControlPIDBitmap(controlPID)

                if (bitmapResult.isFailure) {
                    Logger.w("   ⚠️ Error al leer PID 0x${controlPID.toString(16)}: ${bitmapResult.exceptionOrNull()?.message}")
                    continue
                }

                val bitmap = bitmapResult.getOrNull() ?: continue

                // Extraer PIDs soportados de este rango
                val supportedPIDs = extractPIDsFromBitmap(bitmap, controlPID)
                pidRanges[controlPID] = supportedPIDs

                Logger.i("   ✅ PIDs en rango 0x${controlPID.toString(16)}: ${supportedPIDs.size} PIDs")
                Logger.d("      ${supportedPIDs.joinToString(", ") { "0x${it.toString(16).uppercase().padStart(2, '0')}" }}")

                // Determinar siguiente PID de control
                val nextControlPID = getNextControlPID(controlPID)
                if (nextControlPID != null && bitmap.get(0)) {
                    // Bit 0 (último bit) indica si hay más PIDs en el siguiente rango
                    if (!controlPIDsToCheck.contains(nextControlPID)) {
                        controlPIDsToCheck.add(nextControlPID)
                        Logger.d("      ➡️ Hay más PIDs, consultando 0x${nextControlPID.toString(16).uppercase().padStart(2, '0')}")
                    }
                } else {
                    Logger.d("      🏁 No hay más PIDs en el siguiente rango")
                }

                // Delay entre comandos para no saturar el ECU
                delay(100)
            }

            val result = SupportedPIDsBitmap(
                pidRanges = pidRanges,
                vehicleId = vehicleId,
                vin = vin
            )

            Logger.i("🎉 ========== DETECCIÓN COMPLETADA ==========")
            Logger.i("   Total de PIDs soportados: ${result.getTotalSupportedCount()}")
            Logger.d("   ${result.allSupportedPIDs.joinToString(", ") { "0x${it.toString(16).uppercase().padStart(2, '0')}" }}")
            Result.success(result)

        } catch (e: Exception) {
            Logger.e(e, "❌ Error en detección de PIDs soportados")
            Result.failure(e)
        }
    }

    /**
     * Lee el bitmap de un PID de control específico.
     *
     * @param controlPID PID de control (0x00, 0x20, 0x40, etc.)
     * @return Result con BitSet de 32 bits o error
     */
    private suspend fun readControlPIDBitmap(controlPID: Int): Result<BitSet> {
        return try {
            // Formatear comando OBD: Mode 01 + PID en hex
            val command = String.format("01%02X", controlPID)

            Logger.d("Enviando comando: $command")

            val responseResult = bluetoothService.sendOBDCommand(command)

            if (responseResult.isFailure) {
                return Result.failure(responseResult.exceptionOrNull()!!)
            }

            val response = responseResult.getOrNull() ?: return Result.failure(
                Exception("Respuesta vacía para PID ${controlPID.toString(16)}")
            )

            // Parsear respuesta a BitSet
            parseBitmapResponse(response, controlPID)

        } catch (e: Exception) {
            Logger.e(e, "Error al leer bitmap del PID ${controlPID.toString(16)}")
            Result.failure(e)
        }
    }

    /**
     * Parsea la respuesta hex de un PID de control a BitSet.
     *
     * Formato esperado: "41 00 BE 1F A8 13"
     * - Byte 0: 41 (mode + 0x40)
     * - Byte 1: 00 (PID)
     * - Bytes 2-5: bitmap de 32 bits
     *
     * Sprint 9.5: Actualizado para manejar headers CAN y multi-frame
     *
     * @param response Respuesta hex del comando OBD
     * @param expectedPID PID esperado para validación
     * @return Result con BitSet o error
     */
    fun parseBitmapResponse(response: String, expectedPID: Int): Result<BitSet> {
        return try {
            Logger.d("Respuesta RAW recibida: $response")

            // Sprint 9.5: Usar parser de Sprint 9.2 para limpiar headers CAN y multi-frame
            val cleanResponse = OBDCommandParser.cleanResponse(response)

            Logger.d("Parseando respuesta limpia: $cleanResponse")

            // Validar longitud mínima (mode + PID + 4 bytes bitmap = 12 chars hex)
            if (cleanResponse.length < 12) {
                return Result.failure(Exception("Respuesta muy corta: $cleanResponse (RAW: $response)"))
            }

            // Validar modo de respuesta (41 para Mode 01)
            val mode = cleanResponse.substring(0, 2).toInt(16)
            if (mode != 0x41) {
                return Result.failure(Exception("Modo inválido: $mode, esperado 0x41"))
            }

            // Validar PID
            val pid = cleanResponse.substring(2, 4).toInt(16)
            if (pid != expectedPID) {
                Logger.w("PID recibido ($pid) no coincide con esperado ($expectedPID)")
            }

            // Extraer 4 bytes del bitmap (8 caracteres hex)
            val bitmapHex = cleanResponse.substring(4, 12)
            val bitmapBytes = bitmapHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

            // Convertir a BitSet
            val bitSet = BitSet(32)
            for (byteIndex in 0..3) {
                val byte = bitmapBytes[byteIndex].toInt() and 0xFF
                for (bitIndex in 0..7) {
                    val bit = (byte shr (7 - bitIndex)) and 1
                    if (bit == 1) {
                        // Bit position: byteIndex * 8 + bitIndex
                        bitSet.set(byteIndex * 8 + bitIndex)
                    }
                }
            }

            Logger.d("Bitmap parseado: ${bitSet.cardinality()} bits en 1")
            Result.success(bitSet)

        } catch (e: Exception) {
            Logger.e(e, "Error al parsear bitmap: $response")
            Result.failure(e)
        }
    }

    /**
     * Extrae la lista de PIDs soportados de un BitSet.
     *
     * @param bitmap BitSet de 32 bits
     * @param controlPID PID de control que generó el bitmap
     * @return Lista de PIDs soportados en formato decimal
     */
    fun extractPIDsFromBitmap(bitmap: BitSet, controlPID: Int): List<Int> {
        val supportedPIDs = mutableListOf<Int>()

        // Bit 31 (MSB) = PID siguiente al control
        // Bit 30 = PID siguiente + 1
        // ...
        // Bit 0 = PID de control del siguiente rango

        for (bitIndex in 0..31) {
            if (bitmap.get(bitIndex)) {
                val pid = controlPID + bitIndex + 1
                supportedPIDs.add(pid)
            }
        }

        return supportedPIDs
    }

    /**
     * Obtiene el siguiente PID de control en la secuencia.
     *
     * @param currentPID PID de control actual
     * @return Siguiente PID de control o null si no hay más
     */
    private fun getNextControlPID(currentPID: Int): Int? {
        return when (currentPID) {
            0x00 -> 0x20
            0x20 -> 0x40
            0x40 -> 0x60
            0x60 -> 0x80
            0x80 -> 0xA0
            0xA0 -> 0xC0
            0xC0 -> 0xE0
            0xE0 -> null // No hay más rangos estándar
            else -> null
        }
    }

    /**
     * Verifica si un PID de control está soportado sin hacer la lectura completa.
     *
     * @param controlPID PID de control a verificar
     * @return Result con booleano indicando soporte
     */
    suspend fun isControlPIDSupported(controlPID: Int): Result<Boolean> {
        return try {
            val result = readControlPIDBitmap(controlPID)
            Result.success(result.isSuccess)
        } catch (e: Exception) {
            Result.success(false)
        }
    }
}
