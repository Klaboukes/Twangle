package io.github.klaboukes.twangle.wear

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The screen keeper listens to the tuner only while the tuner is on screen: a collector left on in
 * the background kept the microphone open there (Hard rule 4) until Android silenced it, and the
 * tuner came back from the watch face, the tile or the shortcut saying another app had the mic.
 */
@RunWith(AndroidJUnit4::class)
class WatchScreenAwakeLifecycleTest {

    @get:Rule
    val compose = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun theKeeperStopsListeningWhenTheTunerLeavesTheScreen() {
        val states = MutableStateFlow<WatchTunerState>(WatchTunerState.Listening)
        val owner = Owner()
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                rememberWatchScreenAwake(states)
            }
        }
        compose.waitUntil(5_000) { states.subscriptionCount.value == 1 }

        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitUntil(5_000) { states.subscriptionCount.value == 0 }

        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil(5_000) { states.subscriptionCount.value == 1 }
        assertEquals(1, states.subscriptionCount.value)
    }
}
