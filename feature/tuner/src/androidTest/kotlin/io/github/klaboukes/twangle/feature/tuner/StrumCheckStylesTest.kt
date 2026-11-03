package io.github.klaboukes.twangle.feature.tuner

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.testing.onPhoneScreen
import java.io.File
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.feature.tuner.components.StrumMarkVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * The strum check (2.0, Phase 74): its button above the pegs at their end (beside them on its side), then a strum of
 * the open strings: each string's
 * mark above its peg (↑ to tighten, ↓ to loosen, ✓ in tune, – not heard), "3 of 6 in tune" in the word's place while
 * no single string is heard, nothing at all with nothing heard (a brake on, or no strum yet). Upright on a narrow
 * phone and on the owner's Xiaomi at 1.25× text, light and dark, on its side and in focus mode: each mark centred over
 * its peg, clear of the word above the pegs and of everything else, inside the window; the
 * pegs, the lens and the word don't move when the marks come and go. TalkBack hears each peg's mark as its state and
 * the count through the word. At 2× text in Dutch the count line is wholly shown.
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
    private val shownDark = mutableStateOf(false)
    private val shownLocale = mutableStateOf("en")
    private val shownFocus = mutableStateOf(false)
    private val shownMode = mutableStateOf(TunerMode.AUTO)
    private val shownTools = mutableStateOf(listOf(TunerTool.INTONATION_CHECK, TunerTool.STRUM_CHECK))
    private val shownStrumCheck = mutableStateOf(false)

    // The app's colours, read while composing.
    private var background = Color.Unspecified

    private fun show() {
        compose.setContent {
            // The area inside the bars: TunerScreen keeps TunerContent clear of them.
            PhoneScreen(shownSize.value, SystemBars.NONE, shownFontScale.value, shownLocale.value) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
                    background = MaterialTheme.colorScheme.background
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
                        animationsOff = true,
                    )
                }
            }
        }
    }

    private val strings = listOf("E2", "A2", "D3", "G3", "B3", "E4")
    private fun mark(index: Int) = compose.onNodeWithTag(TunerTestTags.strumMark(index), useUnmergedTree = true)
    private fun peg(index: Int) = compose.onNodeWithText(strings[index])
    private val wordText get() = compose.onNodeWithTag(TunerTestTags.WORD_TEXT, useUnmergedTree = true)

    /** Upright, light and dark, at normal text and the owner's 1.25×: each mark is the string's verdict, centred over its peg, clear of the word. */
    @Test
    fun uprightEachMarkStandsAboveItsPegClearOfTheWord() {
        show()
        for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE)) {
            shownSize.value = size
            shownFontScale.value = scale
            for (dark in listOf(false, true)) {
                shownDark.value = dark
                compose.waitForIdle()
                assertMarksAbovePegs("${if (dark) "dark" else "light"} ${size.width} x ${size.height} at ${scale}x")
            }
        }
    }

    /** On its side (the wide layout, Phase 73): the marks above the pegs too, inside the window and clear of the lens; focus mode has no pegs, so no marks, but the count. */
    @Test
    fun onItsSideAndInFocusMode() {
        shownSize.value = PHONE_LANDSCAPE
        show()
        compose.onNodeWithTag(TunerTestTags.WIDE).assertIsDisplayed()
        assertMarksAbovePegs("on its side")
        val lens = compose.onNodeWithTag(TunerTestTags.GAUGE).getBoundsInRoot()
        for (index in strings.indices) {
            val rect = mark(index).getBoundsInRoot()
            if (rect.overlaps(lens)) fail("on its side: the mark over ${strings[index]} $rect overlaps the lens $lens")
        }
        assertEquals(count(3, 6), wordText.text())

        shownFocus.value = true
        compose.waitForIdle()
        compose.onNodeWithTag(TunerTestTags.COMPACT).assertIsDisplayed()
        compose.onAllNodesWithTag(TunerTestTags.strumMark(0), useUnmergedTree = true).assertCountEquals(0)
        assertEquals(count(3, 6), wordText.text())
    }

    /** "3 of 6 in tune" in the word's place while no string is heard (said by TalkBack too); a string heard takes the word back, the marks staying; all in tune says so. */
    @Test
    fun theCountStandsInTheWordsPlace() {
        show()
        assertEquals(count(3, 6), wordText.text())
        compose.onNode(hasContentDescription(count(3, 6))).assertExists()
        // A strum's many notes may read as noise to the tuner: no tip about the microphone under the count.
        shownState.value = LISTENING.copy(tip = ReceptionTip.TOO_NOISY)
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.tuner_tip_too_noisy)).assertDoesNotExist()
        assertEquals(count(3, 6), wordText.text())
        shownState.value = LISTENING

        shownState.value = DETECTED_A2
        compose.waitForIdle()
        assertEquals(context.getString(R.string.tuner_word_tighten), wordText.text())
        mark(1).assertExists()

        shownState.value = LISTENING
        shownHeard.value = HeardStrum.OpenStrings(strum(StrumPhase.DONE, allInTune = true), 1)
        compose.waitForIdle()
        assertEquals(context.getString(R.string.tuner_strum_all_in_tune), wordText.text())
        for (index in strings.indices) mark(index).assert(verdict(StrumVerdict.IN_TUNE))
    }

    /** With nothing heard (no strum yet, or the strum check's brake on) no mark and no count: the plain tuner, and nothing else moves. */
    @Test
    fun nothingHeardShowsNothingAndTheMarksMoveNothing() {
        show()
        val parts = listOf(
            "lens" to compose.onNodeWithTag(TunerTestTags.GAUGE),
            "word" to compose.onNodeWithTag(TunerTestTags.WORD),
            "first peg" to peg(0),
            "last peg" to peg(5),
        )
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            shownHeard.value = HeardStrum.OpenStrings(strum(StrumPhase.DONE), 1)
            compose.waitForIdle()
            val withMarks = parts.map { (_, node) -> node.getBoundsInRoot() }
            shownHeard.value = HeardStrum.None
            compose.waitForIdle()
            for (index in strings.indices) compose.onAllNodesWithTag(TunerTestTags.strumMark(index), useUnmergedTree = true).assertCountEquals(0)
            assertEquals(context.getString(R.string.tuner_hint_play_string), wordText.text())
            val without = parts.map { (_, node) -> node.getBoundsInRoot() }
            parts.forEachIndexed { i, (name, _) ->
                // The word's width follows its text ("3 of 6 in tune", "Play a string"), centred or at the start: only its line may not move.
                val moved = if (name == "word") withMarks[i].top != without[i].top || withMarks[i].bottom != without[i].bottom else withMarks[i] != without[i]
                if (moved) fail("$size: the $name moved, ${withMarks[i]} with the marks, ${without[i]} without")
            }
        }
    }

    /** In Any note there are no pegs, so no marks and no strum check's button (open strings are checked in guitar mode only). */
    @Test
    fun anyNoteHasNoMarks() {
        shownMode.value = TunerMode.CHROMATIC
        shownState.value = TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD)
        show()
        compose.onAllNodesWithTag(TunerTestTags.strumMark(0), useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag(TunerTestTags.STRUM_BUTTON, useUnmergedTree = true).assertCountEquals(0)
    }

    /**
     * The strum check's button (after the owner's guitar check, 2026-11-03): upright above the pegs at their end, its
     * outline ending in line with the last peg's, clear of the marks; on its side after the last peg, on the pegs' line.
     * A tap turns it on (TalkBack: a switch), and until a strum the word says "Strum all the open strings".
     */
    @Test
    fun theStrumButtonStandsAboveTheLastPeg() {
        shownHeard.value = HeardStrum.OpenStrings(strum(StrumPhase.DONE), 1)
        show()
        for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE, PHONE_LANDSCAPE to 1f)) {
            shownSize.value = size
            shownFontScale.value = scale
            compose.waitForIdle()
            val case = "${size.width} x ${size.height} at ${scale}x"
            val button = strumButton().assertIsDisplayed().getBoundsInRoot()
            val last = peg(5).getBoundsInRoot()
            val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
            if (button.right > window.right + SLACK || button.left < window.left - SLACK) fail("$case: the button $button is outside the window $window")
            if (size.width < size.height) {
                if (button.bottom > last.top + SLACK) fail("$case: the button $button reaches into the last peg $last")
                val buttonCentre = (button.left + button.right).value / 2f
                val pegCentre = (last.left + last.right).value / 2f
                if (abs(buttonCentre - pegCentre) > PEG_FACE_SLACK) fail("$case: the button $button doesn't stand over the last peg $last")
                for (index in strings.indices) {
                    val rect = mark(index).getBoundsInRoot()
                    if (rect.overlaps(button)) fail("$case: the mark over ${strings[index]} $rect overlaps the button $button")
                }
                assertMarksAbovePegs(case)
            } else {
                if (button.left < last.right - SLACK) fail("$case: the button $button isn't after the last peg $last")
                if (button.bottom < last.top || button.top > last.bottom) fail("$case: the button $button isn't on the pegs' line $last")
            }
        }
        shownSize.value = PHONE_UPRIGHT
        shownFontScale.value = 1f
        shownHeard.value = HeardStrum.None
        compose.waitForIdle()
        strumButton().assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        strumButton().performClick()
        compose.waitForIdle()
        strumButton().assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        assertEquals(context.getString(R.string.tuner_strum_waiting), wordText.text())
        strumButton().performClick()
        compose.waitForIdle()
        assertEquals(context.getString(R.string.tuner_hint_play_string), wordText.text())
    }

    /** Without the strum check (the 12-string, a build without it) no button, and the pegs and the lens don't move. */
    @Test
    fun withoutTheStrumCheckNoButtonAndNothingMoves() {
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

    private fun strumButton() = compose.onNodeWithTag(TunerTestTags.STRUM_BUTTON, useUnmergedTree = true)

    /** TalkBack hears each peg's mark as its state: "Tighten", "Loosen", "in tune", "Not heard"; on the locked peg after "Locked". */
    @Test
    fun talkBackHearsEveryPegsMark() {
        show()
        val inTune = context.getString(R.string.tuner_in_tune)
        val expected = listOf(
            inTune,
            context.getString(R.string.tuner_word_tighten),
            inTune,
            context.getString(R.string.tuner_word_loosen),
            context.getString(R.string.tuner_strum_not_heard),
            inTune,
        )
        expected.forEachIndexed { index, state ->
            assertEquals(strings[index], state, peg(index).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        }
        // The marks themselves are silent: the peg says it.
        val markConfig = mark(1).fetchSemanticsNode().config
        assertEquals(null, markConfig.getOrNull(SemanticsProperties.ContentDescription))

        shownState.value = TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, lockedString = 1)
        compose.waitForIdle()
        assertEquals(
            context.getString(R.string.tuner_string_locked_mark, context.getString(R.string.tuner_word_tighten)),
            peg(1).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription),
        )
    }

    /** Light and dark: every mark is drawn (pixels of its own over the background), the paler less sure one too. */
    @Test
    fun everyMarkIsDrawn() {
        show()
        for (dark in listOf(false, true)) {
            shownDark.value = dark
            compose.waitForIdle()
            for (index in strings.indices) {
                val image = mark(index).captureToImage().toPixelMap()
                val back = background.toArgb()
                var drawn = 0
                for (y in 0 until image.height) for (x in 0 until image.width) if (!near(image[x, y].toArgb(), back)) drawn++
                if (drawn < MARK_PIXELS) fail("${if (dark) "dark" else "light"}: the mark over ${strings[index]} has only $drawn pixels")
            }
        }
    }

    /** At 2× text in Dutch, upright and on its side: the count line is wholly shown, and so is every mark. */
    @Test
    fun atTwiceTheTextSizeInDutchTheCountIsWhollyShown() {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        show()
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
            assertWhollyShown(wordText, "nl $size at 2x: the count")
            for (index in strings.indices) assertWhollyShown(mark(index), "nl $size at 2x: the mark over ${strings[index]}")
        }
    }

    /**
     * The phone check with a synthetic strum through a fake microphone: the real screen and ViewModel, the strum check's
     * button tapped, then a strum of the open strings with A2 20 cents flat and G3 15 sharp: ↑ over
     * A2, ↓ over G3, ✓ over the others, and "4 of 6 in tune" once it has rung out. The verdicts stay while the screen is
     * turned and its theme changes: upright and on its side, light and dark (`-a strumScreens=true` saves pictures).
     */
    @Test
    fun aSyntheticStrumIsMarkedOnTheRealScreen() {
        val standard = TunerOptions(tuning = Tunings.STANDARD).stringHz
        val off = floatArrayOf(0f, -20f, 0f, 15f, 0f, 0f)
        val recorder = StrumOnCue(FakeStrums.strum(FakeStrums.detuned(standard, off), 3.0) + FakeStrums.quiet(1.0))
        val saved = AppSettings()
        val settings = FakeSettingsRepository(saved)
        val side = mutableStateOf(false)
        compose.mainClock.autoAdvance = true
        compose.setContent {
            val onItsSide = side.value
            PhoneScreen(if (onItsSide) XIAOMI_ON_ITS_SIDE else XIAOMI_UPRIGHT, SystemBars.tab(onItsSide)) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
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
        compose.waitUntil(STEP_WAIT_MS) { compose.onAllNodesWithTag(TunerTestTags.PICKER_CHIP).fetchSemanticsNodes().isNotEmpty() }
        // The button first, as the player taps it before strumming.
        strumButton().performClick()
        compose.waitUntil(STEP_WAIT_MS) { wordTextOrNull() == context.getString(R.string.tuner_strum_waiting) }
        picture("real-1-waiting")
        recorder.cue = true
        val expected = listOf(StrumVerdict.IN_TUNE, StrumVerdict.FLAT, StrumVerdict.IN_TUNE, StrumVerdict.SHARP, StrumVerdict.IN_TUNE, StrumVerdict.IN_TUNE)
        compose.waitUntil(STEP_WAIT_MS) { strings.indices.all { markVerdict(it) == expected[it] } }
        picture("real-2-ringing")
        // The count in the word's place, and once the strum has rung out the button off again.
        compose.waitUntil(STEP_WAIT_MS) { wordTextOrNull() == count(4, 6) }
        compose.waitUntil(STEP_WAIT_MS) {
            compose.onAllNodesWithTag(TunerTestTags.STRUM_BUTTON, useUnmergedTree = true).fetchSemanticsNodes()
                .firstOrNull()?.config?.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.Off
        }
        for ((name, onItsSide, dark) in listOf(
            Triple("upright-light", false, false),
            Triple("upright-dark", false, true),
            Triple("side-dark", true, true),
            Triple("side-light", true, false),
        )) {
            side.value = onItsSide
            shownDark.value = dark
            compose.waitForIdle()
            assertEquals(name, count(4, 6), wordText.text())
            strings.indices.forEach { mark(it).assert(verdict(expected[it])) }
            picture("real-3-$name")
        }
    }

    private fun markVerdict(index: Int): StrumVerdict? =
        compose.onAllNodesWithTag(TunerTestTags.strumMark(index), useUnmergedTree = true).fetchSemanticsNodes()
            .firstOrNull()?.config?.getOrNull(StrumMarkVerdict)

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

    /** Each mark the string's verdict, centred over its peg and above its face, clear of the word and of each other, inside the window. */
    private fun assertMarksAbovePegs(case: String) {
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val word = compose.onNodeWithTag(TunerTestTags.WORD).assertIsDisplayed().getBoundsInRoot()
        val verdicts = strum(StrumPhase.DONE).strings.map { it.verdict }
        val marks = strings.indices.map { index ->
            val node = mark(index).assert(verdict(verdicts[index]))
            val rect = node.getBoundsInRoot()
            val pegRect = peg(index).getBoundsInRoot()
            val name = "$case: the mark over ${strings[index]} $rect"
            if (abs((rect.left + rect.right).value / 2f - (pegRect.left + pegRect.right).value / 2f) > 1f) fail("$name isn't centred over its peg $pegRect")
            if (rect.bottom > pegRect.top + SLACK) fail("$name reaches into its peg $pegRect")
            if (rect.overlaps(word)) fail("$name overlaps the word $word")
            if (rect.left < window.left - SLACK || rect.right > window.right + SLACK || rect.top < window.top - SLACK) fail("$name is outside the window $window")
            rect
        }
        for (i in marks.indices) for (j in i + 1 until marks.size) if (marks[i].overlaps(marks[j])) fail("$case: marks $i and $j overlap")
    }

    private fun verdict(verdict: StrumVerdict) = SemanticsMatcher.expectValue(StrumMarkVerdict, verdict)

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

        /** ARGB colours within a few steps per channel. */
        private fun near(a: Int, b: Int): Boolean =
            abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) <= 12 &&
                abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) <= 12 &&
                abs((a and 0xFF) - (b and 0xFF)) <= 12

        private const val MARK_PIXELS = 12

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
    }
}
