package io.github.klaboukes.twangle.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Tells [onChange] whether the watch's screen is on and interactive, at once and whenever that
 * changes, while this is in the composition. Going ambient (always-on) counts as off: Wear OS
 * sends `ACTION_SCREEN_OFF` when the display leaves interactive mode, as it does for a dark screen.
 * The screen coming on counts only while the tuner is on it ([screenReport]); otherwise the tuner
 * catches up when it comes back.
 */
@Composable
fun ScreenOnEffect(onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val current by rememberUpdatedState(onChange)
    fun isInteractive() = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    DisposableEffect(context, lifecycle) {
        current(isInteractive())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                screenReport(intent.action, tunerOnScreen = started)?.let(current)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        // System broadcasts reach a not-exported receiver; nothing else can send to it.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    // A broadcast missed or left out while the app was away is caught up on when it comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { current(isInteractive()) }
}

/**
 * What a screen broadcast tells the tuner, or null for nothing: off always; on only while
 * [tunerOnScreen]. Waking from always-on lands on the watch face, not the tuner, and within the
 * microphone's 5 s grace a screen-on would open it behind the watch face until Android silenced it
 * (seen on the Galaxy Watch 5, 2026-10-31; CLAUDE.md, hard rule 4).
 */
internal fun screenReport(action: String?, tunerOnScreen: Boolean): Boolean? = when (action) {
    Intent.ACTION_SCREEN_OFF -> false
    Intent.ACTION_SCREEN_ON -> if (tunerOnScreen) true else null
    else -> null
}
