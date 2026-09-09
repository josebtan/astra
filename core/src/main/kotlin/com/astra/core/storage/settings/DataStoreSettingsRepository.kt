package com.astra.core.storage.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.astra.core.model.RawFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

private val Context.settingsDataStore by preferencesDataStore(name = "astra_user_settings")

/**
 * Persists [UserSettings] to disk via Jetpack DataStore (Preferences), so
 * every setting the user changes — storage location, default ISO/exposure,
 * last camera used, theme, scientific-vs-visualization default — survives
 * app restarts and process death.
 *
 * [scope] should be a long-lived scope (e.g. an application-level
 * CoroutineScope) — it is used to forward DataStore's internal stream of
 * changes to registered [SettingsListener]s.
 *
 * NOTE: this class depends on the Android framework and androidx.datastore,
 * so it cannot be compiled or unit-tested in a plain-JVM sandbox (see
 * docs/PROGRESS.md). Its logic mirrors [InMemorySettingsRepository], which
 * *is* tested, and which this class should stay behaviorally identical to.
 */
class DataStoreSettingsRepository(
    private val context: Context,
    private val scope: CoroutineScope
) : SettingsRepository {

    private object Keys {
        val storageRootPath = stringPreferencesKey("storage_root_path")
        val defaultRawFormat = stringPreferencesKey("default_raw_format")
        val defaultIso = intPreferencesKey("default_iso")
        val defaultExposureSeconds = doublePreferencesKey("default_exposure_seconds")
        val defaultBinning = intPreferencesKey("default_binning")
        val lastUsedCameraId = stringPreferencesKey("last_used_camera_id")
        val theme = stringPreferencesKey("theme")
        val useMetricUnits = booleanPreferencesKey("use_metric_units")
        val defaultScientificMode = booleanPreferencesKey("default_scientific_mode")
    }

    private val listeners = CopyOnWriteArrayList<SettingsListener>()

    init {
        scope.launch {
            context.settingsDataStore.data.map(::mapPrefsToSettings).collect { settings ->
                listeners.forEach { it.onSettingsChanged(settings) }
            }
        }
    }

    override suspend fun getSettings(): UserSettings =
        context.settingsDataStore.data.map(::mapPrefsToSettings).first()

    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings): UserSettings {
        var result: UserSettings = UserSettings()
        context.settingsDataStore.edit { prefs ->
            val current = mapPrefsToSettings(prefs)
            val updated = transform(current)
            result = updated

            prefs[Keys.storageRootPath] = updated.storageRootPath
            prefs[Keys.defaultRawFormat] = updated.defaultRawFormat.name
            updated.defaultIso?.let { prefs[Keys.defaultIso] = it } ?: prefs.remove(Keys.defaultIso)
            updated.defaultExposureSeconds?.let { prefs[Keys.defaultExposureSeconds] = it }
                ?: prefs.remove(Keys.defaultExposureSeconds)
            prefs[Keys.defaultBinning] = updated.defaultBinning
            updated.lastUsedCameraId?.let { prefs[Keys.lastUsedCameraId] = it }
                ?: prefs.remove(Keys.lastUsedCameraId)
            prefs[Keys.theme] = updated.theme.name
            prefs[Keys.useMetricUnits] = updated.useMetricUnits
            prefs[Keys.defaultScientificMode] = updated.defaultScientificMode
        }
        listeners.forEach { it.onSettingsChanged(result) }
        return result
    }

    override fun addListener(listener: SettingsListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: SettingsListener) {
        listeners.remove(listener)
    }

    private fun mapPrefsToSettings(prefs: Preferences): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            storageRootPath = prefs[Keys.storageRootPath] ?: defaults.storageRootPath,
            defaultRawFormat = prefs[Keys.defaultRawFormat]
                ?.let { runCatching { RawFormat.valueOf(it) }.getOrNull() }
                ?: defaults.defaultRawFormat,
            defaultIso = prefs[Keys.defaultIso],
            defaultExposureSeconds = prefs[Keys.defaultExposureSeconds],
            defaultBinning = prefs[Keys.defaultBinning] ?: defaults.defaultBinning,
            lastUsedCameraId = prefs[Keys.lastUsedCameraId],
            theme = prefs[Keys.theme]
                ?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
                ?: defaults.theme,
            useMetricUnits = prefs[Keys.useMetricUnits] ?: defaults.useMetricUnits,
            defaultScientificMode = prefs[Keys.defaultScientificMode] ?: defaults.defaultScientificMode
        )
    }
}
