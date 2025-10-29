package com.fleetcare.obd.domain.repository

import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz del Repository de Autenticación.
 *
 * Define el contrato para operaciones de autenticación.
 * La implementación concreta estará en la capa de datos.
 *
 * Este patrón permite:
 * - Inversión de dependencias (la capa de dominio no depende de la implementación)
 * - Testing fácil con mocks
 * - Cambio de implementación sin afectar el resto del código
 */
interface AuthRepository {

    /**
     * Observa cambios en el estado de autenticación.
     */
    val authStateFlow: Flow<FirebaseUser?>

    /**
     * UID del usuario actual autenticado.
     */
    val currentUserId: String?

    /**
     * Verifica si hay un usuario autenticado.
     */
    val isUserAuthenticated: Boolean

    /**
     * Realiza autenticación anónima.
     */
    suspend fun signInAnonymously(): Result<FirebaseUser>

    /**
     * Cierra la sesión del usuario actual.
     */
    fun signOut()

    /**
     * Elimina la cuenta del usuario actual.
     */
    suspend fun deleteAccount(): Result<Unit>
}
