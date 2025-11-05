package com.fleetcare.obd.domain.model

/**
 * Modelo de configuraciones de la aplicación.
 */
data class AppSettings(
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val autoReconnect: Boolean = true,
    val readInterval: Int = 1000, // milliseconds
    val enableRawCapture: Boolean = false, // Sprint 1: Captura de respuestas RAW para análisis de PIDs
    val rawCaptureRetentionDays: Int = 30 // Sprint 1: Días de retención de respuestas RAW
)

/**
 * Sistema de unidades.
 */
enum class UnitSystem {
    METRIC,    // km/h, km, L
    IMPERIAL   // mph, miles, gal
}

/**
 * Unidad de temperatura.
 */
enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT
}
