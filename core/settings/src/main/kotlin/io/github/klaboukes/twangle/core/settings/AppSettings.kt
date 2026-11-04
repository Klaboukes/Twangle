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
import io.github.klaboukes.twangle.core.dsp.metronome.SpeedTrainer
import io.github.klaboukes.twangle.core.dsp.metronome.Subdivision
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
 * What the tuner's meter draws (Phases 71, 72 and 78). Saved in settings by [name] under 1.1's `meter_look` key, so
 * don't rename the entries: 1.1's Classic and Light arc keep their names, and a 1.1 look that 2.0 doesn't have is read
 * by the meter look's rule (`DataStoreSettingsRepository`, docs/architecture/v2-plan.md, *Settings from 1.1*). 2.0's
 * `TRAIL` (the default from Phase 71 until the trail became a Practice tool in Phase 78) is no entry any more, so a
 * saved one reads as the default.
 */
enum class LensLook {
    /** A needle on a numbered half-circle dial (1.1's Classic meter look, the default again since Phase 78). */
    CLASSIC,

    /** A fan of little lights that opens from the centre toward the note (1.1's Light arc meter look). */
    LIGHT_ARC,

    /** Bands of stripes that drift the way the note is off and stand still in tune (1.1's strobe wave look). */
    STROBE,
    ;

    companion object {
        val DEFAULT = CLASSIC
    }
}

/**
 * A tool of the Practice tab (Phase 32, with its switch on, [ToolSwitches.practiceTab]). Saved in
 * settings by [name], so don't rename the entries. The metronome was one until 2.0 gave it a tab of its
 * own (Phase 69): a saved `METRONOME` reads as nothing used yet.
 */
enum class PracticeTool {
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

    /**
     * The pitch trail (2.0, Phase 78, with its switch, [ToolSwitches.pitchTrail]): a held note's last seconds as a
     * line, for singers and players without frets. The tuner's meter drew it from Phase 71 to 77.
     */
    PITCH_TRAIL,
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
    /**
     * Colours from the wallpaper (Android 12+) for the background, surfaces, text and accent; the in-tune green and the
     * far-off amber stay the app's own. Ignored on older phones. (1.1's styles, saved as `visual_style`, are no longer
     * read: every one shows as the one look, Ink.) Off unless switched on (the owner's review of 2026-11-03: on by
     * default, most players never saw Ink, and a green wallpaper took from the in-tune green); a 1.1 player who
     * switched it on keeps it, one who left it at 1.1's default gets Ink.
     */
    val dynamicColor: Boolean = false,
    /** Stop the screen from dimming while the tuner is open and hears playing (it lets go after a quiet spell). */
    val keepScreenOn: Boolean = true,
    /** One short vibration when the played note becomes in tune. */
    val vibrateInTune: Boolean = true,
    /**
     * The string tick-off (2.0, Phase 71), a beginner's help: each peg gets a check once its string is in tune, and
     * "In tune. Play." shows once all have one. Off by default and when missing, so a 1.1 player (who had the ticks
     * under "Show tuning help", `show_tune_hint`, which 2.0 no longer reads) starts without them.
     */
    val stringTickOff: Boolean = false,
    /** For the curious: small signal bars by the note show how well the tuner hears; a tap shows the figures. */
    val showSignalBars: Boolean = false,
    /**
     * What the tuner's lens draws (read from Phase 71 on). 1.1's display, wave look and Calm or Bold glow are no
     * longer settings: the tuner has one meter and one glow.
     */
    val lensLook: LensLook = LensLook.DEFAULT,
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
    /**
     * The metronome's clicks between the beats (Phase 62): none until the player picks some. Kept as
     * picked in a meter that doesn't offer it (6/8 and 7/8 offer none and two), where it plays as none.
     */
    val metronomeSubdivision: Subdivision = Subdivision.DEFAULT,
    /**
     * The metronome's speed trainer (Phase 62): its start, goal, step and bars, or null until it is
     * first turned on (then it is filled in from the tempo shown, [SpeedTrainer.startingAt]).
     */
    val metronomeTrainer: SpeedTrainer? = null,
    /** Whether the speed trainer is on (only with its tool switch, [ToolSwitches.speedTrainer]). */
    val metronomeTrainerOn: Boolean = false,
    /** The Practice tab's tool used last (only with the Practice tab's switch on), or null while none was (a fresh install, or 1.1's metronome). */
    val practiceTool: PracticeTool? = null,
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
