# ASTRA — Roadmap y Documentación Técnica

**ASTRA** = Astronomical Science & Tracking Research Application (nombre provisional).

Plataforma de adquisición, calibración, procesamiento y análisis astronómico para Android, diseñada para funcionar con múltiples fuentes de imagen:

- Cámara interna del teléfono
- Cámaras USB
- Cámaras astronómicas dedicadas
- Cámaras DSLR/mirrorless compatibles
- Futuras fuentes de imágenes
- Datos RAW/DNG/FITS
- Imágenes ya capturadas externamente

---

## 1. Visión del proyecto

ASTRA no está orientada únicamente a producir imágenes visualmente atractivas. Su prioridad es:

> Preservar los datos originales, realizar procesamiento reproducible y proporcionar mediciones astronómicas.

Flujo de arquitectura de alto nivel:

```
CAPTURA → DATOS RAW → CALIBRACIÓN → REGISTRO → STACKING
        → PROCESAMIENTO → ANÁLISIS → IDENTIFICACIÓN → RESULTADOS
```

---

## 2. Objetivos

| # | Objetivo | Descripción |
|---|----------|-------------|
| O1 | Adquisición | Controlar cámaras compatibles, obtener datos con mínima modificación |
| O2 | Preservación | Nunca modificar el archivo RAW original |
| O3 | Calibración | Eliminar dark current, hot/dead pixels, bias, viñeteo, polvo, ruido fijo |
| O4 | Integración | Combinar múltiples exposiciones (stacking) |
| O5 | Corrección | Rotación terrestre, movimiento del dispositivo, trailing, gradiente de contaminación lumínica, defectos ópticos |
| O6 | Análisis | Detectar/medir estrellas, planetas, nebulosas, galaxias, satélites, aviones, meteoros, objetos móviles |
| O7 | Astrometría | RA, DEC, escala de píxel, orientación, campo de visión |
| O8 | Ciencia | Exportar a formatos astronómicos estándar (principalmente FITS) |

---

## 3. Arquitectura general

```
ASTRA
   ├── USER INTERFACE ─┬─ API CORE
   │                    │
   └────────────────────┴── CAMERA LAYER
                              ├── Android Camera
                              ├── USB Camera
                              └── External Camera
                                    │
                              RAW ENGINE
                                    │
                              DATA MANAGER
                              ├── CALIBRATION ENGINE
                              └── METADATA ENGINE
                                    │
                              IMAGE ENGINE
                              ├── Registration
                              ├── Stacking
                              └── Correction
                                    │
                              ASTRO ENGINE
                              ├── Detection
                              ├── Astrometry
                              └── Tracking
                                    │
                              CATALOG ENGINE
                                    │
                              SCIENCE DATA
```

---

## 4. Principio fundamental: los datos son sagrados

Tres tipos de datos, cada uno en su propio árbol de carpetas:

```
RAW/            # nunca modificados
    IMG_000001.DNG
WORK/           # resultados intermedios
    calibrated/
    registered/
    rejected/
RESULTS/        # resultados finales
    stacked.fits
    calibrated.fits
    preview.jpg
    stars.csv
```

Regla estricta: **nunca** `RAW → modificar → guardar`. Siempre `RAW → leer → procesar → nuevo archivo`.

---

## 5. Sistema de cámaras

Interfaz común para abstraer cualquier fuente de imagen:

```kotlin
interface CameraDevice {
    fun getCapabilities(): CameraCapabilities
    fun connect()
    fun disconnect()
    fun startPreview()
    fun stopPreview()
    fun capture(settings: CaptureSettings)
    fun startSequence(settings: SequenceSettings)
    fun stopSequence()
    fun getStatus(): CameraStatus
}
```

Implementaciones:

```
CameraDevice
├── AndroidCameraDevice
├── UsbCameraDevice
├── AstroCameraDevice
└── ExternalCameraDevice
```

---

## 6. CameraCapabilities

