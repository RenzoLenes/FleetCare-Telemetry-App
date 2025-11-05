package com.fleetcare.obd.data.analysis

import com.fleetcare.obd.domain.model.*
import com.fleetcare.obd.utils.Logger
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Analizador dinámico de patrones en respuestas OBD-II.
 *
 * Sprint 3: Motor de Análisis de Patrones
 *
 * Este analizador procesa múltiples respuestas RAW de un PID para:
 * - Detectar bytes estáticos vs dinámicos
 * - Calcular estadísticas por byte
 * - Analizar correlaciones entre bytes
 * - Inferir el tipo de dato
 * - Sugerir fórmulas de decodificación
 *
 * @property formulaInferenceEngine Motor de inferencia de fórmulas
 */
class DynamicPIDAnalyzer @Inject constructor(
    private val formulaInferenceEngine: FormulaInferenceEngine
) {

    companion object {
        private const val TAG = "DynamicPIDAnalyzer"
        private const val STATIC_THRESHOLD = 0.1 // Desviación estándar mínima para considerar dinámico
        private const val MIN_SAMPLES_FOR_ANALYSIS = 5
    }

    /**
     * Analiza un conjunto de respuestas RAW y genera un patrón completo.
     *
     * @param responses Lista de respuestas RAW del mismo PID
     * @return PIDPattern con toda la información analizada
     */
    fun analyzePattern(responses: List<RawOBDResponse>): Result<PIDPattern> {
        return try {
            if (responses.isEmpty()) {
                return Result.failure(IllegalArgumentException("No hay respuestas para analizar"))
            }

            if (responses.size < MIN_SAMPLES_FOR_ANALYSIS) {
                Logger.w(TAG, "Pocas muestras (${responses.size}), análisis puede ser impreciso")
            }

            val pid = responses.first().command.takeLast(2)
            val command = responses.first().command
            val byteCount = detectByteCount(responses)

            Logger.d(TAG, "Analizando PID $pid: ${responses.size} muestras, $byteCount bytes")

            // Análisis estadístico de bytes
            val byteStats = analyzeByteStatistics(responses, byteCount)

            // Clasificar bytes estáticos vs dinámicos
            val staticBytes = detectStaticBytes(byteStats)
            val dynamicBytes = detectDynamicBytes(byteStats)

            Logger.d(TAG, "Bytes estáticos: $staticBytes, dinámicos: $dynamicBytes")

            // Calcular rango de valores
            val valueRange = calculateValueRange(responses, dynamicBytes)

            // Analizar correlaciones entre bytes dinámicos
            val correlations = if (dynamicBytes.size > 1) {
                analyzeCorrelations(responses, dynamicBytes, byteCount)
            } else {
                emptyMap()
            }

            // Detectar tipo de dato
            val detectedType = detectDataType(byteCount, byteStats, valueRange, correlations)

            Logger.d(TAG, "Tipo detectado: $detectedType")

            // Preparar samples para inferencia de fórmulas
            val samples = responses.map { response ->
                Pair(response.dataBytes, null as Double?) // No tenemos valores esperados aún
            }

            // Inferir fórmulas candidatas
            val suggestedFormulas = formulaInferenceEngine.inferFormula(
                samples = samples,
                expectedValueRange = valueRange,
                byteCount = byteCount
            )

            // Calcular confianza del análisis
            val confidence = calculateAnalysisConfidence(
                sampleCount = responses.size,
                byteStats = byteStats,
                topFormulaScore = suggestedFormulas.firstOrNull()?.score ?: 0.0
            )

            val pattern = PIDPattern(
                pid = pid,
                command = command,
                byteCount = byteCount,
                sampleCount = responses.size,
                staticByteIndices = staticBytes,
                dynamicByteIndices = dynamicBytes,
                valueRange = valueRange,
                byteStatistics = byteStats,
                suggestedFormulas = suggestedFormulas,
                correlations = correlations,
                detectedType = detectedType,
                confidence = confidence
            )

            Logger.d(TAG, "Análisis completado: confianza=${String.format("%.2f", confidence)}")

            Result.success(pattern)
        } catch (e: Exception) {
            Logger.e(e, "Error analizando patrón")
            Result.failure(e)
        }
    }

    /**
     * Detecta el número de bytes en las respuestas.
     */
    fun detectByteCount(responses: List<RawOBDResponse>): Int {
        // Usar la moda (valor más frecuente) del número de bytes
        val byteCounts = responses.map { it.dataBytes.size }
        return byteCounts.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 0
    }

    /**
     * Analiza estadísticas de cada byte individual.
     *
     * Sprint 3.6: Análisis estadístico completo
     */
    fun analyzeByteStatistics(responses: List<RawOBDResponse>, byteCount: Int): List<ByteStatistic> {
        val stats = mutableListOf<ByteStatistic>()

        for (i in 0 until byteCount) {
            // Extraer valores de este byte de todas las respuestas
            val byteValues = responses
                .filter { it.dataBytes.size > i }
                .map { it.dataBytes[i].toUByte().toInt() }

            if (byteValues.isEmpty()) {
                Logger.w(TAG, "No hay valores para byte $i")
                continue
            }

            // Calcular estadísticas
            val min = byteValues.minOrNull() ?: 0
            val max = byteValues.maxOrNull() ?: 0
            val mean = byteValues.average()
            val median = calculateMedian(byteValues)
            val variance = byteValues.map { (it - mean).pow(2) }.average()
            val stdDev = sqrt(variance)
            val isConstant = (max - min) == 0
            val distribution = byteValues.groupingBy { it }.eachCount()
            val mostCommonValue = distribution.maxByOrNull { it.value }?.key ?: 0
            val uniqueValues = distribution.size

            val byteStat = ByteStatistic(
                index = i,
                min = min,
                max = max,
                mean = mean,
                median = median,
                stdDev = stdDev,
                variance = variance,
                isConstant = isConstant,
                mostCommonValue = mostCommonValue,
                uniqueValues = uniqueValues,
                distribution = distribution
            )

            stats.add(byteStat)

            Logger.d(TAG, "Byte[$i]: ${byteStat.toSummary()}")
        }

        return stats
    }

    /**
     * Detecta bytes estáticos (que no cambian o cambian muy poco).
     */
    fun detectStaticBytes(byteStats: List<ByteStatistic>): List<Int> {
        return byteStats
            .filter { it.isConstant || it.stdDev < STATIC_THRESHOLD }
            .map { it.index }
    }

    /**
     * Detecta bytes dinámicos (que varían significativamente).
     */
    fun detectDynamicBytes(byteStats: List<ByteStatistic>): List<Int> {
        return byteStats
            .filter { !it.isConstant && it.stdDev >= STATIC_THRESHOLD }
            .map { it.index }
    }

    /**
     * Calcula el rango de valores observados basándose en bytes dinámicos.
     */
    private fun calculateValueRange(
        responses: List<RawOBDResponse>,
        dynamicBytes: List<Int>
    ): Pair<Double, Double> {
        if (dynamicBytes.isEmpty()) {
            return Pair(0.0, 0.0)
        }

        // Calcular rango considerando interpretación de 16-bit si hay 2+ bytes dinámicos
        val values = if (dynamicBytes.size >= 2 && dynamicBytes[0] == 0 && dynamicBytes[1] == 1) {
            // Probable 16-bit big endian
            responses.map { response ->
                val a = response.dataBytes[0].toUByte().toInt()
                val b = response.dataBytes[1].toUByte().toInt()
                (a * 256 + b).toDouble()
            }
        } else {
            // Usar el byte más dinámico
            val mostDynamicByte = dynamicBytes.first()
            responses.map { response ->
                if (response.dataBytes.size > mostDynamicByte) {
                    response.dataBytes[mostDynamicByte].toUByte().toDouble()
                } else {
                    0.0
                }
            }
        }

        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 0.0

        return Pair(min, max)
    }

    /**
     * Analiza correlaciones entre bytes dinámicos.
     *
     * Sprint 3.6: Análisis de correlaciones
     */
    fun analyzeCorrelations(
        responses: List<RawOBDResponse>,
        dynamicBytes: List<Int>,
        byteCount: Int
    ): Map<Pair<Int, Int>, Double> {
        if (dynamicBytes.size < 2 || responses.size < MIN_SAMPLES_FOR_ANALYSIS) {
            return emptyMap()
        }

        val correlations = mutableMapOf<Pair<Int, Int>, Double>()

        // Calcular correlación de Pearson entre cada par de bytes dinámicos
        for (i in 0 until dynamicBytes.size) {
            for (j in (i + 1) until dynamicBytes.size) {
                val byte1Index = dynamicBytes[i]
                val byte2Index = dynamicBytes[j]

                // Extraer series de valores
                val series1 = responses
                    .filter { it.dataBytes.size > byte1Index }
                    .map { it.dataBytes[byte1Index].toUByte().toDouble() }

                val series2 = responses
                    .filter { it.dataBytes.size > byte2Index }
                    .map { it.dataBytes[byte2Index].toUByte().toDouble() }

                if (series1.size == series2.size && series1.size >= MIN_SAMPLES_FOR_ANALYSIS) {
                    val correlation = calculatePearsonCorrelation(series1, series2)
                    correlations[Pair(byte1Index, byte2Index)] = correlation

                    if (abs(correlation) > 0.7) {
                        Logger.d(TAG, "Correlación fuerte entre Byte[$byte1Index] y Byte[$byte2Index]: $correlation")
                    }
                }
            }
        }

        return correlations
    }

    /**
     * Calcula el coeficiente de correlación de Pearson entre dos series.
     */
    private fun calculatePearsonCorrelation(series1: List<Double>, series2: List<Double>): Double {
        if (series1.size != series2.size || series1.isEmpty()) {
            return 0.0
        }

        val n = series1.size
        val mean1 = series1.average()
        val mean2 = series2.average()

        var numerator = 0.0
        var sumSq1 = 0.0
        var sumSq2 = 0.0

        for (i in 0 until n) {
            val diff1 = series1[i] - mean1
            val diff2 = series2[i] - mean2

            numerator += diff1 * diff2
            sumSq1 += diff1.pow(2)
            sumSq2 += diff2.pow(2)
        }

        val denominator = sqrt(sumSq1 * sumSq2)

        return if (denominator != 0.0) {
            numerator / denominator
        } else {
            0.0
        }
    }

    /**
     * Detecta el tipo de dato basándose en características del patrón.
     */
    fun detectDataType(
        byteCount: Int,
        byteStats: List<ByteStatistic>,
        valueRange: Pair<Double, Double>,
        correlations: Map<Pair<Int, Int>, Double>
    ): DetectedDataType {
        // Single byte
        if (byteCount == 1) {
            val stat = byteStats.firstOrNull() ?: return DetectedDataType.UNKNOWN

            return when {
                // Temperatura típica: rango -40 a 215
                stat.min < 50 && stat.max < 255 -> DetectedDataType.TEMPERATURE
                // Porcentaje: valores 0-255 mapeados a 0-100%
                stat.max <= 255 && stat.min >= 0 -> DetectedDataType.PERCENTAGE
                else -> DetectedDataType.SINGLE_BYTE
            }
        }

        // Two bytes
        if (byteCount == 2) {
            val byte0 = byteStats.getOrNull(0)
            val byte1 = byteStats.getOrNull(1)

            if (byte0 != null && byte1 != null) {
                // Si ambos bytes son dinámicos, probablemente 16-bit
                if (!byte0.isConstant && !byte1.isConstant) {
                    // Verificar correlación fuerte (indicaría big endian)
                    val correlation = correlations[Pair(0, 1)]

                    return if (correlation != null && abs(correlation) > 0.7) {
                        DetectedDataType.TWO_BYTE_BIG_ENDIAN
                    } else {
                        // Sin correlación fuerte, verificar patrones
                        if (byte0.uniqueValues > byte1.uniqueValues) {
                            DetectedDataType.TWO_BYTE_BIG_ENDIAN
                        } else {
                            DetectedDataType.TWO_BYTE_LITTLE_ENDIAN
                        }
                    }
                }
            }

            return DetectedDataType.TWO_BYTE_BIG_ENDIAN
        }

        // Four bytes
        if (byteCount == 4) {
            return DetectedDataType.FOUR_BYTE
        }

        return DetectedDataType.UNKNOWN
    }

    /**
     * Correlaciona bytes con un valor conocido para sugerir fórmula específica.
     *
     * @param bytes Array de bytes
     * @param value Valor conocido que estos bytes representan
     * @return Fórmula candidata que mejor aproxima el valor
     */
    fun correlateWithKnownValue(bytes: ByteArray, value: Double): FormulaCandidate? {
        val samples = listOf(Pair(bytes, value))

        val formulas = formulaInferenceEngine.inferFormula(
            samples = samples,
            expectedValueRange = Pair(value * 0.9, value * 1.1),
            byteCount = bytes.size
        )

        return formulas.maxByOrNull { it.score }
    }

    /**
     * Calcula la confianza del análisis.
     */
    private fun calculateAnalysisConfidence(
        sampleCount: Int,
        byteStats: List<ByteStatistic>,
        topFormulaScore: Double
    ): Double {
        var confidence = 0.0

        // Contribución del número de muestras (máx 0.4)
        val sampleFactor = (sampleCount / 50.0).coerceIn(0.0, 0.4)
        confidence += sampleFactor

        // Contribución de la claridad del patrón (máx 0.3)
        val dynamicBytes = byteStats.filter { !it.isConstant }
        val patternClarityFactor = if (dynamicBytes.isNotEmpty()) {
            val avgStdDev = dynamicBytes.map { it.stdDev }.average()
            (avgStdDev / 50.0).coerceIn(0.0, 0.3)
        } else {
            0.0
        }
        confidence += patternClarityFactor

        // Contribución del score de la mejor fórmula (máx 0.3)
        confidence += topFormulaScore * 0.3

        return confidence.coerceIn(0.0, 1.0)
    }

    /**
     * Calcula la mediana de una lista de valores.
     */
    private fun calculateMedian(values: List<Int>): Double {
        if (values.isEmpty()) return 0.0

        val sorted = values.sorted()
        val size = sorted.size

        return if (size % 2 == 0) {
            (sorted[size / 2 - 1] + sorted[size / 2]) / 2.0
        } else {
            sorted[size / 2].toDouble()
        }
    }

    /**
     * Detecta outliers en los datos usando el método IQR.
     *
     * Sprint 3.6: Detección de outliers
     */
    fun detectOutliers(values: List<Double>): List<Int> {
        if (values.size < 4) return emptyList()

        val sorted = values.sorted()
        val q1Index = (sorted.size * 0.25).toInt()
        val q3Index = (sorted.size * 0.75).toInt()

        val q1 = sorted[q1Index]
        val q3 = sorted[q3Index]
        val iqr = q3 - q1

        val lowerBound = q1 - 1.5 * iqr
        val upperBound = q3 + 1.5 * iqr

        return values.mapIndexedNotNull { index, value ->
            if (value < lowerBound || value > upperBound) index else null
        }
    }
}
