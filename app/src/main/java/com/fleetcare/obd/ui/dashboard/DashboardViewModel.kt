package com.fleetcare.obd.ui.dashboard

import androidx.lifecycle.viewModelScope
import com.fleetcare.obd.domain.model.ConnectionState
import com.fleetcare.obd.domain.model.DiagnosticTroubleCode
import com.fleetcare.obd.domain.model.VehicleData
import com.fleetcare.obd.domain.repository.BluetoothRepository
import com.fleetcare.obd.domain.repository.DiagnosticRepository
import com.fleetcare.obd.domain.repository.VehicleRepository
import com.fleetcare.obd.domain.usecase.ReadDTCsUseCase
import com.fleetcare.obd.domain.usecase.ReadVehicleDataUseCase
import com.fleetcare.obd.domain.usecase.SendDataToFirebaseUseCase
import com.fleetcare.obd.ui.common.BaseViewModel
import com.fleetcare.obd.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para el Dashboard principal.
 *
 * Gestiona:
 * - Observación de datos del vehículo en tiempo real
 * - Estado de conexión Bluetooth
 * - Inicio/detención de lectura de datos
 * - Sincronización automática con Firebase
 * - Lectura de códigos de diagnóstico (DTCs)
 * - Conversión de unidades según preferencias del usuario
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
    private val vehicleRepository: VehicleRepository,
    private val diagnosticRepository: DiagnosticRepository,
    private val readVehicleDataUseCase: ReadVehicleDataUseCase,
    private val sendDataToFirebaseUseCase: SendDataToFirebaseUseCase,
    private val readDTCsUseCase: ReadDTCsUseCase
) : BaseViewModel() {

    /**
     * Estado de la conexión Bluetooth.
     */
    val connectionState: StateFlow<ConnectionState> =
        bluetoothRepository.connectionState as StateFlow

    /**
     * Datos del vehículo en tiempo real.
     */
    val vehicleData: StateFlow<VehicleData> =
        vehicleRepository.vehicleDataFlow as StateFlow

    /**
     * Indica si la lectura continua está activa.
     */
    val isReading: StateFlow<Boolean> =
        vehicleRepository.isReading as StateFlow

    /**
     * Códigos de diagnóstico en tiempo real.
     */
    val diagnosticCodes: StateFlow<List<DiagnosticTroubleCode>> =
        diagnosticRepository.dtcFlow as StateFlow

    /**
     * Indica si hay sincronización con Firebase activa.
     */
    private val _isSyncingToFirebase = MutableStateFlow(false)
    val isSyncingToFirebase: StateFlow<Boolean> = _isSyncingToFirebase.asStateFlow()

    /**
     * Estadísticas de sincronización con Firebase para diagnóstico.
     */
    val firebaseSyncStats = sendDataToFirebaseUseCase.syncStats

    /**
     * Estado combinado de conexión y lectura para la UI.
     */
    val dashboardState: StateFlow<DashboardState> =
        combine(
            connectionState,
            isReading,
            vehicleData,
            _isSyncingToFirebase
        ) { connection, reading, data, syncing ->
            when {
                connection !is ConnectionState.Connected -> {
                    DashboardState.Disconnected
                }
                !connection.isOBDInitialized -> {
                    DashboardState.Connecting
                }
                !reading -> {
                    DashboardState.Connected
                }
                !data.hasData -> {
                    DashboardState.ReadingData
                }
                else -> {
                    DashboardState.DataAvailable(data, syncing)
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardState.Disconnected
        )

    init {
        // Observar cambios de conexión para iniciar lectura automática
        observeConnectionState()
    }

    /**
     * Observa el estado de conexión y inicia lectura automáticamente cuando se conecta.
     */
    private fun observeConnectionState() {
        viewModelScope.launch {
            connectionState.collectLatest { state ->
                if (state is ConnectionState.Connected && state.isOBDInitialized) {
                    // Auto-iniciar lectura cuando se establece conexión
                    if (!isReading.value) {
                        startReading()
                        startFirebaseSync(state.device)
                    }
                } else {
                    // Detener lectura si se pierde conexión
                    if (isReading.value) {
                        stopReading()
                        stopFirebaseSync()
                    }
                }
            }
        }
    }

    /**
     * Inicia la lectura continua de datos.
     */
    fun startReading() {
        launchWithLoading(showLoading = false) {
            Logger.d("Iniciando lectura de datos desde Dashboard...")

            val result = readVehicleDataUseCase()

            result.onSuccess {
                Logger.d("Lectura continua iniciada exitosamente")
            }.onFailure { exception ->
                Logger.e(exception, "Error al iniciar lectura")
                emitError("Error al iniciar lectura: ${exception.message}")
            }
        }
    }

    /**
     * Detiene la lectura continua.
     */
    fun stopReading() {
        readVehicleDataUseCase.stop()
        Logger.d("Lectura continua detenida")
    }

    /**
     * Inicia la sincronización automática con Firebase.
     */
    private fun startFirebaseSync(device: com.fleetcare.obd.domain.model.BluetoothDevice) {
        launchWithLoading(showLoading = false) {
            Logger.d("Iniciando sincronización con Firebase...")

            val result = sendDataToFirebaseUseCase.start(
                vehicleId = device.address,
                vehicleName = device.name ?: ""
            )

            result.onSuccess {
                _isSyncingToFirebase.value = true
                Logger.d("Sincronización con Firebase iniciada")
            }.onFailure { exception ->
                Logger.e(exception, "Error al iniciar sincronización con Firebase")
                emitError("Error al iniciar Firebase: ${exception.message}")
            }
        }
    }

    /**
     * Detiene la sincronización con Firebase.
     */
    private fun stopFirebaseSync() {
        sendDataToFirebaseUseCase.stop()
        _isSyncingToFirebase.value = false
        Logger.d("Sincronización con Firebase detenida")
    }

    /**
     * Lee códigos de diagnóstico (DTCs).
     */
    fun readDiagnosticCodes() {
        launchWithLoading {
            Logger.d("Leyendo códigos de diagnóstico...")

            val result = readDTCsUseCase(includePending = true)

            result.onSuccess { dtcs ->
                Logger.d("${dtcs.size} códigos de diagnóstico leídos")

                if (dtcs.size == 1 && dtcs[0].code == "P0000") {
                    emitSuccess("Sin códigos de error detectados")
                } else {
                    emitSuccess("${dtcs.size} códigos encontrados")
                }
            }.onFailure { exception ->
                Logger.e(exception, "Error al leer DTCs")
                emitError("Error al leer códigos: ${exception.message}")
            }
        }
    }

    /**
     * Formatea un valor con unidad.
     */
    fun formatValue(value: Double?, unit: String, decimals: Int = 1): String {
        return if (value != null) {
            "%.${decimals}f %s".format(value, unit)
        } else {
            "--"
        }
    }

    /**
     * Formatea RPM.
     */
    fun formatRpm(rpm: Int?): String {
        return rpm?.toString() ?: "--"
    }

    /**
     * Resetea las estadísticas de sincronización con Firebase.
     */
    fun resetFirebaseStats() {
        sendDataToFirebaseUseCase.resetStats()
        Logger.d("Estadísticas de Firebase reseteadas")
    }

    /**
     * Realiza un test manual de escritura a Firebase.
     * Envía datos de prueba para verificar conectividad.
     */
    fun testFirebaseWrite() {
        launchWithLoading {
            Logger.d("🧪 Iniciando test manual de Firebase...")

            val currentState = connectionState.value
            if (currentState is ConnectionState.Connected) {
                val testResult = sendDataToFirebaseUseCase.start(
                    vehicleId = "TEST_${System.currentTimeMillis()}",
                    vehicleName = "Test Device"
                )

                testResult.onSuccess {
                    emitSuccess("Test de Firebase exitoso. Verifica los logs para más detalles.")
                    Logger.d("✅ Test de Firebase completado")
                }.onFailure { exception ->
                    emitError("Test falló: ${exception.message}")
                    Logger.e(exception, "❌ Test de Firebase falló")
                }
            } else {
                emitError("Debes estar conectado para realizar el test")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopReading()
        stopFirebaseSync()
    }
}

/**
 * Sealed class que representa los diferentes estados del dashboard.
 */
sealed class DashboardState {
    /**
     * No hay conexión Bluetooth.
     */
    object Disconnected : DashboardState()

    /**
     * Conectando o inicializando OBDII.
     */
    object Connecting : DashboardState()

    /**
     * Conectado pero no leyendo datos.
     */
    object Connected : DashboardState()

    /**
     * Leyendo datos pero aún no hay valores disponibles.
     */
    object ReadingData : DashboardState()

    /**
     * Datos disponibles para mostrar.
     *
     * @param data Datos del vehículo
     * @param isSyncingToFirebase Indica si se está sincronizando con Firebase
     */
    data class DataAvailable(
        val data: VehicleData,
        val isSyncingToFirebase: Boolean = false
    ) : DashboardState()
}
