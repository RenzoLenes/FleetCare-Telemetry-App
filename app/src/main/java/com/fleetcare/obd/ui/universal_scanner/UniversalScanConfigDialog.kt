package com.fleetcare.obd.ui.universal_scanner

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.fleetcare.obd.databinding.DialogUniversalScanConfigBinding
import com.fleetcare.obd.domain.model.ScanMode
import com.fleetcare.obd.domain.model.UniversalScanConfig
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Dialog para configuración avanzada del scanner universal.
 */
class UniversalScanConfigDialog(
    private val currentConfig: UniversalScanConfig,
    private val onConfigSelected: (UniversalScanConfig) -> Unit
) : DialogFragment() {

    private var _binding: DialogUniversalScanConfigBinding? = null
    private val binding get() = _binding!!

    private val selectedModes = mutableSetOf<ScanMode>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogUniversalScanConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        loadCurrentConfig()
    }

    private fun setupUI() {
        // Mode checkboxes
        binding.checkboxMode01.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedModes.add(ScanMode.MODE_01_CURRENT_DATA)
            else selectedModes.remove(ScanMode.MODE_01_CURRENT_DATA)
        }

        binding.checkboxMode02.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedModes.add(ScanMode.MODE_02_FREEZE_FRAME)
            else selectedModes.remove(ScanMode.MODE_02_FREEZE_FRAME)
        }

        binding.checkboxMode09.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedModes.add(ScanMode.MODE_09_VEHICLE_INFO)
            else selectedModes.remove(ScanMode.MODE_09_VEHICLE_INFO)
        }

        binding.checkboxMode22.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedModes.add(ScanMode.MODE_22_MANUFACTURER)
            else selectedModes.remove(ScanMode.MODE_22_MANUFACTURER)
        }

        // Buttons
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnStartScan.setOnClickListener {
            if (validateAndBuildConfig()) {
                val config = buildConfig()
                onConfigSelected(config)
                dismiss()
            }
        }
    }

    private fun loadCurrentConfig() {
        // Load current config
        selectedModes.clear()
        selectedModes.addAll(currentConfig.modes)

        binding.checkboxMode01.isChecked = selectedModes.contains(ScanMode.MODE_01_CURRENT_DATA)
        binding.checkboxMode02.isChecked = selectedModes.contains(ScanMode.MODE_02_FREEZE_FRAME)
        binding.checkboxMode09.isChecked = selectedModes.contains(ScanMode.MODE_09_VEHICLE_INFO)
        binding.checkboxMode22.isChecked = selectedModes.contains(ScanMode.MODE_22_MANUFACTURER)

        // Load PID ranges
        currentConfig.pidRanges[ScanMode.MODE_01_CURRENT_DATA]?.let { range ->
            binding.etMode01Start.setText(range.first.toString(16).uppercase())
            binding.etMode01End.setText(range.last.toString(16).uppercase())
        }

        currentConfig.pidRanges[ScanMode.MODE_09_VEHICLE_INFO]?.let { range ->
            binding.etMode09Start.setText(range.first.toString(16).uppercase())
            binding.etMode09End.setText(range.last.toString(16).uppercase())
        }

        // Load timeout
        binding.etTimeout.setText(currentConfig.timeout.toString())

        // Load intelligent skipping
        binding.switchIntelligentSkipping.isChecked = currentConfig.intelligentSkipping
    }

    private fun validateAndBuildConfig(): Boolean {
        if (selectedModes.isEmpty()) {
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.text = "Selecciona al menos un modo"
            return false
        }

        binding.tvError.visibility = View.GONE
        return true
    }

    private fun buildConfig(): UniversalScanConfig {
        val pidRanges = mutableMapOf<ScanMode, IntRange>()

        // Mode 01 range
        if (selectedModes.contains(ScanMode.MODE_01_CURRENT_DATA)) {
            val start = binding.etMode01Start.text.toString().toIntOrNull(16) ?: 0x00
            val end = binding.etMode01End.text.toString().toIntOrNull(16) ?: 0xFF
            pidRanges[ScanMode.MODE_01_CURRENT_DATA] = start..end
        }

        // Mode 09 range
        if (selectedModes.contains(ScanMode.MODE_09_VEHICLE_INFO)) {
            val start = binding.etMode09Start.text.toString().toIntOrNull(16) ?: 0x00
            val end = binding.etMode09End.text.toString().toIntOrNull(16) ?: 0x0F
            pidRanges[ScanMode.MODE_09_VEHICLE_INFO] = start..end
        }

        // Other modes with default ranges
        if (selectedModes.contains(ScanMode.MODE_02_FREEZE_FRAME)) {
            pidRanges[ScanMode.MODE_02_FREEZE_FRAME] = 0x00..0xFF
        }

        if (selectedModes.contains(ScanMode.MODE_22_MANUFACTURER)) {
            pidRanges[ScanMode.MODE_22_MANUFACTURER] = 0x00..0xFF
        }

        val timeout = binding.etTimeout.text.toString().toLongOrNull() ?: 300L
        val intelligentSkipping = binding.switchIntelligentSkipping.isChecked

        return UniversalScanConfig(
            vehicleId = currentConfig.vehicleId,
            modes = selectedModes.toList(),
            pidRanges = pidRanges,
            timeout = timeout,
            intelligentSkipping = intelligentSkipping
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "UniversalScanConfigDialog"
    }
}
