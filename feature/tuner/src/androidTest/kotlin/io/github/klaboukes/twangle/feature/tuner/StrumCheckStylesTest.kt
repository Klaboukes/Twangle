package io.github.klaboukes.twangle.feature.tuner

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.onPhoneScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * The strum check (2.0, Phase 74, the owner's pick of 2026-11-03: 1.1's columns): its button above the pegs at their
 * end (beside them on its side), and from its tap the panel in the lens's place, one column per string in the pegs'
 * order, no ruler over it and no marks over the pegs; "Strum all the open strings" until the strum, then "3 of 6 in
 * tune" in the word's place while no single string is heard. Its × and Back close it; without the button and a strum
 * the lens stays. TalkBack hears each column. At 2× text in Dutch the count and the panel are wholly shown. And the
 * real screen with a synthetic strum through a fake microphone.
 */
@RunWith(AndroidJUnit4::class)
class StrumCheckStylesTest {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()

    // Set these to change what the window shows without composing it again.
    private val shownState = mutableStateOf<TunerUiState>(LISTENING)
    private val shownHeard = mutableStateOf<HeardStrum>(HeardStrum.OpenStrings(strum(StrumPhase.DONE), 1))
    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownFontScale = mutableStateOf(1f)
    private val shownLocale = mutableStateOf("en")
    private val shownFocus = mutableStateOf(false)
    private val shownMode = mutableStateOf(TunerMode.AUTO)
    private val shownTools = mutableStateOf(listOf(TunerTool.INTONATION_CHECK, TunerTool.STRUM_CHECK))
    private val shownStrumCheck = mutableStateOf(false)
    private var closed = 0

    private fun show() {
        compose.setContent {
            // The area inside the bars: TunerScreen keeps TunerContent clear of them.
            PhoneScreen(shownSize.value, SystemBars.NONE, shownFontScale.value, shownLocale.value) {
                TwangleTheme(animate = false) {
                    TunerContent(
                        shownState.value,
                        shownMode.value,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = {},
                        onRetry = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        focusMode = shownFocus.value,
                        heard = shownHeard.value,
                        // The 12th-fret check offered too (its row is in the picker sheet since 2026-11-03).
                        tunerTools = shownTools.value,
                        onIntonationCheckChange = {},
                        strumCheck = shownStrumCheck.value,
                        onStrumCheckChange = { shownStrumCheck.value = it },
                        onStrumCheckClose = {
                            closed++
                            shownStrumCheck.value = false
                            shownHeard.value = HeardStrum.None
                        },
                        animationsOff = true,
                    )
                }
            }
        }
    }

