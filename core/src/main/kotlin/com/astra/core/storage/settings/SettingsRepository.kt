package com.astra.core.storage.settings

/** Notified every time the stored [UserSettings] change. */
fun interface SettingsListener {
    fun onSettingsChanged(settings: UserSettings)
}

/**
 * Persists and observes [UserSettings]. Deliberately an interface with no
 * Android dependency: the real implementation ([DataStoreSettingsRepository])
 * uses Jetpack DataStore internally, but every other layer (UI, capture
 * pipeline) depends only on this contract, which keeps it testable in plain
 * Kotlin — including in this sandbox, which has no Android runtime.
 */
interface SettingsRepository {

    /** Current settings, read once. */
    suspend fun getSettings(): UserSettings

    /**
     * Atomically reads-modifies-writes the stored settings. Use this instead
     * of read-then-write from callers, e.g.:
     * ```
     * settingsRepository.updateSettings { it.copy(defaultIso = 1600) }
     * ```
     * Registered [SettingsListener]s are notified after the write succeeds.
     */
    suspend fun updateSettings(transform: (UserSettings) -> UserSettings): UserSettings

    /** Registers [listener] to be called on every future settings change. */
    fun addListener(listener: SettingsListener)

    fun removeListener(listener: SettingsListener)
}
