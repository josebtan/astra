package com.astra.calibration

import com.astra.core.model.LinearImage
import org.junit.Assert.assertEquals
import org.junit.Test

class DefectCorrectorTest {

    @Test
    fun `replaces a defect with the median of its 8 neighbors`() {
        val values = floatArrayOf(
            0.1f, 0.2f, 0.3f,
            0.4f, 0.99f /* defect at (1,1) */, 0.5f,
            0.6f, 0.7f, 0.8f
        )
        val image = LinearImage(width = 3, height = 3, channels = 1, bitDepth = 16, data = values)
        val defects = listOf(DefectPixel(x = 1, y = 1, type = DefectType.HOT, confidence = 1.0))

        val corrected = DefectCorrector.correct(image, defects)

        // median of {0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8} = (0.4+0.5)/2 = 0.45
        assertEquals(0.45f, corrected.data[1 * 3 + 1], 1e-6f)
        // everything else is untouched
        assertEquals(0.1f, corrected.data[0], 1e-6f)
        assertEquals(0.8f, corrected.data[8], 1e-6f)
    }

    @Test
    fun `excludes other defect pixels from the neighbor median`() {
        // 1D row: two end pixels are defects, the only shared neighbor (middle) is good.
        val values = floatArrayOf(0.99f, 0.5f, 0.99f)
        val image = LinearImage(width = 3, height = 1, channels = 1, bitDepth = 16, data = values)
        val defects = listOf(
            DefectPixel(x = 0, y = 0, type = DefectType.HOT, confidence = 1.0),
            DefectPixel(x = 2, y = 0, type = DefectType.HOT, confidence = 1.0)
        )

        val corrected = DefectCorrector.correct(image, defects)

        assertEquals(0.5f, corrected.data[0], 1e-6f)
        assertEquals(0.5f, corrected.data[2], 1e-6f)
        assertEquals(0.5f, corrected.data[1], 1e-6f) // the good middle pixel is untouched
    }

    @Test
    fun `returns the same image unchanged when there are no defects`() {
        val image = LinearImage(width = 2, height = 2, channels = 1, bitDepth = 16, data = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f))

        val corrected = DefectCorrector.correct(image, emptyList())

        assertEquals(image, corrected) // same reference, not just equal contents
    }
}
