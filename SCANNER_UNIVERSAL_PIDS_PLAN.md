# 🔍 Scanner Universal de PIDs - Plan de Implementación

> **Objetivo:** Implementar un scanner completo de PIDs que funcione con TODOS los vehículos (legacy, modernos, CAN, ISO, KWP, etc.) para descubrir PIDs ocultos que no aparecen en los bitmaps estándar.

---

## 📊 Problema a Resolver

### Situación Actual
- ✅ Detección automática mediante bitmaps (0x00, 0x20, 0x40, etc.)
- ❌ Solo funciona si el vehículo reporta correctamente sus bitmaps
- ❌ Vehículos legacy no reportan bitmap 0x20+ → pierden 50+ PIDs
- ❌ Vehículos modernos ocultan PIDs del fabricante
- ❌ No hay forma de descubrir PIDs propietarios (Mode 22)

### Casos de Uso

#### 1. **Hyundai H1 2012 (Legacy - ISO 9141-2)**
```
Bitmap 0x00: ✅ Responde → 6 PIDs
Bitmap 0x20: ❌ NO DATA
PERO:
PID 0x21: ✅ Responde (Distancia con MIL)
PID 0x2F: ✅ Responde (Nivel combustible)
PID 0x42: ✅ Responde (Voltaje módulo)
...más PIDs ocultos
```

#### 2. **Toyota Corolla 2020 (Moderno - CAN)**
```
Bitmap 0x00-0x60: ✅ Responde → 80 PIDs estándar
PERO:
Mode 22 PIDs: ❌ No en bitmap
PID 0x22FF01: ✅ Estado batería híbrida
PID 0x22FF02: ✅ Temp motor eléctrico
...PIDs del fabricante ocultos
```

#### 3. **Suzuki Ertiga 2018 (Mixto - CAN)**
```
Bitmap 0x00-0x40: ✅ Responde → 50 PIDs
Bitmap 0x60+: ❌ NO DATA
PERO:
PID 0x61-0x7F: ✅ Algunos responden
Mode 02 PIDs: ✅ Freeze frames disponibles
```

### Solución Universal

**Scanner Inteligente** que:
1. ✅ Escanea TODOS los PIDs individualmente (no depende de bitmaps)
2. ✅ Soporta TODOS los protocolos (ISO, KWP, CAN, J1850)
3. ✅ Escanea MÚLTIPLES modos (01, 02, 03, 09, 22)
4. ✅ Detecta automáticamente el tipo de dato
5. ✅ Aprende y guarda PIDs descubiertos
6. ✅ Exporta resultados para compartir con comunidad

---

## 🎯 Objetivos del Scanner Universal

### Funcionales
1. ✅ Escanear todos los Modes OBD
   - Mode 01: Current data (0x00-0xFF)
   - Mode 02: Freeze frame data (0x00-0xFF)
   - Mode 03: Diagnostic trouble codes
   - Mode 09: Vehicle information (VIN, calibration, etc.)
   - Mode 22: Manufacturer specific (0x0000-0xFFFF)

2. ✅ Configuración flexible
   - Seleccionar rangos de PIDs (ej: solo 0x00-0x4F)
   - Elegir modos a escanear
   - Configurar timeouts por protocolo
   - Opción "Quick Scan" vs "Deep Scan"

3. ✅ Detección inteligente
   - Identificar tipo de dato (int, float, string, bitmap)
   - Sugerir unidades (RPM, km/h, °C, %)
   - Detectar patrones en respuestas
   - Categorizar PIDs automáticamente

4. ✅ Persistencia y análisis
   - Guardar resultados en Room DB
   - Asociar PIDs con vehículo (VIN/modelo)
   - Historial de scans
   - Comparar scans entre vehículos

5. ✅ Exportación y compartir
   - Exportar a JSON/CSV/TXT
   - Generar QR con PIDs descubiertos
   - Compartir con otros usuarios
   - Importar PIDs de otros vehículos

### No Funcionales
1. ✅ Performance optimizada
   - Escaneo paralelo cuando sea posible
   - Cache de respuestas
   - Cancelación rápida

2. ✅ UX intuitiva
   - Progreso en tiempo real
   - Estadísticas visuales
   - Filtros y búsqueda
   - Material Design 3

3. ✅ Robustez
   - Manejo de timeouts
   - Recuperación de errores
   - Validación de respuestas
   - Logs detallados

---

## 🏗️ Arquitectura Técnica

### Capas de la Arquitectura

```
┌──────────────────────────────────────────────────────────┐
│                      UI Layer                            │
│  ┌────────────────────────────────────────────────────┐  │
│  │ UniversalPIDScannerFragment                        │  │
│  │  - Tab: Quick Scan                                 │  │
│  │  - Tab: Advanced Config                            │  │
│  │  - Tab: Results & Analysis                         │  │
│  │  - Tab: Export & Share                             │  │
│  └────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────┐  │
│  │ UniversalPIDScannerViewModel                       │  │
│  │  - Coordina múltiples use cases                    │  │
│  │  - Maneja estado del scanner                       │  │
│  │  - Procesa resultados en tiempo real               │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────┐
│                    Domain Layer                          │
│  ┌────────────────────────────────────────────────────┐  │
│  │ Use Cases                                          │  │
│  │  - ScanMode01UseCase (current data)               │  │
│  │  - ScanMode02UseCase (freeze frame)               │  │
│  │  - ScanMode09UseCase (vehicle info)               │  │
│  │  - ScanMode22UseCase (manufacturer)               │  │
│  │  - AnalyzePIDResponseUseCase                      │  │
│  │  - DetectPIDTypeUseCase                           │  │
│  │  - ExportScanResultsUseCase                       │  │
│  └────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────┐  │
│  │ Models                                             │  │
│  │  - UniversalScanConfig                            │  │
│  │  - ScanResult (mejorado)                          │  │
│  │  - PIDMetadata                                    │  │
│  │  - ScanSession                                    │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────┐
│                     Data Layer                           │
│  ┌────────────────────────────────────────────────────┐  │
│  │ Repositories                                       │  │
│  │  - UniversalScanRepository                        │  │
│  │  - PIDMetadataRepository                          │  │
│  │  - VehicleProfileRepository                       │  │
│  └────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────┐  │
│  │ Room Database                                      │  │
│  │  - ScanResultEntity                               │  │
│  │  - ScanSessionEntity                              │  │
│  │  - PIDMetadataEntity                              │  │
│  │  - VehicleProfileEntity                           │  │
│  └────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────┐  │
│  │ External Services                                  │  │
│  │  - BluetoothService (envío de comandos)           │  │
│  │  - FirebaseDataSource (sync opcional)             │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

---

## 📦 Componentes Detallados

### 1. Domain Layer

#### 1.1 Models

##### UniversalScanConfig.kt
```kotlin
data class UniversalScanConfig(
    val vehicleId: String,
    val modes: List<ScanMode>,
    val pidRanges: Map<ScanMode, IntRange>,
    val timeout: Long = 300L,
    val skipKnownFailures: Boolean = true,
    val parallelScanning: Boolean = false,
    val retryFailedPIDs: Int = 0,
    val intelligentSkipping: Boolean = true  // Skip si 5 consecutivos fallan
)

