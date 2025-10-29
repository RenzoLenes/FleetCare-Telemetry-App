package com.fleetcare.obd.ui.main

import androidx.lifecycle.viewModelScope
import com.fleetcare.obd.domain.usecase.SignInAnonymouslyUseCase
import com.fleetcare.obd.ui.common.BaseViewModel
import com.fleetcare.obd.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para MainActivity.
 *
 * Gestiona:
 * - Autenticación anónima de Firebase
 * - Estado global de la aplicación
 * - Comunicación entre fragments (si fuera necesario)
 *
 * Usa Hilt para inyección de dependencias con la anotación HiltViewModel.
 * Los Use Cases y Repositories son inyectados automáticamente.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val signInAnonymouslyUseCase: SignInAnonymouslyUseCase
) : BaseViewModel() {

    /**
     * StateFlow para el estado de autenticación.
     * Sealed class para representar todos los estados posibles de forma type-safe.
     */
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /**
     * Inicia la autenticación anónima de Firebase.
     *
     * Se llama desde MainActivity al iniciar la app.
     * La autenticación es necesaria para acceder a Firebase Realtime Database.
     */
    fun authenticateAnonymously() {
        viewModelScope.launch(exceptionHandler) {
            _authState.value = AuthState.Loading
            Logger.firebase("Iniciando autenticación anónima desde MainViewModel")

            try {
                // Ejecutar el use case de autenticación
                val result = signInAnonymouslyUseCase()

                result.onSuccess { user ->
                    // Autenticación exitosa
                    _authState.value = AuthState.Authenticated(
                        userId = user.uid,
                        isAnonymous = user.isAnonymous
                    )
                    Logger.firebase("Autenticación exitosa. UserId: ${user.uid}")
                }.onFailure { exception ->
                    // Error en la autenticación
                    val errorMessage = exception.message ?: "Error desconocido"
                    _authState.value = AuthState.Error(errorMessage)
                    Logger.firebaseError("Error en autenticación", exception)
                    emitError("Error al autenticar: $errorMessage")
                }
            } catch (e: Exception) {
                // Capturar cualquier excepción no manejada
                _authState.value = AuthState.Error(e.message ?: "Error desconocido")
                Logger.firebaseError("Excepción en autenticación", e)
                emitError("Error al autenticar: ${e.message}")
            }
        }
    }

    /**
     * Reintentar autenticación en caso de fallo.
     */
    fun retryAuthentication() {
        Logger.d("Reintentando autenticación...")
        authenticateAnonymously()
    }
}

/**
 * Sealed class que representa los diferentes estados de autenticación.
 *
 * Beneficios de usar sealed class:
 * - Type-safety: El compilador verifica que se manejen todos los casos
 * - Patrón when exhaustivo sin necesidad de else
 * - Datos asociados con cada estado
 */
sealed class AuthState {
    /**
     * Estado inicial, sin autenticación iniciada.
     */
    object Idle : AuthState()

    /**
     * Autenticación en progreso.
     */
    object Loading : AuthState()

    /**
     * Usuario autenticado exitosamente.
     */
    data class Authenticated(
        val userId: String,
        val isAnonymous: Boolean
    ) : AuthState()

    /**
     * Error en la autenticación.
     */
    data class Error(val message: String) : AuthState()
}
