package com.fleetcare.obd.utils.obd

/**
 * Comandos AT del protocolo ELM327.
 *
 * El ELM327 es un chip de interfaz OBDII muy común que usa comandos AT
 * (similares a los módems antiguos) para configurarse y comunicarse con el ECU.
 *
 * Comandos AT: Configuración del adaptador
 * Comandos OBD: Lectura de datos del vehículo
 */
object ELM327Commands {

    /**
     * Comandos AT de inicialización y configuración del adaptador.
     */
    object Initialization {
        /**
         * Reset del dispositivo ELM327.
         * Restaura configuración por defecto.
         */
        const val RESET = "ATZ"

        /**
         * Desactiva eco de comandos.
         * Hace que el adaptador no repita los comandos enviados.
         */
        const val ECHO_OFF = "ATE0"

        /**
         * Activa eco de comandos (útil para debugging).
         */
        const val ECHO_ON = "ATE1"

        /**
         * Desactiva linefeeds en las respuestas.
         */
        const val LINEFEED_OFF = "ATL0"

        /**
         * Desactiva espacios en las respuestas hexadecimales.
         * Ejemplo: "41 0C" se convierte en "410C"
         */
        const val SPACES_OFF = "ATS0"

        /**
         * Activa espacios (útil para legibilidad).
         */
        const val SPACES_ON = "ATS1"

        /**
         * Desactiva headers en las respuestas.
         */
        const val HEADERS_OFF = "ATH0"

        /**
         * Activa headers (muestra información adicional del CAN).
         */
        const val HEADERS_ON = "ATH1"

        /**
         * Auto-detecta el protocolo del vehículo.
         * El ELM327 probará todos los protocolos hasta encontrar el correcto.
         */
        const val AUTO_PROTOCOL = "ATSP0"

        /**
         * Describe el protocolo actual.
         */
        const val DESCRIBE_PROTOCOL = "ATDP"

        /**
         * Obtiene información del dispositivo (versión del ELM327).
         */
        const val GET_DEVICE_INFO = "ATI"

        /**
         * Obtiene el voltaje de la batería del vehículo.
         */
        const val GET_VOLTAGE = "ATRV"

        /**
         * Permite respuestas largas (necesario para algunos comandos).
         */
        const val ALLOW_LONG_MESSAGES = "ATAL"

        /**
         * Configura timeout para respuestas (en múltiplos de 4ms).
         * Ejemplo: ATST32 = 32 * 4ms = 128ms timeout
         */
        fun setTimeout(value: Int) = "ATST$value"
    }

    /**
     * Comandos OBD Mode 01: Lectura de datos en tiempo real.
     *
     * Formato: 01 XX donde XX es el PID (Parameter ID)
     */
    object Mode01 {
        /**
         * PIDs soportados (01-20).
         * Respuesta indica qué PIDs están disponibles.
         */
        const val SUPPORTED_PIDS_01_20 = "0100"

        /**
         * PIDs soportados (21-40).
         */
        const val SUPPORTED_PIDS_21_40 = "0120"

        /**
         * Monitor status desde DTCs borrados.
         */
        const val MONITOR_STATUS = "0101"

        /**
         * Códigos DTC que causaron freeze frame.
         */
        const val FREEZE_DTC = "0102"

        /**
         * Estado del sistema de combustible.
         */
        const val FUEL_SYSTEM_STATUS = "0103"

        /**
         * Carga calculada del motor (0-100%).
         * Indica qué tan duro está trabajando el motor.
         */
        const val ENGINE_LOAD = "0104"

        /**
         * Temperatura del refrigerante del motor (°C).
         * Rango: -40°C a 215°C
         */
        const val COOLANT_TEMP = "0105"

        /**
         * Short term fuel trim - Banco 1 (%).
         */
        const val SHORT_FUEL_TRIM_1 = "0106"

        /**
         * Long term fuel trim - Banco 1 (%).
         */
        const val LONG_FUEL_TRIM_1 = "0107"

        /**
         * Presión de combustible (kPa).
         */
        const val FUEL_PRESSURE = "010A"

        /**
         * Presión absoluta del múltiple de admisión (kPa).
         */
        const val INTAKE_MANIFOLD_PRESSURE = "010B"

        /**
         * RPM del motor.
         * Rango: 0 - 16,383.75 RPM
         */
        const val ENGINE_RPM = "010C"

        /**
         * Velocidad del vehículo (km/h).
         * Rango: 0 - 255 km/h
         */
        const val VEHICLE_SPEED = "010D"

        /**
         * Avance de tiempo de encendido (grados antes del PMS).
         */
        const val TIMING_ADVANCE = "010E"

        /**
         * Temperatura del aire de admisión (°C).
         * Rango: -40°C a 215°C
         */
        const val INTAKE_AIR_TEMP = "010F"

        /**
         * Flujo de aire MAF (Mass Air Flow) en g/s.
         */
        const val MAF_AIR_FLOW = "0110"

