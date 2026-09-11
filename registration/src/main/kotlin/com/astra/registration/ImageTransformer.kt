package com.astra.registration

import com.astra.core.model.LinearImage
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Rotates and/or scales an image around its own center. Nearest-neighbor
 * sampling only (no interpolation) — consistent with [TranslationAligner],
 * which is also integer-pixel/no-interpolation. Pixels that map outside
 * the source frame are filled with [fillValue].
 *
 * Used by [SimilarityEstimator] to search candidate rotation/scale values
 * against a reference frame; not meant to be a general-purpose image
 * transform utility.
 */
object ImageTransformer {

    fun rotateAndScale(
        image: LinearImage,
        rotationDegrees: Double,
        scale: Double,
        fillValue: Float = 0f
    ): LinearImage {
        require(scale > 0.0) { "scale must be positive" }

        if (rotationDegrees == 0.0 && scale == 1.0) return image

        val centerX = image.width / 2.0
        val centerY = image.height / 2.0
        val radians = Math.toRadians(rotationDegrees)
        val cosTheta = cos(radians)
        val sinTheta = sin(radians)

        val result = FloatArray(image.data.size) { fillValue }
        for (y in 0 until image.height) {
            val dy = y - centerY
            for (x in 0 until image.width) {
                val dx = x - centerX

                // Inverse-map this output pixel back to source coordinates:
                // undo rotation, then undo scale.
                val rx = dx * cosTheta + dy * sinTheta
                val ry = -dx * sinTheta + dy * cosTheta
                val sourceX = (rx / scale + centerX).roundToInt()
                val sourceY = (ry / scale + centerY).roundToInt()

                if (sourceX in 0 until image.width && sourceY in 0 until image.height) {
                    result[y * image.width + x] = image.data[sourceY * image.width + sourceX]
                }
            }
        }

        return LinearImage(image.width, image.height, image.channels, image.bitDepth, result)
    }
}
