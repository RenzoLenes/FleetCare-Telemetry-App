package com.fleetcare.obd.utils

import timber.log.Timber

/**
 * Wrapper de Timber para proporcionar una interfaz de logging consistente.
 *
 * Este logger facilita el cambio de implementación de logging sin modificar
 * todo el código que lo utiliza. También permite agregar lógica personalizada
 * como formateo de mensajes, filtrado, o envío a servicios de analytics.
 */
object Logger {

    /**
     * Log de nivel DEBUG para información de depuración detallada.
     * Solo se muestra en builds de DEBUG.
     */
    fun d(message: String, vararg args: Any?) {
        Timber.d(message, *args)
    }

    /**
     * Log de nivel DEBUG con excepción.
     */
    fun d(throwable: Throwable, message: String, vararg args: Any?) {
        Timber.d(throwable, message, *args)
    }

    /**
     * Log de nivel INFO para mensajes informativos.
     */
    fun i(message: String, vararg args: Any?) {
        Timber.i(message, *args)
    }

    /**
     * Log de nivel INFO con excepción.
     */
    fun i(throwable: Throwable, message: String, vararg args: Any?) {
        Timber.i(throwable, message, *args)
    }

    /**
     * Log de nivel WARNING para situaciones potencialmente problemáticas.
     */
    fun w(message: String, vararg args: Any?) {
        Timber.w(message, *args)
    }

    /**
     * Log de nivel WARNING con excepción.
     */
    fun w(throwable: Throwable, message: String, vararg args: Any?) {
        Timber.w(throwable, message, *args)
    }

    /**
     * Log de nivel ERROR para errores graves.
     * Estos logs deberían ser monitoreados en producción.
     */
    fun e(message: String, vararg args: Any?) {
        Timber.e(message, *args)
    }

    /**
     * Log de nivel ERROR con excepción.
     */
    fun e(throwable: Throwable, message: String, vararg args: Any?) {
        Timber.e(throwable, message, *args)
    }

    /**
     * Log de nivel VERBOSE para información muy detallada.
     * Solo para debugging extensivo.
     */
    fun v(message: String, vararg args: Any?) {
        Timber.v(message, *args)
    }

    /**
     * Logs especializados para diferentes componentes del sistema.
     */

    // Bluetooth Logging
    fun bluetooth(message: String) {
        d("[BLUETOOTH] $message")
    }

    fun bluetoothError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            e(throwable, "[BLUETOOTH ERROR] $message")
        } else {
            e("[BLUETOOTH ERROR] $message")
        }
    }

    // OBD Protocol Logging
    fun obd(message: String) {
        d("[OBD] $message")
    }

    fun obdCommand(command: String, response: String? = null) {
        if (response != null) {
            d("[OBD CMD] $command -> $response")
        } else {
            d("[OBD CMD] $command")
        }
    }

    fun obdError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            e(throwable, "[OBD ERROR] $message")
        } else {
            e("[OBD ERROR] $message")
        }
    }

    // Firebase Logging
    fun firebase(message: String) {
        d("[FIREBASE] $message")
    }

    fun firebaseError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            e(throwable, "[FIREBASE ERROR] $message")
        } else {
            e("[FIREBASE ERROR] $message")
        }
    }

    // Network/Sync Logging
    fun sync(message: String) {
        d("[SYNC] $message")
    }

    fun syncError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            e(throwable, "[SYNC ERROR] $message")
        } else {
            e("[SYNC ERROR] $message")
        }
    }
}
