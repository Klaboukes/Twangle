package io.github.klaboukes.twangle.core.settings

import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import kotlinx.coroutines.flow.Flow

/**
 * The user's saved settings. [settings] emits the current values straight away and again after
 * every change. The setters persist the new value; [settings] then emits it.
 */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** Rounded to whole Hz and clamped to 415–466 Hz (see [AppSettings.normalizeA4]). */
    suspend fun setA4(hz: Float)

    suspend fun setMode(mode: TunerMode)

    suspend fun setTuning(tuning: Tuning)

    suspend fun setNotation(notation: Notation)

    suspend fun setInTunePrecision(precision: InTunePrecision)

    suspend fun setTheme(theme: ThemeMode)

    suspend fun setStyle(style: VisualStyle)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setKeepScreenOn(enabled: Boolean)

    suspend fun setVibrateInTune(enabled: Boolean)

    suspend fun setShowTuneHint(enabled: Boolean)

    suspend fun setShowWaves(enabled: Boolean)

    suspend fun setWaveLook(look: WaveLook)

    /** Remembers that the string-lock tip was dismissed (or brings it back with false). */
    suspend fun setStringLockTipSeen(seen: Boolean)
}
