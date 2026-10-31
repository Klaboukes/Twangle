package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.ui.state.ToggleableState
import android.Manifest
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.klaboukes.twangle.core.audio.AudioConfig
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The pitch trail (Phase 31) in each of the six styles, light and dark: its panel in the meter's place
 * (the needle gone), the note, its cents and the strings as the tuner's, the "more tools" button above
 * the strings. Upright everything fits without scrolling, clear of each other; on its side the panel
 * fits the compact layout; at 2× text in Dutch every part, and the menu's pitch trail, is wholly shown
 * (every language overnight). The menu lists the four tools and the trail ticked. TalkBack hears the
 * line in words. A synthetic A2 through a fake microphone draws the line on the real screen.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`). With
 * `-a trailScreens=true` it saves pictures (the phone check).
 */
@RunWith(Parameterized::class)
class PitchTrailStylesTest(private val style: VisualStyle) {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()

    private val shownState = mutableStateOf<TunerUiState>(detected(-12.3f))
    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownFontScale = mutableStateOf(1f)
    private val shownLocale = mutableStateOf("en")
    private val shownDark = mutableStateOf(false)
    private val shownHistory = mutableStateOf(heldBentAndWavering())
    private val toolChanges = mutableListOf<Pair<String, Boolean>>()

    private fun show() {
        compose.setContent {
            // The area inside the bars: TunerScreen keeps TunerContent clear of them.
            PhoneScreen(shownSize.value, SystemBars.NONE, shownFontScale.value, shownLocale.value) {
                TwangleTheme(style = style, darkTheme = shownDark.value, animate = false) {
                    StyleBackdrop(Modifier.fillMaxSize())
                    TunerContent(
                        shownState.value,
                        TunerMode.AUTO,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = {},
                        onRetry = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        onStrumCheckChange = { toolChanges += "strum" to it },
                        tunerTools = TunerTool.entries,
                        onIntonationCheckChange = { toolChanges += "intonation" to it },
                        onChordNameChange = { toolChanges += "chord" to it },
                        pitchTrail = true,
                        onPitchTrailChange = { toolChanges += "trail" to it },
                        pitchHistory = shownHistory.value,
                    )
                }
            }
        }
    }

    private val panel get() = compose.onNodeWithTag(TunerTestTags.TRAIL_PANEL)
    private val moreTools get() = compose.onNodeWithTag(TunerTestTags.MORE_TOOLS)
    private val strings = listOf("E2", "A2", "D3", "G3", "B3", "E4")
    /** "12 cents flat" in the language shown. */
    private val twelveCentsFlat: String
        get() {
            val config = android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(shownLocale.value)) }
            return context.createConfigurationContext(config).resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)
        }

    private fun parts(controls: Boolean = true): List<Pair<String, SemanticsNodeInteraction>> = buildList {
        add("trail" to panel)
        add("note" to compose.onNodeWithText("A"))
        add("cents" to compose.onNodeWithText(twelveCentsFlat))
        if (controls) {
            add("more tools" to moreTools)
            strings.forEach { add("string $it" to compose.onNodeWithText(it)) }
        }
    }

    /** Upright on a narrow phone and on the Xiaomi at 1.25×, light and dark: the trail in the meter's place, everything in view and clear. */
    @Test
    fun uprightTheTrailTakesTheMetersPlace() {
        show()
        for (dark in listOf(false, true)) {
            shownDark.value = dark
            for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE)) {
                shownSize.value = size
                shownFontScale.value = scale
                compose.waitForIdle()
                val case = "$style ${if (dark) "dark" else "light"} ${size.width} x ${size.height} at ${scale}x:"
                val bounds = assertAllInView(case, parts())
                if (countOf(TunerTestTags.GAUGE) != 0) fail("$case the needle still shows")
                // At the meter's 2:1 shape, as wide as the meter.
                val trail = bounds[0]
                assertEquals("$case the trail's shape", 2f, (trail.right - trail.left) / (trail.bottom - trail.top), 0.02f)
                if (size == XIAOMI_UPRIGHT) picture("${if (dark) "dark" else "light"}-upright")
            }
        }
    }

    /** On its side (the compact layout), light: the trail beside the note, in view. */
    @Test
    fun onItsSideTheTrailFits() {
        shownSize.value = PHONE_LANDSCAPE
        show()
        compose.waitForIdle()
        assertAllInView("$style on its side:", parts(controls = false))
        picture("light-landscape")
    }

    /** At 2× text in Dutch, upright and on its side: the trail, the note and the strings wholly shown, and the menu's pitch trail. */
    @Test
    fun atTwiceTheTextSizeInDutchEveryPartIsWhollyShown() {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        show()
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
            for ((part, node) in parts(controls = size == PHONE_UPRIGHT)) assertWhollyShown(node, "$style nl $size at 2x: $part")
        }
        shownSize.value = PHONE_UPRIGHT
        compose.waitForIdle()
        moreTools.performClick()
        compose.waitForIdle()
        // The item's name, as drawn (the menu is a window of its own).
        val name = compose.onNode(
            hasAnyAncestor(hasTestTag(TunerTestTags.tool(TunerTool.PITCH_TRAIL))) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
            useUnmergedTree = true,
        )
        assertWhollyShown(name, "$style nl at 2x: the menu's pitch trail")
    }

    /** Every language at 2× text, upright and on its side, and with the menu open: every text wholly shown (overnight). */
    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        show()
        for (language in checkedLanguages) {
            shownLocale.value = language
            for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                compose.waitForIdle()
                compose.assertEveryTextWhollyShown("$style $language ${size.width} x ${size.height} at 2x:")
            }
            shownSize.value = PHONE_UPRIGHT
            compose.waitForIdle()
            // Scrolled into view first: the checks above may leave it scrolled away (Space Age's Indonesian
            // cents line takes three lines at 2× text), and a tap off the screen opens nothing.
            moreTools.performScrollTo().performClick()
            compose.waitForIdle()
            compose.assertEveryTextWhollyShown("$style $language menu at 2x:")
            compose.onNodeWithText(context.getString(R.string.tuner_pitch_trail)).performClick()
            toolChanges.clear()
        }
    }

    /** The "more tools" menu lists the four tools, the pitch trail on (a switch); picking it again turns it off. */
    @Test
    fun theMoreToolsMenuListsThePitchTrail() {
        show()
        assertEquals(
            context.getString(R.string.tuner_tool_on, context.getString(R.string.tuner_pitch_trail)),
            moreTools.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription),
        )
        moreTools.performClick()
        val trail = compose.onNodeWithTag(TunerTestTags.tool(TunerTool.PITCH_TRAIL))
        trail.assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_pitch_trail)).assertIsDisplayed()
        for (tool in listOf(TunerTool.STRUM_CHECK, TunerTool.INTONATION_CHECK, TunerTool.CHORD_NAME)) {
            compose.onNodeWithTag(TunerTestTags.tool(tool)).assertIsDisplayed()
        }
        assertEquals(ToggleableState.On, trail.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState))
        trail.performClick()
        compose.waitForIdle()
        assertEquals(listOf("trail" to false), toolChanges)
    }

    /** TalkBack hears the line in words: steady and in tune, a bend, vibrato off the note, and no note. */
    @Test
    fun talkBackDescribesTheLineInWords() {
        shownHistory.value = history { 0.5f }
        show()
        val inTune = context.getString(R.string.tuner_in_tune)
        expectSpoken(context.getString(R.string.tuner_trail_steady, inTune))
        shownHistory.value = history { t -> if (t < 2.5f) 0f else (t - 2.5f) * 30f }
        expectSpoken(context.getString(R.string.tuner_trail_bending_up))
        shownHistory.value = history(inTuneWithin = 2f) { t -> 20f + 15f * sin(2f * PI.toFloat() * 5f * t) }
        expectSpoken(context.getString(R.string.tuner_trail_wavering, context.resources.getQuantityString(R.plurals.tuner_cents_sharp, 20, 20)))
        shownHistory.value = history { Float.NaN }
        expectSpoken(context.getString(R.string.tuner_trail_no_note))
    }

    private fun expectSpoken(words: String) {
        val expected = context.getString(R.string.tuner_trail_spoken, words)
        compose.waitUntil(DESCRIBE_WAIT_MS) { spoken(panel) == expected }
    }

    /**
     * The phone check with a synthetic note through a fake microphone: the real screen and ViewModel,
     * the trail picked from the menu, then an A2 held, bent up 40 cents and back, and vibrato: the needle
     * gives way to the trail, which TalkBack calls steady and in tune while the note is held, then bending up. With `-a trailScreens=true`
     * it saves pictures.
     */
    @Test
    fun aSyntheticNoteDrawsTheTrailOnTheRealScreen() {
        val recorder = NoteOnCue()
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
        compose.onNodeWithTag(TunerTestTags.GAUGE).assertIsDisplayed()
        moreTools.performClick()
        picture("real-1-menu")
        compose.onNodeWithTag(TunerTestTags.tool(TunerTool.PITCH_TRAIL)).performClick()
        compose.waitForIdle()
        panel.assertIsDisplayed()
        if (countOf(TunerTestTags.GAUGE) != 0) fail("the needle still shows with the trail on")
        recorder.cue = true
        val steady = context.getString(R.string.tuner_trail_spoken, context.getString(R.string.tuner_trail_steady, context.getString(R.string.tuner_in_tune)))
        compose.waitUntil(REAL_WAIT_MS) { spoken(panel) == steady }
        picture("real-2-held")
        compose.waitUntil(REAL_WAIT_MS) { spoken(panel) == context.getString(R.string.tuner_trail_spoken, context.getString(R.string.tuner_trail_bending_up)) }
        Thread.sleep(1_000)
        picture("real-3-bent")
        Thread.sleep(2_500)
        picture("real-4-vibrato")
    }

    private fun spoken(node: SemanticsNodeInteraction): String =
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()

    private fun countOf(tag: String): Int = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    /**
     * A fake microphone: silence until [cue] is set, then in real time an A2 sawtooth held for 2 s, bent
     * up 40 cents over 2 s, held there 1 s and let back over 1 s, then 5 Hz vibrato of ±15 cents for
     * 2.5 s, then held again.
     */
    private class NoteOnCue : AudioRecorder {
        override val config = AudioConfig()

        @Volatile var cue = false

        override fun chunks(): Flow<FloatArray> = flow {
            val chunk = FloatArray(config.chunkSize)
            val rate = config.sampleRate.toFloat()
            val chunkMillis = config.chunkSize * 1000L / config.sampleRate
            var phase = 0.0
            var n = 0L
            while (true) {
                if (!cue) {
                    chunk.fill(0f)
                } else {
                    for (i in chunk.indices) {
                        val t = n / rate
                        val cents = when {
                            t < 2f -> 0f
                            t < 4f -> 20f * (t - 2f)
                            t < 5f -> 40f
                            t < 6f -> 40f * (6f - t)
                            t < 8.5f -> 15f * sin(2f * PI.toFloat() * 5f * (t - 6f))
                            else -> 0f
                        }
                        val hz = 110.0 * Math.pow(2.0, cents / 1200.0)
                        phase = (phase + hz / rate) % 1.0
                        chunk[i] = (0.4 * (2 * phase - 1)).toFloat()
                        n++
                    }
                }
                delay(chunkMillis)
                emit(chunk)
            }
        }
    }

    private fun picture(name: String) {
        if (arguments.getString(SCREENS_ARGUMENT) != "true") return
        val dir = arguments.getString(OUTPUT_DIR_ARGUMENT) ?: return
        compose.waitForIdle()
        val bitmap = compose.onPhoneScreen().captureToImage().asAndroidBitmap()
        File(File(dir).apply { mkdirs() }, "trail-$style-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

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

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        private const val HOP = 1024f / 48_000f

        fun detected(cents: Float) = TunerUiState.Detected(
            mode = TunerMode.AUTO,
            tuning = Tunings.STANDARD,
            noteName = "A",
            octave = 2,
            frequencyHz = 108.47f,
            cents = cents,
            targetString = 1,
            inTune = abs(cents) < 5f,
        )

        /** 5 s of readings, one per hop: [cents] off A2 at each time (NaN for a gap). */
        fun history(inTuneWithin: Float = 5f, cents: (Float) -> Float): PitchHistory {
            val history = PitchHistory()
            var t = 0f
            while (t < 5f) {
                t += HOP
                val c = cents(t)
                if (c.isNaN()) history.pushGap(t) else history.push(t, 45, c, abs(c) < inTuneWithin)
            }
            return history
        }

        /** What the owner's check plays: held in tune, a bend up and back, a short gap, vibrato, ending a little flat. */
        fun heldBentAndWavering(): PitchHistory = history { t ->
            when {
                t < 0.3f -> Float.NaN
                t < 1.3f -> -14f * kotlin.math.exp(-(t - 0.3f) / 0.25f)
                t < 2.6f -> 40f * sin(PI.toFloat() * (t - 1.3f) / 1.3f).let { it * it }
                t < 2.9f -> Float.NaN
                t < 4.4f -> 15f * sin(2f * PI.toFloat() * 5f * (t - 2.9f)) + 2f
                else -> -12f
            }
        }

        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 30L
        private const val DESCRIBE_WAIT_MS = 5_000L
        private const val REAL_WAIT_MS = 8_000L
        private const val SCREENS_ARGUMENT = "trailScreens"
        private const val OUTPUT_DIR_ARGUMENT = "additionalTestOutputDir"

        private val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
        private val XIAOMI_UPRIGHT = DpSize(393.dp, 713.dp)
        private const val OWNER_FONT_SCALE = 1.25f
        private val SLACK = 0.5.dp
    }
}
