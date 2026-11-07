package io.github.klaboukes.twangle.core.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.FileStorage
import androidx.datastore.core.Storage
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * [SettingsRepository] saved with Jetpack DataStore (Preferences) in the app's private files.
 *
 * Reading is forgiving: a missing, unknown or out-of-range value (e.g. a tuning removed in a later
 * version) falls back to its default, and an unreadable file gives all defaults instead of crashing
 * the tuner, while a failed read is tried again ([readRetryMillis]). Damaged custom tunings are
 * skipped (see [CustomTuningCodec]), and a picked one that is gone gives its instrument's standard
 * tuning. Writing never throws: a write that fails (a full phone, a storage error) is logged through
 * [logWriteError] and changes nothing, so the app keeps running with the old value ([save]). Create it
 * once per process with [create]: DataStore allows only one instance per file. [newId] makes the ids
 * of new custom tunings.
 *
 * [deviceStore] holds what belongs to this phone alone and is left out of Android's backups
 * (`app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml`): today only
 * [AppSettings.micRefused], since a phone restored from a backup has never asked for the microphone.
 * Tests may pass the settings' own store.
 *
 * The ~40 collectors (each ViewModel's flows, the widget, the app's theme) all get the same
 * [Preferences] from DataStore after a write; they are turned into [AppSettings] once for all of them
 * ([parse]), not once per collector. A state shared in a scope (`stateIn`) was the other way, but it
 * would lag a write: a read just after one could still give the old value.
 */
class DataStoreSettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val deviceStore: DataStore<Preferences> = dataStore,
    private val newId: () -> String = { CustomTuning.ID_PREFIX + UUID.randomUUID() },
    private val logWriteError: (IOException) -> Unit = { Log.w(TAG, "Settings not saved", it) },
) : SettingsRepository {

    override val settings: Flow<AppSettings> =
        if (deviceStore === dataStore) {
            readable(dataStore).map { parse(it, it) }
        } else {
            // This phone's file is read only once the settings have been read the first time, which is after
            // their migration moved `mic_refused` into it ([MoveMicRefused]). Read side by side, the moved
            // settings could pair with this phone's file from before the move: not refused, for a moment.
            combine(readable(dataStore), readable(deviceStore, after = dataStore), ::parse)
        }.distinctUntilChanged()

    /** The last files read and the settings made of them, shared by every collector of [settings]. */
    private class Parsed(val prefs: Preferences, val device: Preferences, val settings: AppSettings)

    @Volatile
    private var lastParsed: Parsed? = null

    /** How many times the files were turned into settings ([parse]); for tests. */
    @Volatile
    internal var parseCount = 0
        private set

    /** [prefs] and [device] as settings, made only when they differ from the last ones. */
    private fun parse(prefs: Preferences, device: Preferences): AppSettings {
        lastParsed?.let { if (it.prefs == prefs && it.device == device) return it.settings }
        parseCount++
        return toSettings(prefs, device).also { lastParsed = Parsed(prefs, device, it) }
    }

    /**
     * [store]'s data, where a read that fails (an I/O error, not a damaged file: that is replaced,
     * see [create]) shows empty preferences (the defaults) if nothing was shown yet, then tries again,
     * so one failed read doesn't keep the settings at their defaults, or stop later changes, for the session.
     * With [after], [store] is read once [after] has been read the first time (or failed to be).
     */
    private fun readable(store: DataStore<Preferences>, after: DataStore<Preferences>? = null): Flow<Preferences> = flow {
        if (after != null) {
            try {
                after.data.first()
            } catch (_: IOException) {
                // Its own reading tries again; this one need not wait for that.
            }
        }
        var shown = false
        store.data
            .retryWhen { cause, attempt ->
                if (cause !is IOException) return@retryWhen false
                if (!shown) emit(emptyPreferences())
                delay(readRetryMillis(attempt))
                true
            }
            .collect {
                shown = true
                emit(it)
            }
    }

    /**
     * Every write goes through here: [change] in one DataStore edit (of [store], the settings unless
     * said). A write that fails with an [IOException] (a full phone, a storage error) is logged and
     * returns false, the saved values unchanged, instead of reaching the caller's coroutine and
     * closing the app; most of the ~50 callers launch their write bare. True when it was saved.
     */
    private suspend fun save(store: DataStore<Preferences> = dataStore, change: (MutablePreferences) -> Unit): Boolean = try {
        store.edit { change(it) }
        true
    } catch (e: IOException) {
        logWriteError(e)
        false
    }

    override suspend fun setA4(hz: Float) {
        save { it[A4_HZ] = AppSettings.normalizeA4(hz) }
    }

    override suspend fun setMode(mode: TunerMode) {
        save { it[MODE] = mode.name }
    }

    override suspend fun setInstrument(instrument: Instrument) {
        save {
            if (instrumentAndTuning(it, customTunings(it)).first == instrument) return@save
            it[INSTRUMENT_ID] = instrument.id
            it[TUNING_ID] = instrument.defaultTuning.id
            it.remove(TUNING_LOWERED_BY)
        }
    }

    override suspend fun setTuning(tuning: Tuning) {
        save {
            it[TUNING_ID] = tuning.id
            saveLoweredBy(it, tuning.loweredBy)
            val instrument = Instruments.ofTuning(tuning.id)
                ?: customTunings(it).firstOrNull { custom -> custom.id == tuning.id }?.instrument
            instrument?.let { found -> it[INSTRUMENT_ID] = found.id }
        }
    }

    override suspend fun setLoweredBy(semitones: Int) {
        save {
            val picked = instrumentAndTuning(it, customTunings(it)).second
            saveLoweredBy(it, semitones.coerceIn(0, picked.loweredBy + picked.lowerableBy))
        }
    }

    override suspend fun addCustomTuning(name: String, instrument: Instrument, notes: List<Note>, pick: Boolean): CustomTuning? {
        val added = CustomTuning(newId(), CustomTuning.normalizeName(name), instrument, notes)
        val saved = save {
            saveCustomTunings(it, customTunings(it) + added)
            if (pick) pickCustomTuning(it, added)
        }
        return added.takeIf { saved }
    }

    override suspend fun updateCustomTuning(id: String, name: String, notes: List<Note>, pick: Boolean): CustomTuning? {
        var updated: CustomTuning? = null
        val saved = save {
            val tunings = customTunings(it)
            val index = tunings.indexOfFirst { custom -> custom.id == id }
            if (index < 0) return@save
            val changed = CustomTuning(id, CustomTuning.normalizeName(name), tunings[index].instrument, notes)
            saveCustomTunings(it, tunings.toMutableList().apply { set(index, changed) })
            // New notes are the ones to tune to: a picked one is no longer lowered.
            if (it[TUNING_ID] == id) it.remove(TUNING_LOWERED_BY)
            if (pick) pickCustomTuning(it, changed)
            updated = changed
        }
        return updated.takeIf { saved }
    }

    override suspend fun deleteCustomTuning(id: String) {
        save {
            val tunings = customTunings(it)
            val deleted = tunings.firstOrNull { custom -> custom.id == id } ?: return@save
            saveCustomTunings(it, tunings - deleted)
            if (it[TUNING_ID] == id) {
                it[INSTRUMENT_ID] = deleted.instrument.id
                it[TUNING_ID] = deleted.instrument.defaultTuning.id
                it.remove(TUNING_LOWERED_BY)
            }
        }
    }

    override suspend fun setNotation(notation: Notation?) {
        // No saved notation means Automatic, as on an install that never picked one.
        save { if (notation == null) it.remove(NOTATION) else it[NOTATION] = notation.name }
    }

    override suspend fun setInTunePrecision(precision: InTunePrecision) {
        save { it[IN_TUNE_PRECISION] = precision.name }
    }

    override suspend fun setTheme(theme: ThemeMode) {
        save { it[THEME] = theme.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        save { it[DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setKeepScreenOn(enabled: Boolean) {
        save { it[KEEP_SCREEN_ON] = enabled }
    }

    override suspend fun setVibrateInTune(enabled: Boolean) {
        save { it[VIBRATE_IN_TUNE] = enabled }
    }

    override suspend fun setStringTickOff(enabled: Boolean) {
        save { it[STRING_TICK_OFF] = enabled }
    }

    override suspend fun setUnderMeter(choice: UnderMeter) {
        save {
            it[UNDER_METER] = choice.name
            // 1.1's tune hint decides only until a choice is made (underMeter's rule).
            it.remove(OLD_SHOW_TUNE_HINT)
        }
    }

    override suspend fun setLensLook(look: LensLook) {
        save {
            it[METER_LOOK] = look.name
            // 1.1's display and wave look decide only until a look is picked (lensLook's rule).
            it.remove(OLD_DISPLAY)
            it.remove(OLD_SHOW_WAVES)
            it.remove(OLD_WAVE_LOOK)
        }
    }

    override suspend fun takeOldFocusMode(): Boolean {
        // Read first, so a start without the key (every start after the first) writes nothing.
        val wasOn = readable(dataStore).first()[OLD_FOCUS_MODE] ?: return false
        save { it.remove(OLD_FOCUS_MODE) }
        return wasOn
    }

    override suspend fun forgetDroppedSettings() {
        // Read first, so a start without them (every start after the first) writes nothing.
        val saved = readable(dataStore).first()
        if (DROPPED_KEYS.none { it in saved }) return
        save { prefs -> DROPPED_KEYS.forEach { prefs.remove(it) } }
    }

    override suspend fun setMicRefused(refused: Boolean) {
        save(deviceStore) { it[MIC_REFUSED] = refused }
    }

    override suspend fun noteFirstUseOnce(nowMillis: Long) {
        save { if (FIRST_USE_MILLIS !in it) it[FIRST_USE_MILLIS] = nowMillis }
    }

    override suspend fun setRatingAsked(asked: Boolean) {
        save { it[RATING_ASKED] = asked }
    }

    override suspend fun setMetronomeBpm(bpm: Int) {
        save { it[METRONOME_BPM] = ClickTrack.clampBpm(bpm) }
    }

    override suspend fun setMetronomeMeter(meter: Meter) {
        save { it[METRONOME_METER] = meter.name }
    }

    override suspend fun setMetronomeVibrate(enabled: Boolean) {
        save { it[METRONOME_VIBRATE] = enabled }
    }

    override suspend fun setMetronomeClickSound(sound: ClickSound) {
        save { it[METRONOME_CLICK_SOUND] = sound.name }
    }

    override suspend fun setMetronomeSubdivision(subdivision: Subdivision) {
        save { it[METRONOME_SUBDIVISION] = subdivision.name }
    }

    override suspend fun setMetronomeTrainer(trainer: SpeedTrainer) {
        save {
            it[TRAINER_START] = trainer.startBpm
            it[TRAINER_GOAL] = trainer.goalBpm
            it[TRAINER_STEP] = trainer.stepBpm
            it[TRAINER_BARS] = trainer.bars
        }
    }

    override suspend fun setMetronomeTrainerOn(on: Boolean) {
        save { it[TRAINER_ON] = on }
    }

    override suspend fun setPracticeTool(tool: PracticeTool?) {
        save {
            if (tool == null) it.remove(PRACTICE_TOOL) else it[PRACTICE_TOOL] = tool.name
            // The old Chords · Scales choice has had its say once a tool is picked (see [practiceTool]).
            it.remove(OLD_PRACTICE_CHART)
        }
    }

    override suspend fun setPracticeRoot(root: PitchClass) {
        save { it[PRACTICE_ROOT] = root.name }
    }

    override suspend fun setPracticeChordQuality(quality: ChordQuality) {
        save { it[PRACTICE_CHORD_QUALITY] = quality.name }
    }

    override suspend fun setPracticeScaleType(type: ScaleType) {
        save { it[PRACTICE_SCALE_TYPE] = type.name }
    }

    override suspend fun setPracticeCapoShapes(shapes: SongKey) {
        save {
            it[PRACTICE_CAPO_SHAPES] = shapes.tonic.name
            it[PRACTICE_CAPO_MINOR] = shapes.minor
        }
    }

    override suspend fun setPracticeCapoFret(fret: Int) {
        save { it[PRACTICE_CAPO_FRET] = fret.coerceIn(0, Capo.HIGHEST_FRET) }
    }

    override suspend fun setDroneSound(sound: DroneSound) {
        save { it[DRONE_SOUND] = sound.name }
    }

    override suspend fun setDroneVoicing(voicing: DroneVoicing) {
        save { it[DRONE_VOICING] = voicing.name }
    }

    override suspend fun setDroneLower(lower: Boolean) {
        save { it[DRONE_LOWER] = lower }
    }

    override suspend fun setLeftHanded(enabled: Boolean) {
        save { it[LEFT_HANDED] = enabled }
    }

    override suspend fun setEarExercise(exercise: Exercise) {
        save { it[EAR_EXERCISE] = exercise.name }
    }

    override suspend fun setEarNoteChoice(choice: NoteChoice) {
        save { it[EAR_NOTE_CHOICE] = choice.name }
    }

    override suspend fun setEarNotesInRow(count: Int) {
        save { it[EAR_NOTES_IN_ROW] = count.coerceIn(1, NoteQuestions.MAX_LENGTH) }
    }

    override suspend fun setEarNoteMatch(match: NoteMatch) {
        save { it[EAR_NOTE_MATCH] = match.name }
    }

    override suspend fun setEarIntervals(intervals: Set<Interval>) {
        save { it[EAR_INTERVALS] = intervals.mapTo(HashSet()) { interval -> interval.name } }
    }

    override suspend fun setEarDirections(directions: Set<IntervalDirection>) {
        save { it[EAR_DIRECTIONS] = directions.mapTo(HashSet()) { direction -> direction.name } }
    }

    override suspend fun setEarChordTypes(types: Set<ChordQuality>) {
        save { it[EAR_CHORD_TYPES] = types.mapTo(HashSet()) { type -> type.name } }
    }

    /** The settings in [prefs], with what belongs to this phone alone from [device]. */
    private fun toSettings(prefs: Preferences, device: Preferences): AppSettings {
        val defaults = AppSettings()
        // The strobe used to be a display of its own; now it is the wave's strobe look.
        val customTunings = customTunings(prefs)
        val (instrument, tuning) = instrumentAndTuning(prefs, customTunings)
        return AppSettings(
            a4Hz = prefs[A4_HZ]?.let(AppSettings::normalizeA4) ?: defaults.a4Hz,
            mode = enumOrNull<TunerMode>(prefs[MODE]) ?: defaults.mode,
            instrument = instrument,
            tuning = tuning,
            customTunings = customTunings,
            notationChoice = enumOrNull<Notation>(prefs[NOTATION]),
            inTunePrecision = enumOrNull<InTunePrecision>(prefs[IN_TUNE_PRECISION]) ?: defaults.inTunePrecision,
            theme = enumOrNull<ThemeMode>(prefs[THEME]) ?: defaults.theme,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            keepScreenOn = prefs[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            vibrateInTune = prefs[VIBRATE_IN_TUNE] ?: defaults.vibrateInTune,
            stringTickOff = prefs[STRING_TICK_OFF] ?: defaults.stringTickOff,
            underMeter = underMeter(prefs),
            lensLook = lensLook(prefs) ?: defaults.lensLook,
            micRefused = device[MIC_REFUSED] ?: defaults.micRefused,
            firstUseMillis = prefs[FIRST_USE_MILLIS] ?: defaults.firstUseMillis,
            ratingAsked = prefs[RATING_ASKED] ?: defaults.ratingAsked,
            metronomeBpm = prefs[METRONOME_BPM]?.let(ClickTrack::clampBpm) ?: defaults.metronomeBpm,
            metronomeMeter = enumOrNull<Meter>(prefs[METRONOME_METER]) ?: defaults.metronomeMeter,
            metronomeVibrate = prefs[METRONOME_VIBRATE] ?: defaults.metronomeVibrate,
            metronomeClickSound = enumOrNull<ClickSound>(prefs[METRONOME_CLICK_SOUND]) ?: defaults.metronomeClickSound,
            metronomeSubdivision = enumOrNull<Subdivision>(prefs[METRONOME_SUBDIVISION]) ?: defaults.metronomeSubdivision,
            metronomeTrainer = trainer(prefs) ?: defaults.metronomeTrainer,
            metronomeTrainerOn = prefs[TRAINER_ON] ?: defaults.metronomeTrainerOn,
            practiceTool = practiceTool(prefs),
            practiceRoot = enumOrNull<PitchClass>(prefs[PRACTICE_ROOT]) ?: defaults.practiceRoot,
            practiceChordQuality = enumOrNull<ChordQuality>(prefs[PRACTICE_CHORD_QUALITY]) ?: defaults.practiceChordQuality,
            practiceScaleType = enumOrNull<ScaleType>(prefs[PRACTICE_SCALE_TYPE]) ?: defaults.practiceScaleType,
            practiceCapoShapes = enumOrNull<PitchClass>(prefs[PRACTICE_CAPO_SHAPES])
                ?.let { SongKey(it, prefs[PRACTICE_CAPO_MINOR] ?: false) } ?: defaults.practiceCapoShapes,
            practiceCapoFret = prefs[PRACTICE_CAPO_FRET]?.takeIf { it in 0..Capo.HIGHEST_FRET } ?: defaults.practiceCapoFret,
            droneSound = enumOrNull<DroneSound>(prefs[DRONE_SOUND]) ?: defaults.droneSound,
            droneVoicing = enumOrNull<DroneVoicing>(prefs[DRONE_VOICING]) ?: defaults.droneVoicing,
            droneLower = prefs[DRONE_LOWER] ?: defaults.droneLower,
            leftHanded = prefs[LEFT_HANDED] ?: defaults.leftHanded,
            earExercise = enumOrNull<Exercise>(prefs[EAR_EXERCISE]) ?: defaults.earExercise,
            earNoteChoice = enumOrNull<NoteChoice>(prefs[EAR_NOTE_CHOICE]) ?: defaults.earNoteChoice,
            earNotesInRow = prefs[EAR_NOTES_IN_ROW]?.takeIf { it in 1..NoteQuestions.MAX_LENGTH } ?: defaults.earNotesInRow,
            earNoteMatch = enumOrNull<NoteMatch>(prefs[EAR_NOTE_MATCH]) ?: defaults.earNoteMatch,
            earIntervals = enumSetOrNull<Interval>(prefs[EAR_INTERVALS]) ?: defaults.earIntervals,
            earDirections = enumSetOrNull<IntervalDirection>(prefs[EAR_DIRECTIONS]) ?: defaults.earDirections,
            earChordTypes = enumSetOrNull<ChordQuality>(prefs[EAR_CHORD_TYPES]) ?: defaults.earChordTypes,
        )
    }

    companion object {
        private const val FILE_NAME = "settings"

        /**
         * This phone's own store, `files/datastore/this_device.preferences_pb`: left out of backups and
         * transfers to a new phone. Keep the name in step with the backup rules in `app/src/main/res/xml`.
         */
        private const val DEVICE_FILE_NAME = "this_device"
        private const val TAG = "Settings"

        // Saved keys. Never rename them, or users lose their settings on update.
        private val A4_HZ = floatPreferencesKey("a4_hz")
        private val MODE = stringPreferencesKey("tuner_mode")
        private val TUNING_ID = stringPreferencesKey("tuning_id")
        private val INSTRUMENT_ID = stringPreferencesKey("instrument_id")

        /** How many semitones the picked tuning is lowered (1 to 5; no key: as written). */
        private val TUNING_LOWERED_BY = intPreferencesKey("tuning_lowered_by")
        private val NOTATION = stringPreferencesKey("notation")
        private val IN_TUNE_PRECISION = stringPreferencesKey("in_tune_precision")
        private val THEME = stringPreferencesKey("theme")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val VIBRATE_IN_TUNE = booleanPreferencesKey("vibrate_in_tune")
        /** The string tick-off (2.0); 1.1's `show_tune_hint` ("Show tuning help") has no say in it. */
        private val STRING_TICK_OFF = booleanPreferencesKey("string_tick_off")

        /** What goes under the meter ([UnderMeter], by its name; 2.0, Phase 78). */
        private val UNDER_METER = stringPreferencesKey("under_meter")

        /**
         * 1.1's "Show tune up/down hint" (later "Show tuning help"), on by default: read only for the rule of what goes
         * under the meter ([underMeter]), and removed once a choice is made.
         */
        private val OLD_SHOW_TUNE_HINT = booleanPreferencesKey("show_tune_hint")
        /** The meter's look ([LensLook]), under 1.1's meter look's key, where 1.1's Classic and Light arc keep their names. */
        private val METER_LOOK = stringPreferencesKey("meter_look")

        /*
         * What 1.1 showed under its meter: read only for the lens look's rule ([lensLook]), and removed once a look
         * is picked. 1.1's in-tune effect (`in_tune_effect`, Calm or Bold) is not read at all: 2.0 has one glow.
         */
        /** 1.1's display under the note: "NEEDLE" (the cents), "WAVE" (the wave view) or the older "STROBE". */
        private val OLD_DISPLAY = stringPreferencesKey("tuner_display")

        /** The wave-view switch of versions before 1.1's display picker, counted while no display was saved. */
        private val OLD_SHOW_WAVES = booleanPreferencesKey("show_waves")

        /** 1.1's wave look: "STROBE" (or the older "STRIPES") is the strobe. */
        private val OLD_WAVE_LOOK = stringPreferencesKey("wave_look")
        /** Whether the microphone was ever refused in Android's dialog ([AppSettings.micRefused]). */
        private val MIC_REFUSED = booleanPreferencesKey("mic_refused")

        /** When the app was first used ([AppSettings.firstUseMillis]), and whether Google's rating window was asked for. */
        private val FIRST_USE_MILLIS = longPreferencesKey("first_use_millis")
        private val RATING_ASKED = booleanPreferencesKey("rating_asked")
        /** 1.1's focus mode switch: read once and removed ([takeOldFocusMode]). */
        private val OLD_FOCUS_MODE = booleanPreferencesKey("focus_mode")

        /**
         * Settings that went, removed at start ([forgetDroppedSettings]): Phase 71's switch for the 12th-fret check's
         * way in on the tuner screen (the check is opened from the picker sheet since), and "Show signal bars", in versions up
         * to 2.0 (the owner dropped the bars on 2026-11-07; the tips say what to fix).
         */
        private val DROPPED_KEYS = listOf(
            booleanPreferencesKey("show_intonation_check"),
            booleanPreferencesKey("show_signal_bars"),
        )
        private val METRONOME_BPM = intPreferencesKey("metronome_bpm")

        /** The metronome's [Meter], by its name. */
        private val METRONOME_METER = stringPreferencesKey("metronome_meter")
        private val METRONOME_VIBRATE = booleanPreferencesKey("metronome_vibrate")

        /** The metronome's [ClickSound], by its name. */
        private val METRONOME_CLICK_SOUND = stringPreferencesKey("metronome_click_sound")

        /** The metronome's [Subdivision], by its name. */
        private val METRONOME_SUBDIVISION = stringPreferencesKey("metronome_subdivision")

        /** The speed trainer's plan, all four or none ([trainer]), and whether it is on. */
        private val TRAINER_START = intPreferencesKey("metronome_trainer_start")
        private val TRAINER_GOAL = intPreferencesKey("metronome_trainer_goal")
        private val TRAINER_STEP = intPreferencesKey("metronome_trainer_step")
        private val TRAINER_BARS = intPreferencesKey("metronome_trainer_bars")
        private val TRAINER_ON = booleanPreferencesKey("metronome_trainer_on")

        /** The speed trainer saved, kept within its lists ([SpeedTrainer.of]), or null if it never was. */
        private fun trainer(prefs: Preferences): SpeedTrainer? {
            val start = prefs[TRAINER_START] ?: return null
            val goal = prefs[TRAINER_GOAL] ?: return null
            return SpeedTrainer.of(start, goal, prefs[TRAINER_STEP] ?: SpeedTrainer.DEFAULT_STEP, prefs[TRAINER_BARS] ?: SpeedTrainer.DEFAULT_BARS)
        }

        /** The Practice tab's [PracticeTool] used last, by its name; 1.1's `METRONOME` is no tool now and reads as none. */
        private val PRACTICE_TOOL = stringPreferencesKey("practice_tool")

        /** The chord charts' root ([PitchClass]) and chord type ([ChordQuality]) picked last, by their names. */
        private val PRACTICE_ROOT = stringPreferencesKey("practice_root")
        private val PRACTICE_CHORD_QUALITY = stringPreferencesKey("practice_chord_quality")

        /** The Scales tool's [ScaleType] picked last, by its name (the root is [PRACTICE_ROOT], shared with the chord charts). */
        private val PRACTICE_SCALE_TYPE = stringPreferencesKey("practice_scale_type")

        /**
         * The Chords · Scales choice of versions before Scales became a tool of its own (Phase 47), "CHORDS" or
         * "SCALES": read until a tool is picked, so whoever had scales open in Chords opens on Scales.
         */
        private val OLD_PRACTICE_CHART = stringPreferencesKey("practice_chart")

        /** The Capo helper's shapes picked last: their key's tonic ([PitchClass]) by its name, and whether it is minor; and the capo's fret. */
        private val PRACTICE_CAPO_SHAPES = stringPreferencesKey("practice_capo_shapes")
        private val PRACTICE_CAPO_MINOR = booleanPreferencesKey("practice_capo_minor")
        private val PRACTICE_CAPO_FRET = intPreferencesKey("practice_capo_fret")

        /** The drone under the Scales tool: its [DroneSound] and [DroneVoicing] by their names, and whether it sounds an octave lower. */
        private val DRONE_SOUND = stringPreferencesKey("drone_sound")
        private val DRONE_VOICING = stringPreferencesKey("drone_voicing")
        private val DRONE_LOWER = booleanPreferencesKey("drone_lower")
        private val LEFT_HANDED = booleanPreferencesKey("left_handed")

        /**
         * Ear training (Phase 55): the exercise used last, Play it back's [NoteChoice] and [NoteMatch] by their
         * names, and its notes in a row. What the player chose to be asked, never a score: the tally isn't saved.
         */
        private val EAR_EXERCISE = stringPreferencesKey("ear_exercise")
        private val EAR_NOTE_CHOICE = stringPreferencesKey("ear_note_choice")
        private val EAR_NOTES_IN_ROW = intPreferencesKey("ear_notes_in_row")
        private val EAR_NOTE_MATCH = stringPreferencesKey("ear_note_match")

        /** Intervals' [Interval]s and [IntervalDirection]s asked, by their names; none known reads as the default. */
        private val EAR_INTERVALS = stringSetPreferencesKey("ear_intervals")
        private val EAR_DIRECTIONS = stringSetPreferencesKey("ear_directions")
        private val EAR_CHORD_TYPES = stringSetPreferencesKey("ear_chord_types")

        /** Every custom tuning in one string, in [CustomTuningCodec]'s form. */
        private val CUSTOM_TUNINGS = stringPreferencesKey("custom_tunings")

        /**
         * The app's settings, stored in `files/datastore/settings.preferences_pb`, and this phone's own
         * in `this_device.preferences_pb` ([DEVICE_FILE_NAME]). Call once per process.
         */
        fun create(context: Context): SettingsRepository {
            val app = context.applicationContext
            return create(
                fileStorage { app.preferencesDataStoreFile(FILE_NAME) },
                fileStorage { app.preferencesDataStoreFile(DEVICE_FILE_NAME) },
            )
        }

        /** DataStore's own file storage, as `PreferenceDataStoreFactory.create(produceFile)` makes it. */
        private fun fileStorage(file: () -> File): Storage<Preferences> =
            FileStorage(PreferencesFileSerializer) { file().absoluteFile }

        /**
         * The repository on [settingsStorage] and [deviceStorage], as the app makes it (tests pass
         * storage of their own): a damaged file is replaced with defaults, so saving works again
         * afterwards, and a `mic_refused` that versions before Phase 65 kept with the settings moves
         * to this phone's store ([MoveMicRefused]).
         */
        internal fun create(
            settingsStorage: Storage<Preferences>,
            deviceStorage: Storage<Preferences>,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ): DataStoreSettingsRepository {
            val device = PreferenceDataStoreFactory.create(
                storage = deviceStorage,
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
            )
            val settings = PreferenceDataStoreFactory.create(
                storage = settingsStorage,
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                migrations = listOf(MoveMicRefused(device)),
                scope = scope,
            )
            return DataStoreSettingsRepository(settings, device)
        }

        /**
         * Moves `mic_refused` from the settings, where versions before Phase 65 kept it, to [device],
         * which backups leave out. Runs before the settings are first read, while the key is there; a
         * value already in this phone's store wins.
         */
        private class MoveMicRefused(private val device: DataStore<Preferences>) : DataMigration<Preferences> {
            override suspend fun shouldMigrate(currentData: Preferences): Boolean = MIC_REFUSED in currentData

            override suspend fun migrate(currentData: Preferences): Preferences {
                val refused = currentData[MIC_REFUSED] ?: return currentData
                device.edit { if (MIC_REFUSED !in it) it[MIC_REFUSED] = refused }
                return currentData.toMutablePreferences().apply { remove(MIC_REFUSED) }.toPreferences()
            }

            override suspend fun cleanUp() = Unit
        }

        /** The wait after failed read number [attempt] + 1 before trying again: 0.5 s, doubling up to 30 s. */
        internal fun readRetryMillis(attempt: Long): Long = (500L shl attempt.coerceAtMost(6).toInt()).coerceAtMost(30_000L)

        private fun customTunings(prefs: Preferences): List<CustomTuning> =
            CustomTuningCodec.decode(prefs[CUSTOM_TUNINGS])

        private fun pickCustomTuning(prefs: MutablePreferences, tuning: CustomTuning) {
            prefs[TUNING_ID] = tuning.id
            prefs[INSTRUMENT_ID] = tuning.instrument.id
            prefs.remove(TUNING_LOWERED_BY)
        }

        private fun saveLoweredBy(prefs: MutablePreferences, semitones: Int) {
            if (semitones > 0) prefs[TUNING_LOWERED_BY] = semitones else prefs.remove(TUNING_LOWERED_BY)
        }

        private fun saveCustomTunings(prefs: MutablePreferences, tunings: List<CustomTuning>) {
            if (tunings.isEmpty()) prefs.remove(CUSTOM_TUNINGS) else prefs[CUSTOM_TUNINGS] = CustomTuningCodec.encode(tunings)
        }

        /**
         * The saved instrument and tuning, always a matching pair. Versions before the instrument
         * picker saved only a (guitar) tuning, whose instrument it then is. A tuning that is neither
         * one of the instrument's presets nor one of its [customs], or a missing or unknown one (a
         * deleted or damaged custom tuning), gives the instrument's standard tuning; an unknown
         * instrument the tuning's own, else the default guitar. The tuning comes lowered as saved, kept
         * within what it can go ([Tuning.lowerableBy]); an amount out of range is cut to that.
         */
        private fun instrumentAndTuning(prefs: Preferences, customs: List<CustomTuning>): Pair<Instrument, Tuning> {
            val tuningId = prefs[TUNING_ID]
            val custom = tuningId?.let { id -> customs.firstOrNull { it.id == id } }
            val instrument = prefs[INSTRUMENT_ID]?.let(Instruments::byId)
                ?: custom?.instrument
                ?: tuningId?.let(Instruments::ofTuning)
                ?: Instruments.DEFAULT
            val asWritten = tuningId?.let(instrument::tuningById)
                ?: custom?.takeIf { it.instrument == instrument }?.tuning
                ?: instrument.defaultTuning
            val loweredBy = (prefs[TUNING_LOWERED_BY] ?: 0).coerceIn(0, asWritten.lowerableBy)
            return instrument to asWritten.lowered(loweredBy)
        }

        /** The tool used last, with Chords read as Scales where the old choice ([OLD_PRACTICE_CHART]) still says scales. */
        private fun practiceTool(prefs: Preferences): PracticeTool? {
            val saved = enumOrNull<PracticeTool>(prefs[PRACTICE_TOOL])
            return if (saved == PracticeTool.CHORDS && prefs[OLD_PRACTICE_CHART] == "SCALES") PracticeTool.SCALES else saved
        }

        /**
         * The meter look's rule (docs/architecture/v2-plan.md, *Settings from 1.1*): a saved look 2.0 has (1.1's Classic
         * and Light arc included); else the strobe where 1.1 showed its strobe wave under the meter ([showedStrobe]); else
         * null, the default. 1.1's other meter looks (Pointers, Loupe, Arrows, Light bars and older names) and 2.0's
         * trail of Phases 71 to 77 say nothing.
         */
        /** The choice saved; without one, Cents for a 1.1 player who had turned the tune hint off, else Words. */
        private fun underMeter(prefs: Preferences): UnderMeter =
            enumOrNull<UnderMeter>(prefs[UNDER_METER])
                ?: if (prefs[OLD_SHOW_TUNE_HINT] == false) UnderMeter.CENTS else UnderMeter.DEFAULT

        private fun lensLook(prefs: Preferences): LensLook? =
            enumOrNull<LensLook>(prefs[METER_LOOK]) ?: LensLook.STROBE.takeIf { showedStrobe(prefs) }

        /**
         * Whether 1.1 showed the strobe under its meter, read as 1.1 read it: the old strobe display; or the wave
         * view (the saved display, else the older wave-view switch, else 1.1's default, the wave) in the strobe look
         * (or the older stripes, which it replaced). Without a saved look 1.1's wave was the ribbon.
         */
        private fun showedStrobe(prefs: Preferences): Boolean {
            val display = prefs[OLD_DISPLAY]
            if (display == OLD_STROBE_DISPLAY) return true
            val wave = when (display) {
                OLD_WAVE_DISPLAY -> true
                OLD_NEEDLE_DISPLAY -> false
                else -> prefs[OLD_SHOW_WAVES] ?: true
            }
            return wave && prefs[OLD_WAVE_LOOK] in OLD_STROBE_LOOKS
        }

        private const val OLD_NEEDLE_DISPLAY = "NEEDLE"
        private const val OLD_WAVE_DISPLAY = "WAVE"

        /** The strobe display of versions before it became a wave look. */
        private const val OLD_STROBE_DISPLAY = "STROBE"

        /** 1.1's strobe wave look, and the stripes look of the versions before it. */
        private val OLD_STROBE_LOOKS = setOf("STROBE", "STRIPES")

        /** The constants [names] gives that this version knows, in their own order; null for none (missing, empty or all unknown). */
        private inline fun <reified E : Enum<E>> enumSetOrNull(names: Set<String>?): Set<E>? =
            names?.mapNotNullTo(LinkedHashSet()) { enumOrNull<E>(it) }?.takeIf { it.isNotEmpty() }?.let { known ->
                enumValues<E>().filterTo(LinkedHashSet()) { it in known }
            }

        private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
            enumValues<E>().firstOrNull { it.name == name }
    }
}
