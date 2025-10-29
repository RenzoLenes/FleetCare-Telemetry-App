package com.fleetcare.obd.data.repository

import com.fleetcare.obd.domain.model.DiagnosticTroubleCode
import com.fleetcare.obd.domain.repository.BluetoothRepository
import com.fleetcare.obd.domain.repository.DiagnosticRepository
import com.fleetcare.obd.utils.Logger
import com.fleetcare.obd.utils.obd.DTCManager
import com.fleetcare.obd.utils.obd.ELM327Commands
import com.fleetcare.obd.utils.obd.OBDCommandParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación del DiagnosticRepository.
 *
 * Gestiona la comunicación con el ECU para operaciones de diagnóstico.
 */
@Singleton
class DiagnosticRepositoryImpl @Inject constructor(
    private val bluetoothRepository: BluetoothRepository
) : DiagnosticRepository {

    private val _dtcFlow = MutableStateFlow<List<DiagnosticTroubleCode>>(emptyList())
    override val dtcFlow: Flow<List<DiagnosticTroubleCode>> = _dtcFlow.asStateFlow()

    override suspend fun readActiveDTCs(): Result<List<DiagnosticTroubleCode>> {
        return try {
            Logger.obd("Leyendo DTCs activos (Mode 03)...")

            // Enviar comando Mode 03
            val result = bluetoothRepository.sendOBDCommand(ELM327Commands.MODE_03_GET_DTCS)

            if (result.isFailure) {
                return Result.failure(
                    result.exceptionOrNull() ?: Exception("Error al leer DTCs")
                )
            }

            val response = result.getOrNull() ?: return Result.failure(
                Exception("Respuesta vacía")
            )

            Logger.obd("Respuesta Mode 03: $response")

            // Verificar si es error
            if (OBDCommandParser.isErrorResponse(response)) {
                val errorMsg = OBDCommandParser.getErrorMessage(response)
                Logger.obdError("Error en respuesta: $errorMsg")
                return Result.failure(Exception(errorMsg))
            }

            // Parsear DTCs
            val dtcs = DTCManager.parseDTCs(response)

            Logger.obd("DTCs activos encontrados: ${dtcs.size}")
            dtcs.forEach { dtc ->
                Logger.obd("  - ${dtc.code}: ${dtc.description}")
            }

            // Actualizar Flow
            _dtcFlow.value = dtcs

            Result.success(dtcs)

        } catch (e: Exception) {
            Logger.obdError("Error al leer DTCs activos", e)
            Result.failure(e)
        }
    }

    override suspend fun readPendingDTCs(): Result<List<DiagnosticTroubleCode>> {
        return try {
            Logger.obd("Leyendo DTCs pendientes (Mode 07)...")

            // Enviar comando Mode 07
            val result = bluetoothRepository.sendOBDCommand(ELM327Commands.MODE_07_GET_PENDING_DTCS)

            if (result.isFailure) {
                return Result.failure(
                    result.exceptionOrNull() ?: Exception("Error al leer DTCs pendientes")
                )
            }

            val response = result.getOrNull() ?: return Result.failure(
                Exception("Respuesta vacía")
            )

            Logger.obd("Respuesta Mode 07: $response")

            // Verificar si es error
            if (OBDCommandParser.isErrorResponse(response)) {
                val errorMsg = OBDCommandParser.getErrorMessage(response)
                Logger.obdError("Error en respuesta: $errorMsg")
                return Result.failure(Exception(errorMsg))
            }

            // Parsear DTCs pendientes
            val dtcs = DTCManager.parsePendingDTCs(response)

            Logger.obd("DTCs pendientes encontrados: ${dtcs.size}")
            dtcs.forEach { dtc ->
                Logger.obd("  - ${dtc.code}: ${dtc.description}")
            }

            Result.success(dtcs)

        } catch (e: Exception) {
            Logger.obdError("Error al leer DTCs pendientes", e)
            Result.failure(e)
        }
    }

    override suspend fun clearDTCs(): Result<Unit> {
        return try {
            Logger.obd("Limpiando DTCs (Mode 04)...")

            // Enviar comando Mode 04
            val result = bluetoothRepository.sendOBDCommand(ELM327Commands.MODE_04_CLEAR_DTCS)

            if (result.isFailure) {
                return Result.failure(
                    result.exceptionOrNull() ?: Exception("Error al limpiar DTCs")
                )
            }

            val response = result.getOrNull() ?: return Result.failure(
                Exception("Respuesta vacía")
            )

            Logger.obd("Respuesta Mode 04: $response")

            // Verificar si es error
            if (OBDCommandParser.isErrorResponse(response)) {
                val errorMsg = OBDCommandParser.getErrorMessage(response)
                Logger.obdError("Error en respuesta: $errorMsg")
                return Result.failure(Exception(errorMsg))
            }

            // Mode 04 responde con "44" en caso de éxito
            val cleanResponse = OBDCommandParser.cleanResponse(response)
            if (!cleanResponse.contains("44")) {
                return Result.failure(
                    Exception("Respuesta inesperada al limpiar DTCs: $response")
                )
            }

            Logger.obd("DTCs limpiados exitosamente")

            // Limpiar Flow
            _dtcFlow.value = emptyList()

            Result.success(Unit)

        } catch (e: Exception) {
            Logger.obdError("Error al limpiar DTCs", e)
            Result.failure(e)
        }
    }

    override suspend fun getDTCCount(): Result<Int> {
        return try {
            Logger.obd("Obteniendo número de DTCs (Mode 01 PID 01)...")

            // PID 01 del Mode 01 incluye el número de DTCs en el primer byte
            val result = bluetoothRepository.sendOBDCommand("0101")

            if (result.isFailure) {
                return Result.failure(
                    result.exceptionOrNull() ?: Exception("Error al obtener número de DTCs")
                )
            }

            val response = result.getOrNull() ?: return Result.failure(
                Exception("Respuesta vacía")
            )

            Logger.obd("Respuesta PID 01: $response")

            // Verificar si es error
            if (OBDCommandParser.isErrorResponse(response)) {
                val errorMsg = OBDCommandParser.getErrorMessage(response)
                return Result.failure(Exception(errorMsg))
            }

            val cleanResponse = OBDCommandParser.cleanResponse(response)

            // Formato: "41 01 XX XX XX XX"
            // El primer byte de datos (XX) contiene el número de DTCs en los últimos 7 bits
            if (cleanResponse.length >= 6) {
                val firstDataByte = cleanResponse.substring(4, 6).toIntOrNull(16) ?: 0
                val dtcCount = firstDataByte and 0x7F // Últimos 7 bits

                Logger.obd("Número de DTCs: $dtcCount")
                Result.success(dtcCount)
            } else {
                Result.failure(Exception("Respuesta con formato inválido"))
            }

        } catch (e: Exception) {
            Logger.obdError("Error al obtener número de DTCs", e)
            Result.failure(e)
        }
    }
}