    private val strings = listOf("E2", "A2", "D3", "G3", "B3", "E4")
    private fun column(index: Int) = compose.onNodeWithTag(TunerTestTags.strumString(index), useUnmergedTree = true)
    private fun peg(index: Int) = compose.onNodeWithText(strings[index])
    private val panel get() = compose.onNodeWithTag(TunerTestTags.STRUM_PANEL, useUnmergedTree = true)
    private val wordText get() = compose.onNodeWithTag(TunerTestTags.WORD_TEXT, useUnmergedTree = true)
    private fun strumButton() = compose.onNodeWithTag(TunerTestTags.STRUM_BUTTON, useUnmergedTree = true)
    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    /** Upright on a narrow phone and on the Xiaomi at 1.25×: the panel in the lens's place, no ruler, no marks over the pegs, each column over its peg's side, clear of the word. */
    @Test
    fun thePanelTakesTheLensPlaceItsColumnsOverThePegs() {
        show()
        for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE)) {
            shownSize.value = size
            shownFontScale.value = scale
            compose.waitForIdle()
            val case = "${size.width} x ${size.height} at ${scale}x"
            panel.assertIsDisplayed()
            assertEquals("$case: the lens", 0, count(TunerTestTags.GAUGE))
            assertEquals("$case: the ruler", 0, count(TunerTestTags.RULER))
            assertEquals("$case: a mark over a peg", 0, count(TunerTestTags.strumMark(0)))
            assertColumnsOverPegs(case)
            // The × above the panel, clear of every string's name, inside the window.
            val close = compose.onNodeWithTag(TunerTestTags.STRUM_CLOSE, useUnmergedTree = true).assertIsDisplayed().getBoundsInRoot()
            for (index in strings.indices) {
                if (close.overlaps(column(index).getBoundsInRoot()) && close.bottom > panel.getBoundsInRoot().top + SLACK) {
                    fail("$case: the × $close reaches into the panel's columns")
                }
            }
            assertWhollyShown(compose.onNodeWithTag(TunerTestTags.STRUM_CLOSE, useUnmergedTree = true), "$case: the ×")
            val word = compose.onNodeWithTag(TunerTestTags.WORD).getBoundsInRoot()
            if (panel.getBoundsInRoot().overlaps(word)) fail("$case: the panel overlaps the word $word")
        }
    }

    /** The button tapped, no strum yet: the panel waits, empty, and the word says what to do; TalkBack hears "E 2, not strummed yet". */
    @Test
    fun theButtonOpensThePanelWaitingForTheStrum() {
        shownHeard.value = HeardStrum.None
        show()
        assertEquals(1, count(TunerTestTags.GAUGE))
        strumButton().performClick()
        compose.waitForIdle()
        panel.assertIsDisplayed()
        assertEquals(context.getString(R.string.tuner_strum_waiting), wordText.text())
        val waiting = context.getString(R.string.tuner_strum_spoken_waiting, "")
        assertTrue(description(0).endsWith(waiting))
        // Tapped again before a strum: off, the lens back.
        strumButton().performClick()
        compose.waitForIdle()
        assertEquals(0, count(TunerTestTags.STRUM_PANEL))
        assertEquals(1, count(TunerTestTags.GAUGE))
    }

    /** "3 of 6 in tune" in the word's place while no string is heard; a string heard takes the word back, the panel staying; all in tune says so. */
    @Test
    fun theCountStandsInTheWordsPlace() {
        show()
        assertEquals(count(3, 6), wordText.text())
        shownState.value = DETECTED_A2
        compose.waitForIdle()
        assertEquals(context.getString(R.string.tuner_word_tighten), wordText.text())
        panel.assertIsDisplayed()
        shownState.value = LISTENING
        shownHeard.value = HeardStrum.OpenStrings(strum(StrumPhase.DONE, allInTune = true), 1)
        compose.waitForIdle()
        assertEquals(context.getString(R.string.tuner_strum_all_in_tune), wordText.text())
    }

    /** The panel's ×, then Back, close it: the lens comes back. */
    @Test
    fun theCloseButtonAndBackCloseThePanel() {
        show()
        compose.onNodeWithTag(TunerTestTags.STRUM_CLOSE, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(1, closed)
        assertEquals(0, count(TunerTestTags.STRUM_PANEL))
        assertEquals(1, count(TunerTestTags.GAUGE))
    }

    /** Nothing heard and the button off: the plain tuner, the lens and the ruler, and the pegs don't move when the panel comes. */
    @Test
    fun withoutAStrumTheLensStaysAndThePegsDontMove() {
        show()
        val withPanel = listOf(peg(0).getBoundsInRoot(), peg(5).getBoundsInRoot(), compose.onNodeWithTag(TunerTestTags.WORD).getBoundsInRoot().top)
        shownHeard.value = HeardStrum.None
        compose.waitForIdle()
        assertEquals(0, count(TunerTestTags.STRUM_PANEL))
        assertEquals(1, count(TunerTestTags.GAUGE))
        assertEquals(context.getString(R.string.tuner_hint_play_string), wordText.text())
        val without = listOf(peg(0).getBoundsInRoot(), peg(5).getBoundsInRoot(), compose.onNodeWithTag(TunerTestTags.WORD).getBoundsInRoot().top)
        assertEquals("the pegs moved", withPanel.take(2), without.take(2))
    }

    /** In Any note there are no pegs: no panel and no button (open strings are checked in guitar mode only). */
    @Test
    fun anyNoteHasNoPanel() {
        shownMode.value = TunerMode.CHROMATIC
        shownState.value = TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD)
        show()
        assertEquals(0, count(TunerTestTags.STRUM_PANEL))
        compose.onAllNodesWithTag(TunerTestTags.STRUM_BUTTON, useUnmergedTree = true).assertCountEquals(0)
    }

    /** On its side the panel stands as the 12th-fret check's does (two panes), wholly shown; in focus mode too, with the count. */
    @Test
    fun onItsSideAndInFocusMode() {
        shownSize.value = PHONE_LANDSCAPE
        show()
        panel.assertIsDisplayed()
        assertWhollyShown(panel, "on its side: the panel")
        assertEquals(count(3, 6), wordText.text())
        shownFocus.value = true
        compose.waitForIdle()
        panel.assertIsDisplayed()
        assertEquals(count(3, 6), wordText.text())
    }

    /** TalkBack hears each column: "A 2, 12 cents flat, Tighten", "B 3, not heard", "E 4, in tune, less sure". */
    @Test
    fun talkBackHearsEveryColumn() {
        show()
        assertTrue(description(1), description(1).contains(context.getString(R.string.tuner_word_tighten)))
        assertTrue(description(3), description(3).contains(context.getString(R.string.tuner_word_loosen)))
        assertTrue(description(4), description(4).endsWith(context.getString(R.string.tuner_strum_spoken_not_heard, "")))
        assertTrue(description(5), description(5).endsWith(context.getString(R.string.tuner_strum_spoken_less_sure, "")))
    }

    /** At 2× text in Dutch, upright and on its side: the count line and the panel are wholly shown. */
    @Test
    fun atTwiceTheTextSizeInDutchTheCountIsWhollyShown() {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        show()
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
            assertWhollyShown(wordText, "nl $size at 2x: the count")
            assertWhollyShown(panel, "nl $size at 2x: the panel")
        }
    }

    /**
     * The strum check's button: upright above the pegs at their end, standing over the last peg, clear of the panel; on
     * its side after the last peg, on the pegs' line. TalkBack hears a switch.
     */
    @Test
    fun theStrumButtonStandsAboveTheLastPeg() {
        show()
        // On its side with the panel the two panes stand the pegs as upright; without it the wide layout, the button beside them.
        for ((size, scale, withPanel) in listOf(
            Triple(PHONE_UPRIGHT, 1f, true),
            Triple(XIAOMI_UPRIGHT, OWNER_FONT_SCALE, true),
            Triple(PHONE_LANDSCAPE, 1f, true),
            Triple(PHONE_LANDSCAPE, 1f, false),
        )) {
            shownSize.value = size
            shownFontScale.value = scale
            shownHeard.value = if (withPanel) HeardStrum.OpenStrings(strum(StrumPhase.DONE), 1) else HeardStrum.None
            compose.waitForIdle()
            val case = "${size.width} x ${size.height} at ${scale}x${if (withPanel) ", the panel" else ""}"
            val button = strumButton().assertIsDisplayed().getBoundsInRoot()
            val last = peg(5).getBoundsInRoot()
            val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
            if (button.right > window.right + SLACK || button.left < window.left - SLACK) fail("$case: the button $button is outside the window $window")
            if (withPanel && button.overlaps(panel.getBoundsInRoot())) fail("$case: the button $button overlaps the panel")
            if (size.width < size.height || withPanel) {
                if (button.bottom > last.top + SLACK) fail("$case: the button $button reaches into the last peg $last")
                val buttonCentre = (button.left + button.right).value / 2f
                val pegCentre = (last.left + last.right).value / 2f
                if (abs(buttonCentre - pegCentre) > PEG_FACE_SLACK) fail("$case: the button $button doesn't stand over the last peg $last")
            } else {
                if (button.left < last.right - SLACK) fail("$case: the button $button isn't after the last peg $last")
                if (button.bottom < last.top || button.top > last.bottom) fail("$case: the button $button isn't on the pegs' line $last")
            }
        }
        strumButton().assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        strumButton().performClick()
        compose.waitForIdle()
        strumButton().assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    /** Without the strum check (the 12-string, a build without it) no button, and the pegs and the lens don't move. */
    @Test
    fun withoutTheStrumCheckNoButtonAndNothingMoves() {
        shownHeard.value = HeardStrum.None
        show()
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            shownTools.value = listOf(TunerTool.INTONATION_CHECK, TunerTool.STRUM_CHECK)
            compose.waitForIdle()
            val parts = listOf("lens" to compose.onNodeWithTag(TunerTestTags.GAUGE), "first peg" to peg(0), "last peg" to peg(5))
            val with = parts.map { (_, node) -> node.getBoundsInRoot() }
            shownTools.value = listOf(TunerTool.INTONATION_CHECK)
            compose.waitForIdle()
            compose.onAllNodesWithTag(TunerTestTags.STRUM_BUTTON, useUnmergedTree = true).assertCountEquals(0)
            val without = parts.map { (_, node) -> node.getBoundsInRoot() }
            parts.forEachIndexed { i, (name, _) -> if (with[i] != without[i]) fail("$size: the $name moved, ${with[i]} with the button, ${without[i]} without") }
        }
    }

    /**
     * The phone check with a synthetic strum through a fake microphone: the real screen and ViewModel, the strum check's
     * button tapped, then a strum of the open strings with A2 20 cents flat and G3 15 sharp: the panel's columns say so,
     * "4 of 6 in tune" once it has rung out, the button off again and the panel staying until its × (`-a
     * strumScreens=true` saves pictures).
     */
    @Test
    fun aSyntheticStrumOnTheRealScreen() {
        val standard = TunerOptions(tuning = Tunings.STANDARD).stringHz
        val off = floatArrayOf(0f, -20f, 0f, 15f, 0f, 0f)
        val recorder = StrumOnCue(FakeStrums.strum(FakeStrums.detuned(standard, off), 3.0) + FakeStrums.quiet(1.0))
        val saved = AppSettings()
        val settings = FakeSettingsRepository(saved)
        val side = mutableStateOf(false)
        val dark = mutableStateOf(false)
        compose.mainClock.autoAdvance = true
        compose.setContent {
            val onItsSide = side.value
            PhoneScreen(if (onItsSide) XIAOMI_ON_ITS_SIDE else XIAOMI_UPRIGHT, SystemBars.tab(onItsSide)) {
                TwangleTheme(darkTheme = dark.value, animate = false) {
                    StyleBackdrop(Modifier.fillMaxSize())
                    TunerScreen(
                        audioRecorder = recorder,
                        settingsRepository = settings,
                        tonePlayer = FakeTonePlayer(),
                        tools = ToolSwitches.ALL_ON,
                        saved = saved,
                    )
                }
            }
        }
        compose.waitUntil(STEP_WAIT_MS) { count(TunerTestTags.PICKER_CHIP) > 0 }
        // The button first, as the player taps it before strumming.
        strumButton().performClick()
        compose.waitUntil(STEP_WAIT_MS) { wordTextOrNull() == context.getString(R.string.tuner_strum_waiting) }
        picture("real-1-waiting")
        recorder.cue = true
        val tighten = context.getString(R.string.tuner_word_tighten)
        val loosen = context.getString(R.string.tuner_word_loosen)
        compose.waitUntil(STEP_WAIT_MS) { descriptionOrNull(1)?.contains(tighten) == true && descriptionOrNull(3)?.contains(loosen) == true }
        picture("real-2-ringing")
        // The count in the word's place, and once the strum has rung out the button off again, the panel staying.
        compose.waitUntil(STEP_WAIT_MS) { wordTextOrNull() == count(4, 6) }
        compose.waitUntil(STEP_WAIT_MS) {
            compose.onAllNodesWithTag(TunerTestTags.STRUM_BUTTON, useUnmergedTree = true).fetchSemanticsNodes()
                .firstOrNull()?.config?.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.Off
        }
        panel.assertIsDisplayed()
        for ((name, onItsSide, isDark) in listOf(
            Triple("upright-light", false, false),
            Triple("upright-dark", false, true),
            Triple("side-dark", true, true),
            Triple("side-light", true, false),
        )) {
            side.value = onItsSide
            dark.value = isDark
            compose.waitForIdle()
            assertEquals(name, count(4, 6), wordText.text())
            panel.assertIsDisplayed()
            picture("real-3-$name")
        }
        side.value = false
        compose.waitForIdle()
        compose.onNodeWithTag(TunerTestTags.STRUM_CLOSE, useUnmergedTree = true).performClick()
        compose.waitUntil(STEP_WAIT_MS) { count(TunerTestTags.STRUM_PANEL) == 0 }
    }

    /** Each column over its own peg's side, in the pegs' order, inside the panel. */
    private fun assertColumnsOverPegs(case: String) {
        val panelRect = panel.getBoundsInRoot()
        var lastCentre = Float.NEGATIVE_INFINITY
        for (index in strings.indices) {
            val rect = column(index).getBoundsInRoot()
            val pegRect = peg(index).getBoundsInRoot()
            val centre = (rect.left + rect.right).value / 2f
            val pegCentre = (pegRect.left + pegRect.right).value / 2f
            if (centre <= lastCentre) fail("$case: column ${strings[index]} isn't after the one before")
            if (abs(centre - pegCentre) > COLUMN_PEG_SLACK) fail("$case: column ${strings[index]} $rect isn't over its peg $pegRect")
            if (rect.left < panelRect.left - SLACK || rect.right > panelRect.right + SLACK) fail("$case: column ${strings[index]} $rect is outside the panel $panelRect")
            lastCentre = centre
        }
    }

    private fun description(index: Int): String =
        column(index).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()

    private fun descriptionOrNull(index: Int): String? =
        compose.onAllNodesWithTag(TunerTestTags.strumString(index), useUnmergedTree = true).fetchSemanticsNodes()
            .firstOrNull()?.config?.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    private fun wordTextOrNull(): String? =
        compose.onAllNodesWithTag(TunerTestTags.WORD_TEXT, useUnmergedTree = true).fetchSemanticsNodes()
            .firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }

    private fun picture(name: String) {
        if (arguments.getString(SCREENS_ARGUMENT) != "true") return
        val dir = arguments.getString(OUTPUT_DIR_ARGUMENT) ?: return
        compose.waitForIdle()
        val bitmap = compose.onPhoneScreen().captureToImage().asAndroidBitmap()
        File(File(dir).apply { mkdirs() }, "strum-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun count(inTune: Int, of: Int) = context.resources.getQuantityString(R.plurals.tuner_strum_count, inTune, inTune, of)

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.text(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun DpRect.overlaps(other: DpRect) =
        left < other.right - SLACK && other.left < right - SLACK && top < other.bottom - SLACK && other.top < bottom - SLACK

    companion object {
        /** Standard tuning: E2 in tune, A2 12 cents flat, D3 in tune, G3 8 sharp, B3 not heard, E4 in tune but less sure. */
        fun strum(phase: StrumPhase, allInTune: Boolean = false) = TunerUiState.StrumCheck(
            tuning = Tunings.STANDARD,
            phase = phase,
            strings = if (allInTune) {
                List(6) { StrumString(StrumVerdict.IN_TUNE, 0f) }
            } else {
                listOf(
                    StrumString(StrumVerdict.IN_TUNE, 1f),
                    StrumString(StrumVerdict.FLAT, -12f),
                    StrumString(StrumVerdict.IN_TUNE, 0f),
                    StrumString(StrumVerdict.SHARP, 8f),
                    StrumString(StrumVerdict.NOT_HEARD),
                    StrumString(StrumVerdict.IN_TUNE, 3f, sure = false),
                )
            },
        )

        private val LISTENING = TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD)
        private val DETECTED_A2 = TunerUiState.Detected(
            mode = TunerMode.AUTO, tuning = Tunings.STANDARD, noteName = "A", octave = 2, frequencyHz = 109f, cents = -14f,
            targetString = 1, inTune = false, hint = TunerHint.TUNE_UP,
        )

        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 30L
        private const val STEP_WAIT_MS = 8_000L
        private const val SCREENS_ARGUMENT = "strumScreens"
        private const val OUTPUT_DIR_ARGUMENT = "additionalTestOutputDir"

        /**
         * A short narrow phone (360 x 720 dp with navigation buttons: 650 dp without the status bar and
         * the buttons): upright less the app's navigation bar (at most 80 dp), on its side less the 80 dp rail.
         */
        private val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)

        /** The owner's Xiaomi 12X upright (393 x 873 dp) without the status bar, the button bar and the app's navigation bar. */
        private val XIAOMI_UPRIGHT = DpSize(393.dp, 713.dp)

        /** The Xiaomi on its side, less the app's 80 dp rail (the status bar and the gesture bar are the bars'). */
        private val XIAOMI_ON_ITS_SIDE = DpSize(793.dp, 393.dp)
        private const val OWNER_FONT_SCALE = 1.25f
        private val SLACK = 0.5.dp

        /** The strum check's button stands over the last peg: their centres this close (at 1.25× text the peg's face grows, the button's doesn't). */
        private const val PEG_FACE_SLACK = 4f

        /** A column's centre this close to its peg's: the panel spreads its columns evenly, the pegs' row spreads its own. */
        private const val COLUMN_PEG_SLACK = 16f
    }
}
