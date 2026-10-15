package io.github.klaboukes.twangle.feature.metronome

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.audio.FakeMetronomePlayer
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/**
 * The metronome screen with a fake player and settings, in the default style: it opens stopped at
 * the saved tempo and meter, Start / Stop, − and +, the meter picker, the dial (a touch on its ring,
 * none on its middle, and TalkBack's slider) and the vibration switch work and are saved, and the clicks stop when the screen
 * leaves the composition. TalkBack hears every control by name. [MetronomeStylesTest] covers the
 * layout in every style.
 */
class MetronomeScreenTest {

    private val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val player = FakeMetronomePlayer()
    private val settings = FakeSettingsRepository()
    private val shown = mutableStateOf(true)

    private fun show(locale: String? = null, fontScale: Float = 1f, size: DpSize = PHONE) {
        compose.setContent {
            val base = DeviceConfigurationOverride.ForcedSize(size) then DeviceConfigurationOverride.FontScale(fontScale)
            val override = locale?.let { base then DeviceConfigurationOverride.Locales(LocaleList(it)) } ?: base
            DeviceConfigurationOverride(override) {
                TwangleTheme(style = VisualStyle.DEFAULT, darkTheme = false, animate = false) {
                    if (shown.value) {
                        MetronomeScreen(player, settings, savedBpm = 96, savedMeter = Meter.THREE_FOUR, savedVibrate = false)
                    }
                }
            }
        }
    }

    private val dial get() = compose.onNodeWithTag(MetronomeTestTags.DIAL)
    private val start get() = compose.onNodeWithTag(MetronomeTestTags.START)

    @Test
    fun opensStoppedAtTheSavedTempoAndMeter() {
        show()
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("96"))
        compose.onNodeWithText("Andante", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(MetronomeTestTags.meter(Meter.THREE_FOUR)).assertIsSelected()
        compose.onNodeWithTag(MetronomeTestTags.meter(Meter.FOUR_FOUR)).assertIsNotSelected()
        start.assert(hasText("Start"))
        assertFalse(player.isPlaying.value)
        assertEquals(0, player.startCount)
    }

    @Test
    fun startStopStepsAndMeterReachThePlayerAndAreSaved() {
        show()
        start.performClick()
        compose.waitForIdle()
        assertTrue(player.isPlaying.value)
        assertEquals(96, player.bpm)
        assertEquals(Meter.THREE_FOUR, player.meter)
        start.assert(hasText("Stop"))

        compose.onNodeWithTag(MetronomeTestTags.FASTER).performClick()
        compose.onNodeWithTag(MetronomeTestTags.FASTER).performClick()
        compose.onNodeWithTag(MetronomeTestTags.SLOWER).performClick()
        compose.onNodeWithTag(MetronomeTestTags.meter(Meter.SEVEN_EIGHT)).performClick()
        compose.waitForIdle()
        assertEquals(97, player.bpm)
        assertEquals(Meter.SEVEN_EIGHT, player.meter)
        assertEquals(97, settings.current.metronomeBpm)
        assertEquals(Meter.SEVEN_EIGHT, settings.current.metronomeMeter)
        compose.onNodeWithTag(MetronomeTestTags.meter(Meter.SEVEN_EIGHT)).assertIsSelected()

        start.performClick()
        compose.waitForIdle()
        assertFalse(player.isPlaying.value)
    }

    @Test
    fun leavingTheScreenStopsTheClicks() {
        show()
        start.performClick()
        compose.waitForIdle()
        assertTrue(player.isPlaying.value)
        shown.value = false
        compose.waitForIdle()
        assertFalse("the metronome never plays unseen", player.isPlaying.value)
    }

    @Test
    fun aTouchOnTheDialSetsTheTempoItPointsToAndSavesItOnLift() {
        show()
        // Straight up on the dial is the middle of 30-250: 140 BPM.
        dial.performTouchInput {
            down(center.copy(y = center.y - width / 3f))
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("140"))
        assertEquals(140, settings.current.metronomeBpm)
    }

    @Test
    fun aTouchOnTheTempoInTheDialsMiddleLeavesItAlone() {
        show()
        // The middle, then "BPM" under the figures: only the ring turns the dial.
        dial.performTouchInput {
            down(center)
            up()
        }
        dial.performTouchInput {
            down(center.copy(y = center.y + width / 16f))
            moveBy(Offset(width / 20f, 0f))
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("96"))
    }

    @Test
    fun talkBackHearsTheDialAsASliderThatStepsOneBpm() {
        show()
        dial.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Tempo, drag to change")))
        dial.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "96 beats per minute, Andante"))
        val range = dial.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)!!
        assertEquals(96f, range.current)
        assertEquals(30f..250f, range.range)
        assertEquals("one step per BPM", 219, range.steps)
        dial.performSemanticsAction(SemanticsActions.SetProgress) { it(121f) }
        compose.waitForIdle()
        dial.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "121 beats per minute, Allegro"))
        assertEquals(121, settings.current.metronomeBpm)
    }

    @Test
    fun talkBackHearsEveryControlByName() {
        show()
        compose.onNodeWithContentDescription("Slower").assertHasClickAction()
        compose.onNodeWithContentDescription("Faster").assertHasClickAction()
        compose.onNodeWithTag(MetronomeTestTags.TAP).assert(hasText("Tap tempo")).assertHasClickAction()
        compose.onNodeWithContentDescription("Beats in a bar").assertExists()
        for (meter in Meter.entries) {
            compose.onNodeWithTag(MetronomeTestTags.meter(meter))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.RadioButton))
        }
        // Heard by name, not as "6/8" (which TalkBack can read as a date).
        compose.onNodeWithTag(MetronomeTestTags.meter(Meter.SIX_EIGHT)).assertContentDescriptionEquals("Six-eight time")
        compose.onNodeWithTag(MetronomeTestTags.LIGHTS).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }

    @Test
    fun theVibrationSwitchIsSaved() {
        show()
        val vibrate = compose.onNodeWithTag(MetronomeTestTags.VIBRATE)
        vibrate.assert(hasText("Vibrate on the beat")).assertIsOff()
        vibrate.performClick()
        compose.waitForIdle()
        vibrate.assertIsOn()
        assertTrue(settings.current.metronomeVibrate)
    }

    @Test
    fun tapTempoFollowsTheTaps() {
        show()
        val tap = compose.onNodeWithTag(MetronomeTestTags.TAP)
        // TalkBack's double taps count with the phone's clock, so tap twice half a second apart.
        tap.performSemanticsAction(SemanticsActions.OnClick)
        Thread.sleep(500)
        tap.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        val bpm = settings.current.metronomeBpm
        assertTrue("about 120 BPM, was $bpm", bpm in 100..125)
    }

    @Test
    fun dutchAtTwiceTheTextSizeOnItsSideIsWhollyShown() {
        show(locale = "nl", fontScale = 2f, size = PHONE_LANDSCAPE)
        for (text in listOf("Tik tempo", "Start", "Trillen op de tel")) {
            assertWhollyShown(compose.onNodeWithText(text, useUnmergedTree = true), text)
        }
        compose.onNodeWithContentDescription("Langzamer").assertExists()
        compose.onNodeWithContentDescription("Tellen per maat").assertExists()
        compose.onNodeWithContentDescription("Driekwartsmaat").assertExists()
        dial.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "96 slagen per minuut, Andante"))
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 15L
        val PHONE = DpSize(393.dp, 793.dp)
        val PHONE_LANDSCAPE = DpSize(780.dp, 330.dp)
    }
}
