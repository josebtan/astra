# ASTRA

Astronomical Science & Tracking Research Application — plataforma móvil (Android) para adquisición, calibración, procesamiento y análisis científico de imágenes astronómicas.

Este repositorio está en blanco (esqueleto de módulos, sin código todavía). Contiene la documentación fundacional del proyecto:

- [`docs/ROADMAP.md`](docs/ROADMAP.md) — visión, arquitectura completa y roadmap de versiones (V0.1 → V2.0).
- [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) — especificación de requerimientos funcionales y no funcionales.

## Estructura

```
astra/
├── app/            # módulo de la aplicación Android
├── core/           # modelo de datos, metadata, storage, logging
├── camera/         # abstracción de cámaras (android, usb, astro)
├── raw/            # motor de decodificación RAW/DNG/FITS
├── calibration/    # bias, dark, flat, defect maps
├── image/          # procesamiento, registro, stacking
├── astrometry/     # plate solving, RA/DEC, escala de píxel
├── astronomy/      # sol, luna, planetas, condiciones observacionales
├── detection/      # detección de estrellas, objetos extendidos y móviles
├── catalog/        # catálogos astronómicos (estrellas, Messier, NGC, IC...)
├── fits/           # exportación/importación FITS
├── pipeline/       # pipelines de procesamiento reproducibles
├── ui/             # Jetpack Compose UI
└── docs/           # documentación del proyecto
```

## Estado

🟢 Fase actual: **V0.1 — Foundation** (pendiente de implementación).

## Próximo paso

Documento de arquitectura técnica de V0.1 (estructura Gradle, interfaces Kotlin, modelos de datos) y primera implementación de la capa `core` + sistema de cámaras.
