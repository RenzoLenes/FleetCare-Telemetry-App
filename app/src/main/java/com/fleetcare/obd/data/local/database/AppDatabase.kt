package com.fleetcare.obd.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fleetcare.obd.data.local.dao.VehicleDataDao
import com.fleetcare.obd.data.local.entity.VehicleDataEntity

/**
 * Base de datos Room para la aplicación FleetCare OBD.
 *
 * Esta clase define la configuración de la base de datos SQLite local
 * que se usa para cachear datos de telemetría del vehículo cuando no hay conexión,
 * y para mantener un historial local de sesiones.
 *
 * Las entidades son las tablas de la base de datos.
 * Los DAOs (Data Access Objects) son las interfaces para acceder a los datos.
 *
 * TypeConverters permiten almacenar tipos complejos como Date, List, etc.
 */
@Database(
    entities = [
        VehicleDataEntity::class
        // Aquí se agregarán más entidades en Sprint 3
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    /**
     * DAO para acceder a datos de telemetría del vehículo.
     */
    abstract fun vehicleDataDao(): VehicleDataDao
}
