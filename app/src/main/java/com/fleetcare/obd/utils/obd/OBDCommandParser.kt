package com.fleetcare.obd.utils.obd

import com.fleetcare.obd.utils.Logger

/**
 * Parser para respuestas de comandos OBDII.
 *
 * Las respuestas ELM327 vienen en formato hexadecimal y deben ser interpretadas
 * según el PID solicitado. Este parser maneja todo el proceso de conversión.
 *
 * Formato típico de respuesta:
 * Comando enviado: "010C" (RPM)
 * Respuesta: "41 0C 1A F8" donde:
 *   - 41 = Modo 01 + 40 (respuesta)
 *   - 0C = PID solicitado (RPM)
 *   - 1A F8 = Datos (2 bytes para RPM)
 */
object OBDCommandParser {

    /**
     * Parsea una respuesta OBDII completa.
     *
     * @param command Comando enviado (ej: "010C")
     * @param response Respuesta recibida del adaptador
     * @return Valor parseado o null si hay error
     */
    fun parseResponse(command: String, response: String): Double? {
        try {
            // Obtener el PID correspondiente al comando
            val pid = PIDConstants.getPIDByCommand(command)
            if (pid == null) {
                Logger.obdError("PID no encontrado para comando: $command")
                return null
            }

            // Limpiar la respuesta
            val cleanResponse = cleanResponse(response)

            // Verificar que la respuesta corresponde al comando
            if (!isValidResponse(command, cleanResponse)) {
                Logger.obdError("Respuesta inválida para comando $command: $cleanResponse")
                return null
            }

            // Extraer los bytes de datos
            val dataBytes = extractDataBytes(cleanResponse)
            if (dataBytes.isEmpty()) {
                Logger.obdError("No se pudieron extraer bytes de datos")
                return null
            }

            // Aplicar la fórmula del PID
            val value = pid.formula(dataBytes)

            // Validar rango
            if (value < pid.minValue || value > pid.maxValue) {
                Logger.w("Valor fuera de rango para ${pid.name}: $value (esperado: ${pid.minValue}-${pid.maxValue})")
            }

            Logger.obd("Parseado ${pid.name}: $value ${pid.unit}")
            return value

        } catch (e: Exception) {
            Logger.obdError("Error al parsear respuesta: $response", e)
            return null
        }
    }

    /**
     * Limpia una respuesta OBDII removiendo caracteres innecesarios.
     *
     * Elimina:
     * - Espacios
     * - Saltos de línea
     * - Caracteres de retorno
     * - Prompt (>)
     * - Echo del comando
     */
    public fun cleanResponse(response: String): String {
        return response
            .replace(" ", "")           // Quitar espacios
            .replace("\r", "")          // Quitar carriage return
            .replace("\n", "")          // Quitar line feed
            .replace(">", "")           // Quitar prompt
            .trim()
            .uppercase()                // Normalizar a mayúsculas
    }

    /**
     * Verifica si una respuesta es válida para el comando dado.
     *
     * Una respuesta válida debe:
     * 1. Comenzar con "4X" donde X es el modo + 40
     * 2. Seguido del PID solicitado
     *
     * Ejemplo:
     * Comando: "010C" (Modo 01, PID 0C)
     * Respuesta válida: "410C..." (41 = 01 + 40, 0C = PID)
     */
    private fun isValidResponse(command: String, cleanResponse: String): Boolean {
        if (cleanResponse.length < 4) return false

        // Extraer modo y PID del comando
        val mode = command.substring(0, 2)
        val pid = command.substring(2, 4)

        // Calcular modo de respuesta (modo + 40)
        val responseMode = try {
            val modeInt = mode.toInt(16)
            (modeInt + 0x40).toString(16).padStart(2, '0').uppercase()
        } catch (e: NumberFormatException) {
            return false
        }

        // Verificar que la respuesta comienza con modo + PID
        val expectedStart = "$responseMode$pid"
        return cleanResponse.startsWith(expectedStart, ignoreCase = true)
    }

    /**
     * Extrae los bytes de datos de una respuesta.
     *
     * Los primeros 4 caracteres hex son el modo y PID,
     * el resto son los datos.
     *
     * Ejemplo:
     * Respuesta: "410C1AF8"
     * Bytes de datos: [1A, F8]
     */
    private fun extractDataBytes(cleanResponse: String): ByteArray {
        // Saltar los primeros 4 caracteres (modo + PID)
        if (cleanResponse.length <= 4) return byteArrayOf()

        val dataHex = cleanResponse.substring(4)

        // Convertir pares de caracteres hex a bytes
        return try {
            dataHex.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        } catch (e: NumberFormatException) {
            Logger.obdError("Error al convertir hex a bytes: $dataHex", e)
            byteArrayOf()
        }
    }

    /**
     * Parsea múltiples respuestas de un batch de comandos.
     *
     * @param commandResponsePairs Lista de pares (comando, respuesta)
     * @return Mapa de comando a valor parseado
     */
    fun parseBatchResponses(
        commandResponsePairs: List<Pair<String, String>>
    ): Map<String, Double> {
        return commandResponsePairs
            .mapNotNull { (command, response) ->
                parseResponse(command, response)?.let { value ->
                    command to value
                }
            }
            .toMap()
    }

    /**
     * Verifica si una respuesta indica un error.
     *
     * Errores comunes:
     * - "NO DATA": El ECU no tiene datos para ese PID
     * - "UNABLE TO CONNECT": No hay comunicación con el ECU
     * - "BUS INIT": Error de inicialización del bus CAN
     * - "?": Comando no reconocido
     * - "ERROR": Error genérico
     */
    fun isErrorResponse(response: String): Boolean {
        val upperResponse = response.uppercase()
        return upperResponse.contains("NO DATA") ||
                upperResponse.contains("UNABLE TO CONNECT") ||
                upperResponse.contains("BUS INIT") ||
                upperResponse.contains("ERROR") ||
                upperResponse.trim() == "?"
    }

    /**
     * Extrae el mensaje de error de una respuesta.
     */
    fun getErrorMessage(response: String): String {
        val upperResponse = response.uppercase()
        return when {
            upperResponse.contains("NO DATA") -> "Sin datos disponibles para este parámetro"
            upperResponse.contains("UNABLE TO CONNECT") -> "No se puede conectar al ECU"
            upperResponse.contains("BUS INIT") -> "Error de inicialización del bus"
            upperResponse.contains("ERROR") -> "Error en el comando"
            upperResponse.trim() == "?" -> "Comando no reconocido"
            else -> "Error desconocido: $response"
        }
    }

    /**
     * Valida que un valor esté dentro del rango esperado del PID.
     */
    fun isValueInRange(command: String, value: Double): Boolean {
        val pid = PIDConstants.getPIDByCommand(command) ?: return false
        return value >= pid.minValue && value <= pid.maxValue
    }

    /**
     * Obtiene el nombre del parámetro para un comando.
     */
    fun getParameterName(command: String): String? {
        return PIDConstants.getPIDByCommand(command)?.name
    }

    /**
     * Obtiene la unidad de medida para un comando.
     */
    fun getUnit(command: String): String? {
        return PIDConstants.getPIDByCommand(command)?.unit
    }
}
