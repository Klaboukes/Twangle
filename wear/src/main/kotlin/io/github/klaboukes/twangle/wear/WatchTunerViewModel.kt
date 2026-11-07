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

/**
 * Holds the watch tuner's [state]. The microphone is open only while someone collects [state]
 * (the screen, through `collectAsStateWithLifecycle`), plus a 5 s grace so a quick trip away
 * doesn't restart it (CLAUDE.md, hard rule 4). When the watch's screen turns off or goes ambient
 * ([setScreenOn] false) the microphone is let go of at once, without that grace, and opened again
 * when the screen comes back on. A failure shows as [WatchTunerState.Failed] until [retry].
 * While [screenReaderSpeech] says TalkBack's voice plays, the reading holds still (Phase 81, [WatchPitchPipeline]).
 */
class WatchTunerViewModel(
    recorder: AudioRecorder,
    dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
    screenReaderSpeech: ScreenReaderSpeech = ScreenReaderSpeech.NONE,
) : ViewModel() {

    private val pipeline = WatchPitchPipeline(recorder, dspDispatcher = dspDispatcher, screenReaderSpeech = screenReaderSpeech)
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

    companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
