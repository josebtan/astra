package com.astra.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.room.Room
import com.astra.calibration.CalibrationEngine
import com.astra.calibration.CalibrationReport
import com.astra.camera.android.AndroidCameraDevice
import com.astra.core.model.CaptureSettings
import com.astra.core.model.FrameType
import com.astra.core.model.ImageFrame
import com.astra.core.model.LinearImage
import com.astra.core.model.ObservationSession
import com.astra.core.model.SessionStatus
import com.astra.core.storage.SessionFileStore
import com.astra.core.storage.db.AstraDatabase
import com.astra.core.storage.db.RoomSessionRepository
import com.astra.raw.DngTiffReader
import com.astra.registration.RegistrationEngine
import com.astra.stacking.StackingEngine
import com.astra.stacking.StackingMethod
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Instant

/**
 * Real capture screen: live Camera2 preview + actual RAW capture per frame
 * type (LIGHT/DARK/BIAS/FLAT), then the real calibration + stacking
 * pipeline running on those real captured frames — not synthetic data.
 *
 * Still not the final `ui` module design (the roadmap never specifies
 * one), but this is functional: point the camera, capture real frames,
 * process them for real, save the session for real.
 */
class CaptureActivity : Activity() {

    private val sessionId = "capture-${System.currentTimeMillis()}"
    private val fileStore: SessionFileStore by lazy {
        SessionFileStore(File(filesDir, "sessions"), sessionId)
    }
    private val database: AstraDatabase by lazy {
        Room.databaseBuilder(applicationContext, AstraDatabase::class.java, AstraDatabase.DATABASE_NAME).build()
    }
    private val sessionRepository by lazy {
        RoomSessionRepository(database.observationSessionDao(), database.imageFrameDao())
    }

    private var device: AndroidCameraDevice? = null
    private var connected = false

    private val lightImages = mutableListOf<LinearImage>()
    private val darkImages = mutableListOf<LinearImage>()
    private val biasImages = mutableListOf<LinearImage>()
    private val flatImages = mutableListOf<LinearImage>()
    private val allFrames = mutableListOf<ImageFrame>()

    private lateinit var surfaceView: SurfaceView
    private lateinit var exposureInput: EditText
    private lateinit var isoInput: EditText
    private lateinit var frameTypeSpinner: Spinner
    private lateinit var rotationScaleCheckbox: CheckBox
    private lateinit var countsView: TextView
    private lateinit var outputView: TextView
    private val outputBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        device?.let {
            try {
                it.disconnect()
            } catch (e: Exception) {
                // best-effort cleanup, activity is going away regardless
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            appendOutput(
                if (granted) "Permiso concedido. Presiona \"Reintentar preview\" para iniciar la cámara."
                else "Permiso de cámara denegado - la captura no va a funcionar sin él."
            )
        }
    }

    // ---------------------------------------------------------------
    // UI construction
    // ---------------------------------------------------------------