        /**
         * Posición del acelerador (0-100%).
         */
        const val THROTTLE_POSITION = "0111"

        /**
         * Estado del sistema de aire secundario.
         */
        const val SECONDARY_AIR_STATUS = "0112"

        /**
         * Ubicación de sensores de oxígeno.
         */
        const val OXYGEN_SENSORS_PRESENT = "0113"

        /**
         * Sensor de oxígeno 1 - Voltaje y short term fuel trim.
         */
        const val OXYGEN_SENSOR_1 = "0114"

        /**
         * Estándares OBD a los que se diseñó el vehículo.
         */
        const val OBD_STANDARDS = "011C"

        /**
         * Tiempo de ejecución del motor desde que se encendió (segundos).
         */
        const val ENGINE_RUNTIME = "011F"

        /**
         * Distancia recorrida con MIL encendida (km).
         */
        const val DISTANCE_WITH_MIL = "0121"

        /**
         * Presión de combustible relativa al múltiple de vacío (kPa).
         */
        const val FUEL_RAIL_PRESSURE = "0122"

        /**
         * Voltaje del módulo de control (V).
         */
        const val CONTROL_MODULE_VOLTAGE = "0142"

        /**
         * Carga absoluta del motor (%).
         */
        const val ABSOLUTE_ENGINE_LOAD = "0143"

        /**
         * Relación aire-combustible.
         */
        const val AIR_FUEL_RATIO = "0144"

        /**
         * Posición relativa del acelerador (%).
         */
        const val RELATIVE_THROTTLE_POSITION = "0145"

        /**
         * Temperatura del aire ambiente (°C).
         */
        const val AMBIENT_AIR_TEMP = "0146"

        /**
         * Posición absoluta del acelerador B (%).
         */
        const val ABSOLUTE_THROTTLE_POSITION_B = "0147"

        /**
         * Posición del pedal del acelerador D (%).
         */
        const val ACCELERATOR_PEDAL_POSITION_D = "0149"

        /**
         * Nivel de combustible del tanque (0-100%).
         */
        const val FUEL_TANK_LEVEL = "012F"

        /**
         * Temperatura del aceite del motor (°C).
         */
        const val ENGINE_OIL_TEMP = "015C"
    }

    /**
     * Comandos OBD Mode 03: Leer códigos de diagnóstico (DTCs).
     *
     * Muestra los códigos de error almacenados que causaron que se encienda
     * la luz de "Check Engine" (MIL - Malfunction Indicator Lamp).
     */
    object Mode03 {
        /**
         * Lee todos los códigos DTC almacenados.
         * Respuesta incluye los códigos en formato hexadecimal.
         */
        const val GET_DTCS = "03"
    }

    /**
     * Comandos OBD Mode 04: Borrar códigos DTC y valores almacenados.
     *
     * CUIDADO: Esto borrará:
     * - Códigos de error
         * - Freeze frame data
     * - Datos de pruebas de sensores de oxígeno
     * - Estado de pruebas del sistema
     * - Apagará la luz MIL (Check Engine)
     */
    object Mode04 {
        /**
         * Borra todos los códigos DTC y la luz MIL.
         */
        const val CLEAR_DTCS = "04"
    }

    /**
     * Comandos OBD Mode 09: Información del vehículo.
     */
    object Mode09 {
        /**
         * VIN (Vehicle Identification Number).
         * Número de identificación del vehículo.
         */
        const val GET_VIN = "0902"

        /**
         * Calibration ID.
         */
        const val GET_CALIBRATION_ID = "0904"

        /**
         * Calibration Verification Numbers.
         */
        const val GET_CVN = "0906"
    }

    /**
     * Secuencia de inicialización recomendada para el ELM327.
     *
     * Esta secuencia configura el adaptador en modo óptimo para OBDII.
     */
    val INITIALIZATION_SEQUENCE = listOf(
        Initialization.RESET,                // Reset del dispositivo
        Initialization.ECHO_OFF,             // Desactivar eco
        Initialization.LINEFEED_OFF,         // Desactivar linefeeds
        Initialization.SPACES_OFF,           // Desactivar espacios (respuestas más compactas)
        Initialization.HEADERS_OFF,          // Desactivar headers
        Initialization.AUTO_PROTOCOL,        // Auto-detectar protocolo
        Initialization.ALLOW_LONG_MESSAGES   // Permitir mensajes largos
    )

    /**
     * Comandos de verificación para probar la conexión.
     */
    val VERIFICATION_COMMANDS = listOf(
        Initialization.GET_DEVICE_INFO,      // Obtener versión ELM327
        Initialization.GET_VOLTAGE,          // Obtener voltaje del vehículo
        Initialization.DESCRIBE_PROTOCOL     // Verificar protocolo detectado
    )

    // Accesos directos a comandos comunes
    const val MODE_03_GET_DTCS = "03"
    const val MODE_04_CLEAR_DTCS = "04"
    const val MODE_07_GET_PENDING_DTCS = "07"
}
