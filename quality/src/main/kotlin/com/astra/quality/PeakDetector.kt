package com.astra.quality

import com.astra.core.model.LinearImage

data class StarPeak(val x: Int, val y: Int, val amplitude: Double, val fwhmProxy: Double)

/**
 * Finds bright local-maximum pixels above the background — a rough
 * stand-in for real star detection (roadmap section 18), which needs a
 * proper source-extraction pipeline that doesn't exist yet (V0.9 Object
 * Detection). This is deliberately simpler: good enough to count "how
 * many bright points are here" and roughly how sharp they are, for frame
 * quality scoring — not for astrometry or cataloging.
 */
object PeakDetector {

    fun detectPeaks(
        image: LinearImage,
        background: BackgroundStats,
        sigmaThreshold: Double = 5.0,
        minSeparation: Int = 3
    ): List<StarPeak> {
        val threshold = background.level + sigmaThreshold * background.noise
        val width = image.width
        val height = image.height

        val candidates = mutableListOf<Pair<Int, Int>>()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val value = image.data[y * width + x]
                if (value < threshold) continue

                var isLocalMax = true
                outer@ for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        if (image.data[(y + dy) * width + (x + dx)] > value) {
                            isLocalMax = false
                            break@outer
                        }
                    }
                }
                if (isLocalMax) candidates.add(x to y)
            }
        }

        // Greedy non-max suppression: brightest candidates win when two are
        // closer than minSeparation, so one real star isn't counted twice.
        val accepted = mutableListOf<Pair<Int, Int>>()
        for (candidate in candidates.sortedByDescending { (cx, cy) -> image.data[cy * width + cx] }) {
            val tooClose = accepted.any { (ax, ay) ->
                val ddx = ax - candidate.first
                val ddy = ay - candidate.second
                ddx * ddx + ddy * ddy < minSeparation * minSeparation
            }
            if (!tooClose) accepted.add(candidate)
        }

        return accepted.map { (x, y) ->
            val amplitude = image.data[y * width + x].toDouble()
            val fwhm = fwhmProxy(image, x, y, background.level, amplitude)
            StarPeak(x, y, amplitude, fwhm)
        }
    }

    /**
     * Rough FWHM proxy: from the peak, walks outward in each of the 4
     * cardinal directions until intensity drops below the half-max level,
     * then averages the horizontal and vertical widths. Not a Gaussian
     * fit (no sub-pixel precision, doesn't handle elongated/trailed stars
     * specially) — good enough to flag "these stars are unusually wide"
     * without needing real PSF fitting.
     */
    private fun fwhmProxy(image: LinearImage, x: Int, y: Int, backgroundLevel: Double, amplitude: Double): Double {
        val halfMax = backgroundLevel + (amplitude - backgroundLevel) / 2.0

        var left = 0
        while (x - left - 1 >= 0 && image.data[y * image.width + (x - left - 1)] >= halfMax) left++
        var right = 0
        while (x + right + 1 < image.width && image.data[y * image.width + (x + right + 1)] >= halfMax) right++
        var up = 0
        while (y - up - 1 >= 0 && image.data[(y - up - 1) * image.width + x] >= halfMax) up++
        var down = 0
        while (y + down + 1 < image.height && image.data[(y + down + 1) * image.width + x] >= halfMax) down++

        val widthX = (left + right + 1).toDouble()
        val widthY = (up + down + 1).toDouble()
        return (widthX + widthY) / 2.0
    }
}
