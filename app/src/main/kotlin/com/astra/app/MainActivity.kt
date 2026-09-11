package com.astra.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.room.Room
import com.astra.calibration.CalibrationEngine
import com.astra.camera.android.AndroidCameraDevice
import com.astra.core.model.CaptureSettings
import com.astra.core.model.FrameType
import com.astra.core.model.ImageFrame
import com.astra.core.model.ImageMetadata
import com.astra.core.model.LinearImage
import com.astra.core.model.ObservationSession
import com.astra.core.storage.SessionFileStore
import com.astra.core.storage.SessionRepository
import com.astra.core.storage.db.AstraDatabase
import com.astra.core.storage.db.RoomSessionRepository
import com.astra.core.storage.settings.DataStoreSettingsRepository
import com.astra.core.storage.settings.SettingsRepository
import com.astra.raw.DngTiffReader
import com.astra.stacking.StackingEngine
import com.astra.stacking.StackingMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Temporary screen, **not** the real UI module (roadmap section 37 still
 * has `ui/` as a future module, and the roadmap never specifies a visual
 * design for it - there isn't one to follow yet).
 *
 * Two things live here on purpose:
 * 1. A roadmap-progress checklist (what's done/in progress/pending).
 * 2. A "test lab": one button per already-implemented feature (camera
 *    capabilities, RAW capture, DNG decoding, automatic calibration,
 *    session persistence, settings persistence), each running the real
 *    code and printing the real result - so functionality can actually be
 *    exercised and checked against expectations before there's a proper
 *    capture UI to do it through.
 *
 * This should be replaced wholesale once the real UI module exists.
 */
class MainActivity : Activity() {

    private data class Milestone(val label: String, val status: Status)
    private enum class Status(val marker: String, val color: Int) {
        DONE("✓", Color.parseColor("#6CFFB0")),
        IN_PROGRESS("…", Color.parseColor("#FFD166")),
        PENDING("—", Color.parseColor("#7A8199"))
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val sessionId = "test-session-${System.currentTimeMillis()}"
    private val fileStore: SessionFileStore by lazy {
        SessionFileStore(File(filesDir, "sessions"), sessionId)
    }
    private val database: AstraDatabase by lazy {
        Room.databaseBuilder(applicationContext, AstraDatabase::class.java, AstraDatabase.DATABASE_NAME).build()
    }
    private val sessionRepository: SessionRepository by lazy {
        RoomSessionRepository(database.observationSessionDao(), database.imageFrameDao())
    }
    private val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(applicationContext, appScope)
    }

    private var lastCapturedFrame: ImageFrame? = null

    private lateinit var outputView: TextView
    private val outputBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
    }

    override fun onDestroy() {
        super.onDestroy()
        appScope.cancel()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            appendOutput(
                if (granted) "Permiso de cámara concedido. Vuelve a presionar \"Capturar frame de prueba\"."
                else "Permiso de cámara denegado - sin él no se puede probar la captura."
            )
        }
    }

    // ---------------------------------------------------------------
    // UI construction (plain views, no XML - consistent with the rest
    // of this temporary screen, and avoids adding a Compose dependency
    // just for a test harness).
    // ---------------------------------------------------------------

    private fun buildScreen(): ScrollView {
        val backgroundColor = Color.parseColor("#0B1226")
        val textColor = Color.parseColor("#F5F7FF")
        val mutedColor = Color.parseColor("#A9B1C9")
        val padding = dp(24)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(backgroundColor)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        root.addView(TextView(this).apply {
            text = "ASTRA"
            setTextColor(textColor)
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Astronomical Science & Tracking Research Application"
            setTextColor(mutedColor)
            textSize = 14f
            setPadding(0, dp(4), 0, dp(24))
        })

        root.addView(sectionTitle("Progreso del roadmap", textColor))
        milestones().forEach { root.addView(milestoneRow(it, textColor)) }

        root.addView(sectionTitle("Captura real", textColor).apply {
            setPadding(0, dp(28), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "Vista previa en vivo, captura real de LIGHT/DARK/BIAS/FLAT, y el pipeline de " +
                "calibración + alineación + stacking corriendo sobre esos frames reales."
            setTextColor(mutedColor)
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        })
        root.addView(labButton("Abrir pantalla de captura en vivo") {
            startActivity(Intent(this, CaptureActivity::class.java))
        })

        root.addView(sectionTitle("Laboratorio de pruebas", textColor).apply {
            setPadding(0, dp(28), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "Cada botón ejecuta la función real ya implementada y muestra el resultado real abajo - " +
                "no es una simulación."
            setTextColor(mutedColor)
            textSize = 12f
            setPadding(0, 0, 0, dp(12))
        })

        root.addView(labButton("Ver capacidades de cámara") {
            runInBackground("Ver capacidades de cámara") { cameraCapabilitiesReport() }
        })
        root.addView(labButton("Capturar frame de prueba (1s, ISO 400)") {
            onCaptureButtonPressed()
        })
        root.addView(labButton("Decodificar último DNG capturado") {
            runInBackground("Decodificar último DNG") { decodeLastCaptureReport() }
        })
        root.addView(labButton("Autotest de calibración (datos sintéticos)") {
            runInBackground("Autotest de calibración") { calibrationAutotestReport() }
        })
        root.addView(labButton("Autotest de stacking (datos sintéticos)") {
            runInBackground("Autotest de stacking") { stackingAutotestReport() }
        })
        root.addView(labButton("Crear sesión de prueba y releerla (Room)") {
            runInBackground("Sesión + persistencia") { sessionPersistenceReport() }
        })
        root.addView(labButton("Probar ajustes persistentes (DataStore)") {
            runInBackground("Ajustes persistentes") { settingsPersistenceReport() }
        })

        root.addView(sectionTitle("Resultado", textColor).apply {
            setPadding(0, dp(20), 0, dp(8))
        })
        outputView = TextView(this).apply {
            text = "Presiona cualquier botón de arriba para ver el resultado aquí."
            setTextColor(textColor)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.parseColor("#141B33"))
        }
        root.addView(outputView)

        return ScrollView(this).apply {
            setBackgroundColor(backgroundColor)
            addView(root)
        }
    }

    private fun sectionTitle(text: String, color: Int) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun labButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        }
    }

    private fun milestoneRow(milestone: Milestone, textColor: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))

            addView(TextView(this@MainActivity).apply {
                text = milestone.status.marker
                setTextColor(milestone.status.color)
                typeface = Typeface.DEFAULT_BOLD
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(this@MainActivity).apply {
                text = milestone.label
                setTextColor(textColor)
                textSize = 15f
            })
        }

    private fun milestones(): List<Milestone> = listOf(
        Milestone("V0.1 Foundation — modelo de datos, ajustes, sesiones", Status.DONE),
        Milestone("V0.2 Camera — captura RAW real (solo cámara del teléfono)", Status.DONE),
        Milestone("V0.3 RAW Engine — decodificación DNG a LinearImage", Status.DONE),
        Milestone("V0.4 Calibration — bias/dark/flat + corrección de defectos", Status.DONE),
        Milestone("V0.5 Stacking — Mean/Median/Sigma Clip", Status.DONE),
        Milestone("V0.6 Quality Analysis", Status.PENDING),
        Milestone("V0.7 Astrometry", Status.PENDING),
        Milestone("V0.8 Astronomy Engine", Status.PENDING),
        Milestone("V0.9 Object Detection", Status.PENDING),
        Milestone("V1.0 Scientific Release", Status.PENDING)
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------
    // Test lab actions
    // ---------------------------------------------------------------

    private fun appendOutput(text: String) {
        runOnUiThread {
            outputBuilder.insert(0, text.trimEnd() + "\n\n")
            outputView.text = outputBuilder.toString()
        }
    }

    private fun runInBackground(action: String, block: () -> String) {
        appendOutput("▶ $action…")
        Thread {
            val result = try {
                block()
            } catch (e: Exception) {
                "✗ Error en \"$action\": ${e.javaClass.simpleName}: ${e.message}"
            }
            appendOutput("$action:\n$result")
        }.start()
    }

    private fun onCaptureButtonPressed() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            appendOutput("Solicitando permiso de cámara - vuelve a presionar el botón después de aceptarlo.")
            return
        }
        runInBackground("Capturar frame de prueba") { performTestCapture() }
    }

    private fun cameraCapabilitiesReport(): String {
        val ids = AndroidCameraDevice.listAvailableCameraIds(this)
        if (ids.isEmpty()) return "No se detectaron cámaras en este dispositivo."

        val cameraId = ids.first()
        val device = AndroidCameraDevice(this, cameraId, fileStore, sessionId)
        val caps = device.getCapabilities()

        return buildString {
            appendLine("Cámaras disponibles: $ids (usando id=$cameraId)")
            appendLine("Resolución sensor: ${caps.sensorWidthPx} x ${caps.sensorHeightPx}")
            appendLine("Tamaño de píxel: ${caps.pixelSizeMicrons} µm")
            appendLine("Bit depth: ${caps.bitDepth}")
            appendLine("Soporta RAW_SENSOR: ${caps.supportsRaw}")
            appendLine("Rango ISO: ${caps.isoRange ?: "no reportado"}")
            appendLine(
                "Rango exposición: ${caps.exposureRangeSeconds.start}s - ${caps.exposureRangeSeconds.endInclusive}s"
            )
        }
    }

    private fun performTestCapture(): String {
        val ids = AndroidCameraDevice.listAvailableCameraIds(this)
        if (ids.isEmpty()) return "No se detectaron cámaras."

        val cameraId = ids.first()
        val device = AndroidCameraDevice(this, cameraId, fileStore, sessionId)

        if (!device.getCapabilities().supportsRaw) {
            return "La cámara $cameraId no reporta soporte RAW_SENSOR (CameraCharacteristics) - " +
                "no se puede capturar DNG en este dispositivo con la implementación actual."
        }

        device.connect()
        try {
            val frame = device.capture(CaptureSettings(exposureTimeSeconds = 1.0, iso = 400))
            lastCapturedFrame = frame
            val file = File(frame.filePath)
            return buildString {
                appendLine("Captura OK.")
                appendLine("Archivo: ${frame.filePath}")
                appendLine("Tamaño en disco: ${file.length()} bytes")
                appendLine("Resolución: ${frame.metadata.imageWidthPx} x ${frame.metadata.imageHeightPx}")
                appendLine("ISO: ${frame.metadata.iso}, exposición: ${frame.metadata.exposureTimeSeconds}s")
            }
        } finally {
            device.disconnect()
        }
    }

    private fun decodeLastCaptureReport(): String {
        val frame = lastCapturedFrame
            ?: return "Primero presiona \"Capturar frame de prueba\" - todavía no hay ningún DNG que decodificar."

        val image = DngTiffReader(File(frame.filePath)).readLinearImage()
        val min = image.data.min()
        val max = image.data.max()
        val mean = image.data.average()

        return buildString {
            appendLine("Decodificado OK.")
            appendLine("Dimensiones: ${image.width} x ${image.height}, canales=${image.channels}, bitDepth=${image.bitDepth}")
            appendLine("Valores lineales [0,1]: min=$min max=$max media=$mean")
        }
    }

    private fun calibrationAutotestReport(): String {
        fun uniform(value: Float) = LinearImage(4, 4, 1, 16, FloatArray(16) { value })

        val light = uniform(0.6f)
        val bias = listOf(uniform(0.1f), uniform(0.1f))
        // Small jitter + one clear hot pixel, same pattern proven in
        // CalibrationEngineTest - a perfectly uniform dark would hide the
        // hot pixel from the median/MAD statistic in a grid this small.
        val darkBase = floatArrayOf(
            0.150f, 0.151f, 0.149f, 0.1505f,
            0.1495f, 0.9f /* hot pixel */, 0.150f, 0.151f,
            0.149f, 0.1505f, 0.1495f, 0.150f,
            0.151f, 0.149f, 0.1505f, 0.1495f
        )
        val dark = listOf(LinearImage(4, 4, 1, 16, darkBase))
        val flat = listOf(uniform(0.8f), uniform(0.8f))

        val result = CalibrationEngine.calibrate(light, bias, dark, flat)

        return buildString {
            append(result.report.toUserMessage())
            appendLine("Media de señal antes: ${result.report.meanSignalBefore}")
            appendLine("Media de señal después: ${result.report.meanSignalAfter}")
        }
    }

    private fun stackingAutotestReport(): String {
        // Background tightly clustered + one clear outlier frame, same
        // pattern proven in StackingEngineTest's sigma-clip case.
        fun image(value: Float) = LinearImage(3, 3, 1, 16, FloatArray(9) { value })
        val frames = listOf(image(0.30f), image(0.31f), image(0.29f), image(0.30f), image(0.85f))

        val result = StackingEngine.stack(frames, StackingMethod.SIGMA_CLIP, sigmaThreshold = 1.5)

        return buildString {
            append(result.report.toUserMessage())
            appendLine("Valor por píxel (esperado ~0.30, sin el outlier 0.85): ${result.stackedImage.data[0]}")
        }
    }

    private fun sessionPersistenceReport(): String = runBlocking {
        val id = "session-${System.currentTimeMillis()}"
        sessionRepository.createSession(
            ObservationSession(
                id = id,
                name = "Sesión de prueba",
                createdAtUtc = Instant.now().toString(),
                cameraModel = Build.MODEL,
                target = "Test"
            )
        )

        val frameToAdd = lastCapturedFrame?.copy(id = UUID.randomUUID().toString(), sessionId = id)
            ?: syntheticFrame(id)
        sessionRepository.addFrame(frameToAdd)

        val loaded = sessionRepository.getSession(id)
        buildString {
            appendLine("Sesión creada y releída desde Room (persistida en disco, no en memoria).")
            appendLine("id=${loaded?.id}, target=${loaded?.target}, status=${loaded?.status}")
            appendLine("frameIds=${loaded?.frameIds}")
            if (lastCapturedFrame == null) {
                appendLine("(se usó un frame sintético porque todavía no capturaste ninguno real)")
            }
        }
    }

    private fun settingsPersistenceReport(): String = runBlocking {
        val before = settingsRepository.getSettings()
        val newIso = (before.defaultIso ?: 100) + 100
        settingsRepository.updateSettings { it.copy(defaultIso = newIso) }
        val after = settingsRepository.getSettings()

        buildString {
            appendLine("ISO por defecto (persistido con DataStore, sobrevive a cerrar la app):")
            appendLine("antes=${before.defaultIso}, después=${after.defaultIso}")
            appendLine(
                "Cierra la app por completo (no solo minimizar) y vuelve a abrirla: al presionar " +
                    "este botón de nuevo, \"antes\" debería ser igual al \"después\" de esta vez."
            )
        }
    }

    private fun syntheticFrame(sessionId: String) = ImageFrame(
        id = UUID.randomUUID().toString(),
        sessionId = sessionId,
        frameType = FrameType.LIGHT,
        filePath = "synthetic/no-file.dng",
        metadata = ImageMetadata(
            timestampUtc = Instant.now().toString(),
            latitude = null,
            longitude = null,
            altitudeMeters = null,
            cameraModel = "synthetic",
            sensorModel = null,
            lensModel = null,
            focalLengthMm = null,
            apertureFNumber = null,
            exposureTimeSeconds = 1.0,
            gain = null,
            iso = 100,
            temperatureCelsius = null,
            imageWidthPx = 4,
            imageHeightPx = 4,
            pixelSizeMicrons = null,
            orientationDegrees = null
        )
    )

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 1001
    }
}
