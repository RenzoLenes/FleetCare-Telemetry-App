package com.fleetcare.obd.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo de Hilt para proveer instancias de Firebase.
 *
 * Este módulo provee las instancias singleton de:
 * - FirebaseAuth para autenticación
 * - FirebaseDatabase para Realtime Database
 *
 * Se instala en SingletonComponent para que las instancias vivan
 * durante toda la vida de la aplicación.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    /**
     * Provee la instancia de FirebaseAuth.
     * Se usa para autenticación anónima y posteriormente para login con email.
     */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return Firebase.auth
    }

    /**
     * Provee la instancia de FirebaseDatabase.
     * Se configura la persistencia para que los datos estén disponibles offline.
     */
    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase {
        return Firebase.database.apply {
            // Habilitar persistencia offline para que los datos se guarden localmente
            // y se sincronicen automáticamente cuando haya conexión
            setPersistenceEnabled(true)

            // Configurar tamaño de caché (10 MB)
            setPersistenceCacheSizeBytes(10 * 1024 * 1024)
        }
    }
}
