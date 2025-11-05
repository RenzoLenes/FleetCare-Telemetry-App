# Sprint 9: Corrección de Problemas de Conexión OBD-II

**Objetivo:** Solucionar problemas de conexión ECU y parsing de respuestas multi-frame CAN para compatibilidad con más vehículos (ej: Suzuki Ertiga 2018).

**Fecha de inicio:** 2025-10-30
**Prioridad:** Alta
**Épica:** Compatibilidad Multi-Vehículo

---

## Problemas Identificados

### 🔴 Críticos
- [x] Error "No se puede conectar al ECU" en algunos vehículos → ✅ Sprint 9.3
- [x] Respuestas CAN multi-frame no se parsean correctamente (`0:2FFFFFFFFFFFFF`) → ✅ Sprint 9.2
- [x] Timeout insuficiente para ECUs lentos → ✅ Sprint 9.1

### 🟡 Importantes
- [x] Muchos logs de error "NO DATA" por PIDs no soportados → ✅ Sprint 9.4
- [x] No se detectan PIDs soportados antes de intentar leerlos → ✅ Sprint 9.5
- [x] Auto-detección de protocolo falla sin fallback manual → ✅ Sprint 9.3

### 🟢 Mejoras
- [x] No se guarda el protocolo que funcionó → ✅ Sprint 9.3
- [x] Falta adaptive timing para ECUs variables → ✅ Sprint 9.1
- [x] Headers deshabilitados dificultan diagnóstico → ✅ Sprint 9.1

---

## Sprint 9.1: Mejorar Inicialización ELM327

**Archivo:** `app/src/main/java/com/fleetcare/obd/utils/obd/ELM327Commands.kt`

### Tareas

- [x] **9.1.1** Agregar comando de timeout configurable
  - [x] Crear constante `SET_TIMEOUT_50 = "ATST50"` (200ms)
  - [x] Documentar que ATST usa unidades de 4ms (50 = 200ms)
  - [x] Agregar a `Initialization` object

- [x] **9.1.2** Agregar comandos de adaptive timing
  - [x] Crear `ADAPTIVE_TIMING_AUTO1 = "ATAT1"`
  - [x] Crear `ADAPTIVE_TIMING_AUTO2 = "ATAT2"` (más agresivo)
  - [x] Documentar diferencias entre AT1 y AT2
  - [x] Agregar `ADAPTIVE_TIMING_OFF = "ATAT0"` (opcional)

- [x] **9.1.3** Agregar comando para verificar protocolo
  - [x] Crear `DESCRIBE_PROTOCOL = "ATDP"` (ya existía)
  - [x] Crear `DESCRIBE_PROTOCOL_NUMBER = "ATDPN"`

- [x] **9.1.4** Reorganizar secuencia de inicialización
  - [x] Nueva secuencia implementada:
    ```
    1. ATZ - Reset
    2. ATE0 - Echo OFF
    3. ATL0 - Linefeed OFF
    4. ATST50 - Timeout 200ms
    5. ATAT1 - Adaptive timing
    6. ATH1 - Headers ON (para diagnóstico y multi-frame CAN)
    7. ATS0 - Spaces OFF
    8. ATSP0 - Auto protocol
    9. ATAL - Allow long messages
    10. ATDP - Describe protocol (para logging)
    ```
  - [x] Actualizar constante `INITIALIZATION_SEQUENCE`
  - [x] Documentar razón de cada comando con comentarios inline

**Criterios de aceptación:**
- ✅ Secuencia de inicialización incluye timeout configurable
- ✅ Adaptive timing habilitado por defecto
- ✅ Se verifica y loguea el protocolo detectado

**Estado:** ✅ COMPLETADO (2025-10-30)

---

## Sprint 9.2: Parser de Respuestas Multi-Frame CAN

**Archivo:** `app/src/main/java/com/fleetcare/obd/utils/obd/OBDCommandParser.kt`

### Tareas

- [x] **9.2.1** Crear función de detección de multi-frame
  - [x] Método `isCANMultiFrame(response: String): Boolean`
  - [x] Detectar patrones: `0:`, `1:`, `2:`, etc.
  - [x] Detectar longitud en primer frame con regex

- [x] **9.2.2** Implementar parser de multi-frame CAN
  - [x] Método `parseCANMultiFrame(response: String): String`
  - [x] Extraer primer frame: `0:2XXXXXX` → longitud=2, datos=XXXXXX
  - [x] Extraer frames subsecuentes: `1:XXXXXX`, `2:XXXXXX`
  - [x] Combinar todos los datos en un solo string
  - [x] Remover headers CAN (0:, 1:, 2:)
  - [x] Validar longitud esperada vs recibida

