package com.astra.raw

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class DngTiffReaderTest {

    @Test
    fun `reads a minimal uncompressed single-strip DNG correctly`() {
        val width = 4
        val height = 2
        val blackLevel = 100
        val whiteLevel = 1123 // range = 1023
        val rawPixels = intArrayOf(100, 611, 1123, 300, 900, 1123, 100, 500)

        val file = buildMinimalDng(width, height, blackLevel, whiteLevel, rawPixels)

        val image = DngTiffReader(file).readLinearImage()

        assertEquals(width, image.width)
        assertEquals(height, image.height)
        assertEquals(1, image.channels)
        assertEquals(16, image.bitDepth)
        assertEquals(0.0f, image.data[0], 1e-6f)                     // raw == blackLevel -> 0.0
        assertEquals(1.0f, image.data[2], 1e-6f)                     // raw == whiteLevel -> 1.0
        assertEquals((611 - 100) / 1023.0f, image.data[1], 1e-6f)    // mid value, exact linearization
    }

    @Test
    fun `clamps out-of-range values instead of throwing or wrapping`() {
        val rawPixels = intArrayOf(50, 2000) // below black level, above white level
        val file = buildMinimalDng(width = 2, height = 1, blackLevel = 100, whiteLevel = 1123, rawPixels = rawPixels)

        val image = DngTiffReader(file).readLinearImage()

        assertEquals(0.0f, image.data[0], 1e-6f)
        assertEquals(1.0f, image.data[1], 1e-6f)
    }

    @Test
    fun `rejects compressed strips explicitly rather than misreading them`() {
        val file = buildMinimalDng(
            width = 2, height = 1, blackLevel = 0, whiteLevel = 1023,
            rawPixels = intArrayOf(0, 1023), compression = 5 // LZW, unsupported
        )

        var threw = false
        try {
            DngTiffReader(file).readLinearImage()
        } catch (e: IllegalStateException) {
            threw = true
        }
        org.junit.Assert.assertTrue("expected an error for unsupported compression", threw)
    }

    @Test
    fun `defaults black level to 0 and white level to the bit-depth max when tags are absent`() {
        // 8-bit sample, no BlackLevel/WhiteLevel tags at all -> should assume 0..255
        val file = buildMinimalDng(
            width = 2, height = 1, blackLevel = null, whiteLevel = null,
            rawPixels = intArrayOf(0, 255), bitsPerSample = 8
        )

        val image = DngTiffReader(file).readLinearImage()

        assertEquals(8, image.bitDepth)
        assertEquals(0.0f, image.data[0], 1e-6f)
        assertEquals(1.0f, image.data[1], 1e-6f)
    }

    /**
     * Hand-builds the smallest valid uncompressed TIFF/DNG this reader
     * supports: one IFD with every tag value fitting inline (count=1), a
     * single strip of samples right after it. Real DNGs are far more
     * elaborate (multiple IFDs, CFA pattern, EXIF sub-IFD, etc.) — this
     * only exercises the subset [DngTiffReader] actually reads.
     */
    private fun buildMinimalDng(
        width: Int,
        height: Int,
        blackLevel: Int?,
        whiteLevel: Int?,
        rawPixels: IntArray,
        bitsPerSample: Int = 16,
        compression: Int = 1
    ): File {
        data class Entry(val tag: Int, val type: Int, val count: Int, val value: Int)

        val bytesPerSample = bitsPerSample / 8
        val entries = buildList {
            add(Entry(256, 4, 1, width))                              // ImageWidth (LONG)
            add(Entry(257, 4, 1, height))                             // ImageLength (LONG)
            add(Entry(258, 3, 1, bitsPerSample))                      // BitsPerSample (SHORT)
            add(Entry(259, 3, 1, compression))                        // Compression (SHORT)
            add(Entry(277, 3, 1, 1))                                  // SamplesPerPixel (SHORT)
            add(Entry(273, 4, 1, 0))                                  // StripOffsets (LONG) - patched below
            add(Entry(279, 4, 1, rawPixels.size * bytesPerSample))    // StripByteCounts (LONG)
            if (blackLevel != null) add(Entry(50714, 4, 1, blackLevel))  // DNG BlackLevel
            if (whiteLevel != null) add(Entry(50717, 4, 1, whiteLevel))  // DNG WhiteLevel
        }

        val firstEntryOffset = 10
        val entryBlockSize = entries.size * 12
        val nextIfdOffsetPos = firstEntryOffset + entryBlockSize
        val stripDataOffset = nextIfdOffsetPos + 4

        val stripOffsetsIndex = entries.indexOfFirst { it.tag == 273 }
        val resolvedEntries = entries.toMutableList()
        resolvedEntries[stripOffsetsIndex] = entries[stripOffsetsIndex].copy(value = stripDataOffset)

        val totalSize = stripDataOffset + rawPixels.size * bytesPerSample
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(0x49.toByte()).put(0x49.toByte()) // "II" byte-order marker
        buffer.putShort(42)
        buffer.putInt(8) // first IFD starts right after the 8-byte header

        buffer.position(8)
        buffer.putShort(resolvedEntries.size.toShort())

        for (entry in resolvedEntries) {
            buffer.putShort(entry.tag.toShort())
            buffer.putShort(entry.type.toShort())
            buffer.putInt(entry.count)
            buffer.putInt(entry.value) // every test value fits inline (count=1, small values)
        }
        buffer.putInt(0) // next IFD offset: none

        buffer.position(stripDataOffset)
        for (pixel in rawPixels) {
            if (bytesPerSample == 1) buffer.put(pixel.toByte()) else buffer.putShort(pixel.toShort())
        }

        val file = Files.createTempFile("astra-dng-test", ".dng").toFile()
        file.writeBytes(buffer.array())
        return file
    }
}
