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
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
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
     * a name that is already taken is up to the editor.
     */
    suspend fun addCustomTuning(name: String, instrument: Instrument, notes: List<Note>, pick: Boolean = false): CustomTuning

    /**
     * Gives the custom tuning [id] a new [name] (normalized, not empty) and [notes] (one per string
     * of its instrument, which stays), in the same place in the list. When it is picked it stays
     * picked, with the new notes; with [pick] it is picked (with its instrument) in the same write.
     * Returns the changed tuning, or null if no custom tuning has [id] (then nothing changes).
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

    suspend fun setStyle(style: VisualStyle)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setKeepScreenOn(enabled: Boolean)

    suspend fun setVibrateInTune(enabled: Boolean)

    suspend fun setShowTuneHint(enabled: Boolean)

    suspend fun setShowSignalBars(enabled: Boolean)

    /**
     * Numbers ([TunerDisplay.NEEDLE]) or wave. Replaces the old wave-view switch, which counts only until this is first
     * saved, and carries an old saved strobe display over to the wave's strobe look.
     */
    suspend fun setDisplay(display: TunerDisplay)

    /** Also turns an old saved strobe display into the wave, so the picked look shows. */
    suspend fun setWaveLook(look: WaveLook)

    suspend fun setMeterLook(look: MeterLook)

    /** Calm (the soft glow) or Bold (a stronger, larger one) when the note is reached. */
    suspend fun setInTuneEffect(effect: InTuneEffect)

    /** Focus mode: true shows the compact layout (one big meter) on its side; false (the default) the full one. */
    suspend fun setFocusMode(enabled: Boolean)

    /** Remembers that the first-start tour was skipped or finished (or brings it back with false). */
    suspend fun setTourSeen(seen: Boolean)

    /**
     * Decides once, at the app's start, whether this install is due the first-start tour, and saves
     * that: an install that already has saved settings (an update from a version before the tour)
     * counts as having seen it, a fresh one hasn't. Once decided, later saves of other settings don't
     * change it; only [setTourSeen] does.
     */
    suspend fun decideTourOnce()

    /** Remembers that the player refused the microphone in Android's dialog ([AppSettings.micRefused]). */
    suspend fun setMicRefused(refused: Boolean)

    /** The metronome's tempo, clamped to 30–250 BPM (`ClickTrack.clampBpm`). */
    suspend fun setMetronomeBpm(bpm: Int)

    suspend fun setMetronomeMeter(meter: Meter)

    suspend fun setMetronomeVibrate(enabled: Boolean)

    /** The metronome's click ([AppSettings.metronomeClickSound]). */
    suspend fun setMetronomeClickSound(sound: ClickSound)

    /** The Practice tab's tool picked last ([AppSettings.practiceTool]). */
    suspend fun setPracticeTool(tool: PracticeTool)

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

    /** The chord charts and the scale neck mirrored for a left-handed player or not ([AppSettings.leftHanded]). */
    suspend fun setLeftHanded(enabled: Boolean)
}
