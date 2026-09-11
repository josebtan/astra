package com.astra.registration

import com.astra.core.model.LinearImage

data class SimilarityTransform(
    val dx: Int,
    val dy: Int,
    val rotationDegrees: Double,
    val scale: Double,
    val confidence: Double
)

/**
 * Estimates a full similarity transform (translation + rotation + uniform
 * scale) between two frames — the next step up from [TranslationAligner],
 * for cases where the camera wasn't just nudged but also rotated slightly
 * or the zoom/focus changed the effective scale between shots.
 *
 * This is still **not** the real star-based registration from roadmap
 * section 14 (needs star detection - V0.9, doesn't exist yet). It's a
 * brute-force grid search over candidate rotation/scale values — for each
 * one, [ImageTransformer] resamples the target and [TranslationAligner]'s
 * translation search finds the best (dx, dy) for that candidate. The
 * globally best (rotation, scale, dx, dy) combination wins.
 *
 * This is meaningfully slower than translation-only search (roughly
 * `rotationSteps * scaleSteps` times slower), since every candidate
 * requires resampling the whole target image before searching
 * translations against it. Use [TranslationAligner] directly when only
 * hand-shake-level translation is expected — most consumer-camera
 * astrophotography between short exposures on a held phone.
 */
object SimilarityEstimator {

    fun estimate(
        reference: LinearImage,
        target: LinearImage,
        maxShift: Int = 15,
        sampleStride: Int = 2,
        rotationRangeDegrees: Double = 5.0,
        rotationStepDegrees: Double = 1.0,
        scaleRange: Double = 0.03,
        scaleStep: Double = 0.01
    ): SimilarityTransform {
        require(reference.width == target.width && reference.height == target.height) {
            "Reference and target must have the same dimensions"
        }
        require(rotationRangeDegrees >= 0.0) { "rotationRangeDegrees must be >= 0" }
        require(rotationStepDegrees > 0.0) { "rotationStepDegrees must be positive" }
        require(scaleRange >= 0.0) { "scaleRange must be >= 0" }
        require(scaleStep > 0.0) { "scaleStep must be positive" }

        var bestRotation = 0.0
        var bestScale = 1.0
        var bestDx = 0
        var bestDy = 0
        var bestSsd = Double.MAX_VALUE
        var sumOfBestPerCandidate = 0.0
        var candidateCount = 0

        var rotation = -rotationRangeDegrees
        while (rotation <= rotationRangeDegrees + 1e-9) {
            var scale = 1.0 - scaleRange
            while (scale <= 1.0 + scaleRange + 1e-9) {
                val transformedTarget = ImageTransformer.rotateAndScale(target, rotation, scale)
                val shift = TranslationAligner.search(reference, transformedTarget, maxShift, sampleStride)

                sumOfBestPerCandidate += shift.bestSsd
                candidateCount++
                if (shift.bestSsd < bestSsd) {
                    bestSsd = shift.bestSsd
                    bestRotation = rotation
                    bestScale = scale
                    bestDx = shift.dx
                    bestDy = shift.dy
                }

                scale += scaleStep
            }
            rotation += rotationStepDegrees
        }

        val meanSsd = if (candidateCount > 0) sumOfBestPerCandidate / candidateCount else 0.0
        val confidence = TranslationAligner.confidenceFrom(bestSsd, meanSsd)

        return SimilarityTransform(bestDx, bestDy, bestRotation, bestScale, confidence)
    }

    /** Applies the transform found by [estimate]: rotate+scale, then shift. */
    fun apply(image: LinearImage, transform: SimilarityTransform, fillValue: Float = 0f): LinearImage {
        val rotatedScaled = ImageTransformer.rotateAndScale(image, transform.rotationDegrees, transform.scale, fillValue)
        return TranslationAligner.applyShift(rotatedScaled, transform.dx, transform.dy, fillValue)
    }
}
