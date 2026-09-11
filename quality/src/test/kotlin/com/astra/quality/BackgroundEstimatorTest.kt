package com.astra.quality

import com.astra.core.model.LinearImage
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundEstimatorTest {

    @Test
    fun `estimates level and low noise from a tightly jittered background`() {
        val values = floatArrayOf(0.099f, 0.100f, 0.101f, 0.0995f, 0.1005f, 0.099f, 0.101f, 0.100f, 0.0995f)
        val image = LinearImage(3, 3, 1, 16, values)

        val stats = BackgroundEstimator.estimate(image)

        assertEquals(0.1, stats.level, 0.001)
        assertEquals(0.0, stats.noise, 0.005) // tiny jitter -> tiny noise estimate
    }

    @Test
    fun `a few bright outliers do not drag the background level up`() {
        // 14 background pixels around 0.1, 2 very bright "stars" at 0.9 -
        // the median-based estimate should stay near the background, not
        // get pulled toward the bright pixels the way a mean would.
        val values = FloatArray(16) { 0.1f }
        values[0] = 0.9f
        values[1] = 0.9f
        val image = LinearImage(4, 4, 1, 16, values)

        val stats = BackgroundEstimator.estimate(image)

        assertEquals(0.1, stats.level, 0.001)
    }
}
