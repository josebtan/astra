# Progreso — V0.1 Foundation

## Hecho

- **Esqueleto Gradle** multi-módulo (`settings.gradle.kts`, `build.gradle.kts`, módulos `app` y `core` como Android Library/Application con Kotlin, JDK 17, minSdk 26 / compileSdk 34).
- **`core/model`** — modelo de datos base de todo el proyecto (roadmap secciones 5–9):
  - `CameraCapabilities`, `CaptureSettings`, `SequenceSettings`, `RegionOfInterest`, `RawFormat`
  - `CameraStatus`, `CameraConnectionState`
  - `ImageMetadata` (con campos astrométricos opcionales, se rellenan más adelante)
  - `ImageFrame`, `FrameType`
  - `ObservationSession`, `SessionStatus`
  - `CameraDevice` — interfaz común para todas las fuentes de imagen
- **Tests unitarios** (`ObservationSessionTest`) verificando que una sesión nace vacía/trazable y que un frame referencia correctamente su sesión y metadata.

## Añadido: `core/storage` (RAW/WORK/RESULTS) y ajustes de usuario persistentes

- **`SessionFileStore`** — crea y gestiona `RAW/ WORK/ RESULTS/` por sesión (roadmap sección 4). Protege contra sobrescribir un RAW ya existente.
- **`UserSettings`** — todo lo que el usuario puede configurar y que ASTRA debe recordar entre lanzamientos: ruta de almacenamiento, ISO/exposición/formato RAW por defecto, última cámara usada, tema, modo Scientific/Visualization por defecto.
- **`SettingsRepository`** (interfaz) + **`SettingsListener`** — contrato de persistencia y notificación de cambios de ajustes, sin dependencia de Android, para que sea testeable en Kotlin puro.
- **`InMemorySettingsRepository`** — implementación en memoria, usada en tests y previews.
- **`DataStoreSettingsRepository`** — implementación real con Jetpack DataStore, la que efectivamente persiste los ajustes en disco en el dispositivo.

## Verificación realizada en este entorno

Este sandbox no tiene acceso a los repositorios de Google/Maven Central necesarios para ejecutar Gradle con el Android Gradle Plugin real. Para verificar de verdad (no solo "a ojo") el código independiente de Android, se descargó el compilador **Kotlin 1.9.24 real** (el mismo que usa el proyecto) desde GitHub Releases, más `junit4`, `kotlinx-coroutines-core` y `atomicfu` vía `apt`:

```bash
# ver scripts/verify-core-jvm.sh para el detalle reproducible
./scripts/verify-core-jvm.sh
# -> Compiling main sources / Compiling tests / Running tests
# -> OK (12 tests)
```

Se verificaron así: `core/model` completo, `SessionFileStore` (con directorios temporales reales), y `InMemorySettingsRepository` (persistencia en memoria + notificación a listeners).

**No verificable en este sandbox** (requieren Android runtime real, sin red a Maven de Google):
- `DataStoreSettingsRepository` (Jetpack DataStore)
- Cualquier futura entidad/DAO de Room
- Los módulos `app`/`core` como Android Library/Application vía `./gradlew`

**Pendiente:** abrir el proyecto en Android Studio (con acceso normal a internet) para confirmar `./gradlew :core:test` y `./gradlew :app:assembleDebug` con el Android Gradle Plugin real.

## Añadido: persistencia real con Room (`core/storage/db`)

- **`ObservationSessionEntity`** / **`ImageFrameEntity`** (+ `ImageMetadataEntity` embebida) — modelo de persistencia Room, separado deliberadamente del modelo de dominio (`core/model`) para que el esquema en disco pueda evolucionar sin tocar la lógica de negocio.
- **`Mappers.kt`** — conversión dominio ↔ entidad en ambas direcciones.
- **`ObservationSessionDao`** / **`ImageFrameDao`** — operaciones CRUD suspendidas.
- **`AstraDatabase`** — base de datos Room (`astra.db`, versión 1).
- **`RoomSessionRepository`** — implementa `SessionRepository` (interfaz ya definida en `core/storage`, sin dependencia de Room) usando los DAOs. Los `frameIds` de una sesión se derivan consultando los frames por `sessionId`, no se guardan en la fila de la sesión.

## Verificación de Room: Robolectric, no emulador

Room requiere procesamiento de anotaciones (KSP) y una base de datos SQLite real — no se puede probar con `kotlinc` suelto como el resto de `core/model`. En vez de dejarlo sin probar, se añadió **Robolectric** (`org.robolectric:robolectric`), que corre SQLite real dentro de la JVM del test, sin necesitar un emulador de Android. `RoomSessionRepositoryTest` usa una base de datos Room en memoria y prueba: crear sesión, añadir frames y verificar que aparecen en `frameIds`, actualizar estado/tiempo de integración, id desconocido devuelve `null`, y listar todas las sesiones.

Esto **no se pudo ejecutar en este sandbox** (KSP + Robolectric necesitan descargar artefactos de Maven Central/Google, fuera de los dominios permitidos aquí) — la verificación real vino del run de GitHub Actions después del push (ver más abajo).

## Siguiente paso

Con sesiones y frames ya persistidos, el roadmap (sección 31, V0.2) apunta a **Camera** — implementar `AndroidCameraDevice` (Camera2 API) como primera implementación real de la interfaz `CameraDevice`, con captura RAW/DNG funcional.
