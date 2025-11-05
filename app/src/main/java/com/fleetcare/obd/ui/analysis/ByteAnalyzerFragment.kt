package com.fleetcare.obd.ui.analysis

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fleetcare.obd.R
import com.fleetcare.obd.databinding.FragmentByteAnalyzerBinding
import com.fleetcare.obd.domain.model.FormulaCandidate
import com.fleetcare.obd.ui.common.BaseFragment
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fragment para análisis visual de bytes RAW de PIDs.
 *
 * Sprint 4: UI de Análisis de Bytes
 *
 * Características:
 * - Selector de PID con comandos disponibles
 * - Visualización hex de bytes con color coding
 * - Análisis estadístico y detección de patrones
 * - Fórmulas candidatas ranqueadas
 * - Editor de fórmulas personalizadas con preview
 * - Gráfico temporal interactivo (MPAndroidChart)
 */
@AndroidEntryPoint
class ByteAnalyzerFragment : BaseFragment<FragmentByteAnalyzerBinding>() {

    private val viewModel: ByteAnalyzerViewModel by viewModels()

    private val byteListAdapter = ByteListAdapter { byteItem ->
        showByteDetails(byteItem)
    }

    private val formulaAdapter = FormulaCandidateAdapter { formula ->
        useFormula(formula)
    }

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentByteAnalyzerBinding {
        return FragmentByteAnalyzerBinding.inflate(inflater, container, false)
    }

    override fun setupUI() {
        // Configurar RecyclerView de bytes
        binding.bytesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = byteListAdapter
        }

