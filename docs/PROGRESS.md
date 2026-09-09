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

## Siguiente paso

Según el roadmap (sección 26): persistencia de `ObservationSession`/`ImageFrame` con Room (entidades, DAOs, mapeo a los modelos de dominio de `core/model`), para poder crear y recuperar sesiones reales entre lanzamientos de la app — hasta ahora solo viven en memoria.
