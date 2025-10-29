package com.fleetcare.obd.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import com.fleetcare.obd.utils.Constants
import com.fleetcare.obd.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Conector RFCOMM para establecer y gestionar la conexión socket Bluetooth.
 *
 * RFCOMM es el protocolo de emulación de puerto serial sobre Bluetooth que
 * usan los adaptadores OBDII. Usa el UUID del Serial Port Profile (SPP).
 *
 * Responsabilidades:
 * - Crear socket RFCOMM
 * - Conectar al dispositivo
 * - Gestionar streams de entrada/salida
 * - Enviar y recibir datos
 * - Manejar desconexión y limpieza de recursos
 */
class RFCOMMConnector(
    private val bluetoothAdapter: BluetoothAdapter
) {

    // UUID estándar del Serial Port Profile (SPP) para Bluetooth clásico
    private val sppUuid = UUID.fromString(Constants.Bluetooth.SPP_UUID)

    // Socket Bluetooth activo
    private var bluetoothSocket: BluetoothSocket? = null

    // Streams de comunicación
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    /**
     * Indica si hay una conexión activa.
     */
    val isConnected: Boolean
        get() = bluetoothSocket?.isConnected == true

    /**
     * Conecta al dispositivo Bluetooth especificado.
     *
     * Este método:
     * 1. Cancela el descubrimiento de dispositivos (interfiere con la conexión)
     * 2. Obtiene el dispositivo remoto por su dirección MAC
     * 3. Crea un socket RFCOMM
     * 4. Establece la conexión
     * 5. Obtiene los streams de entrada/salida
     *
     * @param deviceAddress Dirección MAC del dispositivo (formato XX:XX:XX:XX:XX:XX)
     * @return Result indicando éxito o fallo
     */
    @Suppress("MissingPermission")
    suspend fun connect(deviceAddress: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Logger.bluetooth("Iniciando conexión RFCOMM a $deviceAddress")

            // Cancelar descubrimiento si está activo (mejora la estabilidad de la conexión)
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
                Logger.bluetooth("Descubrimiento cancelado para mejorar conexión")
            }

            // Obtener el dispositivo remoto
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
                ?: return@withContext Result.failure(
                    IOException("No se pudo obtener el dispositivo: $deviceAddress")
                )

            Logger.bluetooth("Dispositivo remoto obtenido: ${device.address}")

            // Crear socket RFCOMM usando SPP UUID
            bluetoothSocket = device.createRfcommSocketToServiceRecord(sppUuid)
                ?: return@withContext Result.failure(
                    IOException("No se pudo crear socket RFCOMM")
                )

            Logger.bluetooth("Socket RFCOMM creado")

            // Conectar con timeout
            withTimeout(Constants.Bluetooth.CONNECTION_TIMEOUT_MS) {
                bluetoothSocket?.connect()
            }

            // Verificar que la conexión fue exitosa
            if (bluetoothSocket?.isConnected != true) {
                return@withContext Result.failure(
                    IOException("Socket conectado pero isConnected es false")
                )
            }

            // Obtener streams de entrada/salida
            inputStream = bluetoothSocket?.inputStream
            outputStream = bluetoothSocket?.outputStream

            if (inputStream == null || outputStream == null) {
                disconnect()
                return@withContext Result.failure(
                    IOException("No se pudieron obtener los streams de I/O")
                )
            }

            Logger.bluetooth("Conexión RFCOMM establecida exitosamente")
            Result.success(Unit)

        } catch (e: IOException) {
            Logger.bluetoothError("Error de IO al conectar", e)
            disconnect()
            Result.failure(e)
        } catch (e: Exception) {
            Logger.bluetoothError("Error inesperado al conectar", e)
            disconnect()
            Result.failure(e)
        }
    }

    /**
     * Envía un comando al dispositivo OBDII.
     *
     * Los comandos deben terminar con \r (carriage return) según el protocolo ELM327.
     *
     * @param command Comando a enviar (sin el \r final)
     * @return Result indicando éxito o fallo
     */
    suspend fun sendCommand(command: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isConnected) {
                return@withContext Result.failure(
                    IOException("No hay conexión activa")
                )
            }

            val output = outputStream ?: return@withContext Result.failure(
                IOException("OutputStream es null")
            )

            // Los comandos ELM327 deben terminar con \r
            val commandWithTerminator = "$command\r"
            val bytes = commandWithTerminator.toByteArray()

            Logger.obdCommand("Enviando: $command")

            output.write(bytes)
            output.flush()

            Result.success(Unit)

        } catch (e: IOException) {
            Logger.obdError("Error al enviar comando: $command", e)
            Result.failure(e)
        } catch (e: Exception) {
            Logger.obdError("Error inesperado al enviar comando", e)
            Result.failure(e)
        }
    }

    /**
     * Lee la respuesta del dispositivo OBDII.
     *
     * Lee hasta encontrar el terminador '>' que indica fin de respuesta ELM327,
     * o hasta que se agote el timeout.
     *
     * @param timeoutMs Timeout en milisegundos
     * @return Result con la respuesta o error
     */
    suspend fun readResponse(
        timeoutMs: Long = Constants.OBD.RESPONSE_TIMEOUT_MS
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isConnected) {
                return@withContext Result.failure(
                    IOException("No hay conexión activa")
                )
            }

            val input = inputStream ?: return@withContext Result.failure(
                IOException("InputStream es null")
            )

            val response = StringBuilder()
            val buffer = ByteArray(1024)
            val startTime = System.currentTimeMillis()

            // Leer hasta encontrar el terminador '>' o timeout
            while (true) {
                // Verificar timeout
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    Logger.obdError("Timeout al leer respuesta")
                    return@withContext Result.failure(
                        IOException("Timeout al leer respuesta")
                    )
                }

                // Verificar si hay datos disponibles
                if (input.available() > 0) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead > 0) {
                        val chunk = String(buffer, 0, bytesRead)
                        response.append(chunk)

                        // Verificar si hemos recibido el terminador
                        if (response.contains(Constants.OBD.RESPONSE_TERMINATOR)) {
                            break
                        }
                    }
                } else {
                    // No hay datos disponibles, esperar un poco
                    Thread.sleep(10)
                }
            }

            val responseStr = response.toString()
                .replace(Constants.OBD.RESPONSE_TERMINATOR, "")
                .trim()

            Logger.obdCommand("Respuesta recibida", responseStr)

            Result.success(responseStr)

        } catch (e: IOException) {
            Logger.obdError("Error al leer respuesta", e)
            Result.failure(e)
        } catch (e: Exception) {
            Logger.obdError("Error inesperado al leer respuesta", e)
            Result.failure(e)
        }
    }

    /**
     * Envía un comando y espera la respuesta.
     *
     * Método de conveniencia que combina sendCommand y readResponse.
     *
     * @param command Comando a enviar
     * @return Result con la respuesta o error
     */
    suspend fun sendAndReceive(command: String): Result<String> {
        val sendResult = sendCommand(command)
        if (sendResult.isFailure) {
            return Result.failure(sendResult.exceptionOrNull()!!)
        }

        // Pequeño delay para dar tiempo al dispositivo a procesar
        kotlinx.coroutines.delay(Constants.OBD.COMMAND_DELAY_MS)

        return readResponse()
    }

    /**
     * Desconecta y limpia todos los recursos.
     */
    fun disconnect() {
        try {
            Logger.bluetooth("Desconectando socket RFCOMM...")

            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()

            inputStream = null
            outputStream = null
            bluetoothSocket = null

            Logger.bluetooth("Socket RFCOMM desconectado y limpiado")

        } catch (e: IOException) {
            Logger.bluetoothError("Error al cerrar socket", e)
        }
    }

    /**
     * Verifica si la conexión está activa.
     *
     * Intenta enviar un comando simple para verificar la conexión.
     */
    suspend fun verifyConnection(): Boolean {
        if (!isConnected) return false

        return try {
            // Intentar enviar comando simple
            val result = sendAndReceive("ATI") // Get device info
            result.isSuccess
        } catch (e: Exception) {
            Logger.bluetoothError("Error al verificar conexión", e)
            false
        }
    }
}
