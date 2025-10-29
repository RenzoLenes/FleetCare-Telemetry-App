package com.fleetcare.obd.domain.usecase

import com.fleetcare.obd.domain.repository.BluetoothRepository
import javax.inject.Inject

/**
 * Use Case para desconectar del dispositivo Bluetooth actual.
 */
class DisconnectDeviceUseCase @Inject constructor(
    private val bluetoothRepository: BluetoothRepository
) {

    /**
     * Desconecta del dispositivo Bluetooth actual.
     */
    operator fun invoke() {
        bluetoothRepository.disconnect()
    }
}
