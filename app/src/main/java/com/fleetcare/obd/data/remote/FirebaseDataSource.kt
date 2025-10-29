package com.fleetcare.obd.data.remote

import com.fleetcare.obd.domain.model.VehicleData
import com.fleetcare.obd.utils.Logger
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data source para interactuar con Firebase Realtime Database.
 *
 * Estructura de datos en Firebase:
 * ```
 * vehicles/
 *   {vehicleId}/
 *     info/
 *       name: String
 *       lastConnection: Long
 *     sessions/
 *       {sessionId}/
 *         startTime: Long
 *         endTime: Long?
 *         dataPoints: Int
 *     telemetry/
 *       {timestamp}/
 *         rpm: Int?
 *         speed: Double?
 *         coolantTemp: Double?
 *         ...
 * ```
 */
@Singleton
class FirebaseDataSource @Inject constructor(
    private val database: FirebaseDatabase
) {

    companion object {
        private const val VEHICLES_PATH = "vehicles"
        private const val TELEMETRY_PATH = "telemetry"
        private const val SESSIONS_PATH = "sessions"
        private const val INFO_PATH = "info"

        // Límite de datos históricos a mantener (24 horas)
        private const val HISTORY_LIMIT_HOURS = 24L
        private const val MILLIS_PER_HOUR = 3600000L
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Envía datos de telemetría a Firebase.
     *
     * @param vehicleId ID único del vehículo
     * @param sessionId ID de la sesión actual
     * @param data Datos del vehículo
     * @return Result indicando éxito o error
     */
    suspend fun sendTelemetry(
        vehicleId: String,
        sessionId: String,
        data: VehicleData
    ): Result<Unit> {
        return try {
            val timestamp = data.timestamp.time
            val telemetryPath = "$VEHICLES_PATH/$vehicleId/$TELEMETRY_PATH/$timestamp"

            val telemetryData = buildTelemetryMap(data)

            // DIAGNÓSTICO: Log detallado ANTES de enviar
            Logger.d("🔵 FIREBASE_DEBUG: Intentando enviar telemetría...")
            Logger.d("   ├─ Path: $telemetryPath")
            Logger.d("   ├─ VehicleId: $vehicleId")
            Logger.d("   ├─ SessionId: $sessionId")
            Logger.d("   ├─ Timestamp: $timestamp (${Date(timestamp)})")
            Logger.d("   ├─ Campos: ${telemetryData.size}")
            Logger.d("   └─ Datos: $telemetryData")

            // Verificar que database.reference no sea null
            val dbRef = database.reference
            Logger.d("   ├─ Database Reference: ${if (dbRef != null) "✓ OK" else "✗ NULL"}")

            // Enviar datos
            Logger.d("   ├─ Ejecutando setValue()...")
            dbRef
                .child(telemetryPath)
                .setValue(telemetryData)
                .await()
            Logger.d("   ├─ setValue() completado ✓")

            // Actualizar contadores de sesión
            Logger.d("   ├─ Actualizando dataPoints...")
            updateSessionDataPoints(vehicleId, sessionId)
            Logger.d("   ├─ dataPoints actualizado ✓")

            // Actualizar última conexión
            Logger.d("   ├─ Actualizando lastConnection...")
            updateLastConnection(vehicleId)
            Logger.d("   └─ lastConnection actualizado ✓")

            Logger.d("✅ FIREBASE_DEBUG: Telemetría enviada exitosamente: $vehicleId - ${telemetryData.size} parámetros")
            Result.success(Unit)

        } catch (e: Exception) {
            Logger.e(e, "❌ FIREBASE_DEBUG: ERROR al enviar telemetría")
            Logger.e(e, "   ├─ Tipo: ${e.javaClass.simpleName}")
            Logger.e(e, "   ├─ Mensaje: ${e.message}")
            Logger.e(e, "   └─ Stack trace completo:")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Crea o actualiza información de sesión.
     */
    suspend fun createOrUpdateSession(
        vehicleId: String,
        sessionId: String,
        startTime: Long,
        endTime: Long? = null
    ): Result<Unit> {
        return try {
            val sessionPath = "$VEHICLES_PATH/$vehicleId/$SESSIONS_PATH/$sessionId"

            val sessionData = mutableMapOf<String, Any>(
                "startTime" to startTime,
                "dataPoints" to 0
            )

            if (endTime != null) {
                sessionData["endTime"] = endTime
            }

            database.reference
                .child(sessionPath)
                .setValue(sessionData)
                .await()

            Logger.d("Sesión creada/actualizada en Firebase: $sessionId")
            Result.success(Unit)

        } catch (e: Exception) {
            Logger.e(e, "Error al crear sesión en Firebase")
            Result.failure(e)
        }
    }

    /**
     * Finaliza una sesión activa.
     */
    suspend fun endSession(
        vehicleId: String,
        sessionId: String,
        endTime: Long
    ): Result<Unit> {
        return try {
            val sessionPath = "$VEHICLES_PATH/$vehicleId/$SESSIONS_PATH/$sessionId"

            database.reference
                .child(sessionPath)
                .child("endTime")
                .setValue(endTime)
                .await()

            Logger.d("Sesión finalizada en Firebase: $sessionId")
            Result.success(Unit)

        } catch (e: Exception) {
            Logger.e(e, "Error al finalizar sesión")
            Result.failure(e)
        }
    }

    /**
     * Actualiza información del vehículo.
     */
    suspend fun updateVehicleInfo(
        vehicleId: String,
        name: String
    ): Result<Unit> {
        return try {
            val infoPath = "$VEHICLES_PATH/$vehicleId/$INFO_PATH"

            val infoData = mapOf(
                "name" to name,
                "lastConnection" to System.currentTimeMillis()
            )

            database.reference
                .child(infoPath)
                .setValue(infoData)
                .await()

            Result.success(Unit)

        } catch (e: Exception) {
            Logger.e(e, "Error al actualizar info del vehículo")
            Result.failure(e)
        }
    }

    /**
     * Obtiene telemetría histórica en tiempo real.
     *
     * @param vehicleId ID del vehículo
     * @param limitHours Límite en horas de historial (default 24)
     * @return Flow de lista de datos ordenados por timestamp
     */
    fun getTelemetryFlow(
        vehicleId: String,
        limitHours: Long = HISTORY_LIMIT_HOURS
    ): Flow<List<VehicleData>> = callbackFlow {
        val telemetryPath = "$VEHICLES_PATH/$vehicleId/$TELEMETRY_PATH"
        val cutoffTime = System.currentTimeMillis() - (limitHours * MILLIS_PER_HOUR)

        val query = database.reference
            .child(telemetryPath)
            .orderByKey()
            .startAt(cutoffTime.toString())

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dataList = mutableListOf<VehicleData>()

                for (child in snapshot.children) {
                    try {
                        val timestamp = child.key?.toLongOrNull() ?: continue
                        val vehicleData = parseVehicleData(child, Date(timestamp))
                        dataList.add(vehicleData)
                    } catch (e: Exception) {
                        Logger.e(e, "Error al parsear dato de Firebase")
                    }
                }

                trySend(dataList)
            }

            override fun onCancelled(error: DatabaseError) {
                Logger.e(error.toException(), "Error en listener de Firebase")
                close(error.toException())
            }
        }

        query.addValueEventListener(listener)

        awaitClose {
            query.removeEventListener(listener)
        }
    }

    /**
     * Limpia datos antiguos de telemetría.
     */
    suspend fun cleanOldTelemetry(
        vehicleId: String,
        olderThanHours: Long = HISTORY_LIMIT_HOURS
    ): Result<Unit> {
        return try {
            val telemetryPath = "$VEHICLES_PATH/$vehicleId/$TELEMETRY_PATH"
            val cutoffTime = System.currentTimeMillis() - (olderThanHours * MILLIS_PER_HOUR)

            val query = database.reference
                .child(telemetryPath)
                .orderByKey()
                .endAt(cutoffTime.toString())

            val snapshot = query.get().await()

            snapshot.children.forEach { child ->
                child.ref.removeValue()
            }

            Logger.d("Datos antiguos eliminados de Firebase: $vehicleId")
            Result.success(Unit)

        } catch (e: Exception) {
            Logger.e(e, "Error al limpiar datos antiguos")
            Result.failure(e)
        }
    }

    /**
     * Convierte VehicleData a Map para Firebase.
     */
    private fun buildTelemetryMap(data: VehicleData): Map<String, Any> {
        val map = mutableMapOf<String, Any>()

        data.rpm?.let { map["rpm"] = it }
        data.speed?.let { map["speed"] = it }
        data.coolantTemp?.let { map["coolantTemp"] = it }
        data.intakeAirTemp?.let { map["intakeAirTemp"] = it }
        data.throttlePosition?.let { map["throttlePosition"] = it }
        data.engineLoad?.let { map["engineLoad"] = it }
        data.voltage?.let { map["voltage"] = it }
        data.fuelLevel?.let { map["fuelLevel"] = it }
        data.oilTemp?.let { map["oilTemp"] = it }
        data.ambientTemp?.let { map["ambientTemp"] = it }

        // DIAGNÓSTICO: Verificar si el map está vacío
        if (map.isEmpty()) {
            Logger.w("⚠️ FIREBASE_DEBUG: buildTelemetryMap() retornó un Map VACÍO!")
            Logger.w("   └─ VehicleData original: rpm=${data.rpm}, speed=${data.speed}, coolantTemp=${data.coolantTemp}")
        }

        return map
    }

    /**
     * Parsea DataSnapshot a VehicleData.
     */
    private fun parseVehicleData(snapshot: DataSnapshot, timestamp: Date): VehicleData {
        return VehicleData(
            timestamp = timestamp,
            rpm = snapshot.child("rpm").getValue(Int::class.java),
            speed = snapshot.child("speed").getValue(Double::class.java),
            coolantTemp = snapshot.child("coolantTemp").getValue(Double::class.java),
            intakeAirTemp = snapshot.child("intakeAirTemp").getValue(Double::class.java),
            throttlePosition = snapshot.child("throttlePosition").getValue(Double::class.java),
            engineLoad = snapshot.child("engineLoad").getValue(Double::class.java),
            voltage = snapshot.child("voltage").getValue(Double::class.java),
            fuelLevel = snapshot.child("fuelLevel").getValue(Double::class.java),
            oilTemp = snapshot.child("oilTemp").getValue(Double::class.java),
            ambientTemp = snapshot.child("ambientTemp").getValue(Double::class.java)
        )
    }

    /**
     * Actualiza contador de data points en sesión.
     */
    private suspend fun updateSessionDataPoints(vehicleId: String, sessionId: String) {
        try {
            val sessionPath = "$VEHICLES_PATH/$vehicleId/$SESSIONS_PATH/$sessionId/dataPoints"
            val ref = database.reference.child(sessionPath)

            Logger.d("   │  ├─ Iniciando transacción dataPoints en: $sessionPath")

            ref.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val currentValue = currentData.getValue(Int::class.java) ?: 0
                    val newValue = currentValue + 1
                    currentData.value = newValue
                    Logger.d("   │  ├─ Transacción: $currentValue → $newValue")
                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (error != null) {
                        Logger.e(error.toException(), "❌ FIREBASE_DEBUG: Error en transacción dataPoints")
                        Logger.e(error.toException(), "   │  ├─ DatabaseError code: ${error.code}")
                        Logger.e(error.toException(), "   │  └─ DatabaseError message: ${error.message}")
                    } else {
                        val finalValue = currentData?.getValue(Int::class.java) ?: 0
                        Logger.d("   │  └─ Transacción completada: committed=$committed, valor final=$finalValue")
                    }
                }
            })
        } catch (e: Exception) {
            Logger.e(e, "❌ FIREBASE_DEBUG: Excepción en transacción de dataPoints")
            Logger.e(e, "   │  └─ ${e.message}")
        }
    }

    /**
     * Actualiza timestamp de última conexión.
     */
    private suspend fun updateLastConnection(vehicleId: String) {
        try {
            val infoPath = "$VEHICLES_PATH/$vehicleId/$INFO_PATH/lastConnection"
            database.reference
                .child(infoPath)
                .setValue(System.currentTimeMillis())
        } catch (e: Exception) {
            Logger.e(e, "Error al actualizar lastConnection")
        }
    }
}
