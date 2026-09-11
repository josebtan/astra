package com.astra.quality

import com.astra.core.model.LinearImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameQualityAnalyzerTest {

    @Test
    fun `a frame with several clean bright peaks and low noise is accepted`() {
        val image = jitteredBackgroundWithPeaks(
            width = 30, height = 30,
            peaks = listOf(5 to 5, 5 to 25, 25 to 5, 25 to 25),
            peakAmplitude = 0.8f
        )

        val report = FrameQualityAnalyzer.analyze(image)

        assertTrue(report.accepted)
        assertEquals(4, report.starCount)
        assertTrue(report.reasons.isEmpty())
    }

    @Test
    fun `a flat frame with no stars is rejected for too few peaks`() {
        val image = jitteredBackgroundWithPeaks(width = 20, height = 20, peaks = emptyList(), peakAmplitude = 0.8f)

        val report = FrameQualityAnalyzer.analyze(image)

        assertFalse(report.accepted)
        assertTrue(report.reasons.any { it.contains("stars", ignoreCase = true) })
    }

    @Test
    fun `a heavily saturated frame is rejected for saturation`() {
        val data = FloatArray(30 * 30) { 0.1f }
        // a 10x10 saturated block: 100/900 ~= 11% of the frame
        for (y in 0 until 10) for (x in 0 until 10) data[y * 30 + x] = 1.0f
        val image = LinearImage(30, 30, 1, 16, data)

        val report = FrameQualityAnalyzer.analyze(image)

        assertFalse(report.accepted)
        assertTrue(report.reasons.any { it.contains("saturated", ignoreCase = true) })
    }

    @Test
    fun `toUserMessage is non-blank`() {
        val image = jitteredBackgroundWithPeaks(20, 20, listOf(5 to 5, 5 to 15, 15 to 5), 0.8f)

        val message = FrameQualityAnalyzer.analyze(image).toUserMessage()

        assertTrue(message.isNotBlank())
    }

    /** A jittered (not perfectly flat) background with isolated bright peaks at the given coordinates. */
    private fun jitteredBackgroundWithPeaks(
        width: Int,
        height: Int,
        peaks: List<Pair<Int, Int>>,
        peakAmplitude: Float
    ): LinearImage {
        val jitter = floatArrayOf(0.099f, 0.100f, 0.101f, 0.0995f, 0.1005f)
        val data = FloatArray(width * height) { i -> jitter[i % jitter.size] }
        for ((x, y) in peaks) data[y * width + x] = peakAmplitude
        return LinearImage(width, height, 1, 16, data)
    }
}
