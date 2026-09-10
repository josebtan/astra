package com.astra.core.model

import android.view.Surface

/**
 * Common abstraction over every image source ASTRA can capture from:
 * internal phone camera, USB camera, dedicated astro camera, DSLR/mirrorless.
 *
 * The rest of the app (UI, sessions, RAW engine) talks only to this
 * interface and to [CameraCapabilities] — it never needs to know which
 * concrete implementation (AndroidCameraDevice, UsbCameraDevice,
 * AstroCameraDevice, ExternalCameraDevice) it is using.
 *
 * See roadmap section 5.
 */
interface CameraDevice {

    fun getCapabilities(): CameraCapabilities

    fun connect()

    fun disconnect()

    /** Starts a live preview rendered into [surface] (e.g. from a SurfaceView in the UI). */
    fun startPreview(surface: Surface)

    fun stopPreview()

    /** Captures a single frame and returns the resulting [ImageFrame]. */
    fun capture(settings: CaptureSettings): ImageFrame

    fun startSequence(settings: SequenceSettings)

    fun stopSequence()

    fun getStatus(): CameraStatus
}
