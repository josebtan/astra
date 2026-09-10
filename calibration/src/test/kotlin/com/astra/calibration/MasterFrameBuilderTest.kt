package com.astra.calibration

import com.astra.core.model.LinearImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterFrameBuilderTest {

    @Test
    fun `median rejects a single outlier frame per pixel`() {
        val frame1 = image2x2(0.10f, 0.50f, 0.05f, 0.20f)
        val frame2 = image2x2(0.12f, 0.50f, 0.06f, 0.21f)
        val frame3 = image2x2(0.11f, 0.50f, 0.99f, 0.22f) // outlier only on pixel index 2

        val master = MasterFrameBuilder.medianCombine(listOf(frame1, frame2, frame3))

        assertEquals(0.11f, master.data[0], 1e-6f)
        assertEquals(0.50f, master.data[1], 1e-6f)
        assertEquals(0.06f, master.data[2], 1e-6f) // median of {0.05, 0.06, 0.99} = 0.06, not skewed by 0.99
        assertEquals(0.21f, master.data[3], 1e-6f)
    }

    @Test
    fun `even number of frames averages the two middle values`() {
        val frame1 = image2x2(0.10f, 0f, 0f, 0f)
        val frame2 = image2x2(0.20f, 0f, 0f, 0f)

        val master = MasterFrameBuilder.medianCombine(listOf(frame1, frame2))

        assertEquals(0.15f, master.data[0], 1e-6f)
    }

    @Test
    fun `rejects frames with mismatched dimensions`() {
        val a = image2x2(0f, 0f, 0f, 0f)
        val b = LinearImage(width = 3, height = 1, channels = 1, bitDepth = 16, data = FloatArray(3))

        var threw = false
        try {
            MasterFrameBuilder.medianCombine(listOf(a, b))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    private fun image2x2(p0: Float, p1: Float, p2: Float, p3: Float) =
        LinearImage(width = 2, height = 2, channels = 1, bitDepth = 16, data = floatArrayOf(p0, p1, p2, p3))
}
