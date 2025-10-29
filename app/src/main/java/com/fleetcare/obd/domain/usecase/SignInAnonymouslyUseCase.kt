package com.fleetcare.obd.domain.usecase

import com.fleetcare.obd.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseUser
import javax.inject.Inject

/**
 * Use Case para autenticación anónima.
 *
 * Los Use Cases encapsulan la lógica de negocio específica de una operación.
 * Representan casos de uso del sistema desde la perspectiva del usuario.
 *
 * Este Use Case maneja:
 * - Autenticación anónima de Firebase
 * - Validaciones necesarias antes de autenticar
 * - Transformación de resultados
 *
 * Beneficios:
 * - Lógica de negocio centralizada y reutilizable
 * - Testing fácil e independiente
 * - Código más limpio en ViewModels
 */
class SignInAnonymouslyUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {

    /**
     * Ejecuta la autenticación anónima.
     *
     * @return Result con el FirebaseUser si es exitoso, o Exception si falla
     */
    suspend operator fun invoke(): Result<FirebaseUser> {
        // Verificar si ya hay un usuario autenticado
        if (authRepository.isUserAuthenticated) {
            authRepository.currentUserId?.let { userId ->
                // Ya hay sesión activa, retornar éxito sin autenticar nuevamente
                return Result.success(
                    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser!!
                )
            }
        }

        // No hay sesión activa, proceder con autenticación anónima
        return authRepository.signInAnonymously()
    }
}
