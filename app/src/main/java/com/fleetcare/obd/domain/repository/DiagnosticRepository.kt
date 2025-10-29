package com.fleetcare.obd.domain.repository

import com.fleetcare.obd.domain.model.DiagnosticTroubleCode
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para operaciones de diagnóstico del vehículo.
 *
 * Gestiona la lectura y limpieza de DTCs (Diagnostic Trouble Codes).
 */
interface DiagnosticRepository {

    /**
     * Lee los códigos de error activos (Mode 03).
     *
     * @return Result con lista de DTCs activos
     */
    suspend fun readActiveDTCs(): Result<List<DiagnosticTroubleCode>>

    /**
     * Lee los códigos de error pendientes (Mode 07).
     *
     * @return Result con lista de DTCs pendientes
     */
    suspend fun readPendingDTCs(): Result<List<DiagnosticTroubleCode>>

    /**
     * Limpia todos los códigos de error (Mode 04).
     *
     * IMPORTANTE: Solo usar cuando el problema se ha resuelto.
     * Borrar códigos sin resolver el problema puede causar que
     * el vehículo no pase inspecciones técnicas.
     *
     * @return Result indicando éxito o error
     */
    suspend fun clearDTCs(): Result<Unit>

    /**
     * Obtiene el número de DTCs almacenados sin leerlos.
     *
     * Útil para mostrar un contador rápido sin hacer lectura completa.
     *
     * @return Result con número de DTCs
     */
    suspend fun getDTCCount(): Result<Int>

    /**
     * Flow que emite DTCs cuando cambian.
     */
    val dtcFlow: Flow<List<DiagnosticTroubleCode>>
}
