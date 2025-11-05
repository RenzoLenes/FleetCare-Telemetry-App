package com.fleetcare.obd.domain.model

/**
 * Representa una fórmula candidata para decodificar un PID.
 *
 * Sprint 3: Motor de Análisis de Patrones
 *
 * @property id Identificador único de la fórmula
 * @property name Nombre descriptivo de la fórmula
 * @property description Descripción detallada de qué representa
 * @property formula Expresión lambda para calcular el valor
 * @property formulaExpression Expresión como string legible (ej: "(A * 256 + B) / 4")
 * @property requiredByteCount Número mínimo de bytes requeridos
 * @property score Puntuación de precisión (0.0 a 1.0, donde 1.0 es perfecto)
 * @property sampleResults Ejemplos de resultados aplicando la fórmula
 * @property category Categoría de la fórmula (Simple, Temperatura, Velocidad, etc.)
 * @property unit Unidad de medida del resultado (opcional)
 * @property confidenceLevel Nivel de confianza en la precisión de la fórmula
 */
data class FormulaCandidate(
    val id: String,
    val name: String,
    val description: String,
    val formula: (ByteArray) -> Double,
    val formulaExpression: String,
    val requiredByteCount: Int,
    val score: Double = 0.0,
    val sampleResults: List<SampleResult> = emptyList(),
    val category: FormulaCategory,
    val unit: String? = null,
    val confidenceLevel: ConfidenceLevel = ConfidenceLevel.UNKNOWN
) {
    /**
     * Aplica la fórmula a un array de bytes.
     *
     * @param bytes Array de bytes a procesar
     * @return Resultado calculado, o null si no hay suficientes bytes
     */
    fun apply(bytes: ByteArray): Double? {
        return if (bytes.size >= requiredByteCount) {
            try {
                formula(bytes)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    /**
     * Calcula el error cuadrático medio (RMSE) comparando con valores esperados.
     *
     * @param samples Lista de pares (bytes, valorEsperado)
     * @return RMSE, o null si no se puede calcular
     */
    fun calculateRMSE(samples: List<Pair<ByteArray, Double>>): Double? {
        if (samples.isEmpty()) return null

        var sumSquaredErrors = 0.0
        var validSamples = 0

        samples.forEach { (bytes, expected) ->
            apply(bytes)?.let { calculated ->
                val error = calculated - expected
                sumSquaredErrors += error * error
                validSamples++
            }
        }

        return if (validSamples > 0) {
            kotlin.math.sqrt(sumSquaredErrors / validSamples)
        } else {
            null
        }
    }

    /**
     * Calcula el error porcentual medio absoluto (MAPE).
     *
     * @param samples Lista de pares (bytes, valorEsperado)
     * @return MAPE en porcentaje, o null si no se puede calcular
     */
    fun calculateMAPE(samples: List<Pair<ByteArray, Double>>): Double? {
        if (samples.isEmpty()) return null

        var sumPercentageErrors = 0.0
        var validSamples = 0

        samples.forEach { (bytes, expected) ->
            if (expected != 0.0) {
                apply(bytes)?.let { calculated ->
                    val percentageError = kotlin.math.abs((calculated - expected) / expected) * 100
                    sumPercentageErrors += percentageError
                    validSamples++
                }
            }
        }

        return if (validSamples > 0) {
            sumPercentageErrors / validSamples
        } else {
            null
        }
    }

    /**
     * Determina si esta fórmula es mejor que otra basándose en el score.
     */
    fun isBetterThan(other: FormulaCandidate): Boolean {
        return this.score > other.score
    }

    /**
     * Crea una copia con nuevo score y samples.
     */
    fun withResults(newScore: Double, newSamples: List<SampleResult>): FormulaCandidate {
        return copy(
            score = newScore,
            sampleResults = newSamples,
            confidenceLevel = determineConfidenceLevel(newScore)
        )
    }

    private fun determineConfidenceLevel(score: Double): ConfidenceLevel {
        return when {
            score >= 0.95 -> ConfidenceLevel.VERY_HIGH
            score >= 0.85 -> ConfidenceLevel.HIGH
            score >= 0.70 -> ConfidenceLevel.MEDIUM
            score >= 0.50 -> ConfidenceLevel.LOW
            else -> ConfidenceLevel.VERY_LOW
        }
    }

    /**
     * Genera un resumen legible de esta fórmula.
     */
    fun toSummary(): String {
        return buildString {
            append("$name: $formulaExpression")
            unit?.let { append(" ($it)") }
            append(" | Score: ${String.format("%.2f%%", score * 100)}")
            append(" | Confianza: $confidenceLevel")
        }
    }
}

/**
 * Resultado de aplicar una fórmula a datos de ejemplo.
 */
data class SampleResult(
    val inputBytes: ByteArray,
    val calculatedValue: Double,
    val expectedValue: Double? = null,
    val error: Double? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SampleResult

        if (!inputBytes.contentEquals(other.inputBytes)) return false
        if (calculatedValue != other.calculatedValue) return false
        if (expectedValue != other.expectedValue) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result = inputBytes.contentHashCode()
        result = 31 * result + calculatedValue.hashCode()
        result = 31 * result + (expectedValue?.hashCode() ?: 0)
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }

    /**
     * Genera representación legible del resultado.
     */
    fun toDebugString(): String {
        val bytesHex = inputBytes.joinToString(" ") { "%02X".format(it) }
        return buildString {
            append("Bytes: [$bytesHex] → ")
            append("Calculado: %.2f".format(calculatedValue))
            expectedValue?.let {
                append(" | Esperado: %.2f".format(it))
                error?.let { err ->
                    append(" | Error: %.2f".format(err))
                }
            }
        }
    }
}

/**
 * Categorías de fórmulas según el tipo de dato que representan.
 */
enum class FormulaCategory {
    SIMPLE,              // Valor directo: A
    TEMPERATURE,         // Temperaturas con offset
    SPEED,               // Velocidades
    PERCENTAGE,          // Porcentajes (0-100%)
    PRESSURE,            // Presiones
    VOLTAGE,             // Voltajes
    RPM,                 // Revoluciones por minuto
    FLOW_RATE,           // Tasas de flujo
    RATIO,               // Ratios y proporciones
    TIME,                // Tiempos y duraciones
    DISTANCE,            // Distancias
    FUEL,                // Nivel de combustible
    TORQUE,              // Torques
    POWER,               // Potencia
    BITFIELD,            // Campos de bits
    COMPOSITE,           // Fórmulas compuestas complejas
    MANUFACTURER,        // Específicas del fabricante
    UNKNOWN              // Sin categoría específica
}

/**
 * Nivel de confianza en la precisión de una fórmula.
 */
enum class ConfidenceLevel {
    VERY_HIGH,   // Score >= 95%
    HIGH,        // Score >= 85%
    MEDIUM,      // Score >= 70%
    LOW,         // Score >= 50%
    VERY_LOW,    // Score < 50%
    UNKNOWN      // No calculado aún
}
