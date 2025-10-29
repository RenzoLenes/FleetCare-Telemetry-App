package com.fleetcare.obd.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fleetcare.obd.data.local.entity.VehicleDataEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) para VehicleDataEntity.
 *
 * Define las operaciones de base de datos para la tabla vehicle_data.
 * Room genera automáticamente la implementación de estos métodos.
 *
 * Flow permite observar cambios en la base de datos de forma reactiva.
 */
@Dao
interface VehicleDataDao {

    /**
     * Inserta un nuevo registro de datos del vehículo.
     * Si hay conflicto (mismo ID), reemplaza el registro existente.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: VehicleDataEntity): Long

    /**
     * Inserta múltiples registros en una sola transacción.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(dataList: List<VehicleDataEntity>)

    /**
     * Obtiene todos los registros no sincronizados con Firebase.
     * Se usa para sincronización posterior.
     */
    @Query("SELECT * FROM vehicle_data WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedData(): List<VehicleDataEntity>

    /**
     * Marca un registro como sincronizado.
     */
    @Query("UPDATE vehicle_data SET synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    /**
     * Obtiene los últimos N registros de un vehículo específico.
     */
    @Query("SELECT * FROM vehicle_data WHERE vehicleId = :vehicleId ORDER BY timestamp DESC LIMIT :limit")
    fun getLatestDataForVehicle(vehicleId: String, limit: Int = 100): Flow<List<VehicleDataEntity>>

    /**
     * Elimina registros antiguos (más de X días).
     */
    @Query("DELETE FROM vehicle_data WHERE timestamp < :cutoffDate")
    suspend fun deleteOldData(cutoffDate: Long)

    /**
     * Cuenta el número total de registros.
     */
    @Query("SELECT COUNT(*) FROM vehicle_data")
    suspend fun getRecordCount(): Int
}