        // Configurar RecyclerView de fórmulas
        binding.formulasRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = formulaAdapter
        }

        // Botón analizar
        binding.analyzeButton.setOnClickListener {
            viewModel.analyzePattern()
        }

        // Botones del editor
        binding.testFormulaButton.setOnClickListener {
            val formula = binding.customFormulaInput.text.toString()
            if (formula.isNotBlank()) {
                viewModel.testCustomFormula(formula)
            }
        }

        binding.saveFormulaButton.setOnClickListener {
            // TODO: Implementar guardado de fórmula personalizada (Sprint 6)
            showMessage("Guardar fórmulas personalizadas se implementará en Sprint 6")
        }

        // Listener de cambios en input de fórmula
        binding.customFormulaInput.doOnTextChanged { text, _, _, _ ->
            binding.saveFormulaButton.isEnabled = !text.isNullOrBlank()
        }

        // Configurar gráfico temporal
        setupChart()
    }

    override fun observeData() {
        // Observar comandos disponibles
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.availableCommands.collect { commands ->
                updateCommandsSpinner(commands)
            }
        }

        // Observar comando seleccionado
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedCommand.collect { command ->
                // Actualizar UI según comando
            }
        }

        // Observar respuestas RAW
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.rawResponses.collect { responses ->
                updateRawResponsesUI(responses)
            }
        }

        // Observar patrón analizado
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pattern.collect { pattern ->
                updatePatternUI(pattern)
            }
        }

        // Observar fórmulas candidatas
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.formulaCandidates.collect { formulas ->
                updateFormulasUI(formulas)
            }
        }

        // Observar estado de análisis
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isAnalyzing.collect { isAnalyzing ->
                binding.analysisProgress.visibility = if (isAnalyzing) View.VISIBLE else View.GONE
                binding.analyzeButton.isEnabled = !isAnalyzing
            }
        }

        // Observar quick stats
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.quickStats.collect { stats ->
                updateQuickStats(stats)
            }
        }

        // Observar preview de fórmula personalizada
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.customFormulaPreview.collect { preview ->
                updateFormulaPreview(preview)
            }
        }
    }

    /**
     * Actualiza el spinner de comandos.
     */
    private fun updateCommandsSpinner(commands: List<String>) {
        if (commands.isEmpty()) {
            // Mostrar mensaje de empty state
            showMessage("No hay datos de escaneo disponibles. Realiza un escaneo de PIDs primero en el Universal Scanner para generar datos para el analizador.")
            return
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            commands
        )

        binding.commandSpinner.setAdapter(adapter)
        binding.commandSpinner.setOnItemClickListener { _, _, position, _ ->
            viewModel.selectCommand(commands[position])
        }

        // Seleccionar el primero por defecto
        if (binding.commandSpinner.text.isEmpty() && commands.isNotEmpty()) {
            binding.commandSpinner.setText(commands.first(), false)
        }
    }

    /**
     * Actualiza la UI con respuestas RAW.
     */
    private fun updateRawResponsesUI(responses: List<com.fleetcare.obd.domain.model.RawOBDResponse>) {
        if (responses.isEmpty()) {
            binding.rawResponseCard.visibility = View.GONE
            return
        }

        binding.rawResponseCard.visibility = View.VISIBLE

        // Mostrar última respuesta
        val lastResponse = responses.first()
        val hexString = lastResponse.dataBytes.joinToString(" ") { byte ->
            "%02X".format(byte.toUByte().toInt())
        }
        binding.rawResponseText.text = hexString

        // Actualizar lista de bytes
        val pattern = viewModel.pattern.value
        val byteItems = lastResponse.dataBytes.mapIndexed { index, byte ->
            val type = when {
                pattern?.staticByteIndices?.contains(index) == true -> ByteType.STATIC
                pattern?.dynamicByteIndices?.contains(index) == true -> ByteType.DYNAMIC
                else -> ByteType.STATIC
            }

            ByteDisplayItem(
                index = index,
                value = byte.toUByte().toInt(),
                type = type,
                isSelected = viewModel.selectedByteIndex.value == index,
                statistic = pattern?.byteStatistics?.getOrNull(index)
            )
        }

        byteListAdapter.submitList(byteItems)
    }

    /**
     * Actualiza la UI con el patrón analizado.
     */
    private fun updatePatternUI(pattern: com.fleetcare.obd.domain.model.PIDPattern?) {
        if (pattern == null) {
            binding.analysisCard.visibility = View.GONE
            return
        }

        binding.analysisCard.visibility = View.VISIBLE

        // Tipo detectado
        binding.detectedTypeText.text = pattern.detectedType.name

        // Bytes dinámicos
        binding.dynamicBytesText.text = pattern.dynamicByteIndices.size.toString()

        // Confianza
        val confidencePercent = (pattern.confidence * 100).toInt()
        binding.confidenceText.text = "$confidencePercent%"

        // Actualizar gráfico temporal
        updateChart(pattern)

        // Actualizar también los bytes con el patrón
        val responses = viewModel.rawResponses.value
        if (responses.isNotEmpty()) {
            updateRawResponsesUI(responses)
        }
    }

    /**
     * Actualiza la UI con fórmulas candidatas.
     */
    private fun updateFormulasUI(formulas: List<FormulaCandidate>) {
        if (formulas.isEmpty()) {
            binding.formulasCard.visibility = View.GONE
            return
        }

        binding.formulasCard.visibility = View.VISIBLE
        binding.formulasCountText.text = "${formulas.size} fórmulas encontradas, ranqueadas por precisión"

        formulaAdapter.submitList(formulas)

        // Mostrar también el editor
        binding.customFormulaCard.visibility = View.VISIBLE
    }

    /**
     * Actualiza estadísticas rápidas.
     */
    private fun updateQuickStats(stats: Map<String, Any>?) {
        if (stats == null) {
            binding.quickStatsLayout.visibility = View.GONE
            return
        }

        binding.quickStatsLayout.visibility = View.VISIBLE

        val samplesCount = stats["successfulSamples"] as? Int ?: 0
        binding.samplesCountText.text = samplesCount.toString()
    }

    /**
     * Actualiza el preview de fórmula personalizada.
     */
    private fun updateFormulaPreview(preview: FormulaPreviewResult?) {
        if (preview == null) {
            binding.previewLayout.visibility = View.GONE
            return
        }

        binding.previewLayout.visibility = View.VISIBLE

        val previewText = buildString {
            if (preview.isValid) {
                preview.results.take(5).forEach { result ->
                    val bytesHex = result.bytes.joinToString(" ") { "%02X".format(it.toUByte().toInt()) }
                    val resultValue = result.result?.let { "%.2f".format(it) } ?: "ERROR"
                    appendLine("[$bytesHex] → $resultValue")
                }
            } else {
                append("Error: ${preview.error ?: "Fórmula inválida"}")
            }
        }

        binding.previewResultsText.text = previewText.trim()
    }

    /**
     * Configura el gráfico temporal con estilos y comportamiento.
     */
    private fun setupChart() {
        binding.byteTimeSeriesChart.apply {
            // Descripción
            description.isEnabled = false

            // Habilitar zoom y pan
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            // Eje X (tiempo)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = ContextCompat.getColor(requireContext(), R.color.md_theme_light_onSurface)
                setDrawGridLines(true)
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    override fun getFormattedValue(value: Float): String {
                        return dateFormat.format(Date(value.toLong()))
                    }
                }
            }

            // Eje Y izquierdo (valor del byte)
            axisLeft.apply {
                textColor = ContextCompat.getColor(requireContext(), R.color.md_theme_light_onSurface)
                setDrawGridLines(true)
                axisMinimum = 0f
                axisMaximum = 255f
            }

            // Eje Y derecho (deshabilitado)
            axisRight.isEnabled = false

            // Leyenda
            legend.apply {
                textColor = ContextCompat.getColor(requireContext(), R.color.md_theme_light_onSurface)
                verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.RIGHT
                orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.VERTICAL
                setDrawInside(true)
            }

            // Inicialmente sin datos
            setNoDataText("Selecciona un comando y analiza para ver el gráfico temporal")
            setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_onSurfaceVariant))
        }
    }

    /**
     * Actualiza el gráfico con datos de series temporales de bytes.
     */
    private fun updateChart(pattern: com.fleetcare.obd.domain.model.PIDPattern?) {
        if (pattern == null || pattern.dynamicByteIndices.isEmpty()) {
            binding.byteTimeSeriesChart.clear()
            return
        }

        val dataSets = mutableListOf<LineDataSet>()
        val colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary),
            ContextCompat.getColor(requireContext(), R.color.md_theme_light_secondary),
            ContextCompat.getColor(requireContext(), R.color.md_theme_light_tertiary),
            Color.parseColor("#FF6F00"), // Orange
            Color.parseColor("#C51162"), // Pink
            Color.parseColor("#00695C"), // Teal
            Color.parseColor("#4A148C"), // Purple
            Color.parseColor("#BF360C")  // Deep Orange
        )

        // Crear una línea por cada byte dinámico
        pattern.dynamicByteIndices.take(8).forEachIndexed { index, byteIndex ->
            val timeSeries = viewModel.getByteTimeSeries(byteIndex)

            if (timeSeries.isNotEmpty()) {
                val entries = timeSeries.map { (timestamp, value) ->
                    Entry(timestamp.toFloat(), value.toFloat())
                }

                val dataSet = LineDataSet(entries, "Byte [$byteIndex]").apply {
                    color = colors[index % colors.size]
                    setCircleColor(colors[index % colors.size])
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.LINEAR
                }

                dataSets.add(dataSet)
            }
        }

        if (dataSets.isNotEmpty()) {
            val lineData = LineData(dataSets.toList())
            binding.byteTimeSeriesChart.data = lineData
            binding.byteTimeSeriesChart.invalidate() // Refresh
        } else {
            binding.byteTimeSeriesChart.clear()
        }
    }

    /**
     * Muestra detalles completos de un byte.
     */
    private fun showByteDetails(byteItem: ByteDisplayItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Detalles del Byte [${byteItem.index}]")
            .setMessage(byteItem.toDetailString())
            .setPositiveButton("OK", null)
            .setNeutralButton("Ver Serie Temporal") { _, _ ->
                // Seleccionar el byte y actualizar el gráfico para destacarlo
                viewModel.selectByteIndex(byteItem.index)
                showByteTimeSeriesChart(byteItem.index)
            }
            .show()
    }

    /**
     * Muestra el gráfico enfocado en un byte específico.
     */
    private fun showByteTimeSeriesChart(byteIndex: Int) {
        val timeSeries = viewModel.getByteTimeSeries(byteIndex)

        if (timeSeries.isEmpty()) {
            showMessage("No hay suficientes datos para graficar el byte [$byteIndex]")
            return
        }

        // Scroll al gráfico
        binding.root.post {
            binding.root.smoothScrollTo(0, binding.byteTimeSeriesChart.top)
        }

        // El gráfico ya se actualiza automáticamente cuando hay un patrón
        // Aquí solo mostramos un mensaje de confirmación
        showMessage("Mostrando serie temporal del Byte [$byteIndex]")
    }

    /**
     * Usa una fórmula seleccionada.
     */
    private fun useFormula(formula: FormulaCandidate) {
        // Copiar la expresión al editor
        binding.customFormulaInput.setText(formula.formulaExpression)

        // Testear automáticamente
        viewModel.testCustomFormula(formula.formulaExpression)

        // Scroll al editor
        binding.root.post {
            binding.root.smoothScrollTo(0, binding.customFormulaCard.bottom)
        }

        showMessage("Fórmula copiada al editor: ${formula.name}")
    }

    private fun showMessage(message: String) {
        // TODO: Usar Snackbar cuando esté disponible
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
