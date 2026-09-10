package com.astra.calibration

import com.astra.core.model.LinearImage

/**
 * Combines multiple same-shaped frames (bias, dark or flat) into one
 * "master" frame.
 *
 * Uses a per-pixel median rather than a mean: it naturally rejects
 * outliers (a cosmic-ray hit on one frame, a single bad exposure) without
 * needing any threshold to configure, which matters for the "as automatic
 * as possible" goal — there's no sigma-clipping parameter the user has to
 * get right for this to work well.
 */
object MasterFrameBuilder {

    fun medianCombine(frames: List<LinearImage>): LinearImage {
        require(frames.isNotEmpty()) { "Cannot build a master frame from zero input frames" }
        val first = frames.first()
        frames.forEach { frame ->
            require(
                frame.width == first.width &&
                    frame.height == first.height &&
                    frame.channels == first.channels
            ) { "All frames must share the same dimensions to be combined" }
        }

        val pixelCount = first.data.size
        val result = FloatArray(pixelCount)
        val column = FloatArray(frames.size)

        for (pixelIndex in 0 until pixelCount) {
            for (frameIndex in frames.indices) {
                column[frameIndex] = frames[frameIndex].data[pixelIndex]
            }
            column.sort()
            result[pixelIndex] = median(column)
        }

        return LinearImage(first.width, first.height, first.channels, first.bitDepth, result)
    }

    private fun median(sorted: FloatArray): Float {
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
    }
}
