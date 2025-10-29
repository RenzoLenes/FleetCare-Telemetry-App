package com.fleetcare.obd.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

/**
 * Fragment base abstracto que proporciona funcionalidad común a todos los fragments.
 *
 * Implementa ViewBinding de forma genérica para evitar código repetitivo en cada Fragment.
 * Maneja automáticamente la limpieza del binding para prevenir memory leaks.
 *
 * Todos los fragments de la aplicación deben heredar de esta clase base.
 *
 * @param VB Tipo de ViewBinding específico del Fragment
 */
abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    // Binding que puede ser null para manejar el ciclo de vida correctamente
    private var _binding: VB? = null

    /**
     * Propiedad que garantiza que el binding no es null cuando se accede.
     * Solo debe usarse entre onCreateView y onDestroyView.
     */
    protected val binding get() = _binding!!

    /**
     * Método abstracto que cada Fragment debe implementar para crear su ViewBinding.
     * Se llama desde onCreateView.
     */
    abstract fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): VB

    /**
     * Método abstracto para inicializar la UI.
     * Se llama después de que el binding está creado.
     */
    abstract fun setupUI()

    /**
     * Método abstracto para configurar observadores de LiveData/Flow.
     * Se llama después de setupUI.
     */
    abstract fun observeData()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = getViewBinding(inflater, container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializar UI y observadores
        setupUI()
        observeData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Limpiar binding para evitar memory leaks
        _binding = null
    }

    /**
     * Muestra un diálogo de carga genérico.
     * Los fragments hijos pueden sobrescribir este método para personalizarlo.
     */
    protected open fun showLoading() {
        // Implementación por defecto
        // En Sprint posterior se agregará un LoadingDialog
    }

    /**
     * Oculta el diálogo de carga.
     */
    protected open fun hideLoading() {
        // Implementación por defecto
    }

    /**
     * Muestra un mensaje de error genérico.
     * Los fragments hijos pueden sobrescribir este método.
     */
    protected open fun showError(message: String) {
        // Por ahora mostrar en log, después se agregará UI apropiada
        com.fleetcare.obd.utils.Logger.e("Error in ${this::class.simpleName}: $message")
    }
}
