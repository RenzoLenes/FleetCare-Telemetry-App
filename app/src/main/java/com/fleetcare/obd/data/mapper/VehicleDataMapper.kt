package com.fleetcare.obd.data.mapper

import com.fleetcare.obd.data.local.entity.VehicleDataEntity
import com.fleetcare.obd.domain.model.VehicleData

/**
 * Mapper entre VehicleDataEntity (capa de datos) y VehicleData (dominio).
 *
 * Siguiendo Clean Architecture, las capas de dominio y datos deben estar
 * separadas. Este mapper convierte entre ambas representaciones.
 */
object VehicleDataMapper {

    /**
     * Convierte una Entity de Room a modelo de dominio.
     */
    fun entityToDomain(entity: VehicleDataEntity): VehicleData {
        return VehicleData(
            timestamp = entity.timestamp,
            rpm = entity.rpm,
            speed = entity.speed,
            coolantTemp = entity.coolantTemp,
            intakeAirTemp = entity.intakeAirTemp,
            throttlePosition = entity.throttlePosition,
            engineLoad = entity.engineLoad,
            voltage = entity.voltage,
            fuelLevel = entity.fuelLevel,
            oilTemp = entity.oilTemp,
            ambientTemp = entity.ambientTemp
        )
    }

    /**
     * Convierte un modelo de dominio a Entity de Room.
     *
     * @param vehicleData Datos del vehículo
     * @param vehicleId ID del vehículo
     * @param sessionId ID de la sesión actual
     */
    fun domainToEntity(
        vehicleData: VehicleData,
        vehicleId: String,
        sessionId: String
    ): VehicleDataEntity {
        return VehicleDataEntity(
            timestamp = vehicleData.timestamp,
            vehicleId = vehicleId,
            sessionId = sessionId,
            rpm = vehicleData.rpm,
            speed = vehicleData.speed,
            coolantTemp = vehicleData.coolantTemp,
            intakeAirTemp = vehicleData.intakeAirTemp,
            throttlePosition = vehicleData.throttlePosition,
            engineLoad = vehicleData.engineLoad,
            voltage = vehicleData.voltage,
            fuelLevel = vehicleData.fuelLevel,
            oilTemp = vehicleData.oilTemp,
            ambientTemp = vehicleData.ambientTemp,
            synced = false
        )
    }

    /**
     * Convierte una lista de entities a modelos de dominio.
     */
    fun entitiesToDomain(entities: List<VehicleDataEntity>): List<VehicleData> {
        return entities.map { entityToDomain(it) }
    }
}
