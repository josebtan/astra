# ASTRA — Especificación de Requerimientos (v0.1)

Ver `ROADMAP.md` para el detalle completo. Este documento resume los requerimientos funcionales y no funcionales para referencia rápida.

## Requerimientos funcionales

- **RF-01** Abstracción de cámara (`CameraDevice`) para: cámara Android, USB, astronómica dedicada, DSLR/mirrorless.
- **RF-02** Captura en RAW/DNG sin pérdida ni modificación del archivo original.
- **RF-03** Motor RAW capaz de decodificar DNG/RAW/FITS/TIFF a una representación lineal (`LinearImage`).
- **RF-04** Gestión de sesiones de observación (`ObservationSession`) con metadata completa y reproducible.
- **RF-05** Motor de calibración: bias, dark, flat, corrección de defectos (hot/dead/stuck pixels, columnas/filas).
- **RF-06** Motor de stacking: mean, median, weighted mean, sigma clipping, min/max.
- **RF-07** Analizador de calidad de frame (SNR, FWHM, nº estrellas, excentricidad, fondo, trailing, saturación) con rechazo automático.
- **RF-08** Registro/alineación de imágenes (detección y matching de estrellas, transformaciones translation/rotation/scale/affine/projective).
- **RF-09** Corrección de rotación terrestre a partir de timestamp, GPS, orientación y RA/DEC.
- **RF-10** Motor de astrometría: plate solving, RA/DEC del centro, escala de píxel, orientación, FOV.
- **RF-11** Motor de catálogos (estelar, Messier, NGC, IC, efemérides, satélites) para identificación de objetos vía RA/DEC.
- **RF-12** Detección de estrellas y objetos extendidos (nebulosas, galaxias).
- **RF-13** Detección y seguimiento de objetos móviles (satélites, aviones, meteoros, asteroides) con análisis de trayectoria.
- **RF-14** Corrección de contaminación lumínica mediante modelado de gradiente de fondo.
- **RF-15** Motor de reducción de ruido (gaussiano, poisson, read noise, dark current, fixed pattern, hot pixel).
- **RF-16** Dos modos de procesamiento claramente etiquetados: *Scientific* (sin alteraciones artificiales) y *Visualization* (sharpening, HDR, color, etc.).
- **RF-17** Exportación a FITS con headers astronómicos estándar (WCS: CTYPE, CRPIX, CRVAL, CDELT, etc.).
- **RF-18** Base de datos local (Observation, Camera, Sensor, Frame, Calibration, ProcessingPipeline, DetectedObject, AstrometricSolution).
- **RF-19** Pipeline de procesamiento persistente y reproducible (mismos parámetros → mismo resultado).
- **RF-20** (futuro) Smart Capture: captura automática hasta alcanzar un SNR objetivo.
- **RF-21** (futuro) Planificador astronómico ("Tonight") con objetivos ordenados por condiciones observacionales.
- **RF-22** (futuro) Sistema de plugins para cámaras, catálogos, procesamiento, detección y exportación.
- **RF-23** (futuro, V2.0) Integración con monturas motorizadas: GoTo, seguimiento, autofocus, meridian flip, scheduling automático.

## Requerimientos no funcionales

- **RNF-01** Los archivos RAW originales nunca deben sobrescribirse ni modificarse in-place.
- **RNF-02** Procesamiento por bloques/chunks; no cargar cientos de RAW completos en memoria simultáneamente (soporte a dispositivos con RAM limitada).
- **RNF-03** Separación estricta de capas: captura, datos, calibración, registro, stacking, procesamiento, análisis, identificación, resultados.
- **RNF-04** Toda transformación matemática debe ser trazable (input + calibración + algoritmo + parámetros + versión de software).
- **RNF-05** Arquitectura modular en Kotlin, con posibilidad de delegar procesamiento pesado a C/C++ vía JNI (SIMD/multithreading).
- **RNF-06** Posible aceleración por GPU (OpenCL/Vulkan compute) en versiones futuras.
- **RNF-07** UI en Jetpack Compose, adaptada dinámicamente a las capacidades reportadas por cada `CameraDevice`.
- **RNF-08** Almacenamiento con Room + sistema de archivos estructurado (`RAW/`, `WORK/`, `RESULTS/`).

## Estructura de módulos (mapea 1:1 con las carpetas del repositorio)

```
core/  camera/  raw/  calibration/  image/  astrometry/
astronomy/  detection/  catalog/  fits/  pipeline/  ui/  app/
```

## Primer milestone (MVP funcional)

> ASTRA puede conectarse a una cámara, capturar RAW y crear una Observation Session científicamente trazable.

Orden de construcción obligatorio: **Captura → Calibración/Stacking → Astrometría/Catálogos/Detección**.
