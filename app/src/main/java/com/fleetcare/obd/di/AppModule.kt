package com.fleetcare.obd.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.fleetcare.obd.data.local.database.AppDatabase
import com.fleetcare.obd.data.local.database.Migrations
import com.fleetcare.obd.utils.Constants
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Módulo de Hilt para proveer dependencias a nivel de aplicación.
 *
 * Este módulo se instala en SingletonComponent, lo que significa que las dependencias
 * provistas vivirán durante toda la vida de la aplicación.
 *
 * Provee:
 * - Context de aplicación
 * - SharedPreferences
 * - Room Database
 * - Dispatchers de Coroutines
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provee SharedPreferences para almacenar configuraciones y preferencias.
     */
    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences(
            Constants.Preferences.PREF_NAME,
            Context.MODE_PRIVATE
        )
    }

    /**
     * Provee la base de datos Room de la aplicación.
     * Se crea una única instancia que se reutiliza en toda la app.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            Constants.Database.DATABASE_NAME
        )
            .addMigrations(*Migrations.ALL_MIGRATIONS) // Sprint 1: Agregar migraciones
            .fallbackToDestructiveMigration() // Fallback solo si no hay migración disponible
            .build()
    }

    /**
     * Provee DAO para acceso a datos de vehículos.
     */
    @Provides
    @Singleton
    fun provideVehicleDataDao(database: AppDatabase) = database.vehicleDataDao()

    /**
     * Provee DAO para acceso a respuestas RAW de OBD-II.
     * Sprint 1: Captura de respuestas para análisis de patrones.
     */
    @Provides
    @Singleton
    fun provideRawOBDResponseDao(database: AppDatabase) = database.rawOBDResponseDao()

    /**
     * Provee DAO para acceso a PIDs soportados.
     * Sprint 2: Caché de PIDs detectados por vehículo.
     */
    @Provides
    @Singleton
    fun provideSupportedPIDsDao(database: AppDatabase) = database.supportedPIDsDao()

    /**
     * Provee DAO para acceso a PIDs personalizados.
     * Sprint 6: Gestión de PIDs personalizados.
     */
    @Provides
    @Singleton
    fun provideCustomPIDDao(database: AppDatabase) = database.customPIDDao()

    /**
     * Provee DAO para acceso a sesiones de escaneo universal.
     * Sprint 4-5: Universal PID Scanner.
     */
    @Provides
    @Singleton
    fun provideUniversalScanDao(database: AppDatabase) = database.universalScanDao()

    /**
     * Provee DAO para acceso a metadata de PIDs.
     * Sprint 4-5: Universal PID Scanner - Metadata.
     */
    @Provides
    @Singleton
    fun providePIDMetadataDao(database: AppDatabase) = database.pidMetadataDao()

    /**
     * Provee DAO para acceso a perfiles de vehículos.
     * Sprint 4-5: Universal PID Scanner - Vehicle Profiles.
     */
    @Provides
    @Singleton
    fun provideVehicleProfileDao(database: AppDatabase) = database.vehicleProfileDao()

    /**
     * Provee instancia de Gson para serialización/deserialización JSON.
     * Sprint 6: Usado para importar/exportar PIDs personalizados.
     */
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setPrettyPrinting()
            .create()
    }

    /**
     * Provee Dispatcher IO para operaciones de E/S (network, database, file).
     */
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * Provee Dispatcher Main para operaciones en el hilo principal (UI).
     */
    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    /**
     * Provee Dispatcher Default para operaciones de CPU intensivas.
     */
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

/**
 * Qualifiers para diferenciar entre diferentes tipos de Dispatchers.
 * Esto permite inyectar el dispatcher correcto donde se necesite.
 */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class IoDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class MainDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class DefaultDispatcher
