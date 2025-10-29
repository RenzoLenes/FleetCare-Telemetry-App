# FleetCare OBD - Sistema de Telemetría Vehicular

## 1. Descripción del Producto

FleetCare OBD es una aplicación móvil Android profesional que permite monitorear en tiempo real los parámetros de diagnóstico de vehículos mediante dispositivos OBDII conectados por Bluetooth clásico. La aplicación captura datos del ECU (Electronic Control Unit) del vehículo y los transmite a Firebase Realtime Database para análisis, historial y visualización remota.

### Propósito
Proporcionar a conductores, técnicos automotrices y administradores de flotas una herramienta robusta para:
- Monitorear parámetros del motor en tiempo real (RPM, velocidad, temperatura)
- Detectar códigos de error del vehículo (DTCs)
- Mantener historial de telemetría en la nube
- Facilitar el mantenimiento preventivo
- Optimizar el rendimiento del vehículo

### Casos de Uso Principales
- **Conductores individuales**: Monitoreo personal del estado del vehículo
- **Talleres mecánicos**: Diagnóstico remoto y análisis de datos
- **Gestión de flotas**: Supervisión de múltiples vehículos en tiempo real
- **Desarrolladores IoT**: Plataforma base para soluciones vehiculares personalizadas

---

## 2. Arquitectura General

### Arquitectura de la Aplicación

```
┌─────────────────────────────────────────────────────────────┐
│                     PRESENTATION LAYER                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Activities  │  │  Fragments   │  │  ViewModels  │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      DOMAIN LAYER                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Use Cases   │  │  Repositories│  │   Entities   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                       DATA LAYER                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Bluetooth   │  │   Firebase   │  │ Local Cache  │      │
│  │  DataSource  │  │  DataSource  │  │  (Room/Prefs)│      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### Flujo de Datos

```
OBDII Device (Bluetooth)
    │
    ▼ (RFCOMM Socket)
BluetoothService
    │
    ▼ (Raw ELM327 Commands)
OBDCommandParser
    │
    ▼ (Parsed Vehicle Data)
VehicleRepository
    │
    ├──▶ Firebase Realtime Database (Cloud Storage)
    │
    └──▶ ViewModel (LiveData/Flow)
         │
         ▼
    UI Components (Real-time Display)
