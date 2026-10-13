package io.github.klaboukes.twangle.core.settings

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
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
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

    override suspend fun setMode(mode: TunerMode) {
        dataStore.edit { it[MODE] = mode.name }
    }

    override suspend fun setTuning(tuning: Tuning) {
        dataStore.edit { it[TUNING_ID] = tuning.id }
    }

    override suspend fun setNotation(notation: Notation) {
        dataStore.edit { it[NOTATION] = notation.name }
    }

    override suspend fun setInTunePrecision(precision: InTunePrecision) {
        dataStore.edit { it[IN_TUNE_PRECISION] = precision.name }
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

    override suspend fun setShowTuneHint(enabled: Boolean) {
        dataStore.edit { it[SHOW_TUNE_HINT] = enabled }
    }

    override suspend fun setShowSignalBars(enabled: Boolean) {
        dataStore.edit { it[SHOW_SIGNAL_BARS] = enabled }
    }

    override suspend fun setDisplay(display: TunerDisplay) {
        dataStore.edit {
            // An old strobe display was showing as the wave's strobe look: keep that look for the wave.
            if (it[DISPLAY] == OLD_STROBE_DISPLAY) it[WAVE_LOOK] = WaveLook.STROBE.name
            it[DISPLAY] = display.name
            // The old wave-view switch has done its job once a display is saved.
            it.remove(SHOW_WAVES)
        }
    }

    override suspend fun setWaveLook(look: WaveLook) {
        dataStore.edit {
            // An old strobe display would otherwise keep overriding the look picked here.
            if (it[DISPLAY] == OLD_STROBE_DISPLAY) it[DISPLAY] = TunerDisplay.WAVE.name
            it[WAVE_LOOK] = look.name
        }
    }

    override suspend fun setMeterLook(look: MeterLook) {
        dataStore.edit { it[METER_LOOK] = look.name }
    }

    override suspend fun setInTuneEffect(effect: InTuneEffect) {
        dataStore.edit { it[IN_TUNE_EFFECT] = effect.name }
    }

    override suspend fun setWaveBreathesInTune(enabled: Boolean) {
        dataStore.edit { it[WAVE_BREATHES_IN_TUNE] = enabled }
    }

    override suspend fun setKeepTwoPaneLandscape(enabled: Boolean) {
        dataStore.edit { it[KEEP_TWO_PANE_LANDSCAPE] = enabled }
    }

    override suspend fun setStringLockTipSeen(seen: Boolean) {
        dataStore.edit { it[STRING_LOCK_TIP_SEEN] = seen }
    }

    private fun toSettings(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        // The strobe used to be a display of its own; now it is the wave's strobe look.
        val oldStrobe = prefs[DISPLAY] == OLD_STROBE_DISPLAY
        return AppSettings(
            a4Hz = prefs[A4_HZ]?.let(AppSettings::normalizeA4) ?: defaults.a4Hz,
            mode = enumOrNull<TunerMode>(prefs[MODE]) ?: defaults.mode,
            tuning = prefs[TUNING_ID]?.let(Tunings::byId) ?: defaults.tuning,
            notation = enumOrNull<Notation>(prefs[NOTATION]) ?: defaults.notation,
            inTunePrecision = enumOrNull<InTunePrecision>(prefs[IN_TUNE_PRECISION]) ?: defaults.inTunePrecision,
            theme = enumOrNull<ThemeMode>(prefs[THEME]) ?: defaults.theme,
            style = enumOrNull<VisualStyle>(prefs[STYLE]) ?: defaults.style,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            keepScreenOn = prefs[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            vibrateInTune = prefs[VIBRATE_IN_TUNE] ?: defaults.vibrateInTune,
            showTuneHint = prefs[SHOW_TUNE_HINT] ?: defaults.showTuneHint,
            showSignalBars = prefs[SHOW_SIGNAL_BARS] ?: defaults.showSignalBars,
            display = if (oldStrobe) {
                TunerDisplay.WAVE
            } else {
                enumOrNull<TunerDisplay>(prefs[DISPLAY]) ?: displayFromWaveSwitch(prefs[SHOW_WAVES]) ?: defaults.display
            },
            waveLook = if (oldStrobe) WaveLook.STROBE else waveLookOrNull(prefs[WAVE_LOOK]) ?: defaults.waveLook,
            meterLook = enumOrNull<MeterLook>(prefs[METER_LOOK]) ?: defaults.meterLook,
            inTuneEffect = enumOrNull<InTuneEffect>(prefs[IN_TUNE_EFFECT]) ?: defaults.inTuneEffect,
            waveBreathesInTune = prefs[WAVE_BREATHES_IN_TUNE] ?: defaults.waveBreathesInTune,
            keepTwoPaneLandscape = prefs[KEEP_TWO_PANE_LANDSCAPE] ?: defaults.keepTwoPaneLandscape,
            stringLockTipSeen = prefs[STRING_LOCK_TIP_SEEN] ?: defaults.stringLockTipSeen,
        )
    }

    companion object {
        private const val FILE_NAME = "settings"

        // Saved keys. Never rename them, or users lose their settings on update.
        private val A4_HZ = floatPreferencesKey("a4_hz")
        private val MODE = stringPreferencesKey("tuner_mode")
        private val TUNING_ID = stringPreferencesKey("tuning_id")
        private val NOTATION = stringPreferencesKey("notation")
        private val IN_TUNE_PRECISION = stringPreferencesKey("in_tune_precision")
        private val THEME = stringPreferencesKey("theme")
        private val STYLE = stringPreferencesKey("visual_style")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val VIBRATE_IN_TUNE = booleanPreferencesKey("vibrate_in_tune")
        private val SHOW_TUNE_HINT = booleanPreferencesKey("show_tune_hint")
        private val SHOW_SIGNAL_BARS = booleanPreferencesKey("show_signal_bars")
        private val DISPLAY = stringPreferencesKey("tuner_display")

        /** The strobe display of versions before it became a wave look ([WaveLook.STROBE]). */
        private const val OLD_STROBE_DISPLAY = "STROBE"

        /** The stripes look of versions before the strobe look took its place. */
        private const val OLD_STRIPES_LOOK = "STRIPES"

        /** The wave-view switch of versions before the display picker: read once, to carry it over. */
        private val SHOW_WAVES = booleanPreferencesKey("show_waves")
        private val WAVE_LOOK = stringPreferencesKey("wave_look")
        private val WAVE_BREATHES_IN_TUNE = booleanPreferencesKey("wave_breathes_in_tune")
        private val METER_LOOK = stringPreferencesKey("meter_look")
        private val IN_TUNE_EFFECT = stringPreferencesKey("in_tune_effect")
        private val STRING_LOCK_TIP_SEEN = booleanPreferencesKey("string_lock_tip_seen")
        private val KEEP_TWO_PANE_LANDSCAPE = booleanPreferencesKey("keep_two_pane_landscape")

        /** The app's settings, stored in `files/datastore/settings.preferences_pb`. Call once per process. */
        fun create(context: Context): SettingsRepository = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(
                // A damaged file is replaced with defaults, so saving works again afterwards.
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            ) { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
        )

        /** The display a saved wave-view switch stands for: on is the wave, off the needle with cents. */
        private fun displayFromWaveSwitch(showWaves: Boolean?): TunerDisplay? = when (showWaves) {
            true -> TunerDisplay.WAVE
            false -> TunerDisplay.NEEDLE
            null -> null
        }

        /** The saved look, with the old stripes look read as the strobe look that replaced it. */
        private fun waveLookOrNull(name: String?): WaveLook? =
            if (name == OLD_STRIPES_LOOK) WaveLook.STROBE else enumOrNull<WaveLook>(name)

        private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
            enumValues<E>().firstOrNull { it.name == name }
    }
}
