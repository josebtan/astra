package com.astra.core.model

enum class CameraConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * Live status of a [CameraDevice], polled or observed by the UI and by the
 * capture pipeline (e.g. to know when a sequence is still running).
 */
data class CameraStatus(
    val connectionState: CameraConnectionState,
    val isCapturing: Boolean,
    val currentTemperatureCelsius: Double? = null,
    val framesCapturedInSequence: Int = 0,
    val lastErrorMessage: String? = null
)
