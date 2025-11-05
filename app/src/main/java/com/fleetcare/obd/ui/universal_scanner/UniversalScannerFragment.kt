package com.fleetcare.obd.ui.universal_scanner

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.fleetcare.obd.R
import com.fleetcare.obd.databinding.FragmentUniversalScannerBinding
import com.fleetcare.obd.domain.model.ExportFormat
import com.fleetcare.obd.domain.model.ScanSession
import com.fleetcare.obd.domain.usecase.ExportResult
import com.fleetcare.obd.utils.Logger
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment para el Universal PID Scanner.
 *
 * Permite al usuario:
 * - Seleccionar preset de escaneo
 * - Iniciar/pausar/cancelar escaneos
 * - Ver progreso en tiempo real
 * - Navegar a resultados
 */
@AndroidEntryPoint
class UniversalScannerFragment : Fragment() {

    private var _binding: FragmentUniversalScannerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UniversalScannerViewModel by viewModels()
    private val args: UniversalScannerFragmentArgs by navArgs()

    private val currentVehicleId: String
        get() = args.vehicleId

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUniversalScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        observeState()
        observeProgress()

        // Cargar perfil del vehículo
        viewModel.loadVehicleProfile(currentVehicleId)

        // Verificar si hay sesión activa
        viewModel.checkActiveSession(currentVehicleId)
    }

    private fun setupUI() {
        // Preset selection
        binding.chipGroupPresets.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val preset = when (checkedIds[0]) {
                    R.id.chipQuickScan -> ScanPresetType.QUICK
                    R.id.chipFullScan -> ScanPresetType.FULL_STANDARD
                    R.id.chipDeepScan -> ScanPresetType.DEEP
                    R.id.chipLegacyScan -> ScanPresetType.LEGACY
                    R.id.chipManufacturerScan -> ScanPresetType.MANUFACTURER
                    R.id.chipRecommended -> ScanPresetType.RECOMMENDED
                    else -> ScanPresetType.QUICK
                }
                viewModel.selectPreset(preset)
            }
        }

        // Start button
        binding.btnStartScan.setOnClickListener {
            startScan()
        }

        // Pause button
        binding.btnPauseScan.setOnClickListener {
            viewModel.pauseScan(currentVehicleId)
        }

        // Resume button
        binding.btnResumeScan.setOnClickListener {
            viewModel.resumeScan(currentVehicleId)
        }

        // Cancel button
        binding.btnCancelScan.setOnClickListener {
            confirmCancelScan()
        }

        // View results button
        binding.btnViewResults.setOnClickListener {
            viewModel.currentSession.value?.let { session ->
                navigateToResults(session.sessionId)
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.vehicleProfile.collect { profile ->
                    if (profile != null) {
                        binding.tvVehicleName.text = profile.getDisplayName()
                        binding.tvProtocol.text = profile.protocolName
                        binding.tvKnownPids.text = "${profile.supportedPIDsCount} PIDs"

                        // Mostrar chip de recommended si existe perfil
                        if (profile.isComplete()) {
                            binding.chipRecommended.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun observeProgress() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.scanProgress.collect { progress ->
                    if (progress != null) {
                        updateProgress(progress)
                    }
                }
            }
        }
    }

    private fun updateUI(state: ScannerUIState) {
        when (state) {
            is ScannerUIState.Idle -> {
                binding.layoutPresets.visibility = View.VISIBLE
                binding.layoutProgress.visibility = View.GONE
                binding.layoutResults.visibility = View.GONE

                binding.btnStartScan.isEnabled = true
                binding.btnStartScan.text = "Start Scan"
            }

            is ScannerUIState.Preparing -> {
                binding.layoutPresets.visibility = View.GONE
                binding.layoutProgress.visibility = View.VISIBLE
                binding.layoutResults.visibility = View.GONE

                binding.tvProgressStatus.text = "Preparing scan..."
                binding.btnPauseScan.visibility = View.GONE
                binding.btnResumeScan.visibility = View.GONE
                binding.btnCancelScan.visibility = View.VISIBLE
            }

            is ScannerUIState.Scanning -> {
                binding.layoutPresets.visibility = View.GONE
                binding.layoutProgress.visibility = View.VISIBLE
                binding.layoutResults.visibility = View.GONE

                binding.tvProgressStatus.text = "Scanning..."
                binding.btnPauseScan.visibility = View.VISIBLE
                binding.btnResumeScan.visibility = View.GONE
                binding.btnCancelScan.visibility = View.VISIBLE
            }

            is ScannerUIState.Paused -> {
                binding.tvProgressStatus.text = "Paused"
                binding.btnPauseScan.visibility = View.GONE
                binding.btnResumeScan.visibility = View.VISIBLE
                binding.btnCancelScan.visibility = View.VISIBLE
            }

            is ScannerUIState.Completed -> {
                binding.layoutPresets.visibility = View.GONE
                binding.layoutProgress.visibility = View.GONE
                binding.layoutResults.visibility = View.VISIBLE

                state.session?.let { session ->
                    binding.tvResultsTitle.text = "Scan Completed!"
                    binding.tvResultsSuccessful.text = "${session.getSupportedPIDsCount()} PIDs found"
                    binding.tvResultsDuration.text = session.getFormattedDuration()

                    session.statistics?.let { stats ->
                        binding.tvResultsQuality.text = "Quality: ${stats.qualityScore}/100"
                    }
                }

                Snackbar.make(binding.root, "Scan completed successfully!", Snackbar.LENGTH_LONG).show()
            }

            is ScannerUIState.Exporting -> {
                // Export in progress - show in results screen
            }

            is ScannerUIState.ExportCompleted -> {
                // Export completed - handled in results screen
                viewModel.clearExportState()
            }

            is ScannerUIState.Error -> {
                binding.layoutPresets.visibility = View.VISIBLE
                binding.layoutProgress.visibility = View.GONE
                binding.layoutResults.visibility = View.GONE

                Snackbar.make(
                    binding.root,
                    "Error: ${state.message}",
                    Snackbar.LENGTH_LONG
                ).show()

                viewModel.resetState()
            }
        }
    }

    private fun updateProgress(progress: com.fleetcare.obd.domain.model.ScanProgress) {
        binding.progressBar.progress = progress.getProgressPercent()
        binding.tvProgressPercent.text = "${progress.getProgressPercent()}%"
        binding.tvProgressText.text = progress.getProgressText()

        binding.tvProgressMode.text = "Mode ${progress.currentMode}"
        binding.tvProgressSuccess.text = "Success: ${progress.successCount}"
        binding.tvProgressFailed.text = "Failed: ${progress.failedCount}"

        if (progress.skippedCount > 0) {
            binding.tvProgressSkipped.visibility = View.VISIBLE
            binding.tvProgressSkipped.text = "Skipped: ${progress.skippedCount}"
        }

        binding.tvProgressElapsed.text = "Elapsed: ${progress.getElapsedTimeFormatted()}"
        binding.tvProgressRemaining.text = "Remaining: ${progress.getEstimatedTimeFormatted()}"
    }

    private fun startScan() {
        Logger.d("Starting scan with vehicle: $currentVehicleId")
        viewModel.startScan(currentVehicleId)
    }

    private fun confirmCancelScan() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cancel Scan?")
            .setMessage("Are you sure you want to cancel the current scan? Progress will be lost.")
            .setPositiveButton("Cancel Scan") { _, _ ->
                viewModel.cancelScan(currentVehicleId)
            }
            .setNegativeButton("Continue Scanning", null)
            .show()
    }

    private fun navigateToResults(sessionId: String) {
        val action = UniversalScannerFragmentDirections.actionScannerToResults(sessionId)
        findNavController().navigate(action)
    }

    private fun showExportDialog() {
        val dialog = ExportOptionsDialog { format ->
            viewModel.exportSession(format)
        }
        dialog.show(childFragmentManager, ExportOptionsDialog.TAG)
    }

    private fun handleExportResult(result: ExportResult) {
        when (result) {
            is ExportResult.FileExport -> {
                Snackbar.make(
                    binding.root,
                    "Exported to ${result.fileName}",
                    Snackbar.LENGTH_LONG
                ).setAction("Share") {
                    shareFile(result.uri, result.mimeType)
                }.show()
            }

            is ExportResult.QrCodeExport -> {
                showQrCodeDialog(result)
            }
        }
    }

    private fun shareFile(uri: android.net.Uri, mimeType: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share scan results"))
    }

    private fun showQrCodeDialog(result: ExportResult.QrCodeExport) {
        val dialog = QrCodeDisplayDialog(result.bitmap, result.qrData)
        dialog.show(childFragmentManager, "QrCodeDialog")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
