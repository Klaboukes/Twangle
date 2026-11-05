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
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [SettingsRepository] for tests. Starts from [initial]; [current] shows what was saved,
 * and setting [current] directly simulates a change made elsewhere (e.g. on the settings screen).
 * New custom tunings get the ids `custom_1`, `custom_2`, … in the order they are added.
 */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    var current: AppSettings
        get() = state.value
        set(value) {
            state.value = value
        }

    /**
     * True: every write fails as on a full phone, as the real repository's do: nothing changes, the
     * setters return normally, and adding or changing a custom tuning returns null.
     */
    var failWrites = false

    private fun write(change: (AppSettings) -> AppSettings) {
        if (!failWrites) state.update(change)
    }

    override suspend fun setA4(hz: Float) = write { it.copy(a4Hz = AppSettings.normalizeA4(hz)) }

    override suspend fun setMode(mode: TunerMode) = write { it.copy(mode = mode) }

    override suspend fun setInstrument(instrument: Instrument) = write {
        if (it.instrument == instrument) it else it.copy(instrument = instrument, tuning = instrument.defaultTuning)
    }

    override suspend fun setTuning(tuning: Tuning) = write { settings ->
        val instrument = Instruments.ofTuning(tuning.id)
            ?: settings.customTunings.firstOrNull { it.id == tuning.id }?.instrument
        settings.copy(instrument = instrument ?: settings.instrument, tuning = tuning)
    }

    override suspend fun setLoweredBy(semitones: Int) = write { settings ->
        val asWritten = settings.tuningAsWritten
        settings.copy(tuning = asWritten.lowered(semitones.coerceIn(0, asWritten.lowerableBy)))
    }

    private var lastCustomId = 0

    override suspend fun addCustomTuning(name: String, instrument: Instrument, notes: List<Note>, pick: Boolean): CustomTuning? {
        if (failWrites) return null
        val added = CustomTuning(
            "${CustomTuning.ID_PREFIX}${++lastCustomId}", CustomTuning.normalizeName(name), instrument, notes,
        )
        write {
            val withIt = it.copy(customTunings = it.customTunings + added)
            if (pick) withIt.copy(instrument = instrument, tuning = added.tuning) else withIt
        }
        return added
    }

    override suspend fun updateCustomTuning(id: String, name: String, notes: List<Note>, pick: Boolean): CustomTuning? {
        if (failWrites) return null
        val old = state.value.customTunings.firstOrNull { it.id == id } ?: return null
        val changed = CustomTuning(id, CustomTuning.normalizeName(name), old.instrument, notes)
        write { settings ->
            val picked = pick || settings.tuning.id == id
            settings.copy(
                customTunings = settings.customTunings.map { if (it.id == id) changed else it },
                instrument = if (picked) changed.instrument else settings.instrument,
                tuning = if (picked) changed.tuning else settings.tuning,
            )
        }
        return changed
    }

    override suspend fun deleteCustomTuning(id: String) = write { settings ->
        val deleted = settings.customTunings.firstOrNull { it.id == id } ?: return@write settings
        val picked = settings.tuning.id == id
        settings.copy(
            customTunings = settings.customTunings - deleted,
            instrument = if (picked) deleted.instrument else settings.instrument,
            tuning = if (picked) deleted.instrument.defaultTuning else settings.tuning,
        )
    }

    override suspend fun setNotation(notation: Notation?) = write { it.copy(notationChoice = notation) }

    override suspend fun setInTunePrecision(precision: InTunePrecision) =
        write { it.copy(inTunePrecision = precision) }

    override suspend fun setTheme(theme: ThemeMode) = write { it.copy(theme = theme) }


    override suspend fun setDynamicColor(enabled: Boolean) = write { it.copy(dynamicColor = enabled) }

    override suspend fun setKeepScreenOn(enabled: Boolean) = write { it.copy(keepScreenOn = enabled) }

    override suspend fun setVibrateInTune(enabled: Boolean) = write { it.copy(vibrateInTune = enabled) }

    override suspend fun setStringTickOff(enabled: Boolean) = write { it.copy(stringTickOff = enabled) }

    override suspend fun setUnderMeter(choice: UnderMeter) = write { it.copy(underMeter = choice) }

    override suspend fun setShowSignalBars(enabled: Boolean) = write { it.copy(showSignalBars = enabled) }

    override suspend fun setLensLook(look: LensLook) = write { it.copy(lensLook = look) }

    /** 1.1's saved focus mode switch, null without one; [takeOldFocusMode] hands it over once. */
    var oldFocusMode: Boolean? = null

    override suspend fun takeOldFocusMode(): Boolean = (oldFocusMode ?: false).also { oldFocusMode = null }

    /** Nothing is left to forget in memory. */
    override suspend fun forgetDroppedSettings() = Unit

    override suspend fun setMicRefused(refused: Boolean) = write { it.copy(micRefused = refused) }

    override suspend fun noteFirstUseOnce(nowMillis: Long) =
        write { if (it.firstUseMillis == 0L) it.copy(firstUseMillis = nowMillis) else it }

    override suspend fun setRatingAsked(asked: Boolean) = write { it.copy(ratingAsked = asked) }

    override suspend fun setMetronomeBpm(bpm: Int) = write { it.copy(metronomeBpm = ClickTrack.clampBpm(bpm)) }

    override suspend fun setMetronomeMeter(meter: Meter) = write { it.copy(metronomeMeter = meter) }

    override suspend fun setMetronomeVibrate(enabled: Boolean) = write { it.copy(metronomeVibrate = enabled) }

    override suspend fun setMetronomeClickSound(sound: ClickSound) = write { it.copy(metronomeClickSound = sound) }

    override suspend fun setMetronomeSubdivision(subdivision: Subdivision) = write { it.copy(metronomeSubdivision = subdivision) }

    override suspend fun setMetronomeTrainer(trainer: SpeedTrainer) = write { it.copy(metronomeTrainer = trainer) }

    override suspend fun setMetronomeTrainerOn(on: Boolean) = write { it.copy(metronomeTrainerOn = on) }

    override suspend fun setPracticeTool(tool: PracticeTool?) = write { it.copy(practiceTool = tool) }

    override suspend fun setPracticeRoot(root: PitchClass) = write { it.copy(practiceRoot = root) }

    override suspend fun setPracticeChordQuality(quality: ChordQuality) = write { it.copy(practiceChordQuality = quality) }

    override suspend fun setPracticeScaleType(type: ScaleType) = write { it.copy(practiceScaleType = type) }

    override suspend fun setPracticeCapoShapes(shapes: SongKey) = write { it.copy(practiceCapoShapes = shapes) }

    override suspend fun setPracticeCapoFret(fret: Int) = write { it.copy(practiceCapoFret = fret.coerceIn(0, Capo.HIGHEST_FRET)) }

    override suspend fun setDroneSound(sound: DroneSound) = write { it.copy(droneSound = sound) }

    override suspend fun setDroneVoicing(voicing: DroneVoicing) = write { it.copy(droneVoicing = voicing) }

    override suspend fun setDroneLower(lower: Boolean) = write { it.copy(droneLower = lower) }

    override suspend fun setLeftHanded(enabled: Boolean) = write { it.copy(leftHanded = enabled) }

    override suspend fun setEarExercise(exercise: Exercise) = write { it.copy(earExercise = exercise) }

    override suspend fun setEarNoteChoice(choice: NoteChoice) = write { it.copy(earNoteChoice = choice) }

    override suspend fun setEarNotesInRow(count: Int) = write { it.copy(earNotesInRow = count.coerceIn(1, NoteQuestions.MAX_LENGTH)) }

    override suspend fun setEarNoteMatch(match: NoteMatch) = write { it.copy(earNoteMatch = match) }

    override suspend fun setEarIntervals(intervals: Set<Interval>) =
        write { it.copy(earIntervals = intervals.ifEmpty { Interval.DEFAULT }) }

    override suspend fun setEarDirections(directions: Set<IntervalDirection>) =
        write { it.copy(earDirections = directions.ifEmpty { IntervalDirection.DEFAULT }) }

    override suspend fun setEarChordTypes(types: Set<ChordQuality>) =
        write { it.copy(earChordTypes = types.ifEmpty { ChordTypeQuestions.DEFAULT }) }
}
