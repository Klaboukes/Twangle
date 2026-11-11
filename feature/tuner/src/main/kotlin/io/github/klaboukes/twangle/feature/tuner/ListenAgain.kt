package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The readings of [listen], which ends only in a [TunerUiState.Error] (its own `catch` shows it); after an error it
 * listens again by itself once the screen is back in front ([ScreenFront.returns] counts up), as the watch's tuner does
 * on its return (`WatchTunerViewModel.shown()`). Android silences a recording the moment its app leaves the screen, and
 * from Android 10 also while something else is in front of it (an assistant over it, the other app of a split screen
 * that records), which reads as a busy microphone: without this, a quick trip to Home within the 5 s the microphone is
 * kept, or an assistant opened and closed, would leave "Microphone is busy" until Try again (Phase 89 for the trip,
 * Phase 91 for the pause). Coming back after those 5 s starts afresh anyway (the sharing stopped, and this flow with
 * it). Only a return after the error starts the new try, so a failure while the screen stays in front waits for Try
 * again, nothing is tried while it is behind something, and the microphone is held no longer than before (hard rule 4).
 */
internal fun listenAgainOnReturn(returns: StateFlow<Int>, listen: () -> Flow<TunerUiState>): Flow<TunerUiState> = flow {
    while (true) {
        var last: TunerUiState? = null
        emitAll(listen().onEach { last = it })
        // Ended some other way (a test's recorder that ran out): nothing to try again.
        if (last !is TunerUiState.Error) return@flow
        val failedAt = returns.value
        returns.first { it > failedAt }
    }
}

/**
 * As `stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis), initial)`, except that when the sharing stops
 * (nothing shown it for [stopTimeoutMillis], the microphone let go of) the value becomes [atRest] of the last one. A
 * screen that comes back after that starts from it instead of from the last note heard, which it showed for a frame
 * until the new listening's first state arrived (Phase 91).
 */
internal fun Flow<TunerUiState>.shownWhileWatched(
    scope: CoroutineScope,
    initial: TunerUiState,
    stopTimeoutMillis: Long,
    atRest: (TunerUiState) -> TunerUiState = TunerUiState::atRest,
): StateFlow<TunerUiState> {
    val shown = MutableStateFlow(initial)
    scope.launch {
        SharingStarted.WhileSubscribed(stopTimeoutMillis).command(shown.subscriptionCount)
            .distinctUntilChanged()
            .collectLatest { command ->
                when (command) {
                    SharingCommand.START -> this@shownWhileWatched.collect { shown.value = it }
                    SharingCommand.STOP -> shown.value = atRest(shown.value)
                    SharingCommand.STOP_AND_RESET_REPLAY_CACHE -> shown.value = initial
                }
            }
    }
    return shown.asStateFlow()
}

/**
 * What the tuner shows once it stopped listening: a note heard becomes listening for it (the same mode, tuning, lock and
 * names, the needle gone), a tip about the sound goes; an error or the tools' states stay.
 */
internal fun TunerUiState.atRest(): TunerUiState = when (this) {
    is TunerUiState.Detected -> TunerUiState.Listening(mode, tuning, a4Hz, lockedString, notation)
    is TunerUiState.Listening -> if (tip == null) this else copy(tip = null)
    else -> this
}

/**
 * Whether a screen is in front ([set], from [ReportShown]) and how many times it came back to the front ([returns]): a
 * count rather than the flag itself, so a return is never missed however quickly it follows the leaving (a flag that
 * goes false and true again between two looks would seem never to have changed). Starts in front. Set on the main
 * thread.
 */
internal class ScreenFront {
    private var inFront = true
    private val count = MutableStateFlow(0)

    /** How many times the screen came back to the front. */
    val returns: StateFlow<Int> = count.asStateFlow()

    fun set(inFront: Boolean) {
        if (inFront && !this.inFront) count.update { it + 1 }
        this.inFront = inFront
    }
}

/**
 * Tells [setShown] whether the screen is shown (true once started, false when stopped or gone from the composition)
 * and [setInFront] whether it is in front: resumed, and its window has the focus. An assistant laid over the app, the
 * notification shade, the other app of a split screen picked or a dialog of the app's own take the front without
 * stopping the screen (on Android 10 and later the apps of a split screen are both resumed, so the focus is what tells
 * which one the player is using); a stopped screen is never in front.
 */
@Composable
internal fun ReportShown(setShown: (Boolean) -> Unit, setInFront: (Boolean) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val windowInfo = LocalWindowInfo.current
    val currentSetShown by rememberUpdatedState(setShown)
    val currentSetInFront by rememberUpdatedState(setInFront)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> currentSetShown(true)
                Lifecycle.Event.ON_STOP -> currentSetShown(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            currentSetInFront(false)
            currentSetShown(false)
        }
    }
    LaunchedEffect(lifecycle, windowInfo) {
        combine(
            lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.RESUMED) },
            snapshotFlow { windowInfo.isWindowFocused },
        ) { resumed, focused -> resumed && focused }
            .distinctUntilChanged()
            .collect { currentSetInFront(it) }
    }
}
