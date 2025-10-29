package com.fleetcare.obd.domain.model

/**
 * Modelo de dominio para representar un dispositivo Bluetooth.
 *
 * Esta clase es independiente de la implementación de Android Bluetooth,
 * siguiendo los principios de Clean Architecture.
 *
 * @property name Nombre amigable del dispositivo
 * @property address Dirección MAC del dispositivo (formato XX:XX:XX:XX:XX:XX)
 * @property bondState Estado de emparejamiento
 * @property deviceType Tipo de dispositivo (Clásico, BLE, Dual)
 * @property isOBDII Indica si el dispositivo es probablemente un adaptador OBDII
 */
data class BluetoothDevice(
    val name: String?,
    val address: String,
    val bondState: BondState,
    val deviceType: DeviceType = DeviceType.CLASSIC,
    val isOBDII: Boolean = false
) {
    /**
     * Nombre a mostrar en la UI.
     * Si el dispositivo no tiene nombre, muestra la dirección MAC.
     */
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: address

    /**
     * Indica si el dispositivo está emparejado.
     */
    val isPaired: Boolean
        get() = bondState == BondState.BONDED
}

/**
 * Estado de emparejamiento del dispositivo.
 */
enum class BondState {
    /**
     * No hay vínculo con el dispositivo.
     */
    NONE,

    /**
     * Proceso de emparejamiento en curso.
     */
    BONDING,

    /**
     * Dispositivo emparejado exitosamente.
     */
    BONDED
}

/**
 * Tipo de tecnología Bluetooth del dispositivo.
 */
enum class DeviceType {
    /**
     * Bluetooth clásico (lo que usan los OBDII).
     */
    CLASSIC,

    /**
     * Bluetooth Low Energy.
     */
    BLE,

    /**
     * Soporta ambos (Bluetooth Dual Mode).
     */
    DUAL,

    /**
     * Tipo desconocido.
     */
    UNKNOWN
}
