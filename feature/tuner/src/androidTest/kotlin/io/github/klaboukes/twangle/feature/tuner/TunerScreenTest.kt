package io.github.klaboukes.twangle.feature.tuner

import android.Manifest
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
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
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * The whole tuner screen (permission, ViewModel and settings) on a phone on its side, beside the app's
 * navigation rail (which stays in focus mode too): the two-pane layout by default, the compact one in
 * focus mode, where a tap brings the controls for 5 s. Plays silence from a fake recorder, so the real
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

    private fun show(settings: FakeSettingsRepository) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(PHONE_LANDSCAPE)) {
                MaterialTheme {
                    TunerScreen(
                        audioRecorder = silence,
                        settingsRepository = settings,
                        tonePlayer = FakeTonePlayer(),
                        tools = ToolSwitches.ALL_ON,
                        savedFocusMode = settings.current.focusMode,
                    )
                }
            }
        }
    }

    private val modeSwitch get() = compose.onNodeWithText(context.getString(R.string.tuner_mode_strings))

    @Test
    fun onItsSideTheScreenKeepsTheTwoPaneLayoutByDefault() {
        show(FakeSettingsRepository())
        compose.onNodeWithTag(TunerTestTags.GAUGE).assertExists()
        modeSwitch.assertExists()
    }

    @Test
    fun onItsSideFocusModeShowsTheCompactLayout() {
        show(FakeSettingsRepository(AppSettings(focusMode = true)))
        compose.onNodeWithTag(TunerTestTags.GAUGE).assertExists()
        modeSwitch.assertDoesNotExist()
    }

    @Test
    fun onItsSideTheLayoutFollowsTheSettingWhenItChanges() {
        val settings = FakeSettingsRepository()
        show(settings)
        modeSwitch.assertExists()

        settings.current = settings.current.copy(focusMode = true)
        compose.waitForIdle()
        modeSwitch.assertDoesNotExist()

        settings.current = settings.current.copy(focusMode = false)
        compose.waitForIdle()
        modeSwitch.assertExists()
    }

    /** In focus mode a tap brings the tuner's own controls for 5 s; the full-screen tuner has the exit button. */
    @Test
    fun inFocusModeATapShowsTheControlsFor5Seconds() {
        compose.mainClock.autoAdvance = false
        show(FakeSettingsRepository(AppSettings(focusMode = true)))
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithTag(TunerTestTags.EXIT_FOCUS).assertExists()
        modeSwitch.assertDoesNotExist()

        compose.onNodeWithTag(TunerTestTags.COMPACT).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        modeSwitch.assertExists()

        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS + 1_000)
        modeSwitch.assertDoesNotExist()
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 10L

        /** A Galaxy S26 on its side (360 dp tall), without the status bar and the app's 80 dp navigation rail. */
        val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
    }
}