enum class ScanMode {
    MODE_01_CURRENT_DATA,
    MODE_02_FREEZE_FRAME,
    MODE_03_DTCS,
    MODE_09_VEHICLE_INFO,
    MODE_22_MANUFACTURER;

    fun getCommandPrefix(): String = when(this) {
        MODE_01_CURRENT_DATA -> "01"
        MODE_02_FREEZE_FRAME -> "02"
        MODE_03_DTCS -> "03"
        MODE_09_VEHICLE_INFO -> "09"
        MODE_22_MANUFACTURER -> "22"
    }
}

// Presets para Quick Scan
object ScanPresets {
    val QUICK_SCAN = UniversalScanConfig(
        modes = listOf(ScanMode.MODE_01_CURRENT_DATA),
        pidRanges = mapOf(ScanMode.MODE_01_CURRENT_DATA to 0x00..0x4F),
        timeout = 200L
    )

    val FULL_STANDARD_SCAN = UniversalScanConfig(
        modes = listOf(
            ScanMode.MODE_01_CURRENT_DATA,
            ScanMode.MODE_09_VEHICLE_INFO
        ),
        pidRanges = mapOf(
            ScanMode.MODE_01_CURRENT_DATA to 0x00..0xFF,
            ScanMode.MODE_09_VEHICLE_INFO to 0x00..0x0F
        ),
        timeout = 300L
    )

    val DEEP_SCAN_ALL = UniversalScanConfig(
        modes = ScanMode.values().toList(),
        pidRanges = mapOf(
            ScanMode.MODE_01_CURRENT_DATA to 0x00..0xFF,
            ScanMode.MODE_02_FREEZE_FRAME to 0x00..0xFF,
            ScanMode.MODE_09_VEHICLE_INFO to 0x00..0xFF,
            ScanMode.MODE_22_MANUFACTURER to 0x0000..0x00FF  // Solo primeros 256
        ),
        timeout = 500L,
        intelligentSkipping = true
    )
}
```

##### PIDMetadata.kt
```kotlin
data class PIDMetadata(
    val mode: String,
    val pid: String,
    val name: String? = null,
    val description: String? = null,
    val unit: String? = null,
    val formula: String? = null,
    val detectedType: PIDDataType,
    val byteLength: Int,
    val isStandard: Boolean,
    val manufacturer: String? = null,
    val detectionTimestamp: Long = System.currentTimeMillis(),
    val vehicleVIN: String? = null
)

enum class PIDDataType {
    UNSIGNED_INT,     // 00, 0000
    SIGNED_INT,       // -40
    FLOAT,            // 12.5
    BITMAP,           // 10110011
    STRING,           // "TOYOTA"
    MULTI_BYTE,       // [01, 02, 03, 04]
    UNKNOWN
}

// Auto-detection helpers
fun detectTypeFromResponse(rawBytes: ByteArray): PIDDataType {
    // Lógica para detectar tipo de dato
}

fun suggestUnit(mode: String, pid: String, dataType: PIDDataType): String? {
    // Sugerir unidades basado en PID conocido
}
```

##### ScanResult.kt (Mejorado)
```kotlin
data class ScanResult(
    val id: Long = 0,
    val sessionId: String,
    val mode: String,
    val pid: String,
    val command: String,
    val success: Boolean,
    val rawResponse: String,
    val dataBytes: ByteArray = byteArrayOf(),
    val byteCount: Int = 0,
    val interpretation: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val latencyMs: Long = 0,
    val metadata: PIDMetadata? = null,
    val errorMessage: String? = null,
    val attemptNumber: Int = 1
) {
    // Helpers
    fun isStandardPID(): Boolean = metadata?.isStandard == true
    fun hasInterpretation(): Boolean = !interpretation.isNullOrBlank()
    fun getDisplayValue(): String = interpretation ?: rawResponse
}
```

##### ScanSession.kt (Mejorado)
```kotlin
data class ScanSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    val vehicleVIN: String? = null,
    val vehicleMake: String? = null,
    val vehicleModel: String? = null,
    val vehicleYear: Int? = null,
    val config: UniversalScanConfig,
    val protocol: String,  // ISO 9141-2, CAN 11/500, etc.
    val startTimestamp: Long = System.currentTimeMillis(),
    val endTimestamp: Long? = null,
    val state: ScanSessionState,
    val modesScanned: List<ScanMode>,
    val totalPIDsScanned: Int = 0,
    val successfulPIDs: Int = 0,
    val failedPIDs: Int = 0,
    val newPIDsDiscovered: Int = 0,
    val averageLatencyMs: Long = 0
)

