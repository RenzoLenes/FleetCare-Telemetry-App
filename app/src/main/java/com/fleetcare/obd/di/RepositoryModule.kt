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
}
