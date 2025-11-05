package com.fleetcare.obd.data.repository

import com.fleetcare.obd.data.local.dao.UniversalScanDao
import com.fleetcare.obd.data.local.database.Converters
import com.fleetcare.obd.data.local.entity.ScanResultEntity
import com.fleetcare.obd.data.local.entity.ScanSessionEntity
import com.fleetcare.obd.domain.model.*
import com.fleetcare.obd.domain.repository.UniversalScanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación del repositorio de escaneo universal.
 */
@Singleton
class UniversalScanRepositoryImpl @Inject constructor(
    private val dao: UniversalScanDao
) : UniversalScanRepository {

    override suspend fun createSession(session: ScanSession): String {
        val entity = ScanSessionEntity.fromDomain(session)
        dao.insertSession(entity)
        return session.sessionId
    }

    override suspend fun getSession(sessionId: String): ScanSession? {
        val sessionEntity = dao.getSessionById(sessionId) ?: return null
        val results = dao.getResultsBySession(sessionId).first().map { it.toDomain() }
        return sessionEntity.toDomain(results)
    }

    override fun getSessionsByVehicle(vehicleId: String): Flow<List<ScanSession>> {
        return dao.getSessionsByVehicle(vehicleId).map { sessions ->
            sessions.map { sessionEntity ->
                val results = dao.getResultsBySession(sessionEntity.sessionId).first().map { it.toDomain() }
                sessionEntity.toDomain(results)
            }
        }
    }

    override suspend fun getLatestSession(vehicleId: String): ScanSession? {
        val sessionEntity = dao.getLatestSession(vehicleId) ?: return null
        val results = dao.getResultsBySession(sessionEntity.sessionId).first().map { it.toDomain() }
        return sessionEntity.toDomain(results)
    }

    override suspend fun updateSession(session: ScanSession) {
        val entity = ScanSessionEntity.fromDomain(session)
        dao.updateSession(entity)
    }

    override suspend fun updateSessionState(sessionId: String, state: ScannerState) {
        dao.updateSessionState(sessionId, state.name)
    }

    override suspend fun addResults(sessionId: String, results: List<ScanResult>) {
        val entities = results.map { ScanResultEntity.fromDomain(it, sessionId) }
        dao.insertResults(entities)
    }

    override fun getResults(sessionId: String): Flow<List<ScanResult>> {
        return dao.getResultsBySession(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getFilteredResults(
        sessionId: String,
        mode: String?,
        successOnly: Boolean
    ): Flow<List<ScanResult>> {
        return if (mode != null && successOnly) {
            dao.getResultsBySessionModeAndSuccess(sessionId, mode, true).map { it.map { e -> e.toDomain() } }
        } else if (mode != null) {
            dao.getResultsBySessionAndMode(sessionId, mode).map { it.map { e -> e.toDomain() } }
        } else if (successOnly) {
            dao.getResultsBySessionFiltered(sessionId, true).map { it.map { e -> e.toDomain() } }
        } else {
            dao.getResultsBySession(sessionId).map { it.map { e -> e.toDomain() } }
        }
    }

    override suspend fun getStatistics(sessionId: String): ScanStatistics? {
        val session = dao.getSessionById(sessionId) ?: return null
        return session.statistics?.let { Converters.fromStatisticsJson(it) }
    }

    override suspend fun completeSession(sessionId: String, statistics: ScanStatistics) {
        val statsJson = Converters.toStatisticsJson(statistics)
        dao.completeSession(sessionId, statsJson, System.currentTimeMillis())
    }

    override suspend fun errorSession(sessionId: String, errorMessage: String) {
        dao.errorSession(sessionId, errorMessage, System.currentTimeMillis())
    }

    override suspend fun deleteSession(sessionId: String) {
        dao.deleteSessionById(sessionId)
    }

    override suspend fun deleteSessionsByVehicle(vehicleId: String) {
        dao.deleteSessionsByVehicle(vehicleId)
    }

    override suspend fun getSessionCount(vehicleId: String): Int {
        return dao.getSessionCount(vehicleId)
    }

    override suspend fun hasActiveSession(vehicleId: String): Boolean {
        return dao.getActiveSession(vehicleId) != null
    }

    override suspend fun getActiveSession(vehicleId: String): ScanSession? {
        val sessionEntity = dao.getActiveSession(vehicleId) ?: return null
        val results = dao.getResultsBySession(sessionEntity.sessionId).first().map { it.toDomain() }
        return sessionEntity.toDomain(results)
    }
}