enum class ScanSessionState {
    INITIALIZING,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    ERROR
}
```

##### VehicleProfile.kt
```kotlin
data class VehicleProfile(
    val vehicleId: String,
    val vin: String? = null,
    val make: String,
    val model: String,
    val year: Int,
    val protocol: String,
    val knownPIDs: List<PIDMetadata> = emptyList(),
    val scanSessions: List<String> = emptyList(),  // Session IDs
    val lastScannedTimestamp: Long? = null,
    val totalPIDsKnown: Int = 0
)
```

#### 1.2 Use Cases

##### ScanMode01UseCase.kt
```kotlin
@Singleton
class ScanMode01UseCase @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
    private val pidMetadataRepository: PIDMetadataRepository,
    private val detectTypeUseCase: DetectPIDTypeUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun execute(
        config: UniversalScanConfig
    ): Flow<ScanProgress> = flow {
        val range = config.pidRanges[ScanMode.MODE_01_CURRENT_DATA]
            ?: 0x00..0xFF

        var consecutiveFailures = 0
        val intelligentSkip = config.intelligentSkipping

        for (pid in range) {
            // Intelligent skipping: si 5 PIDs consecutivos fallan, skip next 10
            if (intelligentSkip && consecutiveFailures >= 5) {
                emit(ScanProgress(
                    currentPID = pid,
                    status = "Saltando rango (5+ fallos consecutivos)"
                ))
                // Skip next 10 PIDs
                for (i in 0 until 10) {
                    if (range.contains(pid + i)) {
                        emit(ScanProgress(currentPID = pid + i, skipped = true))
                    }
                }
                consecutiveFailures = 0
                continue
            }

            // Escanear PID
            val command = "01${pid.toString(16).padStart(2, '0')}"
            val startTime = System.currentTimeMillis()

            val result = bluetoothRepository.sendCommand(command, config.timeout)
            val latency = System.currentTimeMillis() - startTime

            if (result.isSuccess && !isErrorResponse(result.getOrNull())) {
                consecutiveFailures = 0

                // Detectar tipo y metadata
                val metadata = detectTypeUseCase.analyze(
                    mode = "01",
                    pid = pid.toString(16),
                    response = result.getOrNull() ?: ""
                )

                val scanResult = ScanResult(
                    mode = "01",
                    pid = pid.toString(16),
                    command = command,
                    success = true,
                    rawResponse = result.getOrNull() ?: "",
                    latencyMs = latency,
                    metadata = metadata
                )

                emit(ScanProgress(
                    currentPID = pid,
                    result = scanResult,
                    status = "✓ PID $pid responde"
                ))
            } else {
                consecutiveFailures++

                emit(ScanProgress(
                    currentPID = pid,
                    result = ScanResult(
                        mode = "01",
                        pid = pid.toString(16),
                        command = command,
                        success = false,
                        rawResponse = result.getOrNull() ?: "",
                        errorMessage = result.exceptionOrNull()?.message
                    ),
                    status = "✗ PID $pid no responde"
                ))
            }

            delay(50)  // Pequeño delay entre PIDs
        }
    }.flowOn(ioDispatcher)
}
```

##### ScanMode22UseCase.kt
```kotlin
@Singleton
class ScanMode22UseCase @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun execute(
        config: UniversalScanConfig,
        manufacturerPIDList: List<String>? = null  // PIDs conocidos del fabricante
    ): Flow<ScanProgress> = flow {
        // Mode 22 es 0x2200-0x22FFFF
        // Escanear solo conocidos o rango limitado

        val pidsToScan = manufacturerPIDList ?: getCommonManufacturerPIDs()

        for (pidHex in pidsToScan) {
            val command = "22$pidHex"

            val result = bluetoothRepository.sendCommand(command, config.timeout)

            // ... similar a Mode01UseCase
        }
    }.flowOn(ioDispatcher)

    private fun getCommonManufacturerPIDs(): List<String> {
        // PIDs comunes por fabricante
        return listOf(
            "F190",  // VIN
            "F191",  // ECU SW number
            "F192",  // ECU HW number
            "FF00",  // Manufacturer specific
            // ... más PIDs comunes
        )
    }
}
```

##### DetectPIDTypeUseCase.kt
```kotlin
@Singleton
class DetectPIDTypeUseCase @Inject constructor() {

    fun analyze(mode: String, pid: String, response: String): PIDMetadata {
        val dataBytes = parseDataBytes(response)

        // Auto-detect type
        val detectedType = when {
            dataBytes.size == 1 -> PIDDataType.UNSIGNED_INT
            dataBytes.size == 2 -> detectTwoByteType(dataBytes)
            dataBytes.all { it in 0x20..0x7E } -> PIDDataType.STRING
            else -> PIDDataType.MULTI_BYTE
        }

        // Suggest interpretation
        val interpretation = interpretData(mode, pid, dataBytes, detectedType)

        // Suggest unit
        val unit = suggestUnit(mode, pid, detectedType)

        return PIDMetadata(
            mode = mode,
            pid = pid,
            detectedType = detectedType,
            byteLength = dataBytes.size,
            unit = unit,
            isStandard = isStandardPID(mode, pid),
            name = getStandardPIDName(mode, pid)
        )
    }

    private fun detectTwoByteType(bytes: ByteArray): PIDDataType {
        val value = (bytes[0].toInt() shl 8) or bytes[1].toInt()
        return when {
            value in 0..65535 -> PIDDataType.UNSIGNED_INT
            value and 0x8000 != 0 -> PIDDataType.SIGNED_INT
            else -> PIDDataType.MULTI_BYTE
        }
    }

