package io.github.klaboukes.twangle.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.klaboukes.twangle.core.dsp.capo.Capo
import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.drone.DroneSound
import io.github.klaboukes.twangle.core.dsp.drone.DroneVoicing
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import java.util.UUID

/**
 * [SettingsRepository] saved with Jetpack DataStore (Preferences) in the app's private files.
 *
 * Reading is forgiving: a missing, unknown or out-of-range value (e.g. a tuning removed in a later
 * version) falls back to its default, and an unreadable file gives all defaults instead of crashing
 * the tuner, while a failed read is tried again ([readRetryMillis]). Damaged custom tunings are
 * skipped (see [CustomTuningCodec]), and a picked one that is gone gives its instrument's standard
 * tuning. Create it once per process with [create]: DataStore allows only one instance per file.
 * [newId] makes the ids of new custom tunings.
 */
class DataStoreSettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val newId: () -> String = { CustomTuning.ID_PREFIX + UUID.randomUUID() },
) : SettingsRepository {

    override val settings: Flow<AppSettings> = flow {
        var shown = false
        dataStore.data
            // A read that fails (an I/O error, not a damaged file: that is replaced, see create)
            // shows the defaults if nothing was shown yet, then tries again, so one failed read
            // doesn't keep the settings at their defaults, or stop later changes, for the session.
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
        .map(::toSettings)
        .distinctUntilChanged()

    override suspend fun setA4(hz: Float) {
        dataStore.edit { it[A4_HZ] = AppSettings.normalizeA4(hz) }
    }

    override suspend fun setMode(mode: TunerMode) {
        dataStore.edit { it[MODE] = mode.name }
    }

    override suspend fun setInstrument(instrument: Instrument) {
        dataStore.edit {
            if (instrumentAndTuning(it, customTunings(it)).first == instrument) return@edit
            it[INSTRUMENT_ID] = instrument.id
            it[TUNING_ID] = instrument.defaultTuning.id
            it.remove(TUNING_LOWERED_BY)
        }
    }

    override suspend fun setTuning(tuning: Tuning) {
        dataStore.edit {
            it[TUNING_ID] = tuning.id
            saveLoweredBy(it, tuning.loweredBy)
            val instrument = Instruments.ofTuning(tuning.id)
                ?: customTunings(it).firstOrNull { custom -> custom.id == tuning.id }?.instrument
            instrument?.let { found -> it[INSTRUMENT_ID] = found.id }
        }
    }

    override suspend fun setLoweredBy(semitones: Int) {
        dataStore.edit {
            val picked = instrumentAndTuning(it, customTunings(it)).second
            saveLoweredBy(it, semitones.coerceIn(0, picked.loweredBy + picked.lowerableBy))
        }
    }

    override suspend fun addCustomTuning(name: String, instrument: Instrument, notes: List<Note>, pick: Boolean): CustomTuning {
        val added = CustomTuning(newId(), CustomTuning.normalizeName(name), instrument, notes)
        dataStore.edit {
            saveCustomTunings(it, customTunings(it) + added)
            if (pick) pickCustomTuning(it, added)
        }
        return added
    }

    override suspend fun updateCustomTuning(id: String, name: String, notes: List<Note>, pick: Boolean): CustomTuning? {
        var updated: CustomTuning? = null
        dataStore.edit {
            val tunings = customTunings(it)
            val index = tunings.indexOfFirst { custom -> custom.id == id }
            if (index < 0) return@edit
            val changed = CustomTuning(id, CustomTuning.normalizeName(name), tunings[index].instrument, notes)
            saveCustomTunings(it, tunings.toMutableList().apply { set(index, changed) })
            // New notes are the ones to tune to: a picked one is no longer lowered.
            if (it[TUNING_ID] == id) it.remove(TUNING_LOWERED_BY)
            if (pick) pickCustomTuning(it, changed)
            updated = changed
        }
        return updated
    }

    override suspend fun deleteCustomTuning(id: String) {
        dataStore.edit {
            val tunings = customTunings(it)
            val deleted = tunings.firstOrNull { custom -> custom.id == id } ?: return@edit
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
        dataStore.edit { if (notation == null) it.remove(NOTATION) else it[NOTATION] = notation.name }
    }

    override suspend fun setInTunePrecision(precision: InTunePrecision) {
        dataStore.edit { it[IN_TUNE_PRECISION] = precision.name }
    }

    override suspend fun setTheme(theme: ThemeMode) {
        dataStore.edit { it[THEME] = theme.name }
    }

    override suspend fun setStyle(style: VisualStyle) {
        dataStore.edit { it[STYLE] = style.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[KEEP_SCREEN_ON] = enabled }
    }

    override suspend fun setVibrateInTune(enabled: Boolean) {
        dataStore.edit { it[VIBRATE_IN_TUNE] = enabled }
    }

    override suspend fun setShowTuneHint(enabled: Boolean) {
        dataStore.edit { it[SHOW_TUNE_HINT] = enabled }
    }

    override suspend fun setShowSignalBars(enabled: Boolean) {
        dataStore.edit { it[SHOW_SIGNAL_BARS] = enabled }
    }

    override suspend fun setDisplay(display: TunerDisplay) {
        dataStore.edit {
            // An old strobe display was showing as the wave's strobe look: keep that look for the wave.
            if (it[DISPLAY] == OLD_STROBE_DISPLAY) it[WAVE_LOOK] = WaveLook.STROBE.name
            it[DISPLAY] = display.name
            // The old wave-view switch has done its job once a display is saved.
            it.remove(SHOW_WAVES)
        }
    }

    override suspend fun setWaveLook(look: WaveLook) {
        dataStore.edit {
            // An old strobe display would otherwise keep overriding the look picked here.
            if (it[DISPLAY] == OLD_STROBE_DISPLAY) it[DISPLAY] = TunerDisplay.WAVE.name
            it[WAVE_LOOK] = look.name
        }
    }

    override suspend fun setMeterLook(look: MeterLook) {
        dataStore.edit { it[METER_LOOK] = look.name }
    }

    override suspend fun setInTuneEffect(effect: InTuneEffect) {
        dataStore.edit { it[IN_TUNE_EFFECT] = effect.name }
    }

    override suspend fun setFocusMode(enabled: Boolean) {
        dataStore.edit { it[FOCUS_MODE] = enabled }
    }

    override suspend fun setTourSeen(seen: Boolean) {
        dataStore.edit { it[TOUR_SEEN] = seen }
    }

    override suspend fun decideTourOnce() {
        dataStore.edit { if (TOUR_SEEN !in it) it[TOUR_SEEN] = tourSeen(it) }
    }

    override suspend fun setMicRefused(refused: Boolean) {
        dataStore.edit { it[MIC_REFUSED] = refused }
    }

    override suspend fun setMetronomeBpm(bpm: Int) {
        dataStore.edit { it[METRONOME_BPM] = ClickTrack.clampBpm(bpm) }
    }

    override suspend fun setMetronomeMeter(meter: Meter) {
        dataStore.edit { it[METRONOME_METER] = meter.name }
    }

    override suspend fun setMetronomeVibrate(enabled: Boolean) {
        dataStore.edit { it[METRONOME_VIBRATE] = enabled }
    }

    override suspend fun setMetronomeClickSound(sound: ClickSound) {
        dataStore.edit { it[METRONOME_CLICK_SOUND] = sound.name }
    }

    override suspend fun setPracticeTool(tool: PracticeTool) {
        dataStore.edit {
            it[PRACTICE_TOOL] = tool.name
            // The old Chords · Scales choice has had its say once a tool is picked (see [practiceTool]).
            it.remove(OLD_PRACTICE_CHART)
        }
    }

    override suspend fun setPracticeRoot(root: PitchClass) {
        dataStore.edit { it[PRACTICE_ROOT] = root.name }
    }

    override suspend fun setPracticeChordQuality(quality: ChordQuality) {
        dataStore.edit { it[PRACTICE_CHORD_QUALITY] = quality.name }
    }

    override suspend fun setPracticeScaleType(type: ScaleType) {
        dataStore.edit { it[PRACTICE_SCALE_TYPE] = type.name }
    }

    override suspend fun setPracticeCapoShapes(shapes: SongKey) {
        dataStore.edit {
            it[PRACTICE_CAPO_SHAPES] = shapes.tonic.name
            it[PRACTICE_CAPO_MINOR] = shapes.minor
        }
    }

    override suspend fun setPracticeCapoFret(fret: Int) {
        dataStore.edit { it[PRACTICE_CAPO_FRET] = fret.coerceIn(0, Capo.HIGHEST_FRET) }
    }

    override suspend fun setDroneSound(sound: DroneSound) {
        dataStore.edit { it[DRONE_SOUND] = sound.name }
    }

    override suspend fun setDroneVoicing(voicing: DroneVoicing) {
        dataStore.edit { it[DRONE_VOICING] = voicing.name }
    }

    override suspend fun setDroneLower(lower: Boolean) {
        dataStore.edit { it[DRONE_LOWER] = lower }
    }

    override suspend fun setLeftHanded(enabled: Boolean) {
        dataStore.edit { it[LEFT_HANDED] = enabled }
    }

    private fun toSettings(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        // The strobe used to be a display of its own; now it is the wave's strobe look.
        val oldStrobe = prefs[DISPLAY] == OLD_STROBE_DISPLAY
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
            style = styleOrNull(prefs[STYLE]) ?: defaults.style,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            keepScreenOn = prefs[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            vibrateInTune = prefs[VIBRATE_IN_TUNE] ?: defaults.vibrateInTune,
            showTuneHint = prefs[SHOW_TUNE_HINT] ?: defaults.showTuneHint,
            showSignalBars = prefs[SHOW_SIGNAL_BARS] ?: defaults.showSignalBars,
            display = if (oldStrobe) {
                TunerDisplay.WAVE
            } else {
                enumOrNull<TunerDisplay>(prefs[DISPLAY]) ?: displayFromWaveSwitch(prefs[SHOW_WAVES]) ?: defaults.display
            },
            waveLook = if (oldStrobe) WaveLook.STROBE else waveLookOrNull(prefs[WAVE_LOOK]) ?: defaults.waveLook,
            meterLook = enumOrNull<MeterLook>(prefs[METER_LOOK]) ?: defaults.meterLook,
            inTuneEffect = enumOrNull<InTuneEffect>(prefs[IN_TUNE_EFFECT]) ?: defaults.inTuneEffect,
            focusMode = prefs[FOCUS_MODE] ?: defaults.focusMode,
            tourSeen = tourSeen(prefs),
            micRefused = prefs[MIC_REFUSED] ?: defaults.micRefused,
            metronomeBpm = prefs[METRONOME_BPM]?.let(ClickTrack::clampBpm) ?: defaults.metronomeBpm,
            metronomeMeter = enumOrNull<Meter>(prefs[METRONOME_METER]) ?: defaults.metronomeMeter,
            metronomeVibrate = prefs[METRONOME_VIBRATE] ?: defaults.metronomeVibrate,
            metronomeClickSound = enumOrNull<ClickSound>(prefs[METRONOME_CLICK_SOUND]) ?: defaults.metronomeClickSound,
            practiceTool = practiceTool(prefs) ?: defaults.practiceTool,
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
        )
    }

    companion object {
        private const val FILE_NAME = "settings"

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
        private val STYLE = stringPreferencesKey("visual_style")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val VIBRATE_IN_TUNE = booleanPreferencesKey("vibrate_in_tune")
        private val SHOW_TUNE_HINT = booleanPreferencesKey("show_tune_hint")
        private val SHOW_SIGNAL_BARS = booleanPreferencesKey("show_signal_bars")
        private val DISPLAY = stringPreferencesKey("tuner_display")

        /** The strobe display of versions before it became a wave look ([WaveLook.STROBE]). */
        private const val OLD_STROBE_DISPLAY = "STROBE"

        /** The stripes look of versions before the strobe look took its place. */
        private const val OLD_STRIPES_LOOK = "STRIPES"

        /** The styles of versions before the six styles of October 2026, both merged into [VisualStyle.ANCIENT]. */
        private val OLD_ANCIENT_STYLES = setOf("PREHISTORIC", "MIDDLE_AGES")

        /** The wave-view switch of versions before the display picker: read once, to carry it over. */
        private val SHOW_WAVES = booleanPreferencesKey("show_waves")
        private val WAVE_LOOK = stringPreferencesKey("wave_look")
        private val METER_LOOK = stringPreferencesKey("meter_look")
        private val IN_TUNE_EFFECT = stringPreferencesKey("in_tune_effect")
        /** Whether the first-start tour was skipped or finished; see [tourSeen] for an install without it. */
        private val TOUR_SEEN = booleanPreferencesKey("tour_seen")

        /** Whether the microphone was ever refused in Android's dialog ([AppSettings.micRefused]). */
        private val MIC_REFUSED = booleanPreferencesKey("mic_refused")
        private val FOCUS_MODE = booleanPreferencesKey("focus_mode")
        private val METRONOME_BPM = intPreferencesKey("metronome_bpm")

        /** The metronome's [Meter], by its name. */
        private val METRONOME_METER = stringPreferencesKey("metronome_meter")
        private val METRONOME_VIBRATE = booleanPreferencesKey("metronome_vibrate")

        /** The metronome's [ClickSound], by its name. */
        private val METRONOME_CLICK_SOUND = stringPreferencesKey("metronome_click_sound")

        /** The Practice tab's [PracticeTool] used last, by its name. */
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

        /** Every custom tuning in one string, in [CustomTuningCodec]'s form. */
        private val CUSTOM_TUNINGS = stringPreferencesKey("custom_tunings")

        /** The app's settings, stored in `files/datastore/settings.preferences_pb`. Call once per process. */
        fun create(context: Context): SettingsRepository = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(
                // A damaged file is replaced with defaults, so saving works again afterwards.
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            ) { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
        )

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

        /**
         * The saved tour flag; without one (before [decideTourOnce] has run on this install), any other
         * saved setting means an install from before the tour, which counts as having seen it. That
         * includes keys no longer read, such as `string_lock_tip_seen` (the string-lock tip the tour's
         * strings step replaced) and `wave_breathes_in_tune` (the temporary "when in tune" choice; the
         * wave now always holds still on the note): they stay in the file, so an install that only
         * dismissed that tip still counts as used.
         */
        private fun tourSeen(prefs: Preferences): Boolean = prefs[TOUR_SEEN] ?: prefs.asMap().isNotEmpty()

        /** The tool used last, with Chords read as Scales where the old choice ([OLD_PRACTICE_CHART]) still says scales. */
        private fun practiceTool(prefs: Preferences): PracticeTool? {
            val saved = enumOrNull<PracticeTool>(prefs[PRACTICE_TOOL])
            return if (saved == PracticeTool.CHORDS && prefs[OLD_PRACTICE_CHART] == "SCALES") PracticeTool.SCALES else saved
        }

        /** The display a saved wave-view switch stands for: on is the wave, off the needle with cents. */
        private fun displayFromWaveSwitch(showWaves: Boolean?): TunerDisplay? = when (showWaves) {
            true -> TunerDisplay.WAVE
            false -> TunerDisplay.NEEDLE
            null -> null
        }

        /** The saved look, with the old stripes look read as the strobe look that replaced it. */
        private fun waveLookOrNull(name: String?): WaveLook? =
            if (name == OLD_STRIPES_LOOK) WaveLook.STROBE else enumOrNull<WaveLook>(name)

        /** The saved style, with the old Prehistoric and Middle Ages read as Ancient, which replaced both. */
        private fun styleOrNull(name: String?): VisualStyle? =
            if (name in OLD_ANCIENT_STYLES) VisualStyle.ANCIENT else enumOrNull<VisualStyle>(name)

        private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
            enumValues<E>().firstOrNull { it.name == name }
    }
}
