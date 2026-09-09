package com.astra.core.model

/**
 * Full metadata attached to a single captured frame.
 *
 * Astrometric fields (ra/dec/azimuth/altitudeAngle) start out null and are
 * filled in later by the astrometry engine (roadmap section 16) — a frame's
 * metadata is progressively enriched, never overwritten destructively.
 *
 * See roadmap section 8.
 */
data class ImageMetadata(
    // Observation context
    val timestampUtc: String,      // ISO-8601, e.g. "2026-09-08T23:14:02Z"
    val latitude: Double?,
    val longitude: Double?,
    val altitudeMeters: Double?,

    // Equipment
    val cameraModel: String,
    val sensorModel: String?,
    val lensModel: String?,
    val focalLengthMm: Double?,
    val apertureFNumber: Double?,

    // Exposure
    val exposureTimeSeconds: Double,
    val gain: Double?,
    val iso: Int?,
    val temperatureCelsius: Double?,

    // Frame geometry
    val imageWidthPx: Int,
    val imageHeightPx: Int,
    val pixelSizeMicrons: Double?,
    val orientationDegrees: Double?,

    // Astrometric solution (filled in later, see AstrometryEngine)
    val rightAscensionDeg: Double? = null,
    val declinationDeg: Double? = null,
    val azimuthDeg: Double? = null,
    val altitudeAngleDeg: Double? = null
)
