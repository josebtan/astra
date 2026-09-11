package com.astra.registration

import com.astra.core.model.LinearImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationAlignerTest {

    @Test
    fun `finds a known shift between reference and a shifted target`() {
        val reference = blockImage(width = 20, height = 20, blockX = 8, blockY = 8)
        // Target's block sits 3px right and 2px up from the reference's ->
        // aligning target to reference needs shifting it by (-3, +2).
        val target = blockImage(width = 20, height = 20, blockX = 11, blockY = 6)

        val shift = TranslationAligner.estimateShift(reference, target, maxShift = 6)

        assertEquals(-3, shift.dx)
        assertEquals(2, shift.dy)
        assertTrue("expected high confidence for a clear, distinct match, got ${shift.confidence}", shift.confidence > 0.5)
    }

    @Test
    fun `applyShift actually re-aligns the block to the reference position`() {
        val reference = blockImage(width = 20, height = 20, blockX = 8, blockY = 8)
        val target = blockImage(width = 20, height = 20, blockX = 11, blockY = 6)

        val shift = TranslationAligner.estimateShift(reference, target, maxShift = 6)
        val aligned = TranslationAligner.applyShift(target, shift.dx, shift.dy)

        // The block should now sit where the reference's block is.
        for (y in 8..11) {
            for (x in 8..11) {
                assertEquals(1.0f, aligned.data[y * 20 + x], 1e-6f)
            }
        }
    }

    @Test
    fun `identical images resolve to zero shift`() {
        val image = blockImage(width = 16, height = 16, blockX = 5, blockY = 5)

        val shift = TranslationAligner.estimateShift(image, image, maxShift = 5)

        assertEquals(0, shift.dx)
        assertEquals(0, shift.dy)
    }

    @Test
    fun `a perfectly flat pair of frames has zero confidence - nothing to align against`() {
        val flat = LinearImage(width = 10, height = 10, channels = 1, bitDepth = 16, data = FloatArray(100) { 0.3f })

        val shift = TranslationAligner.estimateShift(flat, flat, maxShift = 4)

        assertEquals(0.0, shift.confidence, 1e-9)
    }

    @Test
    fun `rejects mismatched dimensions`() {
        val a = blockImage(10, 10, 2, 2)
        val b = LinearImage(width = 5, height = 5, channels = 1, bitDepth = 16, data = FloatArray(25))

        var threw = false
        try {
            TranslationAligner.estimateShift(a, b)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    /** A width x height frame of zeros with a 4x4 block of 1.0 at (blockX, blockY). */
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