    private fun buildScreen(): LinearLayout {
        val backgroundColor = Color.parseColor("#0B1226")
        val textColor = Color.parseColor("#F5F7FF")
        val mutedColor = Color.parseColor("#A9B1C9")

        surfaceView = SurfaceView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320))
            holder.addCallback(surfaceCallback)
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(backgroundColor)
        }

        controls.addView(TextView(this).apply {
            text = "Captura en vivo"
            setTextColor(textColor)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(4))
        })
        controls.addView(TextView(this).apply {
            text = "Sesión: $sessionId"
            setTextColor(mutedColor)
            textSize = 12f
            setPadding(0, 0, 0, dp(16))
        })

        exposureInput = labeledInput(controls, "Exposición (segundos)", "1.0", textColor, mutedColor,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        isoInput = labeledInput(controls, "ISO", "400", textColor, mutedColor, InputType.TYPE_CLASS_NUMBER)

        controls.addView(TextView(this).apply {
            text = "Tipo de frame a capturar"
            setTextColor(mutedColor)
            textSize = 12f
            setPadding(0, dp(8), 0, dp(4))
        })
        frameTypeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@CaptureActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("LIGHT", "DARK", "BIAS", "FLAT"))
        }
        controls.addView(frameTypeSpinner)

        countsView = TextView(this).apply {
            text = frameCountsText()
            setTextColor(textColor)
            textSize = 13f
            setPadding(0, dp(12), 0, dp(4))
        }
        controls.addView(countsView)

        rotationScaleCheckbox = CheckBox(this).apply {
            text = "Corregir también rotación/escala al alinear (más lento)"
            setTextColor(mutedColor)
            textSize = 12f
        }
        controls.addView(rotationScaleCheckbox)

        controls.addView(labButton("Capturar") { onCaptureButtonPressed() })
        controls.addView(labButton("Reintentar preview") { onRetryPreviewPressed() })
        controls.addView(labButton("Procesar sesión (calibrar + alinear + stack)") {
            runInBackground("Procesar sesión") { processSessionReport() }
        })
        controls.addView(labButton("Guardar sesión (Room)") {
            runInBackground("Guardar sesión") { saveSessionReport() }
        })
        controls.addView(labButton("Limpiar frames capturados") { clearCapturedFrames() })
        controls.addView(labButton("Progreso del roadmap / laboratorio (debug)") {
            startActivity(Intent(this, MainActivity::class.java))
        })

        controls.addView(TextView(this).apply {
            text = "Salida"
            setTextColor(textColor)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(16), 0, dp(6))
        })
        outputView = TextView(this).apply {
            text = "Presiona un botón para ver el resultado aquí."
            setTextColor(textColor)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.parseColor("#141B33"))
        }
        controls.addView(outputView)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(backgroundColor)
            addView(controls)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(backgroundColor)
            addView(surfaceView)
            addView(scroll)
        }
    }

    private fun labeledInput(
        parent: LinearLayout,
        label: String,
        defaultValue: String,
        textColor: Int,
        mutedColor: Int,
        inputType: Int
    ): EditText {
        parent.addView(TextView(this).apply {
            text = label
            setTextColor(mutedColor)
            textSize = 12f
            setPadding(0, dp(8), 0, dp(2))
        })
        val input = EditText(this).apply {
            setText(defaultValue)
            setTextColor(textColor)
            this.inputType = inputType
        }
        parent.addView(input)
        return input
    }

    private fun labButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------
    // Camera lifecycle
    // ---------------------------------------------------------------

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                appendOutput("Falta permiso de cámara - concédelo y presiona \"Reintentar preview\".")
                return
            }
            runInBackground("Iniciar preview") { startPreviewInternal(holder) }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            try {
                device?.stopPreview()
            } catch (e: Exception) {
                // activity/surface is going away, best effort only
            }
        }
    }

    private fun onRetryPreviewPressed() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            return
        }
        val holder = surfaceView.holder
        runInBackground("Reintentar preview") { startPreviewInternal(holder) }
    }

    private fun startPreviewInternal(holder: SurfaceHolder): String {
        val cameraDevice = cameraDeviceOrCreate()
        ensureConnected(cameraDevice)
        cameraDevice.startPreview(holder.surface)
        return "Preview iniciado."
    }

    private fun cameraDeviceOrCreate(): AndroidCameraDevice {
        device?.let { return it }
        val ids = AndroidCameraDevice.listAvailableCameraIds(this)
        check(ids.isNotEmpty()) { "No se detectaron cámaras en este dispositivo." }
        return AndroidCameraDevice(this, ids.first(), fileStore, sessionId).also { device = it }
    }

    private fun ensureConnected(cameraDevice: AndroidCameraDevice) {
        if (!connected) {
            cameraDevice.connect()
            connected = true
        }
    }

    // ---------------------------------------------------------------
    // Capture + pipeline actions
    // ---------------------------------------------------------------

    private fun onCaptureButtonPressed() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            appendOutput("Solicitando permiso de cámara - vuelve a presionar \"Capturar\" después de aceptarlo.")
            return
        }
        val exposure = exposureInput.text.toString().toDoubleOrNull() ?: 1.0
        val iso = isoInput.text.toString().toIntOrNull()
        val frameType = FrameType.valueOf(frameTypeSpinner.selectedItem as String)
        runInBackground("Capturar $frameType") { performCapture(exposure, iso, frameType) }
    }

    private fun performCapture(exposureSeconds: Double, iso: Int?, frameType: FrameType): String {
        val cameraDevice = cameraDeviceOrCreate()
        ensureConnected(cameraDevice)

        if (!cameraDevice.getCapabilities().supportsRaw) {
            return "Esta cámara no reporta soporte RAW_SENSOR - no se puede capturar DNG."
        }

        val frame = cameraDevice.capture(CaptureSettings(exposureTimeSeconds = exposureSeconds, iso = iso))
            .copy(frameType = frameType) // the device always tags LIGHT; we relabel by capture intent
        allFrames.add(frame)

        val linearImage = DngTiffReader(File(frame.filePath)).readLinearImage()
        when (frameType) {
            FrameType.LIGHT -> lightImages.add(linearImage)
            FrameType.DARK -> darkImages.add(linearImage)
            FrameType.BIAS -> biasImages.add(linearImage)
            FrameType.FLAT -> flatImages.add(linearImage)
        }
        runOnUiThread { countsView.text = frameCountsText() }

        // Preview was dropped to do the capture (Camera2 only allows one
        // active session) - try to resume it automatically.
        try {
            cameraDevice.startPreview(surfaceView.holder.surface)
        } catch (e: Exception) {
            appendOutput("(no se pudo reanudar el preview automáticamente: ${e.message})")
        }

        return buildString {
            appendLine("Capturado $frameType.")
            appendLine("Archivo: ${frame.filePath}")
            appendLine("Decodificado: ${linearImage.width}x${linearImage.height}, canales=${linearImage.channels}, bitDepth=${linearImage.bitDepth}")
        }
    }

    private fun processSessionReport(): String {
        if (lightImages.isEmpty()) {
            return "Captura al menos un frame LIGHT antes de procesar la sesión."
        }

        val calibratedLights = mutableListOf<LinearImage>()
        var lastCalibrationReport: CalibrationReport? = null
        for (light in lightImages) {
            val calibration = CalibrationEngine.calibrate(light, biasImages, darkImages, flatImages)
            calibratedLights.add(calibration.calibratedImage)
            lastCalibrationReport = calibration.report
        }

        val registrationResult = if (rotationScaleCheckbox.isChecked) {
            RegistrationEngine.registerWithRotationAndScale(calibratedLights)
        } else {
            RegistrationEngine.register(calibratedLights)
        }
        val stackResult = StackingEngine.stack(registrationResult.alignedFrames, StackingMethod.SIGMA_CLIP)

        return buildString {
            appendLine("Frames LIGHT procesados: ${lightImages.size} (con ${biasImages.size} bias, ${darkImages.size} dark, ${flatImages.size} flat disponibles)")
            appendLine()
            appendLine("Calibración (reporte del último LIGHT procesado):")
            append(lastCalibrationReport?.toUserMessage() ?: "N/A")
            appendLine()
            append(registrationResult.report.toUserMessage())
            appendLine()
            append(stackResult.report.toUserMessage())
            appendLine("Imagen final: ${stackResult.stackedImage.width}x${stackResult.stackedImage.height}")
            appendLine(
                "min=${stackResult.stackedImage.data.min()} max=${stackResult.stackedImage.data.max()} " +
                    "media=${stackResult.stackedImage.data.average()}"
            )
        }
    }

    private fun saveSessionReport(): String = runBlocking {
        if (allFrames.isEmpty()) return@runBlocking "No hay frames capturados todavía - nada que guardar."

        sessionRepository.createSession(
            ObservationSession(
                id = sessionId,
                name = "Captura $sessionId",
                createdAtUtc = Instant.now().toString(),
                cameraModel = Build.MODEL,
                target = null,
                status = SessionStatus.CAPTURE_COMPLETE
            )
        )
        allFrames.forEach { sessionRepository.addFrame(it) }

        "Sesión guardada en Room: id=$sessionId, ${allFrames.size} frame(s) " +
            "(${lightImages.size} light, ${biasImages.size} bias, ${darkImages.size} dark, ${flatImages.size} flat)."
    }

    private fun clearCapturedFrames() {
        lightImages.clear()
        darkImages.clear()
        biasImages.clear()
        flatImages.clear()
        allFrames.clear()
        countsView.text = frameCountsText()
        appendOutput("Frames capturados en memoria limpiados (los archivos DNG en disco no se borran).")
    }

    private fun frameCountsText(): String =
        "LIGHT: ${lightImages.size}   DARK: ${darkImages.size}   BIAS: ${biasImages.size}   FLAT: ${flatImages.size}"

    // ---------------------------------------------------------------
    // Small helpers
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

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 2001
    }
}
