package com.astra.camera.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCharacteristicsMapperTest {

    @Test
    fun `derives bit depth from white level, phone-camera-like values`() {
        // e.g. a 10-bit RAW sensor: white level 1023 -> 2^10 - 1
        val spec = sampleSpec(whiteLevel = 1023)

        val capabilities = mapToCapabilities(spec)

        assertEquals(10, capabilities.bitDepth)
    }

    @Test
    fun `falls back to 8-bit when white level is unknown`() {
        val spec = sampleSpec(whiteLevel = null)

        val capabilities = mapToCapabilities(spec)

        assertEquals(8, capabilities.bitDepth)
    }

    @Test
    fun `converts exposure range from nanoseconds to seconds`() {
        val spec = sampleSpec(
            minExposureNanos = 100_000L,          // 0.1 ms
            maxExposureNanos = 15_000_000_000L    // 15 s
        )

        val capabilities = mapToCapabilities(spec)

        assertEquals(0.0001, capabilities.exposureRangeSeconds.start, 1e-9)
        assertEquals(15.0, capabilities.exposureRangeSeconds.endInclusive, 1e-9)
    }

    @Test
    fun `iso range is null when either bound is missing`() {
        val spec = sampleSpec(isoRangeMin = 100, isoRangeMax = null)

        val capabilities = mapToCapabilities(spec)

        assertNull(capabilities.isoRange)
    }

    @Test
    fun `iso range is populated when both bounds are present`() {
        val spec = sampleSpec(isoRangeMin = 100, isoRangeMax = 3200)

        val capabilities = mapToCapabilities(spec)

        assertEquals(100..3200, capabilities.isoRange)
    }

    @Test
    fun `phone cameras never report temperature sensor, cooling or binning`() {
        val capabilities = mapToCapabilities(sampleSpec())

        assertFalse(capabilities.hasTemperatureSensor)
        assertFalse(capabilities.hasCooling)
        assertFalse(capabilities.supportsBinning)
        assertTrue(capabilities.supportsRegionOfInterest)
    }

    @Test
    fun `carries sensor dimensions and raw support through unchanged`() {
        val spec = sampleSpec(sensorWidthPx = 4000, sensorHeightPx = 3000, supportsRaw = true)

        val capabilities = mapToCapabilities(spec)

        assertEquals(4000, capabilities.sensorWidthPx)
        assertEquals(3000, capabilities.sensorHeightPx)
        assertTrue(capabilities.supportsRaw)
    }

    /** POCO X7 Pro-ish defaults, matching the example in docs/ROADMAP.md section 6. */
    private fun sampleSpec(
        sensorWidthPx: Int = 4000,
        sensorHeightPx: Int = 3000,
        pixelSizeMicrons: Double = 1.4,
        whiteLevel: Int? = 1023,
        isoRangeMin: Int? = 100,
        isoRangeMax: Int? = 3200,
        minExposureNanos: Long? = 100_000L,
        maxExposureNanos: Long? = 15_000_000_000L,
        supportsRaw: Boolean = true
    ) = CameraSensorSpec(
        sensorWidthPx = sensorWidthPx,
        sensorHeightPx = sensorHeightPx,
        pixelSizeMicrons = pixelSizeMicrons,
        whiteLevel = whiteLevel,
        isoRangeMin = isoRangeMin,
        isoRangeMax = isoRangeMax,
        minExposureNanos = minExposureNanos,
        maxExposureNanos = maxExposureNanos,
        supportsRaw = supportsRaw
    )
}
