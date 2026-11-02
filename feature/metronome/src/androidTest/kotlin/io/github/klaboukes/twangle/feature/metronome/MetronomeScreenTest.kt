package io.github.klaboukes.twangle.feature.metronome

import android.Manifest
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.then
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
 * The metronome screen with a fake player and settings: it opens stopped at
 * the saved tempo and meter, Start / Stop, − and +, the meter picker, the dial (a touch on its ring,
 * none on its middle or deep in its gap, and TalkBack's slider), the row under the dial (Tap tempo ·
 * − · Start · + · options, Phase 62) and the options panel (the four sounds, a pick playing one bar
 * while stopped, the clicks between the beats and their mark, the vibration switch and its mark on the
 * status line, Listen, the speed trainer with its settings, status line and switch) work and are saved,
 * and the clicks stop when the screen leaves the composition. TalkBack hears every control by name.
 * [MetronomeStylesTest] covers the layout light and dark.
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

    private val dial get() = compose.onNodeWithTag(MetronomeTestTags.DIAL)
    private val optionsButton get() = compose.onNodeWithTag(MetronomeTestTags.OPTIONS)
    private val panel get() = compose.onNodeWithTag(MetronomeTestTags.OPTIONS_PANEL)

    private fun openPanel() {
        optionsButton.performScrollTo().performClick()
        compose.waitForIdle()
        panel.assertIsDisplayed()
    }

    /** Opens the panel and starts listening from its Listen row, which closes it. */
    private fun listen() {
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.LISTEN).performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { recorder.isRecording.value }
        compose.waitForIdle()
        panel.assertDoesNotExist()
    }
    private val start get() = compose.onNodeWithTag(MetronomeTestTags.START)

    @Test
    fun opensStoppedAtTheSavedTempoAndMeter() {
        show()
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("96"))
        compose.onNodeWithText("Andante", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(MetronomeTestTags.meter(Meter.THREE_FOUR)).assertIsSelected()
        compose.onNodeWithTag(MetronomeTestTags.meter(Meter.FOUR_FOUR)).assertIsNotSelected()
        start.assertContentDescriptionEquals("Start")
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
    fun aTouchDeepInTheDialsEmptyGapLeavesItAlone() {
        show()
        // Straight down near the bottom edge, where Start stood before Phase 62: it would jump to 30 or 250.
        dial.performTouchInput {
            down(center.copy(y = center.y + width * 0.4f))
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("96"))
    }

    @Test
    fun talkBackHearsTheDialAsASliderThatStepsOneBpm() {
        show()
        dial.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Tempo")))
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
        compose.onNodeWithTag(MetronomeTestTags.TAP).assertContentDescriptionEquals("Tap tempo").assertHasClickAction()
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
    }

    @Test
    fun theVibrationSwitchIsInTheOptionsPanelAndItsMarkOnTheStatusLine() {
        show()
        compose.onNodeWithTag(MetronomeTestTags.MARKS).assertDoesNotExist()
        openPanel()
        val vibrate = compose.onNodeWithTag(MetronomeTestTags.VIBRATE)
        vibrate.assert(hasText("Vibrate on the beat"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
        vibrate.performClick()
        compose.waitForIdle()
        vibrate.assertIsOn()
        assertTrue(settings.current.metronomeVibrate)
        // The panel stays open; the status line now shows the vibration mark, heard by its name.
        panel.assertIsDisplayed()
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.MARKS).assertIsDisplayed().assertContentDescriptionEquals("Vibrate on the beat")
        compose.onNodeWithTag(MetronomeTestTags.VIBRATE_MARK, useUnmergedTree = true).assertExists()
        // Off again, the mark goes.
        openPanel()
        vibrate.performClick()
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.MARKS).assertDoesNotExist()
    }

    @Test
    fun theOptionsButtonOpensThePanelWithTheFourSoundsVibrationAndListen() {
        show()
        panel.assertDoesNotExist()
        openPanel()
        panel.assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Options"))
        compose.onNodeWithContentDescription("Sound").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        for ((sound, name) in listOf(ClickSound.WOOD_BLOCK to "Wood block", ClickSound.COWBELL to "Cowbell", ClickSound.CLAVES to "Claves", ClickSound.RIM_CLICK to "Rim click")) {
            compose.onNodeWithTag(MetronomeTestTags.sound(sound)).assertContentDescriptionEquals(name)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .assertIsDisplayed()
        }
        compose.onNodeWithTag(MetronomeTestTags.sound(ClickSound.WOOD_BLOCK)).assertIsSelected()
        compose.onNodeWithTag(MetronomeTestTags.sound(ClickSound.COWBELL)).assertIsNotSelected()
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
        // Sound, Between the beats, Vibrate on the beat, then Listen, one under the other, with steady sizes.
        val sounds = compose.onNodeWithTag(MetronomeTestTags.sound(ClickSound.WOOD_BLOCK)).getBoundsInRoot()
        val between = Subdivision.entries.map { compose.onNodeWithTag(MetronomeTestTags.subdivision(it)).getBoundsInRoot() }
        val vibrate = compose.onNodeWithTag(MetronomeTestTags.VIBRATE).getBoundsInRoot()
        val listen = compose.onNodeWithTag(MetronomeTestTags.LISTEN).assert(hasText("Listen")).assertHasClickAction().getBoundsInRoot()
        assertTrue("the sounds $sounds over the note values ${between[0]}", sounds.bottom <= between[0].top)
        assertTrue("the note values ${between[0]} over the switch $vibrate", between[0].bottom <= vibrate.top)
        assertTrue("the switch $vibrate over Listen $listen", vibrate.bottom <= listen.top)
        for (bounds in between) {
            assertEquals("each note value as wide as a sound", sounds.width.value, bounds.width.value, 0.5f)
            assertEquals("each note value as tall as a sound", sounds.height.value, bounds.height.value, 0.5f)
        }
    }

    @Test
    fun stoppedANoteValuePlaysOneBarIsSavedAndShowsOnTheStatusLine() {
        show()
        compose.onNodeWithTag(MetronomeTestTags.MARKS).assertDoesNotExist()
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
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.MARKS).assertIsDisplayed().assertContentDescriptionEquals("Triplets between the beats")
        compose.onNodeWithTag(MetronomeTestTags.SUBDIVISION_MARK, useUnmergedTree = true).assertExists()
        // None again: the mark goes.
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.subdivision(Subdivision.NONE)).performClick()
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.MARKS).assertDoesNotExist()
        assertEquals(Subdivision.NONE, settings.current.metronomeSubdivision)
    }

    @Test
    fun playingANoteValueIsHeardFromTheNextBeatAndItsMarkComesBeforeVibration() {
        show()
        start.performClick()
        compose.waitForIdle()
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.VIBRATE).performClick()
        compose.onNodeWithTag(MetronomeTestTags.subdivision(Subdivision.FOUR)).performClick()
        compose.waitForIdle()
        assertEquals("no bar of its own while playing", 0, player.previewCount)
        assertEquals(Subdivision.FOUR, player.subdivision)
        assertTrue(player.isPlaying.value)
        assertEquals(1, player.startCount)
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.MARKS).assertContentDescriptionEquals("Sixteenths between the beats, Vibrate on the beat")
        val note = compose.onNodeWithTag(MetronomeTestTags.SUBDIVISION_MARK, useUnmergedTree = true).getBoundsInRoot()
        val buzz = compose.onNodeWithTag(MetronomeTestTags.VIBRATE_MARK, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("the note value $note before vibration $buzz", note.right <= buzz.left)
        start.performClick()
    }

    @Test
    fun sixEightDimsTripletsAndSixteenthsAndKeepsTheChoiceForFourFour() {
        runBlocking { settings.setMetronomeSubdivision(Subdivision.THREE) }
        show(subdivision = Subdivision.THREE)
        compose.onNodeWithTag(MetronomeTestTags.MARKS).assertContentDescriptionEquals("Triplets between the beats")
        compose.onNodeWithTag(MetronomeTestTags.meter(Meter.SIX_EIGHT)).performClick()
        compose.waitForIdle()
        // 6/8 plays triplets as none: no mark, none shown picked, triplets and sixteenths dimmed in place.
        compose.onNodeWithTag(MetronomeTestTags.MARKS).assertDoesNotExist()
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
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        compose.waitForIdle()
        // Back in 4/4 the triplets come back.
        compose.onNodeWithTag(MetronomeTestTags.meter(Meter.FOUR_FOUR)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.MARKS).assertContentDescriptionEquals("Triplets between the beats")
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.subdivision(Subdivision.THREE)).assertIsSelected().assertIsEnabled()
        compose.onNodeWithTag(MetronomeTestTags.subdivision(Subdivision.TWO)).assertContentDescriptionEquals("Eighths")
    }

    @Test
    fun stoppedAPickPlaysOneBarAndIsSavedAndThePanelStaysOpen() {
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
        // Still open for another try; closing it needs no stop, the bar ends by itself.
        panel.assertIsDisplayed()
        player.finishPreview()
        compose.onNodeWithTag(MetronomeTestTags.sound(ClickSound.RIM_CLICK)).performClick()
        compose.waitForIdle()
        assertEquals(2, player.previewCount)
        assertEquals(ClickSound.RIM_CLICK, settings.current.metronomeClickSound)
        val stops = player.stopCount
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        compose.waitForIdle()
        panel.assertDoesNotExist()
        assertEquals("closing the panel stops nothing", stops, player.stopCount)
    }

    @Test
    fun playingAPickOnlySwitchesTheSound() {
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
        // On the dial's ring, straight up (140 BPM if it reached the dial), where the panel isn't.
        dial.performTouchInput { click(Offset(centerX, height / 6f)) }
        compose.waitForIdle()
        panel.assertDoesNotExist()
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("96"))
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
    fun theRowReadsTapTempoSlowerStartFasterOptions() {
        show()
        val dialBounds = dial.getBoundsInRoot()
        val row = listOf(MetronomeTestTags.TAP, MetronomeTestTags.SLOWER, MetronomeTestTags.START, MetronomeTestTags.FASTER, MetronomeTestTags.OPTIONS)
            .map { it to compose.onNodeWithTag(it).getBoundsInRoot() }
        for ((first, second) in row.zipWithNext()) {
            assertTrue("${first.first} ${first.second}, then ${second.first} ${second.second}", first.second.right <= second.second.left)
        }
        val middle = row.first().second.let { (it.top + it.bottom) / 2 }
        for ((tag, bounds) in row) {
            assertEquals("$tag on the row's line", middle.value, ((bounds.top + bounds.bottom) / 2).value, 0.5f)
            assertTrue("$tag $bounds under the dial $dialBounds", bounds.top >= dialBounds.bottom)
        }
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
        assertDutchRowWhollyShown("on its side")
        compose.onNodeWithContentDescription("Langzamer").assertExists()
        compose.onNodeWithContentDescription("Tellen per maat").assertExists()
        compose.onNodeWithContentDescription("Driekwartsmaat").assertExists()
        dial.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "96 slagen per minuut, Andante"))
    }

    @Test
    fun dutchAtTwiceTheTextSizeUprightIsWhollyShown() {
        show(locale = "nl", fontScale = 2f)
        assertDutchRowWhollyShown("upright")
    }

    /** The row's buttons, each by its Dutch TalkBack name, wholly shown. */
    private fun assertDutchRowWhollyShown(case: String) {
        for (name in listOf("Tempo tikken", "Langzamer", "Start", "Sneller", "Opties")) {
            assertWhollyShown(compose.onNodeWithContentDescription(name), "$case $name")
        }
    }

    @Test
    fun dutchAtTwiceTheTextSizeUprightTheOptionsPanelIsWhollyShown() = assertDutchOptionsPanel(PHONE)

    @Test
    fun dutchAtTwiceTheTextSizeOnItsSideTheOptionsPanelIsWhollyShown() = assertDutchOptionsPanel(PHONE_LANDSCAPE)

    /**
     * The options panel in Dutch at 2x text on a phone of [size]: its headings, every sound and note
     * value, the switch and Listen wholly shown (scrolled to, where the panel scrolls).
     */
    private fun assertDutchOptionsPanel(size: DpSize) {
        show(locale = "nl", fontScale = 2f, size = size)
        openPanel()
        val case = "${size.width} x ${size.height}"
        assertWhollyShown(compose.onNodeWithText("Geluid", useUnmergedTree = true).performScrollTo(), "$case Geluid")
        for ((sound, name) in listOf(ClickSound.WOOD_BLOCK to "Woodblock", ClickSound.COWBELL to "Koebel", ClickSound.CLAVES to "Claves", ClickSound.RIM_CLICK to "Rimclick")) {
            val segment = compose.onNodeWithTag(MetronomeTestTags.sound(sound)).performScrollTo()
            segment.assertContentDescriptionEquals(name)
            assertWhollyShown(segment, "$case $name")
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
        assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.VIBRATE).performScrollTo().assert(hasText("Trillen op de tel")), "$case Trillen op de tel")
        assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.LISTEN).performScrollTo().assert(hasText("Luisteren")), "$case Luisteren")
        val row = compose.onNodeWithTag(MetronomeTestTags.TRAINER).performScrollTo().assert(hasText("Tempotrainer"))
        assertWhollyShown(row, "$case Tempotrainer")
        assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.TRAINER_SWITCH), "$case its switch")
        // Its settings, in the panel's place, every control wholly shown.
        row.performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_SETTINGS).assertIsDisplayed()
        for (name in listOf("Terug naar de opties", "Lager begintempo", "Hoger begintempo", "Lager doel", "Hoger doel")) {
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
        // Back goes to the rows first, then closes the panel.
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_SETTINGS).assertDoesNotExist()
        compose.onNodeWithTag(MetronomeTestTags.LISTEN).assertExists()
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        compose.waitForIdle()
        panel.assertDoesNotExist()
    }

    @Test
    fun dutchAtTwiceTheTextSizeTheClimbingTrainersLineIsWhollyShown() {
        show(locale = "nl", fontScale = 2f, trainer = SpeedTrainer(80, 120, 10, 8), trainerOn = true)
        start.performClick()
        compose.waitForIdle()
        player.beat(0)
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_TEXT, useUnmergedTree = true).assert(hasText("80 → 120 · +10 over 8 maten"))
        assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.TRAINER_TEXT, useUnmergedTree = true), "the trainer's words")
        start.performClick()
    }

    @Test
    fun turningTheTrainerOnTheFirstTimeOpensItsSettingsFilledInFromTheTempo() {
        show()
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER).performScrollTo().assert(hasText("Speed trainer"))
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_SWITCH).assertContentDescriptionEquals("Speed trainer").assertIsOff().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_SETTINGS).assertIsDisplayed()
        compose.onNodeWithTag(MetronomeTestTags.LISTEN).assertDoesNotExist()
        panel.assertIsDisplayed()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_SWITCH).assertIsOn()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_START, useUnmergedTree = true).assert(hasText("96"))
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_GOAL, useUnmergedTree = true).assert(hasText("116"))
        compose.onNodeWithTag(MetronomeTestTags.trainerStep(5)).assertIsSelected().assertContentDescriptionEquals("+5 BPM")
        compose.onNodeWithTag(MetronomeTestTags.trainerBars(4)).assertIsSelected().assertContentDescriptionEquals("4 bars")
        compose.onNodeWithContentDescription("Start tempo, 96 BPM").assertExists()
        assertEquals(SpeedTrainer(96, 116, 5, 4), settings.current.metronomeTrainer)
        assertTrue(settings.current.metronomeTrainerOn)
        // The back arrow returns to the rows, the trainer still on.
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_BACK).assertContentDescriptionEquals("Back to the options").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.LISTEN).assertIsDisplayed()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_SETTINGS).assertDoesNotExist()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_SWITCH).assertIsOn()
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.MARKS).assertContentDescriptionEquals("Speed trainer, from 96 to 116 BPM, +5 every 4 bars")
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_TEXT, useUnmergedTree = true).assert(hasText("96 → 116 · +5 every 4 bars"))
    }

    @Test
    fun theTrainersSettingsAreSavedAndKeepTheGoalAboveTheStart() {
        runBlocking { settings.setMetronomeTrainer(SpeedTrainer(80, 90, 5, 4)) }
        show(trainer = SpeedTrainer(80, 90, 5, 4), trainerOn = true)
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("80"))
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER).performScrollTo().performClick()
        compose.waitForIdle()
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
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("85"))
        assertFalse("nothing plays", player.isPlaying.value)
    }

    @Test
    fun climbingTheLineAndDialFollowEachDownbeatAndATapOnTheLineOpensTheSettings() {
        show(trainer = SpeedTrainer(80, 90, 5, 1), trainerOn = true)
        start.performClick()
        compose.waitForIdle()
        assertEquals(80, player.bpm)
        val text = compose.onNodeWithTag(MetronomeTestTags.TRAINER_TEXT, useUnmergedTree = true)
        player.beat(0)
        compose.waitForIdle()
        text.assert(hasText("80 → 90 · +5 in 1 bar"))
        compose.onNodeWithTag(MetronomeTestTags.MARKS).assertContentDescriptionEquals("Speed trainer, 80 BPM, climbing to 90, +5 in 1 bar")
        for (beat in 1L..3L) player.beat(beat)
        compose.waitForIdle()
        text.assert(hasText("85 → 90 · +5 in 1 bar"))
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("85"))
        for (beat in 4L..6L) player.beat(beat)
        compose.waitForIdle()
        text.assert(hasText("At the goal · 90 BPM"))
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("90"))
        // Not a live region: TalkBack reads it when touched, never over the clicks.
        compose.onNodeWithTag(MetronomeTestTags.MARKS).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        compose.onNodeWithTag(MetronomeTestTags.MARKS).assertHasClickAction().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_SETTINGS).assertIsDisplayed()
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.LISTEN).assertIsDisplayed()
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        compose.waitForIdle()
        panel.assertDoesNotExist()
        // Stop: back at the start.
        start.performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("80"))
        text.assert(hasText("80 → 90 · +5 every bar"))
    }

    @Test
    fun aTempoByHandEndsTheTrainer() {
        show(trainer = SpeedTrainer(80, 90, 5, 1), trainerOn = true)
        start.performClick()
        compose.waitForIdle()
        for (beat in 0L..3L) player.beat(beat)
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.FASTER).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_MARK, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("86"))
        assertEquals(86, player.bpm)
        assertFalse(settings.current.metronomeTrainerOn)
        start.performClick()
    }

    @Test
    fun withItsSwitchOffThereIsNoTrainer() {
        show(tools = ToolSwitches.ALL_ON.copy(speedTrainer = false), trainer = SpeedTrainer(80, 90, 5, 1), trainerOn = true)
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("96"))
        compose.onNodeWithTag(MetronomeTestTags.TRAINER_MARK, useUnmergedTree = true).assertDoesNotExist()
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.LISTEN).assertExists()
        compose.onNodeWithTag(MetronomeTestTags.TRAINER).assertDoesNotExist()
        compose.onNodeWithText("Speed trainer", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theStepButtonsStandEitherSideOfStartAndStepTheTempo() {
        show()
        val startBounds = start.getBoundsInRoot()
        val slower = compose.onNodeWithTag(MetronomeTestTags.SLOWER)
        val faster = compose.onNodeWithTag(MetronomeTestTags.FASTER)
        assertTrue("slower left of Start", slower.getBoundsInRoot().right <= startBounds.left)
        assertTrue("faster right of Start", faster.getBoundsInRoot().left >= startBounds.right)
        slower.performClick()
        slower.performClick()
        faster.performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("95"))
        assertEquals(95, settings.current.metronomeBpm)
    }

    @Test
    fun theStepButtonsKeepSlowerOnTheLeftInArabic() {
        show(locale = "ar")
        val slower = compose.onNodeWithTag(MetronomeTestTags.SLOWER).getBoundsInRoot()
        val faster = compose.onNodeWithTag(MetronomeTestTags.FASTER).getBoundsInRoot()
        assertTrue("slower $slower left of faster $faster, as on the dial", slower.right <= faster.left)
        // The row's ends do mirror: Tap tempo at the start (the right), options at the end.
        assertTrue("Tap tempo at the start", compose.onNodeWithTag(MetronomeTestTags.TAP).getBoundsInRoot().left >= faster.right)
        assertTrue("options at the end", optionsButton.getBoundsInRoot().right <= slower.left)
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
        // Stop on the status line, heard as "Stop listening".
        val stop = compose.onNodeWithTag(MetronomeTestTags.LINE_ACTION)
        stop.assert(hasText("Stop")).assertContentDescriptionEquals("Stop listening")
        // The panel's Listen row says so too while it listens.
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.LISTEN).assert(hasText("Stop listening"))
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        compose.waitForIdle()

        stop.performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { !recorder.isRecording.value }
        start.assertIsEnabled()
        assertTrue("the clicks come back", player.isPlaying.value)
        openPanel()
        compose.onNodeWithTag(MetronomeTestTags.LISTEN).assert(hasText("Listen"))
    }

    @Test
    fun anotherControlEndsListening() {
        show()
        listen()
        compose.onNodeWithTag(MetronomeTestTags.FASTER).performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { !recorder.isRecording.value }
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("97"))
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
        compose.onNodeWithTag(MetronomeTestTags.BALLOON).assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Heard"))
        compose.onNodeWithTag(MetronomeTestTags.BALLOON_BPM).assert(hasText("84", substring = true)).assert(hasText("Andante", substring = true))
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("96"))
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
        compose.onNodeWithTag(MetronomeTestTags.LINE_ACTION).assert(hasText("Stop")).performClick()
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