```

### Patrones de Diseño Implementados

- **MVVM (Model-View-ViewModel)**: Separación de lógica de UI y datos
- **Clean Architecture**: Capas independientes con inversión de dependencias
- **Repository Pattern**: Abstracción de fuentes de datos
- **Observer Pattern**: Reactividad con Flow/LiveData
- **Singleton Pattern**: Gestión de conexiones Bluetooth y Firebase
- **Factory Pattern**: Creación de comandos OBDII
- **State Pattern**: Gestión de estados de conexión

---

## 3. Tecnologías a Usar

### Stack Tecnológico Principal

#### Lenguaje y Plataforma
- **Kotlin**: 1.9.x
- **Android SDK**: Min 21 (Lollipop) - Target 34 (Android 14)
- **Gradle**: 8.x con Kotlin DSL

#### Arquitectura y Concurrencia
- **Kotlin Coroutines**: Operaciones asíncronas
- **Kotlin Flow**: Streams reactivos
- **LiveData**: Observación de datos en UI
- **ViewModel**: Gestión de estado de UI
- **Lifecycle Components**: Manejo del ciclo de vida

#### Comunicación Bluetooth
- **Android Bluetooth Classic API**: BluetoothAdapter, BluetoothSocket
- **RFCOMM Protocol**: UUID estándar SPP (Serial Port Profile)
- **ELM327 Protocol**: Comandos AT y modos OBDII

#### Backend y Almacenamiento
- **Firebase Realtime Database**: Almacenamiento en tiempo real
- **Firebase Authentication**: Autenticación anónima inicial
- **Firebase Crashlytics**: Monitoreo de errores (Fase 2)
- **Firebase Analytics**: Métricas de uso (Fase 2)

#### Inyección de Dependencias
- **Hilt/Dagger**: DI framework (Sprint 1)

#### Persistencia Local
- **Room Database**: Caché local de telemetría (Sprint 3)
- **SharedPreferences/DataStore**: Configuraciones

#### Testing
- **JUnit 5**: Tests unitarios
- **MockK**: Mocking para Kotlin
- **Espresso**: Tests de UI
- **Turbine**: Testing de Flows

#### Herramientas de Desarrollo
- **Android Studio**: Hedgehog o superior
- **Git**: Control de versiones
- **GitHub Actions**: CI/CD (Fase 2)
- **Detekt**: Análisis estático de código
- **ktlint**: Formateo de código

---

## 4. Requisitos Funcionales y No Funcionales

### Requisitos Funcionales

#### RF-001: Gestión de Bluetooth
- El sistema debe escanear dispositivos Bluetooth disponibles
- El sistema debe emparejar automáticamente con dispositivos OBDII
- El sistema debe solicitar PIN (1234, 0000) si es necesario
- El sistema debe establecer conexión RFCOMM
- El sistema debe reconectar automáticamente en caso de desconexión

#### RF-002: Comunicación OBDII
- El sistema debe inicializar el adaptador ELM327
- El sistema debe enviar comandos PID estándar (Modo 01)
- El sistema debe parsear respuestas hexadecimales del ECU
- El sistema debe manejar errores de comunicación
- El sistema debe soportar al menos 10 PIDs básicos:
  - 010C: RPM del motor
  - 010D: Velocidad del vehículo
  - 0105: Temperatura del refrigerante
  - 010F: Temperatura del aire de admisión
  - 0111: Posición del acelerador
  - 0104: Carga del motor
  - 0142: Voltaje del sistema
  - 015C: Temperatura del aceite
  - 0146: Temperatura ambiente
  - 0149: Posición del pedal del acelerador

#### RF-003: Almacenamiento en Firebase
- El sistema debe autenticar usuarios de forma anónima
- El sistema debe crear identificadores únicos por vehículo
- El sistema debe enviar datos cada 2 segundos a Firebase
- El sistema debe estructurar datos con timestamp
- El sistema debe mantener historial de telemetría

#### RF-004: Interfaz de Usuario
- El sistema debe mostrar datos en tiempo real (velocímetro, tacómetro)
- El sistema debe visualizar estado de conexión
- El sistema debe listar dispositivos Bluetooth disponibles
- El sistema debe mostrar gráficos históricos (Fase 2)
- El sistema debe permitir exportar datos (Fase 3)

#### RF-005: Diagnóstico de Errores
- El sistema debe leer códigos DTC (Diagnostic Trouble Codes)
- El sistema debe mostrar descripción de códigos de error
- El sistema debe permitir borrar códigos de error
- El sistema debe registrar historial de errores

### Requisitos No Funcionales

#### RNF-001: Rendimiento
- Latencia de lectura OBDII: < 500ms por comando
- Frecuencia de actualización UI: 2 actualizaciones/segundo
- Tiempo de conexión Bluetooth: < 5 segundos
- Consumo de batería: Modo optimizado < 15%/hora

#### RNF-002: Confiabilidad
- Disponibilidad de la aplicación: 99.5%
- Tasa de reconexión automática: > 95%
- Manejo de errores sin crashes
- Persistencia de datos en caso de pérdida de conexión

#### RNF-003: Seguridad
- Comunicación Firebase con reglas de seguridad
- Datos de usuario anónimos
- Sin almacenamiento de datos sensibles en local
- Encriptación de datos en tránsito (HTTPS Firebase)

#### RNF-004: Usabilidad
- Interfaz intuitiva con Material Design 3
- Tiempo de aprendizaje: < 5 minutos
- Soporte de idiomas: Español e Inglés
- Accesibilidad: Soporte TalkBack

#### RNF-005: Mantenibilidad
- Código documentado con KDoc
- Cobertura de tests: > 70%
- Arquitectura modular
- Cumplimiento de principios SOLID

#### RNF-006: Compatibilidad
- Android 5.0+ (API 21+)
- Soporte para dispositivos con/sin Bluetooth Low Energy
- Compatibilidad con adaptadores ELM327 v1.5+
- Resoluciones desde 5" a 12" (tablets)

---

## 5. Estructura de Carpetas

```
FleetCareOBD/
│
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/fleetcare/obd/
│   │   │   │   │
│   │   │   │   ├── bluetooth/
│   │   │   │   │   ├── BluetoothManager.kt
│   │   │   │   │   ├── BluetoothService.kt
│   │   │   │   │   ├── RFCOMMConnector.kt
│   │   │   │   │   └── BluetoothStateReceiver.kt
│   │   │   │   │
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/
│   │   │   │   │   │   ├── dao/
│   │   │   │   │   │   │   └── VehicleDataDao.kt
│   │   │   │   │   │   ├── database/
│   │   │   │   │   │   │   └── AppDatabase.kt
│   │   │   │   │   │   └── preferences/
│   │   │   │   │   │       └── PreferencesManager.kt
│   │   │   │   │   │
│   │   │   │   │   ├── remote/
│   │   │   │   │   │   ├── firebase/
│   │   │   │   │   │   │   ├── FirebaseDataSource.kt
│   │   │   │   │   │   │   └── FirebaseAuthManager.kt
│   │   │   │   │   │   └── models/
│   │   │   │   │   │       ├── VehicleDataDto.kt
│   │   │   │   │   │       └── ErrorCodeDto.kt
│   │   │   │   │   │
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── VehicleRepositoryImpl.kt
│   │   │   │   │   │   ├── BluetoothRepositoryImpl.kt
│   │   │   │   │   │   └── DiagnosticRepositoryImpl.kt
│   │   │   │   │   │
│   │   │   │   │   └── mapper/
│   │   │   │   │       ├── VehicleDataMapper.kt
│   │   │   │   │       └── ErrorCodeMapper.kt
│   │   │   │   │
│   │   │   │   ├── domain/
│   │   │   │   │   ├── model/
│   │   │   │   │   │   ├── VehicleData.kt
│   │   │   │   │   │   ├── BluetoothDevice.kt
│   │   │   │   │   │   ├── ConnectionState.kt
│   │   │   │   │   │   └── ErrorCode.kt
│   │   │   │   │   │
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── VehicleRepository.kt
│   │   │   │   │   │   ├── BluetoothRepository.kt
│   │   │   │   │   │   └── DiagnosticRepository.kt
│   │   │   │   │   │
│   │   │   │   │   └── usecase/
│   │   │   │   │       ├── ConnectToDeviceUseCase.kt
│   │   │   │   │       ├── ReadVehicleDataUseCase.kt
│   │   │   │   │       ├── SendDataToFirebaseUseCase.kt
│   │   │   │   │       ├── ScanBluetoothDevicesUseCase.kt
│   │   │   │   │       └── ReadErrorCodesUseCase.kt
│   │   │   │   │
│   │   │   │   ├── ui/
│   │   │   │   │   ├── main/
│   │   │   │   │   │   ├── MainActivity.kt
│   │   │   │   │   │   └── MainViewModel.kt
│   │   │   │   │   │
│   │   │   │   │   ├── dashboard/
│   │   │   │   │   │   ├── DashboardFragment.kt
│   │   │   │   │   │   ├── DashboardViewModel.kt
│   │   │   │   │   │   └── adapter/
│   │   │   │   │   │       └── MetricsAdapter.kt
│   │   │   │   │   │
│   │   │   │   │   ├── connection/
│   │   │   │   │   │   ├── ConnectionFragment.kt
│   │   │   │   │   │   ├── ConnectionViewModel.kt
│   │   │   │   │   │   └── adapter/
│   │   │   │   │   │       └── DeviceListAdapter.kt
│   │   │   │   │   │
│   │   │   │   │   ├── diagnostics/
│   │   │   │   │   │   ├── DiagnosticsFragment.kt
│   │   │   │   │   │   ├── DiagnosticsViewModel.kt
│   │   │   │   │   │   └── adapter/
│   │   │   │   │   │       └── ErrorCodeAdapter.kt
│   │   │   │   │   │
│   │   │   │   │   └── common/
│   │   │   │   │       ├── BaseFragment.kt
│   │   │   │   │       ├── LoadingDialog.kt
│   │   │   │   │       └── ViewExtensions.kt
│   │   │   │   │
│   │   │   │   ├── utils/
│   │   │   │   │   ├── obd/
│   │   │   │   │   │   ├── ELM327Commands.kt
│   │   │   │   │   │   ├── OBDCommandParser.kt
│   │   │   │   │   │   ├── PIDConstants.kt
│   │   │   │   │   │   └── OBDResponseParser.kt
│   │   │   │   │   │
│   │   │   │   │   ├── Constants.kt
│   │   │   │   │   ├── Extensions.kt
│   │   │   │   │   ├── Logger.kt
│   │   │   │   │   └── NetworkUtils.kt
│   │   │   │   │
│   │   │   │   └── di/
│   │   │   │       ├── AppModule.kt
│   │   │   │       ├── BluetoothModule.kt
│   │   │   │       ├── FirebaseModule.kt
│   │   │   │       ├── DatabaseModule.kt
│   │   │   │       └── RepositoryModule.kt
│   │   │   │
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   ├── drawable/
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   ├── colors.xml
│   │   │   │   │   ├── themes.xml
│   │   │   │   │   └── dimens.xml
│   │   │   │   └── navigation/
│   │   │   │       └── nav_graph.xml
│   │   │   │
│   │   │   ├── AndroidManifest.xml
│   │   │   └── google-services.json
│   │   │
│   │   ├── test/
│   │   │   └── java/com/fleetcare/obd/
│   │   │       ├── bluetooth/
│   │   │       │   └── BluetoothServiceTest.kt
│   │   │       ├── data/
│   │   │       │   └── VehicleRepositoryTest.kt
│   │   │       ├── domain/
│   │   │       │   └── ReadVehicleDataUseCaseTest.kt
│   │   │       └── utils/
│   │   │           └── OBDCommandParserTest.kt
│   │   │
│   │   └── androidTest/
│   │       └── java/com/fleetcare/obd/
│   │           └── ui/
│   │               └── DashboardFragmentTest.kt
│   │
│   ├── build.gradle.kts
│   └── proguard-rules.pro
│
├── buildSrc/
│   └── src/main/kotlin/
│       └── Dependencies.kt
│
├── gradle/
│   └── libs.versions.toml
│
├── .github/
│   └── workflows/
│       ├── ci.yml
│       └── release.yml
│
├── .gitignore
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── README.md
├── LICENSE
└── CONTRIBUTING.md
```

---

## 6. Historias de Usuario

### ÉPICA 1: Gestión de Conexión Bluetooth

#### HU-001: Escaneo de Dispositivos Bluetooth
**Como** usuario
**Quiero** ver una lista de dispositivos Bluetooth disponibles
**Para** seleccionar mi adaptador OBDII y conectarme a él

**Criterios de Aceptación:**
- Dado que el Bluetooth está activado
- Cuando abro la pantalla de conexión
- Entonces veo todos los dispositivos Bluetooth en rango
- Y puedo identificar dispositivos ya emparejados
- Y los dispositivos se actualizan automáticamente durante el escaneo

**Prioridad:** ALTA
**Sprint:** 2
**Estimación:** 5 puntos

---

#### HU-002: Emparejamiento con Dispositivo OBDII
**Como** usuario
**Quiero** emparejar mi dispositivo OBDII automáticamente
**Para** establecer una conexión segura sin configuración manual

**Criterios de Aceptación:**
- Dado que he seleccionado un dispositivo OBDII
- Cuando inicio el emparejamiento
- Entonces la app intenta PINs comunes (1234, 0000) automáticamente
- Y muestra el progreso del emparejamiento
- Y notifica si el emparejamiento es exitoso o falla

**Prioridad:** ALTA
**Sprint:** 2
**Estimación:** 8 puntos

---

#### HU-003: Conexión RFCOMM
**Como** usuario
**Quiero** conectarme al dispositivo OBDII mediante RFCOMM
**Para** comenzar a recibir datos del vehículo

**Criterios de Aceptación:**
- Dado que el dispositivo está emparejado
- Cuando inicio la conexión
- Entonces se establece un socket RFCOMM
- Y se inicializa el protocolo ELM327
- Y se muestra el estado "Conectado" en la UI

**Prioridad:** ALTA
**Sprint:** 2
**Estimación:** 13 puntos

---

#### HU-004: Reconexión Automática
**Como** usuario
**Quiero** que la app se reconecte automáticamente
**Para** no perder la conexión durante el viaje

**Criterios de Aceptación:**
- Dado que se perdió la conexión Bluetooth
- Cuando el dispositivo vuelve a estar disponible
- Entonces la app intenta reconectar automáticamente cada 5 segundos
- Y notifica al usuario del intento de reconexión
- Y restaura la lectura de datos al reconectar

**Prioridad:** MEDIA
**Sprint:** 3
**Estimación:** 8 puntos

---

### ÉPICA 2: Lectura de Datos OBDII

#### HU-005: Lectura de RPM del Motor
**Como** usuario
**Quiero** ver las revoluciones por minuto (RPM) en tiempo real
**Para** monitorear el rendimiento del motor

**Criterios de Aceptación:**
- Dado que estoy conectado al OBDII
- Cuando el motor está encendido
- Entonces veo las RPM actualizadas cada segundo
- Y el valor se muestra en formato numérico y gráfico
- Y el rango normal es de 0-8000 RPM

**Prioridad:** ALTA
**Sprint:** 3
**Estimación:** 5 puntos

---

#### HU-006: Lectura de Velocidad del Vehículo
**Como** usuario
**Quiero** ver la velocidad del vehículo en tiempo real
**Para** verificar mi velocidad actual

**Criterios de Aceptación:**
- Dado que estoy conectado al OBDII
- Cuando el vehículo está en movimiento
- Entonces veo la velocidad en km/h
- Y puedo alternar entre km/h y mph
- Y el velocímetro digital es visible y legible

**Prioridad:** ALTA
**Sprint:** 3
**Estimación:** 5 puntos

---

#### HU-007: Lectura de Temperatura del Motor
**Como** usuario
**Quiero** monitorear la temperatura del refrigerante
**Para** prevenir sobrecalentamiento del motor

**Criterios de Aceptación:**
- Dado que estoy conectado al OBDII
- Cuando el motor está encendido
- Entonces veo la temperatura en °C
- Y recibo alertas si supera 100°C
- Y puedo alternar entre °C y °F

**Prioridad:** ALTA
**Sprint:** 3
**Estimación:** 5 puntos

---

#### HU-008: Lectura de Múltiples Parámetros Simultáneos
**Como** usuario
**Quiero** ver varios parámetros del vehículo a la vez
**Para** tener una visión completa del estado del vehículo

**Criterios de Aceptación:**
- Dado que estoy conectado al OBDII
- Cuando estoy en el dashboard
- Entonces veo al menos 6 parámetros simultáneamente
- Y cada parámetro se actualiza de forma independiente
- Y puedo personalizar qué parámetros ver (Fase 2)

**Prioridad:** MEDIA
**Sprint:** 3
**Estimación:** 8 puntos

---

### ÉPICA 3: Integración con Firebase

#### HU-009: Autenticación Anónima
**Como** usuario
**Quiero** usar la app sin crear una cuenta
**Para** empezar a monitorear mi vehículo inmediatamente

**Criterios de Aceptación:**
- Dado que abro la app por primera vez
- Cuando la app inicia
- Entonces se crea una sesión anónima en Firebase
- Y obtengo un ID de usuario único
- Y puedo usar todas las funciones básicas

**Prioridad:** ALTA
**Sprint:** 1
**Estimación:** 5 puntos

---

#### HU-010: Envío de Datos a Firebase
**Como** usuario
**Quiero** que mis datos se guarden en la nube
**Para** acceder al historial desde cualquier lugar

**Criterios de Aceptación:**
- Dado que estoy leyendo datos del OBDII
- Cuando hay conexión a internet
- Entonces los datos se envían a Firebase cada 2 segundos
- Y se almacenan con timestamp y ID de vehículo
- Y se maneja la pérdida temporal de conexión

**Prioridad:** ALTA
**Sprint:** 4
**Estimación:** 8 puntos

---

#### HU-011: Visualización de Datos en Tiempo Real desde Firebase
**Como** usuario
**Quiero** ver datos sincronizados en tiempo real
**Para** monitorear mi vehículo desde la app

**Criterios de Aceptación:**
- Dado que hay datos en Firebase
- Cuando abro el dashboard
- Entonces veo los datos más recientes
- Y se actualizan automáticamente cuando cambian
- Y veo indicador de sincronización

**Prioridad:** MEDIA
**Sprint:** 4
**Estimación:** 5 puntos

---

#### HU-012: Estructura de Datos Vehículos
**Como** administrador del sistema
**Quiero** una estructura de datos organizada
**Para** escalar la aplicación a múltiples vehículos

**Criterios de Aceptación:**
- Dado que se están enviando datos a Firebase
- Cuando se crea un nuevo registro
- Entonces sigue la estructura: vehicles/{vehicleId}/sessions/{sessionId}/data
- Y cada registro tiene timestamp, userId, y parámetros del vehículo
- Y la estructura es eficiente para consultas

**Prioridad:** ALTA
**Sprint:** 4
**Estimación:** 8 puntos

---

### ÉPICA 4: Interfaz de Usuario y Experiencia

#### HU-013: Dashboard de Métricas en Tiempo Real
**Como** usuario
**Quiero** un dashboard visual y atractivo
**Para** ver toda la información de mi vehículo de un vistazo

**Criterios de Aceptación:**
- Dado que estoy conectado al OBDII
- Cuando abro el dashboard
- Entonces veo tarjetas con métricas principales
- Y uso Material Design 3
- Y la interfaz es responsiva y fluida

**Prioridad:** ALTA
**Sprint:** 3
**Estimación:** 13 puntos

---

#### HU-014: Indicadores de Estado de Conexión
**Como** usuario
**Quiero** ver el estado de mis conexiones
**Para** saber si estoy conectado a Bluetooth y Firebase

**Criterios de Aceptación:**
- Dado que uso la aplicación
- Cuando hay cambios en las conexiones
- Entonces veo iconos de estado en la toolbar
- Y puedo tocar los iconos para ver detalles
- Y recibo notificaciones de cambios importantes

**Prioridad:** MEDIA
**Sprint:** 2
**Estimación:** 5 puntos

---

#### HU-015: Pantalla de Selección de Dispositivos
**Como** usuario
**Quiero** una pantalla clara para seleccionar mi dispositivo OBDII
**Para** conectarme fácilmente

**Criterios de Aceptación:**
- Dado que no estoy conectado
- Cuando abro la pantalla de conexión
- Entonces veo lista de dispositivos con nombres e iconos
- Y puedo distinguir dispositivos emparejados
- Y puedo iniciar escaneo manualmente

**Prioridad:** ALTA
**Sprint:** 2
**Estimación:** 8 puntos

---

### ÉPICA 5: Diagnóstico y Códigos de Error

#### HU-016: Lectura de Códigos DTC
**Como** usuario
**Quiero** leer los códigos de error del vehículo
**Para** saber qué problemas tiene mi vehículo

**Criterios de Aceptación:**
- Dado que estoy conectado al OBDII
- Cuando accedo a la pantalla de diagnóstico
- Entonces veo lista de códigos DTC activos
- Y cada código muestra descripción en español
- Y veo la cantidad total de códigos

**Prioridad:** MEDIA
**Sprint:** 4
**Estimación:** 8 puntos

---

#### HU-017: Borrar Códigos de Error
**Como** usuario
**Quiero** borrar códigos de error después de repararlos
**Para** resetear la luz de check engine

**Criterios de Aceptación:**
- Dado que hay códigos DTC activos
- Cuando presiono "Borrar códigos"
- Entonces se solicita confirmación
- Y se envía comando de borrado al ECU
- Y se verifica que los códigos fueron eliminados

**Prioridad:** BAJA
**Sprint:** 4
**Estimación:** 5 puntos

---

### ÉPICA 6: Configuración y Personalización

#### HU-018: Configuración de Unidades de Medida
**Como** usuario
**Quiero** elegir mis unidades preferidas
**Para** ver datos en el sistema que prefiero

**Criterios de Aceptación:**
- Dado que estoy en configuración
- Cuando cambio unidades (km/h vs mph, °C vs °F)
- Entonces todos los valores se convierten automáticamente
- Y la preferencia se guarda localmente
- Y se mantiene entre sesiones

**Prioridad:** BAJA
**Sprint:** 3
**Estimación:** 3 puntos

---

#### HU-019: Configuración de Frecuencia de Actualización
**Como** usuario avanzado
**Quiero** ajustar la frecuencia de lectura de datos
**Para** balancear rendimiento y consumo de batería

**Criterios de Aceptación:**
- Dado que estoy en configuración avanzada
- Cuando ajusto el intervalo (1-5 segundos)
- Entonces la app lee datos a esa frecuencia
- Y se muestra advertencia si es < 1 segundo
- Y puedo restaurar valores predeterminados

**Prioridad:** BAJA
**Sprint:** 4
**Estimación:** 5 puntos

---

## 7. Definición de SPRINTS

### SPRINT 1: Fundamentos y Configuración (Duración: 1 semana)

#### Objetivo
Establecer la base del proyecto con arquitectura MVVM, integración de Firebase, y estructura modular lista para desarrollo.

#### Entregables
1. Proyecto Android configurado con Kotlin
2. Gradle configurado con dependencias necesarias
3. Firebase integrado (Authentication + Realtime Database)
4. Estructura de paquetes según Clean Architecture
5. Inyección de dependencias con Hilt
6. Clases base (BaseViewModel, BaseFragment)
7. Autenticación anónima funcional
8. Activity principal con Navigation Component
9. Permisos de Bluetooth en Manifest
10. Sistema de logging centralizado

#### Historias de Usuario
- HU-009: Autenticación Anónima

#### Criterios de Salida
- ✅ La app compila sin errores
- ✅ Firebase está conectado y funcionando
- ✅ Autenticación anónima genera userId
- ✅ Navegación básica implementada
- ✅ Todos los módulos DI configurados
- ✅ Permisos Bluetooth declarados

#### Tecnologías Aplicadas
- Kotlin 1.9.x
- Gradle Kotlin DSL
- Hilt/Dagger
- Firebase SDK
- Jetpack Navigation
- Coroutines + Flow

---

### SPRINT 2: Conexión Bluetooth y OBDII (Duración: 2 semanas)

#### Objetivo
Implementar toda la lógica de Bluetooth clásico: escaneo, emparejamiento, conexión RFCOMM y comunicación básica con protocolo ELM327.

#### Entregables
1. BluetoothManager para gestión de adaptador
2. BluetoothService con lifecycle awareness
3. Scanner de dispositivos Bluetooth
4. Lógica de emparejamiento automático con PIN
5. Conexión RFCOMM con socket persistente
6. Inicialización ELM327 (comandos AT)
7. Envío y recepción de comandos básicos
8. UI para listar dispositivos disponibles
9. Indicadores de estado de conexión
10. Sistema de reconexión automática
11. Tests unitarios para Bluetooth layer

#### Historias de Usuario
- HU-001: Escaneo de Dispositivos Bluetooth
- HU-002: Emparejamiento con Dispositivo OBDII
- HU-003: Conexión RFCOMM
- HU-014: Indicadores de Estado de Conexión
- HU-015: Pantalla de Selección de Dispositivos

#### Criterios de Salida
- ✅ Escaneo lista dispositivos correctamente
- ✅ Emparejamiento automático funciona con PIDs comunes
- ✅ Socket RFCOMM se establece correctamente
- ✅ Se pueden enviar comandos AT al ELM327
- ✅ UI muestra estado de conexión en tiempo real
- ✅ Reconexión automática funciona tras desconexión
- ✅ Cobertura de tests > 60%

#### Tecnologías Aplicadas
- Android Bluetooth Classic API
- RFCOMM Protocol
- ELM327 Protocol
- Kotlin Coroutines
- StateFlow
- MockK para testing

---

### SPRINT 3: Lectura y Parsing de Datos OBDII (Duración: 2 semanas)

#### Objetivo
Implementar lectura, parsing y visualización de parámetros del vehículo desde el ECU utilizando comandos PID estándar.

#### Entregables
1. OBDCommandParser para interpretar respuestas
2. PIDConstants con definición de todos los PIDs
3. Comandos para al menos 10 parámetros básicos
4. VehicleRepository con caché local (Room)
5. UseCases para leer datos específicos
6. ViewModel para Dashboard con LiveData/Flow
7. Dashboard UI con Material Design 3
8. Componentes visuales (velocímetro, tacómetro)
9. Sistema de actualización en tiempo real (2 seg)
10. Configuración de unidades de medida
11. Persistencia local de últimas lecturas
12. Tests de parsing y repository

#### Historias de Usuario
- HU-004: Reconexión Automática
- HU-005: Lectura de RPM del Motor
- HU-006: Lectura de Velocidad del Vehículo
- HU-007: Lectura de Temperatura del Motor
- HU-008: Lectura de Múltiples Parámetros Simultáneos
- HU-013: Dashboard de Métricas en Tiempo Real
- HU-018: Configuración de Unidades de Medida

#### Criterios de Salida
- ✅ Se leen correctamente 10 parámetros diferentes
- ✅ Parsing convierte hexadecimal a valores reales
- ✅ Dashboard muestra datos en tiempo real
- ✅ UI es fluida sin lag
- ✅ Cambio de unidades funciona correctamente
- ✅ Datos se persisten localmente
- ✅ Cobertura de tests > 70%

#### Tecnologías Aplicadas
- Room Database
- Kotlin Flow
- LiveData
- Material Design 3
- Canvas para gráficos personalizados
- DataStore para preferencias

---

### SPRINT 4: Integración Firebase y Diagnóstico (Duración: 2 semanas)

#### Objetivo
Sincronizar datos con Firebase Realtime Database, implementar estructura de datos escalable y añadir funcionalidad de lectura de códigos DTC.

#### Entregables
1. FirebaseDataSource con operaciones CRUD
2. Estructura de datos: vehicles/{vehicleId}/sessions/{sessionId}
3. SendDataToFirebaseUseCase con estrategia de envío
4. Sincronización bidireccional
5. Manejo de conexión offline
6. Lectura de códigos DTC (Modo 03)
7. Base de datos de descripciones de DTCs
8. UI para mostrar códigos de error
9. Funcionalidad de borrar códigos (Modo 04)
10. Configuración de frecuencia de actualización
11. Tests de integración con Firebase

#### Historias de Usuario
- HU-010: Envío de Datos a Firebase
- HU-011: Visualización de Datos en Tiempo Real desde Firebase
- HU-012: Estructura de Datos Vehículos
- HU-016: Lectura de Códigos DTC
- HU-017: Borrar Códigos de Error
- HU-019: Configuración de Frecuencia de Actualización

#### Criterios de Salida
- ✅ Datos se envían correctamente a Firebase
- ✅ Estructura de datos es escalable
- ✅ Sincronización funciona en tiempo real
- ✅ Modo offline mantiene datos localmente
- ✅ Códigos DTC se leen y muestran correctamente
- ✅ Borrado de códigos funciona
- ✅ Tests de integración pasan

#### Tecnologías Aplicadas
- Firebase Realtime Database
- Firebase Rules
- Kotlin Coroutines
- WorkManager (para sincronización offline)
- JUnit + Espresso

---

### SPRINT 5: Refinamiento y Calidad (Duración: 1 semana) - FASE 2

#### Objetivo
Pulir la aplicación, mejorar UX, optimizar rendimiento y aumentar cobertura de tests.

#### Entregables
1. Optimización de consumo de batería
2. Manejo avanzado de errores
3. Snackbars y diálogos informativos
4. Animaciones y transiciones suaves
5. Dark mode
6. Internacionalización (ES/EN)
7. Accesibilidad (TalkBack)
8. Documentación KDoc completa
9. Tests E2E con Espresso
10. Configuración de CI/CD (GitHub Actions)

#### Criterios de Salida
- ✅ Consumo de batería < 15%/hora
- ✅ No hay memory leaks
- ✅ Tiempo de respuesta < 500ms
- ✅ Cobertura de tests > 80%
- ✅ CI/CD funcionando
- ✅ Documentación completa

---

### SPRINT 6: Características Avanzadas (Duración: 2 semanas) - FASE 3

#### Objetivo
Añadir funcionalidades premium: gráficos históricos, exportación de datos, notificaciones y análisis predictivo.

#### Entregables
1. Gráficos históricos con MPAndroidChart
2. Exportación de datos (CSV/PDF)
3. Sistema de notificaciones inteligentes
4. Alertas configurables por parámetro
5. Análisis de tendencias
6. Comparación de sesiones
7. Login con email/password (opcional)
8. Compartir datos con otros usuarios

#### Criterios de Salida
- ✅ Gráficos se renderizan correctamente
- ✅ Exportación genera archivos válidos
- ✅ Notificaciones llegan en tiempo real
- ✅ Sistema de análisis entrega insights útiles

---

## 8. Plan de Integración Continua y Próximas Fases

### Estrategia de CI/CD

#### GitHub Actions Workflow

```yaml
Pipeline Stages:
1. Build (on push/PR)
   - Compilación con Gradle
   - Verificación de sintaxis Kotlin
   - Lint con Detekt

