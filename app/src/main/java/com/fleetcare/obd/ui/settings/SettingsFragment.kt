package com.fleetcare.obd.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.fleetcare.obd.R
import com.fleetcare.obd.databinding.FragmentSettingsBinding
import com.fleetcare.obd.domain.model.TemperatureUnit
import com.fleetcare.obd.domain.model.UnitSystem
import com.fleetcare.obd.ui.common.BaseFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment de configuración de la aplicación.
 *
 * Características:
 * - Configuración de unidades (métrico/imperial)
 * - Configuración de temperatura (Celsius/Fahrenheit)
 * - Activar/desactivar reconexión automática
 * - Intervalo de lectura de datos
 * - Información de la aplicación
 */
@AndroidEntryPoint
class SettingsFragment : BaseFragment<FragmentSettingsBinding>() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentSettingsBinding {
        return FragmentSettingsBinding.inflate(inflater, container, false)
    }

    override fun setupUI() {
        // Configurar listeners de unidades
        binding.unitSystemRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.metricRadioButton -> viewModel.setUnitSystem(UnitSystem.METRIC)
                R.id.imperialRadioButton -> viewModel.setUnitSystem(UnitSystem.IMPERIAL)
            }
        }

        // Configurar listeners de temperatura
        binding.temperatureRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.celsiusRadioButton -> viewModel.setTemperatureUnit(TemperatureUnit.CELSIUS)
                R.id.fahrenheitRadioButton -> viewModel.setTemperatureUnit(TemperatureUnit.FAHRENHEIT)
            }
        }

        // Configurar switch de reconexión automática
        binding.autoReconnectSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutoReconnect(isChecked)
        }

        // Configurar slider de intervalo
        binding.intervalSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.intervalValueText.text = "${value.toInt()} ms"
                viewModel.setReadInterval(value.toInt())
            }
        }

        // Sprint 1: Configurar switch de captura RAW
        binding.enableRawCaptureSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setEnableRawCapture(isChecked)
        }

        // Sprint 1: Configurar slider de días de retención
        binding.retentionDaysSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val days = value.toInt()
                binding.retentionDaysText.text = "$days días"
                viewModel.setRawCaptureRetentionDays(days)
            }
        }

        // Sprint 1: Configurar botón de limpiar datos antiguos
        binding.cleanOldDataButton.setOnClickListener {
            showCleanDataConfirmationDialog()
        }

        // Configurar botón de reset
        binding.resetButton.setOnClickListener {
            showResetConfirmationDialog()
        }
    }

    override fun observeData() {
        // Observar configuraciones
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.settings.collect { settings ->
                updateUI(
                    settings.unitSystem,
                    settings.temperatureUnit,
                    settings.autoReconnect,
                    settings.readInterval,
                    settings.enableRawCapture,
                    settings.rawCaptureRetentionDays
                )
            }
        }

        // Sprint 1: Observar información de almacenamiento
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.storageInfo.collect { info ->
                binding.storageInfoText.text = info.ifEmpty { "No hay datos almacenados" }
            }
        }
    }

    private fun updateUI(
        unitSystem: UnitSystem,
        temperatureUnit: TemperatureUnit,
        autoReconnect: Boolean,
        readInterval: Int,
        enableRawCapture: Boolean,
        rawCaptureRetentionDays: Int
    ) {
        // Actualizar sistema de unidades
        when (unitSystem) {
            UnitSystem.METRIC -> binding.metricRadioButton.isChecked = true
            UnitSystem.IMPERIAL -> binding.imperialRadioButton.isChecked = true
        }

        // Actualizar unidad de temperatura
        when (temperatureUnit) {
            TemperatureUnit.CELSIUS -> binding.celsiusRadioButton.isChecked = true
            TemperatureUnit.FAHRENHEIT -> binding.fahrenheitRadioButton.isChecked = true
        }

        // Actualizar reconexión automática
        binding.autoReconnectSwitch.isChecked = autoReconnect

        // Actualizar intervalo
        binding.intervalSlider.value = readInterval.toFloat()
        binding.intervalValueText.text = "$readInterval ms"

        // Sprint 1: Actualizar configuración de captura RAW
        binding.enableRawCaptureSwitch.isChecked = enableRawCapture
        binding.retentionDaysSlider.value = rawCaptureRetentionDays.toFloat()
        binding.retentionDaysText.text = "$rawCaptureRetentionDays días"
    }

    private fun showResetConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Restaurar Configuración")
            .setMessage("¿Estás seguro de que deseas restaurar todas las configuraciones a sus valores por defecto?")
            .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                viewModel.resetToDefaults()
            }
            .setNegativeButton(getString(R.string.dialog_no), null)
            .show()
    }

    /**
     * Sprint 1: Muestra diálogo de confirmación para limpiar datos RAW antiguos.
     */
    private fun showCleanDataConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_clean_confirmation_title))
            .setMessage(getString(R.string.settings_clean_confirmation_message))
            .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                viewModel.cleanOldData()
            }
            .setNegativeButton(getString(R.string.dialog_no), null)
            .show()
    }
}