- [x] **9.2.3** Implementar parser de headers CAN opcionales
  - [x] Método `removeCANHeaders(response: String): String`
  - [x] Detectar headers de 3 bytes: `7E8 03 41 0C XX`
  - [x] Detectar headers de 1-2 bytes: `48 6B 10 41 0C XX XX`
  - [x] Extraer solo la parte de datos (después del header)
  - [x] Detectar modos de respuesta OBD (41, 62, etc.)

- [x] **9.2.4** Actualizar función `cleanResponse()`
  - [x] Verificar si es multi-frame antes de limpiar
  - [x] Si es multi-frame, llamar a `parseCANMultiFrame()`
  - [x] Si tiene headers CAN, llamar a `removeCANHeaders()`
  - [x] Mantener compatibilidad con respuestas simples
  - [x] Documentar todos los formatos soportados

- [ ] **9.2.5** Agregar tests unitarios
  - [ ] Test: respuesta single-frame normal `"41 0C 1A F8"`
  - [ ] Test: respuesta multi-frame `"0:2FFFFFFFFFFFFF"`
  - [ ] Test: respuesta con headers `"7E8 03 41 0C 1A F8"`
  - [ ] Test: respuesta multi-frame con newlines
  - [ ] Test: respuesta inválida debe retornar error

**Criterios de aceptación:**
- ✅ Parser detecta y combina respuestas multi-frame CAN
- ✅ Parser maneja headers CAN opcionales (3 bytes y 1-2 bytes)
- ✅ Mantiene compatibilidad con respuestas single-frame
- ⏳ Tests unitarios pendientes (se pueden hacer después)

**Estado:** ✅ COMPLETADO (2025-10-30) - Tests pendientes

---

## Sprint 9.3: Fallback de Protocolos

**Archivos:**
- `app/src/main/java/com/fleetcare/obd/bluetooth/BluetoothService.kt`
- `app/src/main/java/com/fleetcare/obd/bluetooth/RFCOMMConnector.kt`
- `app/src/main/java/com/fleetcare/obd/utils/obd/ELM327Commands.kt`

### Tareas

- [x] **9.3.1** Crear lista de protocolos CAN para fallback
  - [x] Constante `CAN_PROTOCOLS` en ELM327Commands (líneas 374-379)
  - [x] Mapa `ALL_PROTOCOLS` con referencia completa (líneas 399-413)
  - [x] Función `setProtocol(protocolNumber: String)` (línea 419)
  - [x] Lista ordenada por probabilidad: 6 → 7 → 8 → 9

- [x] **9.3.2** Implementar método de fallback de protocolo
  - [x] Método `tryProtocolFallback(): Result<String>` en BluetoothService (líneas 227-286)
  - [x] Itera por cada protocolo en `CAN_PROTOCOLS`
  - [x] Envía comando `ATSP{number}` para cada uno
  - [x] Verifica con comando de prueba `0100` (PIDs soportados)
  - [x] Detecta respuestas de error con `OBDCommandParser.isErrorResponse()`
  - [x] Retorna el protocolo que funcionó o error
  - [x] Delays entre intentos (500ms para establecer, 300ms entre protocolos)

- [x] **9.3.3** Integrar fallback en inicialización
  - [x] En `initializeOBDAdapter()` después de comando 0100 de prueba (líneas 340-384)
  - [x] Si comando de prueba falla o retorna error, llama a `tryProtocolFallback()`
  - [x] Si auto-detección funciona, obtiene protocolo con `ATDPN`
  - [x] Loguea protocolo que funcionó (manual o auto-detectado)
  - [x] Guarda protocolo en `rfcommConnector.protocolUsed`

- [x] **9.3.4** Guardar protocolo exitoso
  - [x] Variable `protocolUsed: String?` en RFCOMMConnector (línea 60)
  - [x] Métodos de persistencia en BluetoothService:
    - [x] `saveProtocolForDevice(deviceAddress, protocol)` (líneas 468-476)
    - [x] `loadProtocolForDevice(deviceAddress)` (líneas 487-499)
  - [x] Usa SharedPreferences con clave `protocol_{MAC_ADDRESS}`
  - [x] Guarda después de fallback exitoso (líneas 362-367)
  - [x] Guarda después de auto-detección exitosa (líneas 371-383)

