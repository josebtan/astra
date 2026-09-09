package com.astra.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SessionFileStoreTest {

    @Test
    fun `creates RAW WORK and RESULTS directories under the session name`() {
        val root = Files.createTempDirectory("astra-test").toFile()
        val store = SessionFileStore(root, "M42_2026_09_08")

        assertTrue(store.rawDir.exists())
        assertTrue(store.workDir.exists())
        assertTrue(store.resultsDir.exists())
        assertEquals(File(root, "M42_2026_09_08/RAW").absolutePath, store.rawDir.absolutePath)
    }

    @Test
    fun `newRawFile refuses to overwrite an existing raw frame`() {
        val root = Files.createTempDirectory("astra-test").toFile()
        val store = SessionFileStore(root, "session-A")

        val first = store.newRawFile("IMG_000001.DNG")
        first.writeText("fake raw bytes")

        var threw = false
        try {
            store.newRawFile("IMG_000001.DNG")
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertTrue("expected overwrite protection to trigger", threw)
    }

    @Test
    fun `newWorkFile creates subfolders on demand without touching RAW`() {
        val root = Files.createTempDirectory("astra-test").toFile()
        val store = SessionFileStore(root, "session-B")

        val calibrated = store.newWorkFile("calibrated", "IMG_000001.tif")

        assertTrue(calibrated.parentFile.exists())
        assertEquals("calibrated", calibrated.parentFile.name)
        assertFalse(calibrated.exists()) // path is returned, file not created yet
        assertEquals(0, store.rawDir.listFiles()?.size ?: 0)
    }

    @Test
    fun `newResultFile points inside RESULTS`() {
        val root = Files.createTempDirectory("astra-test").toFile()
        val store = SessionFileStore(root, "session-C")

        val result = store.newResultFile("stacked.fits")

        assertEquals(store.resultsDir.absolutePath, result.parentFile.absolutePath)
    }
}
