package com.astra.registration

import com.astra.core.model.LinearImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationEngineTest {

    @Test
    fun `aligns a shifted second frame to the first and reports the shift`() {
        val reference = blockImage(20, 20, 8, 8)
        val shifted = blockImage(20, 20, 11, 6) // +3,-2 relative to reference

        val result = RegistrationEngine.register(listOf(reference, shifted), maxShift = 6, sampleStride = 1)

        assertEquals(2, result.alignedFrames.size)
        assertTrue(result.report.steps[1].aligned)
        assertEquals(-3, result.report.steps[1].dx)
        assertEquals(2, result.report.steps[1].dy)

        // the second frame's block should now line up with the reference's
        val aligned = result.alignedFrames[1]
        for (y in 8..11) for (x in 8..11) assertEquals(1.0f, aligned.data[y * 20 + x], 1e-6f)
    }

    @Test
    fun `the first frame is always its own reference, unaligned and full confidence`() {
        val reference = blockImage(10, 10, 2, 2)

        val result = RegistrationEngine.register(listOf(reference))

        assertEquals(0, result.report.steps[0].dx)
        assertEquals(0, result.report.steps[0].dy)
        assertEquals(1.0, result.report.steps[0].confidence, 1e-9)
        assertTrue(result.report.steps[0].aligned)
    }

    @Test
    fun `leaves a low-confidence frame unaligned instead of guessing`() {
        val reference = LinearImage(10, 10, 1, 16, FloatArray(100) { 0.3f }) // flat, no features
        val noisyTarget = LinearImage(10, 10, 1, 16, FloatArray(100) { 0.3f })

        val result = RegistrationEngine.register(listOf(reference, noisyTarget), minConfidence = 0.5)

        assertFalse(result.report.steps[1].aligned)
        // unaligned frame is passed through unchanged, not garbled by a bogus shift
        assertEquals(noisyTarget.data.toList(), result.alignedFrames[1].data.toList())
    }

    @Test
    fun `toUserMessage is non-blank and mentions every frame`() {
        val reference = blockImage(12, 12, 3, 3)
        val shifted = blockImage(12, 12, 5, 4)

        val result = RegistrationEngine.register(listOf(reference, shifted), maxShift = 4, sampleStride = 1)
        val message = result.report.toUserMessage()

        assertTrue(message.isNotBlank())
        assertTrue(message.contains("Frame 0"))
        assertTrue(message.contains("Frame 1"))
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
