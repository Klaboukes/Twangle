package io.github.klaboukes.twangle.feature.metronome

import android.Manifest
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import io.github.klaboukes.twangle.core.designsystem.SpokenReadingThrottle
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.Navigation
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
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
    private val recorder = FakeAudioRecorder.silence()
    private val shown = mutableStateOf(true)

    @get:Rule
    val microphone: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private fun show(locale: String? = null, fontScale: Float = 1f, size: DpSize = PHONE) {
        compose.setContent {
            PhoneScreen(size, SystemBars.tab(onItsSide = size.width > size.height, Navigation.BUTTONS), fontScale, locale) {
                TwangleTheme(style = VisualStyle.DEFAULT, darkTheme = false, animate = false) {
                    if (shown.value) {
                        MetronomeScreen(player, settings, recorder, ToolSwitches.ALL_ON, savedBpm = 96, savedMeter = Meter.THREE_FOUR, savedVibrate = false)
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
        compose.onNodeWithTag(MetronomeTestTags.TAP).assertContentDescriptionEquals("Tap tempo").assertHasClickAction()
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
        vibrate.assertContentDescriptionEquals("Vibrate on the beat").assertIsOff()
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
        for (name in listOf("Tempo tikken", "Luisteren", "Start", "Trillen op de tel")) {
            assertWhollyShown(compose.onNodeWithContentDescription(name), name)
        }
        compose.onNodeWithContentDescription("Langzamer").assertExists()
        compose.onNodeWithContentDescription("Tellen per maat").assertExists()
        compose.onNodeWithContentDescription("Driekwartsmaat").assertExists()
        dial.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "96 slagen per minuut, Andante"))
        assertDutchNamesUnderTheButtons("on its side")
    }

    @Test
    fun dutchAtTwiceTheTextSizeUprightShowsTheNamesUnderTheButtons() {
        show(locale = "nl", fontScale = 2f)
        assertDutchNamesUnderTheButtons("upright")
    }

    /** "Tempo tikken", "Luisteren" and "Trillen" under the buttons, each on one line and wholly shown. */
    private fun assertDutchNamesUnderTheButtons(case: String) {
        for ((tag, name) in listOf(MetronomeTestTags.TAP_LABEL to "Tempo tikken", MetronomeTestTags.LISTEN_LABEL to "Luisteren", MetronomeTestTags.VIBRATE_LABEL to "Trillen")) {
            val label = compose.onNodeWithTag(tag)
            label.assertTextEquals(name)
            assertWhollyShown(label, "$case $name")
            val lines = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(lines) }
            assertEquals("$case $name on one line", 1, lines.single().lineCount)
        }
    }

    @Test
    fun theButtonsShowTheirNamesButTalkBackHearsOnlyTheButtons() {
        show()
        for ((tag, name) in listOf(MetronomeTestTags.TAP_LABEL to "Tap tempo", MetronomeTestTags.LISTEN_LABEL to "Listen", MetronomeTestTags.VIBRATE_LABEL to "Vibrate")) {
            compose.onNodeWithTag(tag).assertTextEquals(name).assertIsDisplayed()
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        }
        // Listen keeps its word while listening; its button says "Stop listening".
        compose.onNodeWithTag(MetronomeTestTags.LISTEN).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.LISTEN_LABEL).assertTextEquals("Listen")
        compose.onNodeWithTag(MetronomeTestTags.LISTEN).assertContentDescriptionEquals("Stop listening").performClick()
        compose.waitForIdle()
    }

    @Test
    fun theStepButtonsSitBesideTheTempoAndStartInTheOpeningAndNeitherTurnsTheDial() {
        show()
        val dialBounds = dial.getBoundsInRoot()
        val figures = compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).getBoundsInRoot()
        val slower = compose.onNodeWithTag(MetronomeTestTags.SLOWER)
        val faster = compose.onNodeWithTag(MetronomeTestTags.FASTER)
        fun level(it: androidx.compose.ui.unit.DpRect) = it.top < figures.bottom && it.bottom > figures.top
        assertTrue("slower left of the tempo, level with it", slower.getBoundsInRoot().let { it.left > dialBounds.left && it.right <= figures.left + 4.dp && level(it) })
        assertTrue("faster right of the tempo, level with it", faster.getBoundsInRoot().let { it.right < dialBounds.right && it.left >= figures.right - 4.dp && level(it) })
        assertTrue("start in the dial's opening", start.getBoundsInRoot().let { it.top > figures.bottom && it.bottom <= dialBounds.bottom })
        slower.performClick()
        slower.performClick()
        faster.performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("95"))
        assertEquals(95, settings.current.metronomeBpm)
    }

    @Test
    fun listenHoldsTheMicrophoneOnlyWhileListeningAndRestsStartStop() {
        show()
        start.performClick()
        compose.waitForIdle()
        val listen = compose.onNodeWithTag(MetronomeTestTags.LISTEN)
        listen.assertContentDescriptionEquals("Listen").assertHasClickAction()
        assertFalse(recorder.isRecording.value)

        listen.performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { recorder.isRecording.value }
        listen.assertContentDescriptionEquals("Stop listening")
        assertFalse("the clicks pause while listening", player.isPlaying.value)
        start.assertIsNotEnabled()
        lineSaysSoon("Listening…")

        listen.performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { !recorder.isRecording.value }
        listen.assertContentDescriptionEquals("Listen")
        start.assertIsEnabled()
        assertTrue("the clicks come back", player.isPlaying.value)
    }

    @Test
    fun anotherControlEndsListening() {
        show()
        compose.onNodeWithTag(MetronomeTestTags.LISTEN).performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { recorder.isRecording.value }
        compose.onNodeWithTag(MetronomeTestTags.FASTER).performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { !recorder.isRecording.value }
        compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).assert(hasText("97"))
        assertFalse("it was stopped, so it stays stopped", player.isPlaying.value)
    }

    @Test
    fun leavingTheScreenWhileListeningReleasesTheMicrophone() {
        show()
        compose.onNodeWithTag(MetronomeTestTags.LISTEN).performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { recorder.isRecording.value }
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
                TwangleTheme(style = VisualStyle.DEFAULT, darkTheme = false, animate = false) {
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
        compose.setContent {
            PhoneScreen(PHONE, SystemBars.tab(onItsSide = false)) {
                TwangleTheme(style = VisualStyle.DEFAULT, darkTheme = false, animate = false) {
                    MetronomeContent(
                        bpm = 96, meter = Meter.FOUR_FOUR, playing = false, vibrate = false,
                        onBpmChange = {}, onBpmChangeFinished = {}, onStep = {}, onTap = {}, onMeterChange = {},
                        onToggle = {}, onVibrateChange = {},
                        listen = listen.value,
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
        listen.value = ListenUi(ListenPhase.NO_BEAT)
        compose.waitForIdle()
        line.assert(hasText("No steady beat heard", substring = true))
        compose.onNodeWithTag(MetronomeTestTags.LINE_ACTION).assert(hasText("Try again")).performClick()
        assertEquals(1, again)
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