Cada cámara informa sus capacidades: resolución, tamaño de píxel, profundidad de bits, ISO/gain, rango de exposición, soporte RAW, temperatura, cooling, binning, ROI, FPS. La interfaz de usuario se adapta automáticamente según lo que la cámara reporte soportar.

---

## 7. Motor RAW

Módulo independiente de la cámara.

- **Entrada:** DNG, RAW, FITS, TIFF
- **Salida:** `LinearImage`

```kotlin
class LinearImage(
    val width: Int,
    val height: Int,
    val channels: Int,
    val bitDepth: Int,
    val data: FloatArray,
    val metadata: ImageMetadata
)
```

Procesamiento científico interno en `float`/`double`.

---

## 8. Metadata (`ObservationMetadata`)

timestampUTC, latitude, longitude, altitude, cameraModel, sensorModel, exposureTime, gain, iso, temperature, focalLength, aperture, imageWidth, imageHeight, pixelSize, orientation, lensModel — y cuando sea posible: RA, DEC, Azimuth, Altitude.

---

## 9. Sesiones de observación (`ObservationSession`)

No se trabaja con fotos sueltas, sino con sesiones completas (cámara, objetivo, ISO, exposición, número de frames, calibración aplicada, método de procesamiento, tiempo de integración total), lo que garantiza reproducibilidad.

---

## 10. Calibration Engine

```
CalibrationEngine
├── BiasCalibration     # LIGHT - BIAS
├── DarkCalibration     # LIGHT - DARK
├── FlatCalibration     # LIGHT / FLAT
└── DefectCorrection
```

---

## 11. Defect Map (`SensorDefectMap`)

Clasificación por píxel: `HOT`, `DEAD`, `STUCK`, `COLUMN`, `ROW`, con nivel de confianza.

---

## 12. Stacking Engine

Métodos soportados a largo plazo: MEAN, MEDIAN, WEIGHTED_MEAN, SIGMA_CLIPPING, MIN, MAX.
**MVP:** Mean, Median, Sigma Clip.

---

## 13. Frame Quality Analyzer

Antes de apilar, cada frame recibe un score basado en: SNR, FWHM, número de estrellas, excentricidad, nivel/ruido de fondo, trailing, saturación, movimiento. Los frames de baja calidad se rechazan automáticamente.

---

## 14. Registro astronómico

```
Star Detection → Star Matching → Transformation → Image Registration
```

Transformaciones soportadas: Translation, Rotation, Scale, Affine, Projective.

---

## 15. Rotación terrestre

El movimiento aparente de las estrellas respecto al sensor (por la rotación terrestre) se modela explícitamente usando timestamp, GPS, orientación y RA/DEC, y se refina con el análisis real de posiciones estelares.

---

## 16. Astrometría (`AstrometryEngine`)

Funciones: `detectStars()`, `solveField()`, `calculatePlateScale()`, `calculateOrientation()`.
Resultado: RA/DEC del centro, FOV (ancho/alto), arcsec/píxel, ángulo de rotación.

---

## 17. Catálogos

La identificación de objetos se basa en catálogos astronómicos reales, no en reconocimiento visual por IA:

```
Imagen → Astrometry → RA/DEC → Catalog Search → Candidate Objects
```

Catálogos: estelares, Messier, NGC, IC, efemérides planetarias, satélites.

---

## 18. Detección de estrellas

```
RAW → Calibration → Background estimation → Threshold
    → Connected components → Centroid → PSF/FWHM
```

Cada estrella: x, y, flux, SNR, FWHM, excentricidad, RA, DEC.

---

## 19. Detección de objetos extendidos

Para nebulosas y galaxias: `Background subtraction → Noise estimation → Extended-source detection → Segmentation → Catalog matching`.

---

## 20. Objetos móviles (`MovingObjectEngine`)

Comparación temporal entre frames para detectar tracks de: SATELLITE, AIRCRAFT, METEOR, ASTEROID, UNKNOWN.

