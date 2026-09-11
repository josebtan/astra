package com.astra.registration

import com.astra.core.model.LinearImage

data class RegistrationStepResult(
    val frameIndex: Int,
    val dx: Int,
    val dy: Int,
    val confidence: Double,
    val aligned: Boolean,
    val rotationDegrees: Double = 0.0,
    val scale: Double = 1.0
)

data class RegistrationReport(
    val steps: List<RegistrationStepResult>,
    val minConfidenceThreshold: Double
) {
    fun toUserMessage(): String = buildString {
        appendLine("Alignment (${steps.size} frame(s), relative to the first):")
        steps.forEach { step ->
            val status = if (step.aligned) "aligned" else "NOT aligned (low confidence, left as-is)"
            val transformInfo = if (step.rotationDegrees != 0.0 || step.scale != 1.0) {
                ", rotation=${"%.1f".format(step.rotationDegrees)}deg, scale=${"%.3f".format(step.scale)}"
            } else {
                ""
            }
            appendLine(
                "- Frame ${step.frameIndex}: dx=${step.dx}, dy=${step.dy}$transformInfo, " +
                    "confidence=${"%.2f".format(step.confidence)} -> $status"
            )
        }
    }
}

data class RegistrationResult(val alignedFrames: List<LinearImage>, val report: RegistrationReport)

/**
 * Aligns a list of frames to the first one using [TranslationAligner]
 * (translation only — see its KDoc for why). Frames whose estimated shift
 * confidence falls below [minConfidence] are left unaligned rather than
 * "corrected" with a guess that's likely wrong — the report flags exactly
 * which frames that happened to, so the caller (or the user) can decide
 * whether to exclude them from stacking.
 */
object RegistrationEngine {

    fun register(
        frames: List<LinearImage>,
        maxShift: Int = 20,
        sampleStride: Int = 2,
        minConfidence: Double = 0.1
    ): RegistrationResult {
        require(frames.isNotEmpty()) { "Cannot register zero frames" }
        val reference = frames.first()

        val alignedFrames = mutableListOf(reference)
        val steps = mutableListOf(
            RegistrationStepResult(frameIndex = 0, dx = 0, dy = 0, confidence = 1.0, aligned = true)
        )

        for (index in 1 until frames.size) {
            val target = frames[index]
            val shift = TranslationAligner.estimateShift(reference, target, maxShift, sampleStride)
            val aligned = shift.confidence >= minConfidence
            val alignedImage = if (aligned) {
                TranslationAligner.applyShift(target, shift.dx, shift.dy)
            } else {
                target
            }
            alignedFrames.add(alignedImage)
            steps.add(RegistrationStepResult(index, shift.dx, shift.dy, shift.confidence, aligned))
        }

        return RegistrationResult(alignedFrames, RegistrationReport(steps, minConfidence))
    }

    /**
     * Same idea as [register], but corrects rotation and scale too, via
     * [SimilarityEstimator] instead of plain [TranslationAligner]. Use
     * this when the camera might have rotated slightly or the effective
     * zoom changed between shots (e.g. handheld between exposures rather
     * than on a fixed mount) — it is meaningfully slower per frame, so
     * [register] remains the default for the common hand-shake-only case.
     */
    fun registerWithRotationAndScale(
        frames: List<LinearImage>,
        maxShift: Int = 15,
        sampleStride: Int = 2,
        rotationRangeDegrees: Double = 5.0,
        rotationStepDegrees: Double = 1.0,
        scaleRange: Double = 0.03,
        scaleStep: Double = 0.01,
        minConfidence: Double = 0.1
    ): RegistrationResult {
        require(frames.isNotEmpty()) { "Cannot register zero frames" }
        val reference = frames.first()

        val alignedFrames = mutableListOf(reference)
        val steps = mutableListOf(
            RegistrationStepResult(frameIndex = 0, dx = 0, dy = 0, confidence = 1.0, aligned = true)
        )

        for (index in 1 until frames.size) {
            val target = frames[index]
            val transform = SimilarityEstimator.estimate(
                reference, target, maxShift, sampleStride,
                rotationRangeDegrees, rotationStepDegrees, scaleRange, scaleStep
            )
            val aligned = transform.confidence >= minConfidence
            val alignedImage = if (aligned) SimilarityEstimator.apply(target, transform) else target
            alignedFrames.add(alignedImage)
            steps.add(
                RegistrationStepResult(
                    index, transform.dx, transform.dy, transform.confidence, aligned,
                    transform.rotationDegrees, transform.scale
                )
            )
        }

        return RegistrationResult(alignedFrames, RegistrationReport(steps, minConfidence))
    }
}
