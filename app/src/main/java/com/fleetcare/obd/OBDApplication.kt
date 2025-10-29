package com.fleetcare.obd

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Clase Application principal de FleetCare OBD.
 *
 * Esta clase se inicializa cuando la aplicación arranca y es el punto de entrada
 * para configuración global como Hilt para inyección de dependencias y Timber para logging.
 *
 * La anotación HiltAndroidApp genera el código necesario para que Hilt funcione
 * en toda la aplicación.
 */
@HiltAndroidApp
class OBDApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inicializar Timber para logging con comportamiento diferente según build type
        if (BuildConfig.DEBUG) {
            // En modo DEBUG, muestra logs en Logcat con la línea y método de origen
            Timber.plant(Timber.DebugTree())
        } else {
            // En producción, podrías usar CrashlyticsTree para enviar logs a Firebase
            // Por ahora solo plantar árbol vacío para evitar logs en producción
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    // No hacer nada en producción o enviar a analytics
                }
            })
        }

        Timber.d("FleetCare OBD Application initialized")
    }
}
