package com.fleetcare.obd.domain.usecase

import com.fleetcare.obd.data.remote.FirebaseDataSource
import com.fleetcare.obd.domain.model.ErrorLog
import com.fleetcare.obd.domain.model.FirebaseSyncStats
import com.fleetcare.obd.domain.model.VehicleData
import com.fleetcare.obd.domain.repository.VehicleRepository
import com.fleetcare.obd.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use Case para enviar datos de telemetría a Firebase automáticamente.
 *
 * Características:
 * - Envío automático cada 2 segundos
 * - Cola de retry para datos no enviados (modo offline)
 * - Manejo de sesiones
 * - Limpieza automática de datos antiguos
 */
@Singleton
class SendDataToFirebaseUseCase @Inject constructor(
    private val firebaseDataSource: FirebaseDataSource,
    private val vehicleRepository: VehicleRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var syncJob: Job? = null
    private var sessionId: String? = null
    private var currentVehicleId: String? = null

    // Cola de datos pendientes de envío
    private val pendingQueue = ConcurrentLinkedQueue<PendingData>()

    // Estadísticas de sincronización
    private val _syncStats = MutableStateFlow(FirebaseSyncStats())
    val syncStats: StateFlow<FirebaseSyncStats> = _syncStats.asStateFlow()

    // Contadores internos
    private var totalAttempts = 0
    private var successfulWrites = 0
    private var failedWrites = 0
    private val recentErrors = mutableListOf<ErrorLog>()

    /**
     * Inicia el envío automático de datos a Firebase.
     *
     * @param vehicleId ID único del vehículo (MAC address)
     * @param vehicleName Nombre del vehículo
     * @return Result indicando éxito o error
     */
    suspend fun start(vehicleId: String, vehicleName: String): Result<Unit> {
        return try {
            if (syncJob?.isActive == true) {
                Logger.d("Sincronización con Firebase ya está activa")
                return Result.success(Unit)
            }

            currentVehicleId = vehicleId
            sessionId = UUID.randomUUID().toString()

            // Crear sesión en Firebase
            val sessionStartTime = System.currentTimeMillis()
            firebaseDataSource.createOrUpdateSession(
                vehicleId = vehicleId,
                sessionId = sessionId!!,
                startTime = sessionStartTime
            )

            // Actualizar info del vehículo
            firebaseDataSource.updateVehicleInfo(vehicleId, vehicleName)

            // Iniciar sincronización
            startSyncJob(vehicleId)

            // Actualizar estadísticas
            updateStats()

            Logger.d("Sincronización con Firebase iniciada: vehicleId=$vehicleId, sessionId=$sessionId")
            Result.success(Unit)

        } catch (e: Exception) {
            Logger.e(e, "Error al iniciar sincronización con Firebase")
            Result.failure(e)
        }
    }

    /**
     * Detiene el envío automático.
     */
    fun stop() {
        Logger.d("Deteniendo sincronización con Firebase...")

        syncJob?.cancel()
        syncJob = null

        // Finalizar sesión
        sessionId?.let { session ->
            currentVehicleId?.let { vehicle ->
                scope.launch {
                    firebaseDataSource.endSession(
                        vehicleId = vehicle,
                        sessionId = session,
                        endTime = System.currentTimeMillis()
                    )
                }
            }
        }

        // Intentar enviar datos pendientes antes de cerrar
        if (pendingQueue.isNotEmpty()) {
            Logger.d("Intentando enviar ${pendingQueue.size} datos pendientes...")
            scope.launch {
                processPendingQueue()
            }
        }

        sessionId = null
        currentVehicleId = null
    }

    /**
     * Inicia el job de sincronización que observa el Flow de datos.
     */
    private fun startSyncJob(vehicleId: String) {
        Logger.d("🚀 FIREBASE_DEBUG [UseCase]: Iniciando syncJob para vehicleId=$vehicleId")
        syncJob = scope.launch {
            Logger.d("   ├─ SyncJob coroutine iniciada, observando vehicleDataFlow...")
            vehicleRepository.vehicleDataFlow.collectLatest { data ->
                Logger.d("   ├─ Nuevo dato recibido del Flow: hasData=${data.hasData}")
                if (data.hasData) {
                    Logger.d("   ├─ Dato válido, enviando a Firebase...")
                    sendDataWithRetry(vehicleId, data)
                } else {
                    Logger.w("   └─ ⚠️ Dato sin información útil (hasData=false), ignorando...")
                }
            }
        }
        Logger.d("   └─ SyncJob configurado ✓")
    }

    /**
     * Envía datos a Firebase con manejo de errores.
     */
    private suspend fun sendDataWithRetry(vehicleId: String, data: VehicleData) {
        totalAttempts++
        updateStats()

        try {
            Logger.d("📤 FIREBASE_DEBUG [UseCase]: Procesando datos para envío...")
            Logger.d("   ├─ hasData: ${data.hasData}")
            Logger.d("   ├─ vehicleId: $vehicleId")
            Logger.d("   ├─ sessionId: $sessionId")
            Logger.d("   ├─ Intento #$totalAttempts (exitosos: $successfulWrites, fallidos: $failedWrites)")
            Logger.d("   └─ Cola pendiente: ${pendingQueue.size} items")

            // Primero intentar enviar datos pendientes
            if (pendingQueue.isNotEmpty()) {
                Logger.d("   ├─ Procesando cola de pendientes primero...")
                processPendingQueue()
            }

            // Enviar datos actuales
            Logger.d("   ├─ Llamando a firebaseDataSource.sendTelemetry()...")
            val result = firebaseDataSource.sendTelemetry(
                vehicleId = vehicleId,
                sessionId = sessionId!!,
                data = data
            )

            if (result.isFailure) {
                val exception = result.exceptionOrNull()
                failedWrites++

                // Registrar error
                recordError(
                    errorType = exception?.javaClass?.simpleName ?: "Unknown",
                    errorMessage = exception?.message ?: "Sin mensaje de error",
                    stackTrace = exception?.stackTraceToString()
                )

                if (exception != null) {
                    Logger.e(exception, "❌ FIREBASE_DEBUG [UseCase]: sendTelemetry() retornó FAILURE")
                    Logger.e(exception, "   ├─ Excepción: ${exception.javaClass.simpleName}")
                    Logger.e(exception, "   ├─ Mensaje: ${exception.message}")
                    Logger.e(exception, "   ├─ Tasa de éxito: ${String.format("%.1f", _syncStats.value.successRate)}%")
                    Logger.e(exception, "   └─ Agregando a cola de pendientes...")
                } else {
                    Logger.w("❌ FIREBASE_DEBUG [UseCase]: sendTelemetry() retornó FAILURE sin excepción")
                    Logger.w("   └─ Tasa de éxito: ${String.format("%.1f", _syncStats.value.successRate)}%")
                }

                // Si falla, agregar a cola de pendientes
                addToPendingQueue(vehicleId, data)
                updateStats()
            } else {
                successfulWrites++
                Logger.d("✅ FIREBASE_DEBUG [UseCase]: sendTelemetry() SUCCESS - Guardando en caché local...")
                Logger.d("   ├─ Tasa de éxito: ${String.format("%.1f", _syncStats.value.successRate)}%")

                // También guardar en caché local
                vehicleRepository.saveToCache(
                    data = data,
                    vehicleId = vehicleId,
                    sessionId = sessionId!!
                )

                Logger.d("   └─ Caché local actualizada ✓")
                updateStats()
            }

        } catch (e: Exception) {
            failedWrites++

            // Registrar error
            recordError(
                errorType = e.javaClass.simpleName,
                errorMessage = e.message ?: "Sin mensaje de error",
                stackTrace = e.stackTraceToString()
            )

            Logger.e(e, "❌ FIREBASE_DEBUG [UseCase]: EXCEPCIÓN capturada en sendDataWithRetry()")
            Logger.e(e, "   ├─ Tipo: ${e.javaClass.simpleName}")
            Logger.e(e, "   ├─ Mensaje: ${e.message}")
            Logger.e(e, "   └─ Stack trace:")
            e.printStackTrace()
            addToPendingQueue(vehicleId, data)
            updateStats()
        }
    }

    /**
     * Agrega datos a la cola de pendientes.
     */
    private fun addToPendingQueue(vehicleId: String, data: VehicleData) {
        if (pendingQueue.size < MAX_PENDING_QUEUE_SIZE) {
            pendingQueue.offer(
                PendingData(
                    vehicleId = vehicleId,
                    sessionId = sessionId!!,
                    data = data,
                    attempts = 0
                )
            )
            Logger.w("Datos agregados a cola de pendientes. Total: ${pendingQueue.size}")
        } else {
            Logger.w("Cola de pendientes llena. Descartando dato más antiguo.")
            pendingQueue.poll() // Remover el más antiguo
            pendingQueue.offer(
                PendingData(
                    vehicleId = vehicleId,
                    sessionId = sessionId!!,
                    data = data,
                    attempts = 0
                )
            )
        }
    }

    /**
     * Procesa la cola de datos pendientes.
     */
    private suspend fun processPendingQueue() {
        val iterator = pendingQueue.iterator()
        val toRetry = mutableListOf<PendingData>()

        while (iterator.hasNext()) {
            val pending = iterator.next()

            if (pending.attempts >= MAX_RETRY_ATTEMPTS) {
                Logger.w("Dato descartado después de ${pending.attempts} intentos")
                iterator.remove()
                continue
            }

            val result = firebaseDataSource.sendTelemetry(
                vehicleId = pending.vehicleId,
                sessionId = pending.sessionId,
                data = pending.data
            )

            if (result.isSuccess) {
                Logger.d("Dato pendiente enviado exitosamente")
                iterator.remove()
            } else {
                pending.attempts++
                toRetry.add(pending)
                iterator.remove()
            }
        }

        // Re-agregar los que fallaron
        toRetry.forEach { pendingQueue.offer(it) }

        if (pendingQueue.isEmpty()) {
            Logger.d("Cola de pendientes procesada completamente")
        } else {
            Logger.w("Quedan ${pendingQueue.size} datos pendientes")
        }
    }

    /**
     * Limpia datos antiguos de Firebase (llamar periódicamente).
     */
    suspend fun cleanOldData(vehicleId: String, olderThanHours: Long = 48): Result<Unit> {
        return firebaseDataSource.cleanOldTelemetry(vehicleId, olderThanHours)
    }

    /**
     * Obtiene el tamaño actual de la cola de pendientes.
     */
    fun getPendingQueueSize(): Int = pendingQueue.size

    /**
     * Indica si hay sincronización activa.
     */
    fun isActive(): Boolean = syncJob?.isActive == true

    /**
     * Limpia recursos.
     */
    fun cleanup() {
        stop()
        scope.cancel()
    }

    /**
     * Actualiza las estadísticas de sincronización.
     */
    private fun updateStats() {
        _syncStats.value = FirebaseSyncStats(
            isActive = isActive(),
            sessionId = sessionId,
            vehicleId = currentVehicleId,
            totalAttempts = totalAttempts,
            successfulWrites = successfulWrites,
            failedWrites = failedWrites,
            pendingQueueSize = pendingQueue.size,
            lastSuccessTimestamp = if (successfulWrites > 0) System.currentTimeMillis() else null,
            lastErrorTimestamp = recentErrors.lastOrNull()?.timestamp,
            lastError = recentErrors.lastOrNull()?.errorMessage,
            lastErrorType = recentErrors.lastOrNull()?.errorType,
            recentErrors = recentErrors.takeLast(10)
        )
    }

    /**
     * Registra un error en el historial.
     */
    private fun recordError(errorType: String, errorMessage: String, stackTrace: String?) {
        val errorLog = ErrorLog(
            timestamp = System.currentTimeMillis(),
            errorType = errorType,
            errorMessage = errorMessage,
            stackTrace = stackTrace
        )

        recentErrors.add(errorLog)

        // Mantener solo los últimos 20 errores
        if (recentErrors.size > 20) {
            recentErrors.removeAt(0)
        }
    }

    /**
     * Resetea las estadísticas.
     */
    fun resetStats() {
        totalAttempts = 0
        successfulWrites = 0
        failedWrites = 0
        recentErrors.clear()
        updateStats()
    }

    companion object {
        private const val MAX_PENDING_QUEUE_SIZE = 100
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    /**
     * Clase interna para datos pendientes de envío.
     */
    private data class PendingData(
        val vehicleId: String,
        val sessionId: String,
        val data: VehicleData,
        var attempts: Int = 0
    )
}
