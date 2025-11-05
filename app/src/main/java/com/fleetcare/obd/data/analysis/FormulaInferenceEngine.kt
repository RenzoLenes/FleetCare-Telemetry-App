package com.fleetcare.obd.data.analysis

import com.fleetcare.obd.domain.model.FormulaCandidate
import com.fleetcare.obd.domain.model.FormulaCategory
import com.fleetcare.obd.domain.model.SampleResult
import com.fleetcare.obd.utils.Logger
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Motor de inferencia de fórmulas para decodificación de PIDs OBD-II.
 *
 * Sprint 3: Motor de Análisis de Patrones
 *
 * Este motor contiene un banco de 20+ fórmulas candidatas y algoritmos para
 * inferir cuál es la más apropiada para decodificar un PID desconocido.
 *
 * @property logger Logger para diagnóstico
 */
class FormulaInferenceEngine @Inject constructor() {

    companion object {
        private const val TAG = "FormulaInferenceEngine"
    }

    /**
     * Banco de fórmulas candidatas predefinidas.
     * Cada fórmula se define con su expresión lambda y metadatos.
     */
    private val formulaBank: List<FormulaCandidate> = listOf(
        // 1. Simple: Valor directo del primer byte
        FormulaCandidate(
            id = "F001",
            name = "Valor Simple",
            description = "Valor directo del primer byte (0-255)",
            formula = { bytes -> bytes[0].toUByte().toDouble() },
            formulaExpression = "A",
            requiredByteCount = 1,
            category = FormulaCategory.SIMPLE
        ),

        // 2. Offset -40: Temperatura típica
        FormulaCandidate(
            id = "F002",
            name = "Temperatura Offset -40",
            description = "Temperatura con offset de -40°C (rango: -40 a 215°C)",
            formula = { bytes -> bytes[0].toUByte().toDouble() - 40 },
            formulaExpression = "A - 40",
            requiredByteCount = 1,
            category = FormulaCategory.TEMPERATURE,
            unit = "°C"
        ),

        // 3. Offset +40
        FormulaCandidate(
            id = "F003",
            name = "Offset +40",
            description = "Valor con offset positivo de 40",
            formula = { bytes -> bytes[0].toUByte().toDouble() + 40 },
            formulaExpression = "A + 40",
            requiredByteCount = 1,
            category = FormulaCategory.SIMPLE
        ),

        // 4. Porcentaje (0-100%)
        FormulaCandidate(
            id = "F004",
            name = "Porcentaje",
            description = "Porcentaje de 0 a 100 (255 = 100%)",
            formula = { bytes -> (bytes[0].toUByte().toDouble() * 100.0) / 255.0 },
            formulaExpression = "(A * 100) / 255",
            requiredByteCount = 1,
            category = FormulaCategory.PERCENTAGE,
            unit = "%"
        ),

        // 5. 16-bit big endian: RPM típico
        FormulaCandidate(
            id = "F005",
            name = "16-bit Big Endian /4",
            description = "16-bit big endian dividido por 4 (típico para RPM)",
            formula = { bytes ->
                val value = (bytes[0].toUByte().toInt() * 256) + bytes[1].toUByte().toInt()
                value / 4.0
            },
            formulaExpression = "(A * 256 + B) / 4",
            requiredByteCount = 2,
            category = FormulaCategory.RPM,
            unit = "RPM"
        ),

        // 6. 16-bit little endian /4
        FormulaCandidate(
            id = "F006",
            name = "16-bit Little Endian /4",
            description = "16-bit little endian dividido por 4",
            formula = { bytes ->
                val value = (bytes[1].toUByte().toInt() * 256) + bytes[0].toUByte().toInt()
                value / 4.0
            },
            formulaExpression = "(B * 256 + A) / 4",
            requiredByteCount = 2,
            category = FormulaCategory.SIMPLE
        ),

        // 7. 16-bit porcentaje
        FormulaCandidate(
            id = "F007",
            name = "16-bit Porcentaje",
            description = "Porcentaje de precisión extendida (16-bit)",
            formula = { bytes ->
                val value = (bytes[0].toUByte().toInt() * 256) + bytes[1].toUByte().toInt()
                (value * 100.0) / 65535.0
            },
            formulaExpression = "((A * 256 + B) * 100) / 65535",
            requiredByteCount = 2,
            category = FormulaCategory.PERCENTAGE,
            unit = "%"
        ),

        // 8. 16-bit voltage (mV a V)
        FormulaCandidate(
            id = "F008",
            name = "Voltaje 16-bit",
            description = "Voltaje de milivolts a volts",
            formula = { bytes ->
                val value = (bytes[0].toUByte().toInt() * 256) + bytes[1].toUByte().toInt()
                value / 1000.0
            },
            formulaExpression = "(A * 256 + B) / 1000",
            requiredByteCount = 2,
            category = FormulaCategory.VOLTAGE,
            unit = "V"
        ),

        // 9. Signed byte (-128 a 127)
        FormulaCandidate(
            id = "F009",
            name = "Byte con Signo",
            description = "Byte con signo (-128 a 127)",
            formula = { bytes -> bytes[0].toInt().toByte().toDouble() },
            formulaExpression = "signed(A)",
            requiredByteCount = 1,
            category = FormulaCategory.SIMPLE
        ),

        // 10. Signed byte con offset
        FormulaCandidate(
            id = "F010",
            name = "Byte con Signo Offset -128",
            description = "Conversión de unsigned a signed restando 128",
            formula = { bytes -> bytes[0].toUByte().toDouble() - 128.0 },
            formulaExpression = "A - 128",
            requiredByteCount = 1,
            category = FormulaCategory.SIMPLE
        ),

        // 11. Promedio de dos bytes
        FormulaCandidate(
            id = "F011",
            name = "Promedio de 2 Bytes",
            description = "Promedio aritmético de dos bytes",
            formula = { bytes ->
                (bytes[0].toUByte().toDouble() + bytes[1].toUByte().toDouble()) / 2.0
            },
            formulaExpression = "(A + B) / 2",
            requiredByteCount = 2,
            category = FormulaCategory.SIMPLE
        ),

        // 12. Diferencial (A - B)
        FormulaCandidate(
            id = "F012",
            name = "Diferencial A-B",
            description = "Diferencia entre dos bytes",
            formula = { bytes ->
                bytes[0].toUByte().toDouble() - bytes[1].toUByte().toDouble()
            },
            formulaExpression = "A - B",
            requiredByteCount = 2,
            category = FormulaCategory.SIMPLE
        ),

        // 13. Ratio (A / B)
        FormulaCandidate(
            id = "F013",
            name = "Ratio A/B",
            description = "Ratio entre dos bytes",
            formula = { bytes ->
                val b = bytes[1].toUByte().toDouble()
                if (b != 0.0) {
                    bytes[0].toUByte().toDouble() / b
                } else {
                    0.0
                }
            },
            formulaExpression = "A / B",
            requiredByteCount = 2,
            category = FormulaCategory.RATIO
        ),

        // 14. 32-bit big endian
        FormulaCandidate(
            id = "F014",
            name = "32-bit Big Endian",
            description = "Valor de 4 bytes en big endian",
            formula = { bytes ->
                (bytes[0].toUByte().toLong() * 16777216L +
                        bytes[1].toUByte().toLong() * 65536L +
                        bytes[2].toUByte().toLong() * 256L +
                        bytes[3].toUByte().toLong()).toDouble()
            },
            formulaExpression = "(A * 16777216 + B * 65536 + C * 256 + D)",
            requiredByteCount = 4,
            category = FormulaCategory.SIMPLE
        ),

        // 15. 16-bit big endian simple
        FormulaCandidate(
            id = "F015",
            name = "16-bit Big Endian",
            description = "16-bit big endian sin división",
            formula = { bytes ->
                ((bytes[0].toUByte().toInt() * 256) + bytes[1].toUByte().toInt()).toDouble()
            },
            formulaExpression = "A * 256 + B",
            requiredByteCount = 2,
            category = FormulaCategory.SIMPLE
        ),

        // 16. Presión (kPa)
        FormulaCandidate(
            id = "F016",
            name = "Presión kPa",
            description = "Presión en kiloPascales (típico rango 0-255 kPa)",
            formula = { bytes -> bytes[0].toUByte().toDouble() },
            formulaExpression = "A",
            requiredByteCount = 1,
            category = FormulaCategory.PRESSURE,
            unit = "kPa"
        ),

        // 17. Presión con multiplicador
        FormulaCandidate(
            id = "F017",
            name = "Presión x3",
            description = "Presión con factor multiplicador de 3",
            formula = { bytes -> bytes[0].toUByte().toDouble() * 3.0 },
            formulaExpression = "A * 3",
            requiredByteCount = 1,
            category = FormulaCategory.PRESSURE,
            unit = "kPa"
        ),

        // 18. Velocidad (km/h)
        FormulaCandidate(
            id = "F018",
            name = "Velocidad km/h",
            description = "Velocidad directa en km/h",
            formula = { bytes -> bytes[0].toUByte().toDouble() },
            formulaExpression = "A",
            requiredByteCount = 1,
            category = FormulaCategory.SPEED,
            unit = "km/h"
        ),

        // 19. Tiempo en segundos (16-bit)
        FormulaCandidate(
            id = "F019",
            name = "Tiempo 16-bit",
            description = "Tiempo en segundos (16-bit big endian)",
            formula = { bytes ->
                ((bytes[0].toUByte().toInt() * 256) + bytes[1].toUByte().toInt()).toDouble()
            },
            formulaExpression = "A * 256 + B",
            requiredByteCount = 2,
            category = FormulaCategory.TIME,
            unit = "s"
        ),

        // 20. Distancia en km (16-bit)
        FormulaCandidate(
            id = "F020",
            name = "Distancia km",
            description = "Distancia en kilómetros (16-bit)",
            formula = { bytes ->
                ((bytes[0].toUByte().toInt() * 256) + bytes[1].toUByte().toInt()).toDouble()
            },
            formulaExpression = "A * 256 + B",
            requiredByteCount = 2,
            category = FormulaCategory.DISTANCE,
            unit = "km"
        ),

        // 21. Porcentaje con escala (A * 100 / 128)
        FormulaCandidate(
            id = "F021",
            name = "Porcentaje /128",
            description = "Porcentaje con escala de 128",
            formula = { bytes -> (bytes[0].toUByte().toDouble() * 100.0) / 128.0 },
            formulaExpression = "(A * 100) / 128",
            requiredByteCount = 1,
            category = FormulaCategory.PERCENTAGE,
            unit = "%"
        ),

        // 22. Flujo de aire (g/s) - MAF
        FormulaCandidate(
            id = "F022",
            name = "Flujo de Aire MAF",
            description = "Flujo masivo de aire en gramos por segundo",
            formula = { bytes ->
                val value = (bytes[0].toUByte().toInt() * 256) + bytes[1].toUByte().toInt()
                value / 100.0
            },
            formulaExpression = "(A * 256 + B) / 100",
            requiredByteCount = 2,
            category = FormulaCategory.FLOW_RATE,
            unit = "g/s"
        ),

        // 23. Ángulo de avance (grados)
        FormulaCandidate(
            id = "F023",
            name = "Ángulo con Offset",
            description = "Ángulo de avance con offset (típico timing)",
            formula = { bytes -> (bytes[0].toUByte().toDouble() / 2.0) - 64.0 },
            formulaExpression = "(A / 2) - 64",
            requiredByteCount = 1,
            category = FormulaCategory.SIMPLE,
            unit = "°"
        ),

        // 24. Tasa de inyección
        FormulaCandidate(
            id = "F024",
            name = "Tasa de Inyección",
            description = "Tasa de inyección de combustible",
            formula = { bytes ->
                val value = (bytes[0].toUByte().toInt() * 256) + bytes[1].toUByte().toInt()
                value / 128.0
            },
            formulaExpression = "(A * 256 + B) / 128",
            requiredByteCount = 2,
            category = FormulaCategory.FUEL,
            unit = "mg/stroke"
        )
    )

