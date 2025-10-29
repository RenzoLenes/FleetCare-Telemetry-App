package com.fleetcare.obd.ui.main

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.fleetcare.obd.R
import com.fleetcare.obd.databinding.ActivityMainBinding
import com.fleetcare.obd.utils.Logger
import com.fleetcare.obd.utils.showToast
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Activity principal de la aplicación FleetCare OBD.
 *
 * Esta Activity sirve como contenedor para el Navigation Component y maneja:
 * - Autenticación inicial de Firebase
 * - Configuración de la barra de navegación inferior
 * - Coordinación entre fragments mediante NavController
 * - Manejo de estados globales de la aplicación
 *
 * Usa ViewBinding para acceso seguro a vistas y Hilt para inyección de dependencias.
 *
 * La anotación AndroidEntryPoint permite que Hilt inyecte dependencias en esta Activity.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // ViewBinding para acceso seguro a las vistas del layout
    private lateinit var binding: ActivityMainBinding

    // ViewModel inyectado por Hilt usando el delegado viewModels()
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Logger.d("MainActivity creada")

        // Configurar componentes de UI
        setupToolbar()
        setupNavigation()
        observeViewModel()

        // Iniciar autenticación anónima de Firebase
        if (savedInstanceState == null) {
            // Solo autenticar en la primera creación, no en rotaciones de pantalla
            viewModel.authenticateAnonymously()
        }
    }

    /**
     * Configura el Toolbar personalizado.
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    /**
     * Configura el Navigation Component y la Bottom Navigation.
     *
     * Conecta el NavController con la BottomNavigationView para que
     * los elementos del menú naveguen automáticamente a los fragments correspondientes.
     */
    private fun setupNavigation() {
        // Obtener el NavHostFragment del contenedor
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        val navController = navHostFragment.navController

        // Conectar BottomNavigationView con NavController
        // Esto hace que los items del menú naveguen automáticamente
        binding.bottomNavigation.setupWithNavController(navController)

        // Actualizar el título del toolbar según el destino actual
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.toolbar.title = destination.label
        }

        Logger.d("Navigation Component configurado")
    }

    /**
     * Observa los estados y eventos del ViewModel.
     *
     * Usa lifecycleScope para recolectar Flows de forma segura respecto al ciclo de vida.
     * Los Flows se cancelan automáticamente cuando la Activity es destruida.
     */
    private fun observeViewModel() {
        // Observar estado de autenticación
        lifecycleScope.launch {
            viewModel.authState.collectLatest { state ->
                when (state) {
                    is AuthState.Idle -> {
                        Logger.d("Estado de autenticación: Idle")
                    }
                    is AuthState.Loading -> {
                        Logger.d("Autenticando...")
                        // Aquí se podría mostrar un indicador de loading
                    }
                    is AuthState.Authenticated -> {
                        Logger.i("Usuario autenticado: ${state.userId}")
                        showToast("Autenticación exitosa")
                    }
                    is AuthState.Error -> {
                        Logger.e("Error de autenticación: ${state.message}")
                        showError("Error de autenticación: ${state.message}")
                    }
                }
            }
        }

        // Observar eventos de error globales
        lifecycleScope.launch {
            viewModel.errorEvent.collectLatest { errorMessage ->
                showError(errorMessage)
            }
        }
    }

    /**
     * Muestra un mensaje de error usando Snackbar.
     */
    private fun showError(message: String) {
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.d("MainActivity destruida")
    }
}
