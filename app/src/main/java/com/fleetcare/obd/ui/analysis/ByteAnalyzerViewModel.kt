package com.fleetcare.obd.ui.analysis

import androidx.lifecycle.viewModelScope
import com.fleetcare.obd.domain.model.FormulaCandidate
import com.fleetcare.obd.domain.model.PIDPattern
import com.fleetcare.obd.domain.model.RawOBDResponse
import com.fleetcare.obd.domain.usecase.AnalyzePIDPatternsUseCase
import com.fleetcare.obd.domain.repository.RawOBDResponseRepository
import com.fleetcare.obd.ui.common.BaseViewModel
import com.fleetcare.obd.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para el analizador de bytes.
 *
 * Sprint 4: UI de Análisis de Bytes
 *
 * Gestiona:
 * - Selección de PID a analizar
 * - Carga de respuestas RAW
 * - Ejecución de análisis de patrones
 * - Visualización de fórmulas candidatas
 * - Preview de fórmulas personalizadas
 * - Historial de bytes para gráfico temporal
 */
@HiltViewModel
class ByteAnalyzerViewModel @Inject constructor(
    private val rawOBDResponseRepository: RawOBDResponseRepository,
    private val analyzePIDPatternsUseCase: AnalyzePIDPatternsUseCase
) : BaseViewModel() {

    companion object {
        private const val TAG = "ByteAnalyzerViewModel"
    }

    /**
     * PID/comando seleccionado actualmente para analizar.
     */
    private val _selectedCommand = MutableStateFlow<String?>(null)
    val selectedCommand: StateFlow<String?> = _selectedCommand.asStateFlow()

    /**
     * Lista de comandos disponibles (con respuestas RAW).
     */
    private val _availableCommands = MutableStateFlow<List<String>>(emptyList())
    val availableCommands: StateFlow<List<String>> = _availableCommands.asStateFlow()

    /**
     * Respuestas RAW del comando seleccionado.
     */
    private val _rawResponses = MutableStateFlow<List<RawOBDResponse>>(emptyList())
    val rawResponses: StateFlow<List<RawOBDResponse>> = _rawResponses.asStateFlow()

    /**
     * Patrón analizado del comando actual.
     */
    private val _pattern = MutableStateFlow<PIDPattern?>(null)
    val pattern: StateFlow<PIDPattern?> = _pattern.asStateFlow()

    /**
     * Fórmulas candidatas ranqueadas.
     */
    private val _formulaCandidates = MutableStateFlow<List<FormulaCandidate>>(emptyList())
    val formulaCandidates: StateFlow<List<FormulaCandidate>> = _formulaCandidates.asStateFlow()

    /**
     * Fórmula personalizada ingresada por el usuario.
     */
    private val _customFormula = MutableStateFlow("")
    val customFormula: StateFlow<String> = _customFormula.asStateFlow()

    /**
     * Resultado del preview de fórmula personalizada.
     */
    private val _customFormulaPreview = MutableStateFlow<FormulaPreviewResult?>(null)
    val customFormulaPreview: StateFlow<FormulaPreviewResult?> = _customFormulaPreview.asStateFlow()

    /**
     * Indica si se está ejecutando un análisis.
     */
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    /**
     * Estadísticas rápidas del comando seleccionado.
     */
    private val _quickStats = MutableStateFlow<Map<String, Any>?>(null)
    val quickStats: StateFlow<Map<String, Any>?> = _quickStats.asStateFlow()

    /**
     * Byte seleccionado para visualización detallada.
     */
    private val _selectedByteIndex = MutableStateFlow<Int?>(null)
    val selectedByteIndex: StateFlow<Int?> = _selectedByteIndex.asStateFlow()

    init {
        loadAvailableCommands()
    }

    /**
     * Carga la lista de comandos que tienen respuestas RAW disponibles.
     */
    fun loadAvailableCommands() {
        viewModelScope.launch {
            Logger.d(TAG, "Cargando comandos disponibles...")

            rawOBDResponseRepository.getAllCommands()
                .catch { error ->
                    Logger.e(error, "Error cargando comandos disponibles")
                    emitError("Error cargando comandos: ${error.message}")
                }
                .collect { commands ->
                    _availableCommands.value = commands.sorted()
                    Logger.d(TAG, "Comandos disponibles: ${commands.size}")

                    if (commands.isNotEmpty() && _selectedCommand.value == null) {
                        // Seleccionar el primer comando por defecto
                        selectCommand(commands.first())
                    }
                }
        }
    }

    /**
     * Selecciona un comando para analizar.
     */
    fun selectCommand(command: String) {
        Logger.d(TAG, "Comando seleccionado: $command")
        _selectedCommand.value = command
        _pattern.value = null
        _formulaCandidates.value = emptyList()
        _customFormula.value = ""
        _customFormulaPreview.value = null
        _selectedByteIndex.value = null

        loadResponsesForCommand(command)
        loadQuickStats(command)
    }

    /**
     * Carga las respuestas RAW del comando seleccionado.
     */
    private fun loadResponsesForCommand(command: String) {
        launchWithLoading(showLoading = false) {
            Logger.d(TAG, "Cargando respuestas para comando: $command")

            val result = rawOBDResponseRepository.getLatestResponses(
                command = command,
                limit = 100
            )

            result.onSuccess { responses ->
                val successfulResponses = responses.filter { it.parseSuccess }
                _rawResponses.value = successfulResponses
                Logger.d(TAG, "Respuestas cargadas: ${successfulResponses.size}")
            }.onFailure { error ->
                Logger.e(error, "Error cargando respuestas")
                emitError("Error cargando respuestas: ${error.message}")
            }
        }
    }

    /**
     * Carga estadísticas rápidas del comando.
     */
    private fun loadQuickStats(command: String) {
        viewModelScope.launch {
            val result = analyzePIDPatternsUseCase.getQuickStats(command)

            result.onSuccess { stats ->
                _quickStats.value = stats
                Logger.d(TAG, "Quick stats cargadas: $stats")
            }.onFailure { error ->
                Logger.w(TAG, "Error cargando quick stats: ${error.message}")
            }
        }
    }

    /**
     * Ejecuta el análisis completo del comando seleccionado.
     */
    fun analyzePattern() {
        val command = _selectedCommand.value
        if (command == null) {
            emitError("No hay comando seleccionado")
            return
        }

        launchWithLoading {
            _isAnalyzing.value = true
            Logger.d(TAG, "Iniciando análisis de patrón para: $command")

            val result = analyzePIDPatternsUseCase.execute(
                command = command,
                maxSamples = 100
            )

            result.onSuccess { analyzedPattern ->
                _pattern.value = analyzedPattern
                _formulaCandidates.value = analyzedPattern.suggestedFormulas

                Logger.d(TAG, "Análisis completado: ${analyzedPattern.suggestedFormulas.size} fórmulas encontradas")
                Logger.d(TAG, analyzedPattern.toSummary())

                emitSuccess("Análisis completado: ${analyzedPattern.suggestedFormulas.size} fórmulas encontradas")
            }.onFailure { error ->
                Logger.e(error, "Error en análisis")
                emitError("Error en análisis: ${error.message}")
            }

            _isAnalyzing.value = false
        }
    }

    /**
     * Establece la fórmula personalizada ingresada por el usuario.
     */
    fun setCustomFormula(formula: String) {
        _customFormula.value = formula
    }

    /**
     * Testea una fórmula personalizada con las respuestas actuales.
     */
    fun testCustomFormula(formulaExpression: String) {
        val responses = _rawResponses.value
        if (responses.isEmpty()) {
            emitError("No hay respuestas para testear la fórmula")
            return
        }

        viewModelScope.launch {
            Logger.d(TAG, "Testeando fórmula personalizada: $formulaExpression")

            try {
                // Parsear y evaluar la fórmula
                val results = responses.take(10).map { response ->
                    val bytes = response.dataBytes
                    val result = evaluateFormula(formulaExpression, bytes)
                    FormulaTestResult(
                        bytes = bytes,
                        result = result,
                        timestamp = response.timestamp
                    )
                }

                val successCount = results.count { it.result != null }
                val errorCount = results.size - successCount

                _customFormulaPreview.value = FormulaPreviewResult(
                    formula = formulaExpression,
                    results = results,
                    successCount = successCount,
                    errorCount = errorCount,
                    isValid = successCount > 0
                )

                Logger.d(TAG, "Preview: $successCount exitosos, $errorCount fallidos")
            } catch (e: Exception) {
                Logger.e(e, "Error evaluando fórmula personalizada")
                _customFormulaPreview.value = FormulaPreviewResult(
                    formula = formulaExpression,
                    results = emptyList(),
                    successCount = 0,
                    errorCount = responses.size,
                    isValid = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * Evalúa una expresión de fórmula con bytes dados.
     *
     * Soporta: A, B, C, D para bytes[0-3]
     */
    private fun evaluateFormula(expression: String, bytes: ByteArray): Double? {
        if (bytes.isEmpty()) return null

        try {
            var expr = expression

            // Reemplazar variables con valores
            if (bytes.size > 0) {
                val a = bytes[0].toUByte().toInt()
                expr = expr.replace("A", a.toString())
            }
            if (bytes.size > 1) {
                val b = bytes[1].toUByte().toInt()
                expr = expr.replace("B", b.toString())
            }
            if (bytes.size > 2) {
                val c = bytes[2].toUByte().toInt()
                expr = expr.replace("C", c.toString())
            }
            if (bytes.size > 3) {
                val d = bytes[3].toUByte().toInt()
                expr = expr.replace("D", d.toString())
            }

            // Evaluación simple (en producción usar librería como exp4j)
            // Por ahora solo soportamos expresiones básicas
            return evaluateSimpleExpression(expr)
        } catch (e: Exception) {
            Logger.w(TAG, "Error evaluando expresión: ${e.message}")
            return null
        }
    }

    /**
     * Evaluador de expresiones matemáticas usando exp4j.
     * Soporta expresiones complejas como "(A*256+B)/4-40".
     */
    private fun evaluateSimpleExpression(expr: String): Double? {
        return try {
            net.objecthunter.exp4j.ExpressionBuilder(expr)
                .build()
                .evaluate()
        } catch (e: Exception) {
            Logger.e(TAG, "Error evaluando expresión: $expr", e)
            null
        }
    }

    /**
     * Selecciona un byte específico para visualización detallada.
     */
    fun selectByteIndex(index: Int) {
        _selectedByteIndex.value = index
        Logger.d(TAG, "Byte seleccionado: $index")
    }

    /**
     * Limpia la selección de byte.
     */
    fun clearByteSelection() {
        _selectedByteIndex.value = null
    }

    /**
     * Obtiene la serie temporal de valores de un byte específico.
     */
    fun getByteTimeSeries(byteIndex: Int): List<Pair<Long, Int>> {
        return _rawResponses.value
            .filter { it.dataBytes.size > byteIndex }
            .map { response ->
                Pair(response.timestamp, response.dataBytes[byteIndex].toUByte().toInt())
            }
    }

    /**
     * Obtiene todos los valores de un byte para análisis.
     */
    fun getByteValues(byteIndex: Int): List<Int> {
        return _rawResponses.value
            .filter { it.dataBytes.size > byteIndex }
            .map { it.dataBytes[byteIndex].toUByte().toInt() }
    }

    /**
     * Refresca el análisis con nuevas respuestas.
     */
    fun refresh() {
        val command = _selectedCommand.value
        if (command != null) {
            loadResponsesForCommand(command)
            loadQuickStats(command)
        } else {
            loadAvailableCommands()
        }
    }
}

/**
 * Resultado del preview de una fórmula personalizada.
 */
data class FormulaPreviewResult(
    val formula: String,
    val results: List<FormulaTestResult>,
    val successCount: Int,
    val errorCount: Int,
    val isValid: Boolean,
    val error: String? = null
)

/**
 * Resultado de testear una fórmula con datos específicos.
 */
data class FormulaTestResult(
    val bytes: ByteArray,
    val result: Double?,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FormulaTestResult

        if (!bytes.contentEquals(other.bytes)) return false
        if (result != other.result) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result1 = bytes.contentHashCode()
        result1 = 31 * result1 + (result?.hashCode() ?: 0)
        result1 = 31 * result1 + timestamp.hashCode()
        return result1
    }
}
