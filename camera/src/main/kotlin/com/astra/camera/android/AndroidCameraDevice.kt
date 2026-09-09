package com.astra.camera.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice as Camera2Device
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import com.astra.core.model.CameraCapabilities
import com.astra.core.model.CameraConnectionState
import com.astra.core.model.CameraDevice
import com.astra.core.model.CameraStatus
import com.astra.core.model.CaptureSettings
import com.astra.core.model.FrameType
import com.astra.core.model.ImageFrame
import com.astra.core.model.ImageMetadata
import com.astra.core.model.RawFormat
import com.astra.core.model.SequenceSettings
import com.astra.core.storage.SessionFileStore
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * [CameraDevice] implementation backed by the phone's own camera via the
 * Camera2 API — the first concrete implementation of the abstraction from
 * roadmap section 5. USB and dedicated astro cameras get their own
 * implementations later, behind this same [CameraDevice] interface.
 *
 * Capture is synchronous from the caller's point of view (matching the
 * [CameraDevice] contract), even though Camera2 itself is callback-based:
 * internally this blocks the calling thread on a [Semaphore] until the RAW
 * image and its capture metadata are both available and written to disk.
 * **Call from a background thread** (e.g. `Dispatchers.IO`), never from the
 * main/UI thread.
 *
 * NOTE: [startPreview]/[stopPreview] are not implemented yet. Camera2
 * preview needs a `Surface` supplied by the UI layer, and there is no `ui`
 * module yet — wiring this up is part of building the capture screen, not
 * part of the V0.1/V0.2 data-and-capture milestone (roadmap section 42).
 *
 * NOT VERIFIABLE ON THE PLAIN JVM: this class needs a real Camera2 HAL
 * (an actual device or emulator with camera support). The parts of the
 * logic that don't need Android (bit-depth / unit conversions) are pulled
 * out into [mapToCapabilities], which *is* unit-tested — see
 * CameraCharacteristicsMapperTest.
 */
