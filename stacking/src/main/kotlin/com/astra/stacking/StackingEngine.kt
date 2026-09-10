package com.astra.stacking

import com.astra.core.model.LinearImage
import kotlin.math.abs
import kotlin.math.sqrt

enum class StackingMethod { MEAN, MEDIAN, SIGMA_CLIP }

/** Feedback for one stacking run, meant to be shown to the user. */
data class StackingReport(
    val method: StackingMethod,
    val frameCount: Int,
    val rejectedSampleCount: Int,
    val meanSignal: Double
) {
    fun toUserMessage(): String = buildString {
        appendLine("Stacking: ${method.name.lowercase().replace('_', ' ')} of $frameCount frame(s).")
        if (method == StackingMethod.SIGMA_CLIP) {
            appendLine("Rejected samples: $rejectedSampleCount")
        }
        appendLine("Resulting mean signal: $meanSignal")
    }
}

data class StackingResult(val stackedImage: LinearImage, val report: StackingReport)

/**
 * Combines multiple already-calibrated light frames into one integrated
 * image (roadmap section 12). MVP scope: Mean, Median, Sigma Clip — the
 * three methods the roadmap calls out explicitly for the initial version
 * (WeightedMean/Min/Max are listed there as later additions).
 *
 * Does **not** do star-based alignment/registration (roadmap section 14):
 * that depends on star detection, which doesn't exist yet (V0.9 Object
 * Detection). Frames passed in here are assumed to already be
 * pixel-aligned — stacking unaligned frames will silently blend
 * misaligned stars rather than fail, since there's no way for this engine
 * to know the frames should have been aligned first.
 */
object StackingEngine {

    fun stack(
        frames: List<LinearImage>,
        method: StackingMethod = StackingMethod.SIGMA_CLIP,
        sigmaThreshold: Double = 3.0
    ): StackingResult {
        require(frames.isNotEmpty()) { "Cannot stack zero frames" }
        require(sigmaThreshold > 0) { "sigmaThreshold must be positive" }
        val first = frames.first()
        frames.forEach { frame ->
            require(
                frame.width == first.width && frame.height == first.height && frame.channels == first.channels
            ) { "All frames must share the same dimensions to be stacked" }
        }

        val pixelCount = first.data.size
        val result = FloatArray(pixelCount)
        var rejectedSampleCount = 0
        val column = FloatArray(frames.size)

        for (pixelIndex in 0 until pixelCount) {
            for (frameIndex in frames.indices) {
                column[frameIndex] = frames[frameIndex].data[pixelIndex]
            }

            result[pixelIndex] = when (method) {
                StackingMethod.MEAN -> mean(column)
                StackingMethod.MEDIAN -> median(column.copyOf().also { it.sort() })
                StackingMethod.SIGMA_CLIP -> {
                    val (value, rejected) = sigmaClippedMean(column, sigmaThreshold)
                    rejectedSampleCount += rejected
                    value
                }
            }
        }

        val stacked = LinearImage(first.width, first.height, first.channels, first.bitDepth, result)
        val report = StackingReport(
            method = method,
            frameCount = frames.size,
            rejectedSampleCount = rejectedSampleCount,
            meanSignal = result.average()
        )
        return StackingResult(stacked, report)
    }

    private fun mean(values: FloatArray): Float {
        var sum = 0.0
        for (v in values) sum += v
        return (sum / values.size).toFloat()
    }

    private fun median(sorted: FloatArray): Float {
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
    }

    /** Single-pass sigma clip: excludes samples farther than [sigmaThreshold] std devs from the mean. */
    private fun sigmaClippedMean(values: FloatArray, sigmaThreshold: Double): Pair<Float, Int> {
        val meanValue = values.average()
        var varianceSum = 0.0
        for (v in values) {
            val diff = v.toDouble() - meanValue
            varianceSum += diff * diff
        }
        val stdDev = sqrt(varianceSum / values.size)

        if (stdDev == 0.0) return mean(values) to 0

        var sum = 0.0
        var kept = 0
        for (v in values) {
            if (abs(v.toDouble() - meanValue) <= sigmaThreshold * stdDev) {
                sum += v
                kept++
            }
        }
        return if (kept > 0) {
            (sum / kept).toFloat() to (values.size - kept)
        } else {
            // Degenerate: every sample got rejected (shouldn't normally
            // happen with a sane threshold) - fall back to the plain mean
            // rather than producing a pixel with no data at all.
            mean(values) to 0
        }
    }
}
