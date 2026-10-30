package io.github.klaboukes.twangle.wear

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The wrist tap on the Wear OS emulator: when it plays, and that the real motor call doesn't crash. */
@RunWith(AndroidJUnit4::class)
class WatchInTuneTapDeviceTest {

    @get:Rule
    val compose = createComposeRule()

    private var taps = 0
    private var now = 0L
    private var state by mutableStateOf<WatchTunerState>(WatchTunerState.Listening)

    private val a2 = Note.of(PitchClass.A, 2)

    private fun set(next: WatchTunerState, atMillis: Long) {
        now = atMillis
        state = next
        compose.waitForIdle()
    }

    @Test
    fun tapsOnceAsTheNoteComesInTune() {
        compose.setContent { WatchInTuneTapEffect(state, tap = { taps++ }, clock = { now }) }
        set(WatchTunerState.Detected(a2, -9), 100)
        set(WatchTunerState.Detected(a2, -2, inTune = true), 500)
        set(WatchTunerState.Detected(a2, 1, inTune = true), 600)
        assertEquals(1, taps)

        set(WatchTunerState.Detected(a2, 9), 700)
        set(WatchTunerState.Detected(a2, 2, inTune = true), 900)
        assertEquals("a wobble doesn't tap again", 1, taps)

        // A new note always earns "in tune" afresh in the pipeline, so it shows out of tune first.
        set(WatchTunerState.Detected(Note.of(PitchClass.D, 3), 0), 1_000)
        set(WatchTunerState.Detected(Note.of(PitchClass.D, 3), 0, inTune = true), 1_300)
        assertEquals("another note taps", 2, taps)
    }

    @Test
    fun doesNotTapWhenItAppearsOnANoteInTune() {
        state = WatchTunerState.Detected(a2, 0, inTune = true)
        compose.setContent { WatchInTuneTapEffect(state, tap = { taps++ }, clock = { now }) }
        compose.waitForIdle()
        assertEquals(0, taps)
    }

    @Test
    fun doesNotTapAgainWhenTheActivityIsMadeAgain() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { WatchInTuneTapEffect(state, tap = { taps++ }, clock = { now }) }
        set(WatchTunerState.Detected(a2, 0, inTune = true), 100)
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals(1, taps)
    }

    @Test
    fun theRealMotorTapPlaysWithoutTrouble() {
        // The emulator has no motor; the call must still go through (VIBRATE granted, effect valid).
        var tap: () -> Unit = {}
        compose.setContent { tap = rememberWristTap() }
        compose.runOnIdle { tap() }
    }
}
