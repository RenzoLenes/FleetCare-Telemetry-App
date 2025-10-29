package com.fleetcare.obd.domain.model

import java.util.Date

/**
 * Modelo de dominio para datos del vehículo en tiempo real.
 *
 * Representa un snapshot de todos los parámetros del vehículo
 * en un momento específico del tiempo.
 *
 * Los valores null indican que el parámetro no está disponible
 * o no se pudo leer.
 */
data class VehicleData(
    val timestamp: Date = Date(),
    val rpm: Int? = null,
    val speed: Double? = null,
    val coolantTemp: Double? = null,
    val intakeAirTemp: Double? = null,
    val throttlePosition: Double? = null,
    val engineLoad: Double? = null,
    val voltage: Double? = null,
    val fuelLevel: Double? = null,
    val oilTemp: Double? = null,
    val ambientTemp: Double? = null
) {
    /**
     * Indica si hay al menos un valor disponible.
     */
    val hasData: Boolean
        get() = rpm != null || speed != null || coolantTemp != null ||
                intakeAirTemp != null || throttlePosition != null ||
                engineLoad != null || voltage != null || fuelLevel != null ||
                oilTemp != null || ambientTemp != null

    /**
     * Cuenta cuántos parámetros tienen datos.
     */
    val availableParametersCount: Int
        get() = listOfNotNull(
            rpm, speed, coolantTemp, intakeAirTemp, throttlePosition,
            engineLoad, voltage, fuelLevel, oilTemp, ambientTemp
        ).size

    /**
     * Crea una copia con velocidad convertida a mph.
     */
    fun withSpeedInMph(): VehicleData {
        return copy(speed = speed?.let { it * 0.621371 })
    }

    /**
     * Crea una copia con temperaturas convertidas a Fahrenheit.
     */
    fun withTemperaturesInFahrenheit(): VehicleData {
        return copy(
            coolantTemp = coolantTemp?.let { (it * 9.0 / 5.0) + 32.0 },
            intakeAirTemp = intakeAirTemp?.let { (it * 9.0 / 5.0) + 32.0 },
            oilTemp = oilTemp?.let { (it * 9.0 / 5.0) + 32.0 },
            ambientTemp = ambientTemp?.let { (it * 9.0 / 5.0) + 32.0 }
        )
    }

    companion object {
        /**
         * Crea un VehicleData vacío (sin datos).
         */
        fun empty(): VehicleData {
            return VehicleData()
        }
    }
}
