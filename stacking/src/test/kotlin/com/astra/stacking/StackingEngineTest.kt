package com.astra.stacking

import com.astra.core.model.LinearImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StackingEngineTest {

    @Test
    fun `mean combines frames by simple averaging, including outliers`() {
        val frames = listOf(image(0.1f), image(0.2f), image(0.9f))

        val result = StackingEngine.stack(frames, StackingMethod.MEAN)

        assertEquals((0.1f + 0.2f + 0.9f) / 3f, result.stackedImage.data[0], 1e-5f)
        assertEquals(0, result.report.rejectedSampleCount) // mean never rejects anything
    }

    @Test
    fun `median rejects a single outlier per pixel`() {
        val frames = listOf(image(0.10f), image(0.11f), image(0.99f))

        val result = StackingEngine.stack(frames, StackingMethod.MEDIAN)

        assertEquals(0.11f, result.stackedImage.data[0], 1e-6f)
    }

    @Test
    fun `sigma clip excludes a clear outlier and reports it as rejected`() {
        // Background tightly clustered at 0.10, one outlier at 0.95, and a
        // threshold with clear margin on both sides so the test isn't
        // sensitive to floating-point rounding at a near-exact boundary.
        val frames = listOf(image(0.10f), image(0.10f), image(0.10f), image(0.10f), image(0.95f))

        val result = StackingEngine.stack(frames, StackingMethod.SIGMA_CLIP, sigmaThreshold = 1.5)

        // outlier excluded -> result should stay close to the background, not be pulled toward 0.95
        assertEquals(0.10f, result.stackedImage.data[0], 1e-4f)
        // 2x2 image, every pixel identical -> the outlier is rejected once per pixel = 4 total
        assertEquals(4, result.report.rejectedSampleCount)
    }

    @Test
    fun `a perfectly uniform stack rejects nothing under sigma clip`() {
        val frames = listOf(image(0.5f), image(0.5f), image(0.5f))

        val result = StackingEngine.stack(frames, StackingMethod.SIGMA_CLIP)

        assertEquals(0.5f, result.stackedImage.data[0], 1e-6f)
        assertEquals(0, result.report.rejectedSampleCount)
    }

    @Test
    fun `rejects frames with mismatched dimensions`() {
        val a = image(0.1f)
        val b = LinearImage(width = 3, height = 1, channels = 1, bitDepth = 16, data = FloatArray(3))

        var threw = false
        try {
            StackingEngine.stack(listOf(a, b))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `rejects an empty frame list`() {
        var threw = false
        try {
            StackingEngine.stack(emptyList())
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `toUserMessage produces a non-blank summary mentioning the method`() {
        val result = StackingEngine.stack(listOf(image(0.3f), image(0.3f)), StackingMethod.MEAN)

        val message = result.report.toUserMessage()

        assertTrue(message.isNotBlank())
        assertTrue(message.contains("mean", ignoreCase = true))
    }

    private fun image(value: Float) = LinearImage(width = 2, height = 2, channels = 1, bitDepth = 16, data = FloatArray(4) { value })
}
