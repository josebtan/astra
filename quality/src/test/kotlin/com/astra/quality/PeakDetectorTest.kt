package com.astra.quality

import com.astra.core.model.LinearImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeakDetectorTest {

    @Test
    fun `detects a single isolated bright pixel with fwhm proxy of 1`() {
        val data = FloatArray(9 * 9) { 0.1f }
        data[4 * 9 + 4] = 1.0f // isolated bright pixel at (4,4)
        val image = LinearImage(9, 9, 1, 16, data)
        val background = BackgroundStats(level = 0.1, noise = 0.01)

        val peaks = PeakDetector.detectPeaks(image, background)

        assertEquals(1, peaks.size)
        assertEquals(4, peaks[0].x)
        assertEquals(4, peaks[0].y)
        assertEquals(1.0, peaks[0].fwhmProxy, 1e-9)
    }

    @Test
    fun `a 3-pixel-wide horizontal plateau gives a wider fwhm proxy`() {
        val data = FloatArray(9 * 9) { 0.1f }
        data[4 * 9 + 3] = 1.0f
        data[4 * 9 + 4] = 1.0f
        data[4 * 9 + 5] = 1.0f
        val image = LinearImage(9, 9, 1, 16, data)
        val background = BackgroundStats(level = 0.1, noise = 0.01)

        val peaks = PeakDetector.detectPeaks(image, background, minSeparation = 3)

        // the 3 tied-amplitude pixels collapse into a single detected peak
        // via non-max suppression (they're all within minSeparation)
        assertEquals(1, peaks.size)
        // width ~3 horizontally, ~1 vertically -> average 2.0
        assertEquals(2.0, peaks[0].fwhmProxy, 1e-9)
    }

    @Test
    fun `ignores everything below the sigma threshold`() {
        val data = FloatArray(9 * 9) { 0.1f }
        data[4 * 9 + 4] = 0.11f // barely above background, not a real peak
        val image = LinearImage(9, 9, 1, 16, data)
        val background = BackgroundStats(level = 0.1, noise = 0.01)

        val peaks = PeakDetector.detectPeaks(image, background, sigmaThreshold = 5.0)

        assertTrue(peaks.isEmpty())
    }

    @Test
    fun `two well-separated peaks are both detected`() {
        val data = FloatArray(20 * 20) { 0.1f }
        data[5 * 20 + 5] = 1.0f
        data[15 * 20 + 15] = 1.0f
        val image = LinearImage(20, 20, 1, 16, data)
        val background = BackgroundStats(level = 0.1, noise = 0.01)

        val peaks = PeakDetector.detectPeaks(image, background, minSeparation = 3)

        assertEquals(2, peaks.size)
    }
}
