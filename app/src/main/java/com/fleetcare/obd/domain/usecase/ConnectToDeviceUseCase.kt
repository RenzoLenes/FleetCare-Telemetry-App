package com.fleetcare.obd.domain.usecase

import com.fleetcare.obd.domain.model.BluetoothDevice
import com.fleetcare.obd.domain.repository.BluetoothRepository
import javax.inject.Inject

/**
 * Use Case para conectar a un dispositivo Bluetooth OBDII.
 *
 * Valida precondiciones y delega la conexión al repository.
 */
class ConnectToDeviceUseCase @Inject constructor(
    private val bluetoothRepository: BluetoothRepository
) {

    /**
     * Conecta al dispositivo Bluetooth especificado.
     *
     * Precondiciones verificadas:
     * - Permisos otorgados
     * - Bluetooth habilitado
     * - Dispositivo válido
     *
     * @param device Dispositivo al que conectar
     * @return Result indicando éxito o fallo
     */
    suspend operator fun invoke(device: BluetoothDevice): Result<Unit> {
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

        // Verificar dirección válida
        if (device.address.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Dirección de dispositivo inválida")
            )
        }

        // Intentar conexión
        return bluetoothRepository.connect(device)
    }
}
