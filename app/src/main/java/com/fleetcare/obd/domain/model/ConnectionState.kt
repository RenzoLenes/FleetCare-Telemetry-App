package com.fleetcare.obd.domain.model

/**
 * Sealed class que representa el estado de conexión Bluetooth.
 *
 * Usar sealed class garantiza type-safety y permite manejar todos los estados
 * de forma exhaustiva con when expressions.
 */
sealed class ConnectionState {
    /**
     * Sin conexión establecida.
     */
    object Disconnected : ConnectionState()

    /**
     * Proceso de conexión en curso.
     * @property deviceName Nombre del dispositivo al que se está conectando
     */
    data class Connecting(val deviceName: String) : ConnectionState()

    /**
     * Conexión establecida exitosamente.
     * @property device Información del dispositivo conectado
     * @property isOBDInitialized Indica si el adaptador ELM327 está inicializado
     */
    data class Connected(
        val device: BluetoothDevice,
        val isOBDInitialized: Boolean = false
    ) : ConnectionState()

    /**
     * Error en la conexión.
     * @property message Mensaje de error
     * @property errorType Tipo de error
     */
    data class Error(
        val message: String,
        val errorType: ConnectionErrorType = ConnectionErrorType.UNKNOWN
    ) : ConnectionState()

    /**
     * Intentando reconectar automáticamente.
     * @property attempt Número de intento actual
     * @property maxAttempts Número máximo de intentos
     */
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int
    ) : ConnectionState()
}

/**
 * Tipos de errores de conexión.
 */
enum class ConnectionErrorType {
    /**
     * Bluetooth no está disponible en el dispositivo.
     */
    BLUETOOTH_NOT_AVAILABLE,

    /**
     * Bluetooth no está habilitado.
     */
    BLUETOOTH_NOT_ENABLED,

    /**
     * No se encontró el dispositivo.
     */
    DEVICE_NOT_FOUND,

    /**
     * Error al crear el socket RFCOMM.
     */
    SOCKET_CREATION_FAILED,

    /**
     * Error al conectar el socket.
     */
    CONNECTION_FAILED,

    /**
     * Tiempo de espera agotado.
     */
    TIMEOUT,

    /**
     * Pérdida de conexión inesperada.
     */
    CONNECTION_LOST,

    /**
     * Error al inicializar el adaptador OBDII.
     */
    OBD_INIT_FAILED,

    /**
     * Permisos no otorgados.
     */
    PERMISSION_DENIED,

    /**
     * Error desconocido.
     */
    UNKNOWN
}
