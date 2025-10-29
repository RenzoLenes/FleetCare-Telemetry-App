package com.fleetcare.obd.ui.connection

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.fleetcare.obd.R
import com.fleetcare.obd.databinding.FragmentConnectionBinding
import com.fleetcare.obd.domain.model.ConnectionState
import com.fleetcare.obd.ui.common.BaseFragment
import com.fleetcare.obd.ui.connection.adapter.DeviceListAdapter
import com.fleetcare.obd.utils.Logger
import com.fleetcare.obd.utils.gone
import com.fleetcare.obd.utils.showSnackbar
import com.fleetcare.obd.utils.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Fragment de gestión de conexión Bluetooth.
 *
 * Funcionalidades:
 * - Escaneo y lista de dispositivos Bluetooth emparejados
 * - Conexión a dispositivos OBDII
 * - Indicadores de estado de conexión en tiempo real
 * - Solicitud de permisos runtime
 * - Habilitación de Bluetooth si está desactivado
 */
@AndroidEntryPoint
class ConnectionFragment : BaseFragment<FragmentConnectionBinding>() {

    private val viewModel: ConnectionViewModel by viewModels()

    // Adapter para la lista de dispositivos
    private val deviceAdapter = DeviceListAdapter { device ->
        viewModel.connectToDevice(device)
    }

    // Launcher para solicitar permisos
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Logger.bluetooth("Permisos otorgados")
            viewModel.checkPermissions()
            viewModel.loadPairedDevices()
        } else {
            showSnackbar(getString(R.string.permission_required))
        }
    }

    // Launcher para habilitar Bluetooth
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            Logger.bluetooth("Bluetooth habilitado por el usuario")
            viewModel.loadPairedDevices()
        } else {
            showSnackbar(getString(R.string.error_bluetooth_not_enabled))
        }
    }

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentConnectionBinding {
        return FragmentConnectionBinding.inflate(inflater, container, false)
    }

    override fun setupUI() {
        // Configurar RecyclerView
        binding.rvDevices.adapter = deviceAdapter

        // Configurar botón de escaneo
        binding.btnScan.setOnClickListener {
            checkPermissionsAndScan()
        }

        // Configurar botón de desconexión
        binding.btnDisconnect.setOnClickListener {
            viewModel.disconnect()
        }

        // Cargar dispositivos al iniciar
        checkPermissionsAndScan()
    }

    override fun observeData() {
        // Observar lista de dispositivos
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.devices.collectLatest { devices ->
                deviceAdapter.submitList(devices)

                // Mostrar/ocultar empty state
                if (devices.isEmpty()) {
                    binding.tvEmptyState.visible()
                    binding.rvDevices.gone()
                } else {
                    binding.tvEmptyState.gone()
                    binding.rvDevices.visible()
                }
            }
        }

        // Observar estado de conexión
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.connectionState.collectLatest { state ->
                updateConnectionUI(state)
            }
        }

        // Observar loading
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                if (isLoading) {
                    binding.progressBar.visible()
                } else {
                    binding.progressBar.gone()
                }
            }
        }

        // Observar eventos de error
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.errorEvent.collectLatest { errorMessage ->
                showSnackbar(errorMessage)
            }
        }

        // Observar eventos de éxito
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.successEvent.collectLatest { message ->
                showSnackbar(message)
            }
        }
    }

    /**
     * Verifica permisos y estado de Bluetooth antes de escanear.
     */
    private fun checkPermissionsAndScan() {
        // Verificar permisos
        if (!viewModel.permissionsGranted.value) {
            requestBluetoothPermissions()
            return
        }

        // Verificar estado de Bluetooth
        when (viewModel.checkBluetoothStatus()) {
            BluetoothStatus.NOT_AVAILABLE -> {
                showSnackbar(getString(R.string.error_bluetooth_not_supported))
            }
            BluetoothStatus.NOT_ENABLED -> {
                requestEnableBluetooth()
            }
            BluetoothStatus.READY -> {
                viewModel.loadPairedDevices()
            }
        }
    }

    /**
     * Solicita permisos de Bluetooth según la versión de Android.
     */
    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        permissionLauncher.launch(permissions)
    }

    /**
     * Solicita al usuario habilitar Bluetooth.
     */
    private fun requestEnableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableBluetoothLauncher.launch(enableBtIntent)
    }

    /**
     * Actualiza la UI según el estado de conexión.
     */
    private fun updateConnectionUI(state: ConnectionState) {
        when (state) {
            is ConnectionState.Disconnected -> {
                binding.ivConnectionStatus.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.bluetooth_disconnected)
                )
                binding.tvConnectionStatus.text = getString(R.string.connection_disconnected)
                binding.tvDeviceInfo.text = getString(R.string.bluetooth_no_devices)
                binding.btnDisconnect.gone()
            }

            is ConnectionState.Connecting -> {
                binding.ivConnectionStatus.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.bluetooth_connecting)
                )
                binding.tvConnectionStatus.text = getString(R.string.bluetooth_connecting)
                binding.tvDeviceInfo.text = state.deviceName
                binding.btnDisconnect.gone()
            }

            is ConnectionState.Connected -> {
                binding.ivConnectionStatus.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.bluetooth_connected)
                )
                binding.tvConnectionStatus.text = getString(R.string.connection_connected)
                binding.tvDeviceInfo.text = state.device.displayName
                binding.btnDisconnect.visible()
            }

            is ConnectionState.Error -> {
                binding.ivConnectionStatus.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.bluetooth_error)
                )
                binding.tvConnectionStatus.text = getString(R.string.connection_error)
                binding.tvDeviceInfo.text = state.message
                binding.btnDisconnect.gone()
            }

            is ConnectionState.Reconnecting -> {
                binding.ivConnectionStatus.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.bluetooth_connecting)
                )
                binding.tvConnectionStatus.text = "Reconectando ${state.attempt}/${state.maxAttempts}"
                binding.btnDisconnect.visible()
            }
        }
    }
}
