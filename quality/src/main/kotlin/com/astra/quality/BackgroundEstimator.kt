package com.astra.quality

import com.astra.core.model.LinearImage
import kotlin.math.abs

data class BackgroundStats(val level: Double, val noise: Double)

/**
 * Estimates sky background level and noise using median + MAD (median
 * absolute deviation), for the same robustness reason as
 * `calibration.DefectMapBuilder`: a handful of bright stars shouldn't drag
 * the estimate up the way a plain mean would.
 */
object BackgroundEstimator {

    fun estimate(image: LinearImage): BackgroundStats {
        val values = image.data.map { it.toDouble() }
        val level = median(values)
        val absoluteDeviations = values.map { abs(it - level) }
        val noise = 1.4826 * median(absoluteDeviations)
        return BackgroundStats(level, noise)
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }
}
