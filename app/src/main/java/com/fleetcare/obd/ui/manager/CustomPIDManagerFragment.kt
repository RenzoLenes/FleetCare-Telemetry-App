package com.fleetcare.obd.ui.manager

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fleetcare.obd.databinding.FragmentCustomPidManagerBinding
import com.fleetcare.obd.domain.model.CustomPID
import com.fleetcare.obd.domain.model.PIDCategory
import com.fleetcare.obd.domain.model.PIDSource
import com.fleetcare.obd.ui.common.BaseFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileWriter

/**
 * Fragment para gestionar PIDs personalizados.
 *
 * Sprint 6: Gestión de PIDs Personalizados
 */
@AndroidEntryPoint
class CustomPIDManagerFragment : BaseFragment<FragmentCustomPidManagerBinding>() {

    private val viewModel: CustomPIDManagerViewModel by viewModels()

    // Activity Result Launcher para QR Scanner
    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val qrContent = result.data?.getStringExtra(QRScannerActivity.EXTRA_QR_RESULT)
            qrContent?.let { json ->
                viewModel.importFromJSON(json)
            }
        }
    }

    private val customPIDAdapter = CustomPIDAdapter(
        onItemClick = { pid -> showPIDDetails(pid) },
        onToggleEnabled = { pid -> viewModel.toggleEnabled(pid) },
        onEdit = { pid -> editPID(pid) },
        onShare = { pid -> sharePID(pid) },
        onShareQR = { pid -> sharePIDWithQR(pid) },
        onDelete = { pid -> confirmDeletePID(pid) }
    )

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentCustomPidManagerBinding {
        return FragmentCustomPidManagerBinding.inflate(inflater, container, false)
    }

    override fun setupUI() {
        // RecyclerView
        binding.customPIDsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = customPIDAdapter
        }

        // Search
        binding.searchEditText.doAfterTextChanged { text ->
            viewModel.search(text?.toString() ?: "")
        }

        // Category Filters
        binding.allCategoryChip.setOnClickListener {
            viewModel.filterByCategory(null)
        }
        binding.engineCategoryChip.setOnClickListener {
            viewModel.filterByCategory(PIDCategory.ENGINE)
        }
        binding.fuelCategoryChip.setOnClickListener {
            viewModel.filterByCategory(PIDCategory.FUEL)
        }
        binding.tempCategoryChip.setOnClickListener {
            viewModel.filterByCategory(PIDCategory.TEMPERATURE)
        }

        // Source Filters
        binding.allSourceChip.setOnClickListener {
            viewModel.filterBySource(null)
        }
        binding.userSourceChip.setOnClickListener {
            viewModel.filterBySource(PIDSource.USER)
        }
        binding.autoSourceChip.setOnClickListener {
            viewModel.filterBySource(PIDSource.AUTO_DETECTED)
        }
        binding.importedSourceChip.setOnClickListener {
            viewModel.filterBySource(PIDSource.IMPORTED)
        }

        // Action Buttons
        binding.addPIDButton.setOnClickListener {
            addNewPID()
        }

        binding.addFAB.setOnClickListener {
            addNewPID()
        }

        binding.importButton.setOnClickListener {
            showImportDialog()
        }

        binding.exportButton.setOnClickListener {
            exportPIDs()
        }

        binding.deleteDisabledButton.setOnClickListener {
            confirmDeleteDisabled()
        }
    }

    override fun observeData() {
        // Filtered PIDs
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filteredPIDs.collect { pids ->
                updatePIDsList(pids)
            }
        }

        // Counts
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pidCount.collect { count ->
                binding.totalPIDsText.text = count.toString()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.enabledCount.collect { count ->
                binding.enabledPIDsText.text = count.toString()
            }
        }

        // Loading
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressLayout.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        // Messages
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.errorMessage.collect { error ->
                error?.let {
                    showError(it)
                    viewModel.clearMessages()
                }
            }
        }

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
    private fun updatePIDsList(pids: List<CustomPID>) {
        if (pids.isEmpty()) {
            binding.customPIDsRecyclerView.visibility = View.GONE
            binding.emptyStateLayout.visibility = View.VISIBLE
        } else {
            binding.customPIDsRecyclerView.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE
            customPIDAdapter.submitList(pids)
        }
    }

    /**
     * Muestra detalles completos de un PID.
     */
    private fun showPIDDetails(pid: CustomPID) {
        val details = buildString {
            appendLine("Nombre: ${pid.name}")
            appendLine("PID: ${pid.pid.uppercase()} (${Integer.parseInt(pid.pid, 16)})")
            appendLine("Comando: ${pid.command.uppercase()}")
            appendLine()
            appendLine("Fórmula:")
            appendLine(pid.formula)
            appendLine()
            appendLine("Unidad: ${pid.unit}")
            appendLine("Bytes esperados: ${pid.byteCount}")
            appendLine()
            appendLine("Categoría: ${pid.getCategoryName()}")
            appendLine("Origen: ${pid.getSourceName()}")
            appendLine("Confianza: ${pid.getConfidenceLevel()} (${String.format("%.0f", pid.confidence * 100)}%)")
            appendLine()
            if (pid.vehicleModels.isNotEmpty()) {
                appendLine("Vehículos compatibles:")
                pid.vehicleModels.forEach { vin ->
                    appendLine("  • $vin")
                }
                appendLine()
            }
            if (pid.minValue != null || pid.maxValue != null) {
                appendLine("Rango esperado: ${pid.minValue ?: "?"} - ${pid.maxValue ?: "?"}")
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
            .setNeutralButton("Editar") { _, _ ->
                editPID(pid)
            }
            .show()
    }

    /**
     * Agrega un nuevo PID.
     */
    private fun addNewPID() {
        val dialog = CustomPIDFormDialog.newInstance(
            formulaCandidates = null,
            onSave = {
                viewModel.loadCustomPIDs()
                viewModel.loadCounts()
                showSuccess("PID creado exitosamente")
            }
        )
        dialog.show(childFragmentManager, "CustomPIDFormDialog")
    }

    /**
     * Edita un PID existente.
     */
    private fun editPID(pid: CustomPID) {
        val dialog = CustomPIDFormDialog.editInstance(
            pid = pid,
            onSave = {
                viewModel.loadCustomPIDs()
                viewModel.loadCounts()
                showSuccess("PID actualizado exitosamente")
            }
        )
        dialog.show(childFragmentManager, "CustomPIDFormDialog")
    }

    /**
     * Comparte un PID individual como archivo JSON.
     */
    private fun sharePID(pid: CustomPID) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.exportSinglePID(pid.id)
            result.onSuccess { json ->
                // Guardar en archivo temporal
                val fileName = "pid_${pid.pid}_${System.currentTimeMillis()}.json"
                val file = File(requireContext().cacheDir, fileName)
                FileWriter(file).use { writer ->
                    writer.write(json)
                }

                // Compartir
                shareFile(file, fileName, "application/json")
            }.onFailure { e ->
                showError("Error al compartir PID: ${e.message}")
            }
        }
    }

    /**
     * Comparte un PID individual mediante código QR.
     * Sprint 6.7: QR Code para compartir PIDs
     */
    private fun sharePIDWithQR(pid: CustomPID) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.exportSinglePID(pid.id)
            result.onSuccess { json ->
                // Validar si el JSON puede ser codificado en un QR
                if (!com.fleetcare.obd.utils.QRCodeGenerator.canEncode(json)) {
                    showError("El PID es demasiado grande para un código QR (máx. 2950 caracteres)")
                    return@onSuccess
                }

                // Generar QR code
                val qrResult = com.fleetcare.obd.utils.QRCodeGenerator.generatePIDQRCode(json)

                qrResult.onSuccess { bitmap ->
                    // Mostrar dialog con QR
                    val dialog = QRCodeDialog.newInstance(
                        bitmap = bitmap,
                        title = "Compartir PID ${pid.pid.uppercase()}",
                        description = "Escanea este código QR para importar '${pid.name}' en otro dispositivo",
                        fileName = "pid_${pid.pid}_${System.currentTimeMillis()}"
                    )
                    dialog.show(childFragmentManager, "QRCodeDialog")
                    showSuccess("Código QR generado exitosamente")
                }.onFailure { e ->
                    showError("Error al generar código QR: ${e.message}")
                }
            }.onFailure { e ->
                showError("Error al exportar PID: ${e.message}")
            }
        }
    }

    /**
     * Confirma y elimina un PID.
     */
    private fun confirmDeletePID(pid: CustomPID) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar PID")
            .setMessage("¿Estás seguro de que deseas eliminar '${pid.name}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.deletePID(pid)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Muestra diálogo de importación.
     * Sprint 6.7: Opciones de importación (JSON manual o QR Code)
     */
    private fun showImportDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Importar PIDs")
            .setMessage("Selecciona el método de importación:")
            .setPositiveButton("Escanear QR") { _, _ ->
                launchQRScanner()
            }
            .setNeutralButton("Pegar JSON") { _, _ ->
                showManualImportDialog()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Lanza la actividad de escaneo de QR.
     */
    private fun launchQRScanner() {
        val intent = Intent(requireContext(), QRScannerActivity::class.java)
        qrScannerLauncher.launch(intent)
    }

    /**
     * Muestra diálogo para importación manual de JSON.
     */
    private fun showManualImportDialog() {
        val input = android.widget.EditText(requireContext())
        input.hint = "Pegar JSON aquí..."

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Importar PIDs desde JSON")
            .setMessage("Pega el JSON con los PIDs a importar:")
            .setView(input)
            .setPositiveButton("Importar") { _, _ ->
                val json = input.text.toString()
                if (json.isNotBlank()) {
                    viewModel.importFromJSON(json)
                } else {
                    showError("JSON vacío")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Exporta todos los PIDs.
     */
    private fun exportPIDs() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.exportToJSON()
            result.onSuccess { json ->
                val fileName = "custom_pids_${System.currentTimeMillis()}.json"
                val file = File(requireContext().cacheDir, fileName)
                FileWriter(file).use { writer ->
                    writer.write(json)
                }

                Timber.i("PIDs exportados: ${file.absolutePath}")
                shareFile(file, fileName, "application/json")
            }.onFailure { e ->
                showError("Error al exportar PIDs: ${e.message}")
            }
        }
    }

    /**
     * Confirma y elimina PIDs deshabilitados.
     */
    private fun confirmDeleteDisabled() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Limpiar PIDs Deshabilitados")
            .setMessage("¿Estás seguro de que deseas eliminar todos los PIDs deshabilitados?")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.deleteDisabledPIDs()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Comparte un archivo mediante FileProvider.
     */
    private fun shareFile(file: File, fileName: String, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PIDs Personalizados FleetCare OBD")
            putExtra(Intent.EXTRA_TEXT, "Archivo de PIDs personalizados exportado desde FleetCare OBD")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Compartir PIDs"))
    }

    /**
     * Muestra mensaje de error.
     */
    override fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), com.fleetcare.obd.R.color.md_theme_light_error))
            .show()
    }

    /**
     * Muestra mensaje de éxito.
     */
    private fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), com.fleetcare.obd.R.color.md_theme_light_primary))
            .show()
    }

    /**
     * Muestra mensaje informativo.
     */
    private fun showInfo(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}
