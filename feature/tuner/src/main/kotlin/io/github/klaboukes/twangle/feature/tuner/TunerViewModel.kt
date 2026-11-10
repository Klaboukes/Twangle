package io.github.klaboukes.twangle.feature.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.MicPermissionRules
import io.github.klaboukes.twangle.core.audio.ScreenReaderSpeech
import io.github.klaboukes.twangle.core.audio.TonePlayer
import io.github.klaboukes.twangle.core.audio.watching
import io.github.klaboukes.twangle.core.dsp.guide.GuideCue
import io.github.klaboukes.twangle.core.dsp.guide.VibrationGuide
import io.github.klaboukes.twangle.core.dsp.intonation.IntonationInstruments
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.smoothing.InTuneDetector
import io.github.klaboukes.twangle.core.dsp.strum.StrumInstruments
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.LensLook
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.UnderMeter
import io.github.klaboukes.twangle.feature.tuner.components.GuideVibrator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
 * Smoothing runs on [dspDispatcher] together with the pitch detection. [saved] are the settings the
 * app already read at its start: every saved option here ([mode], [instrument], [stringTickOff] and the rest)
 * starts from them until this ViewModel's own reading arrives, so the first frames show the player's layout
 * instead of the defaults for a moment. [language] is the app's language (e.g. "de"): note names follow its habit
 * ([Notation.forLanguage]) until the player picks a notation; the screen passes a new one with [setLanguage].
 *
 * A long-press on a string plays its note through [tonePlayer] ([playString]). While that tone
 * sounds, and for [TONE_TAIL_SECONDS] after, the microphone stays open but its readings are set
 * aside, so the tuner shows the listening state instead of measuring its own tone.
 *
 * The same while a screen reader's voice plays ([screenReaderSpeech], Phase 81: TalkBack saying "12 cents flat" through
 * the speaker) and for [ScreenReaderSpeech.TAIL_SECONDS] after, so its speech is never read as a note and a spoken
 * reading can't set off the next one. Every tool beside the tuner hears nothing then either.
 *
 * With the vibration guide on (Phase 85, [AppSettings.vibrationGuideOn]: the player's choice, else on while a screen
 * reader is), [guideVibrator] taps while a note is heard, once to tighten and twice to loosen, quicker as it nears
 * pitch, and buzzes long on becoming in tune ([VibrationGuide]); [vibrateInTune]'s click is then off. It plays only
 * while the screen is [setShown] and the tuner listens.
 *
 * [tools] says which music tools show (Phase 28): the tuner modes of Phases 29–31 come only
 * with their switches. Tests get every tool unless they say otherwise; the app passes its build's.
 *
 * [ratingDue] turns true, at most once ever, when Google's rating window may be asked for (Phase 50,
 * [RatingMoment]): every string of the tuning has been in tune in one go, the strings have fallen quiet
 * for [RatingMoment.QUIET_MILLIS], it is past the first day (by [clock]) and it was never asked before.
 * The screen then asks Google ([RatingWindow]) and calls [onRatingAsked].
 *
 * [tunedStrings] are the strings ticked off on their buttons (Phase 65): in tune since listening
 * started and not played clearly out of tune since, shown only with the string tick-off setting
 * on; the same [AllStringsInTune] keeps them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModel(
    recorder: AudioRecorder,
    private val settingsRepository: SettingsRepository,
    private val tonePlayer: TonePlayer,
    private val dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
    saved: AppSettings = AppSettings(),
    language: String = "",
    val tools: ToolSwitches = ToolSwitches.ALL_ON,
    private val clock: () -> Long = System::currentTimeMillis,
    private val screenReaderSpeech: ScreenReaderSpeech = ScreenReaderSpeech.NONE,
    private val guideVibrator: GuideVibrator = GuideVibrator.NONE,
) : ViewModel() {

    /** The app's language, whose usual notation is used while the player hasn't picked one. */
    private val appLanguage = MutableStateFlow(language)

    private val pipeline = PitchPipeline(recorder, dspDispatcher)
    private val permissionGranted = MutableStateFlow(false)
    private val attempt = MutableStateFlow(0)

    /**
     * The mode just picked on the switch, or null when none is waiting for its save. It wins over the saved mode, so
     * the switch and the readings follow a tap at once instead of after the save. It gives way to the saved mode at
     * the first reading of the settings once none of the switch's saves is still under way: by then the saved mode
     * has caught up with it, or was changed from elsewhere (an instrument or tuning picked in Practice switches the
     * tuner to guitar mode), which then shows at once instead of after a restart.
     */
    private val pickedMode = MutableStateFlow<TunerMode?>(null)

    /** How many of the switch's saves ([setMode]) are under way; only touched on the main thread. */
    private var modeSavesUnderWay = 0

    /** Guitar or chromatic: the one last picked, which starts as the saved one. */
    val mode: StateFlow<TunerMode> =
        combine(settingsRepository.settings, pickedMode) { settings, picked -> picked ?: settings.mode }
            .stateIn(viewModelScope, SharingStarted.Eagerly, saved.mode)

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect {
                if (modeSavesUnderWay == 0) pickedMode.value = null
            }
        }
    }

    /** The instrument whose strings guitar mode tunes (a saved setting); the tuning is always one of its presets. */
    val instrument: StateFlow<Instrument> = settingsRepository.settings
        .map { it.instrument }
        .stateIn(viewModelScope, SharingStarted.Eagerly, saved.instrument)

    /** The player's own tunings, every instrument's (a saved setting); the button names a picked one by its name. */
    val customTunings: StateFlow<List<CustomTuning>> = settingsRepository.settings
        .map { it.customTunings }
        .stateIn(viewModelScope, SharingStarted.Eagerly, saved.customTunings)

    /** Whether the screen should stay on while the tuner is shown (a saved setting). */
    val keepScreenOn: StateFlow<Boolean> = settingsRepository.settings
        .map { it.keepScreenOn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), saved.keepScreenOn)

    /**
     * Whether reaching "in tune" gives *Vibrate when in tune*'s haptic tick (a saved setting): never while the vibration
     * guide is on ([AppSettings.vibrationGuideOn]), whose long buzz says "in tune" instead, so the two never play together.
     */
    val vibrateInTune: StateFlow<Boolean> =
        combine(settingsRepository.settings, screenReaderSpeech.screenReaderOn) { settings, screenReader ->
            settings.vibrateInTune && !settings.vibrationGuideOn(screenReader)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), saved.vibrateInTune && saved.vibrationGuide != true)

    /** Whether the tuner's screen is shown ([setShown]): the vibration guide plays only then. */
    @Volatile
    private var shown = true

    /**
     * Whether the pegs tick off the strings tuned and "In tune. Play." shows once all are (the string tick-off, a saved
     * setting, off by default; 2.0, Phase 71).
     */
    val stringTickOff: StateFlow<Boolean> = settingsRepository.settings
        .map { it.stringTickOff }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), saved.stringTickOff)

    /** What goes under the meter: the word with the cents small, or the cents big in its place (a saved setting; 2.0, Phase 78). */
    val underMeter: StateFlow<UnderMeter> = settingsRepository.settings
        .map { it.underMeter }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), saved.underMeter)

    /** What the tuner's meter draws: the saved look (Classic, the Light arc or the strobe; Phase 78). */
    val lensLook: StateFlow<LensLook> = settingsRepository.settings
        .map { it.lensLook }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), saved.lensLook)

    /** How close to the note counts as in tune: Relaxed or Precise (a saved setting), for the meter's in-tune window. */
    val inTunePrecision: StateFlow<InTunePrecision> = settingsRepository.settings
        .map { it.inTunePrecision }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), saved.inTunePrecision)

    /** The string locked in guitar mode, or null for automatic. Cleared when the tuning changes. */
    private val lockedString = MutableStateFlow<Int?>(null)

    private val intonationOn = MutableStateFlow(false)

    /**
     * Whether the 12th-fret check is on (Phase 29): one string at a time, open then pressed at the
     * 12th fret, and whether its saddle moves. Only in guitar mode, on an instrument it is offered for
     * ([tunerTools]); kept in memory only, so the app always opens on the tuner.
     */
    val intonationCheck: StateFlow<Boolean> = intonationOn

    /** The string the 12th-fret check checks (null: none chosen), and a count that starts it afresh. */
    private val intonationChoice = MutableStateFlow(IntonationChoice(string = null, attempt = 0))

    /** The 12th-fret check's results per string, kept while the tuning stays (also across listening sessions). */
    private val intonationResults = IntonationResults()

    /**
     * The tools the tuner offers for the picked instrument ([tunerToolsFor]): the 12th-fret check, whose row the picker
     * sheet shows in guitar mode, and the strum check, whose button stands above the pegs. Picking an instrument without
     * one turns it off too.
     */
    val tunerTools: StateFlow<List<TunerTool>> = settingsRepository.settings
        .map { tunerToolsFor(tools, it.instrument) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, tunerToolsFor(tools, saved.instrument))

    private val strumOn = MutableStateFlow(false)

    /** Counted up each time the strum check's button is turned on. */
    private val strumAttempt = MutableStateFlow(0)

    /**
     * Whether the strum check's button is on (2.0, Phase 74, after the owner's guitar check of 2026-11-03: deciding by
     * itself what each sound was flickered): the next strum is read as a strum of the open strings, its marks shown above
     * the pegs, and once it has rung out the button turns itself off, so the strings are tuned one by one with the marks
     * following them ([heardStrum]). Only in guitar mode, where [tunerTools] offers it; kept in memory only.
     */
    val strumCheck: StateFlow<Boolean> = strumOn

    /** The 12th-fret check, its string and the strum check's button in one flow, so the options combine stays small. */
    private val toolChoice = combine(intonationOn, intonationChoice, strumOn, strumAttempt) { intonation, choice, strum, attempt ->
        ToolChoice(intonation, choice, StrumChoice(strum, attempt))
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
                intonationCheck = tool.intonation && tools.intonationCheck && IntonationInstruments.offers(settings.instrument),
                intonationString = tool.choice.string,
                intonationAttempt = tool.choice.attempt,
                intonationScaleMm = IntonationInstruments.scaleLengthMm(settings.instrument),
                voice = settings.instrument.voice,
                strumsHeard = tools.strumCheck && StrumInstruments.offers(settings.instrument),
                strumCheck = tool.strum.on,
                strumAttempt = tool.strum.attempt,
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
        .stateIn(viewModelScope, SharingStarted.Eagerly, saved.micRefused)

    /** When the app was first used and whether the rating window was asked for (saved settings); null until loaded. */
    private val ratingRecord: StateFlow<RatingRecord?> = settingsRepository.settings
        .map { RatingRecord(it.firstUseMillis, it.ratingAsked) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Set at once by [onRatingAsked], so the window is never asked for twice while the save is under way. */
    @Volatile
    private var ratingAskedNow = false

    private val ratingDueNow = MutableStateFlow(false)

    /** Google's rating window may be asked for now (see the class comment); false again after [onRatingAsked]. */
    val ratingDue: StateFlow<Boolean> = ratingDueNow

    private val tunedNow = MutableStateFlow<TunedStrings?>(null)

    /**
     * The strings ticked off on their buttons (Phase 65): in tune since the tuner started listening,
     * and not played clearly out of tune since; null while none is, and always null while the string
     * tick-off setting ([AppSettings.stringTickOff], off by default; 2.0, Phase 71) is off. Kept by
     * [AllStringsInTune] on the DSP thread, so it follows the tuner's own in-tune and out-of-tune
     * rules, in guitar mode only; a new tuning or A4 starts it afresh, and it goes when listening
     * stops (the tuner left for more than 5 s, the app in the background, the microphone lost). The
     * screen shows it only for the [TunedStrings.tuning] it shows.
     */
    val tunedStrings: StateFlow<TunedStrings?> =
        combine(tunedNow, settingsRepository.settings.map { it.stringTickOff }.distinctUntilChanged()) { tuned, shown ->
            tuned.takeIf { shown }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The wait for the strings to fall quiet after the last one came in tune; touched only on the DSP thread. */
    private var ratingWait: Job? = null

    private val heardNow = MutableStateFlow<HeardStrum>(HeardStrum.None)

    /**
     * The strum check's strum (2.0, Phase 74): after a tap on its button, the next strum of the open strings with each
     * string's verdict (its column in the lens's place, "4 of 6 in tune" in the word's place), heard by the analysis
     * beside the tuner ([PitchPipeline.listen], [StrumHearing]) and kept as [HeardShown] says: while it rings the
     * tuner's own note is set aside ([state] then waits), and the strings tuned one by one after it take the tuner's
     * verdicts. Its brake is [ToolSwitches.strumCheck]; only in guitar mode, and nothing while the 12th-fret check is
     * on or a reference tone sounds. Written on the DSP thread,
     * [HeardStrum.None] again when the tuning, the A4 or the mode changes and when listening stops.
     */
    val heardStrum: StateFlow<HeardStrum> = heardNow

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
        // An instrument without frets has no 12th-fret check: it turns off.
        viewModelScope.launch {
            tunerTools.collect {
                if (TunerTool.INTONATION_CHECK !in it) intonationOn.value = false
                if (TunerTool.STRUM_CHECK !in it) setStrumCheck(false)
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

    /** Switches to [mode] straight away and saves it, so the next start opens in it too. */
    fun setMode(mode: TunerMode) {
        // Chromatic mode has no strings to show which one sounds.
        if (mode != this.mode.value) stopTone()
        if (mode != TunerMode.AUTO) {
            intonationOn.value = false
            setStrumCheck(false)
        }
        pickedMode.value = mode
        modeSavesUnderWay++
        viewModelScope.launch {
            try {
                settingsRepository.setMode(mode)
            } finally {
                modeSavesUnderWay--
            }
        }
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
        if (intonationOn.value) {
            // In the 12th-fret check a string is tapped to check it (again).
            checkIntonationOf(index)
        } else {
            lockedString.value = if (lockedString.value == index) null else index
        }
    }

    /**
     * Switches the 12th-fret check [on] or off (only where [tunerTools] offers it). It keeps the microphone open and
     * switches to guitar mode; it starts at "choose a string".
     */
    fun setIntonationCheck(on: Boolean) {
        if (on && TunerTool.INTONATION_CHECK !in tunerTools.value) return
        if (on) {
            if (mode.value != TunerMode.AUTO) setMode(TunerMode.AUTO)
            intonationChoice.value = IntonationChoice(string = null, attempt = intonationChoice.value.attempt + 1)
            setStrumCheck(false)
        }
        intonationOn.value = on
    }

    /**
     * Turns the strum check's button [on] (only where [tunerTools] offers it): the marks of the strum before go and the
     * next strum is read. Off by a tap again, or by itself once that strum has rung out (in [listen]).
     */
    fun setStrumCheck(on: Boolean) {
        if (on && TunerTool.STRUM_CHECK !in tunerTools.value) return
        if (on) strumAttempt.update { it + 1 }
        strumOn.value = on
    }

    /** The strum check's panel closed (its × or Back): the button off and the strum shown gone, the tuner's own lens back. */
    fun closeStrumCheck() {
        strumOn.value = false
        // The next listening step sees a new turn and clears what the strum showed.
        strumAttempt.update { it + 1 }
    }

    /** The last [closeToolsOnRequest] count acted on. */
    private var closedToolsRequest = 0

    /**
     * The navigation bar's Tuner tab was tapped: [request] goes up with every tap (0 before the first).
     * The 12th-fret check, if on, turns off, so the tab always leads to the plain tuner, also from another tab.
     * A count already acted on changes nothing, so the tuner shown again another way (back from the
     * tuning editor, the phone turned) keeps its tool.
     */
    fun closeToolsOnRequest(request: Int) {
        if (request == closedToolsRequest) return
        closedToolsRequest = request
        intonationOn.value = false
        setStrumCheck(false)
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

    /**
     * The tuner's screen is [shown] or not (the app in the background, another tab or Settings open). The microphone
     * stays open 5 s after the screen goes (hard rule 4), but the vibration guide stops at once: a player who left the
     * tuner shouldn't feel it.
     */
    fun setShown(shown: Boolean) {
        this.shown = shown
        if (!shown) guideVibrator.stop()
    }

    override fun onCleared() {
        stopTone()
        guideVibrator.stop()
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

    /** The screen asked Google for its rating window: saved, so it is never asked for again. */
    fun onRatingAsked() {
        ratingAskedNow = true
        ratingDueNow.value = false
        viewModelScope.launch { settingsRepository.setRatingAsked(true) }
    }

    private fun mayAskRating(): Boolean {
        val record = ratingRecord.value ?: return false
        return RatingMoment.mayAsk(record.firstUseMillis, record.asked || ratingAskedNow, clock())
    }

    /**
     * Follows every state of a listening session on the DSP thread, for Google's rating window: once
     * [AllStringsInTune] has [completed] the tuning while the window may be asked for ([armed] true, returned), the
     * first stretch of [RatingMoment.QUIET_MILLIS] without a note makes [ratingDue] true. A note heard
     * meanwhile starts the wait again, so the window never comes mid-tuning. Allocates nothing per state.
     */
    private fun followRatingMoment(shown: TunerUiState, completed: Boolean, armed: Boolean): Boolean {
        val nowArmed = armed || (completed && mayAskRating())
        if (!nowArmed) return false
        if (shown is TunerUiState.Listening) {
            if (ratingWait?.isActive != true) {
                ratingWait = viewModelScope.launch {
                    delay(RatingMoment.QUIET_MILLIS)
                    if (mayAskRating()) ratingDueNow.value = true
                }
            }
        } else {
            ratingWait?.cancel()
            ratingWait = null
        }
        return true
    }

    /**
     * Feeds the vibration guide (Phase 85) one state [shown] on the DSP thread and plays what it asks for, while it is
     * [on]: taps while a note is heard and its direction settled (the word's own [TunerHint]), the long buzz on becoming
     * in tune. Anything but a note (listening, the 12th-fret check, the strum check, a reference tone or TalkBack's
     * voice set aside) is a silence to it. Off, it starts afresh. Allocates nothing.
     */
    private fun followGuide(guide: VibrationGuide, shown: TunerUiState, on: Boolean, precision: InTunePrecision) {
        if (!on) {
            guide.reset()
            return
        }
        val pulse = if (shown is TunerUiState.Detected) {
            guide.update(guideCueOf(shown.hint), shown.cents, shown.targetMidi, pipeline.frameSeconds, InTuneDetector.enterCentsFor(precision))
        } else {
            guide.update(GuideCue.NONE, Float.NaN, NO_GUIDE_TARGET, pipeline.frameSeconds)
        }
        guideVibrator.play(pulse)
    }

    /** Publishes [allInTune]'s strings in tune as [tunedStrings], only when they changed (a few times a session). */
    private fun publishTuned(allInTune: AllStringsInTune) {
        val mask = allInTune.tuned
        val shown = tunedNow.value
        val tuning = allInTune.tuning
        when {
            mask == 0L || tuning == null -> if (shown != null) tunedNow.value = null
            shown == null || shown.mask != mask || shown.tuning != tuning -> tunedNow.value = TunedStrings(tuning, mask)
        }
    }

    /** Publishes what the tuner shows of what it heard, only when it changed (at most ten times a second while a strum rings). */
    private fun publishHeard(shown: HeardStrum) {
        if (heardNow.value != shown) heardNow.value = shown
    }

    /** Opens the microphone again after an error. */
    fun retry() {
        attempt.value++
    }

    companion object {
        /**
         * After a reference tone has left the speaker the microphone is ignored this much longer, so
         * the room's echo of it isn't read as a note either.
         */
        const val TONE_TAIL_SECONDS = 0.3f

        /** The guide's target while no note is heard (any number does: the cue says there is none). */
        private const val NO_GUIDE_TARGET = Int.MIN_VALUE
    }

    /**
     * The readings while the microphone is open. A screen reader's voice is watched for as long as it is ([watching]:
     * nothing is asked of the system once the tuner stops listening).
     */
    private fun listen(): Flow<TunerUiState> = flow {
        screenReaderSpeech.watching { speaking ->
            // Whether the vibration guide is on, followed while listening: the player's choice, else the screen reader.
            val guideOn = combine(settingsRepository.settings, screenReaderSpeech.screenReaderOn) { settings, screenReader ->
                settings.vibrationGuideOn(screenReader)
            }.stateIn(this)
            emitAll(readings(speaking, guideOn))
        }
    }
        // Leaving the tuner ends the wait: the window comes only right after the tuning, never on coming back.
        // The strings ticked off go too: they say what was tuned since the tuner opened. The guide stops at once.
        .onCompletion {
            ratingWait?.cancel()
            ratingWait = null
            tunedNow.value = null
            heardNow.value = HeardStrum.None
            strumOn.value = false
            guideVibrator.stop()
        }
        // Most frames look the same on screen as the last state sent: an equal Listening state in
        // silence, or a note whose readout and needle moved less than the screen shows. Dropping
        // them here, on the DSP thread, keeps them from waking the main thread ~47 times a second.
        .distinctUntilChanged { old, new -> old.looksLike(new) }
        .catch { emit(TunerUiState.Error(it.toTunerError())) }
        .flowOn(dspDispatcher)

    /**
     * What the tuner shows of each frame; [speaking] says whether a screen reader's voice plays now, [guideOn] whether
     * the vibration guide is on.
     */
    private fun readings(speaking: StateFlow<Boolean>, guideOn: StateFlow<Boolean>): Flow<TunerUiState> = flow {
        var interpreter = TunerInterpreter(pipeline.frameSeconds)
        // The vibration guide (Phase 85) follows every state shown, so its taps, the word and the colour agree.
        val guide = VibrationGuide()
        val intonationInterpreter = IntonationInterpreter(pipeline.frameSeconds, intonationResults)
        val heardInterpreter = HeardStrumInterpreter()
        val heardShown = HeardShown(pipeline.frameSeconds)
        // This session's strings in tune: ticked off on their buttons, and Google's rating window (Phase 50) once the set is complete.
        val allInTune = AllStringsInTune(pipeline.frameSeconds)
        var ratingArmed = false
        val initial = options.filterNotNull().first()
        emit(if (initial.intonationChecking) intonationInterpreter.shown(initial) else interpreter.listening(initial))
        val tailFrames = ceil(TONE_TAIL_SECONDS / pipeline.frameSeconds).toInt()
        val speechTailFrames = ScreenReaderSpeech.tailFrames(pipeline.frameSeconds)
        // Frames still to set aside: the tone or a screen reader's voice sounds, or its echo may still be in the room.
        var ignoredFrames = 0
        // Whether the last reading was the 12th-fret check's: the tuner's smoothing starts afresh after it.
        var elsewhere = false
        // The strum analysis beside the tuner hears what the tuner hears while the strum check's button is on, unless
        // the app's own tone or a screen reader's voice (or its echo) would be heard. Every sound is then a strum: the
        // player said one comes.
        val hearing = object : StrumHearing {
            override fun a4Hz(): Float {
                val current = options.value ?: initial
                val quiet = ignoredFrames == 0 && !tonePlayer.isPlaying.value && !speaking.value
                return if (current.strumChecking && quiet) current.a4Hz else Float.NaN
            }

            override fun openStrings(): FloatArray? = (options.value ?: initial).heardStrings

            override fun range(): PitchRange = (options.value ?: initial).pitchRange
        }
        // The button's turn whose strum is shown: a new one clears the marks of the strum before.
        var shownAttempt = initial.strumAttempt
        emitAll(
            pipeline.listen(
                // The window follows the tuning's lowest string (bass notes need the longer one).
                range = { (options.value ?: initial).pitchRange },
                hearing = hearing,
            ).filter { reading ->
                // The analysis beside the tuner has a flow of its own; the tuner's state goes on.
                val current = options.value ?: initial
                if (current.strumAttempt != shownAttempt) {
                    shownAttempt = current.strumAttempt
                    heardInterpreter.reset(current)
                    publishHeard(heardShown.reset())
                }
                if (reading is HeardReading) {
                    val shown = heardShown.heard(heardInterpreter.interpret(reading, current))
                    publishHeard(shown)
                    // The strum the button waited for has rung out (or a new sound began over it): the button turns
                    // off, and the strings are tuned one by one with its marks. A sound too few strings rang in waits on.
                    val check = (heardInterpreter.shown as? HeardStrum.OpenStrings)?.check
                    if (current.strumCheck && check != null && check.strings.isNotEmpty() && check.phase != StrumPhase.RINGING) {
                        strumOn.value = false
                    }
                    return@filter false
                }
                if (!current.hearing && heardInterpreter.shown != HeardStrum.None) {
                    heardInterpreter.reset(current)
                    publishHeard(heardShown.reset())
                }
                true
            }.map { reading ->
                val current = options.value ?: initial
                reading as PitchReading
                if (elsewhere && !current.intonationChecking) {
                    elsewhere = false
                    interpreter = TunerInterpreter(pipeline.frameSeconds)
                }
                if (tonePlayer.isPlaying.value) ignoredFrames = maxOf(ignoredFrames, tailFrames + 1)
                if (speaking.value) ignoredFrames = maxOf(ignoredFrames, speechTailFrames + 1)
                if (ignoredFrames > 0) {
                    // The mic would hear the app's own tone or the screen reader's voice: show the listening state
                    // instead, and start the smoothing afresh afterwards so nothing of that sound carries over.
                    if (--ignoredFrames == 0) interpreter = TunerInterpreter(pipeline.frameSeconds)
                    return@map if (current.intonationChecking) intonationInterpreter.shown(current) else interpreter.listening(current)
                }
                if (current.intonationChecking) {
                    elsewhere = true
                    return@map intonationInterpreter.interpret(reading, current)
                }
                val own = interpreter.interpret(reading, current)
                // What the tuner heard besides one string follows the tuner's own reading; while a strum rings, the
                // note read is one of the strum's, so the tuner waits instead (the lens and the ruler quiet).
                publishHeard(heardShown.tuner(own))
                if (heardShown.setsAside && own is TunerUiState.Detected) interpreter.listening(current) else own
            }.onEach {
                val precision = (options.value ?: initial).inTunePrecision
                val completed = allInTune.update(it, InTuneDetector.exitCentsFor(precision))
                publishTuned(allInTune)
                ratingArmed = followRatingMoment(it, completed, ratingArmed)
                followGuide(guide, it, guideOn.value && shown, precision)
            },
        )
    }
}

/** The vibration guide's cue for the tuner's held-steady [hint]: the same direction as the word under the meter. */
internal fun guideCueOf(hint: TunerHint?): GuideCue = when (hint) {
    null -> GuideCue.WAIT
    TunerHint.TUNE_UP -> GuideCue.TIGHTEN
    TunerHint.TUNE_DOWN -> GuideCue.LOOSEN
    TunerHint.IN_TUNE -> GuideCue.IN_TUNE
}

/** The string the 12th-fret check checks (null: none chosen), and a count that starts it afresh when it goes up. */
private data class IntonationChoice(val string: Int?, val attempt: Int)

/** Whether the strum check's button is on, and how often it was turned on (each time the marks before go). */
private data class StrumChoice(val on: Boolean, val attempt: Int)

/** Whether the 12th-fret check is on, its [choice], and the strum check's [strum]; one value, so the options combine stays small. */
private data class ToolChoice(val intonation: Boolean, val choice: IntonationChoice, val strum: StrumChoice)

/** When the app was first used and whether Google's rating window was asked for (saved settings). */
private data class RatingRecord(val firstUseMillis: Long, val asked: Boolean)

/** A tuning as the instrument and tuning panel shows it: its notes named in [notation]. */
data class PanelTuning(val tuning: Tuning, val notation: Notation)
