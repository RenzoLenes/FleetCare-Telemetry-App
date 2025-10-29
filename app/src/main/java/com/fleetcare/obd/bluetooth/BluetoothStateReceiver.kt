package com.fleetcare.obd.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fleetcare.obd.utils.Logger

/**
 * BroadcastReceiver para escuchar cambios en el estado de Bluetooth.
 *
 * Este receiver se activa cuando:
 * - El adaptador Bluetooth se enciende o apaga
 * - Un dispositivo se conecta o desconecta
 *
 * Se registra en el AndroidManifest para recibir broadcasts del sistema.
 * En Sprint 2 se utilizará completamente para manejar eventos de Bluetooth.
 *
 * Por ahora solo registra los eventos en el log.
 */
class BluetoothStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            // El estado del adaptador Bluetooth cambió
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                )

                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
                        Logger.bluetooth("Bluetooth desactivado")
                        // En Sprint 2: notificar a la app que Bluetooth se desactivó
                    }
                    BluetoothAdapter.STATE_TURNING_OFF -> {
                        Logger.bluetooth("Bluetooth desactivándose...")
                    }
                    BluetoothAdapter.STATE_ON -> {
                        Logger.bluetooth("Bluetooth activado")
                        // En Sprint 2: notificar a la app que Bluetooth está disponible
                    }
                    BluetoothAdapter.STATE_TURNING_ON -> {
                        Logger.bluetooth("Bluetooth activándose...")
                    }
                }
            }

            // Un dispositivo se conectó
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                Logger.bluetooth("Estado de conexión cambió")
                // En Sprint 2: manejar cambios de conexión
            }
        }
    }
}
