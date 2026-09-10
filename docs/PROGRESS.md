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

## Añadido: ícono de la app + pantalla de progreso (para que la APK muestre algo al abrir)

Hasta ahora toda la app era lógica interna sin ninguna pantalla — por eso al instalar el APK no pasaba nada al abrirlo: no había actividad declarada como *launcher*.

- **Ícono adaptativo** (`app/src/main/res/mipmap-anydpi-v26/`) — vector, sin depender de imágenes externas: fondo azul noche + estrella de 4 puntas. Como `minSdk=26`, no hace falta generar PNGs de respaldo para versiones antiguas de Android.
- **`MainActivity`** — pantalla temporal (no es el módulo `ui` real del roadmap, que sigue pendiente) que muestra el estado de cada milestone del roadmap (✓ hecho / … en curso / — pendiente). Sirve para: (1) confirmar que la app instala y corre, (2) ver de un vistazo en qué va el desarrollo cada vez que se abre.
- Tema oscuro simple (`Theme.Astra`), sin dependencia de AppCompat — no hacía falta para una pantalla tan simple, y evita añadir una dependencia más que verificar.

**No verificable en este sandbox** (necesita build+render real de Android): tanto el ícono como la actividad se confirman con el run de GitHub Actions (compilación) — para verlos de verdad hay que instalar el APK en un teléfono o emulador.

## Añadido: V0.4 — Calibration (`calibration` module)

Motor de calibración **automático** con **feedback estructurado** para el usuario (pedido explícito):

- **`MasterFrameBuilder`** — combina múltiples frames (bias/dark/flat) por mediana por píxel. La mediana rechaza outliers (un rayo cósmico, una toma mala) sin necesitar ningún parámetro que ajustar.
- **`DefectMapBuilder`** — detecta píxeles calientes/muertos automáticamente usando **mediana + MAD** (median absolute deviation), no media/desviación estándar clásica. Esto importa: con media/stddev, un solo píxel muy caliente infla la desviación estándar y se enmascara a sí mismo (y a otros defectos) — lo descubrí con un test que fallaba de verdad. Mediana/MAD es robusta a esto.
- **`DefectCorrector`** — reemplaza cada píxel defectuoso por la mediana de sus vecinos no defectuosos (fallback a la mediana global si el vecindario también está afectado).
- **`CalibrationEngine`** — orquesta todo automáticamente: usa los frames de calibración que estén disponibles para la sesión (ninguna selección manual, ningún parámetro obligatorio), aplica bias → dark → flat → corrección de defectos en el orden correcto, y **siempre** devuelve un `CalibrationReport` con:
  - qué pasos se aplicaron y cuáles se saltaron (y por qué — ej. "no hay frames de flat, paso omitido")
  - cuántos defectos se detectaron/corrigieron
  - señal media antes/después
  - `toUserMessage()` — texto plano listo para mostrar en la UI

## Verificación: encontró un bug de verdad

Los primeros tests de `DefectMapBuilder` **fallaron** con el enfoque media/stddev original (0 defectos detectados donde debía haber 2) — quedó documentado arriba por qué, y se corrigió antes de seguir. Los 13 tests después de la corrección:
```bash
./scripts/verify-calibration-jvm.sh
# -> OK (13 tests)
```

## Añadido: laboratorio de pruebas real (no solo checklist de progreso)

El feedback fue justo: el roadmap nunca definió un diseño de UI, y una lista de progreso no deja *probar* nada. `MainActivity` ahora tiene, debajo del checklist, un botón por cada función ya implementada — cada uno corre el código real (no una simulación) y muestra el resultado real en una consola de salida en pantalla:

- **Ver capacidades de cámara** — lista las cámaras del dispositivo y llama a `AndroidCameraDevice.getCapabilities()` de verdad.
- **Capturar frame de prueba (1s, ISO 400)** — pide el permiso de cámara si falta, conecta, captura un DNG real, muestra ruta/tamaño/resolución. Si el sensor no reporta soporte RAW, lo dice explícitamente en vez de fallar en silencio.
- **Decodificar último DNG capturado** — corre `DngTiffReader` sobre el archivo recién capturado y muestra dimensiones + estadísticas de los valores linealizados.
- **Autotest de calibración** — corre `CalibrationEngine` con frames sintéticos (mismo patrón que los tests unitarios) y muestra el `CalibrationReport.toUserMessage()` completo.
- **Crear sesión de prueba y releerla (Room)** — prueba la persistencia real: crea una `ObservationSession`, le agrega un frame (el capturado si existe, o uno sintético), y la vuelve a leer desde la base de datos en disco.
- **Probar ajustes persistentes (DataStore)** — incrementa el ISO por defecto guardado y lo vuelve a leer; la instrucción en pantalla explica cómo comprobar que sobrevive a cerrar la app por completo, no solo minimizarla.

Esto es honesto: sigue sin ser la UI final (el módulo `ui` del roadmap sigue pendiente, y el roadmap nunca especificó cómo debía verse), pero permite verificar de verdad, botón por botón, si cada pieza hace lo que se espera — que es exactamente lo que se pidió.

**No verificable en este sandbox** (Activity + Room + DataStore + Camera2, todo Android real): se confirma con el build de GitHub Actions (compilación) y, para el comportamiento real, instalando el APK.

## Bug real encontrado probando en dispositivo: `DngTiffReader` no soportaba tipo RATIONAL

Al probar "Decodificar último DNG" en un teléfono real: `IllegalStateException: Unsupported TIFF type 5 for tag 50714`.