2. Test (on push/PR)
   - Unit tests (JUnit)
   - Coverage report (JaCoCo)
   - Validación > 70% coverage

3. Security (on push)
   - Dependency check (OWASP)
   - Secret scanning
   - Code scanning (CodeQL)

4. Release (on tag)
   - Build signed APK
   - Generate changelog
   - Upload to GitHub Releases
   - Notificación a Slack/Discord
```

#### Ramas y Estrategia de Versiones

```
main (producción)
  └── develop (integración)
       ├── feature/bluetooth-connection
       ├── feature/firebase-sync
       ├── feature/dashboard-ui
       └── bugfix/reconnection-issue

Versionado: Semantic Versioning (v1.0.0)
- MAJOR: Cambios incompatibles
- MINOR: Nueva funcionalidad compatible
- PATCH: Corrección de bugs
```

#### Code Review y Quality Gates

- **Pre-commit hooks**: ktlint, detekt
- **Pull Request requirements**:
  - Al menos 1 aprobación
  - Tests pasan
  - Coverage no disminuye
  - Sin conflictos
- **Branch protection**: main y develop protegidas

---

### Próximas Fases y Roadmap

#### FASE 2: Optimización y Análisis (Q2 2024)

**Objetivos:**
- Mejorar rendimiento y eficiencia
- Añadir analytics y crashlytics
- Implementar dark mode
- Soporte multi-idioma completo

**Entregables:**
- Firebase Crashlytics integrado
- Firebase Analytics con eventos personalizados
- Performance Monitoring
- Optimización de consultas Firebase
- Documentación técnica completa

---

#### FASE 3: Funcionalidades Premium (Q3 2024)

**Objetivos:**
- Gráficos históricos interactivos
- Exportación y compartir datos
- Sistema de alertas inteligente
- Machine Learning para predicciones

**Entregables:**
- Visualización de tendencias
- Exportación CSV/PDF
- Notificaciones push personalizadas
- ML Kit para detectar anomalías
- Sistema de scoring de conducción

---

#### FASE 4: Gestión de Flotas (Q4 2024)

**Objetivos:**
- Soporte multi-vehículo
- Dashboard web para administradores
- API REST para integraciones
- Sistema de reportes automáticos

**Entregables:**
- Panel web (React/Angular)
- Backend con Cloud Functions
- API RESTful documentada
- Roles y permisos (admin/conductor)
- Reportes mensuales automáticos

---

#### FASE 5: Expansión y Monetización (2025)

**Objetivos:**
- Modelo de suscripción (freemium)
- Marketplace de plugins
- Integración con aseguradoras
- Soporte para flotas comerciales

**Entregables:**
- Sistema de suscripciones (Stripe/RevenueCat)
- SDK para desarrolladores
- API para aseguradoras
- Features enterprise
- Certificaciones de seguridad

---

### Métricas de Éxito

#### Indicadores Clave de Rendimiento (KPIs)

**Técnicos:**
- Tiempo de conexión Bluetooth: < 5 seg
- Latencia de lectura OBDII: < 500ms
- Tasa de reconexión exitosa: > 95%
- Crash-free rate: > 99.5%
- Consumo de batería: < 15%/hora

**Negocio:**
- Usuarios activos mensuales (MAU)
- Retención a 30 días: > 40%
- Rating en Play Store: > 4.3/5
- Tiempo promedio de sesión: > 15 min
- Conversión freemium a premium: > 5%

**Calidad:**
- Cobertura de tests: > 80%
- Deuda técnica: < 10% del código
- Tiempo de resolución de bugs: < 48h críticos
- Cumplimiento de SLA: 99.5%

---

### Estrategia de Releases

#### Release Cadence

- **Sprints**: Cada 2 semanas (releases internas)
- **Beta**: Cada 4 semanas (Google Play Beta Track)
- **Production**: Cada 6-8 semanas (Release estable)
- **Hotfixes**: Según sea necesario (< 24h)

#### Canales de Distribución

1. **Internal**: Testing interno del equipo
2. **Alpha**: Usuarios early adopters (< 50 usuarios)
3. **Beta**: Beta testers públicos (< 500 usuarios)
4. **Production**: Lanzamiento general Play Store

---

### Stack Tecnológico Futuro

#### Consideraciones para Escalabilidad

- **Backend**: Migrar a Cloud Functions + Firestore
- **Analytics**: Mixpanel o Amplitude
- **Monitoring**: Sentry + New Relic
- **Testing**: Añadir tests de performance (Maestro)
- **Logs**: Centralizados en Datadog
- **Feature Flags**: Firebase Remote Config

---

## 9. Configuración del Entorno de Desarrollo

### Requisitos del Sistema

- **Android Studio**: Hedgehog (2023.1.1) o superior
- **JDK**: 17 o superior
- **Gradle**: 8.2+
- **Kotlin**: 1.9.20+
- **Min SDK**: 21 (Android 5.0)
- **Target SDK**: 34 (Android 14)

### Configuración Inicial

```bash
# Clonar repositorio
git clone https://github.com/fleetcare/obd-android.git
cd obd-android

