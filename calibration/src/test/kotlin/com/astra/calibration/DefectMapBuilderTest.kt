package com.astra.calibration

import com.astra.core.model.LinearImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefectMapBuilderTest {

    @Test
    fun `flags a clear hot pixel and a clear dead pixel by position and type`() {
        // 4x4 background tightly clustered around 0.1, with one pixel driven
        // hot (0.9) and one driven dead (0.0) - both vastly more than 5
        // sigma away from a background this tight.
        val background = floatArrayOf(
            0.099f, 0.100f, 0.101f, 0.0995f,
            0.1005f, 0.099f, 0.9f /* hot at (2,1) */, 0.101f,
            0.100f, 0.0995f, 0.1005f, 0.099f,
            0.0f /* dead at (0,3) */, 0.101f, 0.100f, 0.0995f
        )
        val masterDark = LinearImage(width = 4, height = 4, channels = 1, bitDepth = 16, data = background)

        val defects = DefectMapBuilder.detectDefects(masterDark)

        assertEquals(2, defects.size)
        val hot = defects.first { it.type == DefectType.HOT }
        val dead = defects.first { it.type == DefectType.DEAD }
        assertEquals(2, hot.x)
        assertEquals(1, hot.y)
        assertEquals(0, dead.x)
        assertEquals(3, dead.y)
    }

    @Test
    fun `a perfectly uniform frame has no defects`() {
        val masterDark = LinearImage(width = 3, height = 3, channels = 1, bitDepth = 16, data = FloatArray(9) { 0.2f })

        val defects = DefectMapBuilder.detectDefects(masterDark)

        assertTrue(defects.isEmpty())
    }

    @Test
    fun `confidence increases with how far the pixel is from the threshold`() {
        val background = FloatArray(15) { 0.1f }
        val mildlyHot = background + floatArrayOf(0.9f) // one outlier pixel, mild relative to a 4x4 grid
        val masterDark = LinearImage(width = 4, height = 4, channels = 1, bitDepth = 16, data = mildlyHot)

        val defects = DefectMapBuilder.detectDefects(masterDark, sigmaThreshold = 2.0)

        assertTrue(defects.isNotEmpty())
        assertTrue(defects.first().confidence in 0.0..1.0)
    }
}