---

## 21. Análisis de trayectoria

Por objeto: startTime, endTime, startX/Y, endX/Y, angularVelocity, direction, brightness, y eventualmente trayectoria en RA/DEC.

---

## 22. Contaminación lumínica (`LightPollutionEngine`)

```
Image → Star mask → Object mask → Background model → Gradient → Correction
```

No es un simple oscurecido de la imagen, sino un modelado y corrección del gradiente de fondo.

---

## 23. Noise Engine

Tipos: Gaussian, Poisson, Read Noise, Dark Current, Fixed Pattern, Hot Pixel.
Métodos: Median, Sigma Clip, Wavelet, Temporal Filtering, Spatial Filtering.

---

## 24. Procesamiento destructivo vs científico

Dos modos claramente diferenciados:

- **Scientific:** sin alucinación de IA, sin sharpening agresivo, sin estrellas falsas, sin detalle sintético.
- **Visualization:** permite sharpening, contraste, color, HDR, reducción de ruido, deconvolución — pero el resultado se etiqueta siempre como *Visualization*, nunca como *Scientific data*.

---

## 25. Exportación FITS

Headers mínimos: `DATE-OBS`, `EXPTIME`, `GAIN`, `ISO`, `RA`, `DEC`, `CTYPE1/2`, `CRPIX1/2`, `CRVAL1/2`, `CDELT1/2`.

---

## 26. Base de datos local

Entidades: Observation, Camera, Sensor, Frame, Calibration, ProcessingPipeline, DetectedObject, AstrometricSolution.

```
Observation
  ├── Frames
  ├── CalibrationFrames
  ├── ProcessingPipeline
  ├── AstrometricSolution
  └── DetectedObjects
```

---

## 27. Processing Pipeline

```
RAW → Debayer → Dark correction → Flat correction → Defect correction
    → Star registration → Quality filtering → Sigma clipping
    → Background correction → Astrometry → Object detection → FITS
```

El pipeline completo (incluyendo parámetros) se guarda para permitir reproducir exactamente el procesamiento.

---

## 28. Automatización — Smart Capture

El usuario define un SNR objetivo y un tiempo máximo de integración; ASTRA captura hasta alcanzar el objetivo y detiene la sesión automáticamente.

---

## 29. Planificador astronómico ("Tonight")

Lista de objetivos ordenados por condiciones: altitud, separación lunar, iluminación lunar, calidad general.

---

## 30. Sistema de plugins

```
ASTRA Core
  ├── CameraPlugin
  ├── CatalogPlugin
  ├── ProcessingPlugin
  ├── DetectorPlugin
  └── ExporterPlugin
```

Permite que terceros desarrollen módulos adicionales.

---

## 31–36. Roadmap de versiones

| Versión | Nombre | Foco | Resultado esperado |
|---|---|---|---|
| 🟢 V0.1 | Foundation | Arquitectura, Kotlin, módulos, storage, sesiones, metadata, logging, modelo de datos | App capaz de crear y administrar sesiones de observación |
| 🟢 V0.2 | Camera | Camera2, preview, exposición, ISO, enfoque, captura RAW/DNG, secuencias | Primera captura RAW funcional |
| 🟢 V0.3 | RAW Engine | Parser DNG, decoding, Bayer pattern, linearización, bit depth, histograma | Datos lineales utilizables por el motor científico |
| 🟢 V0.4 | Calibration | Dark, Bias, Flat, defect maps, hot/dead pixels | RAW calibrado |
| 🟢 V0.5 | Stacking | Alignment, Mean, Median, Sigma clipping, rejection masks | Imagen integrada |
| 🟢 V0.6 | Quality Analysis | SNR, FWHM, star count, trailing, ranking, rechazo automático | El sistema decide qué exposiciones son buenas |
| 🟡 V0.7 | Astrometry | Star detection, plate solving, RA/DEC, escala, orientación | La imagen conoce dónde está mirando |
| 🟡 V0.8 | Astronomy Engine | Sol, Luna, planetas, altitud/azimut, rise/set, crepúsculo | Condiciones astronómicas calculadas |
| 🟡 V0.9 | Object Detection | Estrellas, Messier, NGC, IC, galaxias, nebulosas, cúmulos | Identificación por catálogo |
| 🟡 V1.0 | **Scientific Release** | RAW + Calibration + Stacking + Astrometry + Detection + FITS + pipelines reproducibles | Primera versión científicamente seria |
| V1.1 | Moving Objects | Satélites, aviones, meteoros, asteroides, trayectorias | Detección de objetos móviles |
| V1.2 | Smart Capture | SNR objetivo, exposición/rechazo/calibración/stacking automáticos | Captura autónoma |
| V1.3 | Advanced cameras | USB, cámaras astronómicas, sensores enfriados, binning, ROI, gain, cooling | Soporte hardware avanzado |
| V2.0 | **Observatory** | Integración con montura (GoTo, seguimiento, autofocus, meridian flip), GPS, clima, scheduling automático | Sistema de observatorio automatizado |

