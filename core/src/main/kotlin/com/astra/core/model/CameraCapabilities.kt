package com.astra.core.model

/**
 * Describes what a given [CameraDevice] is capable of.
 *
 * The UI and capture pipeline must adapt to whatever a camera reports here
 * instead of assuming a fixed feature set — see ASTRA roadmap, section 6.
 */
data class CameraCapabilities(
    val sensorWidthPx: Int,
    val sensorHeightPx: Int,
    val pixelSizeMicrons: Double,
    val bitDepth: Int,
    val supportsRaw: Boolean,
    val isoRange: IntRange?,
    val exposureRangeSeconds: ClosedRange<Double>,
    val hasTemperatureSensor: Boolean,
    val hasCooling: Boolean,
    val supportsBinning: Boolean,
    val supportsRegionOfInterest: Boolean,
    val maxFramesPerSecond: Double?
)
