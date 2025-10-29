package com.fleetcare.obd.data.remote.firebase

import com.fleetcare.obd.utils.Logger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager para gestionar la autenticación de Firebase.
 *
 * Encapsula toda la lógica de autenticación de Firebase proporcionando una interfaz
 * limpia y fácil de usar. Soporta autenticación anónima inicialmente, con preparación
 * para autenticación por email en futuras fases.
 *
 * Usa Kotlin Coroutines y Flow para operaciones asíncronas reactivas.
 */
@Singleton
class FirebaseAuthManager @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {

    /**
     * Usuario actual autenticado, null si no hay sesión activa.
     */
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    /**
     * UID del usuario actual, null si no hay sesión.
     */
    val currentUserId: String?
        get() = firebaseAuth.currentUser?.uid

    /**
     * Indica si hay un usuario autenticado actualmente.
     */
    val isUserAuthenticated: Boolean
        get() = firebaseAuth.currentUser != null

    /**
     * Flow que emite cambios en el estado de autenticación.
     * Útil para observar login/logout en tiempo real desde ViewModels.
     */
    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }

        firebaseAuth.addAuthStateListener(authStateListener)

        // Emitir estado inicial
        trySend(firebaseAuth.currentUser)

        // Cleanup cuando el Flow se cancela
        awaitClose {
            firebaseAuth.removeAuthStateListener(authStateListener)
        }
    }

    /**
     * Realiza autenticación anónima.
     *
     * Crea un usuario anónimo en Firebase Authentication. Este tipo de autenticación
     * no requiere credenciales y es perfecta para comenzar a usar la app sin registro.
     *
     * El usuario anónimo tiene un UID único que se puede usar para asociar datos.
     * Posteriormente se puede convertir a cuenta permanente.
     *
     * @return Result con el FirebaseUser si es exitoso, o excepción si falla
     */
    suspend fun signInAnonymously(): Result<FirebaseUser> {
        return try {
            Logger.firebase("Iniciando autenticación anónima...")

            val authResult = firebaseAuth.signInAnonymously().await()
            val user = authResult.user

            if (user != null) {
                Logger.firebase("Autenticación anónima exitosa. UID: ${user.uid}")
                Result.success(user)
            } else {
                val error = Exception("Usuario es null después de autenticación anónima")
                Logger.firebaseError("Error en autenticación anónima", error)
                Result.failure(error)
            }
        } catch (e: Exception) {
            Logger.firebaseError("Error en autenticación anónima", e)
            Result.failure(e)
        }
    }

    /**
     * Cierra la sesión actual del usuario.
     */
    fun signOut() {
        Logger.firebase("Cerrando sesión del usuario ${currentUserId}")
        firebaseAuth.signOut()
    }

    /**
     * Elimina la cuenta del usuario actual.
     * CUIDADO: Esta operación es irreversible.
     */
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            val user = currentUser ?: return Result.failure(
                Exception("No hay usuario autenticado para eliminar")
            )

            Logger.firebase("Eliminando cuenta del usuario ${user.uid}")
            user.delete().await()

            Logger.firebase("Cuenta eliminada exitosamente")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.firebaseError("Error al eliminar cuenta", e)
            Result.failure(e)
        }
    }

    // Métodos preparados para futuras fases

    /**
     * Autenticación con email y contraseña.
     * Se implementará en Fase 2 cuando se agregue login tradicional.
     */
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            Logger.firebase("Iniciando sesión con email: $email")

            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user

            if (user != null) {
                Logger.firebase("Sesión iniciada exitosamente. UID: ${user.uid}")
                Result.success(user)
            } else {
                Result.failure(Exception("Usuario es null después de login"))
            }
        } catch (e: Exception) {
            Logger.firebaseError("Error al iniciar sesión con email", e)
            Result.failure(e)
        }
    }

    /**
     * Registro de nueva cuenta con email y contraseña.
     * Se implementará en Fase 2.
     */
    suspend fun createAccountWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            Logger.firebase("Creando cuenta con email: $email")

            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user

            if (user != null) {
                Logger.firebase("Cuenta creada exitosamente. UID: ${user.uid}")
                Result.success(user)
            } else {
                Result.failure(Exception("Usuario es null después de crear cuenta"))
            }
        } catch (e: Exception) {
            Logger.firebaseError("Error al crear cuenta", e)
            Result.failure(e)
        }
    }

    /**
     * Convierte una cuenta anónima en cuenta permanente con email.
     * Útil para cuando el usuario decide crear una cuenta después de usar la app anónimamente.
     * Se implementará en Fase 2.
     */
    suspend fun linkAnonymousAccountToEmail(
        email: String,
        password: String
    ): Result<FirebaseUser> {
        return try {
            val user = currentUser ?: return Result.failure(
                Exception("No hay usuario anónimo para convertir")
            )

            if (!user.isAnonymous) {
                return Result.failure(Exception("El usuario actual no es anónimo"))
            }

            Logger.firebase("Convirtiendo cuenta anónima a email: $email")

            // Crear credencial de email
            val credential = com.google.firebase.auth.EmailAuthProvider
                .getCredential(email, password)

            // Link de cuenta anónima con credencial de email
            val authResult = user.linkWithCredential(credential).await()
            val linkedUser = authResult.user

            if (linkedUser != null) {
                Logger.firebase("Cuenta anónima convertida exitosamente")
                Result.success(linkedUser)
            } else {
                Result.failure(Exception("Error al convertir cuenta"))
            }
        } catch (e: Exception) {
            Logger.firebaseError("Error al convertir cuenta anónima", e)
            Result.failure(e)
        }
    }
}
