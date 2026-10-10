package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InTuneHapticTest {

    @get:Rule
    val compose = createComposeRule()

    private var ticks = 0
    private var now = 0L
    private var inTune by mutableStateOf(false)
    private var target by mutableStateOf<String?>("A2")
    private var enabled by mutableStateOf(true)

    private fun show() = compose.setContent {
        InTuneHaptic(inTune, target, enabled, tick = { ticks++ }, clock = { now })
    }

    private fun set(value: Boolean, atMillis: Long) {
        now = atMillis
        inTune = value
        compose.waitForIdle()
    }

    @Test
    fun ticksOnceEachTimeANoteBecomesInTune() {
        show()
        set(true, 100)
        set(true, 200)
        assertEquals(1, ticks)

        set(false, 300)
        set(true, 5_000)
        assertEquals(2, ticks)
    }

    @Test
    fun aShortDropOutDoesNotTickAgain() {
        show()
        set(true, 100)
        set(false, 600)
        set(true, 900)
        assertEquals(1, ticks)
    }

    @Test
    fun staysQuietWhenTurnedOff() {
        enabled = false
        show()
        set(true, 100)
        assertEquals(0, ticks)
    }

    @Test
    fun doesNotTickWhenItAppearsAlreadyInTune() {
        inTune = true
        show()
        compose.waitForIdle()
        assertEquals(0, ticks)
    }

    @Test
    fun doesNotTickAgainAfterRotation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { InTuneHaptic(inTune, target, enabled, tick = { ticks++ }, clock = { now }) }
        set(true, 100)
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals(1, ticks)
    }
}
