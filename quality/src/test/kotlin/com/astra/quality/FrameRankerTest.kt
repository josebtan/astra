package com.astra.quality

import com.astra.core.model.LinearImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameRankerTest {

    @Test
    fun `separates good frames from a flat bad one`() {
        val good1 = jitteredBackgroundWithPeaks(20, 20, listOf(5 to 5, 5 to 15, 15 to 5), 0.8f)
        val good2 = jitteredBackgroundWithPeaks(20, 20, listOf(3 to 3, 3 to 16, 16 to 3), 0.8f)
        val bad = jitteredBackgroundWithPeaks(20, 20, emptyList(), 0.8f)

        val ranking = FrameRanker.rank(listOf(good1, good2, bad))

        assertEquals(3, ranking.ranked.size)
        assertEquals(2, ranking.accepted.size)
        assertEquals(1, ranking.rejected.size)
        assertEquals(2, ranking.rejected[0].index) // the flat frame was the third one (index 2)
    }

    @Test
    fun `toUserMessage mentions accepted and rejected counts`() {
        val good = jitteredBackgroundWithPeaks(20, 20, listOf(5 to 5, 5 to 15, 15 to 5), 0.8f)
        val bad = jitteredBackgroundWithPeaks(20, 20, emptyList(), 0.8f)

        val message = FrameRanker.rank(listOf(good, bad)).toUserMessage()

        assertTrue(message.contains("1 accepted"))
        assertTrue(message.contains("1 rejected"))
    }

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
