# ASTRA

Astronomical Science & Tracking Research Application — plataforma móvil (Android) para adquisición, calibración, procesamiento y análisis científico de imágenes astronómicas.

![Android CI](https://github.com/josebtan/astra/actions/workflows/android-ci.yml/badge.svg)

Este repositorio contiene la documentación fundacional del proyecto y el desarrollo en curso:

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

## Compilar y probar

CI en GitHub Actions (`.github/workflows/android-ci.yml`) corre en cada push/PR a `main`: tests unitarios de `core` y build del APK debug.

Localmente, con Android Studio:
```bash
./gradlew :core:testDebugUnitTest
./gradlew :app:assembleDebug
```

Verificación rápida sin Android SDK (útil en entornos sin acceso a Google Maven — ver `docs/PROGRESS.md`):
```bash
./scripts/verify-core-jvm.sh
```

## Estado

🟢 Fase actual: **V0.1 — Foundation** (pendiente de implementación).

## Próximo paso

Documento de arquitectura técnica de V0.1 (estructura Gradle, interfaces Kotlin, modelos de datos) y primera implementación de la capa `core` + sistema de cámaras.
