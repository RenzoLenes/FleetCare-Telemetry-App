package com.fleetcare.obd.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.fleetcare.obd.data.local.database.AppDatabase
import com.fleetcare.obd.utils.Constants
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
            .fallbackToDestructiveMigration() // En desarrollo, recrear DB si cambia schema
            .build()
    }

    /**
     * Provee DAO para acceso a datos de vehículos.
     */
    @Provides
    @Singleton
    fun provideVehicleDataDao(database: AppDatabase) = database.vehicleDataDao()

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
