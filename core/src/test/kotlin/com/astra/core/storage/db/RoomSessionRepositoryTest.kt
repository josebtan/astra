package com.astra.core.storage.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.astra.core.model.FrameType
import com.astra.core.model.ImageFrame
import com.astra.core.model.ImageMetadata
import com.astra.core.model.ObservationSession
import com.astra.core.model.SessionStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomSessionRepositoryTest {

    private lateinit var db: AstraDatabase
    private lateinit var repository: RoomSessionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AstraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomSessionRepository(db.observationSessionDao(), db.imageFrameDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a created session can be read back with its target and empty frame list`() = runTest {
        repository.createSession(
            ObservationSession(
                id = "s1",
                name = "M42_2026_09_08",
                createdAtUtc = "2026-09-08T22:00:00Z",
                cameraModel = "POCO X7 Pro",
                target = "M42"
            )
        )

        val loaded = repository.getSession("s1")

        assertNotNull(loaded)
        assertEquals("M42", loaded!!.target)
        assertEquals(emptyList<String>(), loaded.frameIds)
    }

    @Test
    fun `adding frames to a session makes them show up in frameIds and can be fetched`() = runTest {
        repository.createSession(
            ObservationSession(id = "s2", name = "test", createdAtUtc = "t", cameraModel = "cam")
        )
        val metadata = sampleMetadata()
        repository.addFrame(
            ImageFrame(id = "f1", sessionId = "s2", frameType = FrameType.LIGHT, filePath = "RAW/f1.dng", metadata = metadata)
        )
        repository.addFrame(
            ImageFrame(id = "f2", sessionId = "s2", frameType = FrameType.LIGHT, filePath = "RAW/f2.dng", metadata = metadata)
        )

        val loaded = repository.getSession("s2")
        val frames = repository.getFramesForSession("s2")

        assertEquals(listOf("f1", "f2"), loaded!!.frameIds)
        assertEquals(2, frames.size)
        assertEquals("RAW/f1.dng", frames.first().filePath)
        // round-tripped through the embedded metadata columns correctly
        assertEquals(1600, frames.first().metadata.iso)
    }

    @Test
    fun `updating a session persists status and integration time changes`() = runTest {
        val session = ObservationSession(id = "s3", name = "n", createdAtUtc = "t", cameraModel = "c")
        repository.createSession(session)

        repository.updateSession(session.copy(status = SessionStatus.COMPLETED, totalIntegrationSeconds = 3000.0))

        val loaded = repository.getSession("s3")
        assertEquals(SessionStatus.COMPLETED, loaded!!.status)
        assertEquals(3000.0, loaded.totalIntegrationSeconds, 0.0)
    }

    @Test
    fun `an unknown session id returns null instead of throwing`() = runTest {
        assertNull(repository.getSession("does-not-exist"))
    }

    @Test
    fun `getAllSessions returns every stored session with its frame ids`() = runTest {
        repository.createSession(ObservationSession(id = "a", name = "A", createdAtUtc = "1", cameraModel = "c"))
        repository.createSession(ObservationSession(id = "b", name = "B", createdAtUtc = "2", cameraModel = "c"))
        repository.addFrame(
            ImageFrame(id = "f1", sessionId = "a", frameType = FrameType.LIGHT, filePath = "RAW/f1.dng", metadata = sampleMetadata())
        )

        val all = repository.getAllSessions()

        assertEquals(2, all.size)
        assertEquals(listOf("f1"), all.first { it.id == "a" }.frameIds)
        assertEquals(emptyList<String>(), all.first { it.id == "b" }.frameIds)
    }

    private fun sampleMetadata() = ImageMetadata(
        timestampUtc = "2026-09-08T22:00:05Z",
        latitude = null,
        longitude = null,
        altitudeMeters = null,
        cameraModel = "cam",
        sensorModel = null,
        lensModel = null,
        focalLengthMm = null,
        apertureFNumber = null,
        exposureTimeSeconds = 15.0,
        gain = null,
        iso = 1600,
        temperatureCelsius = null,
        imageWidthPx = 4000,
        imageHeightPx = 3000,
        pixelSizeMicrons = 1.4,
        orientationDegrees = 0.0
    )
}