---

## 37. Estructura del proyecto

```
astra/
├── app/
├── core/
│   ├── model/
│   ├── metadata/
│   ├── storage/
│   └── logging/
├── camera/
│   ├── api/
│   ├── android/
│   ├── usb/
│   └── astro/
├── raw/
├── calibration/
├── image/
│   ├── processing/
│   ├── registration/
│   └── stacking/
├── astrometry/
├── astronomy/
├── detection/
├── catalog/
├── fits/
├── pipeline/
└── ui/
```

---

## 38. Tecnologías

- **Lenguaje:** Kotlin
- **UI:** Jetpack Compose
- **Almacenamiento:** Room + filesystem
- **Procesamiento pesado:** Kotlin/Java inicialmente, con posibilidad de C++/NDK vía JNI para SIMD/multithreading cuando sea necesario (datasets del orden de 6000×4000×200 RAW pueden ser extremadamente pesados)
- **GPU (futuro):** OpenCL / Vulkan compute para stacking, convoluciones, transforms, denoise, registration, visualización

---

## 39. Requisito de rendimiento

Procesamiento por bloques/chunks, nunca cargar cientos de RAW completos en RAM simultáneamente. Fundamental para teléfonos con RAM limitada.

---

## 40. Principio de reproducibilidad

Cada resultado debe registrar: input, calibración aplicada, algoritmos, parámetros y versión de software utilizada. Ejemplo:

```
Result:
  Algorithm: SigmaClip
  Sigma: 3.0
  Frames: 183
  Rejected: 17
  Calibration: dark_20C, flat_2026_09_08
  Software: ASTRA 0.5.0
```

---

## 41. Separación conceptual: Datos / Procesamiento / Interpretación

```
PIXEL → FLUX → STAR CANDIDATE → ASTROMETRIC MATCH → "Vega"
```

Cada etapa debe poder auditarse de forma independiente.

---

## 42. Primer objetivo real (milestone inicial)

> "ASTRA puede conectarse a una cámara, capturar RAW y crear una Observation Session científicamente trazable."

Orden de construcción:

1. Conexión de cámara + captura RAW + sesión trazable
2. RAW → Calibration → Stacking
3. Astrometry → Catalogs → Detection

Este orden evita construir un motor de procesamiento sobre datos RAW mal interpretados.

---

## Próximo paso

Documento de arquitectura técnica de V0.1: estructura Gradle, módulos, paquetes, interfaces Kotlin (`CameraDevice`, `CameraCapabilities`, `CaptureSettings`, `ObservationSession`, `ImageFrame`, `ImageMetadata`), almacenamiento, sistema de eventos, manejo de errores, logging, permisos Android, flujo de captura RAW, y primera implementación funcional — comenzando por la capa `core` y el sistema de cámaras.
