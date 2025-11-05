package com.fleetcare.obd.ui.dashboard

import androidx.lifecycle.viewModelScope
import com.fleetcare.obd.domain.model.ConnectionState
import com.fleetcare.obd.domain.model.DiagnosticTroubleCode
import com.fleetcare.obd.domain.model.VehicleData
import com.fleetcare.obd.domain.repository.BluetoothRepository
import com.fleetcare.obd.domain.repository.DiagnosticRepository
import com.fleetcare.obd.domain.repository.VehicleRepository
import com.fleetcare.obd.domain.model.PIDRangeCategory
import com.fleetcare.obd.domain.model.SupportedPIDsBitmap
import com.fleetcare.obd.domain.usecase.DetectSupportedPIDsUseCase
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
    private val readDTCsUseCase: ReadDTCsUseCase,
    private val detectSupportedPIDsUseCase: DetectSupportedPIDsUseCase // Sprint 2
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
     * Sprint 2: PIDs soportados por el vehículo.
     */
    private val _supportedPIDs = MutableStateFlow<SupportedPIDsBitmap?>(null)
    val supportedPIDs: StateFlow<SupportedPIDsBitmap?> = _supportedPIDs.asStateFlow()

    /**
     * Sprint 2: Indica si se están detectando PIDs.
     */
    private val _isDetectingPIDs = MutableStateFlow(false)
    val isDetectingPIDs: StateFlow<Boolean> = _isDetectingPIDs.asStateFlow()

    /**
     * Sprint 2: Items para mostrar en la lista de PIDs por categoría.
     */
    val pidCategoryItems: StateFlow<List<PIDCategoryItem>> =
        _supportedPIDs.map { bitmap ->
            bitmap?.let { convertToCategoryItems(it) } ?: emptyList()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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
                    // Sprint 9.5: Detectar PIDs soportados automáticamente al conectar
                    val currentBitmap = _supportedPIDs.value
                    Logger.d("Estado de bitmap al conectar: ${if (currentBitmap == null) "null" else "${currentBitmap.getTotalSupportedCount()} PIDs"}")

                    if (currentBitmap == null || currentBitmap.getTotalSupportedCount() == 0) {
                        Logger.d("Conexión establecida, detectando PIDs soportados...")
                        detectSupportedPIDs(forceRefresh = false)
                    } else {
                        Logger.d("Bitmap ya cargado, usando caché con ${currentBitmap.getTotalSupportedCount()} PIDs")
                    }

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

    /**
     * Sprint 2: Detecta los PIDs soportados por el vehículo.
     *
     * @param forceRefresh Forzar nueva detección ignorando caché
     */
    fun detectSupportedPIDs(forceRefresh: Boolean = false) {
        Logger.d("🔍 detectSupportedPIDs llamado (forceRefresh=$forceRefresh)")

        val currentState = connectionState.value
        if (currentState !is ConnectionState.Connected) {
            Logger.w("No se puede detectar PIDs: no hay conexión activa")
            emitError("Debes estar conectado para detectar PIDs")
            return
        }

        Logger.d("🔍 Conexión verificada, iniciando detección...")

        launchWithLoading(showLoading = false) {
            _isDetectingPIDs.value = true
            Logger.d("🔍 Iniciando detección de PIDs soportados (forceRefresh=$forceRefresh)...")

            val result = detectSupportedPIDsUseCase.execute(
                vehicleId = currentState.device.address,
                vin = null, // TODO: Obtener VIN si está disponible
                forceRefresh = forceRefresh
            )

            result.onSuccess { bitmap ->
                _supportedPIDs.value = bitmap
                Logger.i("✅ PIDs detectados exitosamente: ${bitmap.getTotalSupportedCount()} PIDs soportados")
                Logger.d("   PIDs: ${bitmap.allSupportedPIDs.joinToString(", ") { "0x${it.toString(16).uppercase()}" }}")
                emitSuccess("${bitmap.getTotalSupportedCount()} PIDs detectados")
            }.onFailure { exception ->
                Logger.e(exception, "❌ Error al detectar PIDs soportados")
                emitError("Error al detectar PIDs: ${exception.message}")
            }

            _isDetectingPIDs.value = false
            Logger.d("🔍 Detección de PIDs finalizada")
        }
    }

    /**
     * Sprint 2: Convierte el bitmap de PIDs a items de categorías para la UI.
     */
    private fun convertToCategoryItems(bitmap: SupportedPIDsBitmap): List<PIDCategoryItem> {
        val grouped = bitmap.groupByCategory()

        return grouped.map { (category, pids) ->
            val categoryName = when (category) {
                PIDRangeCategory.ENGINE -> "Motor"
                PIDRangeCategory.FUEL -> "Combustible y Aire"
                PIDRangeCategory.EMISSIONS -> "Emisiones"
                PIDRangeCategory.TRANSMISSION -> "Transmisión"
                PIDRangeCategory.HYBRID -> "Sistema Híbrido"
                PIDRangeCategory.EXTENDED -> "Extendido"
                PIDRangeCategory.MANUFACTURER -> "Fabricante"
                PIDRangeCategory.UNKNOWN -> "Otros"
            }

            val pidListFormatted = pids.joinToString(", ") {
                "0x${it.toString(16).uppercase().padStart(2, '0')}"
            }

            PIDCategoryItem(
                category = category,
                categoryName = categoryName,
                pidCount = pids.size,
                pidListFormatted = pidListFormatted
            )
        }.sortedBy { it.category.ordinal }
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
