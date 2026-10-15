package io.github.klaboukes.twangle.core.settings

import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import kotlin.math.roundToInt

/**
 * What the tuner compares a note against. Saved in settings by [name], so don't rename the entries.
 */
enum class TunerMode {
    /** Guitar mode: the target is the closest string of the selected tuning. */
    AUTO,

    /** Any note: the target is the nearest equal-tempered note. */
    CHROMATIC,
}

/**
 * How close a note has to be to count as "in tune". Saved in settings by [name], so don't rename
 * the entries.
 */
enum class InTunePrecision {
    /** Within 5 cents: easy to reach, fine for strumming chords. */
    RELAXED,

    /** Within 2 cents: for players who want it spot on. */
    PRECISE,
    ;

    companion object {
        val DEFAULT = RELAXED
    }
}

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
 * the entries. Listed in the order the style picker shows them. (Versions before October 2026 had
 * Prehistoric and Middle Ages; a saved one of those is read as [ANCIENT].)
 */
enum class VisualStyle {
    ANCIENT,
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
 * How the tuner shows how far off a note is, under the note. The needle dial is always there;
 * this picks what goes with it. Saved in settings by [name], so don't rename the entries. (A
 * third display, the strobe, became [WaveLook.STROBE]; a saved "STROBE" is read as [WAVE] with
 * that look.)
 */
enum class TunerDisplay {
    /** The number of cents in a pill, and the frequency. */
    NEEDLE,

    /** A moving picture (in the [WaveLook] picked) instead of a number of cents. */
    WAVE,
    ;

    companion object {
        val DEFAULT = WAVE
    }
}

/**
 * How the wave view draws how far off a note is. Saved in settings by [name], so don't rename the
 * entries. (The stripes look, saved as "STRIPES", made way for [STROBE] and is read as it.)
 */
enum class WaveLook {
    /** Rows of stripes in the style's own marks that drift and stand still in tune, like a strobe tuner. */
    STROBE,

    /** A soft ribbon in the style's own marks. */
    RIBBON,

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

/**
 * How the meter above the note draws how far off a note is. Every look sits in the same frame (a
 * 2:1 box, ±50 cents, left flat and right sharp), so picking one moves nothing else on the screen.
 * Saved in settings by [name], so don't rename the entries. (Rolling scale, Comet and Studio meter,
 * saved as "ROLLING_SCALE", "COMET" and "STUDIO_METER", went in Phase 25; a saved one reads as the
 * default.)
 */
enum class MeterLook {
    /** The half-circle dial with a needle and a numbered scale. */
    CLASSIC,

    /** A curve of glowing segments that light up from the centre out to the reading. */
    LIGHT_ARC,

    /** A bubble in a curved glass tube that floats to the side the note is off. */
    SPIRIT_LEVEL,
    ;

    companion object {
        val DEFAULT = CLASSIC
    }
}

/**
 * How strong the meter's glow is when the note is reached. Both ease in, hold and fade the same
 * way, without bursts or flashes; Bold is only brighter and larger. Saved in settings by [name], so
 * don't rename the entries.
 */
enum class InTuneEffect {
    /** The soft glow. */
    CALM,

    /** A stronger, larger glow, still smooth. */
    BOLD,
    ;

    companion object {
        val DEFAULT = CALM
    }
}

/** Everything the user can change, with the defaults a fresh install starts from. */
data class AppSettings(
    /** Concert pitch in Hz, whole numbers from [NoteMapper.MIN_A4_HZ] to [NoteMapper.MAX_A4_HZ]. */
    val a4Hz: Float = NoteMapper.DEFAULT_A4_HZ,
    /** Guitar or chromatic, as last picked on the tuner, so the app reopens in it. */
    val mode: TunerMode = TunerMode.AUTO,
    /** The instrument picked in the tuning picker; [tuning] is always one of its presets or custom tunings. */
    val instrument: Instrument = Instruments.DEFAULT,
    /** A preset of [instrument], or the [CustomTuning.tuning] of one of its [customTunings]. */
    val tuning: Tuning = Tunings.DEFAULT,
    /** The tunings the player made, for every instrument, in the order they were made. */
    val customTunings: List<CustomTuning> = emptyList(),
    /**
     * The notation the player picked in Settings, or null (Automatic) to follow the app's language
     * ([Notation.forLanguage]). Read the one to show with [notationFor].
     */
    val notationChoice: Notation? = null,
    /** How close a note has to be to count as "in tune". */
    val inTunePrecision: InTunePrecision = InTunePrecision.DEFAULT,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val style: VisualStyle = VisualStyle.DEFAULT,
    /** Colours from the wallpaper (Android 12+), in the contemporary [style] only. Ignored on older phones. */
    val dynamicColor: Boolean = true,
    /** Stop the screen from dimming while the tuner is open. */
    val keepScreenOn: Boolean = true,
    /** One short vibration when the played note becomes in tune. */
    val vibrateInTune: Boolean = true,
    /** "Tune up ↑" / "Tune down ↓" under the note while it is off pitch, also said by TalkBack. */
    val showTuneHint: Boolean = true,
    /** For the curious: small signal bars by the note show how well the tuner hears; a tap shows the figures. */
    val showSignalBars: Boolean = false,
    /** What shows how far off a note is under it: cents or the wave view. */
    val display: TunerDisplay = TunerDisplay.DEFAULT,
    /** What the wave view looks like. */
    val waveLook: WaveLook = WaveLook.DEFAULT,
    /** What the meter above the note looks like. */
    val meterLook: MeterLook = MeterLook.DEFAULT,
    /** How strong the meter's glow is when the note is reached. */
    val inTuneEffect: InTuneEffect = InTuneEffect.DEFAULT,
    /**
     * On the note, the wave keeps breathing gently (true) or holds perfectly still like the strobe
     * (false). Temporary: the owner compares both on the phone, then one goes, with this setting.
     */
    val waveBreathesInTune: Boolean = true,
    /**
     * On its side, show the full two-pane layout (tuner beside its controls) instead of the compact
     * one where the meter fills the screen.
     */
    val keepTwoPaneLandscape: Boolean = false,
    /**
     * The first-start tour ("Show me around") was skipped or finished, so it doesn't come back by
     * itself. An install that already had saved settings before the tour came (an update) counts as
     * having seen it (see [SettingsRepository.decideTourOnce]).
     */
    val tourSeen: Boolean = false,
    /** The metronome's tempo, whole BPM from [ClickTrack.MIN_BPM] to [ClickTrack.MAX_BPM]. */
    val metronomeBpm: Int = ClickTrack.DEFAULT_BPM,
    /** The metronome's time signature. */
    val metronomeMeter: Meter = Meter.DEFAULT,
    /** One short vibration on every metronome beat (off until the player turns it on). */
    val metronomeVibrate: Boolean = false,
) {
    /** The notation to write notes in: the player's [notationChoice], else the one usual in [language] (e.g. "de"). */
    fun notationFor(language: String): Notation = notationChoice ?: Notation.forLanguage(language)

    /** The custom tuning that is picked, or null when [tuning] is a preset. */
    val pickedCustomTuning: CustomTuning? get() = customTunings.firstOrNull { it.id == tuning.id }

    /** The player's own tunings for [instrument], in the order they were made. */
    fun customTuningsFor(instrument: Instrument): List<CustomTuning> =
        customTunings.filter { it.instrument == instrument }

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
