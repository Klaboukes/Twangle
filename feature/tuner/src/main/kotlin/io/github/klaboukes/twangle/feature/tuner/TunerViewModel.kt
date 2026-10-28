package io.github.klaboukes.twangle.feature.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.MicPermissionRules
import io.github.klaboukes.twangle.core.audio.TonePlayer
import io.github.klaboukes.twangle.core.dsp.chord.ChordInstruments
import io.github.klaboukes.twangle.core.dsp.intonation.IntonationInstruments
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.strum.StrumInstruments
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.ToolSwitches
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
 * [savedInstrument] and [savedCustomTunings] do the same for the instrument and tuning panel, and [savedFocusMode] for the
 * layout on its side, and [savedMicRefused] for [micRefused]. [language] is the app's language (e.g. "de"): note names follow its habit
 * ([Notation.forLanguage]) until the player picks a notation; the screen passes a new one with [setLanguage].
 *
 * A long-press on a string plays its note through [tonePlayer] ([playString]). While that tone
 * sounds, and for [TONE_TAIL_SECONDS] after, the microphone stays open but its readings are set
 * aside, so the tuner shows the listening state instead of measuring its own tone.
 *
 * [tools] says which music tools show (Phase 28): the tuner modes of Phases 29–31 come only
 * with their switches. Tests get every tool unless they say otherwise; the app passes its build's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModel(
    recorder: AudioRecorder,
    private val settingsRepository: SettingsRepository,
    private val tonePlayer: TonePlayer,
    private val dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
    savedMode: TunerMode = AppSettings().mode,
    savedFocusMode: Boolean = AppSettings().focusMode,
    savedInstrument: Instrument = AppSettings().instrument,
    savedCustomTunings: List<CustomTuning> = AppSettings().customTunings,
    language: String = "",
    savedMicRefused: Boolean = AppSettings().micRefused,
    val tools: ToolSwitches = ToolSwitches.ALL_ON,
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

    private val trailFeed = PitchTrailFeed(PitchHistory())

    /**
     * The last few seconds of the tuner's readings, for the pitch trail (Phase 31): written on the
     * DSP thread with every reading while [tools] has the pitch trail, read in the draw phase
     * without recomposing (see [PitchHistory]). Stays empty while the switch is off.
     */
    val pitchHistory: PitchHistory get() = trailFeed.history

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
     * Whether focus mode is on, so a phone on its side shows the compact layout instead of the
     * two-pane one (a saved setting). Starts from the saved value, so a turned phone doesn't switch
     * layout a moment after opening.
     */
    val focusMode: StateFlow<Boolean> = settingsRepository.settings
        .map { it.focusMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), savedFocusMode)

    /** The string locked in guitar mode, or null for automatic. Cleared when the tuning changes. */
    private val lockedString = MutableStateFlow<Int?>(null)

    private val strumCheckOn = MutableStateFlow(false)

    /**
     * Whether strum check is on: strum once and every string shows flat, in tune or sharp at once.
     * Only in guitar mode; switching to chromatic turns it off. Kept in memory only, so the app
     * always opens on the tuner.
     */
    val strumCheck: StateFlow<Boolean> = strumCheckOn

    private val intonationOn = MutableStateFlow(false)

    /**
     * Whether the 12th-fret check is on (Phase 29): one string at a time, open then pressed at the
     * 12th fret, and whether its saddle moves. Only in guitar mode, on an instrument it is offered for
     * ([tunerTools]); like strum check (only one of the two is on at a time) kept in memory only.
     */
    val intonationCheck: StateFlow<Boolean> = intonationOn

    private val chordNameOn = MutableStateFlow(false)

    /**
     * Whether the chord name is on (Phase 30): strum a chord and its name shows. Only in guitar mode, on
     * an instrument it is offered for ([tunerTools]); like the other tools (one on at a time) kept in
     * memory only.
     */
    val chordName: StateFlow<Boolean> = chordNameOn

    private val pitchTrailOn = MutableStateFlow(false)

    /**
     * Whether the pitch trail is on (Phase 31): a line of the last seconds of readings in the meter's
     * place ([pitchHistory]). Only where [tunerTools] offers it; one tool at a time like the others and
     * kept in memory only. It is a way of showing the tuner's own readings, so it keeps the tuner
     * listening as it is and stays on in chromatic mode.
     */
    val pitchTrail: StateFlow<Boolean> = pitchTrailOn

    /** The string the 12th-fret check checks (null: none chosen), and a count that starts it afresh. */
    private val intonationChoice = MutableStateFlow(IntonationChoice(string = null, attempt = 0))

    /** The 12th-fret check's results per string, kept while the tuning stays (also across listening sessions). */
    private val intonationResults = IntonationResults()

    /** The tools the tuner offers for the picked instrument ([tunerToolsFor]): one switch, or a "more tools" menu. */
    val tunerTools: StateFlow<List<TunerTool>> = settingsRepository.settings
        .map { tunerToolsFor(tools, it.instrument) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, tunerToolsFor(tools, savedInstrument))

    /** Strum check, the 12th-fret check and its string, and the chord name, in one flow, so the options combine stays small. */
    private val toolChoice = combine(strumCheckOn, intonationOn, intonationChoice, chordNameOn) { strum, intonation, choice, chord ->
        ToolChoice(strum, intonation, choice, chord)
    }

    /**
     * What readings are compared with; null until the saved settings have loaded. Rebuilt only
     * when an input changes, and read by the DSP loop on every frame.
     */
    private val options: StateFlow<TunerOptions?> =
        combine(settingsRepository.settings, pickedMode, lockedString, toolChoice, appLanguage) { settings, picked, locked, tool, language ->
            TunerOptions(
                mode = picked ?: settings.mode,
                tuning = settings.tuning,
                a4Hz = settings.a4Hz,
                lockedString = locked,
                notation = settings.notationFor(language),
                inTunePrecision = settings.inTunePrecision,
                strumCheck = tool.strum && tools.strumCheck && StrumInstruments.offers(settings.instrument),
                intonationCheck = tool.intonation && tools.intonationCheck && IntonationInstruments.offers(settings.instrument),
                intonationString = tool.choice.string,
                intonationAttempt = tool.choice.attempt,
                intonationScaleMm = IntonationInstruments.scaleLengthMm(settings.instrument),
                chordName = tool.chord && tools.chordNaming && ChordInstruments.offers(settings.instrument),
                voice = settings.instrument.voice,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The selected tuning and the notation its notes are named in, for the instrument and tuning panel
     * while the tuner isn't listening (the microphone not allowed); null until the saved settings have loaded.
     */
    val panelTuning: StateFlow<PanelTuning?> = options
        .map { it?.let { options -> PanelTuning(options.tuning, options.notation) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Whether the microphone was ever refused in Android's dialog on this install (a saved setting, see [MicPermissionRules]). */
    val micRefused: StateFlow<Boolean> = settingsRepository.settings
        .map { it.micRefused }
        .stateIn(viewModelScope, SharingStarted.Eagerly, savedMicRefused)

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
                // The 12th-fret check starts again at "choose a string" (its results go with the tuning).
                intonationChoice.value = IntonationChoice(string = null, attempt = intonationChoice.value.attempt + 1)
                stopTone()
            }
        }
        // An instrument without frets has no 12th-fret check, one not strummed in chords no chord name,
        // and the 12-string no strum check: they turn off.
        viewModelScope.launch {
            tunerTools.collect {
                if (TunerTool.STRUM_CHECK !in it) strumCheckOn.value = false
                if (TunerTool.INTONATION_CHECK !in it) intonationOn.value = false
                if (TunerTool.CHORD_NAME !in it) chordNameOn.value = false
                if (TunerTool.PITCH_TRAIL !in it) pitchTrailOn.value = false
            }
        }
    }

    val state: StateFlow<TunerUiState> = combine(permissionGranted, attempt) { granted, _ -> granted }
        .flatMapLatest { granted -> if (granted) listen() else flowOf(TunerUiState.PermissionRequired) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TunerUiState.PermissionRequired)

    /** The player refused the microphone in Android's dialog: saved, so [micRefused] knows it on later starts too. */
    fun onMicRefused() {
        if (!micRefused.value) viewModelScope.launch { settingsRepository.setMicRefused(true) }
    }

    /** Tells the ViewModel whether `RECORD_AUDIO` is granted. The mic stays closed until it is. */
    fun onPermissionChanged(granted: Boolean) {
        permissionGranted.value = granted
    }

    /** The app's language changed (e.g. to "fr"): note names follow its habit unless the player picked a notation. */
    fun setLanguage(language: String) {
        appLanguage.value = language
    }

    /** Switches focus mode on or off, as its switch in Settings does: the tuner's full-screen button and Back. */
    fun setFocusMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFocusMode(enabled) }
    }

    /** Switches to [mode] straight away and saves it, so the next start opens in it too. */
    fun setMode(mode: TunerMode) {
        // Chromatic mode has no strings to show which one sounds.
        if (mode != this.mode.value) stopTone()
        if (mode != TunerMode.AUTO) {
            strumCheckOn.value = false
            intonationOn.value = false
            chordNameOn.value = false
        }
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
        if (strumCheckOn.value || chordNameOn.value) {
            // From strum check or the chord name a string is tapped to tune it: back to the tuner, locked to that string.
            strumCheckOn.value = false
            chordNameOn.value = false
            lockedString.value = index
        } else if (intonationOn.value) {
            // In the 12th-fret check a string is tapped to check it (again).
            checkIntonationOf(index)
        } else {
            lockedString.value = if (lockedString.value == index) null else index
        }
    }

    /**
     * Switches strum check [on] or off. The microphone stays open; from the next chunk its sound goes
     * to the strum check (or back to the tuner). Turning it on also switches to guitar mode, since
     * it checks the tuning's strings; only where [tunerTools] offers it (not on the 12-string).
     */
    fun setStrumCheck(on: Boolean) {
        if (on && TunerTool.STRUM_CHECK !in tunerTools.value) return
        if (on && mode.value != TunerMode.AUTO) setMode(TunerMode.AUTO)
        if (on) {
            intonationOn.value = false
            chordNameOn.value = false
            pitchTrailOn.value = false
        }
        strumCheckOn.value = on
    }

    /**
     * Switches the 12th-fret check [on] or off (only where [tunerTools] offers it). Like strum check
     * it keeps the microphone open, switches to guitar mode and turns the other tool off; it starts
     * at "choose a string".
     */
    fun setIntonationCheck(on: Boolean) {
        if (on && TunerTool.INTONATION_CHECK !in tunerTools.value) return
        if (on) {
            if (mode.value != TunerMode.AUTO) setMode(TunerMode.AUTO)
            strumCheckOn.value = false
            chordNameOn.value = false
            pitchTrailOn.value = false
            intonationChoice.value = IntonationChoice(string = null, attempt = intonationChoice.value.attempt + 1)
        }
        intonationOn.value = on
    }

    /**
     * Switches the chord name [on] or off (only where [tunerTools] offers it). Like the other tools it
     * keeps the microphone open, switches to guitar mode and turns the other tools off; it starts
     * waiting for a strum.
     */
    fun setChordName(on: Boolean) {
        if (on && TunerTool.CHORD_NAME !in tunerTools.value) return
        if (on) {
            if (mode.value != TunerMode.AUTO) setMode(TunerMode.AUTO)
            strumCheckOn.value = false
            intonationOn.value = false
            pitchTrailOn.value = false
        }
        chordNameOn.value = on
    }

    /**
     * Switches the pitch trail [on] or off (only where [tunerTools] offers it). It turns the other
     * tools off, but leaves the mode as it is: the trail draws chromatic mode's notes too.
     */
    fun setPitchTrail(on: Boolean) {
        if (on && TunerTool.PITCH_TRAIL !in tunerTools.value) return
        if (on) {
            strumCheckOn.value = false
            intonationOn.value = false
            chordNameOn.value = false
        }
        pitchTrailOn.value = on
    }

    /** The last [closeToolsOnRequest] count acted on. */
    private var closedToolsRequest = 0

    /**
     * The navigation bar's Tuner tab was tapped: [request] goes up with every tap (0 before the first).
     * Any tool that is on turns off, so the tab always leads to the plain tuner, also from another tab.
     * A count already acted on changes nothing, so the tuner shown again another way (back from the
     * tuning editor, the phone turned) keeps its tool.
     */
    fun closeToolsOnRequest(request: Int) {
        if (request == closedToolsRequest) return
        closedToolsRequest = request
        strumCheckOn.value = false
        intonationOn.value = false
        chordNameOn.value = false
        pitchTrailOn.value = false
    }

    /** The 12th-fret check starts on string [index] (lowest = 0), from its open note. */
    fun checkIntonationOf(index: Int) {
        intonationChoice.value = IntonationChoice(string = index, attempt = intonationChoice.value.attempt + 1)
    }

    /** "Check again": the same string, from its open note. */
    fun checkIntonationAgain() {
        val choice = intonationChoice.value
        intonationChoice.value = choice.copy(attempt = choice.attempt + 1)
    }

    /** "Next string": the next string of the tuning; after the last, back to choosing, with every string's result shown. */
    fun checkNextString() {
        val count = options.value?.tuning?.stringCount ?: return
        val next = intonationChoice.value.string?.plus(1)?.takeIf { it < count }
        intonationChoice.value = IntonationChoice(string = next, attempt = intonationChoice.value.attempt + 1)
    }

    /**
     * Plays string [index]'s note of the current tuning at the current A4, in the picked
     * instrument's voice (a plucked guitar string, a bowed violin string), so it can be tuned by ear;
     * a 12-string's course with an octave string sounds as the pair. A tone already sounding gives way to it. Does nothing before the settings have loaded or for a
     * string the tuning doesn't have.
     */
    fun playString(index: Int) {
        val options = options.value ?: return
        val note = options.tuning.strings.getOrNull(index) ?: return
        playedString.value = index
        tonePlayer.play(NoteMapper(options.a4Hz).frequencyOf(note), options.voice, octavePair = options.tuning.hasOctaveString(index))
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

    /**
     * Saves [tuning] as the selected tuning, lowered as it is ([Tuning.loweredBy]); listening follows
     * it from the next frame.
     */
    fun setTuning(tuning: Tuning) {
        viewModelScope.launch { settingsRepository.setTuning(tuning) }
    }

    /**
     * Lowers the selected tuning [semitones] below the tuning as written (0: as written); listening
     * follows from the next frame, with the longer window for low strings where needed.
     */
    fun setLoweredBy(semitones: Int) {
        viewModelScope.launch { settingsRepository.setLoweredBy(semitones) }
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
        val intonationInterpreter = IntonationInterpreter(pipeline.frameSeconds, intonationResults)
        val chordInterpreter = ChordNameInterpreter()
        val initial = options.filterNotNull().first()
        emit(
            when {
                initial.strumChecking -> strumInterpreter.waiting(initial)
                initial.intonationChecking -> intonationInterpreter.shown(initial)
                initial.chordNaming -> chordInterpreter.waiting(initial)
                else -> interpreter.listening(initial)
            },
        )
        val framesPerFigure = (1f / (SIGNAL_UPDATES_PER_SECOND * pipeline.frameSeconds)).toInt().coerceAtLeast(1)
        val tailFrames = ceil(TONE_TAIL_SECONDS / pipeline.frameSeconds).toInt()
        var frame = 0
        // Frames still to set aside: the tone sounds, or its echo may still be in the room.
        var ignoredFrames = 0
        // Whether the last reading was a tool's (strum check, 12th-fret check, chord name): the tuner's smoothing starts afresh after it.
        var elsewhere = false
        val frameSeconds = pipeline.frameSeconds
        if (tools.pitchTrail) trailFeed.start()
        emitAll(
            pipeline.listen(
                // The window follows the tuning's lowest string (bass notes need the longer one).
                range = { (options.value ?: initial).pitchRange },
                // The chord name hears the mic likewise, named at the tuner's A4.
                chordA4Hz = {
                    val current = options.value ?: initial
                    if (current.chordNaming && ignoredFrames == 0 && !tonePlayer.isPlaying.value) current.a4Hz else Float.NaN
                },
                // Strum check hears the mic unless the app's own tone (or its echo) would be heard.
                strumStrings = {
                    val current = options.value ?: initial
                    if (current.strumChecking && ignoredFrames == 0 && !tonePlayer.isPlaying.value) current.stringHz else null
                },
            ).map { reading ->
                val current = options.value ?: initial
                if (reading is StrumReading) {
                    elsewhere = true
                    return@map when {
                        current.strumChecking -> strumInterpreter.interpret(reading, current)
                        current.intonationChecking -> intonationInterpreter.shown(current)
                        current.chordNaming -> chordInterpreter.shown(current)
                        else -> interpreter.listening(current)
                    }
                }
                if (reading is ChordReading) {
                    elsewhere = true
                    return@map when {
                        current.chordNaming -> chordInterpreter.interpret(reading, current)
                        current.strumChecking -> strumInterpreter.shown(current)
                        current.intonationChecking -> intonationInterpreter.shown(current)
                        else -> interpreter.listening(current)
                    }
                }
                reading as PitchReading
                if (elsewhere && !current.intonationChecking) {
                    elsewhere = false
                    interpreter = TunerInterpreter(pipeline.frameSeconds)
                }
                if (tonePlayer.isPlaying.value) ignoredFrames = tailFrames + 1
                // The pitch trail follows the tuner's own readings; a tone or a tool leaves a gap in its line.
                if (tools.pitchTrail && (ignoredFrames > 0 || current.strumChecking || current.chordNaming || current.intonationChecking)) {
                    trailFeed.gap(frameSeconds)
                }
                if (ignoredFrames > 0) {
                    // The mic would hear the app's own tone: show the listening state instead, and
                    // start the smoothing afresh afterwards so nothing of the tone carries over.
                    if (--ignoredFrames == 0) interpreter = TunerInterpreter(pipeline.frameSeconds)
                    return@map when {
                        current.strumChecking -> strumInterpreter.waiting(current)
                        current.intonationChecking -> intonationInterpreter.shown(current)
                        current.chordNaming -> chordInterpreter.shown(current)
                        else -> interpreter.listening(current)
                    }
                }
                // Strum check or the chord name was switched on between two chunks: its first reading comes with the next.
                if (current.strumChecking) return@map strumInterpreter.shown(current)
                if (current.chordNaming) return@map chordInterpreter.shown(current)
                if (current.intonationChecking) {
                    elsewhere = true
                    return@map intonationInterpreter.interpret(reading, current)
                }
                val state = interpreter.interpret(reading, current)
                if (tools.pitchTrail) trailFeed.record(state, frameSeconds)
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

/** The string the 12th-fret check checks (null: none chosen), and a count that starts it afresh when it goes up. */
private data class IntonationChoice(val string: Int?, val attempt: Int)

/** Which tool is on, and the 12th-fret check's [choice]; one value, so the options combine stays small. */
private data class ToolChoice(val strum: Boolean, val intonation: Boolean, val choice: IntonationChoice, val chord: Boolean)

/** A tuning as the instrument and tuning panel shows it: its notes named in [notation]. */
data class PanelTuning(val tuning: Tuning, val notation: Notation)
