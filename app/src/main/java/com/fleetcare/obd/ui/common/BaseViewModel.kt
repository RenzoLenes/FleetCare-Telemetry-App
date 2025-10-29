package com.fleetcare.obd.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fleetcare.obd.utils.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel base abstracto que proporciona funcionalidad común a todos los ViewModels.
 *
 * Características:
 * - Manejo centralizado de errores con CoroutineExceptionHandler
 * - Estados de UI genéricos (loading, error, success)
 * - Eventos de UI de un solo uso (SingleLiveEvent pattern con Flow)
 * - Logging consistente
 *
 * Todos los ViewModels de la aplicación deben heredar de esta clase base.
 */
abstract class BaseViewModel : ViewModel() {

    /**
     * Exception handler para capturar errores no manejados en coroutines.
     * Los errores se loguean y se propagan al estado de error de UI.
     */
    protected val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Logger.e(throwable, "Error no manejado en ${this::class.simpleName}")
        handleError(throwable)
    }

    /**
     * StateFlow para el estado de loading.
     * True cuando hay una operación en progreso.
     */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * SharedFlow para eventos de error.
     * Se usa SharedFlow en lugar de StateFlow porque los errores son eventos de un solo uso.
     */
    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    /**
     * SharedFlow para mensajes de éxito.
     */
    private val _successEvent = MutableSharedFlow<String>()
    val successEvent: SharedFlow<String> = _successEvent.asSharedFlow()

    /**
     * Ejecuta un bloque de código dentro de una coroutine con manejo de errores.
     *
     * @param showLoading Si debe mostrar el indicador de loading
     * @param block Bloque de código suspend a ejecutar
     */
    protected fun launchWithLoading(
        showLoading: Boolean = true,
        block: suspend CoroutineScope.() -> Unit
    ) {
        viewModelScope.launch(exceptionHandler) {
            if (showLoading) _isLoading.value = true
            try {
                block()
            } finally {
                if (showLoading) _isLoading.value = false
            }
        }
    }

    /**
     * Maneja errores y los convierte en mensajes amigables para el usuario.
     */
    protected open fun handleError(throwable: Throwable) {
        val errorMessage = when (throwable) {
            is java.net.UnknownHostException -> "Sin conexión a internet"
            is java.net.SocketTimeoutException -> "Tiempo de espera agotado"
            is java.io.IOException -> "Error de comunicación"
            else -> throwable.message ?: "Error desconocido"
        }

        viewModelScope.launch {
            _errorEvent.emit(errorMessage)
        }
    }

    /**
     * Emite un evento de error manual.
     */
    protected fun emitError(message: String) {
        viewModelScope.launch {
            _errorEvent.emit(message)
        }
    }

    /**
     * Emite un evento de éxito.
     */
    protected fun emitSuccess(message: String) {
        viewModelScope.launch {
            _successEvent.emit(message)
        }
    }

    /**
     * Actualiza el estado de loading manualmente.
     */
    protected fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    /**
     * Hook para limpiar recursos cuando el ViewModel es destruido.
     * Los ViewModels hijos pueden sobrescribir este método.
     */
    override fun onCleared() {
        super.onCleared()
        Logger.d("${this::class.simpleName} cleared")
    }
}

/**
 * Sealed class para representar estados de UI de forma type-safe.
 * Útil para operaciones que tienen múltiples estados.
 */
sealed class UIState<out T> {
    object Idle : UIState<Nothing>()
    object Loading : UIState<Nothing>()
    data class Success<T>(val data: T) : UIState<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : UIState<Nothing>()
}