    /**
     * Infiere las mejores fórmulas para un conjunto de muestras.
     *
     * @param samples Lista de pares (bytes, valorEsperado opcional)
     * @param expectedValueRange Rango de valores esperados (opcional)
     * @param byteCount Número de bytes en las muestras
     * @return Lista de fórmulas candidatas rankeadas por precisión
     */
    fun inferFormula(
        samples: List<Pair<ByteArray, Double?>>,
        expectedValueRange: Pair<Double, Double>? = null,
        byteCount: Int
    ): List<FormulaCandidate> {
        Logger.d(TAG, "Infiriendo fórmulas para ${samples.size} muestras con $byteCount bytes")

        // Filtrar fórmulas aplicables según el número de bytes
        val applicableFormulas = formulaBank.filter { it.requiredByteCount <= byteCount }

        Logger.d(TAG, "Fórmulas aplicables: ${applicableFormulas.size}/${formulaBank.size}")

        // Evaluar cada fórmula
        val evaluatedFormulas = applicableFormulas.map { formula ->
            evaluateFormula(formula, samples, expectedValueRange)
        }

        // Ordenar por score descendente
        val rankedFormulas = evaluatedFormulas.sortedByDescending { it.score }

        Logger.d(TAG, "Top 3 fórmulas:")
        rankedFormulas.take(3).forEachIndexed { index, formula ->
            Logger.d(TAG, "  ${index + 1}. ${formula.name}: score=${String.format("%.4f", formula.score)}")
        }

        return rankedFormulas
    }

