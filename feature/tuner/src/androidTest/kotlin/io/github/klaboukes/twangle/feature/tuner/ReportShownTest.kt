package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * [ReportShown] says the screen is in front only while it is resumed and its window has the focus (Phase 91): a pause
 * without a stop (an assistant over the app) or a window over it (here a dialog, as an assistant's or a split screen's
 * other app takes the focus) leaves the front and comes back, so a silenced microphone is tried again.
 */
@RunWith(AndroidJUnit4::class)
class ReportShownTest {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val shown = mutableListOf<Boolean>()
    private val inFront = mutableListOf<Boolean>()
    private val dialogOpen = mutableStateOf(false)

    /** A lifecycle of the test's own, so it can pause and stop the screen without leaving the shared activity. */
    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun show() {
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                ReportShown(setShown = { shown += it }, setInFront = { inFront += it })
                if (dialogOpen.value) Dialog(onDismissRequest = { dialogOpen.value = false }) { Text("Over the tuner") }
            }
        }
        compose.waitUntil(WAIT_MILLIS) { inFront.lastOrNull() == true }
    }

    private fun moveTo(state: Lifecycle.State) {
        compose.runOnIdle { owner.registry.currentState = state }
        compose.waitForIdle()
    }

    @Test
    fun aPauseWithoutAStopLeavesTheFrontAndTheResumeComesBack() {
        show()
        assertEquals(listOf(true), shown)

        moveTo(Lifecycle.State.STARTED)
        compose.waitUntil(WAIT_MILLIS) { inFront.lastOrNull() == false }
        assertEquals("still shown", listOf(true), shown)

        moveTo(Lifecycle.State.RESUMED)
        compose.waitUntil(WAIT_MILLIS) { inFront.lastOrNull() == true }
        assertEquals(listOf(true, false, true), inFront)
        assertEquals(listOf(true), shown)
    }

    @Test
    fun aStopLeavesTheFrontAndHidesTheScreen() {
        show()
        moveTo(Lifecycle.State.CREATED)
        compose.waitUntil(WAIT_MILLIS) { inFront.lastOrNull() == false }
        assertEquals(listOf(true, false), shown)

        moveTo(Lifecycle.State.RESUMED)
        compose.waitUntil(WAIT_MILLIS) { inFront.lastOrNull() == true }
        assertEquals(listOf(true, false, true), shown)
    }

    @Test
    fun aWindowThatTakesTheFocusLeavesTheFrontWhileTheScreenStaysResumed() {
        show()
        compose.runOnIdle { dialogOpen.value = true }
        compose.waitUntil(WAIT_MILLIS) { inFront.lastOrNull() == false }

        compose.runOnIdle { dialogOpen.value = false }
        compose.waitUntil(WAIT_MILLIS) { inFront.lastOrNull() == true }
        assertEquals(listOf(true, false, true), inFront)
        assertEquals("never stopped", listOf(true), shown)
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 20L
        const val WAIT_MILLIS = 5_000L
    }
}
