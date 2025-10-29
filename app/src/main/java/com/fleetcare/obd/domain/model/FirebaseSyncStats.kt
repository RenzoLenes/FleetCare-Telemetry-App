package com.fleetcare.obd.domain.model

/**
 * Estadísticas de sincronización con Firebase para diagnóstico.
 */
data class FirebaseSyncStats(
    val isActive: Boolean = false,
    val sessionId: String? = null,
    val vehicleId: String? = null,
    val totalAttempts: Int = 0,
    val successfulWrites: Int = 0,
    val failedWrites: Int = 0,
    val pendingQueueSize: Int = 0,
    val lastSuccessTimestamp: Long? = null,
    val lastErrorTimestamp: Long? = null,
    val lastError: String? = null,
    val lastErrorType: String? = null,
    val recentErrors: List<ErrorLog> = emptyList()
) {
    val successRate: Float
        get() = if (totalAttempts > 0) {
            (successfulWrites.toFloat() / totalAttempts.toFloat()) * 100f
        } else {
            0f
        }

    val hasErrors: Boolean
        get() = failedWrites > 0 || lastError != null
}

/**
 * Log de error individual.
 */
data class ErrorLog(
    val timestamp: Long,
    val errorType: String,
    val errorMessage: String,
    val stackTrace: String? = null
)