    /**
     * Evalúa una fórmula contra un conjunto de muestras.
     *
     * @param formula Fórmula a evaluar
     * @param samples Muestras de prueba
     * @param expectedValueRange Rango esperado de valores
     * @return Fórmula con score calculado y resultados de ejemplo
     */
    private fun evaluateFormula(
        formula: FormulaCandidate,
        samples: List<Pair<ByteArray, Double?>>,
        expectedValueRange: Pair<Double, Double>?
    ): FormulaCandidate {
        val sampleResults = mutableListOf<SampleResult>()
        var totalScore = 0.0
        var validSamples = 0

        samples.forEach { (bytes, expectedValue) ->
            formula.apply(bytes)?.let { calculatedValue ->
                val error = expectedValue?.let { calculatedValue - it }

                sampleResults.add(
                    SampleResult(
                        inputBytes = bytes,
                        calculatedValue = calculatedValue,
                        expectedValue = expectedValue,
                        error = error
                    )
                )

                // Calcular score para esta muestra
                val sampleScore = calculateSampleScore(
                    calculatedValue = calculatedValue,
                    expectedValue = expectedValue,
                    expectedRange = expectedValueRange
                )

                totalScore += sampleScore
                validSamples++
            }
        }

        // Score promedio
        val avgScore = if (validSamples > 0) totalScore / validSamples else 0.0

        // Aplicar penalizaciones y bonificaciones
        val finalScore = applyScoreAdjustments(
            baseScore = avgScore,
            formula = formula,
            sampleResults = sampleResults,
            expectedRange = expectedValueRange
        )

        return formula.withResults(
            newScore = finalScore.coerceIn(0.0, 1.0),
            newSamples = sampleResults.take(10) // Guardar solo 10 ejemplos
        )
    }

