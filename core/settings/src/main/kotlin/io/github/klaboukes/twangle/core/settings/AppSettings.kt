package io.github.klaboukes.twangle.core.settings

import io.github.klaboukes.twangle.core.dsp.capo.Capo
import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.drone.DroneSound
import io.github.klaboukes.twangle.core.dsp.drone.DroneVoicing
import io.github.klaboukes.twangle.core.dsp.eartraining.ChordTypeQuestions
import io.github.klaboukes.twangle.core.dsp.eartraining.Exercise
import io.github.klaboukes.twangle.core.dsp.eartraining.Interval
import io.github.klaboukes.twangle.core.dsp.eartraining.IntervalDirection
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteChoice
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteMatch
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteQuestions
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
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
 * default. Spirit level, saved as "SPIRIT_LEVEL", went in Phase 60; a saved one reads as [POINTERS],
 * which took its place.)
 */
enum class MeterLook {
    /** The half-circle dial with a needle and a numbered scale. */
    CLASSIC,

    /** A fan of little lights that opens from the centre toward the note. */
    LIGHT_ARC,

    /** The note's pointer slides along a straight track under a fixed one; in tune their tips meet. */
    POINTERS,

    /** The note is a moon sliding across the sun; in tune, a total eclipse. */
    ECLIPSE,

    /** Chevrons on the side the note is off light up and point the way to turn. */
    ARROWS,

    /** A row of light bars with a soft peak where the note is. */
    LIGHT_BARS,
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

/**
 * The tool the Practice tab shows (Phase 32, with its switch on, [ToolSwitches.practiceTab]): it
 * opens on the one used last. Saved in settings by [name], so don't rename the entries.
 */
enum class PracticeTool {
    /** The metronome, the Metronome tab's screen. */
    METRONOME,

    /** Chord charts for the picked instrument and tuning. */
    CHORDS,

    /**
     * A scale on the whole neck of the picked instrument and tuning, on the same root as [CHORDS]
     * (Phase 47; until then a second view of [CHORDS], picked with a Chords · Scales choice).
     */
    SCALES,

    /** The capo helper (Phase 33, with its own switch, [ToolSwitches.capoHelper]). */
    CAPO,

    /** Ear training (Phase 55, with its own switch, [ToolSwitches.earTraining]): Play it back, Intervals and Chord type. */
    EAR_TRAINING,
    ;