class AndroidCameraDevice(
    private val context: Context,
    private val cameraId: String,
    private val fileStore: SessionFileStore,
    private val sessionId: String
) : CameraDevice {

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val characteristics: CameraCharacteristics =
        cameraManager.getCameraCharacteristics(cameraId)

    private var device: Camera2Device? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    @Volatile private var connectionState: CameraConnectionState = CameraConnectionState.DISCONNECTED
    @Volatile private var lastError: String? = null
    @Volatile private var framesCapturedInSequence: Int = 0
    @Volatile private var sequenceActive = false

    override fun getCapabilities(): CameraCapabilities = mapToCapabilities(readSensorSpec())

    override fun connect() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            connectionState = CameraConnectionState.ERROR
            lastError = "CAMERA permission not granted"
            error(lastError!!)
        }

        startBackgroundThread()
        connectionState = CameraConnectionState.CONNECTING

        val openLatch = Semaphore(0)
        cameraManager.openCamera(cameraId, object : Camera2Device.StateCallback() {
            override fun onOpened(cameraDevice: Camera2Device) {
                device = cameraDevice
                connectionState = CameraConnectionState.CONNECTED
                openLatch.release()
            }

            override fun onDisconnected(cameraDevice: Camera2Device) {
                cameraDevice.close()
                device = null
                connectionState = CameraConnectionState.DISCONNECTED
                openLatch.release()
            }

            override fun onError(cameraDevice: Camera2Device, errorCode: Int) {
                cameraDevice.close()
                device = null
                connectionState = CameraConnectionState.ERROR
                lastError = "Camera2 error code $errorCode"
                openLatch.release()
            }
        }, backgroundHandler)

        if (!openLatch.tryAcquire(10, TimeUnit.SECONDS)) {
            connectionState = CameraConnectionState.ERROR
            lastError = "Timed out opening camera $cameraId"
        }
        if (connectionState != CameraConnectionState.CONNECTED) {
            error(lastError ?: "Failed to connect to camera $cameraId")
        }
    }

    override fun disconnect() {
        captureSession?.close()
        captureSession = null
        device?.close()
        device = null
        connectionState = CameraConnectionState.DISCONNECTED
        stopBackgroundThread()
    }

    override fun startPreview() {
        throw UnsupportedOperationException(
            "Preview needs a Surface from the UI layer, which doesn't exist yet."
        )
    }

    override fun stopPreview() {
        // No-op until startPreview() is implemented against a real Surface.
    }

    override fun capture(settings: CaptureSettings): ImageFrame {
        val cameraDevice = device ?: error("Camera not connected - call connect() first")
        check(settings.rawFormat == RawFormat.DNG) {
            "AndroidCameraDevice currently only supports RawFormat.DNG"
        }

        val spec = readSensorSpec()
        val imageReader = ImageReader.newInstance(
            spec.sensorWidthPx, spec.sensorHeightPx, ImageFormat.RAW_SENSOR, /* maxImages = */ 2
        )

        var pendingImage: Image? = null
        var pendingResult: TotalCaptureResult? = null
        var resultFrame: ImageFrame? = null
        val captureLatch = Semaphore(0)

        fun finishIfReady() {
            val image = pendingImage
            val result = pendingResult
            if (image != null && result != null && resultFrame == null) {
                resultFrame = writeDngAndBuildFrame(image, result, settings)
                image.close()
                captureLatch.release()
            }
        }

        imageReader.setOnImageAvailableListener({ reader ->
            pendingImage = reader.acquireLatestImage()
            finishIfReady()
        }, backgroundHandler)

        val sessionLatch = Semaphore(0)
        cameraDevice.createCaptureSession(
            listOf(imageReader.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    sessionLatch.release()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    lastError = "Failed to configure capture session"
                    sessionLatch.release()
                }
            },
            backgroundHandler
        )
        if (!sessionLatch.tryAcquire(10, TimeUnit.SECONDS) || captureSession == null) {
            imageReader.close()
            error(lastError ?: "Timed out configuring capture session")
        }

        val requestBuilder = cameraDevice.createCaptureRequest(Camera2Device.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(imageReader.surface)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            set(CaptureRequest.SENSOR_EXPOSURE_TIME, (settings.exposureTimeSeconds * 1_000_000_000).toLong())
            settings.iso?.let { set(CaptureRequest.SENSOR_SENSITIVITY, it) }
        }

        captureSession?.capture(
            requestBuilder.build(),
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    pendingResult = result
                    finishIfReady()
                }

                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: CaptureFailure
                ) {
                    lastError = "Capture failed, reason=${failure.reason}"
                    captureLatch.release()
                }
            },
            backgroundHandler
        )

        val timeoutSeconds = settings.exposureTimeSeconds.toLong() + 15
        if (!captureLatch.tryAcquire(timeoutSeconds, TimeUnit.SECONDS)) {
            imageReader.close()
            error(lastError ?: "Timed out waiting for capture")
        }

        imageReader.close()
        return resultFrame ?: error(lastError ?: "Capture failed: no image produced")
    }

    override fun startSequence(settings: SequenceSettings) {
        sequenceActive = true
        framesCapturedInSequence = 0
        repeat(settings.frameCount) {
            if (!sequenceActive) return
            capture(settings.captureSettings)
            framesCapturedInSequence++
            if (settings.intervalSeconds > 0) {
                Thread.sleep((settings.intervalSeconds * 1000).toLong())
            }
        }
        sequenceActive = false
    }

    override fun stopSequence() {
        sequenceActive = false
    }

    override fun getStatus(): CameraStatus = CameraStatus(
        connectionState = connectionState,
        isCapturing = sequenceActive,
        currentTemperatureCelsius = null, // not exposed by phone cameras
        framesCapturedInSequence = framesCapturedInSequence,
        lastErrorMessage = lastError
    )

    private fun writeDngAndBuildFrame(
        image: Image,
        captureResult: TotalCaptureResult,
        settings: CaptureSettings
    ): ImageFrame {
        val fileName = "IMG_${System.currentTimeMillis()}.DNG"
        val outputFile = fileStore.newRawFile(fileName)

        DngCreator(characteristics, captureResult).use { dngCreator ->
            FileOutputStream(outputFile).use { out ->
                dngCreator.writeImage(out, image)
            }
        }

        val spec = readSensorSpec()
        val metadata = ImageMetadata(
            timestampUtc = Instant.now().toString(),
            latitude = null,
            longitude = null,
            altitudeMeters = null,
            cameraModel = Build.MODEL,
            sensorModel = cameraId,
            lensModel = null,
            focalLengthMm = characteristics
                .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.firstOrNull()
                ?.toDouble(),
            apertureFNumber = characteristics
                .get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                ?.firstOrNull()
                ?.toDouble(),
            exposureTimeSeconds = settings.exposureTimeSeconds,
            gain = settings.gain,
            iso = settings.iso,
            temperatureCelsius = null,
            imageWidthPx = image.width,
            imageHeightPx = image.height,
            pixelSizeMicrons = spec.pixelSizeMicrons,
            orientationDegrees = null
        )

        return ImageFrame(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            frameType = FrameType.LIGHT,
            filePath = outputFile.absolutePath,
            metadata = metadata
        )
    }

    private fun readSensorSpec(): CameraSensorSpec {
        val pixelArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val pixelSizeMicrons = if (pixelArraySize != null && physicalSize != null && pixelArraySize.width > 0) {
            (physicalSize.width / pixelArraySize.width) * 1000.0
        } else {
            0.0
        }

        val whiteLevel = characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL)
        val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val availableCapabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        val supportsRaw = availableCapabilities
            ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true

        return CameraSensorSpec(
            sensorWidthPx = pixelArraySize?.width ?: 0,
            sensorHeightPx = pixelArraySize?.height ?: 0,
            pixelSizeMicrons = pixelSizeMicrons,
            whiteLevel = whiteLevel,
            isoRangeMin = isoRange?.lower,
            isoRangeMax = isoRange?.upper,
            minExposureNanos = exposureRange?.lower,
            maxExposureNanos = exposureRange?.upper,
            supportsRaw = supportsRaw,
            maxFramesPerSecond = null
        )
    }

    private fun startBackgroundThread() {
        val thread = HandlerThread("AstraCameraThread").also { it.start() }
        backgroundThread = thread
        backgroundHandler = Handler(thread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
        backgroundHandler = null
    }

    companion object {
        /** Lists camera IDs available on this device, e.g. for a camera picker in the UI. */
        fun listAvailableCameraIds(context: Context): List<String> {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            return manager.cameraIdList.toList()
        }
    }
}
