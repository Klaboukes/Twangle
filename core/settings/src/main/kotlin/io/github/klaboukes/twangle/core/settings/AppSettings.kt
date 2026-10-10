package io.github.klaboukes.twangle.core.settings

import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import kotlin.math.roundToInt

/** Light or dark colours. Saved in settings by [name], so don't rename the entries. */
enum class ThemeMode {
    /** Follow the phone's dark-theme setting. */
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * The look of the whole app: colours, lettering, background and the tuning dial. Each style has a
 * light and a dark version ([ThemeMode] picks which). Saved in settings by [name], so don't rename
 * the entries.
 */
enum class VisualStyle {
    PREHISTORIC,
    MIDDLE_AGES,
    CLASSIC,
    RETRO,
    CONTEMPORARY,
    FUTURISTIC,
    SPACE_AGE,
    ;

    companion object {
        val DEFAULT = CONTEMPORARY
    }
}

/**
 * How the wave view draws how far off a note is. Saved in settings by [name], so don't rename the
 * entries.
 */
enum class WaveLook {
    /** A soft ribbon in the style's own marks. */
    RIBBON,

    /** Stripes that lean the way the note is off. */
    STRIPES,

    /** A grid of little lights. */
    DOTS,

    /** Glowing specks that line up when in tune. */
    FIREFLIES,

    /** A ring that tumbles until the note is in tune. */
    LOOP,

    /** Rings that spread out (sharp) or close in (flat). */
    RIPPLES,
    ;

    companion object {
        val DEFAULT = RIBBON
    }
}

/** Everything the user can change, with the defaults a fresh install starts from. */
data class AppSettings(
    /** Concert pitch in Hz, whole numbers from [NoteMapper.MIN_A4_HZ] to [NoteMapper.MAX_A4_HZ]. */
    val a4Hz: Float = NoteMapper.DEFAULT_A4_HZ,
    val tuning: Tuning = Tunings.DEFAULT,
    val notation: Notation = Notation.DEFAULT,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val style: VisualStyle = VisualStyle.DEFAULT,
    /** Colours from the wallpaper (Android 12+), in the contemporary [style] only. Ignored on older phones. */
    val dynamicColor: Boolean = true,
    /** Stop the screen from dimming while the tuner is open. */
    val keepScreenOn: Boolean = true,
    /** One short vibration when the played note becomes in tune. */
    val vibrateInTune: Boolean = true,
    /** Show how far off a note is as a moving sound ribbon instead of in cents. */
    val showWaves: Boolean = true,
    /** What the wave view looks like. */
    val waveLook: WaveLook = WaveLook.DEFAULT,
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
