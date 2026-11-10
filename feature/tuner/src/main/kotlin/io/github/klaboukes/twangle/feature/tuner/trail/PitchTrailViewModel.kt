package io.github.klaboukes.twangle.feature.tuner.trail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.ScreenReaderSpeech
import io.github.klaboukes.twangle.core.audio.watching
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.tuner.ListenLog
import io.github.klaboukes.twangle.feature.tuner.PitchPipeline
import io.github.klaboukes.twangle.feature.tuner.PitchTrailFeed
import io.github.klaboukes.twangle.feature.tuner.TunerInterpreter
import io.github.klaboukes.twangle.feature.tuner.TunerOptions
import io.github.klaboukes.twangle.feature.tuner.TunerUiState
import io.github.klaboukes.twangle.feature.tuner.ScreenFront
import io.github.klaboukes.twangle.feature.tuner.listenAgainOnReturn
import io.github.klaboukes.twangle.feature.tuner.looksLike
import io.github.klaboukes.twangle.feature.tuner.showingMicFailures
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

/**
 * The pitch trail's ViewModel (2.0, Phase 78; docs/architecture/ui-tuner.md, *The pitch trail*): the tuner's own
 * pipeline and smoothing on [recorder] in Any note (chromatic), so a held note is heard and named exactly as the
 * tuner hears it, at the saved concert pitch, notation and in-tune precision. Every reading also goes into [history]
 * ([PitchTrailFeed], on the DSP thread), which the trail draws; anything but a note is a gap in its line.
 *
 * The microphone is open only while [state] is collected and the permission is granted
 * ([SharingStarted.WhileSubscribed], hard rule 4); each opening starts the line afresh, and a failure is tried again
 * when the screen is back in front ([setInFront]). [language] is the app's
 * language, whose usual notation names the note until the player picks one.
 *
 * While a screen reader's voice plays ([screenReaderSpeech]) and for [ScreenReaderSpeech.TAIL_SECONDS] after, the
 * readings are set aside as the tuner sets them aside (Phase 81): the trail shows a gap, not TalkBack's speech.
 * Why listening stopped goes to [listenLog], and a failure that isn't the microphone's is thrown on ([showingMicFailures]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PitchTrailViewModel(
    recorder: AudioRecorder,
    private val settingsRepository: SettingsRepository,
    private val dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
    saved: AppSettings = AppSettings(),
    language: String = "",
    private val screenReaderSpeech: ScreenReaderSpeech = ScreenReaderSpeech.NONE,
    private val listenLog: ListenLog = ListenLog.NONE,
) : ViewModel() {

    private val appLanguage = MutableStateFlow(language)
    private val pipeline = PitchPipeline(recorder, dspDispatcher)
    private val permissionGranted = MutableStateFlow(false)
    private val attempt = MutableStateFlow(0)

    /** Whether the trail's screen is in front ([setInFront]): a failure from while it wasn't is tried again on its return. */
    private val front = ScreenFront()
    private val feed = PitchTrailFeed(PitchHistory())

    /** The last seconds of readings, written on the DSP thread and read while drawing (see [PitchHistory]). */
    val history: PitchHistory get() = feed.history

    /** How close counts as in tune (a saved setting): the trail's band and the green. */
    val inTunePrecision: StateFlow<InTunePrecision> = settingsRepository.settings
        .map { it.inTunePrecision }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), saved.inTunePrecision)

    /** Whether the player refused the microphone before (saved), so the prompt tells a dismissed dialog from a lost one. */
    val micRefused: StateFlow<Boolean> = settingsRepository.settings
        .map { it.micRefused }
        .stateIn(viewModelScope, SharingStarted.Eagerly, saved.micRefused)

    /** What readings are compared with, in Any note; null until the saved settings have loaded. */
    private val options: StateFlow<TunerOptions?> =
        combine(settingsRepository.settings, appLanguage) { settings, language ->
            TunerOptions(
                mode = TunerMode.CHROMATIC,
                tuning = settings.tuning,
                a4Hz = settings.a4Hz,
                notation = settings.notationFor(language),
                inTunePrecision = settings.inTunePrecision,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<TunerUiState> = combine(permissionGranted, attempt) { granted, _ -> granted }
        .flatMapLatest { granted -> if (granted) listenAgainOnReturn(front.returns, ::listen) else flowOf(TunerUiState.PermissionRequired) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TunerUiState.PermissionRequired)

    /**
     * The trail's screen is in front or not ([ReportShown]: resumed with its window focused; not when the app is in the
     * background or another screen is open). The microphone stays open 5 s after the screen goes (hard rule 4); one that
     * failed while it wasn't in front (Android silences it in the background, under an assistant, beside the app of a
     * split screen that records) is tried again when it is back in front ([listenAgainOnReturn]).
     */
    fun setInFront(inFront: Boolean) {
        front.set(inFront)
    }

    /** Tells the ViewModel whether `RECORD_AUDIO` is granted. The microphone stays closed until it is. */
    fun onPermissionChanged(granted: Boolean) {
        permissionGranted.value = granted
    }

    /** The player refused the microphone in Android's dialog: saved, as the tuner saves it. */
    fun onMicRefused() {
        if (!micRefused.value) viewModelScope.launch { settingsRepository.setMicRefused(true) }
    }

    /** The app's language changed: the note's name follows its habit unless the player picked a notation. */
    fun setLanguage(language: String) {
        appLanguage.value = language
    }

    /** Opens the microphone again after it failed (Try again). */
    fun retry() {
        attempt.value++
    }

    /** The readings while the microphone is open, a screen reader's voice watched as long ([watching]). */
    private fun listen(): Flow<TunerUiState> = flow {
        screenReaderSpeech.watching { speaking -> emitAll(readings(speaking)) }
    }
        // Most frames look the same on screen as the last one sent; dropped here, on the DSP thread. The history has
        // every reading already.
        .distinctUntilChanged { old, new -> old.looksLike(new) }
        .showingMicFailures(listenLog)
        .flowOn(dspDispatcher)

    /** What the trail shows of each frame; [speaking] says whether a screen reader's voice plays now. */
    private fun readings(speaking: StateFlow<Boolean>): Flow<TunerUiState> = flow {
        var interpreter = TunerInterpreter(pipeline.frameSeconds)
        val initial = options.filterNotNull().first()
        feed.start()
        emit(interpreter.listening(initial))
        val frameSeconds = pipeline.frameSeconds
        val tailFrames = ScreenReaderSpeech.tailFrames(frameSeconds)
        // Frames still to set aside: the voice plays, or its echo may still be in the room.
        var ignoredFrames = 0
        emitAll(
            pipeline.readings(range = { (options.value ?: initial).pitchRange }).map { reading ->
                val current = options.value ?: initial
                if (speaking.value) ignoredFrames = tailFrames + 1
                val state = if (ignoredFrames > 0) {
                    // The smoothing starts afresh afterwards, so nothing of the voice carries over.
                    if (--ignoredFrames == 0) interpreter = TunerInterpreter(frameSeconds)
                    interpreter.listening(current)
                } else {
                    interpreter.interpret(reading, current)
                }
                feed.record(state, frameSeconds)
                state
            },
        )
    }
}
