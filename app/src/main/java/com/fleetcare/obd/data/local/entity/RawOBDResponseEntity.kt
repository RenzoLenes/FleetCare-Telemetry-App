package com.fleetcare.obd.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad de Room para almacenar respuestas RAW de comandos OBD-II.
 *
 * Esta tabla captura TODAS las respuestas (exitosas y fallidas) para permitir:
 * - Análisis posterior de patrones de bytes
 * - Inferencia de fórmulas para PIDs desconocidos
 * - Debugging de problemas de comunicación
 * - Histórico completo de respuestas por comando
 *
 * Índices creados para:
 * - Búsqueda por comando (query frecuente)
 * - Búsqueda por vehículo + timestamp (historial)
 * - Limpieza por timestamp (mantenimiento)
 */
@Entity(
    tableName = "raw_obd_responses",
    indices = [
        Index(value = ["command"]),
        Index(value = ["vehicleId", "timestamp"]),
        Index(value = ["timestamp"]),
        Index(value = ["sessionId"])
    ]
)
data class RawOBDResponseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Timestamp de captura en milisegundos desde epoch
     */
    val timestamp: Long,

    /**
     * Identificador del vehículo (MAC address del adaptador)
     */
    val vehicleId: String,

    /**
     * ID de sesión de lectura (UUID)
     */
    val sessionId: String,

    /**
     * Comando OBD enviado (ej: "010C" para RPM)
     */
    val command: String,

    /**
     * Respuesta completa sin procesar
     * Ejemplo: "41 0C 1A F8\r\n>"
     */
    val rawResponse: String,

    /**
     * Respuesta limpiada (sin espacios, \r\n, >)
     * Ejemplo: "410C1AF8"
     */
    val cleanResponse: String,

    /**
     * Bytes de datos como string hexadecimal separado por comas
     * Ejemplo: "1A,F8" para [0x1A, 0xF8]
     * Se almacena como string para compatibilidad con Room
     */
    val dataBytesHex: String,

    /**
     * Valor parseado (si fue exitoso)
     */
    val parsedValue: Double?,

    /**
     * Indica si el parseo fue exitoso
     */
    val parseSuccess: Boolean,

    /**
     * Mensaje de error si el parseo falló
     */
    val errorMessage: String?,

    /**
     * Latencia de la respuesta en milisegundos
     */
    val latencyMs: Long,

    /**
     * Número de intento (para comandos con retry)
     */
    val attemptNumber: Int = 1,

    /**
     * Protocolo usado (ej: "ISO 15765-4 CAN")
     */
    val protocolUsed: String? = null
)
