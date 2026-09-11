package com.astra.registration

import com.astra.core.model.LinearImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SimilarityEstimatorTest {

    @Test
    fun `reduces to a translation-equivalent result when there is no rotation or scale`() {
        val reference = blockImage(width = 30, height = 30, blockX = 10, blockY = 10)
        val target = TranslationAligner.applyShift(reference, dx = 4, dy = -3)

        val transform = SimilarityEstimator.estimate(
            reference, target,
            maxShift = 6, sampleStride = 1,
            rotationRangeDegrees = 2.0, rotationStepDegrees = 1.0,
            scaleRange = 0.02, scaleStep = 0.01
        )
        val aligned = SimilarityEstimator.apply(target, transform)

        // A solid square block can tie in SSD across a couple of small
        // rotation candidates under nearest-neighbor resampling (rotating
        // a filled square by 1-2 degrees can round back to the same
        // pixels), so the *specific* rotation/scale chosen among ties
        // isn't meaningful here. What matters is that applying whichever
        // transform was found actually reconstructs the reference.
        for (y in 10..13) for (x in 10..13) {
            assertEquals(1.0f, aligned.data[y * 30 + x], 1e-6f)
        }
    }

    @Test
    fun `detects a pure rotation and recovers something close to the inverse angle`() {
        val reference = blockImage(width = 30, height = 30, blockX = 20, blockY = 15)
        val target = ImageTransformer.rotateAndScale(reference, rotationDegrees = 4.0, scale = 1.0)

        val transform = SimilarityEstimator.estimate(
            reference, target,
            maxShift = 3, sampleStride = 1,
            rotationRangeDegrees = 6.0, rotationStepDegrees = 1.0,
            scaleRange = 0.0, scaleStep = 1.0
        )

        // rotating reference by +4 to make target means the correction is
        // close to -4; allow one grid step of slack for resampling noise.
        assertTrue(
            "expected rotation near -4, got ${transform.rotationDegrees}",
            abs(transform.rotationDegrees - (-4.0)) <= 1.0
        )
    }

    @Test
    fun `applying the found transform improves the match with the reference`() {
        val reference = blockImage(width = 30, height = 30, blockX = 10, blockY = 10)
        val target = TranslationAligner.applyShift(reference, dx = 3, dy = 2)

        val transform = SimilarityEstimator.estimate(
            reference, target,
            maxShift = 5, sampleStride = 1,
            rotationRangeDegrees = 0.0, rotationStepDegrees = 1.0,
            scaleRange = 0.0, scaleStep = 1.0
        )
        val aligned = SimilarityEstimator.apply(target, transform)

        for (y in 10..13) for (x in 10..13) assertEquals(1.0f, aligned.data[y * 30 + x], 1e-6f)
    }

    private fun blockImage(width: Int, height: Int, blockX: Int, blockY: Int): LinearImage {
        val data = FloatArray(width * height)
        for (y in blockY until (blockY + 4).coerceAtMost(height)) {
            for (x in blockX until (blockX + 4).coerceAtMost(width)) {
                data[y * width + x] = 1.0f
            }
        }
        return LinearImage(width, height, 1, 16, data)
    }
}