Causa: el `DngCreator` real de Android escribe `BlackLevel` (tag 50714) como TIFF **RATIONAL** (numerador/denominador), no como `LONG` — el parser solo soportaba BYTE/SHORT/LONG porque se diseñó y probó contra archivos sintéticos que solo cubrían esos tipos, no lo que Android realmente escribe. Es el tipo de error que la verificación con sintéticos no detecta si los sintéticos no reproducen el caso real — quedó confirmado en cuanto se probó contra hardware de verdad.

Arreglado:
- `blackLevel`/`whiteLevel` ahora son `Double`, no `Int` (RATIONAL puede no ser un entero exacto).
- Soporte para tipo RATIONAL (8 bytes: numerador + denominador, siempre por offset ya que nunca cabe inline en el campo de 4 bytes).
- Nuevo test que reproduce el caso exacto (`BlackLevel` como RATIONAL 64/1) — pasa.

```bash
./scripts/verify-raw-jvm.sh
# -> OK (5 tests)
```

Sigue siendo un parser acotado (solo lee el primer elemento de un tag con count>1, sin patrón CFA todavía), pero ahora cubre el caso real que rompía en el teléfono.

## Siguiente paso

Seguir probando en el dispositivo real (captura, sesión, ajustes) y corrigiendo lo que aparezca, antes de avanzar a V0.5 (Stacking).

## Añadido: V0.5 — Stacking (`stacking` module)

- **`StackingEngine`** — combina múltiples light frames ya calibrados en una sola imagen integrada. Soporta `MEAN`, `MEDIAN` y `SIGMA_CLIP` (el alcance MVP exacto que pide la sección 12 del roadmap; `WEIGHTED_MEAN`/`MIN`/`MAX` quedan para después).
- Sigma clip de un solo paso: excluye muestras a más de `sigmaThreshold` desviaciones estándar de la media, y reporta cuántas muestras se rechazaron.
- **`StackingReport`** — mismo patrón de feedback que `CalibrationReport`: método usado, cantidad de frames, muestras rechazadas, señal media resultante, `toUserMessage()`.
- Deliberadamente **sin** alineación/registro astronómico todavía (sección 14 del roadmap) — eso depende de detección de estrellas, que es V0.9. Por ahora asume que los frames ya están alineados píxel a píxel.
- 7 tests, verificados de verdad en el sandbox (Kotlin puro, sin Android):
```bash
./scripts/verify-stacking-jvm.sh
# -> OK (7 tests)
```
- Nuevo botón "Autotest de stacking" en el laboratorio de pruebas de `MainActivity`.

## Siguiente paso

Según el roadmap (V0.6): **Quality Analysis** — SNR, FWHM, conteo de estrellas, trailing, ranking de frames y rechazo automático. Esto típicamente iría *antes* de armar qué frames stackear (para descartar tomas malas), así que el orden real de uso del pipeline será: calibrar → analizar calidad → rechazar malos → stackear los buenos.

## Nota sobre commits directos al repo

Hubo un intento de avanzar directamente sobre el repositorio en GitHub que rompió la compilación (10 commits agregando módulos `quality`/`registration` a medio terminar). Se descartaron por completo (reset + force-push al último commit sano, V0.5 Stacking) para retomar desde ahí. Si vas a experimentar directo en el repo, avísame antes para coordinar y no perder trabajo en ninguno de los dos lados.

## Añadido: cámara e interfaz reales funcionando (no más autotest sintético)

Petición explícita: dejar de probar con datos sintéticos y poner a funcionar la cámara y la interfaz de verdad.

- **`CameraDevice.startPreview(surface: Surface)`** — la interfaz ahora recibe un `Surface` real en vez de no hacer nada. Es el único archivo de `core/model` que ya no es Android-free a propósito (necesita `android.view.Surface`); los scripts de verificación local lo excluyen explícitamente y lo documentan.
- **`AndroidCameraDevice`** — implementación real de preview: `startPreview()` crea una sesión de captura repetitiva contra el `Surface`; `stopPreview()` la detiene. Como Camera2 solo permite una sesión activa a la vez, `capture()` cierra el preview antes de capturar y no lo reanuda automáticamente (documentado en el KDoc) — la UI es responsable de reiniciarlo.
- **`CaptureActivity`** (nueva pantalla) — vista previa en vivo con `SurfaceView`, campos de exposición/ISO, selector de tipo de frame (LIGHT/DARK/BIAS/FLAT), y:
  - **Capturar** — captura real, decodifica el DNG resultante con `DngTiffReader`, lo guarda en la lista correspondiente, reanuda el preview.
  - **Procesar sesión** — corre `CalibrationEngine` sobre cada LIGHT capturado usando los BIAS/DARK/FLAT reales capturados en la sesión (no sintéticos), y luego `StackingEngine` sobre los resultados calibrados. Muestra el reporte completo.
  - **Guardar sesión** — persiste todo en Room de verdad.
  - **Limpiar frames** — reinicia el estado en memoria para volver a intentar.
- `MainActivity` ahora tiene un botón "Abrir pantalla de captura en vivo" que lanza `CaptureActivity`. El laboratorio de pruebas con datos sintéticos se queda como está (sigue siendo útil para aislar problemas de una función específica), pero ya no es el único camino.

**No verificable en este sandbox** (Camera2 real, `SurfaceView`, sesiones concurrentes) — se confirma con el build de CI y, para el comportamiento real de la cámara, en el dispositivo.