# Configurar Firebase
# 1. Descargar google-services.json desde Firebase Console
# 2. Colocar en app/google-services.json

# Configurar variables de entorno (opcional)
echo "FIREBASE_PROJECT_ID=your-project-id" > local.properties

# Build
./gradlew clean build

# Run tests
./gradlew test

# Install en dispositivo
./gradlew installDebug
```

---

## 10. Contribución y Soporte

### Cómo Contribuir

1. Fork del proyecto
2. Crear branch desde `develop`: `git checkout -b feature/nueva-funcionalidad`
3. Commit con mensajes descriptivos
4. Push al branch: `git push origin feature/nueva-funcionalidad`
5. Abrir Pull Request a `develop`

### Código de Conducta

Ver [CONTRIBUTING.md](CONTRIBUTING.md)

### Licencia

Este proyecto está bajo licencia MIT. Ver [LICENSE](LICENSE).

---

## 11. Contacto y Recursos

### Documentación Adicional

- [Wiki del Proyecto](https://github.com/fleetcare/obd-android/wiki)
- [API Documentation](https://fleetcare.github.io/obd-android/api/)
- [Protocolo ELM327](https://www.elmelectronics.com/wp-content/uploads/2016/07/ELM327DS.pdf)
- [OBDII PIDs](https://en.wikipedia.org/wiki/OBD-II_PIDs)

### Soporte

- **Issues**: [GitHub Issues](https://github.com/fleetcare/obd-android/issues)
- **Discussions**: [GitHub Discussions](https://github.com/fleetcare/obd-android/discussions)
- **Email**: support@fleetcare.com

---

**Versión del README:** 1.0.0
**Última actualización:** Octubre 2024
**Autor:** FleetCare Development Team

---

## Apéndices

### Apéndice A: Glosario de Términos

- **OBDII**: On-Board Diagnostics II - Sistema de diagnóstico vehicular
- **ELM327**: Chip de interfaz OBDII más común
- **ECU**: Engine Control Unit - Unidad de control del motor
- **DTC**: Diagnostic Trouble Code - Código de error de diagnóstico
- **PID**: Parameter ID - Identificador de parámetro OBDII
- **RFCOMM**: Radio Frequency Communication - Protocolo Bluetooth
- **SPP**: Serial Port Profile - Perfil Bluetooth para puerto serial

### Apéndice B: Comandos ELM327 Básicos

```
AT Z       - Reset del dispositivo
AT E0      - Desactivar echo
AT L0      - Desactivar linefeeds
AT S0      - Desactivar espacios
AT H0      - Desactivar headers
AT SP 0    - Auto-detectar protocolo

