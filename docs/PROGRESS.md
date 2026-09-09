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

## Verificación realizada en este entorno

Este entorno no tiene acceso a los repositorios de Google/Maven Central necesarios para ejecutar Gradle con el Android Gradle Plugin real, así que la verificación se hizo directamente con `kotlinc` + `junit4` (instalados vía `apt`):

```bash
kotlinc core/src/main/kotlin/com/astra/core/model/*.kt -d out/
kotlinc -cp out/:junit4.jar core/src/test/kotlin/.../ObservationSessionTest.kt -d test-out/
java -cp out/:test-out/:junit4.jar:hamcrest-core.jar:kotlin-stdlib.jar \
  org.junit.runner.JUnitCore com.astra.core.model.ObservationSessionTest
# -> OK (2 tests)
```

**Pendiente:** abrir el proyecto en Android Studio (con acceso normal a internet) para confirmar que `./gradlew :core:test` y `./gradlew :app:assembleDebug` funcionan con el Android Gradle Plugin real. La lógica y sintaxis Kotlin ya están validadas; lo que falta es la resolución de dependencias de Android, que este sandbox no puede hacer.

## Siguiente paso

Según el roadmap (sección 36/9): `core/storage` — estructura de carpetas `RAW/ WORK/ RESULTS/` y persistencia de `ObservationSession` (Room), para poder crear y recuperar sesiones de verdad, no solo tener las clases en memoria.
