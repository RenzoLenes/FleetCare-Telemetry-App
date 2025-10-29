package com.fleetcare.obd.utils.obd

/**
 * Constantes de PIDs (Parameter IDs) del protocolo OBDII.
 *
 * Cada PID representa un parámetro específico del vehículo que se puede leer.
 * Este archivo define todos los PIDs soportados con sus fórmulas de conversión.
 */
object PIDConstants {

    /**
     * Definición de un PID OBDII.
     *
     * @property command Comando completo a enviar (ej: "010C")
     * @property name Nombre descriptivo del parámetro
     * @property unit Unidad de medida
     * @property formula Función para convertir bytes de respuesta a valor real
     * @property minValue Valor mínimo esperado
     * @property maxValue Valor máximo esperado
     */
    data class PID(
        val command: String,
        val name: String,
        val unit: String,
        val formula: (ByteArray) -> Double,
        val minValue: Double = 0.0,
        val maxValue: Double = Double.MAX_VALUE
    )

    /**
     * RPM del motor.
     * Respuesta: 2 bytes (A, B)
     * Fórmula: ((A * 256) + B) / 4
     * Rango: 0 - 16,383.75 RPM
     */
    val ENGINE_RPM = PID(
        command = "010C",
        name = "RPM del Motor",
        unit = "RPM",
        formula = { bytes ->
            if (bytes.size >= 2) {
                val a = bytes[0].toInt() and 0xFF
                val b = bytes[1].toInt() and 0xFF
                ((a * 256.0) + b) / 4.0
            } else 0.0
        },
        minValue = 0.0,
        maxValue = 16383.75
    )

    /**
     * Velocidad del vehículo.
     * Respuesta: 1 byte (A)
     * Fórmula: A
     * Rango: 0 - 255 km/h
     */
    val VEHICLE_SPEED = PID(
        command = "010D",
        name = "Velocidad",
        unit = "km/h",
        formula = { bytes ->
            if (bytes.isNotEmpty()) {
                (bytes[0].toInt() and 0xFF).toDouble()
            } else 0.0
        },
        minValue = 0.0,
        maxValue = 255.0
    )

    /**
     * Temperatura del refrigerante del motor.
     * Respuesta: 1 byte (A)
     * Fórmula: A - 40
     * Rango: -40 - 215 °C
     */
    val COOLANT_TEMP = PID(
        command = "0105",
        name = "Temperatura Refrigerante",
        unit = "°C",
        formula = { bytes ->
            if (bytes.isNotEmpty()) {
                (bytes[0].toInt() and 0xFF) - 40.0
            } else 0.0
        },
        minValue = -40.0,
        maxValue = 215.0
    )

    /**
     * Temperatura del aire de admisión.
     * Respuesta: 1 byte (A)
     * Fórmula: A - 40
     * Rango: -40 - 215 °C
     */
    val INTAKE_AIR_TEMP = PID(
        command = "010F",
        name = "Temperatura Aire Admisión",
        unit = "°C",
        formula = { bytes ->
            if (bytes.isNotEmpty()) {
                (bytes[0].toInt() and 0xFF) - 40.0
            } else 0.0
        },
        minValue = -40.0,
        maxValue = 215.0
    )

    /**
     * Posición del acelerador.
     * Respuesta: 1 byte (A)
     * Fórmula: (A * 100) / 255
     * Rango: 0 - 100 %
     */
    val THROTTLE_POSITION = PID(
        command = "0111",
        name = "Posición Acelerador",
        unit = "%",
        formula = { bytes ->
            if (bytes.isNotEmpty()) {
                ((bytes[0].toInt() and 0xFF) * 100.0) / 255.0
            } else 0.0
        },
        minValue = 0.0,
        maxValue = 100.0
    )

    /**
     * Carga calculada del motor.
     * Respuesta: 1 byte (A)
     * Fórmula: (A * 100) / 255
     * Rango: 0 - 100 %
     */
    val ENGINE_LOAD = PID(
        command = "0104",
        name = "Carga del Motor",
        unit = "%",
        formula = { bytes ->
            if (bytes.isNotEmpty()) {
                ((bytes[0].toInt() and 0xFF) * 100.0) / 255.0
            } else 0.0
        },
        minValue = 0.0,
        maxValue = 100.0
    )

    /**
     * Voltaje del módulo de control.
     * Respuesta: 2 bytes (A, B)
     * Fórmula: ((A * 256) + B) / 1000
     * Rango: 0 - 65.535 V
     */
    val CONTROL_MODULE_VOLTAGE = PID(
        command = "0142",
        name = "Voltaje del Sistema",
        unit = "V",
        formula = { bytes ->
            if (bytes.size >= 2) {
                val a = bytes[0].toInt() and 0xFF
                val b = bytes[1].toInt() and 0xFF
                ((a * 256.0) + b) / 1000.0
            } else 0.0
        },
        minValue = 0.0,
        maxValue = 65.535
    )

    /**
     * Nivel de combustible del tanque.
     * Respuesta: 1 byte (A)
     * Fórmula: (A * 100) / 255
     * Rango: 0 - 100 %
     */
    val FUEL_LEVEL = PID(
        command = "012F",
        name = "Nivel de Combustible",
        unit = "%",
        formula = { bytes ->
            if (bytes.isNotEmpty()) {
                ((bytes[0].toInt() and 0xFF) * 100.0) / 255.0
            } else 0.0
        },
        minValue = 0.0,
        maxValue = 100.0
    )

    /**
     * Temperatura del aceite del motor.
     * Respuesta: 1 byte (A)
     * Fórmula: A - 40
     * Rango: -40 - 215 °C
     */
    val ENGINE_OIL_TEMP = PID(
        command = "015C",
        name = "Temperatura Aceite",
        unit = "°C",
        formula = { bytes ->
            if (bytes.isNotEmpty()) {
                (bytes[0].toInt() and 0xFF) - 40.0
            } else 0.0
        },
        minValue = -40.0,
        maxValue = 215.0
    )

    /**
     * Temperatura ambiente.
     * Respuesta: 1 byte (A)
     * Fórmula: A - 40
     * Rango: -40 - 215 °C
     */
    val AMBIENT_AIR_TEMP = PID(
        command = "0146",
        name = "Temperatura Ambiente",
        unit = "°C",
        formula = { bytes ->
            if (bytes.isNotEmpty()) {
                (bytes[0].toInt() and 0xFF) - 40.0
            } else 0.0
        },
        minValue = -40.0,
        maxValue = 215.0
    )

    /**
     * Lista de PIDs básicos para monitoreo en tiempo real.
     * Estos son los 10 parámetros principales que se mostrarán en el dashboard.
     */
    val BASIC_PIDS = listOf(
        ENGINE_RPM,
        VEHICLE_SPEED,
        COOLANT_TEMP,
        INTAKE_AIR_TEMP,
        THROTTLE_POSITION,
        ENGINE_LOAD,
        CONTROL_MODULE_VOLTAGE,
        FUEL_LEVEL,
        ENGINE_OIL_TEMP,
        AMBIENT_AIR_TEMP
    )

    /**
     * Mapa de comandos a PIDs para búsqueda rápida.
     */
    val COMMAND_TO_PID_MAP = BASIC_PIDS.associateBy { it.command }

    /**
     * Obtiene un PID por su comando.
     */
    fun getPIDByCommand(command: String): PID? {
        return COMMAND_TO_PID_MAP[command]
    }
}
