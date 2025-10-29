package com.fleetcare.obd.ui.connection.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fleetcare.obd.databinding.ItemBluetoothDeviceBinding
import com.fleetcare.obd.domain.model.BluetoothDevice
import com.fleetcare.obd.utils.visibleIf

/**
 * Adapter para mostrar lista de dispositivos Bluetooth en RecyclerView.
 *
 * Usa ListAdapter con DiffUtil para actualizaciones eficientes de la lista.
 */
class DeviceListAdapter(
    private val onDeviceClick: (BluetoothDevice) -> Unit
) : ListAdapter<BluetoothDevice, DeviceListAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemBluetoothDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding, onDeviceClick)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder para un dispositivo Bluetooth.
     */
    class DeviceViewHolder(
        private val binding: ItemBluetoothDeviceBinding,
        private val onDeviceClick: (BluetoothDevice) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: BluetoothDevice) {
            binding.apply {
                // Nombre del dispositivo
                tvDeviceName.text = device.displayName

                // Dirección MAC
                tvDeviceAddress.text = device.address

                // Mostrar badge OBDII si es un dispositivo OBDII
                tvOBDIIBadge.visibleIf(device.isOBDII)

                // Click listener
                root.setOnClickListener {
                    onDeviceClick(device)
                }
            }
        }
    }

    /**
     * DiffUtil callback para comparar dispositivos de forma eficiente.
     */
    private class DeviceDiffCallback : DiffUtil.ItemCallback<BluetoothDevice>() {
        override fun areItemsTheSame(oldItem: BluetoothDevice, newItem: BluetoothDevice): Boolean {
            // Los dispositivos son el mismo si tienen la misma dirección MAC
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(oldItem: BluetoothDevice, newItem: BluetoothDevice): Boolean {
            // El contenido es el mismo si todos los campos son iguales
            return oldItem == newItem
        }
    }
}
