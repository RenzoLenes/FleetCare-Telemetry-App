package com.fleetcare.obd.utils.obd

import com.fleetcare.obd.domain.model.DiagnosticTroubleCode

/**
 * Manager para Diagnostic Trouble Codes (DTCs).
 *
 * Maneja la lectura, parsing y descripción de códigos de error del vehículo.
 *
 * Formato de DTCs:
 * - P: Powertrain (motor y transmisión)
 * - C: Chassis (frenos, suspensión, dirección)
 * - B: Body (carrocería, airbags, clima)
 * - U: Network (comunicaciones)
 *
 * Ejemplo: P0301 = Fallo de encendido en cilindro 1
 */
object DTCManager {

    /**
     * Parsea la respuesta del comando Mode 03 (leer DTCs).
     *
     * Formato de respuesta:
     * "43 02 01 43 01 96"
     * - 43: Mode 03 response
     * - 02: Número de códigos (2 códigos)
     * - 01 43: Código 1 (P0143)
     * - 01 96: Código 2 (P0196)
     *
     * @param response Respuesta hexadecimal del ECU
     * @return Lista de DTCs parseados
     */
    fun parseDTCs(response: String): List<DiagnosticTroubleCode> {
        try {
            val cleanResponse = OBDCommandParser.cleanResponse(response)

            // Verificar si hay errores
            if (OBDCommandParser.isErrorResponse(cleanResponse)) {
                return emptyList()
            }

            // Verificar formato válido (debe empezar con "43")
            if (!cleanResponse.startsWith("43")) {
                return emptyList()
            }

            // Remover el modo (43) y obtener el número de códigos
            val dataHex = cleanResponse.substring(2)

            if (dataHex.length < 2) {
                return emptyList()
            }

            val numCodes = dataHex.substring(0, 2).toIntOrNull(16) ?: 0

            if (numCodes == 0) {
                return emptyList()
            }

            // Parsear cada DTC (4 caracteres hex = 2 bytes)
            val codes = mutableListOf<DiagnosticTroubleCode>()
            var offset = 2 // Saltar el byte de número de códigos

            for (i in 0 until numCodes) {
                if (offset + 4 <= dataHex.length) {
                    val codeHex = dataHex.substring(offset, offset + 4)
                    val dtc = hexToDTC(codeHex)
                    if (dtc != null) {
                        codes.add(dtc)
                    }
                    offset += 4
                }
            }

            return codes

        } catch (e: Exception) {
            return emptyList()
        }
    }

