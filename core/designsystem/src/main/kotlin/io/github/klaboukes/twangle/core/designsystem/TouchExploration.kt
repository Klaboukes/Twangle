package io.github.klaboukes.twangle.core.designsystem

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether touch exploration is on (TalkBack and the screen readers built like it), followed while the screen shows.
 * A screen reader's swipes and double taps reach the app as accessibility actions, not touches, so whatever hides
 * itself until the next touch (focus mode's controls) must stay put while this is on.
 */
@Composable
fun rememberTouchExploration(): Boolean {
    val manager = LocalContext.current.getSystemService(AccessibilityManager::class.java)
    val on = remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        if (manager == null) return@DisposableEffect onDispose {}
        // Told on the main thread.
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on.value = it }
        manager.addTouchExplorationStateChangeListener(listener)
        // It may have changed between reading it and listening.
        on.value = manager.isTouchExplorationEnabled
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    return on.value
}
