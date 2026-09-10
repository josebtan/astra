package com.astra.calibration

import com.astra.core.model.LinearImage
import kotlin.math.abs
import kotlin.math.sqrt

enum class DefectType { HOT, DEAD }

data class DefectPixel(val x: Int, val y: Int, val type: DefectType, val confidence: Double)

/**
 * Flags statistically anomalous pixels in a master dark frame: "hot"
 * pixels sit far above the bulk of the data, "dead" ones far below it
 * (usually pinned near zero) — see roadmap section 11 (SensorDefectMap).
 *
 * Uses the **median and median absolute deviation (MAD)**, not mean and
 * standard deviation. Mean/stddev is not robust here: a single very hot
 * pixel drags the mean up and inflates the standard deviation, which can
 * mask that same pixel (and any others) instead of flagging it — the
 * outlier hides itself in its own statistic. Median/MAD doesn't have that
 * problem, which matters for the "as automatic as possible" goal: it
 * needs to reliably find defects with zero tuning, including when there's
 * more than one.
 *
 * Falls back to mean/stddev only in the degenerate case where MAD is
 * exactly zero (e.g. a near-uniform frame where at least half the pixels
 * share one exact value) — a rare edge case where MAD carries no signal
 * but the plain standard deviation still does.
 */
object DefectMapBuilder {

    fun detectDefects(masterDark: LinearImage, sigmaThreshold: Double = 5.0): List<DefectPixel> {
        require(sigmaThreshold > 0) { "sigmaThreshold must be positive" }
        val data = masterDark.data

        val median = median(data.map { it.toDouble() })
        val absoluteDeviations = data.map { value -> abs(value - median) }
        var robustSigma = 1.4826 * median(absoluteDeviations)

        if (robustSigma == 0.0) {
            robustSigma = fallbackStandardDeviation(data)
        }
        if (robustSigma == 0.0) return emptyList() // truly flat frame: nothing to flag

        val defects = mutableListOf<DefectPixel>()
        for (y in 0 until masterDark.height) {
            for (x in 0 until masterDark.width) {
                val value = data[y * masterDark.width + x]
                val sigmasAway = (value - median) / robustSigma
                when {
                    sigmasAway > sigmaThreshold ->
                        defects.add(DefectPixel(x, y, DefectType.HOT, confidenceFor(sigmasAway, sigmaThreshold)))
                    sigmasAway < -sigmaThreshold ->
                        defects.add(DefectPixel(x, y, DefectType.DEAD, confidenceFor(-sigmasAway, sigmaThreshold)))
                }
            }
        }
        return defects
    }

    private fun fallbackStandardDeviation(data: FloatArray): Double {
        val mean = data.average()
        val variance = data.sumOf { value -> (value - mean) * (value - mean) } / data.size
        return sqrt(variance)
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }

    /** 0.0 right at the threshold, ramping linearly to 1.0 at twice the threshold. */
    private fun confidenceFor(sigmasAway: Double, threshold: Double): Double =
        ((sigmasAway - threshold) / threshold).coerceIn(0.0, 1.0)
}
