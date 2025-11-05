package com.fleetcare.obd.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migraciones de base de datos para Room.
 *
 * Cada migración define cómo actualizar la base de datos de una versión a otra
 * sin perder datos del usuario.
 */
object Migrations {

    /**
     * Migración de versión 1 a 2.
     *
     * Cambios:
     * - Agrega tabla raw_obd_responses para capturar respuestas RAW de OBD-II
     * - Incluye índices para optimizar consultas frecuentes
     *
     * Sprint 1: Sistema de análisis dinámico de bytes
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Crear tabla raw_obd_responses
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `raw_obd_responses` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `vehicleId` TEXT NOT NULL,
                    `sessionId` TEXT NOT NULL,
                    `command` TEXT NOT NULL,
                    `rawResponse` TEXT NOT NULL,
                    `cleanResponse` TEXT NOT NULL,
                    `dataBytesHex` TEXT NOT NULL,
                    `parsedValue` REAL,
                    `parseSuccess` INTEGER NOT NULL,
                    `errorMessage` TEXT,
                    `latencyMs` INTEGER NOT NULL,
                    `attemptNumber` INTEGER NOT NULL DEFAULT 1,
                    `protocolUsed` TEXT
                )
            """.trimIndent())

            // Crear índice para búsquedas por comando (consulta frecuente)
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_raw_obd_responses_command`
                ON `raw_obd_responses` (`command`)
            """.trimIndent())

            // Crear índice compuesto para búsquedas por vehículo y tiempo
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_raw_obd_responses_vehicleId_timestamp`
                ON `raw_obd_responses` (`vehicleId`, `timestamp`)
            """.trimIndent())

            // Crear índice para limpieza por timestamp
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_raw_obd_responses_timestamp`
                ON `raw_obd_responses` (`timestamp`)
            """.trimIndent())

            // Crear índice para consultas por sesión
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_raw_obd_responses_sessionId`
                ON `raw_obd_responses` (`sessionId`)
            """.trimIndent())
        }
    }

    /**
     * Migración de versión 2 a 3.
     *
     * Cambios:
     * - Agrega tabla supported_pids para cachear PIDs detectados por vehículo
     * - Incluye índices para consultas rápidas por vehicleId, VIN y timestamp
     *
     * Sprint 2: Detección de PIDs soportados
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Crear tabla supported_pids
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `supported_pids` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `vehicleId` TEXT NOT NULL,
                    `vin` TEXT,
                    `pidRangesJson` TEXT NOT NULL,
                    `detectionTimestamp` INTEGER NOT NULL,
                    `totalPIDsCount` INTEGER NOT NULL,
                    `detectionVersion` INTEGER NOT NULL DEFAULT 1
                )
            """.trimIndent())

            // Crear índice único para vehicleId (un vehículo = un registro)
            database.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS `index_supported_pids_vehicleId`
                ON `supported_pids` (`vehicleId`)
            """.trimIndent())

            // Crear índice para búsquedas por VIN
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_supported_pids_vin`
                ON `supported_pids` (`vin`)
            """.trimIndent())

            // Crear índice para limpieza por timestamp
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_supported_pids_detectionTimestamp`
                ON `supported_pids` (`detectionTimestamp`)
            """.trimIndent())
        }
    }

    /**
     * Migración de versión 3 a 4.
     *
     * Cambios:
     * - Agrega tabla custom_pids para gestionar PIDs personalizados descubiertos
     * - Incluye índices para búsquedas por pid, command, category, is_enabled
     *
     * Sprint 6: Gestión de PIDs personalizados
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Crear tabla custom_pids
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `custom_pids` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `pid` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `command` TEXT NOT NULL,
                    `formula` TEXT NOT NULL,
                    `unit` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `vehicle_models_json` TEXT NOT NULL,
                    `discovery_date` INTEGER NOT NULL,
                    `last_used` INTEGER NOT NULL,
                    `confidence` REAL NOT NULL,
                    `source` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `is_enabled` INTEGER NOT NULL,
                    `byte_count` INTEGER NOT NULL,
                    `min_value` REAL,
                    `max_value` REAL
                )
            """.trimIndent())

            // Crear índice para búsquedas por PID
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_custom_pids_pid`
                ON `custom_pids` (`pid`)
            """.trimIndent())

            // Crear índice para búsquedas por comando
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_custom_pids_command`
                ON `custom_pids` (`command`)
            """.trimIndent())

            // Crear índice para filtrado por categoría
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_custom_pids_category`
                ON `custom_pids` (`category`)
            """.trimIndent())

            // Crear índice para filtrado por habilitados
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_custom_pids_is_enabled`
                ON `custom_pids` (`is_enabled`)
            """.trimIndent())

            // Crear índice para filtrado por origen
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_custom_pids_source`
                ON `custom_pids` (`source`)
            """.trimIndent())
        }
    }

    /**
     * Migración de versión 4 a 5.
     *
     * Cambios:
     * - Agrega tabla scan_sessions para sesiones de escaneo universal
     * - Agrega tabla scan_results para resultados de PIDs escaneados
     * - Agrega tabla pid_metadata para metadata aprendida de PIDs
     * - Agrega tabla vehicle_profiles para perfiles completos de vehículos
     *
     * Universal PID Scanner: Escaneo multi-modo (01, 02, 09, 22)
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Tabla scan_sessions
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `scan_sessions` (
                    `sessionId` TEXT PRIMARY KEY NOT NULL,
                    `vehicleId` TEXT NOT NULL,
                    `state` TEXT NOT NULL,
                    `config` TEXT NOT NULL,
                    `statistics` TEXT,
                    `startTime` INTEGER NOT NULL,
                    `endTime` INTEGER,
                    `errorMessage` TEXT
                )
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_scan_sessions_vehicleId`
                ON `scan_sessions` (`vehicleId`)
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_scan_sessions_startTime`
                ON `scan_sessions` (`startTime`)
            """.trimIndent())

            // Tabla scan_results
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `scan_results` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `sessionId` TEXT NOT NULL,
                    `vehicleId` TEXT NOT NULL,
                    `mode` TEXT NOT NULL,
                    `pid` TEXT NOT NULL,
                    `command` TEXT NOT NULL,
                    `success` INTEGER NOT NULL,
                    `rawResponse` TEXT NOT NULL,
                    `dataBytes` BLOB NOT NULL,
                    `byteCount` INTEGER NOT NULL,
                    `interpretation` TEXT,
                    `timestamp` INTEGER NOT NULL,
                    `responseTime` INTEGER NOT NULL,
                    `isStandardPID` INTEGER NOT NULL,
                    FOREIGN KEY(`sessionId`) REFERENCES `scan_sessions`(`sessionId`) ON DELETE CASCADE
                )
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_scan_results_sessionId`
                ON `scan_results` (`sessionId`)
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_scan_results_vehicleId`
                ON `scan_results` (`vehicleId`)
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_scan_results_mode_pid`
                ON `scan_results` (`mode`, `pid`)
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_scan_results_success`
                ON `scan_results` (`success`)
            """.trimIndent())

            // Tabla pid_metadata
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `pid_metadata` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `mode` TEXT NOT NULL,
                    `pid` TEXT NOT NULL,
                    `vehicleId` TEXT,
                    `name` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `unit` TEXT NOT NULL,
                    `formula` TEXT NOT NULL,
                    `detectedType` TEXT NOT NULL,
                    `minValue` REAL,
                    `maxValue` REAL,
                    `averageResponseTime` INTEGER NOT NULL,
                    `successRate` REAL NOT NULL,
                    `responseLength` INTEGER NOT NULL,
                    `isStandard` INTEGER NOT NULL,
                    `vehicleSpecific` INTEGER NOT NULL,
                    `lastUpdated` INTEGER NOT NULL
                )
            """.trimIndent())

            database.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS `index_pid_metadata_mode_pid_vehicleId`
                ON `pid_metadata` (`mode`, `pid`, `vehicleId`)
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_pid_metadata_vehicleId`
                ON `pid_metadata` (`vehicleId`)
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_pid_metadata_mode`
                ON `pid_metadata` (`mode`)
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_pid_metadata_detectedType`
                ON `pid_metadata` (`detectedType`)
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_pid_metadata_isStandard`
                ON `pid_metadata` (`isStandard`)
            """.trimIndent())

            // Tabla vehicle_profiles
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `vehicle_profiles` (
                    `vehicleId` TEXT PRIMARY KEY NOT NULL,
                    `vin` TEXT NOT NULL,
                    `make` TEXT NOT NULL,
                    `model` TEXT NOT NULL,
                    `year` INTEGER,
                    `protocol` TEXT NOT NULL,
                    `protocolName` TEXT NOT NULL,
                    `ecuInfo` TEXT NOT NULL,
                    `supportedPIDsCount` INTEGER NOT NULL,
                    `knownPIDs` TEXT NOT NULL,
                    `failedPIDs` TEXT NOT NULL,
                    `optimalScanConfig` TEXT,
                    `isLegacyVehicle` INTEGER NOT NULL,
                    `lastScanned` INTEGER NOT NULL,
                    `totalScans` INTEGER NOT NULL,
                    `averageQualityScore` INTEGER NOT NULL
                )
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_vehicle_profiles_vin`
                ON `vehicle_profiles` (`vin`)
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_vehicle_profiles_lastScanned`
                ON `vehicle_profiles` (`lastScanned`)
            """.trimIndent())
        }
    }

    /**
     * Array con todas las migraciones disponibles.
     * Se pasa a Room.databaseBuilder().addMigrations()
     */
    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5  // Universal PID Scanner
    )
}
