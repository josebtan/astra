package com.astra.core.storage.settings

import com.astra.core.model.RawFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemorySettingsRepositoryTest {

    @Test
    fun `defaults are returned when nothing has been set yet`() = runBlocking {
        val repo = InMemorySettingsRepository()

        val settings = repo.getSettings()

        assertEquals(UserSettings(), settings)
    }

    @Test
    fun `updateSettings persists the change and getSettings reflects it`() = runBlocking {
        val repo = InMemorySettingsRepository()

        repo.updateSettings { it.copy(defaultIso = 1600, defaultExposureSeconds = 15.0) }
        val settings = repo.getSettings()

        assertEquals(1600, settings.defaultIso)
        assertEquals(15.0, settings.defaultExposureSeconds!!, 0.0)
    }

    @Test
    fun `updateSettings is based on the current value, not a stale copy`() = runBlocking {
        val repo = InMemorySettingsRepository()

        // Two sequential updates, each depending on the previous result —
        // this is exactly why updateSettings takes a transform lambda
        // instead of a plain setter.
        repo.updateSettings { it.copy(defaultIso = 800) }
        repo.updateSettings { it.copy(defaultRawFormat = RawFormat.FITS) }

        val settings = repo.getSettings()
        assertEquals(800, settings.defaultIso)
        assertEquals(RawFormat.FITS, settings.defaultRawFormat)
    }

    @Test
    fun `initial value is available without waiting for a change`() = runBlocking {
        val repo = InMemorySettingsRepository(UserSettings(lastUsedCameraId = "poco-x7-pro"))

        assertEquals("poco-x7-pro", repo.getSettings().lastUsedCameraId)
    }

    @Test
    fun `registered listeners are notified when settings change`() = runBlocking {
        val repo = InMemorySettingsRepository()
        val received = mutableListOf<UserSettings>()
        repo.addListener { settings -> received.add(settings) }

        repo.updateSettings { it.copy(defaultIso = 3200) }

        assertEquals(1, received.size)
        assertEquals(3200, received.first().defaultIso)
    }

    @Test
    fun `removed listeners stop receiving updates`() = runBlocking {
        val repo = InMemorySettingsRepository()
        var callCount = 0
        val listener = SettingsListener { callCount++ }
        repo.addListener(listener)

        repo.updateSettings { it.copy(defaultIso = 100) }
        repo.removeListener(listener)
        repo.updateSettings { it.copy(defaultIso = 200) }

        assertEquals(1, callCount)
        assertTrue(repo.getSettings().defaultIso == 200)
    }
}
