package com.fleetcare.obd.ui.diagnostics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.fleetcare.obd.R
import com.fleetcare.obd.databinding.FragmentDiagnosticsBinding
import com.fleetcare.obd.ui.common.BaseFragment
import com.fleetcare.obd.ui.diagnostics.adapter.DTCListAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment de diagnóstico de códigos DTC.
 *
 * Características:
 * - Lectura de códigos de error (DTCs)
 * - Descripción de códigos
 * - Funcionalidad para borrar códigos
 * - Visualización de errores activos
 */
@AndroidEntryPoint
class DiagnosticsFragment : BaseFragment<FragmentDiagnosticsBinding>() {

    private val viewModel: DiagnosticsViewModel by viewModels()
    private lateinit var dtcAdapter: DTCListAdapter

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentDiagnosticsBinding {
        return FragmentDiagnosticsBinding.inflate(inflater, container, false)
    }

    override fun setupUI() {
        // Configurar RecyclerView
        dtcAdapter = DTCListAdapter()
        binding.dtcRecyclerView.adapter = dtcAdapter

        // Configurar botones
        binding.readDtcButton.setOnClickListener {
            viewModel.readDTCs()
        }

        binding.clearDtcButton.setOnClickListener {
            showClearConfirmationDialog()
        }
    }

    override fun observeData() {
        // Observar estado de diagnóstico
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.diagnosticsState.collect { state ->
                updateUIState(state)
            }
        }

        // Observar lista de DTCs
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.dtcList.collect { dtcs ->
                dtcAdapter.submitList(dtcs)
                updateDTCCount(dtcs.size)
                binding.clearDtcButton.isEnabled = dtcs.isNotEmpty()
            }
        }
    }

    private fun updateUIState(state: DiagnosticsState) {
        when (state) {
            is DiagnosticsState.Idle -> {
                showEmptyState()
            }

            is DiagnosticsState.Reading -> {
                showLoading()
                binding.statusText.text = "Leyendo códigos..."
            }

            is DiagnosticsState.Clearing -> {
                showLoading()
                binding.statusText.text = "Limpiando códigos..."
            }

            is DiagnosticsState.NoCodes -> {
                showEmptyState()
                binding.statusText.text = getString(R.string.diagnostics_title)
                binding.dtcCountText.text = getString(R.string.diagnostics_no_codes)
            }

            is DiagnosticsState.CodesAvailable -> {
                showDTCList()
                binding.statusText.text = getString(R.string.diagnostics_title)
                binding.dtcCountText.text = getString(R.string.diagnostics_code_count, state.count)
                binding.dtcCountText.setTextColor(ContextCompat.getColor(requireContext(),R.color.md_theme_light_error))
            }

            is DiagnosticsState.Cleared -> {
                showEmptyState()
                binding.statusText.text = getString(R.string.diagnostics_title)
                binding.dtcCountText.text = "Códigos limpiados exitosamente"
                binding.dtcCountText.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary))
            }

            is DiagnosticsState.Error -> {
                showEmptyState()
                binding.statusText.text = "Error"
                binding.dtcCountText.text = state.message
                binding.dtcCountText.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_error))
            }
        }
    }

    override fun showLoading() {
        binding.loadingIndicator.isVisible = true
        binding.dtcRecyclerView.isVisible = false
        binding.emptyStateLayout.isVisible = false
        binding.readDtcButton.isEnabled = false
        binding.clearDtcButton.isEnabled = false
    }

    private fun showEmptyState() {
        binding.loadingIndicator.isVisible = false
        binding.dtcRecyclerView.isVisible = false
        binding.emptyStateLayout.isVisible = true
        binding.readDtcButton.isEnabled = true
    }

    private fun showDTCList() {
        binding.loadingIndicator.isVisible = false
        binding.dtcRecyclerView.isVisible = true
        binding.emptyStateLayout.isVisible = false
        binding.readDtcButton.isEnabled = true
    }

    private fun updateDTCCount(count: Int) {
        if (count == 0) {
            binding.dtcCountText.text = getString(R.string.diagnostics_no_codes)
            binding.dtcCountText.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_onSurfaceVariant))
        } else {
            binding.dtcCountText.text = getString(R.string.diagnostics_code_count, count)
            binding.dtcCountText.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_error))
        }
    }

    private fun showClearConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Limpiar Códigos")
            .setMessage(getString(R.string.diagnostics_confirm_clear))
            .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                viewModel.clearDTCs()
            }
            .setNegativeButton(getString(R.string.dialog_no), null)
            .show()
    }
}
