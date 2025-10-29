package com.fleetcare.obd.data.local.database

import androidx.room.TypeConverter
import java.util.Date

/**
 * TypeConverters para Room Database.
 *
 * Room solo puede persistir tipos primitivos. Para almacenar tipos complejos
 * como Date, necesitamos convertirlos a tipos que Room pueda manejar (Long, String, etc).
 *
 * Estos conversores se aplican automáticamente cuando Room lee/escribe datos.
 */
class Converters {

    /**
     * Convierte un timestamp (Long) a Date.
     * Se usa cuando Room lee datos de la base de datos.
     */
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    /**
     * Convierte un Date a timestamp (Long).
     * Se usa cuando Room escribe datos en la base de datos.
     */
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
