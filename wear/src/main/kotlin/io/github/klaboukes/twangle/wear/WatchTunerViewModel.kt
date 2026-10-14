package io.github.klaboukes.twangle.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Holds the watch tuner's [state]. The microphone is open only while someone collects [state]
 * (the screen, through `collectAsStateWithLifecycle`), plus a 5 s grace so a quick trip away
 * doesn't restart it (CLAUDE.md, hard rule 4). A failure shows as [WatchTunerState.Failed] until
 * [retry].
 */
class WatchTunerViewModel(
    recorder: AudioRecorder,
    dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val pipeline = WatchPitchPipeline(recorder, dspDispatcher = dspDispatcher)
    private val attempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<WatchTunerState> = attempt
        .flatMapLatest {
            pipeline.states().catch { emit(WatchTunerState.Failed(WatchTunerError.of(it))) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WatchTunerState.Listening)

    /** Opens the microphone again after a failure. */
    fun retry() {
        attempt.value++
    }

    companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
