package com.intune.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.intune.core.dsp.music.Notation
import com.intune.core.dsp.music.Tuning
import com.intune.core.dsp.music.Tunings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * [SettingsRepository] saved with Jetpack DataStore (Preferences) in the app's private files.
 *
 * Reading is forgiving: a missing, unknown or out-of-range value (e.g. a tuning removed in a later
 * version) falls back to its default, and an unreadable file gives all defaults instead of crashing
 * the tuner. Create it once per process with [create]: DataStore allows only one instance per file.
 */
class DataStoreSettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(::toSettings)
        .distinctUntilChanged()

    override suspend fun setA4(hz: Float) {
        dataStore.edit { it[A4_HZ] = AppSettings.normalizeA4(hz) }
    }

    override suspend fun setTuning(tuning: Tuning) {
        dataStore.edit { it[TUNING_ID] = tuning.id }
    }

    override suspend fun setNotation(notation: Notation) {
        dataStore.edit { it[NOTATION] = notation.name }
    }

    override suspend fun setTheme(theme: ThemeMode) {
        dataStore.edit { it[THEME] = theme.name }
    }

    override suspend fun setStyle(style: VisualStyle) {
        dataStore.edit { it[STYLE] = style.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[KEEP_SCREEN_ON] = enabled }
    }

    override suspend fun setVibrateInTune(enabled: Boolean) {
        dataStore.edit { it[VIBRATE_IN_TUNE] = enabled }
    }

    override suspend fun setShowWaves(enabled: Boolean) {
        dataStore.edit { it[SHOW_WAVES] = enabled }
    }

    override suspend fun setWaveLook(look: WaveLook) {
        dataStore.edit { it[WAVE_LOOK] = look.name }
    }

    private fun toSettings(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            a4Hz = prefs[A4_HZ]?.let(AppSettings::normalizeA4) ?: defaults.a4Hz,
            tuning = prefs[TUNING_ID]?.let(Tunings::byId) ?: defaults.tuning,
            notation = enumOrNull<Notation>(prefs[NOTATION]) ?: defaults.notation,
            theme = enumOrNull<ThemeMode>(prefs[THEME]) ?: defaults.theme,
            style = enumOrNull<VisualStyle>(prefs[STYLE]) ?: defaults.style,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            keepScreenOn = prefs[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            vibrateInTune = prefs[VIBRATE_IN_TUNE] ?: defaults.vibrateInTune,
            showWaves = prefs[SHOW_WAVES] ?: defaults.showWaves,
            waveLook = enumOrNull<WaveLook>(prefs[WAVE_LOOK]) ?: defaults.waveLook,
        )
    }

    companion object {
        private const val FILE_NAME = "settings"

        // Saved keys. Never rename them, or users lose their settings on update.
        private val A4_HZ = floatPreferencesKey("a4_hz")
        private val TUNING_ID = stringPreferencesKey("tuning_id")
        private val NOTATION = stringPreferencesKey("notation")
        private val THEME = stringPreferencesKey("theme")
        private val STYLE = stringPreferencesKey("visual_style")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val VIBRATE_IN_TUNE = booleanPreferencesKey("vibrate_in_tune")
        private val SHOW_WAVES = booleanPreferencesKey("show_waves")
        private val WAVE_LOOK = stringPreferencesKey("wave_look")

        /** The app's settings, stored in `files/datastore/settings.preferences_pb`. Call once per process. */
        fun create(context: Context): SettingsRepository = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(
                // A damaged file is replaced with defaults, so saving works again afterwards.
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            ) { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
        )

        private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
            enumValues<E>().firstOrNull { it.name == name }
    }
}
