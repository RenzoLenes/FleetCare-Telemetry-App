package com.fleetcare.obd.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fleetcare.obd.data.local.dao.*
import com.fleetcare.obd.data.local.entity.*

/**
 * Base de datos Room para la aplicación FleetCare OBD.
 *
 * Esta clase define la configuración de la base de datos SQLite local
 * que se usa para:
 * - Cachear datos de telemetría del vehículo cuando no hay conexión
 * - Mantener historial local de sesiones
 * - Almacenar respuestas RAW para análisis de patrones
 * - Guardar PIDs personalizados descubiertos
 * - Universal PID Scanner: sesiones, resultados, metadata, perfiles
 *
 * Las entidades son las tablas de la base de datos.
 * Los DAOs (Data Access Objects) son las interfaces para acceder a los datos.
 *
 * TypeConverters permiten almacenar tipos complejos como Date, List, etc.
 *
 * Versiones:
 * - v1: Tabla inicial vehicle_data
 * - v2: Agregada tabla raw_obd_responses
 * - v3: Agregada tabla supported_pids
 * - v4: Agregada tabla custom_pids
 * - v5: Universal Scanner (scan_sessions, scan_results, pid_metadata, vehicle_profiles)
 */
@Database(
    entities = [
        VehicleDataEntity::class,
        RawOBDResponseEntity::class,
        SupportedPIDsEntity::class,
        CustomPIDEntity::class,
        // Universal PID Scanner entities
        ScanSessionEntity::class,
        ScanResultEntity::class,
        PIDMetadataEntity::class,
        VehicleProfileEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    /**
     * DAO para acceder a datos de telemetría del vehículo.
     */
    abstract fun vehicleDataDao(): VehicleDataDao

    /**
     * DAO para acceder a respuestas RAW de comandos OBD-II.
     */
    abstract fun rawOBDResponseDao(): RawOBDResponseDao

    /**
     * DAO para acceder a PIDs soportados por vehículo.
     */
    abstract fun supportedPIDsDao(): SupportedPIDsDao

    /**
     * DAO para acceder a PIDs personalizados.
     */
    abstract fun customPIDDao(): CustomPIDDao

    // ========== Universal PID Scanner DAOs ==========

    /**
     * DAO para sesiones de escaneo universal.
     */
    abstract fun universalScanDao(): UniversalScanDao

    /**
     * DAO para metadata de PIDs.
     */
    abstract fun pidMetadataDao(): PIDMetadataDao

    /**
     * DAO para perfiles de vehículos.
     */
    abstract fun vehicleProfileDao(): VehicleProfileDao
}
