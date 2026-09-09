package com.astra.core.model

/**
 * The requested region of interest, in sensor pixel coordinates.
 * Null means "full frame".
 */
data class RegionOfInterest(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

enum class RawFormat { DNG, NATIVE_RAW, FITS, TIFF }

/**
 * Parameters for a single exposure. Passed to [CameraDevice.capture].
 * See roadmap section 5 (CameraDevice interface) and section 9 (session example).
 */
data class CaptureSettings(
    val exposureTimeSeconds: Double,
    val iso: Int? = null,
    val gain: Double? = null,
    val binning: Int = 1,
    val regionOfInterest: RegionOfInterest? = null,
    val rawFormat: RawFormat = RawFormat.DNG
)

/**
 * Parameters for an automated sequence of exposures (a burst of frames for
 * a single [ObservationSession]). Passed to [CameraDevice.startSequence].
 */
data class SequenceSettings(
    val captureSettings: CaptureSettings,
    val frameCount: Int,
    val intervalSeconds: Double = 0.0
)