    /**
     * Calcula el score para una muestra individual.
     */
    private fun calculateSampleScore(
        calculatedValue: Double,
        expectedValue: Double?,
        expectedRange: Pair<Double, Double>?
    ): Double {
        // Si hay valor esperado, usar error relativo
        if (expectedValue != null) {
            if (expectedValue == 0.0) {
                return if (calculatedValue == 0.0) 1.0 else 0.0
            }

            val relativeError = abs((calculatedValue - expectedValue) / expectedValue)
            return (1.0 - relativeError).coerceIn(0.0, 1.0)
        }

        // Si hay rango esperado, verificar si está dentro
        if (expectedRange != null) {
            val (min, max) = expectedRange
            return if (calculatedValue in min..max) 0.8 else 0.2
        }

        // Sin referencias, dar score neutro
        return 0.5
    }

    /**
     * Aplica ajustes al score basados en heurísticas.
     */
    private fun applyScoreAdjustments(
        baseScore: Double,
        formula: FormulaCandidate,
        sampleResults: List<SampleResult>,
        expectedRange: Pair<Double, Double>?
    ): Double {
        var adjustedScore = baseScore

        // Bonificación: valores están en rango esperado
        if (expectedRange != null) {
            val (min, max) = expectedRange
            val inRangeCount = sampleResults.count { it.calculatedValue in min..max }
            val inRangeRatio = inRangeCount.toDouble() / sampleResults.size
            adjustedScore *= (0.7 + inRangeRatio * 0.3)
        }

        // Bonificación: baja varianza en errores (consistencia)
        if (sampleResults.size > 1) {
            val errors = sampleResults.mapNotNull { it.error }
            if (errors.isNotEmpty()) {
                val errorStdDev = calculateStdDev(errors)
                val consistencyBonus = (1.0 - (errorStdDev / 100.0).coerceIn(0.0, 0.3))
                adjustedScore *= consistencyBonus
            }
        }

        // Penalización: valores negativos inesperados en categorías que no lo permiten
        if (formula.category in listOf(
                FormulaCategory.PERCENTAGE,
                FormulaCategory.SPEED,
                FormulaCategory.RPM,
                FormulaCategory.PRESSURE
            )
        ) {
            val negativeCount = sampleResults.count { it.calculatedValue < 0 }
            if (negativeCount > 0) {
                val penalty = negativeCount.toDouble() / sampleResults.size
                adjustedScore *= (1.0 - penalty * 0.5)
            }
        }

        // Penalización: valores muy grandes (posibles overflows)
        val veryLargeCount = sampleResults.count { it.calculatedValue > 100000 }
        if (veryLargeCount > sampleResults.size / 2) {
            adjustedScore *= 0.5
        }

        return adjustedScore
    }

    /**
     * Calcula la desviación estándar de una lista de valores.
     */
    private fun calculateStdDev(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0

        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }

    /**
     * Obtiene todas las fórmulas del banco.
     */
    fun getAllFormulas(): List<FormulaCandidate> = formulaBank

    /**
     * Obtiene fórmulas por categoría.
     */
    fun getFormulasByCategory(category: FormulaCategory): List<FormulaCandidate> {
        return formulaBank.filter { it.category == category }
    }

    /**
     * Obtiene una fórmula por ID.
     */
    fun getFormulaById(id: String): FormulaCandidate? {
        return formulaBank.find { it.id == id }
    }
}
