package com.fleetcare.obd.domain.model

/**
 * Representa el patrón detectado al analizar múltiples respuestas de un PID.
 *
 * Sprint 3: Motor de Análisis de Patrones
 *
 * Este modelo contiene información estadística sobre cómo se comportan los bytes
 * de un PID a lo largo del tiempo, permitiendo inferir la estructura de los datos
 * y sugerir fórmulas de decodificación.
 *
 * @property pid Identificador del PID (ej: "0C" para RPM)
 * @property command Comando OBD completo (ej: "010C")
 * @property byteCount Número de bytes en la respuesta (sin contar el header)
 * @property sampleCount Número de muestras analizadas
 * @property staticByteIndices Índices de bytes que nunca cambian (constantes)
 * @property dynamicByteIndices Índices de bytes que varían (contienen datos)
 * @property valueRange Rango de valores observados (min, max)
 * @property byteStatistics Estadísticas por cada byte individual
 * @property suggestedFormulas Fórmulas candidatas ranqueadas por score
 * @property correlations Correlaciones entre bytes
 * @property detectedType Tipo de dato detectado automáticamente
 * @property analysisTimestamp Timestamp del análisis
 * @property confidence Nivel de confianza en el análisis
 */
data class PIDPattern(
    val pid: String,
    val command: String,
    val byteCount: Int,
    val sampleCount: Int,
    val staticByteIndices: List<Int>,
    val dynamicByteIndices: List<Int>,
    val valueRange: Pair<Double, Double>,
    val byteStatistics: List<ByteStatistic>,
    val suggestedFormulas: List<FormulaCandidate>,
    val correlations: Map<Pair<Int, Int>, Double>,
    val detectedType: DetectedDataType,
    val analysisTimestamp: Long = System.currentTimeMillis(),
    val confidence: Double = 0.0
) {
    /**
     * Obtiene la mejor fórmula sugerida.
     */
    fun getBestFormula(): FormulaCandidate? {
        return suggestedFormulas.maxByOrNull { it.score }
    }

    /**
     * Obtiene las top N fórmulas.
     */
    fun getTopFormulas(n: Int): List<FormulaCandidate> {
        return suggestedFormulas.sortedByDescending { it.score }.take(n)
    }

    /**
     * Verifica si un byte es estático (constante).
     */
    fun isByteStatic(index: Int): Boolean {
        return index in staticByteIndices
    }

    /**
     * Verifica si un byte es dinámico (variable).
     */
    fun isByteDynamic(index: Int): Boolean {
        return index in dynamicByteIndices
    }

    /**
     * Obtiene las estadísticas de un byte específico.
     */
    fun getByteStatistic(index: Int): ByteStatistic? {
        return byteStatistics.getOrNull(index)
    }

    /**
     * Obtiene la correlación entre dos bytes.
     */
    fun getCorrelation(index1: Int, index2: Int): Double? {
        return correlations[Pair(index1, index2)] ?: correlations[Pair(index2, index1)]
    }

    /**
     * Determina si el patrón es confiable para inferir fórmulas.
     */
    fun isReliable(): Boolean {
        return sampleCount >= 10 && confidence >= 0.7
    }

    /**
     * Genera un resumen legible del patrón.
     */
    fun toSummary(): String {
        return buildString {
            appendLine("=== Análisis de PID $pid ===")
            appendLine("Comando: $command")
            appendLine("Muestras analizadas: $sampleCount")
            appendLine("Bytes totales: $byteCount")
            appendLine("Bytes estáticos: ${staticByteIndices.size}")
            appendLine("Bytes dinámicos: ${dynamicByteIndices.size}")
            appendLine("Rango de valores: ${valueRange.first} - ${valueRange.second}")
            appendLine("Tipo detectado: $detectedType")
            appendLine("Confianza: ${String.format("%.2f%%", confidence * 100)}")
            appendLine()
            appendLine("Top 3 fórmulas sugeridas:")
            getTopFormulas(3).forEachIndexed { index, formula ->
                appendLine("  ${index + 1}. ${formula.toSummary()}")
            }
        }
    }
}

/**
 * Estadísticas de un byte individual.
 *
 * @property index Posición del byte en la respuesta
 * @property min Valor mínimo observado
 * @property max Valor máximo observado
 * @property mean Media aritmética
 * @property median Mediana
 * @property stdDev Desviación estándar
 * @property variance Varianza
 * @property isConstant Si el byte nunca cambia
 * @property mostCommonValue Valor más frecuente
 * @property uniqueValues Número de valores únicos observados
 * @property distribution Distribución de frecuencias
 */
