package com.astra.calibration

import com.astra.core.model.LinearImage

enum class CalibrationStepType { BIAS_SUBTRACTION, DARK_SUBTRACTION, FLAT_DIVISION, DEFECT_CORRECTION }

/** One calibration step's outcome, meant to be shown to the user, not just logged. */
data class CalibrationStepResult(
    val type: CalibrationStepType,
    val applied: Boolean,
    val message: String
)

/**
 * Full feedback for one calibration run: what was actually done, what was
 * skipped and why, how many sensor defects were found/fixed, and a
 * before/after noise indicator. This is the object the UI shows the user —
 * calibration should never be a silent pass/fail.
 */
data class CalibrationReport(
    val steps: List<CalibrationStepResult>,
    val defectsDetected: Int,
    val defectsCorrected: Int,
    val meanSignalBefore: Double,
    val meanSignalAfter: Double
) {
    /** Plain-text summary suitable for direct display; the UI layer can localize/style it later. */
    fun toUserMessage(): String = buildString {
        appendLine("Automatic calibration:")
        steps.forEach { step ->
            val bullet = if (step.applied) "done" else "skipped"
            appendLine("- [$bullet] ${step.type.name.lowercase().replace('_', ' ')}: ${step.message}")
        }
    }
}

data class CalibrationResult(val calibratedImage: LinearImage, val report: CalibrationReport)

/**
 * Fully automatic calibration pipeline (roadmap sections 10-11): given a
 * light frame and whatever bias/dark/flat frames happen to be available
 * for the session, applies as many of bias subtraction, dark subtraction,
 * flat division and defect correction as it can, in the right order, with
 * zero required configuration.
 *
 * "As automatic as possible" means: the caller only supplies the frames it
 * has — no thresholds, no method choice, no manual matching. Every step
 * still reports what it did or why it was skipped (missing frames), via
 * [CalibrationReport], so the result is never a silent pass/fail.
 */
object CalibrationEngine {

    fun calibrate(
        light: LinearImage,
        biasFrames: List<LinearImage> = emptyList(),
        darkFrames: List<LinearImage> = emptyList(),
        flatFrames: List<LinearImage> = emptyList()
    ): CalibrationResult {
        val steps = mutableListOf<CalibrationStepResult>()
        val meanBefore = light.data.average()

        var working = light
        var masterBias: LinearImage? = null

        if (biasFrames.isNotEmpty()) {
            masterBias = MasterFrameBuilder.medianCombine(biasFrames)
            working = subtract(working, masterBias)
            steps += CalibrationStepResult(
                CalibrationStepType.BIAS_SUBTRACTION,
                applied = true,
                message = "Subtracted a master bias built from ${biasFrames.size} frame(s)."
            )
        } else {
            steps += CalibrationStepResult(
                CalibrationStepType.BIAS_SUBTRACTION,
                applied = false,
                message = "No bias frames available for this session - step skipped."
            )
        }

        var masterDark: LinearImage? = null
        if (darkFrames.isNotEmpty()) {
            val rawMasterDark = MasterFrameBuilder.medianCombine(darkFrames)
            masterDark = masterBias?.let { subtract(rawMasterDark, it) } ?: rawMasterDark
            working = subtract(working, masterDark)
            steps += CalibrationStepResult(
                CalibrationStepType.DARK_SUBTRACTION,
                applied = true,
                message = "Subtracted a master dark built from ${darkFrames.size} frame(s)."
            )
        } else {
            steps += CalibrationStepResult(
                CalibrationStepType.DARK_SUBTRACTION,
                applied = false,
                message = "No dark frames available for this session - step skipped."
            )
        }

        if (flatFrames.isNotEmpty()) {
            val rawMasterFlat = MasterFrameBuilder.medianCombine(flatFrames)
            val biasCorrectedFlat = masterBias?.let { subtract(rawMasterFlat, it) } ?: rawMasterFlat
            val normalizedFlat = normalize(biasCorrectedFlat)
            working = divide(working, normalizedFlat)
            steps += CalibrationStepResult(
                CalibrationStepType.FLAT_DIVISION,
                applied = true,
                message = "Divided by a normalized master flat built from ${flatFrames.size} frame(s)."
            )
        } else {
            steps += CalibrationStepResult(
                CalibrationStepType.FLAT_DIVISION,
                applied = false,
                message = "No flat frames available for this session - step skipped."
            )
        }

        var defectsDetected = 0
        var defectsCorrected = 0
        if (masterDark != null) {
            val defects = DefectMapBuilder.detectDefects(masterDark)
            defectsDetected = defects.size
            if (defects.isNotEmpty()) {
                working = DefectCorrector.correct(working, defects)
                defectsCorrected = defects.size
            }
            steps += CalibrationStepResult(
                CalibrationStepType.DEFECT_CORRECTION,
                applied = defects.isNotEmpty(),
                message = if (defects.isEmpty()) {
                    "No defective sensor pixels detected."
                } else {
                    "Detected and corrected $defectsDetected defective pixel(s)."
                }
            )
        } else {
            steps += CalibrationStepResult(
                CalibrationStepType.DEFECT_CORRECTION,
                applied = false,
                message = "No master dark available - cannot detect sensor defects without one."
            )
        }

        val report = CalibrationReport(
            steps = steps,
            defectsDetected = defectsDetected,
            defectsCorrected = defectsCorrected,
            meanSignalBefore = meanBefore,
            meanSignalAfter = working.data.average()
        )

        return CalibrationResult(working, report)
    }

    private fun subtract(a: LinearImage, b: LinearImage): LinearImage {
        require(a.data.size == b.data.size) { "Images must have matching dimensions to subtract" }
        val result = FloatArray(a.data.size) { i -> (a.data[i] - b.data[i]).coerceIn(0f, 1f) }
        return LinearImage(a.width, a.height, a.channels, a.bitDepth, result)
    }

    private fun divide(a: LinearImage, b: LinearImage): LinearImage {
        require(a.data.size == b.data.size) { "Images must have matching dimensions to divide" }
        val result = FloatArray(a.data.size) { i ->
            val denominator = if (b.data[i] == 0f) 1f else b.data[i]
            (a.data[i] / denominator).coerceIn(0f, 1f)
        }
        return LinearImage(a.width, a.height, a.channels, a.bitDepth, result)
    }

    /** Divides every pixel by the frame's mean, so a flat's average becomes 1.0. */
    private fun normalize(image: LinearImage): LinearImage {
        val mean = image.data.average().toFloat()
        if (mean == 0f) return image
        val result = FloatArray(image.data.size) { i -> image.data[i] / mean }
        return LinearImage(image.width, image.height, image.channels, image.bitDepth, result)
    }
}
