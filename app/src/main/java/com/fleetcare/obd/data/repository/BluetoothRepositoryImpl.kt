package com.fleetcare.obd.data.repository

import com.fleetcare.obd.bluetooth.BluetoothManager
import com.fleetcare.obd.bluetooth.BluetoothService
import com.fleetcare.obd.domain.model.BluetoothDevice
import com.fleetcare.obd.domain.model.ConnectionState
import com.fleetcare.obd.domain.repository.BluetoothRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación del BluetoothRepository.
 *
 * Delega operaciones a BluetoothManager y BluetoothService,
 * actuando como capa de abstracción entre dominio y fuentes de datos.
 */
@Singleton
class BluetoothRepositoryImpl @Inject constructor(
    private val bluetoothManager: BluetoothManager,
    private val bluetoothService: BluetoothService
) : BluetoothRepository {

    override val connectionState: Flow<ConnectionState>
        get() = bluetoothService.connectionState

    override val isOBDInitialized: Flow<Boolean>
        get() = bluetoothService.isOBDInitialized

    override fun isBluetoothAvailable(): Boolean {
        return bluetoothManager.isBluetoothAvailable
    }

    override fun isBluetoothEnabled(): Boolean {
        return bluetoothManager.isBluetoothEnabled
    }

    override fun hasRequiredPermissions(): Boolean {
        return bluetoothManager.hasRequiredPermissions()
    }

    override fun getMissingPermissions(): List<String> {
        return bluetoothManager.getMissingPermissions()
    }

    override suspend fun getPairedDevices(): Result<List<BluetoothDevice>> {
        return try {
            val devices = bluetoothManager.getPairedDevices()
            if (devices == null) {
                Result.failure(Exception("No se pudieron obtener dispositivos emparejados"))
            } else {
                Result.success(devices)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun startDiscovery(): Flow<BluetoothDevice> {
        return bluetoothManager.startDiscovery()
    }

    override fun cancelDiscovery() {
        bluetoothManager.cancelDiscovery()
    }

    override suspend fun connect(device: BluetoothDevice): Result<Unit> {
        return try {
            bluetoothService.connect(device)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun disconnect() {
        bluetoothService.disconnect()
    }

    override suspend fun sendOBDCommand(command: String): Result<String> {
        return bluetoothService.sendOBDCommand(command)
    }

    override fun startAutoReconnection() {
        bluetoothService.startAutoReconnection()
    }

    override fun stopAutoReconnection() {
        bluetoothService.stopAutoReconnection()
    }
}
