package io.github.klaboukes.twangle.wear

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView
import io.github.klaboukes.twangle.core.dsp.awake.ScreenAwakePhase
import io.github.klaboukes.twangle.core.dsp.awake.ScreenAwakeRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * The phone's Keep screen on for the watch ([ScreenAwakeRule], on the watch's clock): [held] while
 * the tuner hears playing, and 3 minutes after the last note, or the screen coming back on, the
 * screen is left to the watch's own timeout. No warning first: a raised wrist or a tap wakes the
 * watch, and the tuner listens again at once.
 */
@Stable
internal class WatchScreenAwake(private val clock: () -> Long) {
    private val rule = ScreenAwakeRule(clock(), warningMillis = 0)

    var held by mutableStateOf(true)
        private set

    // Goes up when activity takes the screen back, so [run]'s wait starts afresh.
    private var restarts by mutableIntStateOf(0)

    /** The screen came on, or the tuner came back to it: the quiet time starts afresh. */
    fun touched() {
        rule.touched(clock())
        update()
    }

    fun heard(state: WatchTunerState) {
        rule.heard(soundOf(state), clock())
        update()
    }

    private fun update() {
        val was = held
        held = rule.phase(clock()) == ScreenAwakePhase.ON
        if (!was) restarts++
    }

    /** Moves [held] on as the quiet time passes; runs until cancelled. */
    suspend fun run() {
        snapshotFlow { restarts }.collectLatest {
            while (true) {
                val now = clock()
                held = rule.phase(now) == ScreenAwakePhase.ON
                delay(rule.millisUntilChange(now) ?: break)
            }
        }
    }

    companion object {
        /** What [state] tells [ScreenAwakeRule] of the player. */
        fun soundOf(state: WatchTunerState): ScreenAwakeRule.Sound = when (state) {
            is WatchTunerState.Detected -> ScreenAwakeRule.Sound.SOUNDING
            WatchTunerState.Listening, is WatchTunerState.Failed -> ScreenAwakeRule.Sound.QUIET
        }
    }
}

/**
 * Keeps the watch's screen on while [states] tell of playing ([WatchScreenAwake]); a window flag,
 * not a wake lock. The returned keeper takes [WatchScreenAwake.touched] when the screen comes on.
 */
@Composable
internal fun rememberWatchScreenAwake(states: StateFlow<WatchTunerState>): WatchScreenAwake {
    val awake = remember { WatchScreenAwake(SystemClock::elapsedRealtime) }
    LaunchedEffect(awake) { awake.run() }
    LaunchedEffect(awake, states) { states.collect(awake::heard) }
    if (awake.held) {
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
    }
    return awake
}