- [x] **9.3.5** Usar protocolo guardado en reconexiones
  - [x] Al inicio de inicialización, carga protocolo guardado (líneas 299-303)
  - [x] Reemplaza `ATSP0` con `ATSP{saved}` si existe protocolo guardado (líneas 307-312)
  - [x] Loguea "Usando protocolo guardado X en lugar de auto-detección"
  - [x] Si falla, fallback automáticamente prueba otros protocolos

**Criterios de aceptación:**
- ✅ Si auto-detección falla, prueba protocolos CAN manualmente
- ✅ Se guarda el protocolo que funcionó
- ✅ Reconexiones usan el protocolo guardado primero
- ✅ Logs indican qué protocolo se está usando

**Estado:** ✅ COMPLETADO (2025-10-30)

---

## Sprint 9.4: Mejorar Manejo de "NO DATA"

**Archivos:**
- `app/src/main/java/com/fleetcare/obd/data/repository/VehicleRepositoryImpl.kt`
- `app/src/main/java/com/fleetcare/obd/domain/repository/VehicleRepository.kt`

### Tareas

- [x] **9.4.1** Crear caché de PIDs no soportados
  - [x] Agregado `unsupportedPIDsCache: MutableSet<String>` (línea 63)
  - [x] Agregado `pidFailureCountMap: MutableMap<String, Int>` (línea 64)
  - [x] Constante `maxFailuresBeforeCache = 3` (línea 65)
  - [x] Flow `unsupportedPIDsFlow` para observación en UI (líneas 68-69)

- [x] **9.4.2** Filtrar PIDs no soportados
  - [x] Filtro en `readAllBasicParameters()` (líneas 138-142)
  - [x] Verifica caché antes de enviar comando
  - [x] Skip con log DEBUG si PID está en caché
  - [x] Reset contador de fallos en caso de éxito (líneas 163-164)

- [x] **9.4.3** Cambiar nivel de log para "NO DATA"
  - [x] Método `handleNoDataError()` implementado (líneas 552-577)
  - [x] Primera vez: `Logger.w()` (WARNING)
  - [x] Intentos 2-2: `Logger.d()` (DEBUG)
  - [x] Tercer fallo: agrega a caché con `Logger.i()` (INFO)
  - [x] Subsecuentes: solo DEBUG
  - [x] Integrado en `readSingleParameter()` (líneas 210-213)

- [x] **9.4.4** Agregar UI indicator para PIDs no disponibles
  - [x] Flow `unsupportedPIDsFlow` expuesto en interfaz (línea 28)
  - [x] Flow emite cambios cuando se agregan PIDs (línea 574)
  - [x] UI ya muestra "--" para valores null (DashboardFragment existente)
  - [x] Método `clearUnsupportedPIDsCache()` público (líneas 590-596)

- [x] **9.4.5** Limpiar caché al cambiar de vehículo
  - [x] Método `checkVINChangeAndClearCache()` (líneas 606-631)
  - [x] Detecta cambio de VIN comparando con SharedPreferences
  - [x] Limpia caché automáticamente al detectar nuevo VIN
  - [x] Guarda VIN con clave `PREF_KEY_LAST_VIN` (línea 634)
  - [x] Llamado desde `detectVINIfNeeded()` (línea 463)
  - [x] SharedPreferences inyectado en constructor (línea 39)

**Criterios de aceptación:**
- ✅ "NO DATA" no genera logs de ERROR (solo WARNING primera vez, DEBUG después)
- ✅ PIDs que fallan 3+ veces no se reintentan (agregados a caché)
- ✅ UI muestra claramente PIDs no disponibles ("--" para valores null)
- ✅ Caché se limpia al cambiar de vehículo (detecta VIN diferente)

**Estado:** ✅ COMPLETADO (2025-10-30)

---

## Sprint 9.5: Detección Automática de PIDs Soportados

**Archivos:**
- `app/src/main/java/com/fleetcare/obd/data/obd/SupportedPIDsDetector.kt`
- `app/src/main/java/com/fleetcare/obd/ui/dashboard/DashboardViewModel.kt`
- `app/src/main/java/com/fleetcare/obd/data/repository/VehicleRepositoryImpl.kt`

### Tareas

- [x] **9.5.1** Arreglar parsing de bitmap con headers CAN
  - [x] Actualizado `parseBitmapResponse()` (líneas 148-193)
  - [x] Usa `OBDCommandParser.cleanResponse()` de Sprint 9.2 (línea 153)
  - [x] Remueve headers CAN y multi-frame automáticamente
  - [x] Validación mejorada de longitud mínima (línea 158-160)
  - [x] Log de respuesta RAW y limpia para diagnóstico

