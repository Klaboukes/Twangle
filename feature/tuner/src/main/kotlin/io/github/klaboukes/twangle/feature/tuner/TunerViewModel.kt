package io.github.klaboukes.twangle.feature.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.TonePlayer
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.WaveLook
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * Owns the tuner's audio pipeline and exposes everything the screen shows as one [state].
 *
 * The microphone opens only when the permission is granted ([onPermissionChanged]) *and* the UI
 * collects [state]. It stays open 5 s after the UI stops collecting, so a rotation doesn't reopen
 * it (CLAUDE.md, hard rule 4). Switching [mode] takes effect on the next frame without reopening
 * the mic, and is saved so the app reopens in it. Saved settings (tuning, A4, notation, in-tune precision) are
 * followed live, also while listening.
 * Smoothing runs on [dspDispatcher] together with the pitch detection. [savedMode] is the mode
 * shown until the saved settings arrive, so a screen that already read them starts in the right one;
 * [savedInstrument] and [savedCustomTunings] do the same for the instrument and tuning panel, and [savedKeepTwoPaneLandscape] for the
 * layout on its side. [language] is the app's language (e.g. "de"): note names follow its habit
 * ([Notation.forLanguage]) until the player picks a notation; the screen passes a new one with [setLanguage].
 *
 * A long-press on a string plays its note through [tonePlayer] ([playString]). While that tone
 * sounds, and for [TONE_TAIL_SECONDS] after, the microphone stays open but its readings are set
 * aside, so the tuner shows the listening state instead of measuring its own tone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModel(
    recorder: AudioRecorder,
    private val settingsRepository: SettingsRepository,
    private val tonePlayer: TonePlayer,
    private val dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
    savedMode: TunerMode = AppSettings().mode,
    savedKeepTwoPaneLandscape: Boolean = AppSettings().keepTwoPaneLandscape,
    savedInstrument: Instrument = AppSettings().instrument,
    savedCustomTunings: List<CustomTuning> = AppSettings().customTunings,
    language: String = "",
) : ViewModel() {

    /** The app's language, whose usual notation is used while the player hasn't picked one. */
    private val appLanguage = MutableStateFlow(language)

    private val pipeline = PitchPipeline(recorder, dspDispatcher)
    private val permissionGranted = MutableStateFlow(false)
    private val attempt = MutableStateFlow(0)

    /**
     * The mode just picked on the switch, or null until one is. It wins over the saved mode, so
     * the switch and the readings follow a tap at once instead of after the save.
     */
    private val pickedMode = MutableStateFlow<TunerMode?>(null)

    /** Guitar or chromatic: the one last picked, which starts as the saved one. */
    val mode: StateFlow<TunerMode> =
        combine(settingsRepository.settings, pickedMode) { settings, picked -> picked ?: settings.mode }
            .stateIn(viewModelScope, SharingStarted.Eagerly, savedMode)

    /** The instrument whose strings guitar mode tunes (a saved setting); the tuning is always one of its presets. */
    val instrument: StateFlow<Instrument> = settingsRepository.settings
        .map { it.instrument }
        .stateIn(viewModelScope, SharingStarted.Eagerly, savedInstrument)

    /** The player's own tunings, every instrument's (a saved setting); the button names a picked one by its name. */
    val customTunings: StateFlow<List<CustomTuning>> = settingsRepository.settings
        .map { it.customTunings }
        .stateIn(viewModelScope, SharingStarted.Eagerly, savedCustomTunings)

    /** Whether the screen should stay on while the tuner is shown (a saved setting). */
    val keepScreenOn: StateFlow<Boolean> = settingsRepository.settings
        .map { it.keepScreenOn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether reaching "in tune" gives a haptic tick (a saved setting). */
    val vibrateInTune: StateFlow<Boolean> = settingsRepository.settings
        .map { it.vibrateInTune }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** What shows how far off a note is under it: cents or the wave (a saved setting). */
    val display: StateFlow<TunerDisplay> = settingsRepository.settings
        .map { it.display }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings().display)

    /** Whether "Tune up ↑" / "Tune down ↓" shows under an off-pitch note and TalkBack says it (a saved setting). */
    val showTuneHint: StateFlow<Boolean> = settingsRepository.settings
        .map { it.showTuneHint }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings().showTuneHint)

    /** Whether small signal bars by the note show how well the tuner hears (a saved setting, off by default). */
    val showSignalBars: StateFlow<Boolean> = settingsRepository.settings
        .map { it.showSignalBars }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings().showSignalBars)

    private val signal = MutableStateFlow<SignalFigures?>(null)

    /**
     * What the tuner hears now, for the signal bars' figures: updated about [SIGNAL_UPDATES_PER_SECOND]
     * times a second while listening and [showSignalBars] is on, else left as it is (null until then).
     */
    val signalFigures: StateFlow<SignalFigures?> = signal

    /** What the wave view looks like (a saved setting). */
    val waveLook: StateFlow<WaveLook> = settingsRepository.settings
        .map { it.waveLook }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaveLook.DEFAULT)

    /** What the meter above the note looks like (a saved setting). */
    val meterLook: StateFlow<MeterLook> = settingsRepository.settings
        .map { it.meterLook }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeterLook.DEFAULT)

    /** How strong the meter's in-tune glow is: Calm or Bold (a saved setting). */
    val inTuneEffect: StateFlow<InTuneEffect> = settingsRepository.settings
        .map { it.inTuneEffect }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InTuneEffect.DEFAULT)

    /**
     * Whether a phone on its side keeps the two-pane layout instead of the compact one (a saved
     * setting). Starts from the saved value, so a turned phone doesn't switch layout a moment after opening.
     */
    val keepTwoPaneLandscape: StateFlow<Boolean> = settingsRepository.settings
        .map { it.keepTwoPaneLandscape }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), savedKeepTwoPaneLandscape)

    /** The string locked in guitar mode, or null for automatic. Cleared when the tuning changes. */
    private val lockedString = MutableStateFlow<Int?>(null)

    private val strumCheckOn = MutableStateFlow(false)

    /**
     * Whether strum check is on: strum once and every string shows flat, in tune or sharp at once.
     * Only in guitar mode; switching to chromatic turns it off. Kept in memory only, so the app
     * always opens on the tuner.
     */
    val strumCheck: StateFlow<Boolean> = strumCheckOn

    /**
     * What readings are compared with; null until the saved settings have loaded. Rebuilt only
     * when an input changes, and read by the DSP loop on every frame.
     */
    private val options: StateFlow<TunerOptions?> =
        combine(settingsRepository.settings, pickedMode, lockedString, strumCheckOn, appLanguage) { settings, picked, locked, strum, language ->
            TunerOptions(
                mode = picked ?: settings.mode,
                tuning = settings.tuning,
                a4Hz = settings.a4Hz,
                lockedString = locked,
                notation = settings.notationFor(language),
                inTunePrecision = settings.inTunePrecision,
                strumCheck = strum,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The string whose tone was played last, while its tone may still sound. */
    private val playedString = MutableStateFlow<Int?>(null)

    /**
     * The string whose reference tone is sounding now (lowest = 0), or null. Set by [playString],
     * cleared when the tone has died away or is stopped.
     */
    val soundingString: StateFlow<Int?> =
        combine(playedString, tonePlayer.isPlaying) { string, playing -> string.takeIf { playing } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // A lock belongs to one tuning: a string index means something else in another tuning.
        // A tone still sounding belonged to the old tuning too.
        viewModelScope.launch {
            settingsRepository.settings.map { it.tuning }.distinctUntilChanged().drop(1).collect {
                lockedString.value = null
                stopTone()
            }
        }
    }

    val state: StateFlow<TunerUiState> = combine(permissionGranted, attempt) { granted, _ -> granted }
        .flatMapLatest { granted -> if (granted) listen() else flowOf(TunerUiState.PermissionRequired) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TunerUiState.PermissionRequired)

    /** Tells the ViewModel whether `RECORD_AUDIO` is granted. The mic stays closed until it is. */
    fun onPermissionChanged(granted: Boolean) {
        permissionGranted.value = granted
    }

    /** Switches to [mode] straight away and saves it, so the next start opens in it too. */
    /** The app's language changed (e.g. to "fr"): note names follow its habit unless the player picked a notation. */
    fun setLanguage(language: String) {
        appLanguage.value = language
    }

    fun setMode(mode: TunerMode) {
        // Chromatic mode has no strings to show which one sounds.
        if (mode != this.mode.value) stopTone()
        if (mode != TunerMode.AUTO) strumCheckOn.value = false
        pickedMode.value = mode
        viewModelScope.launch { settingsRepository.setMode(mode) }
    }

    /**
     * Locks guitar mode to string [index] (lowest = 0), or back to automatic if it was already
     * locked. Kept while switching to chromatic and back.
     *
     * While a reference tone sounds, a tap on any string only stops the tone ("tap again to stop"):
     * the lock stays as it was, so stopping the sound never changes what the tuner listens for.
     */
    fun toggleString(index: Int) {
        if (tonePlayer.isPlaying.value) {
            stopTone()
            return
        }
        if (strumCheckOn.value) {
            // From strum check a string is tapped to tune it: back to the tuner, locked to that string.
            strumCheckOn.value = false
            lockedString.value = index
        } else {
            lockedString.value = if (lockedString.value == index) null else index
        }
    }

    /**
     * Switches strum check [on] or off. The microphone stays open; from the next chunk its sound goes
     * to the strum check (or back to the tuner). Turning it on also switches to guitar mode, since
     * it checks the tuning's strings.
     */
    fun setStrumCheck(on: Boolean) {
        if (on && mode.value != TunerMode.AUTO) setMode(TunerMode.AUTO)
        strumCheckOn.value = on
    }

    /**
     * Plays string [index]'s note of the current tuning as a plucked tone at the current A4, so it
     * can be tuned by ear. A tone already sounding gives way to it. Does nothing before the settings
     * have loaded or for a string the tuning doesn't have.
     */
    fun playString(index: Int) {
        val options = options.value ?: return
        val note = options.tuning.strings.getOrNull(index) ?: return
        playedString.value = index
        tonePlayer.play(NoteMapper(options.a4Hz).frequencyOf(note))
    }

    /** Stops a reference tone that is sounding (it fades out within a few tens of ms); also when the tuner leaves the screen. */
    fun stopTone() {
        playedString.value = null
        if (tonePlayer.isPlaying.value) tonePlayer.stop()
    }

    override fun onCleared() {
        stopTone()
    }

    /**
     * Saves [instrument] with its standard tuning; listening follows from the next frame, with the
     * longer window for low strings where needed. The instrument already picked keeps its tuning.
     */
    fun setInstrument(instrument: Instrument) {
        viewModelScope.launch { settingsRepository.setInstrument(instrument) }
    }

    /** Saves [tuning] as the selected tuning; listening follows it from the next frame. */
    fun setTuning(tuning: Tuning) {
        viewModelScope.launch { settingsRepository.setTuning(tuning) }
    }

    /**
     * Deletes the player's own tuning [id] for good; when it was picked, the tuner goes back to its
     * instrument's standard tuning (see [SettingsRepository.deleteCustomTuning]).
     */
    fun deleteCustomTuning(id: String) {
        viewModelScope.launch { settingsRepository.deleteCustomTuning(id) }
    }

    /** Opens the microphone again after an error. */
    fun retry() {
        attempt.value++
    }

    companion object {
        /** The signal bars' figures change this often: often enough to follow, slow enough to read. */
        private const val SIGNAL_UPDATES_PER_SECOND = 4f

        /**
         * After a reference tone has left the speaker the microphone is ignored this much longer, so
         * the room's echo of it isn't read as a note either.
         */
        const val TONE_TAIL_SECONDS = 0.3f
    }

    private fun listen(): Flow<TunerUiState> = flow {
        var interpreter = TunerInterpreter(pipeline.frameSeconds)
        val strumInterpreter = StrumCheckInterpreter()
        val initial = options.filterNotNull().first()
        emit(if (initial.strumChecking) strumInterpreter.waiting(initial) else interpreter.listening(initial))
        val framesPerFigure = (1f / (SIGNAL_UPDATES_PER_SECOND * pipeline.frameSeconds)).toInt().coerceAtLeast(1)
        val tailFrames = ceil(TONE_TAIL_SECONDS / pipeline.frameSeconds).toInt()
        var frame = 0
        // Frames still to set aside: the tone sounds, or its echo may still be in the room.
        var ignoredFrames = 0
        // Whether the last reading was the strum check's: the tuner's smoothing starts afresh after it.
        var strummed = false
        emitAll(
            pipeline.listen(
                // The window follows the tuning's lowest string (bass notes need the longer one).
                range = { (options.value ?: initial).pitchRange },
                // Strum check hears the mic unless the app's own tone (or its echo) would be heard.
                strumStrings = {
                    val current = options.value ?: initial
                    if (current.strumChecking && ignoredFrames == 0 && !tonePlayer.isPlaying.value) current.stringHz else null
                },
            ).map { reading ->
                val current = options.value ?: initial
                if (reading is StrumReading) {
                    strummed = true
                    return@map if (current.strumChecking) strumInterpreter.interpret(reading, current) else interpreter.listening(current)
                }
                reading as PitchReading
                if (strummed) {
                    strummed = false
                    interpreter = TunerInterpreter(pipeline.frameSeconds)
                }
                if (tonePlayer.isPlaying.value) ignoredFrames = tailFrames + 1
                if (ignoredFrames > 0) {
                    // The mic would hear the app's own tone: show the listening state instead, and
                    // start the smoothing afresh afterwards so nothing of the tone carries over.
                    if (--ignoredFrames == 0) interpreter = TunerInterpreter(pipeline.frameSeconds)
                    return@map if (current.strumChecking) strumInterpreter.waiting(current) else interpreter.listening(current)
                }
                // Strum check was switched on between two chunks: its first reading comes with the next.
                if (current.strumChecking) return@map strumInterpreter.shown(current)
                val state = interpreter.interpret(reading, current)
                if (showSignalBars.value && frame++ % framesPerFigure == 0) {
                    val reception = interpreter.reception
                    signal.value = SignalFigures(
                        levelDbfs = if (reception.hasNote) reception.levelDbfs else reading.levelDbfs,
                        noiseFloorDbfs = reading.noiseFloorDbfs,
                        clarity = if (reception.hasNote) reception.clarity else Float.NaN,
                    )
                }
                state
            },
        )
    }
        // Most frames look the same on screen as the last state sent: an equal Listening state in
        // silence, or a note whose readout and needle moved less than the screen shows. Dropping
        // them here, on the DSP thread, keeps them from waking the main thread ~47 times a second.
        .distinctUntilChanged { old, new -> old.looksLike(new) }
        .catch { emit(TunerUiState.Error(it.toTunerError())) }
        .flowOn(dspDispatcher)
}
