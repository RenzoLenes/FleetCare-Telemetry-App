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

        // Configurar botón de reset
        binding.resetButton.setOnClickListener {
            showResetConfirmationDialog()
        }
    }

    override fun observeData() {
        // Observar configuraciones
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.settings.collect { settings ->
                updateUI(settings.unitSystem, settings.temperatureUnit, settings.autoReconnect, settings.readInterval)
            }
        }
    }

    private fun updateUI(
        unitSystem: UnitSystem,
        temperatureUnit: TemperatureUnit,
        autoReconnect: Boolean,
        readInterval: Int
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
}