010C       - RPM del motor
010D       - Velocidad del vehículo
0105       - Temperatura del refrigerante
0104       - Carga del motor
0111       - Posición del acelerador
```

### Apéndice C: Estructura de Base de Datos Firebase

```json
{
  "users": {
    "{userId}": {
      "createdAt": "timestamp",
      "isAnonymous": true,
      "vehicles": ["{vehicleId1}", "{vehicleId2}"]
    }
  },
  "vehicles": {
    "{vehicleId}": {
      "userId": "{userId}",
      "name": "Mi Vehículo",
      "make": "Toyota",
      "model": "Corolla",
      "year": 2020,
      "vin": "optional",
      "sessions": {
        "{sessionId}": {
          "startTime": "timestamp",
          "endTime": "timestamp",
          "data": {
            "{timestamp}": {
              "rpm": 2500,
              "speed": 60,
              "coolantTemp": 85,
              "throttlePosition": 25,
              "engineLoad": 45,
              "fuelLevel": 75
            }
          },
          "summary": {
            "duration": 3600,
            "distance": 45.5,
            "avgSpeed": 55,
            "maxSpeed": 100,
            "avgRPM": 2200
          }
        }
      },
      "diagnostics": {
        "dtcCodes": {
          "{timestamp}": {
            "code": "P0301",
            "description": "Cylinder 1 Misfire Detected",
            "isActive": true
          }
        }
      }
    }
  }
}
```

---

**¡Gracias por usar FleetCare OBD!** 🚗📊