    private fun interpretData(
        mode: String,
        pid: String,
        bytes: ByteArray,
        type: PIDDataType
    ): String {
        // Interpretación automática basada en PIDs conocidos
        return when (mode to pid) {
            "01" to "0C" -> "${parseRPM(bytes)} RPM"
            "01" to "0D" -> "${bytes[0].toInt()} km/h"
            "01" to "05" -> "${bytes[0].toInt() - 40} °C"
            // ... más interpretaciones
            else -> bytes.joinToString(" ") { "%02X".format(it) }
        }
    }
}
```

##### ExportScanResultsUseCase.kt
```kotlin
@Singleton
class ExportScanResultsUseCase @Inject constructor(
    private val scanResultRepository: UniversalScanRepository,
    private val gson: Gson
) {
    suspend fun exportToJson(sessionId: String): String {
        val session = scanResultRepository.getSession(sessionId)
        val results = scanResultRepository.getResults(sessionId)

        val export = mapOf(
            "session" to session,
            "results" to results,
            "exportTimestamp" to System.currentTimeMillis()
        )

        return gson.toJson(export)
    }

    suspend fun exportToCsv(sessionId: String): String {
        val results = scanResultRepository.getResults(sessionId)

        val csv = StringBuilder()
        csv.appendLine("Mode,PID,Command,Success,Response,Interpretation,Latency(ms)")

        results.forEach { result ->
            csv.appendLine(
                "${result.mode},${result.pid},${result.command}," +
                "${result.success},\"${result.rawResponse}\"," +
                "\"${result.interpretation ?: ""}\",${result.latencyMs}"
            )
        }

        return csv.toString()
    }

    suspend fun generateQRCode(sessionId: String): Bitmap {
        // Generar QR con datos del scan para compartir
        val session = scanResultRepository.getSession(sessionId)
        val successfulPIDs = scanResultRepository.getSuccessfulResults(sessionId)

        val qrData = mapOf(
            "vehicle" to session.vehicleVIN,
            "protocol" to session.protocol,
            "pids" to successfulPIDs.map { "${it.mode}${it.pid}" }
        )

        return QRCodeGenerator.generate(gson.toJson(qrData))
    }
}
```

---

### 2. Data Layer

#### 2.1 Entities

##### ScanResultEntity.kt
```kotlin
@Entity(
    tableName = "universal_scan_results",
    foreignKeys = [
        ForeignKey(
            entity = ScanSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index("mode"),
        Index("pid"),
        Index("success")
    ]
)
data class ScanResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val mode: String,
    val pid: String,
    val command: String,
    val success: Boolean,
    val rawResponse: String,
    val dataBytesHex: String,  // ByteArray as hex string
    val byteCount: Int,
    val interpretation: String?,
    val timestamp: Long,
    val latencyMs: Long,
    val metadataJson: String?,  // PIDMetadata as JSON
    val errorMessage: String?,
    val attemptNumber: Int
)
```

##### PIDMetadataEntity.kt
```kotlin
@Entity(
    tableName = "pid_metadata",
    indices = [
        Index(value = ["mode", "pid"], unique = true),
        Index("vehicleVIN"),
        Index("manufacturer")
    ]
)
data class PIDMetadataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mode: String,
    val pid: String,
    val name: String?,
    val description: String?,
    val unit: String?,
    val formula: String?,
    val detectedType: String,
    val byteLength: Int,
    val isStandard: Boolean,
    val manufacturer: String?,
    val detectionTimestamp: Long,
    val vehicleVIN: String?,
    val confidence: Float = 1.0f  // 0.0-1.0 para auto-detected
)
```

##### VehicleProfileEntity.kt
```kotlin
@Entity(
    tableName = "vehicle_profiles",
    indices = [Index("vin", unique = true)]
)
data class VehicleProfileEntity(
    @PrimaryKey
    val vehicleId: String,
    val vin: String?,
    val make: String,
    val model: String,
    val year: Int,
    val protocol: String,
    val knownPIDsJson: String,  // List<PIDMetadata> as JSON
    val scanSessionIds: String,  // List<String> as JSON
    val lastScannedTimestamp: Long?,
    val totalPIDsKnown: Int
)
```

#### 2.2 DAOs

##### UniversalScanDao.kt
```kotlin
@Dao
interface UniversalScanDao {
    // Scan Results
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ScanResultEntity)

    @Insert
    suspend fun insertResults(results: List<ScanResultEntity>)

    @Query("SELECT * FROM universal_scan_results WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getResults(sessionId: String): Flow<List<ScanResultEntity>>

    @Query("SELECT * FROM universal_scan_results WHERE sessionId = :sessionId AND success = 1")
    suspend fun getSuccessfulResults(sessionId: String): List<ScanResultEntity>

    @Query("SELECT * FROM universal_scan_results WHERE sessionId = :sessionId AND mode = :mode")
    suspend fun getResultsByMode(sessionId: String, mode: String): List<ScanResultEntity>

    // Sessions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ScanSessionEntity)

    @Query("SELECT * FROM scan_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): ScanSessionEntity?

    @Query("SELECT * FROM scan_sessions ORDER BY startTimestamp DESC LIMIT :limit")
    fun getRecentSessions(limit: Int = 10): Flow<List<ScanSessionEntity>>

    // PID Metadata
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: PIDMetadataEntity)

    @Query("SELECT * FROM pid_metadata WHERE mode = :mode AND pid = :pid")
    suspend fun getMetadata(mode: String, pid: String): PIDMetadataEntity?

    @Query("SELECT * FROM pid_metadata WHERE vehicleVIN = :vin")
    suspend fun getMetadataForVehicle(vin: String): List<PIDMetadataEntity>

    // Vehicle Profiles
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicleProfile(profile: VehicleProfileEntity)

    @Query("SELECT * FROM vehicle_profiles WHERE vehicleId = :vehicleId")
    suspend fun getVehicleProfile(vehicleId: String): VehicleProfileEntity?

    @Query("SELECT * FROM vehicle_profiles ORDER BY lastScannedTimestamp DESC")
    fun getAllVehicleProfiles(): Flow<List<VehicleProfileEntity>>
}
```

---

### 3. UI Layer

#### 3.1 Fragment

**UniversalPIDScannerFragment.kt**

```kotlin
@AndroidEntryPoint
class UniversalPIDScannerFragment : BaseFragment<FragmentUniversalPidScannerBinding>() {

