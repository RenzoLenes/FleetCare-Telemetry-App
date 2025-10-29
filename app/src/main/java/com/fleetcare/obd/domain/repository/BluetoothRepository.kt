package com.fleetcare.obd.domain.repository

import com.fleetcare.obd.domain.model.BluetoothDevice
import com.fleetcare.obd.domain.model.ConnectionState
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz del Repository de Bluetooth.
 *
 * Define el contrato para todas las operaciones relacionadas con Bluetooth y OBDII.
 * La implementación concreta está en la capa de datos.
 */
interface BluetoothRepository {

    /**
     * Flow del estado de conexión actual.
     */
    val connectionState: Flow<ConnectionState>

    /**
     * Indica si el adaptador OBDII está inicializado.
     */
    val isOBDInitialized: Flow<Boolean>

    /**
     * Verifica si Bluetooth está disponible en el dispositivo.
     */
    fun isBluetoothAvailable(): Boolean

    /**
     * Verifica si Bluetooth está habilitado.
     */
    fun isBluetoothEnabled(): Boolean

    /**
     * Verifica si la app tiene todos los permisos necesarios.
     */
    fun hasRequiredPermissions(): Boolean

    /**
     * Obtiene lista de permisos faltantes.
     */
    fun getMissingPermissions(): List<String>

    /**
     * Obtiene dispositivos Bluetooth emparejados.
     */
    suspend fun getPairedDevices(): Result<List<BluetoothDevice>>

    /**
     * Inicia descubrimiento de dispositivos Bluetooth.
     */
    fun startDiscovery(): Flow<BluetoothDevice>

    /**
     * Cancela el descubrimiento de dispositivos.
     */
    fun cancelDiscovery()

    /**
     * Conecta a un dispositivo Bluetooth OBDII.
     */
    suspend fun connect(device: BluetoothDevice): Result<Unit>

    /**
     * Desconecta del dispositivo actual.
     */
    fun disconnect()

    /**
     * Envía un comando OBDII.
     */
    suspend fun sendOBDCommand(command: String): Result<String>

    /**
     * Inicia reconexión automática.
     */
    fun startAutoReconnection()

    /**
     * Detiene reconexión automática.
     */
    fun stopAutoReconnection()
}