    /**
     * Convierte código hexadecimal a formato DTC estándar.
     *
     * Formato:
     * - Primer byte: primeros 2 bits = tipo de código, siguientes 6 bits = primer dígito
     * - Segundo byte: 2 dígitos hexadecimales
     *
     * Ejemplo: 0143 hex
     * - 01: 00 (P) + 01 (primer dígito)
     * - 43: 43 hex = 67 decimal → "143"
     * - Resultado: P0143
     */
    private fun hexToDTC(hex: String): DiagnosticTroubleCode? {
        try {
            val firstByte = hex.substring(0, 2).toInt(16)
            val secondByte = hex.substring(2, 4).toInt(16)

            // Primeros 2 bits determinan el tipo
            val typeCode = (firstByte and 0xC0) shr 6
            val type = when (typeCode) {
                0 -> 'P'
                1 -> 'C'
                2 -> 'B'
                3 -> 'U'
                else -> 'P'
            }

            // Siguientes 6 bits del primer byte = primer dígito
            val firstDigit = firstByte and 0x3F

            // Segundo byte en formato hexadecimal
            val remainingDigits = secondByte.toString(16).padStart(2, '0')

            val code = "$type$firstDigit$remainingDigits".uppercase()
            val description = getDescription(code)

            return DiagnosticTroubleCode(
                code = code,
                description = description,
                isPending = false
            )

        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Parsea DTCs pendientes (Mode 07).
     *
     * Similar a Mode 03 pero para códigos pendientes que aún no han
     * establecido una falla permanente.
     */
    fun parsePendingDTCs(response: String): List<DiagnosticTroubleCode> {
        try {
            val cleanResponse = OBDCommandParser.cleanResponse(response)

            // Verificar formato válido (debe empezar con "47")
            if (!cleanResponse.startsWith("47")) {
                return emptyList()
            }

            // Reemplazar "47" con "43" para usar el mismo parser
            val modifiedResponse = "43" + cleanResponse.substring(2)
            val dtcs = parseDTCs(modifiedResponse)

            // Marcar como pendientes
            return dtcs.map { it.copy(isPending = true) }

        } catch (e: Exception) {
            return emptyList()
        }
    }

    /**
     * Obtiene descripción de un código DTC.
     *
     * Base de datos de códigos más comunes.
     */
    fun getDescription(code: String): String {
        return dtcDescriptions[code] ?: getGenericDescription(code)
    }

    /**
     * Genera descripción genérica basada en el código.
     */
    private fun getGenericDescription(code: String): String {
        if (code.length < 2) return "Código desconocido"

        val type = code[0]
        val system = code[1]

        val typeDesc = when (type) {
            'P' -> "Tren motriz"
            'C' -> "Chasis"
            'B' -> "Carrocería"
            'U' -> "Red de comunicación"
            else -> "Desconocido"
        }

        val systemDesc = when (system) {
            '0' -> "SAE (genérico)"
            '1' -> "Fabricante (específico)"
            '2' -> "SAE (genérico)"
            '3' -> "Fabricante (específico)"
            else -> ""
        }

        return "$typeDesc - $systemDesc - Código: $code"
    }

    /**
     * Base de datos de códigos DTC comunes con sus descripciones.
     */
    private val dtcDescriptions = mapOf(
        // P0xxx - Códigos genéricos del tren motriz
        "P0030" to "Sensor de oxígeno calentado (HO2S) 1, banco 1 - circuito de control",
        "P0100" to "Sensor de flujo de aire masivo (MAF) - mal funcionamiento del circuito",
        "P0101" to "Sensor MAF - rango/rendimiento del circuito",
        "P0102" to "Sensor MAF - entrada baja del circuito",
        "P0103" to "Sensor MAF - entrada alta del circuito",
        "P0104" to "Sensor MAF - circuito intermitente",
        "P0105" to "Sensor de presión absoluta del colector (MAP) - mal funcionamiento",
        "P0106" to "Sensor MAP - rango/rendimiento del circuito",
        "P0107" to "Sensor MAP - entrada baja del circuito",
        "P0108" to "Sensor MAP - entrada alta del circuito",
        "P0110" to "Sensor de temperatura del aire de admisión - mal funcionamiento",
        "P0111" to "Sensor IAT - rango/rendimiento del circuito",
        "P0112" to "Sensor IAT - entrada baja del circuito",
        "P0113" to "Sensor IAT - entrada alta del circuito",
        "P0115" to "Sensor de temperatura del refrigerante del motor (ECT) - mal funcionamiento",
        "P0116" to "Sensor ECT - rango/rendimiento del circuito",
        "P0117" to "Sensor ECT - entrada baja del circuito",
        "P0118" to "Sensor ECT - entrada alta del circuito",
        "P0120" to "Sensor de posición del acelerador/pedal (TPS) - mal funcionamiento",
        "P0121" to "Sensor TPS - rango/rendimiento del circuito",
        "P0122" to "Sensor TPS - entrada baja del circuito",
        "P0123" to "Sensor TPS - entrada alta del circuito",
        "P0130" to "Sensor de oxígeno O2S 1, banco 1 - mal funcionamiento del circuito",
        "P0131" to "Sensor O2S 1, banco 1 - voltaje bajo",
        "P0132" to "Sensor O2S 1, banco 1 - voltaje alto",
        "P0133" to "Sensor O2S 1, banco 1 - respuesta lenta",
        "P0134" to "Sensor O2S 1, banco 1 - sin actividad",
        "P0135" to "Sensor O2S 1, banco 1 - mal funcionamiento del calentador",
        "P0140" to "Sensor O2S 2, banco 1 - mal funcionamiento del circuito",
        "P0141" to "Sensor O2S 2, banco 1 - mal funcionamiento del calentador",
        "P0171" to "Sistema demasiado pobre - banco 1",
        "P0172" to "Sistema demasiado rico - banco 1",
        "P0174" to "Sistema demasiado pobre - banco 2",
        "P0175" to "Sistema demasiado rico - banco 2",
        "P0201" to "Inyector del cilindro 1 - mal funcionamiento del circuito",
        "P0202" to "Inyector del cilindro 2 - mal funcionamiento del circuito",
        "P0203" to "Inyector del cilindro 3 - mal funcionamiento del circuito",
        "P0204" to "Inyector del cilindro 4 - mal funcionamiento del circuito",
        "P0205" to "Inyector del cilindro 5 - mal funcionamiento del circuito",
        "P0206" to "Inyector del cilindro 6 - mal funcionamiento del circuito",
        "P0300" to "Detección de fallos de encendido aleatorios/múltiples cilindros",
        "P0301" to "Fallo de encendido detectado - cilindro 1",
        "P0302" to "Fallo de encendido detectado - cilindro 2",
        "P0303" to "Fallo de encendido detectado - cilindro 3",
        "P0304" to "Fallo de encendido detectado - cilindro 4",
        "P0305" to "Fallo de encendido detectado - cilindro 5",
        "P0306" to "Fallo de encendido detectado - cilindro 6",
        "P0325" to "Sensor de detonación 1, banco 1 - mal funcionamiento del circuito",
        "P0335" to "Sensor de posición del cigüeñal - mal funcionamiento del circuito",
        "P0340" to "Sensor de posición del árbol de levas - mal funcionamiento del circuito",
        "P0401" to "Sistema EGR - flujo insuficiente detectado",
        "P0402" to "Sistema EGR - flujo excesivo detectado",
        "P0420" to "Catalizador sistema de eficiencia por debajo del umbral - banco 1",
        "P0430" to "Catalizador sistema de eficiencia por debajo del umbral - banco 2",
        "P0440" to "Sistema de control de emisiones evaporativas - mal funcionamiento",
        "P0441" to "Sistema EVAP - flujo de purga incorrecto",
        "P0442" to "Sistema EVAP - fuga pequeña detectada",
        "P0443" to "Sistema EVAP - mal funcionamiento del circuito de válvula de purga",
        "P0455" to "Sistema EVAP - fuga grande detectada",
        "P0500" to "Sensor de velocidad del vehículo - mal funcionamiento",
        "P0501" to "Sensor de velocidad del vehículo - rango/rendimiento",
        "P0505" to "Sistema de control de velocidad de ralentí - mal funcionamiento",
        "P0506" to "Sistema de control de velocidad de ralentí - RPM más bajas de lo esperado",
        "P0507" to "Sistema de control de velocidad de ralentí - RPM más altas de lo esperado",
        "P0510" to "Interruptor de posición del acelerador cerrado - mal funcionamiento",
        "P0600" to "Enlace de comunicación del bus serie - mal funcionamiento",
        "P0601" to "Módulo de control interno - error de memoria",
        "P0602" to "Módulo de control - error de programación",
        "P0603" to "Módulo de control interno - error de memoria KAM",
        "P0604" to "Módulo de control interno - error de memoria RAM",
        "P0605" to "Módulo de control interno - error de memoria ROM",
        "P0700" to "Sistema de control de la transmisión - mal funcionamiento",
        "P0701" to "Sistema de control de la transmisión - rango/rendimiento",
        "P0705" to "Sensor de posición de transmisión/rango - mal funcionamiento del circuito",
        "P0710" to "Sensor de temperatura del fluido de la transmisión - mal funcionamiento",
        "P0715" to "Sensor de velocidad del eje de entrada/turbina - mal funcionamiento",
        "P0720" to "Sensor de velocidad del eje de salida - mal funcionamiento",
        "P0725" to "Sensor de velocidad del motor - mal funcionamiento del circuito",
        "P0730" to "Relación de transmisión incorrecta",
        "P0740" to "Embrague del convertidor de par - mal funcionamiento del circuito",
        "P0750" to "Solenoide de cambio A - mal funcionamiento",
        "P0755" to "Solenoide de cambio B - mal funcionamiento",
        "P0760" to "Solenoide de cambio C - mal funcionamiento",

        // C0xxx - Códigos del chasis
        "C0030" to "Sistema ABS - sensor de velocidad de rueda frontal izquierda - mal funcionamiento",
        "C0035" to "Sistema ABS - sensor de velocidad de rueda frontal derecha - mal funcionamiento",
        "C0040" to "Sistema ABS - sensor de velocidad de rueda trasera izquierda - mal funcionamiento",
        "C0045" to "Sistema ABS - sensor de velocidad de rueda trasera derecha - mal funcionamiento",
        "C0060" to "Sistema ABS - válvula de solenoide - mal funcionamiento",

        // B0xxx - Códigos de carrocería
        "B0001" to "Circuito del airbag del conductor - mal funcionamiento",
        "B0002" to "Circuito del airbag del pasajero - mal funcionamiento",
        "B1000" to "Sistema de inmovilizador del motor - mal funcionamiento",

        // U0xxx - Códigos de red
        "U0100" to "Comunicación perdida con ECM/PCM",
        "U0101" to "Comunicación perdida con TCM",
        "U0121" to "Comunicación perdida con módulo de control ABS",
        "U0140" to "Comunicación perdida con módulo de control de carrocería",
        "U0151" to "Comunicación perdida con módulo de control de airbag"
    )
}