    private val viewModel: UniversalPIDScannerViewModel by viewModels()

    // Adapters
    private val resultsAdapter = UniversalScanResultAdapter(
        onItemClick = { result -> showResultDetails(result) },
        onItemLongClick = { result -> showResultOptions(result) }
    )

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = FragmentUniversalPidScannerBinding.inflate(inflater, container, false)

    override fun setupUI() {
        setupTabs()
        setupRecyclerView()
        setupButtons()
        setupFilters()
    }

    override fun observeData() {
        observeScanState()
        observeProgress()
        observeResults()
        observeStatistics()
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Quick Scan"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Advanced"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Results"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Export"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showTabContent(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun showScanConfigDialog() {
        UniversalScanConfigDialog(
            currentConfig = viewModel.currentConfig.value,
            onConfigSelected = { config ->
                viewModel.startScan(config)
            }
        ).show(childFragmentManager, "scan_config")
    }
}
```

#### 3.2 ViewModel

**UniversalPIDScannerViewModel.kt**

```kotlin
@HiltViewModel
class UniversalPIDScannerViewModel @Inject constructor(
    private val scanMode01UseCase: ScanMode01UseCase,
    private val scanMode02UseCase: ScanMode02UseCase,
    private val scanMode09UseCase: ScanMode09UseCase,
    private val scanMode22UseCase: ScanMode22UseCase,
    private val exportUseCase: ExportScanResultsUseCase,
    private val scanRepository: UniversalScanRepository,
    private val supportedPIDsRepository: SupportedPIDsRepository
) : BaseViewModel() {

    private val _scanState = MutableStateFlow<ScannerState>(ScannerState.IDLE)
    val scanState: StateFlow<ScannerState> = _scanState.asStateFlow()

    private val _currentConfig = MutableStateFlow(ScanPresets.QUICK_SCAN)
    val currentConfig: StateFlow<UniversalScanConfig> = _currentConfig.asStateFlow()

    private val _progress = MutableStateFlow<ScanProgress?>(null)
    val progress: StateFlow<ScanProgress?> = _progress.asStateFlow()

    private val _results = MutableStateFlow<List<ScanResult>>(emptyList())
    val results: StateFlow<List<ScanResult>> = _results.asStateFlow()

    private val _statistics = MutableStateFlow<ScanStatistics?>(null)
    val statistics: StateFlow<ScanStatistics?> = _statistics.asStateFlow()

    private val _filter = MutableStateFlow(ScanFilter.ALL)
    private val _modeFilter = MutableStateFlow<ScanMode?>(null)

    private var currentJob: Job? = null
    private var currentSessionId: String? = null

    val filteredResults: StateFlow<List<ScanResult>> = combine(
        results, _filter, _modeFilter
    ) { results, filter, modeFilter ->
        var filtered = results

        // Filter by success/failure
        filtered = when (filter) {
            ScanFilter.ALL -> filtered
            ScanFilter.SUCCESS_ONLY -> filtered.filter { it.success }
            ScanFilter.FAILED_ONLY -> filtered.filter { !it.success }
        }

        // Filter by mode
        if (modeFilter != null) {
            filtered = filtered.filter { it.mode == modeFilter.getCommandPrefix() }
        }

        filtered
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun startScan(config: UniversalScanConfig = currentConfig.value) {
        currentJob?.cancel()

        currentJob = viewModelScope.launch {
            try {
                _scanState.value = ScannerState.SCANNING
                _currentConfig.value = config
                currentSessionId = UUID.randomUUID().toString()

                val allResults = mutableListOf<ScanResult>()

                // Scan each mode
                for (mode in config.modes) {
                    val useCase = when (mode) {
                        ScanMode.MODE_01_CURRENT_DATA -> scanMode01UseCase
                        ScanMode.MODE_02_FREEZE_FRAME -> scanMode02UseCase
                        ScanMode.MODE_09_VEHICLE_INFO -> scanMode09UseCase
                        ScanMode.MODE_22_MANUFACTURER -> scanMode22UseCase
                        else -> continue
                    }

                    useCase.execute(config)
                        .collect { progress ->
                            _progress.value = progress

                            progress.result?.let { result ->
                                allResults.add(result)
                                _results.value = allResults.toList()

                                // Save to DB
                                scanRepository.saveResult(result)

                                // Update statistics
                                updateStatistics(allResults)
                            }
                        }
                }

                // Update SupportedPIDsBitmap with discovered PIDs
                updatePIDsBitmap(allResults.filter { it.success })

                _scanState.value = ScannerState.COMPLETED
                emitSuccess("Scan completado: ${allResults.count { it.success }} PIDs descubiertos")

            } catch (e: Exception) {
                _scanState.value = ScannerState.ERROR
                emitError("Error durante scan: ${e.message}")
            }
        }
    }

    fun pauseScan() {
        currentJob?.cancel()
        _scanState.value = ScannerState.PAUSED
    }

    fun resumeScan() {
        if (_scanState.value == ScannerState.PAUSED) {
            startScan()
        }
    }

    fun cancelScan() {
        currentJob?.cancel()
        _scanState.value = ScannerState.IDLE
        _progress.value = null
    }

    fun setFilter(filter: ScanFilter) {
        _filter.value = filter
    }

    fun setModeFilter(mode: ScanMode?) {
        _modeFilter.value = mode
    }

    fun exportResults(format: ExportFormat) {
        viewModelScope.launch {
            val sessionId = currentSessionId ?: return@launch

            val exported = when (format) {
                ExportFormat.JSON -> exportUseCase.exportToJson(sessionId)
                ExportFormat.CSV -> exportUseCase.exportToCsv(sessionId)
            }

            // Share or save
            shareText(exported, format)
        }
    }

    private suspend fun updatePIDsBitmap(successfulResults: List<ScanResult>) {
        val pidsByRange = successfulResults
            .filter { it.mode == "01" }
            .map { it.pid.toInt(16) }
            .groupBy { it / 32 }

        val bitmap = SupportedPIDsBitmap(
            pidRanges = pidsByRange,
            vehicleId = currentConfig.value.vehicleId,
            detectionTimestamp = System.currentTimeMillis()
        )

        supportedPIDsRepository.saveSupportedPIDs(bitmap)
    }

    private fun updateStatistics(results: List<ScanResult>) {
        val successful = results.count { it.success }
        val failed = results.count { !it.success }
        val total = results.size

        val avgLatency = if (results.isNotEmpty()) {
            results.map { it.latencyMs }.average().toLong()
        } else 0L

        _statistics.value = ScanStatistics(
            totalPIDs = 255,  // Will be calculated based on config
            scannedPIDs = total,
            successfulPIDs = successful,
            failedPIDs = failed,
            elapsedTimeMs = 0,  // Track from start
            estimatedTimeRemainingMs = 0,
            averageLatencyMs = avgLatency,
            successRate = if (total > 0) (successful.toFloat() / total) else 0f
        )
    }
}
```

---

### 4. UI Layouts

#### fragment_universal_pid_scanner.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            app:title="Scanner Universal de PIDs"/>

        <com.google.android.material.tabs.TabLayout
            android:id="@+id/tabLayout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:tabMode="scrollable"/>
    </com.google.android.material.appbar.AppBarLayout>

    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/viewPager"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior"/>

    <!-- FAB for quick actions -->
    <com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
        android:id="@+id/fabStartScan"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_margin="16dp"
        android:text="Iniciar Scan"
        app:icon="@drawable/ic_scan"/>
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

---

## 🎨 Mockups de UI

### Tab 1: Quick Scan
```
┌──────────────────────────────────────┐
│  Scanner Universal de PIDs           │
│  [Todos][Config][Results][Export]    │
├──────────────────────────────────────┤
│                                      │
│  🚗 Vehículo Detectado              │
│  Hyundai H1 2012                     │
│  Protocolo: ISO 9141-2               │
│  VIN: KMHSH81BDCU123456             │
│                                      │
│  ⚡ Presets Rápidos                  │
│  ┌─────────────────────────────────┐│
│  │ [Quick Scan (1-2 min)]         ││
│  │ PIDs estándar 0x00-0x4F         ││
│  └─────────────────────────────────┘│
│  ┌─────────────────────────────────┐│
│  │ [Full Scan (3-5 min)]          ││
│  │ Todos los PIDs Mode 01 + Mode 09││
│  └─────────────────────────────────┘│
│  ┌─────────────────────────────────┐│
│  │ [Deep Scan (10-15 min)]        ││
│  │ Todos los Modes + Manufacturer  ││
│  └─────────────────────────────────┘│
│                                      │
│         [Configuración Avanzada]     │
│                                      │
└──────────────────────────────────────┘
```

### Tab 2: Configuración Avanzada
```
┌──────────────────────────────────────┐
│  Configuración de Scan               │
├──────────────────────────────────────┤
│                                      │
│  📋 Modes a Escanear                │
│  ☑ Mode 01 - Current Data           │
│  ☐ Mode 02 - Freeze Frame           │
│  ☐ Mode 03 - DTCs                   │
│  ☑ Mode 09 - Vehicle Info           │
│  ☐ Mode 22 - Manufacturer           │
│                                      │
│  🎯 Rangos de PIDs                   │
│  Mode 01: [0x00] - [0xFF]           │
│  Mode 09: [0x00] - [0x0F]           │
│                                      │
│  ⏱️  Timeouts                        │
│  Por PID: [300] ms                  │
│  Total: [No límite ▼]              │
│                                      │
│  🧠 Opciones Inteligentes           │
│  ☑ Saltar fallos consecutivos       │
│  ☑ Re-intentar PIDs fallidos (1x)   │
│  ☐ Escaneo paralelo (experimental)  │
│                                      │
│         [Guardar Config]             │
│         [Iniciar Scan]               │
│                                      │
└──────────────────────────────────────┘
```

### Tab 3: Resultados
```
┌──────────────────────────────────────┐
│  Resultados del Scan                 │
├──────────────────────────────────────┤
│  📊 Progreso: 127/255 (50%)         │
│  ████████████░░░░░░░░░░░░░░          │
│                                      │
│  ✅ Exitosos: 42 | ❌ Fallidos: 85  │
│  ⏱️  2m 15s restantes               │
│                                      │
│  🔍 Filtros:                        │
│  [Todos][✓Exitosos][Fallidos]      │
│  [Mode 01▼][Estándar▼]             │
│                                      │
│  ┌────────────────────────────────┐ │
│  │ 📊 0x0C - Engine RPM           │ │
│  │ ✅ 410C0000 → 0 RPM            │ │
│  │ Latencia: 215ms                 │ │
│  ├────────────────────────────────┤ │
│  │ 📊 0x0D - Vehicle Speed        │ │
│  │ ✅ 410D00 → 0 km/h             │ │
│  │ Latencia: 189ms                 │ │
│  ├────────────────────────────────┤ │
│  │ 📊 0x2F - Fuel Level           │ │
│  │ ✅ 412F64 → 39%                │ │
│  │ Latencia: 267ms | 🆕 NUEVO!    │ │
│  ├────────────────────────────────┤ │
│  │ 📊 0x42 - Control Module V     │ │
│  │ ✅ 41423456 → 13.3V            │ │
│  │ Latencia: 234ms | 🆕 NUEVO!    │ │
│  └────────────────────────────────┘ │
│                                      │
└──────────────────────────────────────┘
```

### Tab 4: Exportar
```
┌──────────────────────────────────────┐
│  Exportar & Compartir                │
├──────────────────────────────────────┤
│                                      │
│  📁 Formatos de Exportación          │
│  ┌─────────────────────────────────┐│
│  │ [📄 JSON]                       ││
│  │ Formato completo con metadata    ││
│  └─────────────────────────────────┘│
│  ┌─────────────────────────────────┐│
│  │ [📊 CSV]                        ││
│  │ Tabla simple para Excel          ││
│  └─────────────────────────────────┘│
│  ┌─────────────────────────────────┐│
│  │ [📱 QR Code]                    ││
│  │ Compartir PIDs descubiertos      ││
│  └─────────────────────────────────┘│
│                                      │
│  💾 Historial de Scans              │
│  ┌─────────────────────────────────┐│
│  │ 02/11/2025 - 42 PIDs            ││
│  │ H1 2012 | ISO 9141-2            ││
│  └─────────────────────────────────┘│
│  ┌─────────────────────────────────┐│
│  │ 01/11/2025 - 35 PIDs            ││
│  │ H1 2012 | ISO 9141-2            ││
│  └─────────────────────────────────┘│
│                                      │
│  🌐 Compartir con Comunidad          │
│  [Compartir Anónimamente]            │
│                                      │
└──────────────────────────────────────┘
```

---

## 📝 Plan de Implementación

### Sprint 1: Core Domain (Semana 1)
**Objetivo:** Implementar modelos y lógica de negocio

1. ✅ Crear modelos en `domain/model/`:
   - UniversalScanConfig
   - ScanResult (mejorado)
   - PIDMetadata
   - ScanSession
   - VehicleProfile
   - ScanPresets

2. ✅ Crear Use Cases en `domain/usecase/`:
   - ScanMode01UseCase
   - ScanMode09UseCase
   - DetectPIDTypeUseCase
   - ExportScanResultsUseCase

3. ✅ Definir interfaces de repositorio

**Entregables:** Domain layer completo con tests unitarios

---

### Sprint 2: Data Layer (Semana 2)
**Objetivo:** Persistencia y acceso a datos

1. ✅ Crear entities en `data/local/entity/`:
   - ScanResultEntity
   - ScanSessionEntity
   - PIDMetadataEntity
   - VehicleProfileEntity

2. ✅ Crear DAOs en `data/local/dao/`:
   - UniversalScanDao (unificar operaciones)

3. ✅ Actualizar AppDatabase:
   - Agregar nuevas tablas
   - Crear migrations

4. ✅ Implementar repositorios:
   - UniversalScanRepositoryImpl
   - PIDMetadataRepositoryImpl
   - VehicleProfileRepositoryImpl

5. ✅ Actualizar Hilt modules

**Entregables:** Data layer con tests de integración

---

### Sprint 3: UI Layer - Parte 1 (Semana 3)
**Objetivo:** Interfaz básica de scanning

1. ✅ Crear ViewModel:
   - UniversalPIDScannerViewModel
   - Estados y flows

2. ✅ Crear Fragment:
   - UniversalPIDScannerFragment
   - Tab layout básico

3. ✅ Diseñar layouts:
   - fragment_universal_pid_scanner.xml
   - tab_quick_scan.xml
   - tab_advanced_config.xml

4. ✅ Implementar adaptadores:
   - UniversalScanResultAdapter
   - Mejorar item layouts

**Entregables:** UI básica funcional con Quick Scan

---

### Sprint 4: UI Layer - Parte 2 (Semana 4)
**Objetivo:** Funcionalidades avanzadas

1. ✅ Implementar tabs restantes:
   - Results tab con filtros
   - Export tab con opciones

2. ✅ Crear dialogs:
   - UniversalScanConfigDialog
   - ExportOptionsDialog
   - ResultDetailsDialog

3. ✅ Implementar exportación:
   - JSON export
   - CSV export
   - QR code generation

4. ✅ Historial de scans

**Entregables:** UI completa con todas las funcionalidades

---

### Sprint 5: Integración & Testing (Semana 5)
**Objetivo:** Integrar con sistema existente y testing

1. ✅ Integración:
   - Conectar con BluetoothService
   - Actualizar SupportedPIDsRepository
   - Agregar navegación desde dashboard

2. ✅ Testing:
   - Unit tests para Use Cases
   - UI tests para flujos principales
   - Integration tests end-to-end

3. ✅ Optimización:
   - Performance profiling
   - Reducir timeouts
   - Cache de resultados

4. ✅ Documentación:
   - Javadoc/KDoc completo
   - User guide
   - Technical docs

**Entregables:** Sistema completo, testeado y documentado

---

### Sprint 6: Polish & Launch (Semana 6)
**Objetivo:** Pulir y lanzar

1. ✅ UX improvements:
   - Animaciones
   - Feedback visual
   - Error handling mejorado

2. ✅ Accessibility:
   - Content descriptions
   - TalkBack support
   - Keyboard navigation

3. ✅ Analytics:
   - Track scan success rate
   - Popular PIDs discovered
   - Performance metrics

4. ✅ Release:
   - Build signed APK
   - Update Play Store listing
   - Create release notes

**Entregables:** Versión 1.0 en producción

---

## 🎯 Métricas de Éxito

### Funcionales
- ✅ Descubre 80%+ de PIDs soportados por el vehículo
- ✅ Funciona con todos los protocolos (ISO, KWP, CAN, J1850)
- ✅ Quick Scan completa en < 2 minutos
- ✅ Full Scan completa en < 5 minutos
- ✅ 95%+ de accuracy en detección de tipos

### Performance
- ✅ Latencia promedio < 300ms por PID
- ✅ Uso de memoria < 50MB durante scan
- ✅ UI responsive (60 FPS) durante scan
- ✅ No crashes en 1000+ scans

### UX
- ✅ 90%+ de usuarios completan su primer scan
- ✅ Rating 4.5+ estrellas
- ✅ < 5% de scans cancelados
- ✅ Feature más usada de la app

---

## 📚 Documentación Técnica

### APIs y Protocolos

#### OBD-II Modes Soportados

**Mode 01 - Show current data**
```
Command: 01 [PID]
Response: 41 [PID] [Data bytes...]
Range: 0x00 - 0xFF (256 PIDs)
```

**Mode 02 - Show freeze frame data**
```
Command: 02 [PID] [Frame]
Response: 42 [PID] [Data bytes...]
Range: 0x00 - 0xFF (256 PIDs)
```

**Mode 09 - Request vehicle information**
```
Command: 09 [PID]
Response: 49 [PID] [Data bytes...]
Common PIDs:
- 0x02: VIN
- 0x04: Calibration ID
- 0x06: CVN
```

**Mode 22 - Read data by identifier (Manufacturer)**
```
Command: 22 [DID high] [DID low]
Response: 62 [DID high] [DID low] [Data...]
Range: 0x0000 - 0xFFFF
Example: 22 F1 90 → Read VIN (manufacturer specific)
```

### Detección de Tipos de Datos

#### Heurísticas para Auto-detección

```kotlin
fun detectType(bytes: ByteArray): PIDDataType {
    return when {
        // 1 byte unsigned (0-255)
        bytes.size == 1 ->
            PIDDataType.UNSIGNED_INT

        // 2 bytes, probablemente int16
        bytes.size == 2 -> {
            val value = (bytes[0].toInt() shl 8) or bytes[1].toInt()
            if (value > 32767) PIDDataType.SIGNED_INT
            else PIDDataType.UNSIGNED_INT
        }

        // Todos ASCII printable = String
        bytes.all { it in 0x20..0x7E } ->
            PIDDataType.STRING

        // Patrones de bits (muchos 0s y 1s)
        bytes.count { it == 0x00 || it == 0xFF } > bytes.size / 2 ->
            PIDDataType.BITMAP

        // Default: multi-byte
        else -> PIDDataType.MULTI_BYTE
    }
}
```

### Fórmulas de Conversión Comunes

```kotlin
object PIDFormulas {
    fun engineRPM(a: Byte, b: Byte): Float =
        ((a.toInt() * 256) + b.toInt()) / 4f

    fun vehicleSpeed(a: Byte): Int =
        a.toInt()

    fun coolantTemp(a: Byte): Int =
        a.toInt() - 40

    fun fuelLevel(a: Byte): Float =
        (a.toInt() * 100f) / 255f

    fun throttlePosition(a: Byte): Float =
        (a.toInt() * 100f) / 255f

    fun controlModuleVoltage(a: Byte, b: Byte): Float =
        ((a.toInt() * 256) + b.toInt()) / 1000f
}
```

---

## ⚠️ Consideraciones y Limitaciones

### Limitaciones Técnicas

1. **Tiempo de Scan**
   - Full scan (255 PIDs) puede tomar 5-10 minutos
   - Depende del timeout configurado
   - Vehículos lentos (ISO 9141-2) tardan más

2. **Compatibilidad de Adaptadores**
   - Adaptadores genéricos pueden tener bugs
   - Algunos no soportan Mode 22
   - Timeouts pueden variar

3. **Interpretación Automática**
   - Solo PIDs estándar tienen fórmulas conocidas
   - PIDs del fabricante requieren documentación
   - Algunos datos son contextuales

4. **Batería del Vehículo**
   - Scans largos consumen batería
   - Recomendar motor encendido para scans > 5 min

### Mejores Prácticas

1. **Antes del Scan**
   - ✅ Motor encendido (o contacto ON)
   - ✅ Adaptador bien conectado
   - ✅ Bluetooth estable
   - ✅ Batería del teléfono > 50%

2. **Durante el Scan**
   - ✅ No desconectar adaptador
   - ✅ Mantener app en primer plano
   - ✅ No hacer otras operaciones OBD

3. **Después del Scan**
   - ✅ Revisar PIDs descubiertos
   - ✅ Exportar resultados
   - ✅ Verificar interpretaciones
   - ✅ Reportar bugs si hay errores

---

## 🚀 Roadmap Futuro

### v1.1 - Enhanced Analysis
- ✅ Machine learning para detección de tipos
- ✅ Base de datos cloud de PIDs conocidos
- ✅ Comparar con otros vehículos del mismo modelo
- ✅ Sugerir fórmulas basadas en patterns

### v1.2 - Community Features
- ✅ Compartir descubrimientos con comunidad
- ✅ Importar PIDs de otros usuarios
- ✅ Ranking de vehículos mejor documentados
- ✅ Wiki colaborativa de PIDs

### v1.3 - Advanced Protocols
- ✅ Soporte para CAN extended (29-bit)
- ✅ Soporte para J1939 (camiones)
- ✅ Soporte para ISO-TP multi-frame
- ✅ Custom protocol definitions

### v2.0 - Professional Tools
- ✅ Monitoring en tiempo real de PIDs custom
- ✅ Grabación de sesiones
- ✅ Replay de datos grabados
- ✅ Alertas basadas en valores
- ✅ Dashboard personalizable

---

## 📄 Licencia y Créditos

### Open Source
Este proyecto utiliza:
- Material Design 3
- Hilt (Dagger)
- Room Database
- Kotlin Coroutines & Flow
- ZXing (QR codes)

### Contribuciones
¿Quieres contribuir al proyecto?
- Report bugs en GitHub Issues
- Submit PIDs descubiertos
- Mejora documentación
- Comparte con la comunidad

---

## 📞 Soporte

### Ayuda y Documentación
- 📖 [User Guide](docs/user-guide.md)
- 🔧 [Technical Docs](docs/technical.md)
- 🐛 [Bug Reports](github.com/fleetcare/issues)
- 💬 [Community Forum](forum.fleetcare.com)

---

**Documento creado:** 02 Noviembre 2025
**Versión:** 1.0
**Autor:** FleetCare OBD Team
**Estado:** Ready for Implementation ✅
