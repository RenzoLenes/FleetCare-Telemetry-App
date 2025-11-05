package com.fleetcare.obd.di

import com.fleetcare.obd.data.repository.AuthRepositoryImpl
import com.fleetcare.obd.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo de Hilt para proveer Repositories.
 *
 * Usa @Binds en lugar de @Provides para mayor eficiencia.
 * @Binds le dice a Hilt que use una implementación específica cuando
 * se solicite la interfaz.
 *
 * Se instala en SingletonComponent para que los repositories sean singleton.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Provee la implementación de AuthRepository.
     */
    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    /**
     * Provee la implementación de BluetoothRepository.
     */
    @Binds
    @Singleton
    abstract fun bindBluetoothRepository(
        bluetoothRepositoryImpl: com.fleetcare.obd.data.repository.BluetoothRepositoryImpl
    ): com.fleetcare.obd.domain.repository.BluetoothRepository

    /**
     * Provee la implementación de VehicleRepository.
     */
    @Binds
    @Singleton
    abstract fun bindVehicleRepository(
        vehicleRepositoryImpl: com.fleetcare.obd.data.repository.VehicleRepositoryImpl
    ): com.fleetcare.obd.domain.repository.VehicleRepository

    /**
     * Provee la implementación de DiagnosticRepository.
     */
    @Binds
    @Singleton
    abstract fun bindDiagnosticRepository(
        diagnosticRepositoryImpl: com.fleetcare.obd.data.repository.DiagnosticRepositoryImpl
    ): com.fleetcare.obd.domain.repository.DiagnosticRepository

    /**
     * Provee la implementación de RawOBDResponseRepository.
     * Sprint 1: Captura de respuestas RAW para análisis de patrones.
     */
    @Binds
    @Singleton
    abstract fun bindRawOBDResponseRepository(
        rawOBDResponseRepositoryImpl: com.fleetcare.obd.data.repository.RawOBDResponseRepositoryImpl
    ): com.fleetcare.obd.domain.repository.RawOBDResponseRepository

    /**
     * Provee la implementación de SupportedPIDsRepository.
     * Sprint 2: Caché de PIDs soportados detectados.
     */
    @Binds
    @Singleton
    abstract fun bindSupportedPIDsRepository(
        supportedPIDsRepositoryImpl: com.fleetcare.obd.data.repository.SupportedPIDsRepositoryImpl
    ): com.fleetcare.obd.domain.repository.SupportedPIDsRepository

    /**
     * Provee la implementación de CustomPIDRepository.
     * Sprint 6: Gestión de PIDs personalizados.
     */
    @Binds
    @Singleton
    abstract fun bindCustomPIDRepository(
        customPIDRepositoryImpl: com.fleetcare.obd.data.repository.CustomPIDRepositoryImpl
    ): com.fleetcare.obd.domain.repository.CustomPIDRepository

    // ========== Universal PID Scanner Repositories ==========

    /**
     * Provee la implementación de UniversalScanRepository.
     * Universal Scanner: Gestión de sesiones de escaneo multi-modo.
     */
    @Binds
    @Singleton
    abstract fun bindUniversalScanRepository(
        universalScanRepositoryImpl: com.fleetcare.obd.data.repository.UniversalScanRepositoryImpl
    ): com.fleetcare.obd.domain.repository.UniversalScanRepository

    /**
     * Provee la implementación de PIDMetadataRepository.
     * Universal Scanner: Metadata aprendida de PIDs.
     */
    @Binds
    @Singleton
    abstract fun bindPIDMetadataRepository(
        pidMetadataRepositoryImpl: com.fleetcare.obd.data.repository.PIDMetadataRepositoryImpl
    ): com.fleetcare.obd.domain.repository.PIDMetadataRepository

    /**
     * Provee la implementación de VehicleProfileRepository.
     * Universal Scanner: Perfiles completos de vehículos.
     */
    @Binds
    @Singleton
    abstract fun bindVehicleProfileRepository(
        vehicleProfileRepositoryImpl: com.fleetcare.obd.data.repository.VehicleProfileRepositoryImpl
    ): com.fleetcare.obd.domain.repository.VehicleProfileRepository
}
