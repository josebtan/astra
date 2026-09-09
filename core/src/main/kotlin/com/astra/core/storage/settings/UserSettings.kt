package com.astra.core.storage.settings

import com.astra.core.model.RawFormat

enum class AppTheme { SYSTEM, LIGHT, DARK }

/**
 * Everything the user can configure and that ASTRA must remember between
 * launches. This is deliberately separate from [com.astra.core.model.ObservationSession]:
 * settings are app-wide preferences, sessions are per-observation data.
 *
 * Anything added here later (e.g. a new default capture parameter) should
 * get a default value so old stored settings still deserialize cleanly.
 */
data class UserSettings(
    // Storage
    val storageRootPath: String = "/storage/emulated/0/Astra",

    // Capture defaults, pre-filled into CaptureSettings for a new session
    val defaultRawFormat: RawFormat = RawFormat.DNG,
    val defaultIso: Int? = null,
    val defaultExposureSeconds: Double? = null,
    val defaultBinning: Int = 1,

    // Last used equipment, so the UI can pre-select it next time
    val lastUsedCameraId: String? = null,

    // UI preferences
    val theme: AppTheme = AppTheme.SYSTEM,
    val useMetricUnits: Boolean = true,

    // Processing preference: "Scientific" vs "Visualization" mode (roadmap section 24)
    val defaultScientificMode: Boolean = true
)
