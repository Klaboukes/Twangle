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

/**
 * Tells [onChange] whether the watch's screen is on and interactive, at once and whenever that
 * changes, while this is in the composition. Going ambient (always-on) counts as off: Wear OS
 * sends `ACTION_SCREEN_OFF` when the display leaves interactive mode, as it does for a dark screen.
 */
@Composable
fun ScreenOnEffect(onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val current by rememberUpdatedState(onChange)
    fun isInteractive() = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    DisposableEffect(context) {
        current(isInteractive())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> current(false)
                    Intent.ACTION_SCREEN_ON -> current(true)
                }
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
    // A broadcast missed while the app was away is caught up on when it comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { current(isInteractive()) }
}
