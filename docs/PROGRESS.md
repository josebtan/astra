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

## Añadido: V0.2 — Camera (`camera` module, `AndroidCameraDevice`)

Nuevo módulo Gradle **`camera`**, primer consumidor real de la interfaz `CameraDevice` (roadmap sección 5). Por ahora solo cámara del teléfono — USB/astro quedan para más adelante, detrás de la misma interfaz.

- **`AndroidCameraDevice`** — implementación con Camera2: `connect()`/`disconnect()` (abre/cierra el dispositivo con `CameraManager`), `capture()` (crea sesión de captura, dispara con `CONTROL_AE_MODE_OFF` + exposición/ISO manuales del `CaptureSettings`, escribe DNG con `DngCreator` usando el `TotalCaptureResult` real, arma el `ImageFrame` con metadata), `startSequence()`/`stopSequence()`, `getStatus()`.
  - `capture()` es síncrona de cara al llamador (como pide la interfaz `CameraDevice`) aunque Camera2 es 100% basado en callbacks — internamente bloquea con un `Semaphore` hasta tener imagen + resultado de captura. **Debe llamarse desde un hilo de fondo**, nunca desde el hilo principal.
  - `startPreview()`/`stopPreview()` **no están implementados todavía** — Camera2 necesita un `Surface` que vendrá de la capa `ui` (que no existe aún). Lanzan `UnsupportedOperationException` con nota explicando por qué. No bloquea el milestone: la prioridad de la sección 42 es captura + trazabilidad, no preview.
- **`CameraSensorSpec` + `mapToCapabilities()`** — lógica pura (sin ningún import de `android.*`) que convierte los valores crudos de `CameraCharacteristics` en `CameraCapabilities`: bit depth derivado del white level, conversión ns→s del rango de exposición, defaults correctos para cámara de teléfono (sin sensor de temperatura, sin cooling, sin binning — igual que el ejemplo del POCO X7 Pro en la sección 6 del roadmap).

## Verificación

**Sí verificable en este sandbox** (lógica pura, sin Android): `mapToCapabilities()` — 7 tests reales cubriendo bit depth, conversión de unidades, rangos ISO faltantes/presentes, y los defaults de cámara de teléfono.
```bash
./scripts/verify-camera-jvm.sh
# -> OK (7 tests)
```

**No verificable aquí** (necesita un HAL de Camera2 real — dispositivo o emulador): todo `AndroidCameraDevice.kt` (apertura de cámara, sesión de captura, escritura de DNG). Se compilará en CI (GitHub Actions ya tiene Android SDK), pero el comportamiento real de la cámara solo se puede confirmar en un dispositivo/emulador de verdad — CI no tiene hardware de cámara.

## Añadido: V0.3 — RAW Engine (`raw` module, `DngTiffReader`)

Nuevo módulo Gradle **`raw`**: decodifica los DNG que `AndroidCameraDevice` ya escribe hacia `LinearImage`, el tipo de dato que van a consumir calibración/registro/stacking (roadmap sección 7).

- **`LinearImage`** (nuevo, en `core/model`) — `width`, `height`, `channels`, `bitDepth`, `data: FloatArray`. Es una `class` normal, no `data class`: comparar `FloatArray` por `equals()` generado compararía por referencia, no por contenido — se añadió `contentEquals()` explícito para eso.
- **`DngTiffReader`** — parser mínimo de TIFF/DNG, **100% Kotlin puro** (`java.io`/`java.nio`, sin ningún import de `android.*`). Deliberadamente acotado: un solo IFD, una sola tira sin comprimir, 8/16 bits por muestra — exactamente lo que produce `DngCreator` en el pipeline de captura. No es un parser TIFF/DNG general (no soporta múltiples tiras, compresión, sub-IFDs ni extracción del patrón CFA — eso es un paso de debayering futuro).
- Linealización: `(raw - blackLevel) / (whiteLevel - blackLevel)`, recortado a `[0,1]`.

## Verificación: esta vez de punta a punta, con archivos reales

Como `DngTiffReader` no depende de Android, pude construir a mano — byte por byte — archivos DNG/TIFF mínimos sintéticos dentro del test (`DngTiffReaderTest`) y verificar que el parser los lee correctamente: valores en el límite negro/blanco, recorte de valores fuera de rango, rechazo explícito de compresión no soportada, y valores por defecto cuando faltan las etiquetas BlackLevel/WhiteLevel.

```bash
./scripts/verify-raw-jvm.sh
# -> OK (4 tests)
```

Este es el primer módulo del pipeline de procesamiento (a diferencia de `camera`) que se pudo verificar **completamente** en este sandbox, sin depender de CI para confirmar que la lógica funciona.

## Siguiente paso

Con RAW ya decodificado a `LinearImage`, el roadmap (V0.4) apunta a **Calibration**: `BiasCalibration`, `DarkCalibration`, `FlatCalibration` y `DefectCorrection` (sección 10-11) — restar dark/bias, dividir por flat, y generar el mapa de píxeles defectuosos del sensor. Esto también es aritmética pura sobre `LinearImage`, así que debería poder verificarse igual de a fondo que el RAW engine.
