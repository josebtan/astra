package com.astra.registration

import com.astra.core.model.LinearImage

data class RegistrationStepResult(
    val frameIndex: Int,
    val dx: Int,
    val dy: Int,
    val confidence: Double,
    val aligned: Boolean
)

data class RegistrationReport(
    val steps: List<RegistrationStepResult>,
    val minConfidenceThreshold: Double
) {
    fun toUserMessage(): String = buildString {
        appendLine("Alignment (${steps.size} frame(s), relative to the first):")
        steps.forEach { step ->
            val status = if (step.aligned) "aligned" else "NOT aligned (low confidence, left as-is)"
            appendLine(
                "- Frame ${step.frameIndex}: dx=${step.dx}, dy=${step.dy}, " +
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
}
