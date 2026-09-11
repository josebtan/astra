package com.astra.registration

import com.astra.core.model.LinearImage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Uses a 20x20 (even-sized) image throughout, so the geometric center
 * (width/2.0 = 10.0) lands exactly on a pixel index. An odd size (e.g.
 * 21) puts the center at 10.5, between two pixels - which broke an
 * earlier version of these tests in a subtle way (see git history).
 */
class ImageTransformerTest {

    @Test
    fun `identity transform returns the image unchanged`() {
        val image = singlePixelImage(width = 20, height = 20, x = 12, y = 10)

        val result = ImageTransformer.rotateAndScale(image, rotationDegrees = 0.0, scale = 1.0)

        assertEquals(image, result) // short-circuits to the same reference
    }

    @Test
    fun `a pixel exactly at the center stays at the center under any rotation or scale`() {
        val image = singlePixelImage(width = 20, height = 20, x = 10, y = 10) // exact center

        val rotated = ImageTransformer.rotateAndScale(image, rotationDegrees = 37.0, scale = 1.4)

        assertEquals(1.0f, rotated.data[10 * 20 + 10], 1e-6f)
    }

    @Test
    fun `scaling by 2x doubles a point's distance from the center`() {
        // bright pixel 2px right of center (10,10) -> after 2x scale, the
        // output pixel 4px right of center should sample from it.
        val image = singlePixelImage(width = 20, height = 20, x = 12, y = 10)

        val scaled = ImageTransformer.rotateAndScale(image, rotationDegrees = 0.0, scale = 2.0)

        assertEquals(1.0f, scaled.data[10 * 20 + 14], 1e-6f)
    }

    @Test
    fun `a 90-degree rotation moves a point per the documented inverse-mapping convention`() {
        // Derived directly from the formula rotateAndScale uses: with
        // theta=90 deg, an output offset of (0, +2) from center maps back
        // to a source offset of (+2, 0). This locks that convention down
        // as a regression check, not a claim about "clockwise" vs
        // "counterclockwise" in any external frame.
        val image = singlePixelImage(width = 20, height = 20, x = 12, y = 10) // offset (+2, 0) from center

        val rotated = ImageTransformer.rotateAndScale(image, rotationDegrees = 90.0, scale = 1.0)

        assertEquals(1.0f, rotated.data[12 * 20 + 10], 1e-6f) // offset (0, +2) from center -> (10, 12)
    }

    private fun singlePixelImage(width: Int, height: Int, x: Int, y: Int): LinearImage {
        val data = FloatArray(width * height)
        data[y * width + x] = 1.0f
        return LinearImage(width, height, 1, 16, data)
    }
}
