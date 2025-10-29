package com.fleetcare.obd.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.fleetcare.obd.R
import com.fleetcare.obd.databinding.FragmentDashboardBinding
import com.fleetcare.obd.domain.model.VehicleData
import com.fleetcare.obd.ui.common.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment que muestra el Dashboard con datos en tiempo real del vehículo.
 *
 * Características:
 * - Visualización de 10 parámetros del vehículo
 * - Actualización en tiempo real
 * - Indicadores visuales (progress bars, circular indicators)
 * - Botón para leer DTCs
 * - Indicador de sincronización con Firebase
 */
@AndroidEntryPoint
class DashboardFragment : BaseFragment<FragmentDashboardBinding>() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentDashboardBinding {
        return FragmentDashboardBinding.inflate(inflater, container, false)
    }

    override fun setupUI() {
        binding.readDtcButton.setOnClickListener {
            viewModel.readDiagnosticCodes()
        }

        // Firebase Diagnostics Card - Expandir/Colapsar
        binding.firebaseDiagnosticsHeader.setOnClickListener {
            toggleFirebaseDiagnostics()
        }

        // Botones de acción de Firebase
        binding.testFirebaseButton.setOnClickListener {
            viewModel.testFirebaseWrite()
        }

        binding.resetStatsButton.setOnClickListener {
            viewModel.resetFirebaseStats()
        }
    }

    override fun observeData() {
        // Observar estado del dashboard
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.dashboardState.collect { state ->
                updateUI(state)
            }
        }

        // Observar estadísticas de Firebase
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.firebaseSyncStats.collect { stats ->
                updateFirebaseStats(stats)
            }
        }
    }

    private fun updateUI(state: DashboardState) {
        when (state) {
            is DashboardState.Disconnected -> {
                showDisconnectedState()
            }

            is DashboardState.Connecting -> {
                showConnectingState()
            }

            is DashboardState.Connected -> {
                showConnectedState()
            }

            is DashboardState.ReadingData -> {
                showReadingState()
            }

            is DashboardState.DataAvailable -> {
                showDataAvailable(state.data, state.isSyncingToFirebase)
            }
        }
    }

    private fun showDisconnectedState() {
        binding.connectionStatusText.text = "Desconectado"
        binding.connectionStatusText.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.md_theme_light_error)
        )
        binding.firebaseSyncText.text = "Sin sincronización"
        binding.readDtcButton.isEnabled = false

        // Resetear todos los valores
        clearAllValues()
    }

    private fun showConnectingState() {
        binding.connectionStatusText.text = "Conectando..."
        binding.connectionStatusText.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary)
        )
        binding.firebaseSyncText.text = "Inicializando..."
        binding.readDtcButton.isEnabled = false
    }

    private fun showConnectedState() {
        binding.connectionStatusText.text = "Conectado"
        binding.connectionStatusText.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary)
        )
        binding.firebaseSyncText.text = "Esperando datos..."
        binding.readDtcButton.isEnabled = true
    }

    private fun showReadingState() {
        binding.connectionStatusText.text = "Leyendo datos..."
        binding.connectionStatusText.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary)
        )
        binding.firebaseSyncText.text = "Esperando datos..."
        binding.readDtcButton.isEnabled = true
    }

    private fun showDataAvailable(data: VehicleData, isSyncingToFirebase: Boolean) {
        binding.connectionStatusText.text = "Datos en tiempo real"
        binding.connectionStatusText.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary)
        )

        binding.firebaseSyncText.text = if (isSyncingToFirebase) {
            "Sincronizando con Firebase ✓"
        } else {
            "Sin sincronización"
        }
        binding.firebaseSyncText.setTextColor(
            if (isSyncingToFirebase) {
                ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary)
            } else {
                ContextCompat.getColor(requireContext(), R.color.md_theme_light_onSurfaceVariant)
            }
        )

        binding.readDtcButton.isEnabled = true

        // Actualizar todos los valores
        updateVehicleData(data)
    }

    private fun updateVehicleData(data: VehicleData) {
        // RPM
        binding.rpmValue.text = viewModel.formatRpm(data.rpm)
        data.rpm?.let { rpm ->
            binding.rpmProgress.progress = rpm.coerceIn(0, 8000)
        } ?: run {
            binding.rpmProgress.progress = 0
        }

        // Speed
        binding.speedValue.text = data.speed?.toInt()?.toString() ?: "--"

        // Coolant Temperature
        binding.coolantTempValue.text = viewModel.formatValue(data.coolantTemp, "°C", 0)
        data.coolantTemp?.let { temp ->
            val progress = (temp + 40).coerceIn(0.0, 150.0).toInt()
            binding.coolantTempProgress.progress = progress
        } ?: run {
            binding.coolantTempProgress.progress = 0
        }

        // Engine Load
        binding.engineLoadValue.text = viewModel.formatValue(data.engineLoad, "%", 0)
        data.engineLoad?.let { load ->
            binding.engineLoadProgress.progress = load.toInt()
        } ?: run {
            binding.engineLoadProgress.progress = 0
        }

        // Throttle Position
        binding.throttlePositionValue.text = viewModel.formatValue(data.throttlePosition, "%", 0)
        data.throttlePosition?.let { throttle ->
            binding.throttlePositionProgress.progress = throttle.toInt()
        } ?: run {
            binding.throttlePositionProgress.progress = 0
        }

        // Voltage
        binding.voltageValue.text = viewModel.formatValue(data.voltage, "V", 1)

        // Fuel Level
        binding.fuelLevelValue.text = viewModel.formatValue(data.fuelLevel, "%", 0)

        // Intake Air Temp
        binding.intakeAirTempValue.text = viewModel.formatValue(data.intakeAirTemp, "°C", 0)

        // Ambient Temp
        binding.ambientTempValue.text = viewModel.formatValue(data.ambientTemp, "°C", 0)
    }

    private fun clearAllValues() {
        binding.rpmValue.text = "--"
        binding.rpmProgress.progress = 0

        binding.speedValue.text = "--"

        binding.coolantTempValue.text = "--"
        binding.coolantTempProgress.progress = 0

        binding.engineLoadValue.text = "--"
        binding.engineLoadProgress.progress = 0

        binding.throttlePositionValue.text = "--"
        binding.throttlePositionProgress.progress = 0

        binding.voltageValue.text = "--"
        binding.fuelLevelValue.text = "--"
        binding.intakeAirTempValue.text = "--"
        binding.ambientTempValue.text = "--"
    }

    private fun toggleFirebaseDiagnostics() {
        val content = binding.firebaseDiagnosticsContent
        val icon = binding.firebaseDiagnosticsExpandIcon

        if (content.visibility == View.GONE) {
            content.visibility = View.VISIBLE
            icon.rotation = 180f
        } else {
            content.visibility = View.GONE
            icon.rotation = 0f
        }
    }

    private fun updateFirebaseStats(stats: com.fleetcare.obd.domain.model.FirebaseSyncStats) {
        // Actualizar summary
        if (stats.isActive) {
            val summary = if (stats.hasErrors) {
                "Activo - ${stats.successfulWrites} exitosos, ${stats.failedWrites} fallidos"
            } else {
                "Activo - ${stats.successfulWrites} escrituras exitosas"
            }
            binding.firebaseDiagnosticsSummary.text = summary
            binding.firebaseDiagnosticsSummary.setTextColor(
                if (stats.hasErrors) {
                    ContextCompat.getColor(requireContext(), R.color.md_theme_light_error)
                } else {
                    ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary)
                }
            )
        } else {
            binding.firebaseDiagnosticsSummary.text = "Inactivo - Toca para ver detalles"
            binding.firebaseDiagnosticsSummary.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.md_theme_light_onSurfaceVariant)
            )
        }

        // Actualizar valores detallados
        binding.totalAttemptsValue.text = stats.totalAttempts.toString()
        binding.successfulWritesValue.text = stats.successfulWrites.toString()
        binding.failedWritesValue.text = stats.failedWrites.toString()
        binding.successRateValue.text = String.format("%.1f%%", stats.successRate)
        binding.pendingQueueValue.text = "${stats.pendingQueueSize} items"

        // Mostrar último error si existe
        if (stats.lastError != null) {
            binding.lastErrorLabel.visibility = View.VISIBLE
            binding.lastErrorValue.visibility = View.VISIBLE
            binding.lastErrorValue.text = "[${stats.lastErrorType}] ${stats.lastError}"
        } else {
            binding.lastErrorLabel.visibility = View.GONE
            binding.lastErrorValue.visibility = View.GONE
        }
    }
}
