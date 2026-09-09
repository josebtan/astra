package com.astra.core.model

/**
 * Role of a frame within calibration/processing (roadmap section 10).
 */
enum class FrameType { LIGHT, DARK, BIAS, FLAT }

/**
 * A single physical file produced during an [ObservationSession].
 *
 * IMPORTANT (roadmap section 4 — "los datos son sagrados"): [filePath]
 * for a RAW frame must always point into the session's RAW/ directory and
 * must never be overwritten in place. Any processed derivative gets its
 * own [ImageFrame] pointing into WORK/ or RESULTS/, referencing this frame
 * via [derivedFromFrameId].
 */
data class ImageFrame(
    val id: String,
    val sessionId: String,
    val frameType: FrameType,
    val filePath: String,
    val metadata: ImageMetadata,
    val derivedFromFrameId: String? = null,
    val qualityScore: Double? = null // set by the Frame Quality Analyzer, section 13
)
