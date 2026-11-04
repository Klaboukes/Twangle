package io.github.klaboukes.twangle.feature.metronome

import android.Manifest
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.rule.GrantPermissionRule
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeMetronomePlayer
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.SpeedTrainer
import io.github.klaboukes.twangle.core.dsp.metronome.Subdivision
import io.github.klaboukes.twangle.core.dsp.speech.SpokenReadingThrottle
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.testing.Navigation
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/**
 * The metronome screen (2.0, Phase 75: the tempo is the screen) with a fake player and settings: it opens
 * stopped at the saved tempo and meter; Start / Stop, − and +, the ruler (a drag, a flick, a tap on a
 * mark, and TalkBack's slider), the meter as a segmented choice, Tap the beat, the Vibrate and Listen
 * buttons under the beat bars, the options panel under its button in the top end corner (the sounds,
 * one bar playing while stopped; the clicks between the beats, the speed trainer's row), and the speed
 * trainer's switch, words, settings and card (only while on) work and are saved, and the clicks stop
 * when the screen leaves the composition. TalkBack hears every control by name. [MetronomeStylesTest]
 * covers the layout light and dark.
 */
class MetronomeScreenTest {

    private val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val player = FakeMetronomePlayer()
    private val settings = FakeSettingsRepository()
    private val recorder = FakeAudioRecorder.silence()
    private val shown = mutableStateOf(true)

    @get:Rule
    val microphone: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private fun show(
        locale: String? = null,
        fontScale: Float = 1f,
        size: DpSize = PHONE,
        subdivision: Subdivision = Subdivision.NONE,
        tools: ToolSwitches = ToolSwitches.ALL_ON,
        trainer: SpeedTrainer? = null,
        trainerOn: Boolean = false,
    ) {
        compose.setContent {
            PhoneScreen(size, SystemBars.tab(onItsSide = size.width > size.height, Navigation.BUTTONS), fontScale, locale) {
                TwangleTheme(darkTheme = false, animate = false) {
                    backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                    if (shown.value) {
                        MetronomeScreen(
                            player,
                            settings,
                            recorder,
                            tools,
                            savedBpm = 96,
                            savedMeter = Meter.THREE_FOUR,
                            savedVibrate = false,
                            savedSubdivision = subdivision,
                            savedTrainer = trainer,
                            savedTrainerOn = trainerOn,
                        )
                    }
                }
            }
        }
    }

    private var backDispatcher: OnBackPressedDispatcher? = null