data class ByteStatistic(
    val index: Int,
    val min: Int,
    val max: Int,
    val mean: Double,
    val median: Double,
    val stdDev: Double,
    val variance: Double,
    val isConstant: Boolean,
    val mostCommonValue: Int,
    val uniqueValues: Int,
    val distribution: Map<Int, Int> = emptyMap()
) {
    /**
     * Verifica si el byte tiene baja variabilidad (probablemente estático).
     */
    fun hasLowVariability(): Boolean {
        return stdDev < 1.0 || uniqueValues <= 2
    }

    /**
     * Verifica si el byte tiene alta variabilidad (probablemente dinámico).
     */
    fun hasHighVariability(): Boolean {
        return stdDev > 10.0 && uniqueValues > 10
    }

    /**
     * Calcula el rango de valores (max - min).
     */
    fun getRange(): Int {
        return max - min
    }

    /**
     * Obtiene el coeficiente de variación (CV = stdDev / mean).
     * Útil para comparar variabilidad entre bytes con diferentes escalas.
     */
    fun getCoefficientOfVariation(): Double? {
        return if (mean != 0.0) {
            (stdDev / mean) * 100
        } else {
            null
        }
    }

    /**
     * Genera un resumen de las estadísticas.
     */
    fun toSummary(): String {
        return buildString {
            append("Byte[$index]: ")
            if (isConstant) {
                append("CONSTANTE (valor=$mostCommonValue)")
            } else {
                append("min=$min, max=$max, mean=%.2f".format(mean))
                append(", σ=%.2f".format(stdDev))
                append(", valores únicos=$uniqueValues")
            }
        }
    }
}

/**
 * Tipos de datos detectados automáticamente.
 */
enum class DetectedDataType {
    SINGLE_BYTE,           // Un solo byte (0-255)
    TWO_BYTE_BIG_ENDIAN,   // 16-bit big endian (A*256 + B)
    TWO_BYTE_LITTLE_ENDIAN, // 16-bit little endian (B*256 + A)
    FOUR_BYTE,             // 32-bit
    TEMPERATURE,           // Temperatura con offset típico (-40)
    PERCENTAGE,            // Porcentaje (0-100%)
    SIGNED_BYTE,           // Byte con signo (-128 a 127)
    BCD,                   // Binary Coded Decimal
    BITFIELD,              // Campo de bits (flags)
    COMPOSITE,             // Múltiples valores empaquetados
    FLOAT_IEEE754,         // Punto flotante IEEE 754
    ASCII,                 // Texto ASCII
    MANUFACTURER_SPECIFIC, // Específico del fabricante
    UNKNOWN                // No se pudo determinar
}

/**
 * Resultado del análisis de correlación entre bytes.
 *
 * @property byte1 Índice del primer byte
 * @property byte2 Índice del segundo byte
 * @property correlation Coeficiente de correlación de Pearson (-1.0 a 1.0)
 * @property isSignificant Si la correlación es estadísticamente significativa
 */
data class ByteCorrelation(
    val byte1: Int,
    val byte2: Int,
    val correlation: Double,
    val isSignificant: Boolean
) {
    /**
     * Verifica si hay correlación fuerte positiva.
     */
    fun hasStrongPositiveCorrelation(): Boolean {
        return correlation > 0.7 && isSignificant
    }

    /**
     * Verifica si hay correlación fuerte negativa.
     */
    fun hasStrongNegativeCorrelation(): Boolean {
        return correlation < -0.7 && isSignificant
    }

    /**
     * Verifica si los bytes están fuertemente correlacionados (en cualquier dirección).
     */
    fun isStronglyCorrelated(): Boolean {
        return kotlin.math.abs(correlation) > 0.7 && isSignificant
    }

    /**
     * Interpreta el nivel de correlación.
     */
    fun getCorrelationStrength(): CorrelationStrength {
        val absCorr = kotlin.math.abs(correlation)
        return when {
            absCorr >= 0.9 -> CorrelationStrength.VERY_STRONG
            absCorr >= 0.7 -> CorrelationStrength.STRONG
            absCorr >= 0.5 -> CorrelationStrength.MODERATE
            absCorr >= 0.3 -> CorrelationStrength.WEAK
            else -> CorrelationStrength.VERY_WEAK
        }
    }

    fun toSummary(): String {
        val direction = if (correlation > 0) "positiva" else "negativa"
        return "Byte[$byte1] ↔ Byte[$byte2]: ${getCorrelationStrength()} $direction (r=%.3f)".format(correlation)
    }
}

/**
 * Niveles de fuerza de correlación.
 */
enum class CorrelationStrength {
    VERY_STRONG,  // |r| >= 0.9
    STRONG,       // |r| >= 0.7
    MODERATE,     // |r| >= 0.5
    WEAK,         // |r| >= 0.3
    VERY_WEAK     // |r| < 0.3
}
