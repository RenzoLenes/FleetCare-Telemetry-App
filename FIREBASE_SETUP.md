# Configuración de Firebase para FleetCare OBD

Este documento explica cómo configurar Firebase para que la aplicación funcione correctamente.

## Pasos de Configuración

### 1. Crear Proyecto en Firebase Console

1. Ve a [Firebase Console](https://console.firebase.google.com/)
2. Click en "Agregar proyecto"
3. Nombre del proyecto: `FleetCareOBD` (o el nombre que prefieras)
4. Deshabilita Google Analytics (opcional para desarrollo)
5. Click en "Crear proyecto"

### 2. Agregar Aplicación Android

1. En la página del proyecto, click en el ícono de Android
2. Paquete de Android: `com.fleetcare.obd`
3. Nickname (opcional): `FleetCare OBD`
4. SHA-1 (opcional por ahora, se usa para Auth avanzada)
5. Click en "Registrar app"

### 3. Descargar google-services.json

1. Descarga el archivo `google-services.json`
2. Coloca el archivo en: `app/google-services.json`
3. Este archivo contiene las credenciales de tu proyecto Firebase

**IMPORTANTE:** Este archivo ya está en `.gitignore` para proteger tus credenciales.

### 4. Habilitar Firebase Authentication

1. En Firebase Console, ve a "Authentication"
2. Click en "Comenzar"
3. En la pestaña "Sign-in method":
   - Habilita "Anónimo" (Anonymous)
   - Guarda los cambios

### 5. Configurar Firebase Realtime Database

1. En Firebase Console, ve a "Realtime Database"
2. Click en "Crear base de datos"
3. Ubicación: Elige la más cercana a tu región
4. Modo de seguridad: Comienza en **modo de prueba** (test mode)
5. Click en "Habilitar"

### 6. Reglas de Seguridad de Realtime Database

Por ahora, usa estas reglas para desarrollo (modo de prueba):

```json
{
  "rules": {
    ".read": "auth != null",
    ".write": "auth != null"
  }
}
```

**Para producción (implementar en Sprint 4):**

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "$uid === auth.uid",
        ".write": "$uid === auth.uid"
      }
    },
    "vehicles": {
      "$vehicleId": {
        ".read": "root.child('vehicles').child($vehicleId).child('userId').val() === auth.uid",
        ".write": "root.child('vehicles').child($vehicleId).child('userId').val() === auth.uid"
      }
    }
  }
}
```

### 7. Estructura de Datos en Firebase

La aplicación creará automáticamente esta estructura:

```
firebase-root/
├── users/
│   └── {userId}/
│       ├── createdAt: timestamp
│       ├── isAnonymous: boolean
│       └── vehicles: array
│
└── vehicles/
    └── {vehicleId}/
        ├── userId: string
        ├── name: string
        ├── make: string (opcional)
        ├── model: string (opcional)
        └── sessions/
            └── {sessionId}/
                ├── startTime: timestamp
                ├── endTime: timestamp
                └── data/
                    └── {timestamp}/
                        ├── rpm: number
                        ├── speed: number
                        ├── coolantTemp: number
                        ├── throttlePosition: number
                        ├── engineLoad: number
                        └── fuelLevel: number
```

## Verificación de Instalación

### Compilar el Proyecto

```bash
./gradlew clean build
```

Si hay errores relacionados con `google-services.json`, verifica que:
1. El archivo está en la ubicación correcta: `app/google-services.json`
2. El package name en el archivo coincide con `com.fleetcare.obd`
3. Has sincronizado el proyecto en Android Studio

### Probar Autenticación

1. Ejecuta la app en un emulador o dispositivo
2. En Logcat, busca logs que contengan "[FIREBASE]"
3. Deberías ver: "Autenticación anónima exitosa. UID: xxxxx"
4. En Firebase Console > Authentication > Users, deberías ver el usuario anónimo creado

## Troubleshooting

### Error: "google-services.json not found"

**Solución:**
- Verifica que el archivo está en `app/google-services.json`
- Sync el proyecto: File > Sync Project with Gradle Files

### Error: "Default FirebaseApp is not initialized"

**Solución:**
- Verifica que `google-services.json` tiene el package name correcto
- Clean y rebuild el proyecto
- Verifica que el plugin de Google Services está aplicado en `app/build.gradle.kts`

### Error: "Authentication failed"

**Solución:**
- Verifica que Anonymous Auth está habilitado en Firebase Console
- Verifica conexión a Internet
- Revisa las reglas de seguridad de Realtime Database

## Notas de Seguridad

### Para Desarrollo:
- Usa reglas de prueba (test mode)
- Usuario anónimo tiene acceso completo

### Para Producción:
- Implementar reglas estrictas de seguridad
- Validar datos en Cloud Functions
- Habilitar App Check para prevenir abuso
- Limitar lectura/escritura por usuario

## Próximos Pasos

Una vez configurado Firebase:

1. ✅ **Sprint 1 completado**: Autenticación anónima funcional
2. **Sprint 2**: Implementar Bluetooth
3. **Sprint 3**: Lectura de datos OBDII
4. **Sprint 4**: Envío de datos a Firebase Realtime Database

## Recursos

- [Documentación Firebase Android](https://firebase.google.com/docs/android/setup)
- [Firebase Realtime Database](https://firebase.google.com/docs/database)
- [Firebase Authentication](https://firebase.google.com/docs/auth)
- [Reglas de Seguridad](https://firebase.google.com/docs/database/security)
