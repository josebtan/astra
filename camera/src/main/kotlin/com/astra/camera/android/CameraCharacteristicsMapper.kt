package com.astra.camera.android

import com.astra.core.model.CameraCapabilities
import kotlin.math.ceil
import kotlin.math.ln

/**
 * Plain-data snapshot of the `android.hardware.camera2.CameraCharacteristics`
 * fields ASTRA cares about.
 *
 * Deliberately has **no** `android.*` import, so [mapToCapabilities] below
 * is testable on the plain JVM (see CameraCharacteristicsMapperTest) even
 * though this sandbox has no Android runtime. The actual extraction from a
 * real `CameraCharacteristics` object happens in [AndroidCameraDevice],
 * which is not unit-testable here — this split keeps the interesting logic
 * (bit depth from white level, unit conversions, defaults) verifiable
 * while isolating the untestable Android I/O to the smallest surface
 * possible.
 */
data class CameraSensorSpec(
    val sensorWidthPx: Int,
    val sensorHeightPx: Int,
    val pixelSizeMicrons: Double,
    val whiteLevel: Int?,
    val isoRangeMin: Int?,
    val isoRangeMax: Int?,
    val minExposureNanos: Long?,
    val maxExposureNanos: Long?,
    val supportsRaw: Boolean,
    val maxFramesPerSecond: Double? = null
)

/**
 * Converts a raw sensor spec into ASTRA's [CameraCapabilities]. Phone
 * cameras (unlike dedicated astro cameras) don't expose a temperature
 * sensor, cooling, or binning through the public Camera2 API — see the
 * POCO X7 Pro example in docs/ROADMAP.md section 6 — so those are always
 * reported as unsupported here regardless of [spec].
 */
fun mapToCapabilities(spec: CameraSensorSpec): CameraCapabilities {
    val bitDepth = spec.whiteLevel
        ?.let { level -> ceil(ln((level + 1).toDouble()) / ln(2.0)).toInt() }
        ?: 8

    val isoRange = if (spec.isoRangeMin != null && spec.isoRangeMax != null) {
        spec.isoRangeMin..spec.isoRangeMax
    } else null

    val exposureRangeSeconds = if (spec.minExposureNanos != null && spec.maxExposureNanos != null) {
        (spec.minExposureNanos / 1_000_000_000.0)..(spec.maxExposureNanos / 1_000_000_000.0)
    } else {
        0.0..0.0
    }

    return CameraCapabilities(
        sensorWidthPx = spec.sensorWidthPx,
        sensorHeightPx = spec.sensorHeightPx,
        pixelSizeMicrons = spec.pixelSizeMicrons,
        bitDepth = bitDepth,
        supportsRaw = spec.supportsRaw,
        isoRange = isoRange,
        exposureRangeSeconds = exposureRangeSeconds,
        hasTemperatureSensor = false,
        hasCooling = false,
        supportsBinning = false,
        supportsRegionOfInterest = true, // via CaptureRequest.SCALER_CROP_REGION
        maxFramesPerSecond = spec.maxFramesPerSecond
    )
}
