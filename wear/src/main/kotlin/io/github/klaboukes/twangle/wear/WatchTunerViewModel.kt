package io.github.klaboukes.twangle.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.ScreenReaderSpeech
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds the watch tuner's [state]. The microphone is open only while someone collects [state]
 * (the screen, through `collectAsStateWithLifecycle`), plus a 5 s grace so a quick trip away
 * doesn't restart it (CLAUDE.md, hard rule 4). When the watch's screen turns off or goes ambient
 * ([setScreenOn] false) the microphone is let go of at once, without that grace, and opened again
 * when the screen comes back on. A failure shows as [WatchTunerState.Failed] until [retry].
 * While [screenReaderSpeech] says TalkBack's voice plays, the reading holds still (Phase 81, [WatchPitchPipeline]).
 *
 * The vibration guide (Phase 85) is [guideOn]: the player's choice kept in [store], else on while a screen reader is.
 * While it is on, [guideVibrator] taps the wrist as the pipeline says, and only while the tuner is on screen ([setShown])
 * and listening; the screen's in-tune tap gives way to its long buzz.
 */
class WatchTunerViewModel(
    recorder: AudioRecorder,
    dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
    screenReaderSpeech: ScreenReaderSpeech = ScreenReaderSpeech.NONE,
    private val store: WatchTunerStore = WatchTunerStore.inMemory(),
    private val guideVibrator: WatchGuideVibrator = WatchGuideVibrator.NONE,
) : ViewModel() {

    /** Whether the vibration guide is on: the player's choice on the tuner's switch, else on while a screen reader is. */
    val guideOn: StateFlow<Boolean> = combine(store.vibrationGuide, screenReaderSpeech.screenReaderOn) { choice, reader -> watchVibrationGuideOn(choice, reader) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Whether the tuner's page is shown ([setShown]): the guide plays only then. */
    @Volatile
    private var shown = true

    private val pipeline = WatchPitchPipeline(
        recorder,
        dspDispatcher = dspDispatcher,
        screenReaderSpeech = screenReaderSpeech,
        guideVibrator = guideVibrator,
        guideOn = { guideOn.value && shown },
    )
    private val attempt = MutableStateFlow(0)
    private val screenOn = MutableStateFlow(true)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<WatchTunerState> = combine(attempt, screenOn) { _, on -> on }
        .flatMapLatest { on ->
            if (on) {
                pipeline.states().catch { emit(WatchTunerState.Failed(WatchTunerError.of(it))) }
            } else {
                // Nothing listens while the screen is off; it starts afresh when it comes back.
                flowOf(WatchTunerState.Listening)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WatchTunerState.Listening)

    /** Whether the watch's screen is on and interactive (not off, not ambient). */
    fun setScreenOn(on: Boolean) {
        screenOn.value = on
    }

    /** Opens the microphone again after a failure. */
    fun retry() {
        attempt.value++
    }

    /**
     * The tuner is on screen again (from the watch face, the tile or the shortcut). A failure from
     * while it was away gets a fresh try: Android silences a recording the moment its app leaves the
     * screen, which reads as a busy mic, and within the 5 s grace that would greet the player on
     * their return.
     */
    fun shown() {
        if (state.value is WatchTunerState.Failed) retry()
    }

    /**
     * The tuner's page is [shown] or not (the app in the background, the metronome's page). The microphone stays open
     * 5 s after it goes (hard rule 4), but the guide stops at once: a player who left the tuner shouldn't feel it.
     */
    fun setShown(shown: Boolean) {
        this.shown = shown
        if (!shown) guideVibrator.stop()
    }

    /** The player turns the vibration guide [on] or off; from then on it no longer follows the screen reader. */
    fun setVibrationGuide(on: Boolean) {
        viewModelScope.launch { store.setVibrationGuide(on) }
    }

    override fun onCleared() {
        guideVibrator.stop()
    }

    companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