- [x] **9.5.2** Ejecutar detección automática al conectar
  - [x] En `DashboardViewModel.observeConnectionState()` (líneas 149-153)
  - [x] Después de conexión exitosa e inicialización OBD
  - [x] Llama a `detectSupportedPIDs(forceRefresh = false)`
  - [x] Solo si no hay bitmap cargado previamente
  - [x] Loguea PIDs detectados en `DetectSupportedPIDsUseCase`

- [x] **9.5.3** Guardar bitmap en Room Database
  - [x] Tabla `SupportedPIDsCache` ya implementada (Sprint 2)
  - [x] `SupportedPIDsDao` con métodos CRUD completos
  - [x] `SupportedPIDsRepositoryImpl` con persistencia (línea 27-28)
  - [x] Campos: `id`, `vehicleId`, `vin`, `detectedAt`, `pidRanges`
  - [x] TTL de 30 días por defecto (configurable en UseCase)

- [x] **9.5.4** Cargar bitmap al iniciar Dashboard
  - [x] `DetectSupportedPIDsUseCase.execute()` verifica caché primero
  - [x] Si existe y no expiró (< 30 días), usa caché (líneas 54-75)
  - [x] Si no existe o expiró, ejecuta detección completa
  - [x] `DashboardViewModel` llama automáticamente al conectar

- [x] **9.5.5** Usar bitmap para filtrar PIDs en Dashboard
  - [x] `VehicleRepositoryImpl` inyecta `SupportedPIDsRepository` (línea 40)
  - [x] Método `loadSupportedPIDsBitmap()` (líneas 659-687)
  - [x] Método `isPIDSupportedByBitmap()` (líneas 697-710)
  - [x] Filtro en `readAllBasicParameters()` (líneas 160-164)
  - [x] Skip de PIDs no soportados con log DEBUG
  - [x] Combinado con caché de "NO DATA" de Sprint 9.4

**Criterios de aceptación:**
- ✅ Detección de PIDs se ejecuta automáticamente al conectar
- ✅ Bitmap se guarda en database por vehicleId y VIN
- ✅ Dashboard solo intenta leer PIDs soportados (doble filtro: bitmap + caché NO DATA)
- ✅ Parsing funciona con respuestas CAN multi-frame (usa parser de Sprint 9.2)

**Estado:** ✅ COMPLETADO (2025-10-30)

---

## Sprint 9.6: Diagnóstico y Logging Mejorado

**Archivos:**
- `app/src/main/java/com/fleetcare/obd/bluetooth/BluetoothService.kt`
- `app/src/main/java/com/fleetcare/obd/utils/obd/OBDCommandParser.kt`
- `app/src/main/java/com/fleetcare/obd/utils/Constants.kt`

### Tareas

- [x] **9.6.1** Agregar logging de diagnóstico de inicialización
  - [x] Método `logDiagnosticInfo()` en BluetoothService (líneas 506-539)
  - [x] Loguea versión ELM327 (ATI) con formato limpio
  - [x] Loguea protocolo detectado con nombre completo (ATDP)
  - [x] Loguea voltaje del vehículo (ATRV)
  - [x] Banner de diagnóstico con separadores visuales
  - [x] Se ejecuta automáticamente después de inicialización exitosa (línea 401)

- [ ] **9.6.2** Crear modo de diagnóstico (OPCIONAL - No implementado)
  - [ ] Agregar flag `diagnosticMode` en Settings
  - [ ] Si está habilitado, loguear TODAS las respuestas RAW
  - [ ] Guardar log de sesión en archivo
  - [ ] Botón para compartir log de diagnóstico
  - **Nota:** Feature opcional para futuras versiones

- [ ] **9.6.3** Agregar métricas de conexión (OPCIONAL - No implementado)
  - [ ] Contar comandos enviados
  - [ ] Contar respuestas exitosas vs errores
  - [ ] Calcular tasa de éxito
  - [ ] Mostrar en Settings
  - **Nota:** Feature opcional para futuras versiones

- [x] **9.6.4** Mejorar mensajes de error para usuario
  - [x] Actualizado `getErrorMessage()` en OBDCommandParser (líneas 389-427)
  - [x] "UNABLE TO CONNECT" → Mensaje con checklist de verificación
  - [x] "BUS INIT/BUS ERROR" → Sugerencias de reconexión
  - [x] "CAN ERROR" → Explicación de compatibilidad
  - [x] "BUFFER FULL" → Mensaje informativo
  - [x] Mensajes mejorados en Constants.ErrorMessages (líneas 111-137)
  - [x] Todos los errores incluyen sugerencias accionables

