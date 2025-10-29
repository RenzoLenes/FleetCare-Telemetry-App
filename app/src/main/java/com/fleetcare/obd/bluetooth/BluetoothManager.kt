package com.fleetcare.obd.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.fleetcare.obd.domain.model.BluetoothDevice
import com.fleetcare.obd.domain.model.BondState
import com.fleetcare.obd.domain.model.DeviceType
import com.fleetcare.obd.utils.Constants
import com.fleetcare.obd.utils.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager para gestionar el adaptador Bluetooth del dispositivo.
 *
 * Responsabilidades:
 * - Verificar disponibilidad y estado de Bluetooth
 * - Escanear dispositivos Bluetooth
 * - Obtener dispositivos emparejados
 * - Verificar permisos
 * - Identificar dispositivos OBDII
 *
 * Usa Flow para operaciones reactivas y está optimizado para Android 12+
 * con manejo de permisos granulares.
 */
@Singleton
class BluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val bluetoothManager: AndroidBluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? AndroidBluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? =
        bluetoothManager?.adapter

    /**
     * Verifica si Bluetooth está disponible en el dispositivo.
     */
    val isBluetoothAvailable: Boolean
        get() = bluetoothAdapter != null

    /**
     * Verifica si Bluetooth está habilitado.
     */
    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    /**
     * Verifica si la app tiene todos los permisos necesarios.
     */
    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requiere permisos granulares
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT) &&
                    hasPermission(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            // Android < 12 requiere ubicación para escaneo
            hasPermission(Manifest.permission.BLUETOOTH) &&
                    hasPermission(Manifest.permission.BLUETOOTH_ADMIN) &&
                    hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /**
     * Obtiene la lista de permisos faltantes.
     */
    fun getMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                missing.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                missing.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (!hasPermission(Manifest.permission.BLUETOOTH)) {
                missing.add(Manifest.permission.BLUETOOTH)
            }
            if (!hasPermission(Manifest.permission.BLUETOOTH_ADMIN)) {
                missing.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        return missing
    }

    /**
     * Obtiene los dispositivos Bluetooth ya emparejados.
     *
     * @return Lista de dispositivos emparejados, o null si hay error de permisos
     */
    @Suppress("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice>? {
        if (!hasRequiredPermissions()) {
            Logger.bluetoothError("No hay permisos para obtener dispositivos emparejados")
            return null
        }

        if (!isBluetoothAvailable || !isBluetoothEnabled) {
            Logger.bluetooth("Bluetooth no disponible o no habilitado")
            return emptyList()
        }

        return try {
            bluetoothAdapter?.bondedDevices?.map { device ->
                BluetoothDevice(
                    name = device.name,
                    address = device.address,
                    bondState = BondState.BONDED,
                    deviceType = mapDeviceType(device.type),
                    isOBDII = isLikelyOBDDevice(device.name, device.address)
                )
            } ?: emptyList()
        } catch (e: SecurityException) {
            Logger.bluetoothError("Error de seguridad al obtener dispositivos emparejados", e)
            null
        }
    }

    /**
     * Inicia el descubrimiento de dispositivos Bluetooth.
     *
     * NOTA: En Android moderno, el descubrimiento clásico está limitado.
     * Para OBDII, es más eficiente usar dispositivos emparejados.
     *
     * @return Flow que emite dispositivos descubiertos
     */
    @Suppress("MissingPermission")
    fun startDiscovery(): Flow<BluetoothDevice> = callbackFlow {
        if (!hasRequiredPermissions()) {
            Logger.bluetoothError("No hay permisos para iniciar descubrimiento")
            close()
            return@callbackFlow
        }

        if (!isBluetoothAvailable || !isBluetoothEnabled) {
            Logger.bluetooth("No se puede iniciar descubrimiento: Bluetooth no disponible")
            close()
            return@callbackFlow
        }

        Logger.bluetooth("Iniciando descubrimiento de dispositivos...")

        val receiver = BluetoothDiscoveryReceiver { device ->
            trySend(device)
        }

        // Registrar receiver para eventos de descubrimiento
        val intentFilter = android.content.IntentFilter().apply {
            addAction(android.bluetooth.BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        try {
            context.registerReceiver(receiver, intentFilter)

            // Cancelar descubrimiento previo si existe
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }

            // Iniciar descubrimiento
            val started = bluetoothAdapter?.startDiscovery() ?: false
            if (!started) {
                Logger.bluetoothError("No se pudo iniciar el descubrimiento")
            }
        } catch (e: SecurityException) {
            Logger.bluetoothError("Error de seguridad al iniciar descubrimiento", e)
        }

        awaitClose {
            try {
                bluetoothAdapter?.cancelDiscovery()
                context.unregisterReceiver(receiver)
                Logger.bluetooth("Descubrimiento finalizado")
            } catch (e: Exception) {
                Logger.bluetoothError("Error al finalizar descubrimiento", e)
            }
        }
    }

    /**
     * Cancela el descubrimiento en curso.
     */
    @Suppress("MissingPermission")
    fun cancelDiscovery() {
        if (!hasRequiredPermissions()) return

        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
                Logger.bluetooth("Descubrimiento cancelado")
            }
        } catch (e: SecurityException) {
            Logger.bluetoothError("Error al cancelar descubrimiento", e)
        }
    }

    /**
     * Verifica si un dispositivo es probablemente un adaptador OBDII.
     *
     * Basado en prefijos comunes de nombres de dispositivos OBDII.
     */
    private fun isLikelyOBDDevice(name: String?, address: String?): Boolean {
        if (name.isNullOrBlank()) return false

        val upperName = name.uppercase()
        return Constants.Bluetooth.OBDII_DEVICE_PREFIXES.any { prefix ->
            upperName.contains(prefix, ignoreCase = true)
        }
    }

    /**
     * Mapea el tipo de dispositivo Android a nuestro enum.
     */
    private fun mapDeviceType(type: Int): DeviceType {
        return when (type) {
            android.bluetooth.BluetoothDevice.DEVICE_TYPE_CLASSIC -> DeviceType.CLASSIC
            android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE -> DeviceType.BLE
            android.bluetooth.BluetoothDevice.DEVICE_TYPE_DUAL -> DeviceType.DUAL
            else -> DeviceType.UNKNOWN
        }
    }

    /**
     * Mapea el estado de emparejamiento Android a nuestro enum.
     */
    private fun mapBondState(bondState: Int): BondState {
        return when (bondState) {
            android.bluetooth.BluetoothDevice.BOND_NONE -> BondState.NONE
            android.bluetooth.BluetoothDevice.BOND_BONDING -> BondState.BONDING
            android.bluetooth.BluetoothDevice.BOND_BONDED -> BondState.BONDED
            else -> BondState.NONE
        }
    }

    /**
     * Verifica si la app tiene un permiso específico.
     */
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * BroadcastReceiver interno para eventos de descubrimiento.
     */
    private inner class BluetoothDiscoveryReceiver(
        private val onDeviceFound: (BluetoothDevice) -> Unit
    ) : android.content.BroadcastReceiver() {

        @Suppress("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                android.bluetooth.BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            android.bluetooth.BluetoothDevice.EXTRA_DEVICE,
                            android.bluetooth.BluetoothDevice::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let {
                        val btDevice = BluetoothDevice(
                            name = it.name,
                            address = it.address,
                            bondState = mapBondState(it.bondState),
                            deviceType = mapDeviceType(it.type),
                            isOBDII = isLikelyOBDDevice(it.name, it.address)
                        )
                        Logger.bluetooth("Dispositivo encontrado: ${btDevice.displayName}")
                        onDeviceFound(btDevice)
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Logger.bluetooth("Descubrimiento iniciado")
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Logger.bluetooth("Descubrimiento terminado")
                }
            }
        }
    }
}
