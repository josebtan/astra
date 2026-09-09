package com.astra.core.model

enum class SessionStatus { CREATED, CAPTURING, CAPTURE_COMPLETE, PROCESSING, COMPLETED, ABORTED }

/**
 * A fully traceable astronomical observation, per roadmap section 9.
 *
 * Example from the spec:
 * ```
 * Session:      M42_2026_09_08
 * Camera:       POCO X7 Pro
 * Target:       M42
 * ISO:          1600
 * Exposure:     15s
 * Frames:       200
 * Calibration:  Dark + Flat
 * Processing:   Sigma Clip
 * Integration:  50m
 * ```
 *
 * This is the root object of ASTRA's V0.1 milestone: "ASTRA puede
 * conectarse a una cámara, capturar RAW y crear una Observation Session
 * científicamente trazable" (roadmap section 42). Everything else
 * (calibration, stacking, astrometry, detection) hangs off a session.
 */
data class ObservationSession(
    val id: String,
    val name: String,
    val createdAtUtc: String,
    val cameraModel: String,
    val target: String? = null,
    val status: SessionStatus = SessionStatus.CREATED,
    val frameIds: List<String> = emptyList(),
    val calibrationDescription: String? = null,   // e.g. "Dark + Flat"
    val processingPipelineId: String? = null,
    val totalIntegrationSeconds: Double = 0.0
)
