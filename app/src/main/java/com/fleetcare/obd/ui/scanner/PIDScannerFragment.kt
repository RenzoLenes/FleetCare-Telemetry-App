package com.fleetcare.obd.ui.scanner

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fleetcare.obd.databinding.FragmentPidScannerBinding
import com.fleetcare.obd.domain.model.ExportFormat
import com.fleetcare.obd.domain.model.ScanFilter
import com.fleetcare.obd.domain.model.ScanResult
import com.fleetcare.obd.domain.model.ScannerState
import com.fleetcare.obd.ui.common.BaseFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileWriter

/**
 * Fragment para escanear todos los PIDs del modo 01.
 *
 * Sprint 5: Escáner de PIDs Completo
 *
 * Características:
 * - Escaneo de 255 PIDs con progreso en tiempo real
 * - Filtrado por éxito/fallo
 * - Exportación a JSON/CSV
 * - Visualización de resultados con RecyclerView
 * - Controles de inicio/pausa/cancelar
 */
@AndroidEntryPoint
class PIDScannerFragment : BaseFragment<FragmentPidScannerBinding>() {

    private val viewModel: PIDScannerViewModel by viewModels()

    private val scanResultAdapter = ScanResultAdapter { result ->
        showResultDetails(result)
    }

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentPidScannerBinding {
        return FragmentPidScannerBinding.inflate(inflater, container, false)
    }

    override fun setupUI() {
        // Configurar RecyclerView
        binding.resultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = scanResultAdapter
        }

        // Botones de control
        binding.startScanButton.setOnClickListener {
            viewModel.startScan()
        }

        binding.pauseScanButton.setOnClickListener {
            viewModel.pauseScan()
        }

        binding.cancelScanButton.setOnClickListener {
            viewModel.cancelScan()
        }

        // Filtros
        binding.filterAllChip.setOnClickListener {
            viewModel.setFilter(ScanFilter.ALL)
        }

        binding.filterSuccessChip.setOnClickListener {
            viewModel.setFilter(ScanFilter.SUCCESS_ONLY)
        }

        binding.filterFailedChip.setOnClickListener {
            viewModel.setFilter(ScanFilter.FAILED_ONLY)
        }

