package com.fleetcare.obd.domain.usecase

import com.fleetcare.obd.data.obd.SupportedPIDsDetector
import com.fleetcare.obd.domain.model.SupportedPIDsBitmap
import com.fleetcare.obd.domain.repository.SupportedPIDsRepository
import com.fleetcare.obd.utils.Logger
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use Case para detectar PIDs soportados por el vehículo.
 *
 * Orquesta la detección de PIDs mediante lectura de bitmaps OBD-II
 * y gestiona el caché para evitar detecciones innecesarias.
 *
 * Flujo:
 * 1. Verificar si existe caché válido para el vehículo
 * 2. Si existe y no está obsoleto, retornar del caché
 * 3. Si no existe o está obsoleto, ejecutar detección completa
 * 4. Guardar resultados en caché
 * 5. Retornar bitmap de PIDs soportados
 *
 * Sprint 2: Detección automática de PIDs soportados
 */
@Singleton
class DetectSupportedPIDsUseCase @Inject constructor(
    private val supportedPIDsDetector: SupportedPIDsDetector,
    private val supportedPIDsRepository: SupportedPIDsRepository
) {

    /**
     * Detecta los PIDs soportados por el vehículo.
     *
     * @param vehicleId MAC del adaptador Bluetooth
     * @param vin VIN del vehículo (opcional)
     * @param forceRefresh Forzar nueva detección ignorando caché
     * @param maxCacheAgeDays Edad máxima del caché en días (default: 30)
     * @param timeoutSeconds Timeout para toda la detección (default: 30)
     * @return Result con SupportedPIDsBitmap o error
     */
    suspend fun execute(
        vehicleId: String,
        vin: String? = null,
        forceRefresh: Boolean = false,
        maxCacheAgeDays: Int = 30,
        timeoutSeconds: Long = 30
    ): Result<SupportedPIDsBitmap> {
        return try {
            Logger.d("DetectSupportedPIDsUseCase: Iniciando para vehicleId=$vehicleId, forceRefresh=$forceRefresh")

            // Verificar si existe caché válido
            if (!forceRefresh) {
                val hasCacheResult = supportedPIDsRepository.hasCachedPIDs(vehicleId)
                val hasCache = hasCacheResult.getOrNull() ?: false

                if (hasCache) {
                    // Verificar si el caché necesita actualización
                    val needsRefreshResult = supportedPIDsRepository.needsRefresh(vehicleId, maxCacheAgeDays)
                    val needsRefresh = needsRefreshResult.getOrNull() ?: true

                    if (!needsRefresh) {
                        Logger.d("Usando PIDs del caché (edad < $maxCacheAgeDays días)")

                        val cachedResult = supportedPIDsRepository.getSupportedPIDsSync(vehicleId)
                        val cached = cachedResult.getOrNull()

                        if (cached != null) {
                            // Actualizar VIN si cambió
                            if (vin != null && vin != cached.vin) {
                                supportedPIDsRepository.updateVIN(vehicleId, vin)
                            }

                            return Result.success(cached)
                        }
                    } else {
                        Logger.d("Caché obsoleto (> $maxCacheAgeDays días), ejecutando nueva detección")
                    }
                } else {
                    Logger.d("No existe caché, ejecutando detección completa")
                }
            } else {
                Logger.d("Forzando nueva detección (forceRefresh=true)")
            }

            // Ejecutar detección con timeout
            val detectedBitmap = withTimeout(timeoutSeconds * 1000) {
                val detectionResult = supportedPIDsDetector.detectSupportedPIDs(vehicleId, vin)

                if (detectionResult.isFailure) {
                    throw detectionResult.exceptionOrNull() ?: Exception("Error en detección de PIDs")
                }

                detectionResult.getOrNull() ?: throw Exception("Detección retornó null")
            }

            Logger.d("Detección completada: ${detectedBitmap.getTotalSupportedCount()} PIDs encontrados")

            // Guardar en caché
            val saveResult = supportedPIDsRepository.saveSupportedPIDs(detectedBitmap)
            if (saveResult.isFailure) {
                Logger.w("Error al guardar PIDs en caché: ${saveResult.exceptionOrNull()?.message}")
                // No fallar si el guardado falla, solo advertir
            }

            Result.success(detectedBitmap)

        } catch (e: Exception) {
            Logger.e(e, "Error en DetectSupportedPIDsUseCase")
            Result.failure(e)
        }
    }

    /**
     * Obtiene los PIDs soportados del caché sin ejecutar detección.
     *
     * @param vehicleId MAC del adaptador
     * @return Result con bitmap o null si no hay caché
     */
    suspend fun getCachedPIDs(vehicleId: String): Result<SupportedPIDsBitmap?> {
        return try {
            supportedPIDsRepository.getSupportedPIDsSync(vehicleId)
        } catch (e: Exception) {
            Logger.e(e, "Error al obtener PIDs del caché")
            Result.failure(e)
        }
    }

    /**
     * Invalida el caché de un vehículo para forzar re-detección.
     *
     * @param vehicleId MAC del adaptador
     * @return Result indicando éxito
     */
    suspend fun invalidateCache(vehicleId: String): Result<Unit> {
        return try {
            val deleteResult = supportedPIDsRepository.deleteSupportedPIDs(vehicleId)
            if (deleteResult.isSuccess) {
                Logger.d("Caché invalidado para vehicleId: $vehicleId")
                Result.success(Unit)
            } else {
                Result.failure(deleteResult.exceptionOrNull() ?: Exception("Error al invalidar caché"))
            }
        } catch (e: Exception) {
            Logger.e(e, "Error al invalidar caché")
            Result.failure(e)
        }
    }

    /**
     * Limpia cachés antiguos para liberar espacio.
     *
     * @param maxAgeDays Edad máxima en días
     * @return Result con número de cachés eliminados
     */
    suspend fun cleanOldCaches(maxAgeDays: Int = 90): Result<Int> {
        return try {
            val deleteResult = supportedPIDsRepository.deleteOldCaches(maxAgeDays)
            if (deleteResult.isSuccess) {
                val deleted = deleteResult.getOrNull() ?: 0
                Logger.d("Cachés antiguos eliminados: $deleted")
                Result.success(deleted)
            } else {
                Result.failure(deleteResult.exceptionOrNull() ?: Exception("Error al limpiar cachés"))
            }
        } catch (e: Exception) {
            Logger.e(e, "Error al limpiar cachés antiguos")
            Result.failure(e)
        }
    }
}
