package com.intune.core.settings

import com.intune.core.dsp.music.Notation
import com.intune.core.dsp.music.NoteMapper
import com.intune.core.dsp.music.Tuning
import com.intune.core.dsp.music.Tunings
import kotlin.math.roundToInt

/** Light or dark colours. Saved in settings by [name], so don't rename the entries. */
enum class ThemeMode {
    /** Follow the phone's dark-theme setting. */
    SYSTEM,
    LIGHT,
    DARK,
}

/** Everything the user can change, with the defaults a fresh install starts from. */
data class AppSettings(
    /** Concert pitch in Hz, whole numbers from [NoteMapper.MIN_A4_HZ] to [NoteMapper.MAX_A4_HZ]. */
    val a4Hz: Float = NoteMapper.DEFAULT_A4_HZ,
    val tuning: Tuning = Tunings.DEFAULT,
    val notation: Notation = Notation.DEFAULT,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Colours from the wallpaper (Android 12+). Ignored on older phones. */
    val dynamicColor: Boolean = true,
    /** Stop the screen from dimming while the tuner is open. */
    val keepScreenOn: Boolean = true,
) {
    companion object {
        /** Rounds [hz] to whole Hz and keeps it in the range [NoteMapper] accepts. NaN gives the default. */
        fun normalizeA4(hz: Float): Float =
            if (hz.isNaN()) {
                NoteMapper.DEFAULT_A4_HZ
            } else {
                hz.coerceIn(NoteMapper.MIN_A4_HZ, NoteMapper.MAX_A4_HZ).roundToInt().toFloat()
            }
    }
}
