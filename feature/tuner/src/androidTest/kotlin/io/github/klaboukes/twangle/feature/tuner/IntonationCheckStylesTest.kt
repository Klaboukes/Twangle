package io.github.klaboukes.twangle.feature.tuner

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.klaboukes.twangle.core.audio.AudioConfig
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.intonation.IntonationResult
import io.github.klaboukes.twangle.core.dsp.intonation.IntonationVerdict
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.synth.PluckSynth
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.checkedLanguages
import io.github.klaboukes.twangle.core.testing.onPhoneScreen
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.pow

/**
 * The 12th-fret check (Phase 29) and the "more tools" button in each of the six styles: its panel in
 * the meter's place, the line (and the verdict's buttons) under it, the button above the strings.
 * Upright every step fits without scrolling and nothing moves between steps; at 2× text in Dutch every
 * part is wholly shown (every language overnight). The menu lists both tools and turns one on; with the
 * strum check alone its own switch shows. TalkBack hears the steps, the verdict, each string's result
 * and the button's state. Synthetic plucks through a fake microphone give a verdict on the real screen.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class IntonationCheckStylesTest(private val style: VisualStyle) {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()

    private val shownState = mutableStateOf<TunerUiState>(check(IntonationStep.CHOOSE))
    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownFontScale = mutableStateOf(1f)
    private val shownLocale = mutableStateOf("en")
    private val shownTools = mutableStateOf(listOf(TunerTool.STRUM_CHECK, TunerTool.INTONATION_CHECK))
    private val shownIntonation = mutableStateOf(true)
    private val toolChanges = mutableListOf<Pair<String, Boolean>>()
    private val clicks = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            // The area inside the bars: TunerScreen keeps TunerContent clear of them.
            PhoneScreen(shownSize.value, SystemBars.NONE, shownFontScale.value, shownLocale.value) {
                TwangleTheme(style = style, darkTheme = false, animate = false) {
                    TunerContent(
                        shownState.value,
                        TunerMode.AUTO,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = { clicks += "string $it" },
                        onRetry = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        strumCheck = false,
                        onStrumCheckChange = { toolChanges += "strum" to it },
                        tunerTools = shownTools.value,
                        intonationCheck = shownIntonation.value,
                        onIntonationCheckChange = { toolChanges += "intonation" to it },
                        onCheckAgain = { clicks += "again" },
                        onNextString = { clicks += "next" },
                    )
                }
            }
        }
    }

    private val panel get() = compose.onNodeWithTag(TunerTestTags.INTONATION_PANEL)
    private val status get() = compose.onNodeWithTag(TunerTestTags.INTONATION_STATUS)
    private val moreTools get() = compose.onNodeWithTag(TunerTestTags.MORE_TOOLS)
    private val strings = listOf("E2", "A2", "D3", "G3", "B3", "E4")

    /** The parts to check; on its side the compact layout hides the controls until tapped ([controls] false). */
    private fun parts(controls: Boolean = true): List<Pair<String, SemanticsNodeInteraction>> = buildList {
        add("panel" to panel)
        add("status" to status)
        if (controls) {
            add("more tools" to moreTools)
            strings.forEach { add("string $it" to compose.onNodeWithText(it)) }
        }
    }

    /** Upright on a narrow phone and on the Xiaomi at 1.25×: every step in view, clear of each other, the line never moving. */
    @Test
    fun uprightEveryStepFitsAndNothingMoves() {
        show()
        for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE)) {
            shownSize.value = size
            shownFontScale.value = scale
            val tops = mutableSetOf<Float>()
            for ((name, state) in steps()) {
                shownState.value = state
                compose.waitForIdle()
                val case = "$style $name ${size.width} x ${size.height} at ${scale}x:"
                val bounds = assertAllInView(case, parts())
                tops += bounds[1].top.value
                tops += bounds[2].top.value
            }
            if (tops.size != 2) fail("$style ${size.width} x ${size.height}: the line or the strings moved between steps: $tops")
        }
    }

    /** At 2× text in Dutch, upright and on its side: the panel, the line, the verdict's buttons and the strings wholly shown. */
    @Test
    fun atTwiceTheTextSizeInDutchEveryPartIsWhollyShown() {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        show()
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            for ((name, state) in steps()) {
                shownState.value = state
                compose.waitForIdle()
                for ((part, node) in parts(controls = size == PHONE_UPRIGHT)) assertWhollyShown(node, "$style nl $name $size at 2x: $part")
                if (state.step == IntonationStep.VERDICT) {
                    picture("2x-$name-${size.width.value.toInt()}")
                    assertWhollyShown(compose.onNodeWithTag(TunerTestTags.INTONATION_AGAIN), "$style $size: Check again")
                    assertWhollyShown(compose.onNodeWithTag(TunerTestTags.INTONATION_NEXT), "$style $size: Next string")
                }
            }
        }
    }

    /** Every language at 2× text, upright and on its side, every step: every text wholly shown (overnight). */
    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        show()
        for (language in checkedLanguages) {
            shownLocale.value = language
            for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                for ((name, state) in steps()) {
                    shownState.value = state
                    compose.waitForIdle()
                    compose.assertEveryTextWhollyShown("$style $language $name ${size.width} x ${size.height} at 2x:")
                }
            }
        }
    }

    /** The verdict's two buttons are the same size whatever their labels, and call back. */
    @Test
    fun theVerdictsButtonsAreTheSameSizeAndWork() {
        shownState.value = check(IntonationStep.VERDICT, result = IntonationResult(8f, IntonationVerdict.MOVE_SADDLE_BACK))
        show()
        val again = compose.onNodeWithTag(TunerTestTags.INTONATION_AGAIN)
        val next = compose.onNodeWithTag(TunerTestTags.INTONATION_NEXT)
        val a = again.getBoundsInRoot()
        val b = next.getBoundsInRoot()
        assertEquals((a.right - a.left).value, (b.right - b.left).value, 0.5f)
        assertEquals((a.bottom - a.top).value, (b.bottom - b.top).value, 0.5f)
        assertTrue("at least 48 dp high: $a", (a.bottom - a.top) >= 48.dp - SLACK)
        again.performClick()
        next.performClick()
        assertEquals(listOf("again", "next"), clicks)
        // Hidden outside the verdict.
        shownState.value = check(IntonationStep.OPEN)
        compose.waitForIdle()
        again.assertDoesNotExist()
    }

    /** The "more tools" menu lists both tools, the one that is on ticked (selected); picking one calls back. */
    @Test
    fun theMoreToolsMenuListsBothToolsAndTurnsOneOn() {
        show()
        compose.onNodeWithTag(TunerTestTags.tool(TunerTool.STRUM_CHECK)).assertDoesNotExist()
        moreTools.performClick()
        val strum = compose.onNodeWithTag(TunerTestTags.tool(TunerTool.STRUM_CHECK))
        val intonation = compose.onNodeWithTag(TunerTestTags.tool(TunerTool.INTONATION_CHECK))
        strum.assertIsDisplayed()
        intonation.assertIsDisplayed()
        assertEquals(false, strum.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        assertEquals(true, intonation.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        strum.performClick()
        compose.waitForIdle()
        assertEquals(listOf("strum" to true), toolChanges)
        strum.assertDoesNotExist()
        moreTools.performClick()
        compose.onNodeWithTag(TunerTestTags.tool(TunerTool.INTONATION_CHECK)).performClick()
        assertEquals(listOf("strum" to true, "intonation" to false), toolChanges)
    }

    /** With the strum check as the only tool (a violin), its own switch shows and no "more tools". */
    @Test
    fun withOneToolTheStrumSwitchShowsAsInVersionOne() {
        shownTools.value = listOf(TunerTool.STRUM_CHECK)
        shownIntonation.value = false
        shownState.value = TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD)
        show()
        compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE).assertIsDisplayed()
        moreTools.assertDoesNotExist()
    }

    /** TalkBack: the line says the step with the string spoken, the verdict, each string's result, the button's state. */
    @Test
    fun talkBackHearsTheStepsTheVerdictAndTheResults() {
        shownState.value = check(IntonationStep.OPEN)
        show()
        fun spokenLine(): String = status.fetchSemanticsNode().children
            .firstNotNullOf { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() }
        assertTrue(spokenLine(), spokenLine().startsWith(context.getString(R.string.tuner_intonation_play_open, "A 2")))

        shownState.value = check(IntonationStep.VERDICT, result = IntonationResult(8f, IntonationVerdict.MOVE_SADDLE_BACK))
        compose.waitForIdle()
        assertTrue(spokenLine(), spokenLine().startsWith(context.getString(R.string.tuner_intonation_back)))
        assertTrue(spokenLine(), spokenLine().endsWith(context.getString(R.string.tuner_intonation_back_mm, "3")))
        val sharp = context.resources.getQuantityString(R.plurals.tuner_cents_sharp, 8, 8)
        assertEquals(listOf(sharp), panel.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
        // The A string's button carries its result.
        val chip = compose.onNodeWithText("A2", useUnmergedTree = false).fetchSemanticsNode().config
        assertEquals(context.getString(R.string.tuner_intonation_mark_back), chip.getOrNull(SemanticsProperties.StateDescription))
        assertEquals(
            context.getString(R.string.tuner_intonation_check_string, "A 2"),
            chip.getOrNull(SemanticsActions.OnClick)?.label,
        )
        val button = moreTools.fetchSemanticsNode().config
        assertEquals(listOf(context.getString(R.string.tuner_more_tools)), button.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(context.getString(R.string.tuner_intonation_check), button.getOrNull(SemanticsProperties.StateDescription))

        // The summary: each string a button with its result.
        shownState.value = check(IntonationStep.CHOOSE, string = null)
        compose.waitForIdle()
        picture("5-summary")
        val column = compose.onNodeWithTag(TunerTestTags.intonationString(1)).fetchSemanticsNode().config
        val spoken = column.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
        assertTrue(spoken, spoken.startsWith("A 2") && spoken.contains(context.getString(R.string.tuner_intonation_mark_back)))
        val notChecked = compose.onNodeWithTag(TunerTestTags.intonationString(4)).fetchSemanticsNode().config
        assertEquals(
            listOf(context.getString(R.string.tuner_intonation_spoken_not_checked, "B 3")),
            notChecked.getOrNull(SemanticsProperties.ContentDescription),
        )
        compose.onNodeWithTag(TunerTestTags.intonationString(4)).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("string 4"), clicks)
    }

    /**
     * The phone check with synthetic tones through a fake microphone: the real screen and ViewModel,
     * the 12th-fret check turned on from the menu, the A string tapped, then an open A2 pluck and an A3
     * pluck 8 cents sharp: the verdict says move the saddle back, about 3 mm, and the A string's
     * button carries its mark. With `-a intonationScreens=true` it saves pictures of each step.
     */
    @Test
    fun syntheticPlucksGiveTheVerdictOnTheRealScreen() {
        // Silence until the string is chosen, then the two plucks.
        val recorder = PluckOnCue(pluck(110f, 2.6f) + pluck(220f * cents(8f), 2.6f))
        compose.mainClock.autoAdvance = true
        compose.setContent {
            PhoneScreen(XIAOMI_UPRIGHT, SystemBars.tab(onItsSide = false)) {
                TwangleTheme(style = style, darkTheme = true, animate = false) {
                    StyleBackdrop(Modifier.fillMaxSize())
                    TunerScreen(
                        audioRecorder = recorder,
                        settingsRepository = FakeSettingsRepository(),
                        tonePlayer = FakeTonePlayer(),
                        tools = ToolSwitches.ALL_ON,
                    )
                }
            }
        }
        moreTools.performClick()
        picture("1-menu")
        compose.onNodeWithTag(TunerTestTags.tool(TunerTool.INTONATION_CHECK)).performClick()
        compose.onNodeWithText("A2").performClick()
        recorder.cue = true
        compose.waitUntil(STEP_WAIT_MS) { stepShown(R.string.tuner_intonation_play_open) }
        picture("2-open")
        compose.waitUntil(STEP_WAIT_MS) { stepShown(R.string.tuner_intonation_press_fret) }
        picture("3-fret")
        compose.waitUntil(STEP_WAIT_MS) { stepShown(R.string.tuner_intonation_back) }
        compose.waitForIdle()
        picture("4-verdict")
        compose.onNodeWithText(context.getString(R.string.tuner_intonation_back_mm, "3")).assertIsDisplayed()
        assertEquals(
            context.getString(R.string.tuner_intonation_mark_back),
            compose.onNodeWithText("A2").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription),
        )
    }

    /** A fake microphone: silence, then [signal] once [cue] is set, then silence again, in real time. */
    private class PluckOnCue(private val signal: FloatArray) : AudioRecorder {
        override val config = AudioConfig()

        @Volatile var cue = false

        override fun chunks(): Flow<FloatArray> = flow {
            val chunk = FloatArray(config.chunkSize)
            val chunkMillis = config.chunkSize * 1000L / config.sampleRate
            var pos = 0
            while (true) {
                chunk.fill(0f)
                if (cue && pos < signal.size) {
                    val count = minOf(chunk.size, signal.size - pos)
                    System.arraycopy(signal, pos, chunk, 0, count)
                    pos += count
                }
                delay(chunkMillis)
                emit(chunk)
            }
        }
    }

    private fun stepShown(title: Int): Boolean =
        compose.onAllNodesWithText(context.getString(title, "A2")).fetchSemanticsNodes().isNotEmpty()

    private fun picture(name: String) {
        if (arguments.getString(SCREENS_ARGUMENT) != "true") return
        val dir = arguments.getString(OUTPUT_DIR_ARGUMENT) ?: return
        compose.waitForIdle()
        val bitmap = compose.onPhoneScreen().captureToImage().asAndroidBitmap()
        File(File(dir).apply { mkdirs() }, "intonation-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The steps to look at: choosing, the open string, the fret not the octave, each verdict, the summary. */
    private fun steps(): List<Pair<String, TunerUiState.IntonationCheck>> = listOf(
        "choose" to TunerUiState.IntonationCheck(Tunings.STANDARD, results = List(6) { null }),
        "open" to check(IntonationStep.OPEN),
        "tune first" to check(IntonationStep.OPEN, snag = IntonationSnag.TUNE_FIRST, tuneFirstCents = -23),
        "fret, not the octave" to check(IntonationStep.FRET, snag = IntonationSnag.NOT_THE_OCTAVE),
        "move back" to check(IntonationStep.VERDICT, result = IntonationResult(8f, IntonationVerdict.MOVE_SADDLE_BACK)),
        "move forward" to check(IntonationStep.VERDICT, result = IntonationResult(-1.4f * 2.7f, IntonationVerdict.MOVE_SADDLE_FORWARD)),
        "fine" to check(IntonationStep.VERDICT, result = IntonationResult(1f, IntonationVerdict.FINE)),
        "summary" to check(IntonationStep.CHOOSE, string = null),
    )

    private fun assertAllInView(case: String, parts: List<Pair<String, SemanticsNodeInteraction>>): List<DpRect> {
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val bounds = parts.map { (name, node) -> name to node.assertIsDisplayed().getBoundsInRoot() }
        for ((name, rect) in bounds) {
            if (rect.left < window.left - SLACK || rect.top < window.top - SLACK || rect.right > window.right + SLACK || rect.bottom > window.bottom + SLACK) {
                fail("$case $name $rect is outside the window $window")
            }
        }
        for (i in bounds.indices) {
            for (j in i + 1 until bounds.size) {
                val (a, first) = bounds[i]
                val (b, second) = bounds[j]
                if (first.overlaps(second)) fail("$case $a $first overlaps $b $second")
            }
        }
        return bounds.map { it.second }
    }

    private fun DpRect.overlaps(other: DpRect) =
        left < other.right - SLACK && other.left < right - SLACK && top < other.bottom - SLACK && other.top < bottom - SLACK

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithText(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text))

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        /** Results so far: E2 fine, A2 back 8, D3 forward 6, the rest not checked. */
        private val RESULTS = listOf(
            IntonationResult(1f, IntonationVerdict.FINE),
            IntonationResult(8f, IntonationVerdict.MOVE_SADDLE_BACK),
            IntonationResult(-6f, IntonationVerdict.MOVE_SADDLE_FORWARD),
            null, null, null,
        )

        fun check(
            step: IntonationStep,
            string: Int? = 1,
            snag: IntonationSnag = IntonationSnag.NONE,
            tuneFirstCents: Int = 0,
            result: IntonationResult? = null,
        ) = TunerUiState.IntonationCheck(
            tuning = Tunings.STANDARD,
            string = string,
            step = step,
            progress = if (step == IntonationStep.OPEN || step == IntonationStep.FRET) 0.6f else 0f,
            snag = snag,
            tuneFirstCents = tuneFirstCents,
            result = result,
            results = RESULTS,
            scaleLengthMm = 648f,
        )

        private fun cents(c: Float) = 2.0.pow(c / 1200.0).toFloat()

        /** A plucked string [seconds] long at 48 kHz, through the app's own pluck synthesis. */
        private fun pluck(hz: Float, seconds: Float): FloatArray {
            val out = FloatArray((48_000 * seconds).toInt())
            PluckSynth(48_000, durationSeconds = seconds).apply { start(hz) }.render(out)
            return out
        }

        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 20L
        private const val STEP_WAIT_MS = 8_000L
        private const val SCREENS_ARGUMENT = "intonationScreens"
        private const val OUTPUT_DIR_ARGUMENT = "additionalTestOutputDir"

        private val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
        private val XIAOMI_UPRIGHT = DpSize(393.dp, 713.dp)
        private const val OWNER_FONT_SCALE = 1.25f
        private val SLACK = 0.5.dp
    }
}