        // Exportar
        binding.exportButton.setOnClickListener {
            showExportDialog()
        }
    }

    override fun observeData() {
        // Observar estado del escáner
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scannerState.collect { state ->
                updateUIForState(state)
            }
        }

        // Observar progreso
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scanProgress.collect { progress ->
                progress?.let { updateProgress(it) }
            }
        }

        // Observar resultados filtrados
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filteredResults.collect { results ->
                updateResults(results)
            }
        }

        // Observar errores
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.errorMessage.collect { error ->
                error?.let {
                    showError(it)
                    viewModel.clearError()
                }
            }
        }
    }

    /**
     * Actualiza la UI según el estado del escáner.
     */
    private fun updateUIForState(state: ScannerState) {
        when (state) {
            ScannerState.IDLE -> {
                binding.startScanButton.isEnabled = true
                binding.startScanButton.text = "Iniciar Escaneo"
                binding.pauseScanButton.isEnabled = false
                binding.cancelScanButton.isEnabled = false
                binding.progressCard.visibility = View.GONE
                binding.filtersLayout.visibility = if (viewModel.scanResults.value.isNotEmpty()) View.VISIBLE else View.GONE
            }
            ScannerState.SCANNING -> {
                binding.startScanButton.isEnabled = false
                binding.pauseScanButton.isEnabled = true
                binding.cancelScanButton.isEnabled = true
                binding.progressCard.visibility = View.VISIBLE
                binding.filtersLayout.visibility = View.VISIBLE
                binding.emptyStateLayout.visibility = View.GONE
            }
            ScannerState.PAUSED -> {
                binding.startScanButton.isEnabled = true
                binding.startScanButton.text = "Reanudar"
                binding.pauseScanButton.isEnabled = false
                binding.cancelScanButton.isEnabled = true
            }
            ScannerState.COMPLETED -> {
                binding.startScanButton.isEnabled = true
                binding.startScanButton.text = "Nuevo Escaneo"
                binding.pauseScanButton.isEnabled = false
                binding.cancelScanButton.isEnabled = false
                showScanCompletedDialog()
            }
            ScannerState.ERROR -> {
                binding.startScanButton.isEnabled = true
                binding.startScanButton.text = "Reintentar"
                binding.pauseScanButton.isEnabled = false
                binding.cancelScanButton.isEnabled = false
            }
        }
    }

    /**
     * Actualiza la barra de progreso y estadísticas.
     */
    private fun updateProgress(progress: com.fleetcare.obd.domain.model.ScanProgress) {
        binding.progressText.text = "Progreso: ${progress.getProgressText()}"
        binding.progressPercentText.text = "${progress.getProgressPercent()}%"
        binding.progressBar.progress = progress.currentPID
        binding.successCountText.text = progress.successCount.toString()
        binding.failedCountText.text = progress.failedCount.toString()
        binding.elapsedTimeText.text = progress.getElapsedTimeFormatted()
        binding.estimatedTimeText.text = "Tiempo estimado restante: ${progress.getEstimatedTimeFormatted()}"
    }

    /**
     * Actualiza la lista de resultados.
     */
    private fun updateResults(results: List<ScanResult>) {
        if (results.isEmpty()) {
            binding.resultsRecyclerView.visibility = View.GONE
            if (viewModel.scannerState.value == ScannerState.IDLE) {
                binding.emptyStateLayout.visibility = View.VISIBLE
            }
        } else {
            binding.resultsRecyclerView.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE
            scanResultAdapter.submitList(results)

            // Auto-scroll al último resultado
            if (viewModel.scannerState.value == ScannerState.SCANNING) {
                binding.resultsRecyclerView.smoothScrollToPosition(results.size - 1)
            }
        }
    }

    /**
     * Muestra detalles completos de un resultado.
     */
    private fun showResultDetails(result: ScanResult) {
        val details = buildString {
            appendLine("PID: ${result.pid} (${result.getPIDDecimal()})")
            appendLine("Comando: ${result.command}")
            appendLine("Estado: ${if (result.success) "✓ Éxito" else "✗ Fallo"}")
            appendLine()
            appendLine("Respuesta RAW:")
            appendLine(result.rawResponse)
            appendLine()
            if (result.success) {
                appendLine("Bytes de datos: ${result.byteCount}")
                if (result.dataBytes.isNotEmpty()) {
                    val hexBytes = result.dataBytes.joinToString(" ") { "%02X".format(it.toUByte().toInt()) }
                    appendLine("Hex: $hexBytes")
                }
                appendLine()
                if (result.interpretation != null) {
                    appendLine("Interpretación:")
                    appendLine(result.interpretation)
                    appendLine()
                }
                result.detectedType?.let {
                    appendLine("Tipo detectado: ${it.name}")
                }
            }
            appendLine()
            appendLine("Categoría: ${result.getCategory()}")
            appendLine("Latencia: ${result.latencyMs}ms")
            appendLine("PID Estándar: ${if (result.isStandardPID) "Sí" else "No (Propietario)"}")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Detalles PID ${result.pid}")
            .setMessage(details.trim())
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Muestra diálogo de escaneo completado.
     */
    private fun showScanCompletedDialog() {
        val stats = viewModel.getStatistics()
        val message = buildString {
            appendLine("Escaneo completado exitosamente")
            appendLine()
            appendLine("Total escaneado: ${stats["total"]}")
            appendLine("Exitosos: ${stats["successful"]}")
            appendLine("Fallidos: ${stats["failed"]}")
            appendLine()
            appendLine("PIDs estándar: ${stats["standardPIDs"]}")
            appendLine("PIDs propietarios: ${stats["proprietaryPIDs"]}")
            appendLine()
            val avgLatency = stats["avgLatency"] as? Double ?: 0.0
            appendLine("Latencia promedio: ${String.format("%.0f", avgLatency)}ms")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🎉 Escaneo Completado")
            .setMessage(message.trim())
            .setPositiveButton("OK", null)
            .setNeutralButton("Exportar") { _, _ ->
                showExportDialog()
            }
            .show()
    }

    /**
     * Muestra diálogo para seleccionar formato de exportación.
     */
    private fun showExportDialog() {
        val formats = arrayOf("JSON", "CSV")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Exportar Resultados")
            .setItems(formats) { _, which ->
                val format = if (which == 0) ExportFormat.JSON else ExportFormat.CSV
                exportResults(format)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Exporta los resultados al formato especificado.
     */
    private fun exportResults(format: ExportFormat) {
        try {
            val content = viewModel.exportResults(
                format = format,
                vehicleId = "UNKNOWN", // TODO: Obtener del sistema
                vin = null // TODO: Obtener del sistema
            )

            val extension = if (format == ExportFormat.JSON) "json" else "csv"
            val fileName = "pid_scan_${System.currentTimeMillis()}.$extension"

            // Guardar archivo temporalmente
            val file = File(requireContext().cacheDir, fileName)
            FileWriter(file).use { writer ->
                writer.write(content)
            }

            Timber.i("Archivo exportado: ${file.absolutePath}")

            // Compartir archivo
            shareFile(file, fileName, format)

        } catch (e: Exception) {
            Timber.e(e, "Error al exportar resultados")
            showError("Error al exportar: ${e.message}")
        }
    }

    /**
     * Comparte el archivo exportado.
     */
    private fun shareFile(file: File, fileName: String, format: ExportFormat) {
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )

        val mimeType = if (format == ExportFormat.JSON) "application/json" else "text/csv"

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Resultados Escaneo PIDs")
            putExtra(Intent.EXTRA_TEXT, "Resultados del escaneo de PIDs OBD-II")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Compartir resultados"))
    }

    /**
     * Muestra un mensaje de error.
     */
    override fun showError(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
