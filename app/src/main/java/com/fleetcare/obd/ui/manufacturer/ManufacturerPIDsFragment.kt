package com.fleetcare.obd.ui.manufacturer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fleetcare.obd.databinding.FragmentManufacturerPidsBinding
import com.fleetcare.obd.domain.model.ManufacturerPID
import com.fleetcare.obd.ui.common.BaseFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Fragment para gestionar PIDs propietarios del fabricante.
 *
 * Sprint 7: Modo 22 y PIDs del Fabricante - Tarea 7.5
 */
@AndroidEntryPoint
class ManufacturerPIDsFragment : BaseFragment<FragmentManufacturerPidsBinding>() {

    private val viewModel: ManufacturerPIDsViewModel by viewModels()

    private val manufacturerPIDAdapter = ManufacturerPIDAdapter(
        onItemClick = { pid -> showPIDDetails(pid) },
        onTest = { pid -> testPID(pid) },
        onSave = { pid -> saveAsCustomPID(pid) }
    )

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentManufacturerPidsBinding {
        return FragmentManufacturerPidsBinding.inflate(inflater, container, false)
    }

    override fun setupUI() {
        // RecyclerView
        binding.manufacturerPidsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = manufacturerPIDAdapter
        }

        // Search
        binding.searchEditText.doAfterTextChanged { text ->
            viewModel.search(text?.toString() ?: "")
        }

        // Detect Vehicle button
        binding.detectVehicleButton.setOnClickListener {
            viewModel.detectVehicle()
        }

        // Clear filters button
        binding.clearFiltersButton.setOnClickListener {
            viewModel.filterByManufacturer(null)
            binding.manufacturerChipGroup.clearCheck()
        }
    }

    override fun observeData() {
        // Filtered PIDs
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filteredPIDs.collect { pids ->
                updatePIDsList(pids)
            }
        }

        // Available Manufacturers
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.availableManufacturers.collect { manufacturers ->
                updateManufacturerChips(manufacturers)
            }
        }

        // Detected VIN
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.detectedVIN.collect { vin ->
                vin?.let {
                    binding.detectedVinText.text = "VIN: $it"
                    binding.detectedVinText.visibility = View.VISIBLE
                }
            }
        }

        // Detected Manufacturer
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.detectedManufacturer.collect { manufacturer ->
                manufacturer?.let {
                    binding.detectedManufacturerText.text = "Fabricante: $it"
                    binding.detectedManufacturerText.visibility = View.VISIBLE
                }
            }
        }

        // Loading
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressLayout.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        // Testing
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isTesting.collect { isTesting ->
                binding.testProgressBar.visibility = if (isTesting) View.VISIBLE else View.GONE
            }
        }

        // Test Results
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.testResults.collect { results ->
                // Los resultados se muestran en el adapter
                manufacturerPIDAdapter.updateTestResults(results)
            }
        }

        // Stats
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.stats.collect { stats ->
                binding.totalPidsText.text = stats.totalPIDs.toString()
                binding.manufacturersCountText.text = stats.manufacturersCount.toString()
            }
        }

        // Error messages
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.errorMessage.collect { error ->
                error?.let {
                    showError(it)
                    viewModel.clearMessages()
                }
            }
        }

        // Success messages
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.successMessage.collect { success ->
                success?.let {
                    showSuccess(it)
                    viewModel.clearMessages()
                }
            }
        }
    }

    /**
     * Actualiza la lista de PIDs.
     */
    private fun updatePIDsList(pids: List<ManufacturerPID>) {
        if (pids.isEmpty()) {
            binding.manufacturerPidsRecyclerView.visibility = View.GONE
            binding.emptyStateLayout.visibility = View.VISIBLE
        } else {
            binding.manufacturerPidsRecyclerView.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE
            manufacturerPIDAdapter.submitList(pids)
        }
    }

    /**
     * Actualiza los chips de fabricantes.
     */
    private fun updateManufacturerChips(manufacturers: List<String>) {
        binding.manufacturerChipGroup.removeAllViews()

        manufacturers.forEach { manufacturer ->
            val chip = com.google.android.material.chip.Chip(requireContext())
            chip.text = manufacturer
            chip.isCheckable = true
            chip.setOnClickListener {
                viewModel.filterByManufacturer(manufacturer)
            }
            binding.manufacturerChipGroup.addView(chip)
        }
    }

    /**
     * Muestra detalles completos de un PID.
     */
    private fun showPIDDetails(pid: ManufacturerPID) {
        val details = buildString {
            appendLine("PID: ${pid.pid.uppercase()}")
            appendLine("Fabricante: ${pid.manufacturer}")
            appendLine()
            appendLine("Nombre: ${pid.name}")
            appendLine("Descripción: ${pid.description}")
            appendLine()
            appendLine("Comando: ${pid.buildCommand()}")
            appendLine("Tipo de dato: ${pid.dataType}")
            appendLine("Fórmula: ${pid.formula}")
            appendLine("Unidad: ${pid.unit}")
            appendLine("Bytes esperados: ${pid.byteCount}")
            appendLine()
            if (pid.minValue != null || pid.maxValue != null) {
                appendLine("Rango: ${pid.minValue ?: "?"} - ${pid.maxValue ?: "?"}")
                appendLine()
            }
            if (pid.applicableModels.isNotEmpty()) {
                appendLine("Modelos compatibles:")
                pid.applicableModels.forEach { model ->
                    appendLine("  • $model")
                }
                appendLine()
            }
            if (pid.notes.isNotBlank()) {
                appendLine("Notas:")
                appendLine(pid.notes)
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Detalles PID ${pid.pid.uppercase()}")
            .setMessage(details.trim())
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Probar") { _, _ ->
                testPID(pid)
            }
            .setNegativeButton("Guardar") { _, _ ->
                saveAsCustomPID(pid)
            }
            .show()
    }

    /**
     * Prueba un PID individual.
     */
    private fun testPID(pid: ManufacturerPID) {
        Timber.d("Probando PID: ${pid.pid} (${pid.name})")
        viewModel.testPID(pid)
    }

    /**
     * Guarda un PID del fabricante como PID personalizado.
     */
    private fun saveAsCustomPID(pid: ManufacturerPID) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Guardar como PID Personalizado")
            .setMessage("¿Deseas guardar '${pid.name}' como un PID personalizado?\n\nEsto te permitirá usarlo en lecturas continuas y personalizarlo.")
            .setPositiveButton("Guardar") { _, _ ->
                viewModel.saveAsCustomPID(pid)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Muestra mensaje de error.
     */
    override fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(resources.getColor(com.fleetcare.obd.R.color.md_theme_light_error, null))
            .show()
    }

    /**
     * Muestra mensaje de éxito.
     */
    private fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(resources.getColor(com.fleetcare.obd.R.color.md_theme_light_primary, null))
            .show()
    }

    /**
     * Muestra mensaje informativo.
     */
    private fun showInfo(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}
