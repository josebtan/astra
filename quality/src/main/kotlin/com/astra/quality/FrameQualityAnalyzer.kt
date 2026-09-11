package com.astra.quality

import com.astra.core.model.LinearImage

data class FrameQualityReport(
    val backgroundLevel: Double,
    val backgroundNoise: Double,
    val starCount: Int,
    val averageFwhm: Double,
    val snr: Double,
    val saturationFraction: Double,
    val qualityScore: Double,
    val accepted: Boolean,
    val reasons: List<String>
) {
    fun toUserMessage(): String = buildString {
        appendLine("Quality score: ${"%.0f".format(qualityScore)}/100 -> ${if (accepted) "ACCEPTED" else "REJECTED"}")
        appendLine(
            "Stars/peaks: $starCount, avg FWHM: ${"%.1f".format(averageFwhm)}px, " +
                "SNR: ${"%.1f".format(snr)}, saturation: ${"%.2f".format(saturationFraction * 100)}%"
        )
        if (reasons.isNotEmpty()) {
            appendLine("Reasons:")
            reasons.forEach { appendLine("- $it") }
        }
    }
}

/**
 * Scores a single frame's quality (roadmap section 13 — Frame Quality
 * Analyzer) using [BackgroundEstimator] + [PeakDetector] as a lightweight
 * stand-in for real star detection/PSF fitting, which doesn't exist yet
 * (V0.9). Good enough to catch the obvious problems automatically: too
 * few/no stars visible, too noisy, badly saturated, or badly blurred/
 * trailed — not a substitute for real astrometric quality metrics later.
 */
object FrameQualityAnalyzer {

    fun analyze(
        image: LinearImage,
        minStarCount: Int = 3,
        minSnr: Double = 3.0,
        maxSaturationFraction: Double = 0.02,
        maxAverageFwhm: Double = 12.0
    ): FrameQualityReport {
        val background = BackgroundEstimator.estimate(image)
        val peaks = PeakDetector.detectPeaks(image, background)

        val starCount = peaks.size
        val averageFwhm = if (peaks.isNotEmpty()) peaks.map { it.fwhmProxy }.average() else 0.0
        val peakAmplitude = peaks.maxOfOrNull { it.amplitude } ?: background.level
        val snr = if (background.noise > 0.0) (peakAmplitude - background.level) / background.noise else 0.0
        val saturationFraction = image.data.count { it >= 0.99f }.toDouble() / image.data.size

        val reasons = mutableListOf<String>()
        var score = 100.0

        if (starCount < minStarCount) {
            reasons.add("Too few detected stars/peaks ($starCount < $minStarCount)")
            score -= 30.0
        }
        if (snr < minSnr) {
            reasons.add("SNR too low (${"%.1f".format(snr)} < $minSnr)")
            score -= 30.0
        }
        if (saturationFraction > maxSaturationFraction) {
            reasons.add(
                "Too many saturated pixels (${"%.2f".format(saturationFraction * 100)}% > " +
                    "${"%.2f".format(maxSaturationFraction * 100)}%)"
            )
            score -= 20.0
        }
        if (starCount > 0 && averageFwhm > maxAverageFwhm) {
            reasons.add(
                "Stars too broad (FWHM ${"%.1f".format(averageFwhm)}px > ${maxAverageFwhm}px) " +
                    "- possible trailing or defocus"
            )
            score -= 20.0
        }

        return FrameQualityReport(
            backgroundLevel = background.level,
            backgroundNoise = background.noise,
            starCount = starCount,
            averageFwhm = averageFwhm,
            snr = snr,
            saturationFraction = saturationFraction,
            qualityScore = score.coerceIn(0.0, 100.0),
            accepted = reasons.isEmpty(),
            reasons = reasons
        )
    }
}