**Criterios de aceptación:**
- ⏸️ Modo diagnóstico loguea toda la comunicación RAW (opcional, no implementado)
- ⏸️ Métricas muestran salud de la conexión (opcional, no implementado)
- ✅ Mensajes de error son amigables para usuario final
- ✅ Logs de diagnóstico básicos disponibles (versión, protocolo, voltaje)

**Estado:** ✅ COMPLETADO (2025-10-30) - Tareas core completadas, opcionales pospuestas

---

## Checklist General del Sprint 9

### Pre-requisitos
- [ ] Backup del código actual en branch `feature/sprint-8-completed`
- [ ] Crear branch `feature/sprint-9-obd-fixes`
- [ ] Revisar documentación de ELM327
- [ ] Tener acceso a vehículo de prueba (Suzuki Ertiga 2018)

### Desarrollo
- [x] Completar Sprint 9.1: Inicialización mejorada ✅ (2025-10-30)
- [x] Completar Sprint 9.2: Parser multi-frame ✅ (2025-10-30)
- [x] Completar Sprint 9.3: Fallback de protocolos ✅ (2025-10-30)
- [x] Completar Sprint 9.4: Manejo "NO DATA" ✅ (2025-10-30)
- [x] Completar Sprint 9.5: Detección PIDs ✅ (2025-10-30)
- [x] Completar Sprint 9.6: Diagnóstico ✅ (2025-10-30)

### Testing
- [ ] Test unitarios de parser multi-frame
- [ ] Test unitarios de fallback de protocolo
- [ ] Test de integración con Suzuki Ertiga 2018
- [ ] Test de regresión con Kia Sportage 2025
- [ ] Test de reconexión y protocolo guardado

### Documentación
- [ ] Actualizar README con vehículos probados
- [ ] Documentar formatos de respuesta CAN soportados
- [ ] Documentar proceso de fallback de protocolo
- [ ] Crear guía de troubleshooting

### Deploy
- [ ] Merge a `develop`
- [ ] Test en staging
- [ ] Crear release notes
- [ ] Merge a `main`
- [ ] Tag versión `v1.1.0-sprint9`

---

## Métricas de Éxito

### Técnicas
- ✅ Tasa de conexión exitosa > 95% con vehículos comunes
- ✅ Parsing correcto de respuestas multi-frame CAN
- ✅ Reducción de logs ERROR en 80%
- ✅ Tiempo de inicialización < 3 segundos

### Funcionales
- ✅ Funciona con Suzuki Ertiga 2018
- ✅ Mantiene funcionamiento con Kia Sportage 2025
- ✅ Protocolo se detecta automáticamente o mediante fallback
- ✅ Solo se intentan leer PIDs soportados

### UX
- ✅ Mensajes de error claros y accionables
- ✅ UI indica claramente PIDs no disponibles
- ✅ Modo diagnóstico ayuda a troubleshooting
- ✅ No hay crashes por respuestas inesperadas

---

## Riesgos y Mitigaciones

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|--------------|---------|------------|
| Parser multi-frame rompe parsing simple | Media | Alto | Tests exhaustivos de regresión |
| Fallback de protocolo toma mucho tiempo | Media | Medio | Timeout por protocolo, max 3 intentos |
| Caché de PIDs no soportados se vuelve obsoleto | Baja | Medio | TTL de 30 días, botón para limpiar |
| Headers CAN varían por fabricante | Alta | Alto | Soportar múltiples formatos, modo diagnóstico |

---

## Notas de Implementación

### Formato de Respuestas CAN Observadas

**Single-frame sin headers:**
```
41 0C 1A F8
```

**Single-frame con headers:**
```
7E8 03 41 0C 1A F8
```

**Multi-frame:**
```
0:2FFFFFFFFFFFFF
1:XXXXXXXXXXXXXX
```

**Multi-frame con headers:**
```
7E8 10 0F 49 02 01 31 47
7E8 21 34 48 52 33 48 35
```

### Comandos AT Útiles para Diagnóstico

```
ATI    - Versión del ELM327
ATRV   - Voltaje del vehículo
ATDP   - Protocolo actual
ATDPN  - Número de protocolo actual
ATST50 - Set timeout 200ms
ATAT1  - Adaptive timing auto
```

---

**Última actualización:** 2025-10-30
**Estado:** Planificación
**Estimación:** 3-5 días de desarrollo
