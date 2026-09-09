package com.astra.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationSessionTest {

    @Test
    fun `a freshly created session starts empty and traceable`() {
        val session = ObservationSession(
            id = "session-001",
            name = "M42_2026_09_08",
            createdAtUtc = "2026-09-08T22:00:00Z",
            cameraModel = "POCO X7 Pro",
            target = "M42"
        )

        assertEquals(SessionStatus.CREATED, session.status)
        assertTrue(session.frameIds.isEmpty())
        assertEquals(0.0, session.totalIntegrationSeconds, 0.0)
    }

    @Test
    fun `a light frame references its session and carries raw metadata`() {
        val metadata = ImageMetadata(
            timestampUtc = "2026-09-08T22:00:05Z",
            latitude = 40.4168,
            longitude = -3.7038,
            altitudeMeters = 650.0,
            cameraModel = "POCO X7 Pro",
            sensorModel = null,
            lensModel = null,
            focalLengthMm = 26.0,
            apertureFNumber = 1.8,
            exposureTimeSeconds = 15.0,
            gain = null,
            iso = 1600,
            temperatureCelsius = null,
            imageWidthPx = 4000,
            imageHeightPx = 3000,
            pixelSizeMicrons = 1.4,
            orientationDegrees = 0.0
        )

        val frame = ImageFrame(
            id = "frame-001",
            sessionId = "session-001",
            frameType = FrameType.LIGHT,
            filePath = "RAW/IMG_000001.DNG",
            metadata = metadata
        )

        assertEquals("session-001", frame.sessionId)
        assertEquals(FrameType.LIGHT, frame.frameType)
        // Astrometric fields are unresolved until the astrometry engine runs.
        assertEquals(null, frame.metadata.rightAscensionDeg)
    }
}
