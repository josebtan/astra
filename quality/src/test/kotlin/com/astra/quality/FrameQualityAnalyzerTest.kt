package com.astra.quality

import com.astra.core.model.LinearImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameQualityAnalyzerTest {
    @Test
    fun `uniform frame has no stars and stable background`() {
        val image = LinearImage(20, 20, 1, 16, FloatArray(400) { 0.1f })
        val report = FrameQualityAnalyzer.analyze(image)

        assertEquals(0.1, report.backgroundMean, 1e-6)
        assertEquals(0, report.starCount)
        assertEquals(0, report.saturatedPixelCount)
        assertEquals(0.0, report.qualityScore, 1e-6)
    }

    @Test
    fun `isolated bright source is detected and background noise stays robust`() {
        val data = FloatArray(100) { 0.1f }
        data[55] = 0.9f
        val image = LinearImage(10, 10, 1, 16, data)
        val report = FrameQualityAnalyzer.analyze(image, FrameQualityAnalyzer.Config(minSourcePixels = 1))

        assertTrue(report.starCount >= 1)
        assertTrue(report.snr > 3.0)
        assertEquals(0, report.saturatedPixelCount)
    }

    @Test
    fun `saturated pixels are counted without modifying input`() {
        val data = FloatArray(100) { 0.1f }
        data[44] = 1.0f
        data[45] = 1.0f
        val before = data.copyOf()
        val image = LinearImage(10, 10, 1, 16, data)

        val report = FrameQualityAnalyzer.analyze(image, FrameQualityAnalyzer.Config(minSourcePixels = 1))

        assertEquals(2, report.saturatedPixelCount)
        assertEquals(2.0 / 100.0, report.saturatedPixelRatio, 1e-9)
        assertTrue(data.contentEquals(before))
    }

    @Test
    fun `elliptical source has measurable ellipticity`() {
        val data = FloatArray(25) { 0.1f }
        // Horizontal 3-pixel source: intentionally elongated.
        data[12] = 0.9f
        data[11] = 0.7f
        data[13] = 0.7f
        val image = LinearImage(5, 5, 1, 16, data)

        val report = FrameQualityAnalyzer.analyze(image, FrameQualityAnalyzer.Config(minSourcePixels = 1))

        assertTrue(report.starCount >= 1)
        assertTrue(report.medianEllipticity > 0.0)
    }

    @Test
    fun `multichannel input does not pretend to be a star detector`() {
        val image = LinearImage(5, 5, 3, 16, FloatArray(75) { 0.1f })
        val report = FrameQualityAnalyzer.analyze(image)
        assertEquals(0, report.starCount)
    }

    @Test
    fun `report produces user-facing summary`() {
        val image = LinearImage(5, 5, 1, 16, FloatArray(25) { 0.1f })
        val message = FrameQualityAnalyzer.analyze(image).toUserMessage()
        assertTrue(message.contains("SNR"))
        assertTrue(message.contains("Background"))
        assertTrue(message.contains("Stars"))
    }
}
