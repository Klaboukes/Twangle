package io.github.klaboukes.twangle.core.settings

import io.github.klaboukes.twangle.core.dsp.capo.Capo
import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.drone.DroneSound
import io.github.klaboukes.twangle.core.dsp.drone.DroneVoicing
import io.github.klaboukes.twangle.core.dsp.eartraining.Exercise
import io.github.klaboukes.twangle.core.dsp.eartraining.Interval
import io.github.klaboukes.twangle.core.dsp.eartraining.IntervalDirection
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteChoice
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteMatch
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteQuestions
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.SpeedTrainer
import io.github.klaboukes.twangle.core.dsp.metronome.Subdivision
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import kotlinx.coroutines.flow.Flow

/**
 * The user's saved settings. [settings] emits the current values straight away and again after
 * every change. The setters persist the new value; [settings] then emits it. A setter never throws:
 * a write that fails (a full phone, a storage error) keeps the old value, so callers may launch their
 * writes bare.
 */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** Rounded to whole Hz and clamped to 415–466 Hz (see [AppSettings.normalizeA4]). */
    suspend fun setA4(hz: Float)

    suspend fun setMode(mode: TunerMode)

    /**
     * Picks [instrument] and its standard tuning ([Instrument.defaultTuning]), not lowered, both at
     * once. Picking the instrument already picked changes nothing, so its tuning stays.
     */
    suspend fun setInstrument(instrument: Instrument)

    /**
     * Picks [tuning] and the instrument it belongs to, so the two always match. [tuning] is a preset
     * or a saved custom tuning's [CustomTuning.tuning], lowered as much as it is ([Tuning.loweredBy]):
     * a tuning as written (another tuning picked in the panel) is no longer lowered.
     */
    suspend fun setTuning(tuning: Tuning)

    /**
     * Lowers the picked tuning [semitones] below the tuning as written (0 puts it back), kept within
     * what it can go ([Tuning.lowerableBy] of the tuning as written). Saved with it, so a restart keeps
     * it; picking another tuning or instrument, or editing or deleting the picked own tuning, ends it.
     */
    suspend fun setLoweredBy(semitones: Int)

    /**
     * Saves a new custom tuning after the player's others and returns it with its new, lasting id.
     * [name] is normalized ([CustomTuning.normalizeName]) and must not come out empty; [notes] has
     * one note per string of [instrument]. With [pick] it is also picked (with its instrument) in the
     * same write, so nothing can stop the save half way; else call [setTuning] for that. Checking for
     * a name that is already taken is up to the editor. Returns null if it couldn't be saved (a full
     * phone, a storage error); then nothing changes.
     */
    suspend fun addCustomTuning(name: String, instrument: Instrument, notes: List<Note>, pick: Boolean = false): CustomTuning?

    /**
     * Gives the custom tuning [id] a new [name] (normalized, not empty) and [notes] (one per string
     * of its instrument, which stays), in the same place in the list. When it is picked it stays
     * picked, with the new notes; with [pick] it is picked (with its instrument) in the same write.
     * Returns the changed tuning, or null if no custom tuning has [id] or it couldn't be saved (then
     * nothing changes).
     */
    suspend fun updateCustomTuning(id: String, name: String, notes: List<Note>, pick: Boolean = false): CustomTuning?

    /**
     * Removes the custom tuning [id]; an unknown id changes nothing. When it is picked, its
     * instrument's standard tuning is picked instead.
     */
    suspend fun deleteCustomTuning(id: String)

    /** Saves the player's pick, or null for Automatic: follow the app's language (see [AppSettings.notationChoice]). */
    suspend fun setNotation(notation: Notation?)

    suspend fun setInTunePrecision(precision: InTunePrecision)

    suspend fun setTheme(theme: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    /**
     * The player's own choice for the high-contrast look ([AppSettings.highContrast]): from then on it no longer follows
     * the phone. Null forgets the choice, so it follows the phone again (only the UI tests' high-contrast pass, which
     * puts back what it found).
     */
    suspend fun setHighContrast(enabled: Boolean?)

    suspend fun setKeepScreenOn(enabled: Boolean)

    suspend fun setVibrateInTune(enabled: Boolean)

    /** The player's own choice for the vibration guide ([AppSettings.vibrationGuide]): from then on it no longer follows the screen reader. */
    suspend fun setVibrationGuide(enabled: Boolean)

    suspend fun setStringTickOff(enabled: Boolean)

    /** What goes under the meter. Also removes 1.1's `show_tune_hint`, which decides only until one is picked. */
    suspend fun setUnderMeter(choice: UnderMeter)

    /**
     * The lens's look. Also removes 1.1's display, wave-view switch and wave look, which the lens look's rule reads
     * only until a look is picked (docs/architecture/v2-plan.md, *Settings from 1.1*).
     */
    suspend fun setLensLook(look: LensLook)

    /**
     * 1.1's focus mode switch, read once (2.0, Phase 73: focus mode is the tuner's button alone, not saved): removes
     * the saved choice and says whether it was on, so the tuner opens in focus mode that first time; false ever after,
     * and on an install that never had it (docs/architecture/v2-plan.md, *Settings from 1.1*).
     */
    suspend fun takeOldFocusMode(): Boolean

    /**
     * Removes what is left of settings that went: `show_intonation_check`, Phase 71's switch for the 12th-fret check's
     * way in, which went when the owner put the check in the picker sheet (2026-11-03), and `show_signal_bars`, the
     * signal bars' switch, which went with the bars (2026-11-07). Called at every start; writes nothing where none is
     * left (docs/architecture/lifecycle.md).
     */
    suspend fun forgetDroppedSettings()

    /** Remembers that the player refused the microphone in Android's dialog ([AppSettings.micRefused]). */
    suspend fun setMicRefused(refused: Boolean)

    /** Notes [nowMillis] as the first use ([AppSettings.firstUseMillis]) unless one is noted already. Called at the app's start. */
    suspend fun noteFirstUseOnce(nowMillis: Long)

    /** Remembers that Google's rating window was asked for ([AppSettings.ratingAsked]). */
    suspend fun setRatingAsked(asked: Boolean)

    /** The metronome's tempo, clamped to 30–250 BPM (`ClickTrack.clampBpm`). */
    suspend fun setMetronomeBpm(bpm: Int)

    suspend fun setMetronomeMeter(meter: Meter)

    suspend fun setMetronomeVibrate(enabled: Boolean)

    /** The metronome's click ([AppSettings.metronomeClickSound]). */
    suspend fun setMetronomeClickSound(sound: ClickSound)

    /** The metronome's clicks between the beats ([AppSettings.metronomeSubdivision]). */
    suspend fun setMetronomeSubdivision(subdivision: Subdivision)

    /** The metronome's speed trainer's plan ([AppSettings.metronomeTrainer]). */
    suspend fun setMetronomeTrainer(trainer: SpeedTrainer)

    /** Whether the speed trainer is on ([AppSettings.metronomeTrainerOn]). */
    suspend fun setMetronomeTrainerOn(on: Boolean)

    /** The Practice tab's tool picked last ([AppSettings.practiceTool]); null forgets it (nothing used yet). */
    suspend fun setPracticeTool(tool: PracticeTool?)

    /** The root picked last in the chord charts and scales, one for both ([AppSettings.practiceRoot]). */
    suspend fun setPracticeRoot(root: PitchClass)

    /** The chord type picked last in the chord charts ([AppSettings.practiceChordQuality]). */
    suspend fun setPracticeChordQuality(quality: ChordQuality)

    /** The scale picked last in the Scales tool ([AppSettings.practiceScaleType]). */
    suspend fun setPracticeScaleType(type: ScaleType)

    /** The shapes picked last in the Capo helper ([AppSettings.practiceCapoShapes]). */
    suspend fun setPracticeCapoShapes(shapes: SongKey)

    /** The capo's fret picked last in the Capo helper, kept within 0..[Capo.HIGHEST_FRET] ([AppSettings.practiceCapoFret]). */
    suspend fun setPracticeCapoFret(fret: Int)

    /** The drone's sound under the Scales tool ([AppSettings.droneSound]). */
    suspend fun setDroneSound(sound: DroneSound)

    /** The drone's notes ([AppSettings.droneVoicing]). */
    suspend fun setDroneVoicing(voicing: DroneVoicing)

    /** The drone an octave lower or not ([AppSettings.droneLower]). */
    suspend fun setDroneLower(lower: Boolean)

    /** Ear training's exercise picked last ([AppSettings.earExercise]). */
    suspend fun setEarExercise(exercise: Exercise)

    /** The notes Play it back asks for ([AppSettings.earNoteChoice]). */
    suspend fun setEarNoteChoice(choice: NoteChoice)

    /** How many notes Play it back plays in a row, kept within 1..[NoteQuestions.MAX_LENGTH] ([AppSettings.earNotesInRow]). */
    suspend fun setEarNotesInRow(count: Int)

    /** Any octave or only the exact note asked counts in Play it back ([AppSettings.earNoteMatch]). */
    suspend fun setEarNoteMatch(match: NoteMatch)

    /** The intervals Intervals asks ([AppSettings.earIntervals]); none reads back as the default. */
    suspend fun setEarIntervals(intervals: Set<Interval>)

    /** Up, down or together, as Intervals plays them ([AppSettings.earDirections]); none reads back as the default. */
    suspend fun setEarDirections(directions: Set<IntervalDirection>)

    /** The chord types Chord type asks ([AppSettings.earChordTypes]); none reads back as the default. */
    suspend fun setEarChordTypes(types: Set<ChordQuality>)

    /** The chord charts and the scale neck mirrored for a left-handed player or not ([AppSettings.leftHanded]). */
    suspend fun setLeftHanded(enabled: Boolean)
}
