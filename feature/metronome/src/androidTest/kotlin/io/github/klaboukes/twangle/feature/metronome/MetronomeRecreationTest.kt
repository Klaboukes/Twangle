package io.github.klaboukes.twangle.feature.metronome

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeMetronomePlayer
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The phone turning or dark mode switching recreates the activity; the metronome plays on through
 * it (the screen leaving for good stops it: [MetronomeScreenTest]). Its own activity, since it
 * recreates it.
 */
class MetronomeRecreationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aConfigurationChangeLeavesTheClicksPlaying() {
        val player = FakeMetronomePlayer()
        compose.setContent {
            TwangleTheme(darkTheme = false, animate = false) {
                MetronomeScreen(player, FakeSettingsRepository(), FakeAudioRecorder.silence(), ToolSwitches.ALL_ON)
            }
        }
        compose.onNodeWithTag(MetronomeTestTags.START).performClick()
        compose.waitForIdle()
        assertTrue(player.isPlaying.value)

        compose.activityRule.scenario.recreate()
        assertTrue("plays on through the recreation", player.isPlaying.value)
        assertEquals(0, player.stopCount)
    }
}
