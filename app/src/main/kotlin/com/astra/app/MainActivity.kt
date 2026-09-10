package com.astra.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Temporary status screen, **not** the real UI module (roadmap section 37
 * still has `ui/` as a future module). Until there's an actual capture
 * screen, this exists purely so opening the installed APK shows something
 * meaningful instead of a blank/crashing screen: which roadmap milestones
 * are done, in progress, or pending.
 *
 * This should be replaced wholesale once the capture UI exists.
 */
class MainActivity : Activity() {

    private data class Milestone(val label: String, val status: Status)
    private enum class Status(val marker: String, val color: Int) {
        DONE("✓", Color.parseColor("#6CFFB0")),
        IN_PROGRESS("…", Color.parseColor("#FFD166")),
        PENDING("—", Color.parseColor("#7A8199"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildStatusScreen())
    }

    private fun buildStatusScreen(): ScrollView {
        val backgroundColor = Color.parseColor("#0B1226")
        val textColor = Color.parseColor("#F5F7FF")
        val mutedColor = Color.parseColor("#A9B1C9")
        val padding = dp(24)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(backgroundColor)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
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

        root.addView(TextView(this).apply {
            text = "Progreso del roadmap"
            setTextColor(textColor)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(12))
        })

        milestones().forEach { milestone ->
            root.addView(milestoneRow(milestone, textColor))
        }

        root.addView(TextView(this).apply {
            text = "Esta pantalla es temporal: todavía no existe la capa de UI real " +
                "(módulo ui, pendiente en el roadmap). Sirve solo para confirmar que " +
                "la app instala y corre, y para ver en qué va el desarrollo."
            setTextColor(mutedColor)
            textSize = 12f
            setPadding(0, dp(24), 0, 0)
        })

        return ScrollView(this).apply {
            setBackgroundColor(backgroundColor)
            addView(root)
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
        Milestone("V0.4 Calibration — bias/dark/flat + corrección de defectos", Status.IN_PROGRESS),
        Milestone("V0.5 Stacking", Status.PENDING),
        Milestone("V0.6 Quality Analysis", Status.PENDING),
        Milestone("V0.7 Astrometry", Status.PENDING),
        Milestone("V0.8 Astronomy Engine", Status.PENDING),
        Milestone("V0.9 Object Detection", Status.PENDING),
        Milestone("V1.0 Scientific Release", Status.PENDING)
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