    private val ruler get() = compose.onNodeWithTag(MetronomeTestTags.RULER)
    private val figures get() = compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true)
    private val optionsButton get() = compose.onNodeWithTag(MetronomeTestTags.OPTIONS)
    private val panel get() = compose.onNodeWithTag(MetronomeTestTags.OPTIONS_PANEL)
    private val trainerPanel get() = compose.onNodeWithTag(MetronomeTestTags.TRAINER_PANEL)
    private val start get() = compose.onNodeWithTag(MetronomeTestTags.START)

    private fun back() {
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        compose.waitForIdle()
    }

    private fun openPanel() {
        optionsButton.performScrollTo().performClick()
        compose.waitForIdle()
        panel.assertIsDisplayed()
    }

    private val listenButton get() = compose.onNodeWithTag(MetronomeTestTags.LISTEN)
    private val vibrateButton get() = compose.onNodeWithTag(MetronomeTestTags.VIBRATE)

    /** Starts listening from the Listen button under the beat bars. */
    private fun listen() {
        listenButton.performScrollTo().performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { recorder.isRecording.value }
        compose.waitForIdle()
    }

    /** Opens the speed trainer's settings from its row in the options panel, which they take the place of. */
    private fun openTrainer() {
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_OPTION).performClick()
        compose.waitForIdle()
        panel.assertDoesNotExist()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_SETTINGS).assertIsDisplayed()
    }

    @Test
    fun opensStoppedAtTheSavedTempoAndMeter() {
        show()
        figures.assert(hasText("96"))
        compose.onNodeWithTag(MetronomeTestTags.TEMPO_WORDS, useUnmergedTree = true).assert(hasText("beats per minute · Andante"))
        compose.onNodeWithTag(MetronomeTestTags.meter(Meter.THREE_FOUR)).assertIsSelected()
        compose.onNodeWithTag(MetronomeTestTags.meter(Meter.FOUR_FOUR)).assertIsNotSelected()
        start.assertContentDescriptionEquals("Start")
        compose.onNodeWithText("Start", useUnmergedTree = true).assertIsDisplayed()
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
        start.assertContentDescriptionEquals("Stop")
        compose.onNodeWithText("Stop", useUnmergedTree = true).assertIsDisplayed()

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
        figures.assert(hasText("97"))
        compose.onNodeWithTag(MetronomeTestTags.TEMPO_WORDS, useUnmergedTree = true).assert(hasText("beats per minute · Andante"))

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
    fun aDragOnTheRulerSlidesTheTempoAndSavesItOnLift() {
        show()
        // 60 dp to the left, held still before the finger lifts: about 6 BPM faster (less the touch slop), no flick.
        ruler.performTouchInput {
            down(center)
            repeat(6) {
                moveBy(Offset(-10.dp.toPx(), 0f))
            }
            advanceEventTime(400)
            moveBy(Offset(0f, 0f))
            advanceEventTime(400)
            up()
        }
        compose.waitForIdle()
        val bpm = settings.current.metronomeBpm
        assertTrue("about 6 BPM faster, was $bpm", bpm in 100..102)
        figures.assert(hasText("$bpm"))
        // And back to the right, slower.
        ruler.performTouchInput {
            down(center)
            repeat(12) {
                moveBy(Offset(10.dp.toPx(), 0f))
            }
            advanceEventTime(400)
            moveBy(Offset(0f, 0f))
            advanceEventTime(400)
            up()
        }
        compose.waitForIdle()
        val slower = settings.current.metronomeBpm
        assertTrue("about 12 BPM slower than $bpm, was $slower", slower in bpm - 12..bpm - 10)
        figures.assert(hasText("$slower"))
    }

    @Test
    fun aFlickCarriesTheTempoOnAndStopsOnAWholeTempo() {
        show()
        ruler.performTouchInput { swipeLeft(startX = right - 8f, endX = left + 8f, durationMillis = 120) }
        compose.waitForIdle()
        val bpm = settings.current.metronomeBpm
        assertTrue("the flick carried it well past the drag's 30-odd BPM: $bpm", bpm > 140)
        assertTrue("within 30-250: $bpm", bpm <= 250)
        figures.assert(hasText("$bpm"))
    }

    @Test
    fun aTapOnAMarkJumpsToThatTempo() {
        show()
        // Three marks right of the cursor: 99 BPM.
        ruler.performTouchInput { click(Offset(centerX + 30.dp.toPx(), centerY)) }
        compose.waitForIdle()
        figures.assert(hasText("99"))
        assertEquals(99, settings.current.metronomeBpm)
    }

    @Test
    fun talkBackHearsTheRulerAsASliderThatStepsOneBpm() {
        show()
        ruler.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Tempo")))
        ruler.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "96 beats per minute, Andante"))
        val range = ruler.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)!!
        assertEquals(96f, range.current)
        assertEquals(30f..250f, range.range)
        assertEquals("one step per BPM", 219, range.steps)
        ruler.performSemanticsAction(SemanticsActions.SetProgress) { it(121f) }
        compose.waitForIdle()
        ruler.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "121 beats per minute, Allegro"))
        assertEquals(121, settings.current.metronomeBpm)
        // The figures and their words are the ruler's to say.
        figures.assert(hasText("121"))
        compose.onNodeWithText("121").assertDoesNotExist()
    }

    @Test
    fun talkBackHearsEveryControlByName() {
        show()
        compose.onNodeWithContentDescription("Slower").assertHasClickAction()
        compose.onNodeWithContentDescription("Faster").assertHasClickAction()
        compose.onNodeWithTag(MetronomeTestTags.TAP).assertContentDescriptionEquals("Tap the beat").assertHasClickAction()
        optionsButton.assertContentDescriptionEquals("Options")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
            .assertHasClickAction()
        compose.onNodeWithContentDescription("Beats in a bar").assertExists()
        for (meter in Meter.entries) {
            compose.onNodeWithTag(MetronomeTestTags.meter(meter))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
        // Heard by name, not as "6/8" (which TalkBack can read as a date).
        compose.onNodeWithTag(MetronomeTestTags.meter(Meter.SIX_EIGHT)).assertContentDescriptionEquals("Six-eight time")
        compose.onNodeWithTag(MetronomeTestTags.LIGHTS).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        vibrateButton.assertContentDescriptionEquals("Vibrate on the beat")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
        listenButton.assertContentDescriptionEquals("Listen")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction()
        // The sounds, in the options panel.
        compose.onNodeWithContentDescription("Sound").assertDoesNotExist()
        openPanel()
        compose.onNodeWithContentDescription("Sound").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        for ((sound, name) in listOf(ClickSound.WOOD_BLOCK to "Wood block", ClickSound.COWBELL to "Cowbell", ClickSound.CLAVES to "Claves", ClickSound.RIM_CLICK to "Rim click")) {
            compose.onNodeWithTag(MetronomeTestTags.sound(sound)).assertContentDescriptionEquals(name)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .assertIsDisplayed()
        }
        compose.onNodeWithTag(MetronomeTestTags.sound(ClickSound.WOOD_BLOCK)).assertIsSelected()
    }

    @Test
    fun theScreenReadsTopToBottomAsTheMockUp() {
        show()
        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = tag == MetronomeTestTags.BPM).performScrollTo().getBoundsInRoot()
        val options = bounds(MetronomeTestTags.OPTIONS)
        val slower = bounds(MetronomeTestTags.SLOWER)
        val number = bounds(MetronomeTestTags.BPM)
        val faster = bounds(MetronomeTestTags.FASTER)
        val ruler = bounds(MetronomeTestTags.RULER)
        val lights = bounds(MetronomeTestTags.LIGHTS)
        val vibrate = bounds(MetronomeTestTags.VIBRATE)
        val line = bounds(MetronomeTestTags.LINE)
        val listen = bounds(MetronomeTestTags.LISTEN)
        val meter = bounds(MetronomeTestTags.meter(Meter.FOUR_FOUR))
        val tap = bounds(MetronomeTestTags.TAP)
        val start = bounds(MetronomeTestTags.START)
        assertTrue("options $options in the top end corner", options.right >= PHONE.width - 24.dp && options.top <= SystemBars.STATUS_BAR + 16.dp)
        assertTrue("− $slower, the tempo $number, + $faster", slower.right <= number.left && number.right <= faster.left)
        val middle = (number.top + number.bottom) / 2
        for ((name, rect) in listOf("−" to slower, "+" to faster)) {
            assertEquals("$name level with the tempo", middle.value, ((rect.top + rect.bottom) / 2).value, 1f)
        }
        assertTrue("the tempo $number under the options button $options", number.top >= options.bottom)
        assertTrue("the ruler $ruler under the tempo", ruler.top >= number.bottom)
        assertTrue("the lights $lights under the ruler", lights.top >= ruler.bottom)
        // Under the bars one row: Vibrate at the start, Listen's line, Listen at the end, all level.
        for ((name, rect) in listOf("Vibrate" to vibrate, "the line" to line, "Listen" to listen)) {
            assertTrue("$name $rect under the lights $lights", rect.top >= lights.bottom)
            assertTrue("the meter $meter under $name $rect", meter.top >= rect.bottom)
        }
        val row = (vibrate.top + vibrate.bottom) / 2
        assertEquals("Listen level with Vibrate", row.value, ((listen.top + listen.bottom) / 2).value, 1f)
        assertEquals("the line level with Vibrate", row.value, ((line.top + line.bottom) / 2).value, 1f)
        assertTrue("Vibrate $vibrate, the line $line, Listen $listen", vibrate.right <= line.left && line.right <= listen.left)
        // The buttons' outlines in line with the top row's ⋯ (and the gear, at the margin).
        assertEquals("Listen's end in line with the ⋯'s", options.right.value, listen.right.value, 1f)
        assertEquals("Vibrate at the margin, as the gear", 16f, vibrate.left.value, 1f)
        assertEquals("− at the margin, as the gear", 16f, slower.left.value, 1f)
        assertEquals("+'s end in line with the ⋯'s", options.right.value, faster.right.value, 1f)
        assertTrue("the meter $meter under the lights", meter.top >= lights.bottom)
        assertTrue("Tap the beat $tap under the meter", tap.top >= meter.bottom)
        // The meter right over the big buttons; the spare room goes to the beat bars, never over the buttons.
        assertTrue("the meter $meter right over Tap the beat $tap", tap.top - meter.bottom <= 24.dp)
        assertTrue("the beat bars $lights at least 72 dp tall", lights.height >= 71.5.dp)
        assertTrue("Tap the beat $tap beside Start $start", tap.right <= start.left)
        assertEquals("Tap the beat level with Start", tap.top.value, start.top.value, 0.5f)
        assertEquals("Tap the beat and Start the same width", tap.width.value, start.width.value, 0.5f)
        // Every round button has the ⋯'s face: −, +, Vibrate and Listen.
        for ((name, rect) in listOf("−" to slower, "+" to faster, "Vibrate" to vibrate, "Listen" to listen)) {
            assertEquals("$name's face as wide as the ⋯'s", options.width.value, rect.width.value, 0.5f)
            assertEquals("$name's face as tall as the ⋯'s", options.height.value, rect.height.value, 0.5f)
        }
        // The sound and the trainer are behind the options button; the trainer's card shows only while it is on.
        compose.onNodeWithTag(MetronomeTestTags.sound(ClickSound.COWBELL)).assertDoesNotExist()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER).assertDoesNotExist()
    }

    @Test
    fun theVibrateButtonUnderTheBarsTogglesVibrationAndIsSaved() {
        show()
        vibrateButton.assertIsOff()
        vibrateButton.performClick()
        compose.waitForIdle()
        vibrateButton.assertIsOn()
        assertTrue(settings.current.metronomeVibrate)
        // Nothing else changed for it: no panel opened, and the line under the bars says nothing.
        panel.assertDoesNotExist()
        compose.onNodeWithTag(MetronomeTestTags.LINE).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        vibrateButton.performClick()
        compose.waitForIdle()
        vibrateButton.assertIsOff()
        assertFalse(settings.current.metronomeVibrate)
    }

    @Test
    fun theOptionsButtonOpensThePanelUnderItWithItsThreeRows() {
        show()
        panel.assertDoesNotExist()
        openPanel()
        panel.assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Options"))
        compose.onNodeWithContentDescription("Between the beats").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        for ((subdivision, name) in listOf(
            Subdivision.NONE to "None",
            Subdivision.TWO to "Eighths",
            Subdivision.THREE to "Triplets",
            Subdivision.FOUR to "Sixteenths",
        )) {
            compose.onNodeWithTag(MetronomeTestTags.subdivision(subdivision)).assertContentDescriptionEquals(name)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .assertIsEnabled()
                .assertIsDisplayed()
        }
        compose.onNodeWithTag(MetronomeTestTags.subdivision(Subdivision.NONE)).assertIsSelected()
        // The sound, Between the beats and the speed trainer, one under the other, ending the panel; the
        // panel under its button.
        val sound = compose.onNodeWithTag(MetronomeTestTags.sound(ClickSound.WOOD_BLOCK)).assertIsDisplayed().getBoundsInRoot()
        val between = compose.onNodeWithTag(MetronomeTestTags.subdivision(Subdivision.NONE)).getBoundsInRoot()
        val trainer = compose.onNodeWithTag(MetronomeTestTags.TRAINER_OPTION).assert(hasText("Speed trainer")).assertIsDisplayed().getBoundsInRoot()
        assertTrue("the sounds $sound over the note values $between", sound.bottom <= between.top)
        assertTrue("the note values $between over the trainer $trainer", between.bottom <= trainer.top)
        val button = optionsButton.getBoundsInRoot()
        val shownPanel = panel.getBoundsInRoot()
        assertTrue("the trainer $trainer the panel's last row $shownPanel", shownPanel.bottom - trainer.bottom <= 24.dp)
        // Vibrate and Listen are not in it: they are the buttons under the beat bars.
        for (tag in listOf(MetronomeTestTags.VIBRATE, MetronomeTestTags.LISTEN)) {
            compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(MetronomeTestTags.OPTIONS_PANEL))).assertDoesNotExist()
            compose.onNodeWithTag(tag).assertExists()
        }
        assertTrue("the panel $shownPanel under the button $button", shownPanel.top >= button.bottom)
        assertEquals("its end in line with the button's", button.right.value, shownPanel.right.value, 1f)
        // The trainer's settings are not open in it.
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_SETTINGS).assertDoesNotExist()
    }

    @Test
    fun stoppedANoteValuePlaysOneBarAndIsSaved() {
        show()
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.subdivision(Subdivision.THREE)).performClick()
        compose.waitForIdle()
        assertEquals("one bar of it", 1, player.previewCount)
        assertEquals(Subdivision.THREE, player.subdivision)
        assertFalse("a preview is not playing", player.isPlaying.value)
        assertEquals(Subdivision.THREE, settings.current.metronomeSubdivision)
        compose.onNodeWithTag(MetronomeTestTags.subdivision(Subdivision.THREE)).assertIsSelected()
        compose.onNodeWithTag(MetronomeTestTags.subdivision(Subdivision.NONE)).assertIsNotSelected()
        panel.assertIsDisplayed()
        // None again.
        compose.onNodeWithTag(MetronomeTestTags.subdivision(Subdivision.NONE)).performClick()
        compose.waitForIdle()
        back()
        assertEquals(Subdivision.NONE, settings.current.metronomeSubdivision)
    }

    @Test
    fun playingANoteValueIsHeardFromTheNextBeat() {
        show()
        start.performClick()
        compose.waitForIdle()
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.subdivision(Subdivision.FOUR)).performClick()
        compose.waitForIdle()
        assertEquals("no bar of its own while playing", 0, player.previewCount)
        assertEquals(Subdivision.FOUR, player.subdivision)
        assertTrue(player.isPlaying.value)
        assertEquals(1, player.startCount)
        back()
        start.performClick()
    }

    @Test
    fun sixEightDimsTripletsAndSixteenthsAndKeepsTheChoiceForFourFour() {
        runBlocking { settings.setMetronomeSubdivision(Subdivision.THREE) }
        show(subdivision = Subdivision.THREE)
        compose.onNodeWithTag(MetronomeTestTags.meter(Meter.SIX_EIGHT)).performClick()
        compose.waitForIdle()
        // 6/8 plays triplets as none: none shown picked, triplets and sixteenths dimmed in place.
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.subdivision(Subdivision.NONE)).assertIsSelected().assertIsEnabled()
        compose.onNodeWithTag(MetronomeTestTags.subdivision(Subdivision.TWO)).assertIsEnabled()
            .assertContentDescriptionEquals("Sixteenths")
        for (dimmed in listOf(Subdivision.THREE, Subdivision.FOUR)) {
            compose.onNodeWithTag(MetronomeTestTags.subdivision(dimmed)).assertIsNotEnabled().assertIsNotSelected().assertIsDisplayed()
        }
        compose.onNodeWithTag(MetronomeTestTags.subdivision(Subdivision.FOUR)).performClick()
        compose.waitForIdle()
        assertEquals("a dimmed one takes no tap", 0, player.previewCount)
        assertEquals("the choice is kept", Subdivision.THREE, settings.current.metronomeSubdivision)
        back()
        // Back in 4/4 the triplets come back.
        compose.onNodeWithTag(MetronomeTestTags.meter(Meter.FOUR_FOUR)).performClick()
        compose.waitForIdle()
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.subdivision(Subdivision.THREE)).assertIsSelected().assertIsEnabled()
        compose.onNodeWithTag(MetronomeTestTags.subdivision(Subdivision.TWO)).assertContentDescriptionEquals("Eighths")
    }

    @Test
    fun stoppedASoundPlaysOneBarAndIsSaved() {
        show()
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.sound(ClickSound.COWBELL)).performClick()
        compose.waitForIdle()
        assertEquals("one bar of it", 1, player.previewCount)
        assertEquals(ClickSound.COWBELL, player.sound)
        assertEquals(96, player.bpm)
        assertEquals(Meter.THREE_FOUR, player.meter)
        assertTrue(player.isPreviewing.value)
        assertFalse("a preview is not playing", player.isPlaying.value)
        assertEquals(ClickSound.COWBELL, settings.current.metronomeClickSound)
        compose.onNodeWithTag(MetronomeTestTags.sound(ClickSound.COWBELL)).assertIsSelected()
        compose.onNodeWithTag(MetronomeTestTags.sound(ClickSound.WOOD_BLOCK)).assertIsNotSelected()
        player.finishPreview()
        compose.onNodeWithTag(MetronomeTestTags.sound(ClickSound.RIM_CLICK)).performClick()
        compose.waitForIdle()
        assertEquals(2, player.previewCount)
        assertEquals(ClickSound.RIM_CLICK, settings.current.metronomeClickSound)
        // The panel stays open for the next try.
        panel.assertIsDisplayed()
    }

    @Test
    fun playingASoundOnlySwitchesIt() {
        show()
        start.performClick()
        compose.waitForIdle()
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.sound(ClickSound.CLAVES)).performClick()
        compose.waitForIdle()
        assertEquals("no bar of its own while playing", 0, player.previewCount)
        assertEquals(ClickSound.CLAVES, player.sound)
        assertTrue(player.isPlaying.value)
        assertEquals(ClickSound.CLAVES, settings.current.metronomeClickSound)
    }

    @Test
    fun aTapBesideThePanelClosesItAndDoesNothingElse() {
        show()
        openPanel()
        // On the ruler's start, where the panel isn't (a jump to about 80 BPM if it reached the ruler).
        ruler.performTouchInput { click(Offset(left + 16.dp.toPx(), centerY)) }
        compose.waitForIdle()
        panel.assertDoesNotExist()
        figures.assert(hasText("96"))
    }

    @Test
    fun leavingTheScreenEndsAPreview() {
        show()
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.sound(ClickSound.CLAVES)).performClick()
        compose.waitForIdle()
        assertTrue(player.isPreviewing.value)
        shown.value = false
        compose.waitForIdle()
        assertFalse("no sound once the screen is gone", player.isPreviewing.value)
    }

    @Test
    fun tapTheBeatFollowsTheTaps() {
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
        assertDutchWhollyShown("on its side")
        compose.onNodeWithContentDescription("Tellen per maat").assertExists()
        compose.onNodeWithContentDescription("Driekwartsmaat").assertExists()
        ruler.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "96 slagen per minuut, Andante"))
    }

    @Test
    fun dutchAtTwiceTheTextSizeUprightIsWhollyShown() {
        show(locale = "nl", fontScale = 2f)
        assertDutchWhollyShown("upright")
        compose.onNodeWithTag(MetronomeTestTags.TEMPO_WORDS, useUnmergedTree = true).assert(hasText("slagen per minuut · Andante"))
    }

    /** The screen's controls, each by its Dutch TalkBack name or its own words, wholly shown (scrolled to). */
    private fun assertDutchWhollyShown(case: String) {
        for (name in listOf("Tik de tel", "Langzamer", "Start", "Sneller", "Opties", "Trillen op de tel", "Luisteren")) {
            assertWhollyShown(compose.onNodeWithContentDescription(name).performScrollTo(), "$case $name")
        }
        for (tag in listOf(MetronomeTestTags.RULER, MetronomeTestTags.BPM, MetronomeTestTags.TEMPO_WORDS)) {
            assertWhollyShown(compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo(), "$case $tag")
        }
        for (meter in Meter.entries) assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.meter(meter)).performScrollTo(), "$case $meter")
    }

    @Test
    fun dutchAtTwiceTheTextSizeUprightThePanelsAreWhollyShown() = assertDutchPanels(PHONE)

    @Test
    fun dutchAtTwiceTheTextSizeOnItsSideThePanelsAreWhollyShown() = assertDutchPanels(PHONE_LANDSCAPE)

    /**
     * The options panel and the speed trainer's in Dutch at 2x text on a phone of [size]: their headings,
     * every sound and note value, the trainer's row and every trainer control wholly shown (scrolled to,
     * where the panel scrolls).
     */
    private fun assertDutchPanels(size: DpSize) {
        show(locale = "nl", fontScale = 2f, size = size)
        openPanel()
        val case = "${size.width} x ${size.height}"
        assertWhollyShown(compose.onNodeWithText("Geluid", useUnmergedTree = true).performScrollTo(), "$case Geluid")
        for (name in listOf("Woodblock", "Koebel", "Claves", "Rimclick")) {
            assertWhollyShown(compose.onNodeWithContentDescription(name).performScrollTo(), "$case $name")
        }
        assertWhollyShown(compose.onNodeWithText("Tussen de tellen", useUnmergedTree = true).performScrollTo(), "$case Tussen de tellen")
        for ((subdivision, name) in listOf(
            Subdivision.NONE to "Geen",
            Subdivision.TWO to "Achtsten",
            Subdivision.THREE to "Triolen",
            Subdivision.FOUR to "Zestienden",
        )) {
            val segment = compose.onNodeWithTag(MetronomeTestTags.subdivision(subdivision)).performScrollTo()
            segment.assertContentDescriptionEquals(name)
            assertWhollyShown(segment, "$case $name")
        }
        assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.TRAINER_OPTION).performScrollTo().assert(hasText("Tempotrainer")), "$case Tempotrainer")
        assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.TRAINER_OPTION_SWITCH).performScrollTo(), "$case the trainer's switch")
        assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.TRAINER_OPTION_TEXT, useUnmergedTree = true).performScrollTo(), "$case the trainer's words")
        back()
        panel.assertDoesNotExist()
        // The trainer's settings, from its row, every control wholly shown.
        openTrainer()
        trainerPanel.assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Tempotrainer"))
        for (name in listOf("Lager begintempo", "Hoger begintempo", "Lager doel", "Hoger doel")) {
            assertWhollyShown(compose.onNodeWithContentDescription(name).performScrollTo(), "$case $name")
        }
        for (tag in listOf(MetronomeTestTags.TRAINER_START, MetronomeTestTags.TRAINER_GOAL)) {
            assertWhollyShown(compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo(), "$case $tag")
        }
        for (words in listOf("Begintempo", "Doel", "Stap", "Maten")) {
            assertWhollyShown(compose.onNodeWithText(words, useUnmergedTree = true).performScrollTo(), "$case $words")
        }
        for (step in SpeedTrainer.STEPS) {
            assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.trainerStep(step)).performScrollTo(), "$case +$step")
        }
        for (bars in SpeedTrainer.BAR_CHOICES) {
            assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.trainerBars(bars)).performScrollTo(), "$case $bars")
        }
        back()
        trainerPanel.assertDoesNotExist()
    }

    @Test
    fun dutchAtTwiceTheTextSizeTheClimbingTrainersWordsAreWhollyShown() {
        show(locale = "nl", fontScale = 2f, trainer = SpeedTrainer(80, 120, 10, 8), trainerOn = true)
        start.performScrollTo().performClick()
        compose.waitForIdle()
        player.beat(0)
        compose.waitForIdle()
        val words = compose.onNodeWithTag(MetronomeTestTags.TRAINER_TEXT, useUnmergedTree = true)
        words.assert(hasText("80 → 120 · +10 over 8 maten"))
        assertWhollyShown(words.performScrollTo(), "the trainer's words")
        start.performScrollTo().performClick()
    }

    @Test
    fun turningTheTrainerOnTheFirstTimeOpensItsSettingsFilledInFromTheTempo() {
        show()
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_OPTION).assert(hasText("Speed trainer"))
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_OPTION_SWITCH).assertContentDescriptionEquals("Speed trainer").assertIsOff().performClick()
        compose.waitForIdle()
        // The settings take the options panel's place, under the options button, their ends in line.
        panel.assertDoesNotExist()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_SETTINGS).assertIsDisplayed()
        trainerPanel.assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Speed trainer"))
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_SETTINGS_SWITCH).assertContentDescriptionEquals("Speed trainer").assertIsOn()
        val button = optionsButton.getBoundsInRoot()
        val shownPanel = trainerPanel.getBoundsInRoot()
        assertTrue("the panel $shownPanel under the button $button", shownPanel.top >= button.bottom)
        assertEquals("its end in line with the button's", button.right.value, shownPanel.right.value, 1f)
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_START, useUnmergedTree = true).assert(hasText("96"))
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_GOAL, useUnmergedTree = true).assert(hasText("116"))
        compose.onNodeWithTag(MetronomeTestTags.trainerStep(5)).assertIsSelected().assertContentDescriptionEquals("+5 BPM")
        compose.onNodeWithTag(MetronomeTestTags.trainerBars(4)).assertIsSelected().assertContentDescriptionEquals("4 bars")
        compose.onNodeWithContentDescription("Start tempo, 96 BPM").assertExists()
        assertEquals(SpeedTrainer(96, 116, 5, 4), settings.current.metronomeTrainer)
        assertTrue(settings.current.metronomeTrainerOn)
        // Back closes it, the trainer still on, its card on the screen at the foot saying what it will do.
        back()
        trainerPanel.assertDoesNotExist()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_SWITCH).performScrollTo().assertIsOn()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER).assertContentDescriptionEquals("Speed trainer, from 96 to 116 BPM, +5 every 4 bars")
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_TEXT, useUnmergedTree = true).assert(hasText("96 → 116 · +5 every 4 bars"))
        val card = compose.onNodeWithTag(MetronomeTestTags.TRAINER).getBoundsInRoot()
        val lightsBounds = compose.onNodeWithTag(MetronomeTestTags.LIGHTS).getBoundsInRoot()
        val meterBounds = compose.onNodeWithTag(MetronomeTestTags.meter(Meter.FOUR_FOUR)).getBoundsInRoot()
        assertTrue("the card $card under the beat bars $lightsBounds", card.top >= lightsBounds.bottom)
        assertTrue("the card $card over the meter $meterBounds", card.bottom <= meterBounds.top)
        // Off on its card: the card goes.
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_SWITCH).performClick()
        compose.waitForIdle()
        assertFalse(settings.current.metronomeTrainerOn)
        compose.onNodeWithTag(MetronomeTestTags.TRAINER).assertDoesNotExist()
        // On again in the panel: its plan is saved, so no settings open and the panel stays.
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_OPTION_SWITCH).assertIsOff().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_OPTION_SWITCH).assertIsOn()
        panel.assertIsDisplayed()
        trainerPanel.assertDoesNotExist()
        back()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER).assertExists()
    }

    @Test
    fun theTrainersSettingsAreSavedAndKeepTheGoalAboveTheStart() {
        runBlocking { settings.setMetronomeTrainer(SpeedTrainer(80, 90, 5, 4)) }
        show(trainer = SpeedTrainer(80, 90, 5, 4), trainerOn = true)
        figures.assert(hasText("80"))
        openTrainer()
        val startFaster = compose.onNodeWithContentDescription("Higher start tempo")
        startFaster.performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_START, useUnmergedTree = true).assert(hasText("85"))
        startFaster.assertIsNotEnabled()
        compose.onNodeWithContentDescription("Lower goal").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Higher goal").assertIsEnabled().performClick()
        compose.onNodeWithTag(MetronomeTestTags.trainerStep(10)).performClick()
        compose.onNodeWithTag(MetronomeTestTags.trainerBars(2)).performClick()
        compose.waitForIdle()
        assertEquals(SpeedTrainer(85, 95, 10, 2), settings.current.metronomeTrainer)
        compose.onNodeWithTag(MetronomeTestTags.trainerBars(2)).assertIsSelected()
        compose.onNodeWithTag(MetronomeTestTags.trainerBars(4)).assertIsNotSelected()
        figures.assert(hasText("85"))
        assertFalse("nothing plays", player.isPlaying.value)
    }

    @Test
    fun climbingTheRowAndTempoFollowEachDownbeatAndATapOnTheRowOpensTheSettings() {
        show(trainer = SpeedTrainer(80, 90, 5, 1), trainerOn = true)
        start.performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(80, player.bpm)
        val text = compose.onNodeWithTag(MetronomeTestTags.TRAINER_TEXT, useUnmergedTree = true)
        player.beat(0)
        compose.waitForIdle()
        text.assert(hasText("80 → 90 · +5 in 1 bar"))
        compose.onNodeWithTag(MetronomeTestTags.TRAINER).assertContentDescriptionEquals("Speed trainer, 80 BPM, climbing to 90, +5 in 1 bar")
        for (beat in 1L..3L) player.beat(beat)
        compose.waitForIdle()
        text.assert(hasText("85 → 90 · +5 in 1 bar"))
        figures.assert(hasText("85"))
        for (beat in 4L..6L) player.beat(beat)
        compose.waitForIdle()
        text.assert(hasText("At the goal · 90 BPM"))
        figures.assert(hasText("90"))
        // Not a live region: TalkBack reads it when touched, never over the clicks.
        compose.onNodeWithTag(MetronomeTestTags.TRAINER).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        // A tap on the card opens the settings over it.
        compose.onNodeWithTag(MetronomeTestTags.TRAINER).performScrollTo().performClick()
        compose.waitForIdle()
        val card = compose.onNodeWithTag(MetronomeTestTags.TRAINER).getBoundsInRoot()
        val shownPanel = trainerPanel.assertIsDisplayed().getBoundsInRoot()
        assertTrue("the panel $shownPanel over the card $card", shownPanel.bottom <= card.top)
        back()
        trainerPanel.assertDoesNotExist()
        // Stop: back at the start.
        start.performScrollTo().performClick()
        compose.waitForIdle()
        figures.assert(hasText("80"))
        text.assert(hasText("80 → 90 · +5 every bar"))
    }

    @Test
    fun aTempoByHandEndsTheTrainer() {
        show(trainer = SpeedTrainer(80, 90, 5, 1), trainerOn = true)
        start.performScrollTo().performClick()
        compose.waitForIdle()
        for (beat in 0L..3L) player.beat(beat)
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.FASTER).performScrollTo().performClick()
        compose.waitForIdle()
        // Off, so its card goes.
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_SWITCH).assertDoesNotExist()
        figures.assert(hasText("86"))
        assertEquals(86, player.bpm)
        assertFalse(settings.current.metronomeTrainerOn)
        start.performScrollTo().performClick()
    }

    @Test
    fun turningTheTrainerOnOrOffMovesNothing() {
        show(trainer = SpeedTrainer(96, 116, 5, 4))
        val tags = listOf(
            MetronomeTestTags.BPM, MetronomeTestTags.RULER, MetronomeTestTags.LIGHTS, MetronomeTestTags.VIBRATE, MetronomeTestTags.LINE,
            MetronomeTestTags.LISTEN, MetronomeTestTags.meter(Meter.FOUR_FOUR), MetronomeTestTags.TAP, MetronomeTestTags.START,
        )
        fun places() = tags.map { compose.onNodeWithTag(it, useUnmergedTree = it == MetronomeTestTags.BPM).getBoundsInRoot() }
        val off = places()
        openPanel()
        // Its plan is saved, so turning it on opens no settings.
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_OPTION_SWITCH).performClick()
        compose.waitForIdle()
        back()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER).assertIsDisplayed()
        val on = places()
        for (i in tags.indices) {
            assertEquals("${tags[i]} top", off[i].top.value, on[i].top.value, 1f)
            assertEquals("${tags[i]} bottom", off[i].bottom.value, on[i].bottom.value, 1f)
        }
    }

    @Test
    fun withItsSwitchOffThereIsNoTrainer() {
        show(tools = ToolSwitches.ALL_ON.copy(speedTrainer = false), trainer = SpeedTrainer(80, 90, 5, 1), trainerOn = true)
        figures.assert(hasText("96"))
        compose.onNodeWithTag(MetronomeTestTags.TRAINER).assertDoesNotExist()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_SWITCH).assertDoesNotExist()
        compose.onNodeWithText("Speed trainer", useUnmergedTree = true).assertDoesNotExist()
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.subdivision(Subdivision.NONE)).assertExists()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_OPTION).assertDoesNotExist()
    }

    @Test
    fun theStepButtonsStandEitherSideOfTheTempoAndStepIt() {
        show()
        val slower = compose.onNodeWithTag(MetronomeTestTags.SLOWER)
        val faster = compose.onNodeWithTag(MetronomeTestTags.FASTER)
        slower.performClick()
        slower.performClick()
        faster.performClick()
        compose.waitForIdle()
        figures.assert(hasText("95"))
        assertEquals(95, settings.current.metronomeBpm)
        ruler.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "95 beats per minute, Andante"))
    }

    @Test
    fun theStepButtonsTakeTapsBesideTheirSmallerFaces() {
        show()
        // 5 dp past the 44 dp face's outer side: outside a 48 dp touch area, inside the 56 dp one.
        compose.onNodeWithTag(MetronomeTestTags.SLOWER).performTouchInput { click(Offset(-5.dp.toPx(), centerY)) }
        compose.onNodeWithTag(MetronomeTestTags.FASTER).performTouchInput { click(Offset(width + 5.dp.toPx(), centerY)) }
        compose.onNodeWithTag(MetronomeTestTags.FASTER).performTouchInput { click(Offset(width + 5.dp.toPx(), centerY)) }
        compose.waitForIdle()
        figures.assert(hasText("97"))
    }

    @Test
    fun theStepButtonsKeepSlowerOnTheLeftInArabic() {
        show(locale = "ar")
        val slower = compose.onNodeWithTag(MetronomeTestTags.SLOWER).getBoundsInRoot()
        val faster = compose.onNodeWithTag(MetronomeTestTags.FASTER).getBoundsInRoot()
        assertTrue("slower $slower left of faster $faster, as on the ruler", slower.right <= faster.left)
        // The top row does mirror: the options button at the end, on the left.
        assertTrue("options at the end", optionsButton.getBoundsInRoot().left <= 24.dp)
        // So do Tap the beat and Start: Tap the beat at the start, on the right.
        val tap = compose.onNodeWithTag(MetronomeTestTags.TAP).performScrollTo().getBoundsInRoot()
        assertTrue("Tap the beat at the start", tap.left >= start.getBoundsInRoot().right)
    }

    @Test
    fun listenHoldsTheMicrophoneOnlyWhileListeningAndRestsStartStop() {
        show()
        start.performClick()
        compose.waitForIdle()
        assertFalse(recorder.isRecording.value)

        listen()
        assertFalse("the clicks pause while listening", player.isPlaying.value)
        start.assertIsNotEnabled()
        lineSaysSoon("Listening…")
        // The Listen button is Stop while it listens; the line has no Stop of its own.
        listenButton.assertContentDescriptionEquals("Stop listening")
        compose.onNodeWithTag(MetronomeTestTags.LINE_ACTION).assertDoesNotExist()

        listenButton.performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { !recorder.isRecording.value }
        start.assertIsEnabled()
        assertTrue("the clicks come back", player.isPlaying.value)
        listenButton.assertContentDescriptionEquals("Listen")
    }

    @Test
    fun anotherControlEndsListening() {
        show()
        listen()
        compose.onNodeWithTag(MetronomeTestTags.FASTER).performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { !recorder.isRecording.value }
        figures.assert(hasText("97"))
        assertFalse("it was stopped, so it stays stopped", player.isPlaying.value)
    }

    @Test
    fun leavingTheScreenWhileListeningReleasesTheMicrophone() {
        show()
        listen()
        shown.value = false
        compose.waitUntil(TIMEOUT_MILLIS) { !recorder.isRecording.value }
    }

    @Test
    fun theSuggestionChangesNothingUntilUse() {
        val listen = mutableStateOf(ListenUi(ListenPhase.FOUND, wasPlaying = false, found = 84, half = 42, double = 168))
        var used: TempoPick? = null
        var notNow = 0
        compose.setContent {
            PhoneScreen(PHONE, SystemBars.tab(onItsSide = false)) {
                TwangleTheme(darkTheme = false, animate = false) {
                    MetronomeContent(
                        bpm = 96, meter = Meter.FOUR_FOUR, playing = false, vibrate = false,
                        onBpmChange = {}, onBpmChangeFinished = {}, onStep = {}, onTap = {}, onMeterChange = {},
                        onToggle = {}, onVibrateChange = {},
                        listen = listen.value,
                        onUse = { pick, _ -> used = pick },
                        onNotNow = { notNow++ },
                    )
                }
            }
        }
        val balloon = compose.onNodeWithTag(MetronomeTestTags.BALLOON).assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Heard"))
        // Over the Listen button under the beat bars, pointing at it, its end over the button.
        val shownBalloon = balloon.getBoundsInRoot()
        val button = listenButton.getBoundsInRoot()
        assertTrue("the balloon $shownBalloon over the Listen button $button", shownBalloon.bottom <= button.top && button.top - shownBalloon.bottom <= 16.dp)
        assertTrue("the balloon $shownBalloon reaching over the button $button", shownBalloon.left <= button.left && shownBalloon.right >= button.right - 1.dp)
        compose.onNodeWithTag(MetronomeTestTags.BALLOON_BPM).assert(hasText("84", substring = true)).assert(hasText("Andante", substring = true))
        figures.assert(hasText("96"))
        val asHeard = compose.onNodeWithTag(MetronomeTestTags.pick(TempoPick.AS_HEARD))
        asHeard.assertContentDescriptionEquals("As heard").assertIsSelected()
        compose.onNodeWithContentDescription("Speed").assertExists()
        compose.onNodeWithTag(MetronomeTestTags.pick(TempoPick.HALF)).assertContentDescriptionEquals("Half")
        val double = compose.onNodeWithTag(MetronomeTestTags.pick(TempoPick.DOUBLE))
        double.assertContentDescriptionEquals("Double").performClick()
        compose.waitForIdle()
        double.assertIsSelected()
        compose.onNodeWithTag(MetronomeTestTags.BALLOON_BPM).assert(hasText("168", substring = true)).assert(hasText("Presto", substring = true))
        compose.onNodeWithTag(MetronomeTestTags.NOT_NOW).assert(hasText("Not now")).performClick()
        assertEquals(1, notNow)
        compose.onNodeWithTag(MetronomeTestTags.USE).assert(hasText("Use")).performClick()
        assertEquals(TempoPick.DOUBLE, used)

        // Only the choices within 30-250 BPM.
        listen.value = ListenUi(ListenPhase.FOUND, found = 160, half = 80, double = 0)
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.pick(TempoPick.DOUBLE)).assertDoesNotExist()
        listen.value = ListenUi()
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.BALLOON).assertDoesNotExist()
    }

    @Test
    fun theStatusLineSaysWhatListenHeard() {
        val listen = mutableStateOf(ListenUi(ListenPhase.LISTENING, startedAtNanos = 1L))
        var again = 0
        var stops = 0
        compose.setContent {
            PhoneScreen(PHONE, SystemBars.tab(onItsSide = false)) {
                TwangleTheme(darkTheme = false, animate = false) {
                    MetronomeContent(
                        bpm = 96, meter = Meter.FOUR_FOUR, playing = false, vibrate = false,
                        onBpmChange = {}, onBpmChangeFinished = {}, onStep = {}, onTap = {}, onMeterChange = {},
                        onToggle = {}, onVibrateChange = {},
                        listen = listen.value,
                        onListen = { stops++ },
                        onTryAgain = { again++ },
                    )
                }
            }
        }
        val line = compose.onNodeWithTag(MetronomeTestTags.LINE)
        line.assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        lineSaysSoon("Listening…")
        listen.value = listen.value.copy(estimate = 96)
        lineSaysSoon("Listening… 96 BPM")
        // A tempo still settling is passed on to TalkBack at most every 2 s, not with every change.
        listen.value = listen.value.copy(estimate = 97)
        compose.waitForIdle()
        line.assert(hasContentDescription("Listening… 96 BPM"))
        listen.value = listen.value.copy(estimate = 98)
        lineSaysSoon("Listening… 98 BPM")
        compose.onNodeWithTag(MetronomeTestTags.LINE_ACTION).assertDoesNotExist()
        compose.onNodeWithTag(MetronomeTestTags.LISTEN).assertContentDescriptionEquals("Stop listening").performClick()
        assertEquals(1, stops)
        listen.value = ListenUi(ListenPhase.NO_BEAT)
        compose.waitForIdle()
        line.assert(hasText("No steady beat heard", substring = true))
        compose.onNodeWithTag(MetronomeTestTags.LINE_ACTION).assert(hasText("Try again")).performClick()
        assertEquals(1, again)
        listen.value = ListenUi(ListenPhase.MIC_BUSY)
        compose.waitForIdle()
        line.assert(hasText("Microphone is busy", substring = true))
        compose.onNodeWithTag(MetronomeTestTags.LINE_ACTION).assert(hasText("Try again")).performClick()
        assertEquals(2, again)
        listen.value = ListenUi(ListenPhase.MIC_OFF_SETTINGS)
        compose.waitForIdle()
        line.assert(hasText("Microphone is off", substring = true))
        compose.onNodeWithTag(MetronomeTestTags.LINE_ACTION).assert(hasText("Open settings"))
    }

    /** TalkBack is given [words] for the status line within the pacing's longest wait. */
    private fun lineSaysSoon(words: String) {
        compose.waitUntil(SpokenReadingThrottle.DEFAULT_REPEAT_MILLIS + 1_000) {
            compose.onAllNodes(hasTestTag(MetronomeTestTags.LINE) and hasContentDescription(words)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 15L
        const val TIMEOUT_MILLIS = 3_000L
        val PHONE = DpSize(393.dp, 793.dp)
        val PHONE_LANDSCAPE = DpSize(780.dp, 330.dp)
    }
}
