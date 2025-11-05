package com.fleetcare.obd.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fleetcare.obd.domain.model.*
import com.fleetcare.obd.domain.repository.CustomPIDRepository
import com.fleetcare.obd.domain.repository.RawOBDResponseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel para el formulario de creación/edición de PIDs personalizados.
 *
 * Sprint 6: Gestión de PIDs Personalizados - Tarea 6.5
 */
@HiltViewModel
class CustomPIDFormViewModel @Inject constructor(
    private val customPIDRepository: CustomPIDRepository,
    private val rawOBDResponseRepository: RawOBDResponseRepository
) : ViewModel() {

    // ========== STATE FLOWS ==========

    private val _editingPID = MutableStateFlow<CustomPID?>(null)
    val editingPID: StateFlow<CustomPID?> = _editingPID.asStateFlow()

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _pid = MutableStateFlow("")
    val pid: StateFlow<String> = _pid.asStateFlow()

    private val _command = MutableStateFlow("")
    val command: StateFlow<String> = _command.asStateFlow()

    private val _formula = MutableStateFlow("")
    val formula: StateFlow<String> = _formula.asStateFlow()

    private val _unit = MutableStateFlow("")
    val unit: StateFlow<String> = _unit.asStateFlow()

    private val _category = MutableStateFlow(PIDCategory.GENERAL)
    val category: StateFlow<PIDCategory> = _category.asStateFlow()

    private val _byteCount = MutableStateFlow(1)
    val byteCount: StateFlow<Int> = _byteCount.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _source = MutableStateFlow(PIDSource.USER)
    val source: StateFlow<PIDSource> = _source.asStateFlow()

    private val _confidence = MutableStateFlow(0.7f)
    val confidence: StateFlow<Float> = _confidence.asStateFlow()

    // Fórmulas candidatas (desde análisis previo)
    private val _formulaCandidates = MutableStateFlow<List<FormulaCandidate>>(emptyList())
    val formulaCandidates: StateFlow<List<FormulaCandidate>> = _formulaCandidates.asStateFlow()

    // Preview de fórmula
    private val _formulaPreview = MutableStateFlow<FormulaPreviewResult?>(null)
    val formulaPreview: StateFlow<FormulaPreviewResult?> = _formulaPreview.asStateFlow()

    // Estado del formulario
    private val _isValid = MutableStateFlow(false)
    val isValid: StateFlow<Boolean> = _isValid.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // ========== INIT ==========

    init {
        // Validar formulario cuando cambien los campos
        viewModelScope.launch {
            combine(
                _name,
                _pid,
                _command,
                _formula,
                _unit,
                _byteCount
            ) { flows ->
                val name = flows[0] as String
                val pid = flows[1] as String
                val command = flows[2] as String
                val formula = flows[3] as String
                val unit = flows[4] as String
                val byteCount = flows[5] as Int
                validateForm(name, pid, command, formula, unit, byteCount)
            }.collect { isValid ->
                _isValid.value = isValid
            }
        }

        // Auto-generar comando cuando cambie el PID
        viewModelScope.launch {
            _pid.collect { pid ->
                if (pid.isNotBlank() && _command.value.isEmpty()) {
                    _command.value = "01${pid.uppercase()}"
                }
            }
        }
    }

    // ========== PUBLIC METHODS ==========

    /**
     * Inicializa el formulario para editar un PID existente.
     */
    fun loadPID(pid: CustomPID) {
        _editingPID.value = pid
        _name.value = pid.name
        _pid.value = pid.pid
        _command.value = pid.command
        _formula.value = pid.formula
        _unit.value = pid.unit
        _category.value = pid.category
        _byteCount.value = pid.byteCount
        _notes.value = pid.notes
        _isEnabled.value = pid.isEnabled
        _source.value = pid.source
        _confidence.value = pid.confidence
    }

    /**
     * Carga fórmulas candidatas para un PID específico.
     */
    fun loadFormulaCandidates(candidates: List<FormulaCandidate>) {
        _formulaCandidates.value = candidates
    }

    /**
     * Actualiza el nombre del PID.
     */
    fun setName(name: String) {
        _name.value = name
    }

    /**
     * Actualiza el PID (hex).
     */
    fun setPID(pid: String) {
        // Validar que sea hexadecimal
        val cleaned = pid.uppercase().filter { it in "0123456789ABCDEF" }
        _pid.value = cleaned.take(4) // Max 4 caracteres (2 bytes)
    }

    /**
     * Actualiza el comando OBD.
     */
    fun setCommand(command: String) {
        val cleaned = command.uppercase().filter { it in "0123456789ABCDEF" }
        _command.value = cleaned.take(8) // Max 8 caracteres
    }

    /**
     * Actualiza la fórmula.
     */
    fun setFormula(formula: String) {
        _formula.value = formula
        previewFormula()
    }

    /**
     * Selecciona una fórmula candidata.
     */
    fun selectFormulaCandidate(candidate: FormulaCandidate) {
        _formula.value = candidate.formulaExpression
        _unit.value = candidate.unit ?: ""
        _byteCount.value = candidate.requiredByteCount
        _confidence.value = when (candidate.confidenceLevel) {
            ConfidenceLevel.VERY_HIGH -> 0.95f
            ConfidenceLevel.HIGH -> 0.8f
            ConfidenceLevel.MEDIUM -> 0.6f
            ConfidenceLevel.LOW -> 0.4f
            ConfidenceLevel.VERY_LOW -> 0.2f
            else -> 0.5f
        }
        previewFormula()
    }

    /**
     * Actualiza la unidad.
     */
    fun setUnit(unit: String) {
        _unit.value = unit
    }

    /**
     * Actualiza la categoría.
     */
    fun setCategory(category: PIDCategory) {
        _category.value = category
    }

    /**
     * Actualiza el número de bytes.
     */
    fun setByteCount(count: Int) {
        _byteCount.value = count.coerceIn(1, 8)
    }

    /**
     * Actualiza las notas.
     */
    fun setNotes(notes: String) {
        _notes.value = notes
    }

    /**
     * Actualiza el estado habilitado.
     */
    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
    }

    /**
     * Actualiza la confianza.
     */
    fun setConfidence(confidence: Float) {
        _confidence.value = confidence.coerceIn(0f, 1f)
    }

    /**
     * Guarda el PID (crear nuevo o actualizar existente).
     */
    fun savePID() {
        viewModelScope.launch {
            _isSaving.value = true

            val customPID = CustomPID(
                id = _editingPID.value?.id ?: 0,
                pid = _pid.value,
                name = _name.value,
                command = _command.value,
                formula = _formula.value,
                unit = _unit.value,
                category = _category.value,
                byteCount = _byteCount.value,
                notes = _notes.value,
                isEnabled = _isEnabled.value,
                source = _source.value,
                confidence = _confidence.value,
                vehicleModels = _editingPID.value?.vehicleModels ?: emptyList(),
                discoveryDate = _editingPID.value?.discoveryDate ?: System.currentTimeMillis(),
                lastUsed = System.currentTimeMillis()
            )

            if (_editingPID.value != null) {
                // Actualizar
                customPIDRepository.updateCustomPID(customPID)
                    .onSuccess {
                        _successMessage.value = "PID actualizado exitosamente"
                        Timber.d("PID actualizado: ${customPID.name}")
                    }
                    .onFailure { e ->
                        _errorMessage.value = "Error al actualizar PID: ${e.message}"
                        Timber.e(e, "Error al actualizar PID")
                    }
            } else {
                // Crear nuevo
                customPIDRepository.saveCustomPID(customPID)
                    .onSuccess { id ->
                        _successMessage.value = "PID creado exitosamente"
                        Timber.d("PID creado: ${customPID.name} (ID: $id)")
                    }
                    .onFailure { e ->
                        _errorMessage.value = "Error al crear PID: ${e.message}"
                        Timber.e(e, "Error al crear PID")
                    }
            }

            _isSaving.value = false
        }
    }

    /**
     * Reinicia el formulario.
     */
    fun reset() {
        _editingPID.value = null
        _name.value = ""
        _pid.value = ""
        _command.value = ""
        _formula.value = ""
        _unit.value = ""
        _category.value = PIDCategory.GENERAL
        _byteCount.value = 1
        _notes.value = ""
        _isEnabled.value = true
        _source.value = PIDSource.USER
        _confidence.value = 0.7f
        _formulaCandidates.value = emptyList()
        _formulaPreview.value = null
    }

    /**
     * Limpia mensajes.
     */
    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    // ========== PRIVATE METHODS ==========

    /**
     * Valida el formulario.
     */
    private fun validateForm(
        name: String,
        pid: String,
        command: String,
        formula: String,
        unit: String,
        byteCount: Int
    ): Boolean {
        return name.isNotBlank() &&
                pid.isNotBlank() &&
                pid.length >= 2 &&
                command.isNotBlank() &&
                command.length >= 4 &&
                formula.isNotBlank() &&
                unit.isNotBlank() &&
                byteCount > 0
    }

    /**
     * Genera un preview de la fórmula con datos históricos.
     */
    private fun previewFormula() {
        if (_formula.value.isBlank() || _command.value.isBlank()) {
            _formulaPreview.value = null
            return
        }

        viewModelScope.launch {
            try {
                // Obtener respuestas RAW históricas del comando
                rawOBDResponseRepository.getResponsesForCommand(_command.value)
                    .firstOrNull()
                    ?.let { responses ->
                        val successfulResponses = responses.filter { it.parseSuccess }
                            .take(5) // Primeros 5 resultados

                        if (successfulResponses.isEmpty()) {
                            _formulaPreview.value = FormulaPreviewResult(
                                success = false,
                                message = "No hay datos históricos disponibles para este comando"
                            )
                            return@launch
                        }

                        // Intentar aplicar la fórmula
                        val results = successfulResponses.mapNotNull { response ->
                            try {
                                val customPID = CustomPID(
                                    pid = _pid.value,
                                    name = _name.value,
                                    command = _command.value,
                                    formula = _formula.value,
                                    unit = _unit.value,
                                    category = _category.value,
                                    byteCount = _byteCount.value
                                )
                                val result = customPID.applyFormula(response.dataBytes)
                                result?.let {
                                    FormulaPreviewItem(
                                        bytes = response.dataBytes,
                                        result = it,
                                        timestamp = response.timestamp
                                    )
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }

                        if (results.isEmpty()) {
                            _formulaPreview.value = FormulaPreviewResult(
                                success = false,
                                message = "Error al evaluar la fórmula. Verifica la sintaxis."
                            )
                        } else {
                            _formulaPreview.value = FormulaPreviewResult(
                                success = true,
                                items = results,
                                message = "Preview generado con ${results.size} muestras"
                            )
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error al generar preview de fórmula")
                _formulaPreview.value = FormulaPreviewResult(
                    success = false,
                    message = "Error al generar preview: ${e.message}"
                )
            }
        }
    }
}

/**
 * Resultado del preview de fórmula.
 */
data class FormulaPreviewResult(
    val success: Boolean,
    val items: List<FormulaPreviewItem> = emptyList(),
    val message: String = ""
)

/**
 * Item individual del preview.
 */
data class FormulaPreviewItem(
    val bytes: ByteArray,
    val result: Double,
    val timestamp: Long
) {
    fun getBytesHex(): String {
        return bytes.joinToString(" ") { "%02X".format(it.toUByte().toInt()) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FormulaPreviewItem
        if (!bytes.contentEquals(other.bytes)) return false
        if (result != other.result) return false
        if (timestamp != other.timestamp) return false
        return true
    }

    override fun hashCode(): Int {
        var result1 = bytes.contentHashCode()
        result1 = 31 * result1 + result.hashCode()
        result1 = 31 * result1 + timestamp.hashCode()
        return result1
    }
}
