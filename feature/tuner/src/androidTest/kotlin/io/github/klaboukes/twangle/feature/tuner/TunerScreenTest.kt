package io.github.klaboukes.twangle.feature.tuner

import android.Manifest
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.klaboukes.twangle.core.audio.AudioConfig
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.testing.Navigation
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * The whole tuner screen (permission, ViewModel and settings) on a phone on its side, beside the app's
 * navigation rail: the wide layout with the pegs by default, without them in focus mode (which the app holds,
 * 2.0 Phase 73), where a tap brings the controls for 5 s. Plays silence from a fake recorder, so the real
 * microphone stays closed.
 */
@RunWith(AndroidJUnit4::class)
class TunerScreenTest {

    private val compose = createSharedActivityComposeRule()

    // Outermost, so a test that hangs fails in seconds; the permission is granted before the screen shows.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val silence = FakeAudioRecorder(signal = FloatArray(AudioConfig().chunkSize), loop = true)

    /** Whether [show] says focus mode is on; set it to switch while the screen is shown. */
    private val focusMode = mutableStateOf(false)
    private val focusChanges = mutableListOf<Boolean>()

    private fun show(settings: FakeSettingsRepository, focus: Boolean = false) {
        focusMode.value = focus
        compose.setContent {
            PhoneScreen(PHONE_LANDSCAPE, SystemBars.tab(onItsSide = true, Navigation.BUTTONS)) {
                MaterialTheme {
                    TunerScreen(
                        audioRecorder = silence,
                        settingsRepository = settings,
                        tonePlayer = FakeTonePlayer(),
                        tools = ToolSwitches.ALL_ON,
                        saved = settings.current,
                        focusMode = focusMode.value,
                        onFocusModeChange = {
                            focusChanges += it
                            focusMode.value = it
                        },
                    )
                }
            }
        }
    }

    private val pickerChip get() = compose.onNodeWithTag(TunerTestTags.PICKER_CHIP)

    @Test
    fun onItsSideTheScreenShowsTheWideLayoutWithThePegsByDefault() {
        show(FakeSettingsRepository())
        compose.onNodeWithTag(TunerTestTags.WIDE).assertExists()
        compose.onNodeWithTag(TunerTestTags.GAUGE).assertExists()
        pickerChip.assertExists()
    }

    @Test
    fun onItsSideFocusModeShowsTheWideLayoutAlone() {
        show(FakeSettingsRepository(), focus = true)
        compose.onNodeWithTag(TunerTestTags.WIDE).assertExists()
        compose.onNodeWithTag(TunerTestTags.GAUGE).assertExists()
        pickerChip.assertDoesNotExist()
    }

    /** The button turns focus mode on, its exit twin off; nothing is saved (the app holds it, 2.0 Phase 73). */
    @Test
    fun onItsSideTheButtonsTurnFocusModeOnAndOff() {
        val settings = FakeSettingsRepository()
        show(settings)
        compose.onNodeWithTag(TunerTestTags.ENTER_FOCUS).performClick()
        compose.waitForIdle()
        pickerChip.assertDoesNotExist()
        compose.onNodeWithTag(TunerTestTags.EXIT_FOCUS).performClick()
        compose.waitForIdle()
        pickerChip.assertExists()
        assertEquals(listOf(true, false), focusChanges)
        assertEquals("nothing saved", AppSettings(), settings.current)
    }

    /** In focus mode a tap brings the tuner's own controls for 5 s; the full-screen tuner has the exit button. */
    @Test
    fun inFocusModeATapShowsTheControlsFor5Seconds() {
        compose.mainClock.autoAdvance = false
        show(FakeSettingsRepository(), focus = true)
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithTag(TunerTestTags.EXIT_FOCUS).assertExists()
        pickerChip.assertDoesNotExist()

        compose.onNodeWithTag(TunerTestTags.COMPACT).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        pickerChip.assertExists()

        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS + 1_000)
        pickerChip.assertDoesNotExist()
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 10L

        /** A Galaxy S26 on its side (360 dp tall), without the status bar and the app's 80 dp navigation rail. */
        val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
    }
}
