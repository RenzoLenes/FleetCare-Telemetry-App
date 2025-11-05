package com.fleetcare.obd.domain.usecase

import com.fleetcare.obd.data.analysis.DynamicPIDAnalyzer
import com.fleetcare.obd.domain.model.PIDPattern
import com.fleetcare.obd.domain.repository.RawOBDResponseRepository
import com.fleetcare.obd.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Caso de uso para analizar patrones en respuestas RAW de PIDs.
 *
 * Sprint 3: Motor de Análisis de Patrones
 *
 * Este use case coordina el proceso completo de análisis:
 * 1. Obtener historial de respuestas RAW de un PID específico
 * 2. Ejecutar análisis estadístico y de patrones
 * 3. Inferir fórmulas candidatas
 * 4. Retornar patrón completo con sugerencias
 *
 * @property rawOBDResponseRepository Repositorio de respuestas RAW
 * @property pidAnalyzer Analizador de patrones
 */
class AnalyzePIDPatternsUseCase @Inject constructor(
    private val rawOBDResponseRepository: RawOBDResponseRepository,
    private val pidAnalyzer: DynamicPIDAnalyzer
) {

    companion object {
        private const val TAG = "AnalyzePIDPatternsUseCase"
        private const val DEFAULT_MAX_SAMPLES = 100
        private const val MIN_SAMPLES_RECOMMENDED = 20
    }

    /**
     * Analiza el patrón de un PID específico.
     *
     * @param command Comando OBD del PID (ej: "010C" para RPM)
     * @param vehicleId ID del vehículo (opcional, para filtrar por vehículo)
     * @param maxSamples Número máximo de muestras a analizar
     * @param timeRangeMs Rango de tiempo en milisegundos (null = todas las muestras)
     * @return Result con PIDPattern o error
     */
    suspend fun execute(
        command: String,
        vehicleId: String? = null,
        maxSamples: Int = DEFAULT_MAX_SAMPLES,
        timeRangeMs: Long? = null
    ): Result<PIDPattern> = withContext(Dispatchers.Default) {
        try {
            Logger.d(TAG, "Analizando patrón para comando: $command")

            // Obtener respuestas RAW del repositorio
            val responses = if (timeRangeMs != null) {
                val endTime = System.currentTimeMillis()
                val startTime = endTime - timeRangeMs

                rawOBDResponseRepository.getSuccessfulResponsesInRange(
                    command = command,
                    startTime = startTime,
                    endTime = endTime
                ).getOrNull() ?: emptyList()
            } else {
                rawOBDResponseRepository.getLatestResponses(
                    command = command,
                    limit = maxSamples
                ).getOrNull() ?: emptyList()
            }

            // Filtrar por vehículo si se especifica
            val filteredResponses = if (vehicleId != null) {
                responses.filter { it.vehicleId == vehicleId }
            } else {
                responses
            }

            if (filteredResponses.isEmpty()) {
                Logger.w(TAG, "No hay respuestas RAW para el comando $command")
                return@withContext Result.failure(
                    IllegalStateException("No hay datos RAW disponibles para analizar. " +
                            "Asegúrate de que la captura RAW esté habilitada en Settings.")
                )
            }

            Logger.d(TAG, "Encontradas ${filteredResponses.size} respuestas para análisis")

            if (filteredResponses.size < MIN_SAMPLES_RECOMMENDED) {
                Logger.w(TAG, "Pocas muestras (${filteredResponses.size}). " +
                        "Se recomiendan al menos $MIN_SAMPLES_RECOMMENDED para análisis preciso.")
            }

            // Filtrar solo respuestas exitosas
            val successfulResponses = filteredResponses.filter { it.parseSuccess }

            if (successfulResponses.isEmpty()) {
                Logger.e(TAG, "Todas las respuestas fueron fallidas")
                return@withContext Result.failure(
                    IllegalStateException("No hay respuestas exitosas para analizar")
                )
            }

            Logger.d(TAG, "Respuestas exitosas: ${successfulResponses.size}/${filteredResponses.size}")

            // Limitar número de muestras para evitar procesamiento excesivo
            val samplesToAnalyze = if (successfulResponses.size > maxSamples) {
                Logger.d(TAG, "Limitando análisis a $maxSamples muestras (de ${successfulResponses.size})")
                // Tomar muestras distribuidas uniformemente
                val step = successfulResponses.size / maxSamples
                successfulResponses.filterIndexed { index, _ -> index % step == 0 }.take(maxSamples)
            } else {
                successfulResponses
            }

            // Ejecutar análisis
            val analysisResult = pidAnalyzer.analyzePattern(samplesToAnalyze)

            analysisResult.onSuccess { pattern ->
                Logger.d(TAG, "Análisis completado exitosamente")
                Logger.d(TAG, pattern.toSummary())
            }.onFailure { error ->
                Logger.e(error, "Error durante el análisis")
            }

            analysisResult
        } catch (e: Exception) {
            Logger.e(e, "Error ejecutando análisis de patrones")
            Result.failure(e)
        }
    }

    /**
     * Analiza el patrón de un PID con valor conocido para validación.
     *
     * Esta versión permite proporcionar muestras con valores esperados,
     * útil para validar fórmulas con datos conocidos.
     *
     * @param command Comando OBD del PID
     * @param knownValues Mapa de timestamp a valor esperado
     * @return Result con PIDPattern incluyendo precisión de fórmulas
     */
    suspend fun executeWithKnownValues(
        command: String,
        knownValues: Map<Long, Double>
    ): Result<PIDPattern> = withContext(Dispatchers.Default) {
        try {
            Logger.d(TAG, "Analizando patrón con ${knownValues.size} valores conocidos")

            // Obtener todas las respuestas
            val allResponses = rawOBDResponseRepository.getLatestResponses(
                command = command,
                limit = DEFAULT_MAX_SAMPLES
            ).getOrNull() ?: emptyList()

            if (allResponses.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("No hay datos RAW disponibles")
                )
            }

            // Filtrar solo respuestas exitosas
            val successfulResponses = allResponses.filter { it.parseSuccess }

            // Ejecutar análisis básico
            val analysisResult = pidAnalyzer.analyzePattern(successfulResponses)

            analysisResult.onSuccess { pattern ->
                // Mejorar análisis con valores conocidos
                val enhancedPattern = enhancePatternWithKnownValues(
                    pattern = pattern,
                    responses = successfulResponses,
                    knownValues = knownValues
                )

                Logger.d(TAG, "Análisis mejorado con valores conocidos completado")
                return@withContext Result.success(enhancedPattern)
            }

            analysisResult
        } catch (e: Exception) {
            Logger.e(e, "Error en análisis con valores conocidos")
            Result.failure(e)
        }
    }

    /**
     * Mejora el patrón usando valores conocidos para recalcular scores de fórmulas.
     */
    private fun enhancePatternWithKnownValues(
        pattern: PIDPattern,
        responses: List<com.fleetcare.obd.domain.model.RawOBDResponse>,
        knownValues: Map<Long, Double>
    ): PIDPattern {
        // Crear samples con valores esperados
        val samplesWithExpected = responses.mapNotNull { response ->
            knownValues[response.timestamp]?.let { expectedValue ->
                Pair(response.dataBytes, expectedValue)
            }
        }

        if (samplesWithExpected.isEmpty()) {
            Logger.w(TAG, "No se pudieron emparejar respuestas con valores conocidos")
            return pattern
        }

        Logger.d(TAG, "Emparejadas ${samplesWithExpected.size} respuestas con valores conocidos")

        // Re-evaluar fórmulas con valores esperados
        val reEvaluatedFormulas = pattern.suggestedFormulas.map { formula ->
            // Calcular RMSE y MAPE
            val rmse = formula.calculateRMSE(samplesWithExpected)
            val mape = formula.calculateMAPE(samplesWithExpected)

            // Calcular nuevo score basado en error
            val newScore = if (rmse != null && rmse > 0) {
                // Score inverso al RMSE normalizado
                val normalizedRMSE = rmse / (pattern.valueRange.second - pattern.valueRange.first + 1)
                (1.0 - normalizedRMSE).coerceIn(0.0, 1.0)
            } else {
                formula.score
            }

            Logger.d(TAG, "${formula.name}: RMSE=$rmse, MAPE=$mape, newScore=$newScore")

            formula.copy(score = newScore)
        }.sortedByDescending { it.score }

        // Recalcular confianza
        val topScore = reEvaluatedFormulas.firstOrNull()?.score ?: 0.0
        val enhancedConfidence = (pattern.confidence * 0.7 + topScore * 0.3).coerceIn(0.0, 1.0)

        return pattern.copy(
            suggestedFormulas = reEvaluatedFormulas,
            confidence = enhancedConfidence
        )
    }

    /**
     * Obtiene estadísticas rápidas de un PID sin análisis completo.
     *
     * @param command Comando OBD del PID
     * @return Map con estadísticas básicas
     */
    suspend fun getQuickStats(command: String): Result<Map<String, Any>> = withContext(Dispatchers.Default) {
        try {
            val responses = rawOBDResponseRepository.getLatestResponses(
                command = command,
                limit = 50
            ).getOrNull() ?: emptyList()

            if (responses.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("No hay datos disponibles")
                )
            }

            val successfulResponses = responses.filter { it.parseSuccess }
            val byteCount = pidAnalyzer.detectByteCount(successfulResponses)

            val stats = mapOf(
                "totalSamples" to responses.size,
                "successfulSamples" to successfulResponses.size,
                "failedSamples" to (responses.size - successfulResponses.size),
                "byteCount" to byteCount,
                "timeSpanMs" to (responses.maxOfOrNull { it.timestamp } ?: 0L) -
                        (responses.minOfOrNull { it.timestamp } ?: 0L),
                "avgLatencyMs" to responses.mapNotNull { it.latencyMs }.average()
            )

            Result.success(stats)
        } catch (e: Exception) {
            Logger.e(e, "Error obteniendo estadísticas rápidas")
            Result.failure(e)
        }
    }

    /**
     * Compara el patrón de un PID entre diferentes vehículos.
     *
     * @param command Comando OBD del PID
     * @param vehicleIds Lista de IDs de vehículos a comparar
     * @return Map de vehicleId a PIDPattern
     */
    suspend fun compareAcrossVehicles(
        command: String,
        vehicleIds: List<String>
    ): Result<Map<String, PIDPattern>> = withContext(Dispatchers.Default) {
        try {
            val patterns = mutableMapOf<String, PIDPattern>()

            vehicleIds.forEach { vehicleId ->
                val result = execute(
                    command = command,
                    vehicleId = vehicleId,
                    maxSamples = 50
                )

                result.onSuccess { pattern ->
                    patterns[vehicleId] = pattern
                }
            }

            if (patterns.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("No se pudo analizar ningún vehículo")
                )
            }

            Logger.d(TAG, "Comparación completada para ${patterns.size} vehículos")

            Result.success(patterns)
        } catch (e: Exception) {
            Logger.e(e, "Error comparando patrones entre vehículos")
            Result.failure(e)
        }
    }
}
