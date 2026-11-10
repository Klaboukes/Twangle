package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

/**
 * The readings of [listen], which ends only in a [TunerUiState.Error] (its own `catch` shows it); after an error it
 * listens again by itself once the screen has gone and come back ([shown] false, then true), as the watch's tuner does
 * (`WatchTunerViewModel.shown()`). Android silences a recording the moment its app leaves the screen, which reads as a
 * busy microphone: within the 5 s the microphone is kept after the screen goes, a quick trip to Home would otherwise
 * greet the player with "Microphone is busy" on their return, until Try again. Coming back after those 5 s starts
 * afresh anyway (the sharing stopped, and this flow with it).
 */
internal fun listenAgainOnReturn(shown: StateFlow<Boolean>, listen: () -> Flow<TunerUiState>): Flow<TunerUiState> = flow {
    while (true) {
        var last: TunerUiState? = null
        emitAll(listen().onEach { last = it })
        // Ended some other way (a test's recorder that ran out): nothing to try again.
        if (last !is TunerUiState.Error) return@flow
        shown.first { !it }
        shown.first { it }
    }
}

/** Tells [setShown] whether the screen is shown: true once started, false when stopped or gone from the composition. */
@Composable
internal fun ReportShown(setShown: (Boolean) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentSetShown by rememberUpdatedState(setShown)
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
            currentSetShown(false)
        }
    }
}
