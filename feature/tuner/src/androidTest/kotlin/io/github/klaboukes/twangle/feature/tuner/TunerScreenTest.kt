package io.github.klaboukes.twangle.feature.tuner

import android.Manifest
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.klaboukes.twangle.core.audio.AudioConfig
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * The whole tuner screen (permission, ViewModel and settings) on a phone on its side: the "full
 * layout on its side" setting picks the two-pane layout, otherwise the compact one shows. Plays
 * silence from a fake recorder, so the real microphone stays closed.
 */
@RunWith(AndroidJUnit4::class)
class TunerScreenTest {

    private val compose = createComposeRule()

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
                        onOpenSettings = {},
                        savedKeepTwoPaneLandscape = settings.current.keepTwoPaneLandscape,
                    )
                }
            }
        }
    }

    private val modeSwitch get() = compose.onNodeWithText(context.getString(R.string.tuner_mode_guitar))

    @Test
    fun onItsSideTheScreenIsCompactByDefault() {
        show(FakeSettingsRepository())
        compose.onNodeWithTag(TunerTestTags.GAUGE).assertExists()
        modeSwitch.assertDoesNotExist()
    }

    @Test
    fun onItsSideTheFullLayoutSettingKeepsTheTwoPaneLayout() {
        show(FakeSettingsRepository(AppSettings(keepTwoPaneLandscape = true)))
        compose.onNodeWithTag(TunerTestTags.GAUGE).assertExists()
        modeSwitch.assertExists()
    }

    @Test
    fun onItsSideTheLayoutFollowsTheSettingWhenItChanges() {
        val settings = FakeSettingsRepository()
        show(settings)
        modeSwitch.assertDoesNotExist()

        settings.current = settings.current.copy(keepTwoPaneLandscape = true)
        compose.waitForIdle()
        modeSwitch.assertExists()

        settings.current = settings.current.copy(keepTwoPaneLandscape = false)
        compose.waitForIdle()
        modeSwitch.assertDoesNotExist()
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 10L

        /** A Galaxy S26 on its side (360 dp tall), without the status bar. */
        val PHONE_LANDSCAPE = DpSize(780.dp, 330.dp)
    }
}
