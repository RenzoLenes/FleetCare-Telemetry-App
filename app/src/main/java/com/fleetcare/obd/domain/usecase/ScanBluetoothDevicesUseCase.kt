package com.fleetcare.obd.domain.usecase

import com.fleetcare.obd.domain.model.BluetoothDevice
import com.fleetcare.obd.domain.repository.BluetoothRepository
import javax.inject.Inject

/**
 * Use Case para obtener dispositivos Bluetooth disponibles.
 *
 * Combina dispositivos emparejados y descubrimiento de nuevos dispositivos.
 * Los dispositivos emparejados se retornan primero para acceso rápido.
 */
class ScanBluetoothDevicesUseCase @Inject constructor(
    private val bluetoothRepository: BluetoothRepository
) {

    /**
     * Obtiene lista de dispositivos Bluetooth emparejados.
     *
     * @return Result con lista de dispositivos o error
     */
    suspend fun getPairedDevices(): Result<List<BluetoothDevice>> {
        // Verificar permisos
        if (!bluetoothRepository.hasRequiredPermissions()) {
            return Result.failure(
                SecurityException("Permisos de Bluetooth no otorgados")
            )
        }

        // Verificar Bluetooth habilitado
        if (!bluetoothRepository.isBluetoothEnabled()) {
            return Result.failure(
                IllegalStateException("Bluetooth no está habilitado")
            )
        }

        return bluetoothRepository.getPairedDevices()
    }

    /**
     * Cancela el escaneo de dispositivos en progreso.
     */
    fun cancelScan() {
        bluetoothRepository.cancelDiscovery()
    }
}
