package com.astra.registration

import com.astra.core.model.LinearImage

/** [dx]/[dy]: how much [confidence] between 0 (couldn't tell) and 1 (clear match). */
data class ShiftEstimate(val dx: Int, val dy: Int, val confidence: Double)

/** Internal result of the raw translation search, before confidence is derived from it. */
internal data class ShiftSearchResult(val dx: Int, val dy: Int, val bestSsd: Double, val meanSsd: Double)

/**
 * Estimates and corrects **translation-only** misalignment between two
 * frames — no rotation, no scale. This is a deliberately simplified
 * stand-in for the full star-based registration described in roadmap
 * section 14 (star detection -> matching -> transformation), which needs
 * star detection that doesn't exist yet (V0.9 Object Detection). It
 * handles the common case of hand-shake-level misalignment between shots
 * of the same target; it does not handle field rotation from long
 * exposures or any real lens distortion — for that, see [SimilarityEstimator],
 * which layers rotation/scale search on top of this same translation search.
 *
 * Method: brute-force search over candidate (dx, dy) shifts within
 * [±maxShift], picking the one with the lowest sum-of-squared-differences
 * (SSD) between the reference and the shifted target, sampling every
 * [sampleStride]-th pixel to keep it fast on larger images.
 */
object TranslationAligner {

    fun estimateShift(
        reference: LinearImage,
        target: LinearImage,
        maxShift: Int = 20,
        sampleStride: Int = 1
    ): ShiftEstimate {
        val search = search(reference, target, maxShift, sampleStride)
        val confidence = confidenceFrom(search.bestSsd, search.meanSsd)
        return ShiftEstimate(search.dx, search.dy, confidence)
    }

    /**
     * Confidence compares the best candidate to the *average* candidate,
     * not just to zero-shift: a sharp, distinct minimum among many worse
     * candidates means "clearly this shift, not another" (high
     * confidence). A flat/featureless frame gives every candidate a
     * similar score, so the best one isn't meaningfully better than
     * average (low confidence) - that matters more than a fixed
     * threshold, since it also naturally handles frames with too little
     * signal to align against.
     */
    internal fun confidenceFrom(bestSsd: Double, meanSsd: Double): Double =
        if (meanSsd > 0.0) ((meanSsd - bestSsd) / meanSsd).coerceIn(0.0, 1.0) else 0.0

    internal fun search(
        reference: LinearImage,
        target: LinearImage,
        maxShift: Int,
        sampleStride: Int
    ): ShiftSearchResult {
        require(reference.width == target.width && reference.height == target.height) {
            "Reference and target must have the same dimensions"
        }
        require(maxShift >= 0) { "maxShift must be >= 0" }
        require(sampleStride >= 1) { "sampleStride must be >= 1" }

        var bestDx = 0
        var bestDy = 0
        var bestSsd = Double.MAX_VALUE
        var sumSsd = 0.0
        var candidateCount = 0

        for (dy in -maxShift..maxShift) {
            for (dx in -maxShift..maxShift) {
                val ssd = sumSquaredDifference(reference, target, dx, dy, sampleStride)
                sumSsd += ssd
                candidateCount++
                if (ssd < bestSsd) {
                    bestSsd = ssd
                    bestDx = dx
                    bestDy = dy
                }
            }
        }

        val meanSsd = if (candidateCount > 0) sumSsd / candidateCount else 0.0
        return ShiftSearchResult(bestDx, bestDy, bestSsd, meanSsd)
    }

    /**
     * Returns a copy of [image] shifted by ([dx], [dy]) pixels — i.e. the
     * transform that [estimateShift] found would align it with whatever it
     * was compared against. Integer-pixel shift only, no interpolation.
     * Pixels shifted in from outside the original frame are filled with
     * [fillValue].
     */
    fun applyShift(image: LinearImage, dx: Int, dy: Int, fillValue: Float = 0f): LinearImage {
        val result = FloatArray(image.data.size) { fillValue }
        for (y in 0 until image.height) {
            val sourceY = y - dy
            if (sourceY !in 0 until image.height) continue
            for (x in 0 until image.width) {
                val sourceX = x - dx
                if (sourceX !in 0 until image.width) continue
                result[y * image.width + x] = image.data[sourceY * image.width + sourceX]
            }
        }
        return LinearImage(image.width, image.height, image.channels, image.bitDepth, result)
    }

    /** Sum of squared differences between reference(x,y) and target(x-dx,y-dy) over their valid overlap. */
    private fun sumSquaredDifference(
        reference: LinearImage,
        target: LinearImage,
        dx: Int,
        dy: Int,
        stride: Int
    ): Double {
        var sum = 0.0
        var count = 0
        var y = 0
        while (y < reference.height) {
            val sourceY = y - dy
            if (sourceY in 0 until target.height) {
                var x = 0
                while (x < reference.width) {
                    val sourceX = x - dx
                    if (sourceX in 0 until target.width) {
                        val diff = (reference.data[y * reference.width + x] -
                            target.data[sourceY * target.width + sourceX]).toDouble()
                        sum += diff * diff
                        count++
                    }
                    x += stride
                }
            }
            y += stride
        }
        return if (count > 0) sum / count else Double.MAX_VALUE
    }
}
