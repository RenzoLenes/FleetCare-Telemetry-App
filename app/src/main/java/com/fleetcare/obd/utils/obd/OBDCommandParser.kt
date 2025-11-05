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
 *
 * Sprint 7: Soporte para Modo 22 (PIDs del fabricante)
 * Comando enviado: "22 11 06" (GM Oil Life)
 * Respuesta: "62 11 06 5A" donde:
 *   - 62 = Modo 22 + 40 (respuesta)
 *   - 11 06 = PID de 2 bytes
 *   - 5A = Datos
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
     * Sprint 9.2: Actualizado para manejar respuestas CAN multi-frame.
     *
     * Elimina:
     * - Espacios
     * - Saltos de línea
     * - Caracteres de retorno
     * - Prompt (>)
     * - Echo del comando
     * - Headers CAN si están presentes
     *
     * Maneja formatos:
     * - Single-frame: "41 0C 1A F8"
     * - Multi-frame CAN: "0:2FFFFFFFFFFFFF\n1:XXXXXX"
     * - Con headers: "7E8 03 41 0C 1A F8"
     */
    public fun cleanResponse(response: String): String {
        var cleaned = response
            .replace(">", "")           // Quitar prompt
            .trim()
            .uppercase()                // Normalizar a mayúsculas

        // Sprint 9.2: Detectar y parsear multi-frame CAN
        if (isCANMultiFrame(cleaned)) {
            cleaned = parseCANMultiFrame(cleaned)
        }

        // Sprint 9.2: Remover headers CAN si están presentes
        cleaned = removeCANHeaders(cleaned)

        // Limpiar espacios y line breaks
        return cleaned
            .replace(" ", "")
            .replace("\r", "")
            .replace("\n", "")
            .trim()
    }

    // ========== SPRINT 9.2: MULTI-FRAME CAN PARSER ==========

    /**
     * Detecta si una respuesta es multi-frame CAN.
     *
     * Formato multi-frame CAN:
     * - Primer frame: "0:LLXXXXXX" donde LL es longitud en hex
     * - Frames siguientes: "1:XXXXXX", "2:XXXXXX", etc.
     *
     * Ejemplos:
     * - "0:2FFFFFFFFFFFFF" → true (multi-frame)
     * - "41 0C 1A F8" → false (single-frame)
     * - "0:149010255444D455231\n1:56343248523348354A42" → true (multi-frame con newline)
     *
     * @param response Respuesta a verificar
     * @return true si es multi-frame CAN
     */
    private fun isCANMultiFrame(response: String): Boolean {
        // Buscar patrón "0:" que indica primer frame
        val hasFirstFrame = response.contains(Regex("[0-9A-F]:"))

        // Verificar que no sea solo un "0:" aislado
        if (!hasFirstFrame) return false

        // Verificar formato del primer frame: debe tener al menos "0:LL"
        val firstFramePattern = Regex("0:[0-9A-F]{1,2}")
        return firstFramePattern.containsMatchIn(response)
    }

    /**
     * Parsea una respuesta CAN multi-frame y combina los frames.
     *
     * Formato:
     * - Frame 0: "0:LLXXXXXX" (LL = longitud total en bytes, XXXX = primeros datos)
     * - Frame 1: "1:XXXXXX" (siguiente chunk de datos)
     * - Frame 2: "2:XXXXXX" (siguiente chunk de datos)
     * - etc.
     *
     * Ejemplo:
     * Input:  "0:2FFFFFFFFFFFFF\n1:414243"
     * Output: "FFFFFFFFFFFFFFFFFFFFFFF414243"
     *
     * @param response Respuesta multi-frame RAW
     * @return Datos combinados de todos los frames
     */
    private fun parseCANMultiFrame(response: String): String {
        try {
            // Dividir por newlines o frames
            val frames = response.split(Regex("[\n\r]+"))
                .filter { it.isNotBlank() }

            if (frames.isEmpty()) {
                Logger.w("Multi-frame vacío")
                return response
            }

            val combinedData = StringBuilder()
            var expectedLength: Int? = null

            for (frame in frames) {
                val trimmed = frame.trim()

                // Buscar patrón "N:XXXX" donde N es el número de frame
                val frameMatch = Regex("([0-9A-F]):([0-9A-F]+)").find(trimmed)

                if (frameMatch != null) {
                    val frameNumber = frameMatch.groupValues[1].toInt(16)
                    val frameData = frameMatch.groupValues[2]

                    if (frameNumber == 0) {
                        // Frame 0: primer byte o dos bytes son la longitud
                        if (frameData.length >= 2) {
                            // Extraer longitud (puede ser 1 o 2 bytes)
                            val lengthHex = if (frameData.length >= 3 && frameData[0] == '0') {
                                // Formato: 0:0LLXXXXXX (longitud de 1 byte)
                                frameData.substring(0, 2)
                            } else {
                                // Formato: 0:LLXXXXXX (longitud de 1 byte)
                                frameData.substring(0, 2)
                            }

                            expectedLength = lengthHex.toInt(16)

                            // Agregar datos después de la longitud
                            combinedData.append(frameData.substring(2))

                            Logger.d("Multi-frame: longitud esperada = $expectedLength bytes")
                        } else {
                            combinedData.append(frameData)
                        }
                    } else {
                        // Frames 1, 2, 3, etc.: solo datos
                        combinedData.append(frameData)
                    }
                } else {
                    // No tiene formato "N:", agregar tal cual
                    combinedData.append(trimmed)
                }
            }

            val result = combinedData.toString()

            // Validar longitud si se especificó
            if (expectedLength != null) {
                val actualLength = result.length / 2  // 2 caracteres hex = 1 byte
                if (actualLength < expectedLength) {
                    Logger.w("Multi-frame incompleto: esperado $expectedLength bytes, recibido $actualLength bytes")
                }
            }

            Logger.d("Multi-frame parseado: ${frames.size} frames → ${result.length} caracteres hex")
            return result

        } catch (e: Exception) {
            Logger.e(e, "Error al parsear multi-frame CAN")
            return response
        }
    }

    /**
     * Remueve headers CAN de una respuesta.
     *
     * Formatos de headers CAN:
     * 1. Headers con espacios: "7E8 03 41 0C 1A F8"
     * 2. Headers sin espacios (Chevrolet/GM): "7E8064100BE3EA813"
     * 3. Headers de 1 byte al inicio: "48 6B 10 41 0C XX XX"
     * 4. Sin headers: "41 0C 1A F8"
     *
     * @param response Respuesta con posibles headers
     * @return Respuesta sin headers CAN
     */
    private fun removeCANHeaders(response: String): String {
        val trimmed = response.trim()

        // Si la respuesta es muy corta, no tiene headers
        if (trimmed.length < 6) return trimmed

        // NUEVO: Detectar formato CAN sin espacios (Chevrolet/GM)
        // Formato: 7E8064100BE3EA813
        //   - 7E8 = ECU ID (3 chars)
        //   - 06 = Longitud (1-2 chars)
        //   - 4100BE3EA813 = Datos
        if (!trimmed.contains(" ") && trimmed.length >= 8) {
            // Buscar patrón: [ECU_ID de 3 chars][Longitud de 1-2 chars][Modo 4X o 62][Resto]
            val canNoSpacePattern = Regex("^[0-9A-F]{3}[0-9A-F]{1,2}((?:4[0-9A-F]|62)[0-9A-F]+)$")
            val matchNoSpace = canNoSpacePattern.find(trimmed)

            if (matchNoSpace != null) {
                val dataOnly = matchNoSpace.groupValues[1]
                Logger.d("Removiendo header CAN sin espacios (formato GM/Chevrolet)")
                return dataOnly
            }
        }

        // Detectar header de 3 bytes con espacios (ej: "7E8 03 41 0C...")
        // Header CAN típico: 7E8, 7E0, 7DF, etc.
        val header3BytePattern = Regex("^[0-9A-F]{3}\\s+[0-9A-F]{2}\\s+([0-9A-F\\s]+)$")
        val match3Byte = header3BytePattern.find(trimmed)

        if (match3Byte != null) {
            val dataOnly = match3Byte.groupValues[1]
            Logger.d("Removiendo header CAN de 3 bytes con espacios")
            return dataOnly.trim()
        }

        // Detectar header de 1-2 bytes al inicio (48 6B 10 → modo 41)
        // Si empieza con bytes que no son modo de respuesta (41, 62, etc.)
        val spacedResponse = trimmed.replace(Regex("([0-9A-F]{2})"), "$1 ").trim()
        val bytes = spacedResponse.split("\\s+".toRegex())

        if (bytes.size >= 4) {
            // Buscar dónde empieza el modo de respuesta (41, 62, 43, etc.)
            for (i in 0 until bytes.size - 1) {
                val byte = bytes[i]
                // Modos de respuesta OBD: 41, 42, 43, 44, 45, 46, 47, 49, 62
                if (byte.matches(Regex("^4[1-9]$")) || byte == "62") {
                    // Encontramos el inicio de la respuesta OBD
                    if (i > 0) {
                        Logger.d("Removiendo header CAN de $i bytes")
                        return bytes.drop(i).joinToString(" ")
                    }
                    break
                }
            }
        }

        // No se detectaron headers, retornar tal cual
        return trimmed
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
    /**
     * Sprint 9.6: Mejorado para mensajes más amigables y útiles.
     */
    fun getErrorMessage(response: String): String {
        val upperResponse = response.uppercase()
        return when {
            upperResponse.contains("NO DATA") ->
                "NO DATA" // Dejar técnico, se maneja especialmente en Sprint 9.4

            upperResponse.contains("UNABLE TO CONNECT") ->
                "No se puede comunicar con el vehículo. Verifica que:\n" +
                "• El motor esté encendido\n" +
                "• El adaptador esté bien conectado al puerto OBD\n" +
                "• El vehículo sea compatible con OBD-II"

            upperResponse.contains("BUS INIT") || upperResponse.contains("BUS ERROR") ->
                "Error de comunicación con el vehículo. Intenta:\n" +
                "• Reconectar el adaptador\n" +
                "• Apagar y encender el motor\n" +
                "• Verificar que el puerto OBD no esté dañado"

            upperResponse.contains("CAN ERROR") ->
                "Error en el bus CAN. El vehículo puede no ser compatible o hay un problema de conexión"

            upperResponse.contains("BUFFER") || upperResponse.contains("FULL") ->
                "Buffer lleno. Reduciendo velocidad de lectura..."

            upperResponse.contains("STOPPED") ->
                "Comunicación detenida por el usuario"

            upperResponse.trim() == "?" ->
                "Comando no reconocido por el adaptador"

            upperResponse.contains("SEARCHING") ->
                "Buscando protocolo..." // No es error, es informativo

            upperResponse.contains("ERROR") ->
                "Error en el comando OBD"

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

    // ========== SPRINT 7: MODO 22 SUPPORT ==========

    /**
     * Detecta si un comando es del Modo 22.
     */
    fun isMode22Command(command: String): Boolean {
        val cleanCommand = command.replace(" ", "").uppercase()
        return cleanCommand.startsWith(Mode22Constants.MODE_22_PREFIX)
    }

    /**
     * Parsea una respuesta del Modo 22.
     *
     * @param command Comando enviado (ej: "22 11 06")
     * @param response Respuesta recibida (ej: "62 11 06 5A")
     * @return Array de bytes de datos o null si hay error
     */
    fun parseMode22Response(command: String, response: String): ByteArray? {
        try {
            val cleanResponse = cleanResponse(response)

            // Verificar que es una respuesta válida del Modo 22
            if (!cleanResponse.startsWith(Mode22Constants.MODE_22_RESPONSE_PREFIX)) {
                Logger.obdError("Respuesta no es del Modo 22: $cleanResponse")
                return null
            }

            // Extraer PID de la respuesta (bytes 2-5, después de "62")
            if (cleanResponse.length < 6) {
                Logger.obdError("Respuesta Modo 22 muy corta: $cleanResponse")
                return null
            }

            val responsePID = cleanResponse.substring(2, 6)

            // Extraer PID esperado del comando
            val commandClean = command.replace(" ", "").uppercase()
            if (commandClean.length < 6) {
                Logger.obdError("Comando Modo 22 inválido: $command")
                return null
            }

            val expectedPID = commandClean.substring(2, 6)

            // Verificar que los PIDs coinciden
            if (responsePID != expectedPID) {
                Logger.obdError("PID de respuesta ($responsePID) no coincide con esperado ($expectedPID)")
                return null
            }

            // Extraer bytes de datos (después de modo + PID = 6 caracteres)
            val dataHex = cleanResponse.substring(6)

            if (dataHex.isEmpty()) {
                Logger.w("Respuesta Modo 22 sin datos: $cleanResponse")
                return byteArrayOf()
            }

            // Convertir hex a bytes
            val dataBytes = dataHex.chunked(2)
                .mapNotNull { hexByte ->
                    try {
                        hexByte.toInt(16).toByte()
                    } catch (e: NumberFormatException) {
                        null
                    }
                }
                .toByteArray()

            Logger.obd("Modo 22 parseado - PID: $responsePID, Bytes: ${dataBytes.size}")
            return dataBytes

        } catch (e: Exception) {
            Logger.obdError("Error al parsear respuesta Modo 22: $response", e)
            return null
        }
    }

    /**
     * Parsea una respuesta del Modo 22 usando un ManufacturerPID.
     *
     * @param manufacturerPID PID del fabricante con la fórmula
     * @param response Respuesta RAW del adaptador
     * @return Valor calculado o null si hay error
     */
    fun parseMode22WithPID(
        manufacturerPID: com.fleetcare.obd.domain.model.ManufacturerPID,
        response: String
    ): Double? {
        val dataBytes = parseMode22Response(manufacturerPID.buildCommand(), response)
            ?: return null

        return manufacturerPID.applyFormula(dataBytes)
    }

    /**
     * Extrae el PID de un comando Modo 22.
     *
     * "22 11 06" -> "1106"
     */
    fun extractMode22PID(command: String): String? {
        val cleanCommand = command.replace(" ", "").uppercase()

        if (!cleanCommand.startsWith(Mode22Constants.MODE_22_PREFIX)) {
            return null
        }

        if (cleanCommand.length < 6) {
            return null
        }

        return cleanCommand.substring(2, 6)
    }
}