    companion object {
        /** A fresh install opens Practice on the metronome, the tab it used to be. */
        val DEFAULT = METRONOME
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
    /**
     * A preset of [instrument], or the [CustomTuning.tuning] of one of its [customTunings], lowered as
     * the player set it ([Tuning.loweredBy], 0 as written; see [tuningAsWritten]).
     */
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
    /** Stop the screen from dimming while the tuner is open and hears playing (it lets go after a quiet spell). */
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
     * Focus mode: on its side the tuner shows the compact layout, one big meter that fills the
     * screen. Off (the default), it keeps the full two-pane layout (tuner beside its controls).
     */
    val focusMode: Boolean = false,
    /**
     * The first-start tour ("Show me around") was skipped or finished, so it doesn't come back by
     * itself. An install that already had saved settings before the tour came (an update) counts as
     * having seen it (see [SettingsRepository.decideTourOnce]).
     */
    val tourSeen: Boolean = false,
    /**
     * The player has refused the microphone in Android's own dialog at least once on this install.
     * Android doesn't say whether it will show that dialog again, so the tuner uses this to tell a
     * dialog that was only dismissed (ask again) from one Android no longer shows (only Settings helps).
     */
    val micRefused: Boolean = false,
    /**
     * When Twangle was first opened on this install (or first opened in a version that noted it), in
     * ms since 1970; 0 until noted ([SettingsRepository.noteFirstUseOnce]). Google's rating window
     * never comes on the first day (Phase 50).
     */
    val firstUseMillis: Long = 0L,
    /** Google's rating window was asked for once (Phase 50), so it is never asked for again. */
    val ratingAsked: Boolean = false,
    /** The metronome's tempo, whole BPM from [ClickTrack.MIN_BPM] to [ClickTrack.MAX_BPM]. */
    val metronomeBpm: Int = ClickTrack.DEFAULT_BPM,
    /** The metronome's time signature. */
    val metronomeMeter: Meter = Meter.DEFAULT,
    /** One short vibration on every metronome beat (off until the player turns it on). */
    val metronomeVibrate: Boolean = false,
    /** The metronome's click (Phase 45): the wood block until another is picked. */
    val metronomeClickSound: ClickSound = ClickSound.DEFAULT,
    /** The Practice tab's tool used last, which it opens on (only with the Practice tab's switch on). */
    val practiceTool: PracticeTool = PracticeTool.DEFAULT,
    /** The root picked last in the Practice tab's chord charts and scales, one for both (C on a fresh install). */
    val practiceRoot: PitchClass = PitchClass.C,
    /** The chord type picked last in the Practice tab's chord charts (major on a fresh install). */
    val practiceChordQuality: ChordQuality = ChordQuality.MAJOR,
    /** The scale picked last in the Scales tool, on [practiceRoot] (major on a fresh install). */
    val practiceScaleType: ScaleType = ScaleType.MAJOR,
    /** The shapes picked last in the Practice tab's Capo helper, by their key (C shapes on a fresh install). */
    val practiceCapoShapes: SongKey = SongKey(PitchClass.C),
    /** The capo's fret picked last in the Capo helper, 0 (no capo) to [Capo.HIGHEST_FRET] (0 on a fresh install). */
    val practiceCapoFret: Int = 0,
    /** The drone's sound under the Scales tool (Phase 48): the tanpura until another is picked. */
    val droneSound: DroneSound = DroneSound.TANPURA,
    /** The drone's notes: the root with its fifth until another is picked. */
    val droneVoicing: DroneVoicing = DroneVoicing.FIFTH,
    /** The whole drone an octave lower (off until the player turns it on). */
    val droneLower: Boolean = false,
    /** Ear training's exercise used last (Phase 55), which the tool opens on: Play it back on a fresh install. */
    val earExercise: Exercise = Exercise.DEFAULT,
    /** The notes Play it back asks for, as the player chose them: the open strings on a fresh install. */
    val earNoteChoice: NoteChoice = NoteChoice.OPEN_STRINGS,
    /** How many notes Play it back plays in a row, 1 to [NoteQuestions.MAX_LENGTH]: one on a fresh install. */
    val earNotesInRow: Int = 1,
    /** Whether Play it back takes the note asked in any octave ("Any octave counts", on a fresh install) or only the exact one. */
    val earNoteMatch: NoteMatch = NoteMatch.ANY_OCTAVE,
    /** The intervals Intervals asks, never none: [Interval.DEFAULT] (thirds, fourth, fifth, major sixth, octave) on a fresh install. */
    val earIntervals: Set<Interval> = Interval.DEFAULT,
    /** How Intervals plays them, up, down or together, never none: up on a fresh install. */
    val earDirections: Set<IntervalDirection> = IntervalDirection.DEFAULT,
    /** The chord types Chord type asks, never none: major, minor and 7 ([ChordTypeQuestions.DEFAULT]) on a fresh install. */
    val earChordTypes: Set<ChordQuality> = ChordTypeQuestions.DEFAULT,
    /**
     * Left-handed (Phase 57): the chord charts and the scale neck drawn mirrored, for a guitar held the other way
     * round (off until the player turns it on). The tuner's string row doesn't change.
     */
    val leftHanded: Boolean = false,
) {
    /** The notation to write notes in: the player's [notationChoice], else the one usual in [language] (e.g. "de"). */
    fun notationFor(language: String): Notation = notationChoice ?: Notation.forLanguage(language)

    /** [tuning] as written, before the player lowered it. */
    val tuningAsWritten: Tuning
        get() = if (tuning.loweredBy == 0) {
            tuning
        } else {
            Instruments.tuningById(tuning.id) ?: pickedCustomTuning?.tuning ?: tuning
        }

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
