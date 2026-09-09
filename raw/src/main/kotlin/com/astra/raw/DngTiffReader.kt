package com.astra.raw

import com.astra.core.model.LinearImage
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

private object TiffType {
    const val BYTE = 1
    const val ASCII = 2
    const val SHORT = 3
    const val LONG = 4
}

private object DngTag {
    const val IMAGE_WIDTH = 256
    const val IMAGE_LENGTH = 257
    const val BITS_PER_SAMPLE = 258
    const val COMPRESSION = 259
    const val SAMPLES_PER_PIXEL = 277
    const val STRIP_OFFSETS = 273
    const val STRIP_BYTE_COUNTS = 279
    const val BLACK_LEVEL = 50714
    const val WHITE_LEVEL = 50717
}

private data class IfdEntry(
    val tag: Int,
    val type: Int,
    val count: Int,
    val valueFieldPosition: Int,
    val rawFieldValue: Int
)

/**
 * Reads the subset of the TIFF/DNG format that ASTRA's own capture path
 * (`AndroidCameraDevice` + Android's `DngCreator`) actually produces:
 * a single IFD, an uncompressed single strip of 8- or 16-bit samples, and
 * the handful of tags needed to linearize it (BlackLevel/WhiteLevel).
 *
 * This is **not** a general-purpose TIFF/DNG parser: no support for
 * multiple strips, compressed data, sub-IFDs, or CFA pattern extraction
 * (debayering is a separate future step — see roadmap section 7). It only
 * needs to be Android-free because that's what makes it testable on a
 * plain JVM (see DngTiffReaderTest, which hand-builds minimal DNG-shaped
 * files and round-trips them) — everything here is `java.io`/`java.nio`.
 *
 * NOTE (performance, roadmap section 39): this loads the whole file into
 * memory at once. That's fine for a single frame; sessions with hundreds
 * of frames should process them one at a time rather than holding many
 * decoded [LinearImage]s in memory simultaneously — that's a concern for
 * the calibration/stacking engines that consume this, not for this reader.
 */
class DngTiffReader(private val file: File) {

    fun readLinearImage(): LinearImage {
        val buffer = readWholeFile(file)

        val byteOrder = readByteOrder(buffer)
        buffer.order(byteOrder)

        buffer.position(2)
        val magic = buffer.short.toInt()
        check(magic == 42) { "Not a valid TIFF/DNG file (magic=$magic, expected 42)" }

        val firstIfdOffset = buffer.int
        val entries = readIfd(buffer, firstIfdOffset)

        val width = entries.intValue(DngTag.IMAGE_WIDTH, buffer)
            ?: error("Missing required tag ImageWidth")
        val height = entries.intValue(DngTag.IMAGE_LENGTH, buffer)
            ?: error("Missing required tag ImageLength")
        val bitsPerSample = entries.intValue(DngTag.BITS_PER_SAMPLE, buffer) ?: 16
        val compression = entries.intValue(DngTag.COMPRESSION, buffer) ?: 1
        check(compression == 1) {
            "Only uncompressed strips are supported (Compression=$compression)"
        }
        val samplesPerPixel = entries.intValue(DngTag.SAMPLES_PER_PIXEL, buffer) ?: 1
        val stripOffset = entries.intValue(DngTag.STRIP_OFFSETS, buffer)
            ?: error("Missing required tag StripOffsets")

        val blackLevel = entries.intValue(DngTag.BLACK_LEVEL, buffer) ?: 0
        val whiteLevel = entries.intValue(DngTag.WHITE_LEVEL, buffer)
            ?: ((1 shl bitsPerSample) - 1)
        val range = whiteLevel - blackLevel
        check(range > 0) { "Invalid black/white level: black=$blackLevel white=$whiteLevel" }

        val pixelCount = width * height * samplesPerPixel
        val data = FloatArray(pixelCount)

        buffer.position(stripOffset)
        for (i in 0 until pixelCount) {
            val raw = when (bitsPerSample) {
                8 -> buffer.get().toInt() and 0xFF
                16 -> buffer.short.toInt() and 0xFFFF
                else -> error("Unsupported BitsPerSample=$bitsPerSample")
            }
            val clamped = (raw - blackLevel).coerceIn(0, range)
            data[i] = clamped.toFloat() / range.toFloat()
        }

        return LinearImage(
            width = width,
            height = height,
            channels = samplesPerPixel,
            bitDepth = bitsPerSample,
            data = data
        )
    }

    private fun readWholeFile(file: File): ByteBuffer {
        RandomAccessFile(file, "r").use { raf ->
            val bytes = ByteArray(raf.length().toInt())
            raf.readFully(bytes)
            return ByteBuffer.wrap(bytes)
        }
    }

    private fun readByteOrder(buffer: ByteBuffer): ByteOrder {
        buffer.position(0)
        val b0 = buffer.get().toInt() and 0xFF
        val b1 = buffer.get().toInt() and 0xFF
        return when {
            b0 == 0x49 && b1 == 0x49 -> ByteOrder.LITTLE_ENDIAN // "II"
            b0 == 0x4D && b1 == 0x4D -> ByteOrder.BIG_ENDIAN    // "MM"
            else -> error("Not a TIFF/DNG file: unrecognized byte-order marker")
        }
    }

    private fun readIfd(buffer: ByteBuffer, offset: Int): List<IfdEntry> {
        buffer.position(offset)
        val count = buffer.short.toInt() and 0xFFFF
        val entries = ArrayList<IfdEntry>(count)
        repeat(count) {
            val tag = buffer.short.toInt() and 0xFFFF
            val type = buffer.short.toInt() and 0xFFFF
            val valueCount = buffer.int
            val valueFieldPosition = buffer.position()
            val rawFieldValue = buffer.int
            entries.add(IfdEntry(tag, type, valueCount, valueFieldPosition, rawFieldValue))
        }
        return entries
    }

    private fun List<IfdEntry>.intValue(tag: Int, buffer: ByteBuffer): Int? =
        firstOrNull { it.tag == tag }?.let { resolveIntValue(it, buffer) }

    private fun resolveIntValue(entry: IfdEntry, buffer: ByteBuffer): Int {
        val typeSizeBytes = when (entry.type) {
            TiffType.BYTE, TiffType.ASCII -> 1
            TiffType.SHORT -> 2
            TiffType.LONG -> 4
            else -> error("Unsupported TIFF type ${entry.type} for tag ${entry.tag}")
        }
        val totalBytes = typeSizeBytes * entry.count
        val readPosition = if (totalBytes <= 4) entry.valueFieldPosition else entry.rawFieldValue
        buffer.position(readPosition)
        return when (entry.type) {
            TiffType.BYTE, TiffType.ASCII -> buffer.get().toInt() and 0xFF
            TiffType.SHORT -> buffer.short.toInt() and 0xFFFF
            TiffType.LONG -> buffer.int
            else -> error("unreachable")
        }
    }
}
