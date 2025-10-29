package com.fleetcare.obd.utils

/**
 * Archivo de constantes globales de la aplicación.
 *
 * Centraliza todos los valores constantes utilizados en múltiples partes del código
 * para facilitar el mantenimiento y evitar magic numbers/strings.
 */
object Constants {

    // Bluetooth Constants
    object Bluetooth {
        // UUID estándar para Serial Port Profile (SPP) usado por dispositivos OBDII
        const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

        // Timeout para operaciones de conexión Bluetooth en milisegundos
        const val CONNECTION_TIMEOUT_MS = 10000L

        // Intervalo de reconexión automática en milisegundos
        const val RECONNECTION_INTERVAL_MS = 5000L

        // PINs comunes para dispositivos OBDII
        val COMMON_PINS = listOf("1234", "0000", "6789")

        // Prefijos comunes de nombres de dispositivos OBDII
        val OBDII_DEVICE_PREFIXES = listOf("OBD", "ELM", "OBDII", "CHX", "V-LINK", "VGATE")
    }

    // OBD Protocol Constants
    object OBD {
        // Delay entre comandos OBDII en milisegundos para evitar saturar el adaptador
        const val COMMAND_DELAY_MS = 100L

        // Timeout para esperar respuesta del ECU en milisegundos
        const val RESPONSE_TIMEOUT_MS = 2000L

        // Intervalo de lectura de datos en tiempo real (2 segundos según requisitos)
        const val DATA_READ_INTERVAL_MS = 2000L

        // Caracteres terminadores de respuesta ELM327
        const val RESPONSE_TERMINATOR = ">"

        // Número máximo de reintentos para un comando fallido
        const val MAX_COMMAND_RETRIES = 3
    }

    // Firebase Constants
    object Firebase {
        // Nodos de la base de datos
        const val NODE_USERS = "users"
        const val NODE_VEHICLES = "vehicles"
        const val NODE_SESSIONS = "sessions"
        const val NODE_DATA = "data"
        const val NODE_DIAGNOSTICS = "diagnostics"

        // Intervalo de sincronización con Firebase (2 segundos)
        const val SYNC_INTERVAL_MS = 2000L

        // Número máximo de registros a mantener en caché offline
        const val MAX_OFFLINE_CACHE_SIZE = 500
    }

    // Database Constants
    object Database {
        const val DATABASE_NAME = "fleet_care_obd.db"
        const val DATABASE_VERSION = 1

        // Número de días para mantener datos en caché local
        const val DATA_RETENTION_DAYS = 7
    }

    // SharedPreferences Keys
    object Preferences {
        const val PREF_NAME = "fleet_care_prefs"

        const val KEY_LAST_CONNECTED_DEVICE = "last_connected_device"
        const val KEY_LAST_CONNECTED_ADDRESS = "last_connected_address"
        const val KEY_AUTO_RECONNECT = "auto_reconnect"
        const val KEY_USER_ID = "user_id"
        const val KEY_CURRENT_VEHICLE_ID = "current_vehicle_id"
        const val KEY_UNITS_SYSTEM = "units_system" // METRIC or IMPERIAL
        const val KEY_TEMPERATURE_UNIT = "temperature_unit" // CELSIUS or FAHRENHEIT
        const val KEY_DATA_READ_INTERVAL = "data_read_interval"
    }

    // UI Constants
    object UI {
        // Duración de animaciones en milisegundos
        const val ANIMATION_DURATION_SHORT = 150L
        const val ANIMATION_DURATION_NORMAL = 300L
        const val ANIMATION_DURATION_LONG = 500L

        // Delay para mensajes de Snackbar
        const val SNACKBAR_DURATION_SHORT = 2000
        const val SNACKBAR_DURATION_LONG = 3500
    }

    // Units
    object Units {
        const val METRIC = "METRIC"
        const val IMPERIAL = "IMPERIAL"

        const val CELSIUS = "CELSIUS"
        const val FAHRENHEIT = "FAHRENHEIT"

        const val KMH = "km/h"
        const val MPH = "mph"
    }

    // Error Messages
    object ErrorMessages {
        const val BLUETOOTH_NOT_AVAILABLE = "Bluetooth no está disponible en este dispositivo"
        const val BLUETOOTH_NOT_ENABLED = "Por favor, habilita Bluetooth"
        const val CONNECTION_FAILED = "No se pudo conectar al dispositivo OBDII"
        const val DEVICE_NOT_FOUND = "Dispositivo no encontrado"
        const val NO_PERMISSIONS = "Permisos necesarios no otorgados"
        const val OBD_INIT_FAILED = "Error al inicializar el adaptador OBDII"
        const val FIREBASE_CONNECTION_ERROR = "Error de conexión con Firebase"
        const val NETWORK_UNAVAILABLE = "Sin conexión a internet"
    }

    // Success Messages
    object SuccessMessages {
        const val CONNECTED = "Conectado exitosamente"
        const val DISCONNECTED = "Desconectado"
        const val DATA_SYNCED = "Datos sincronizados"
        const val DTC_CLEARED = "Códigos de error borrados"
    }
}
