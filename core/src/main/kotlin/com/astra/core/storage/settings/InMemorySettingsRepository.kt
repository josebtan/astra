package com.astra.core.storage.settings

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Simple in-memory [SettingsRepository]. Used by unit tests and by Compose
 * previews. The real app uses [DataStoreSettingsRepository], which persists
 * to disk so settings survive process death — this one does not.
 */
class InMemorySettingsRepository(
    initial: UserSettings = UserSettings()
) : SettingsRepository {

    @Volatile
    private var current: UserSettings = initial

    private val listeners = CopyOnWriteArrayList<SettingsListener>()

    override suspend fun getSettings(): UserSettings = current

    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings): UserSettings {
        val updated = transform(current)
        current = updated
        listeners.forEach { it.onSettingsChanged(updated) }
        return updated
    }

    override fun addListener(listener: SettingsListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: SettingsListener) {
        listeners.remove(listener)
    }
}
