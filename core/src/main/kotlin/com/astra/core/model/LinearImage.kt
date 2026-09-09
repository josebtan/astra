package com.astra.core.model

/**
 * Linear, decoded image data — the output of the RAW engine (roadmap
 * section 7) and the shared currency of everything downstream:
 * calibration, registration, stacking.
 *
 * Deliberately **not** a `data class`: [data] is a potentially large
 * `FloatArray`, and Kotlin data classes compare arrays by reference, which
 * would silently break `equals`/`hashCode` (two images with identical
 * pixels would compare unequal). Use [contentEquals] for value comparison,
 * e.g. in tests.
 *
 * For a single-channel Bayer mosaic (straight off the sensor, before
 * debayering), [channels] is 1. Once/if a debayering step is added later,
 * it would produce a 3-channel [LinearImage].
 */
class LinearImage(
    val width: Int,
    val height: Int,
    val channels: Int,
    val bitDepth: Int,
    val data: FloatArray
) {
    init {
        require(width > 0 && height > 0 && channels > 0) {
            "width, height and channels must all be positive (got $width x $height x $channels)"
        }
        require(data.size == width * height * channels) {
            "data.size=${data.size} does not match width*height*channels=" +
                "${width * height * channels} ($width*$height*$channels)"
        }
    }

    /** Value comparison, since [data] is a [FloatArray] (reference-compared by default). */
    fun contentEquals(other: LinearImage): Boolean =
        width == other.width &&
            height == other.height &&
            channels == other.channels &&
            bitDepth == other.bitDepth &&
            data.contentEquals(other.data)

    override fun toString(): String =
        "LinearImage(width=$width, height=$height, channels=$channels, " +
            "bitDepth=$bitDepth, data.size=${data.size})"
}
