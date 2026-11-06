package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Focus mode on its side (2.0, Phase 73: the wide layout with nothing but the ruler, the lens and the word) in Ink. On
 * a phone and a tablet on their side, full screen (no rail, no status bar, Phase 40), the ruler names the note across
 * the full width, the exit button over its end in the top end corner, and the lens wide under it (the window less
 * 24 dp each side, as high as the ruler and the word leave, at most half as high as wide) with the word at the start
 * of the line under it and the string small at its end; with 2× font the word and a tip are wholly shown; a tap
 * (light) or TalkBack (dark) brings back the controls. The lens, at its biggest here, is clearly drawn inside its box,
 * light and dark, and skipped by TalkBack, also at 2× font. [TunerContentTest] and [WideLayoutTest] cover the layout itself.
 */
@RunWith(AndroidJUnit4::class)
class CompactLandscapeStylesTest {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Set these to change what the window shows without composing it again.
    private val shownState = mutableStateOf<TunerUiState>(detected())
    private val shownMode = mutableStateOf(TunerMode.AUTO)
    private val shownSize = mutableStateOf(PHONE_LANDSCAPE)
    private val shownFontScale = mutableStateOf(1f)
    private val shownDark = mutableStateOf(false)
    private val shownCompact = mutableStateOf(true)

    private fun show() {
        compose.setContent {
            // The area inside the bars: TunerScreen keeps TunerContent clear of them.
            PhoneScreen(shownSize.value, SystemBars.NONE, shownFontScale.value) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
                    TunerContent(
                        shownState.value,
                        shownMode.value,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = {},
                        onRetry = {},
                        modifier = Modifier.fillMaxSize().background(BACKGROUND).testTag(WINDOW),
                        showSignalBars = true,
                        focusMode = shownCompact.value,
                        onFocusModeChange = {},
                    )
                }
            }
        }
    }

    private fun detected(mode: TunerMode = TunerMode.AUTO) = TunerUiState.Detected(
        mode = mode,
        tuning = Tunings.STANDARD,
        noteName = "A",
        octave = 2,
        frequencyHz = 108.47f,
        cents = -12.3f,
        targetString = if (mode == TunerMode.AUTO) 1 else null,
        inTune = false,
    )

    private val ruler get() = compose.onNodeWithTag(TunerTestTags.RULER)
    private val gauge get() = compose.onNodeWithTag(TunerTestTags.GAUGE)
    private val cents get() = compose.onNodeWithText(context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12))
    private val focusString get() = compose.onNodeWithTag(TunerTestTags.FOCUS_STRING, useUnmergedTree = true)
    private val signalBars get() = compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS_ICON)
    private val pickerChip get() = compose.onNodeWithTag(TunerTestTags.PICKER_CHIP)

    /**
     * Guitar mode, then chromatic mode: the ruler across the top, the lens wide under it, the word under the lens,
     * the string small at the end of its line in guitar mode, and nothing else shows (no pegs, chip or signal bars).
     */
    @Test
    fun onItsSideTheMeterFillsTheScreen() {
        show()
        forEachLook { case ->
            shownState.value = detected()
            shownMode.value = TunerMode.AUTO
            compose.waitForIdle()
            assertMeterFillsTheScreen("$case guitar")
            focusString.assertTextEquals("A2")
            shownState.value = detected(TunerMode.CHROMATIC)
            shownMode.value = TunerMode.CHROMATIC
            compose.waitForIdle()
            assertMeterFillsTheScreen("$case chromatic")
            focusString.assertDoesNotExist()
        }
    }

    /**
     * Runs [check] on a phone and a tablet on their side, in the light theme or also in the dark one
     * ([bothThemes]). Layout checks need only one: the fonts and the dial's shape are the same in both,
     * only the colours change, and each run here costs a quarter of a second.
     */
    private fun forEachLook(bothThemes: Boolean = false, check: (case: String) -> Unit) {
        val themes = if (bothThemes) listOf(false, true) else listOf(false)
        val cases = listOf(PHONE_LANDSCAPE, TABLET_LANDSCAPE).flatMap { size -> themes.map { size to it } }
        forEach(*cases.toTypedArray(), check = check)
    }

    /** Runs [check] on each window size, in the dark theme (true) or the light one. */
    private fun forEach(vararg cases: Pair<DpSize, Boolean>, check: (case: String) -> Unit) {
        for ((size, dark) in cases) {
            shownSize.value = size
            shownDark.value = dark
            compose.waitForIdle()
            check("${size.width} x ${size.height}, ${if (dark) "dark" else "light"}:")
        }
    }

    /**
     * The lens is wide (the window less 24 dp each side) and as tall as the window between the ruler and the word
     * allows less their gaps, or half its width where that is less (a tablet), the ruler across the window over it
     * at the top, the word with the cents under it at the start, all within the window, and nothing else of the
     * tuner shows.
     */
    private fun assertMeterFillsTheScreen(case: String) {
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val meter = gauge.assertIsDisplayed().getBoundsInRoot()
        val top = ruler.assertIsDisplayed().getBoundsInRoot()
        val word = compose.onNodeWithTag(TunerTestTags.WORD).assertIsDisplayed().getBoundsInRoot()
        assertTrue("$case: meter ${meter.width} wide in ${window.width}", abs((window.width - 48.dp - meter.width).value) < 2f)
        assertTrue("$case: ruler ${top.width} wide in ${window.width}", abs((window.width - top.width).value) < 2f)
        assertTrue(
            "$case: meter ${meter.width} x ${meter.height} in ${window.width} x ${window.height}, between the ruler $top and the word $word",
            meter.height >= word.top - top.bottom - FULL_HEIGHT_MARGINS - 1.dp || abs((meter.height - meter.width / 2).value) < 2f,
        )
        assertTrue("$case: at most half as high as wide", meter.height <= meter.width / 2 + 1.dp)
        assertTrue("$case: word $word at the start", abs((word.left - window.left - 24.dp).value) < 2f)
        assertInside(window, "meter", meter, case)
        assertInside(window, "ruler", top, case)
        assertInside(window, "word", word, case)
        assertTrue("$case: ruler $top over the lens $meter", top.bottom <= meter.top + 0.5.dp)
        assertTrue("$case: word $word under the lens $meter", word.top >= meter.bottom - 0.5.dp)
        cents.assertIsDisplayed()
        pickerChip.assertDoesNotExist()
        compose.onNodeWithText("E2").assertDoesNotExist()
        signalBars.assertDoesNotExist()
    }

    @Test
    fun withTheLargestFontTheWordAndATipAreWhollyShown() {
        shownFontScale.value = 2f
        show()
        forEachLook { case ->
            shownState.value = detected()
            compose.waitForIdle()
            assertWhollyShown(compose.onNodeWithTag(TunerTestTags.WORD_TEXT), "$case word", scroll = false)
            // The longest tip, in its line under the word.
            shownState.value = detected().copy(tip = ReceptionTip.TOO_LOUD)
            compose.waitForIdle()
            assertWhollyShown(compose.onNodeWithText(context.getString(R.string.tuner_tip_too_loud)), "$case tip", scroll = false)
            shownState.value = detected()
        }
    }

    /** A tap in the light theme, TalkBack in the dark one. */
    @Test
    fun aTapOrTalkBackBringsBackTheControls() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(TunerTestTags.COMPACT).performClick()
        compose.mainClock.advanceTimeBy(FADE_MS)
        pickerChip.assertIsDisplayed()
        compose.onNodeWithText("E2").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS + FADE_MS)
        pickerChip.assertDoesNotExist()
        shownDark.value = true
        compose.mainClock.advanceTimeBy(FADE_MS)
        // TalkBack hears the reading as one sentence, and the screen is a "Show controls" button.
        val spoken = "A 2, ${context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)}, ${context.getString(R.string.tuner_word_tighten)}"
        compose.onNode(hasContentDescription(spoken)).assertExists()
        compose.onNode(hasContentDescription(context.getString(R.string.tuner_show_controls)))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(FADE_MS)
        pickerChip.assertIsDisplayed()
    }

    /** Focus mode's exit button in the top end corner, over the ruler's end (where it draws no name), wholly shown, also at 2× font. */
    @Test
    fun theExitButtonSitsInTheTopEndCorner() {
        show()
        forEach(PHONE_LANDSCAPE to false, TABLET_LANDSCAPE to true) { case ->
            for (scale in listOf(1f, 2f)) {
                shownFontScale.value = scale
                compose.waitForIdle()
                val button = compose.onNodeWithTag(TunerTestTags.EXIT_FOCUS)
                assertWhollyShown(button, "$case ${scale}x exit button", scroll = false)
                val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
                val place = button.getBoundsInRoot()
                assertTrue("$case ${scale}x exit button at $place", abs((window.right - 16.dp - place.right).value) < 2f && place.top <= window.top + 12.dp)
                val rulerAt = ruler.getBoundsInRoot()
                assertTrue("$case ${scale}x exit button at $place, over the ruler's end $rulerAt", place.right <= rulerAt.right && place.top >= rulerAt.top)
            }
            shownFontScale.value = 1f
        }
    }

    /**
     * The meter (Classic, the default look; `LensLooksTest` and `WideLayoutTest` draw the others wide) on a phone
     * (light) and a tablet (dark) on their side. With the test below, each size is seen in both themes.
     */
    @Test
    fun onItsSideTheMeterCoversItsBox() {
        show()
        forEach(PHONE_LANDSCAPE to false, TABLET_LANDSCAPE to true) { case -> assertMeterCoversItsBox(case) }
    }

    /** The meter with 2× font, on a phone (dark) and a tablet (light) on their side. */
    @Test
    fun withTheLargestFontTheMeterCoversItsBox() {
        shownFontScale.value = 2f
        show()
        forEach(PHONE_LANDSCAPE to true, TABLET_LANDSCAPE to false) { case -> assertMeterCoversItsBox("$case 2x font") }
    }

    /**
     * The meter's wide box has the look's clear marks in it, straight on the page (no face since Phase 78), and TalkBack
     * finds nothing in it. (1.1 also
     * checked each other meter look against Classic here; they went in Phase 67, and the lens's looks get `LensLooksTest`.)
     */
    private fun assertMeterCoversItsBox(case: String) {
        compose.waitForIdle()
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val pixels = compose.onNodeWithTag(WINDOW).captureToImage().toPixelMap()
        val meter = gauge.getBoundsInRoot()
        val scale = pixels.width / window.width.value
        // The box, one pixel larger for antialiasing, in the window's pixels.
        val boxLeft = ((meter.left - window.left).value * scale).toInt() - 1
        val boxTop = ((meter.top - window.top).value * scale).toInt() - 1
        val boxRight = ((meter.right - window.left).value * scale).toInt() + 1
        val boxBottom = ((meter.bottom - window.top).value * scale).toInt() + 1
        val node = gauge.fetchSemanticsNode()
        assertNull("$case: TalkBack reads the meter", node.config.getOrNull(SemanticsProperties.ContentDescription))
        assertNull("$case: TalkBack reads the meter", node.config.getOrNull(SemanticsProperties.Text))
        assertTrue("$case: something inside the meter for TalkBack", node.children.isEmpty())
        // In the box (every other pixel): clear marks on the page, which is the colour seen most. Colours as ARGB
        // ints, as a million pixels add up.
        var inBox = 0
        val counts = HashMap<Int, Int>()
        for (y in evenFrom(boxTop)..boxBottom.coerceAtMost(pixels.height - 1) step 2) {
            for (x in evenFrom(boxLeft)..boxRight.coerceAtMost(pixels.width - 1) step 2) {
                val argb = pixels.argbAt(x, y)
                inBox++
                counts[argb] = (counts[argb] ?: 0) + 1
            }
        }
        val face = counts.maxBy { it.value }.key
        var details = 0
        for (y in boxTop.coerceAtLeast(0)..boxBottom.coerceAtMost(pixels.height - 1) step 2) {
            for (x in boxLeft.coerceAtLeast(0)..boxRight.coerceAtMost(pixels.width - 1) step 2) {
                if (channelDistance(pixels.argbAt(x, y), face) > 0.25f * 255) details++
            }
        }
        assertTrue("$case details ${details * 100 / inBox} %", details > inBox * 0.01f)
    }

    private fun PixelMap.argbAt(x: Int, y: Int): Int = buffer[bufferOffset + y * stride + x]

    /** The first even number from [from] on, and at least 0. */
    private fun evenFrom(from: Int): Int = from.coerceAtLeast(0).let { it + it % 2 }

    /** How far apart two ARGB colours are in their most different channel, 0..255. */
    private fun channelDistance(a: Int, b: Int): Int = maxOf(
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
        abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)),
        abs((a and 0xFF) - (b and 0xFF)),
    )

    /**
     * The signal bars stand out from what is behind them in the app's colours, light and dark, in the
     * wide layout with its controls (focus mode leaves them out).
     */
    @Test
    fun theSignalBarsAreDrawn() {
        shownCompact.value = false
        show()
        forEachLook(bothThemes = true) { case ->
            compose.waitForIdle()
            val pixels = signalBars.captureToImage().toPixelMap()
            // The top-left corner is above the shortest bar: what is behind them.
            val behind = pixels[0, 0]
            var drawn = 0
            for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
                val p = pixels[x, y]
                val distance = maxOf(abs(p.red - behind.red), abs(p.green - behind.green), abs(p.blue - behind.blue))
                if (distance > 0.3f) drawn++
            }
            val share = drawn / (pixels.width * pixels.height).toFloat()
            assertTrue("$case: bars cover ${(share * 100).toInt()} % clearly", share > 0.1f)
        }
    }

    private fun assertInside(window: DpRect, name: String, rect: DpRect, case: String) {
        // Half a dp of slack for rounding.
        assertTrue(
            "$case: $name $rect is outside the window $window",
            rect.left >= window.left - SLACK && rect.top >= window.top - SLACK &&
                rect.right <= window.right + SLACK && rect.bottom <= window.bottom + SLACK,
        )
    }

    companion object {
        private const val WINDOW = "window"

        /**
         * Each test takes up to about 3 s on the Xiaomi 12X; the first of a run, which also opens the
         * window, about 4.5 s. 10 s means it hangs, or the phone's screen is off.
         */
        private const val TEST_TIMEOUT_SECONDS = 10L

        /** A Galaxy S26 (360 dp tall) and a tablet on their side, full screen as focus mode shows the tuner. */
        private val PHONE_LANDSCAPE = DpSize(780.dp, 360.dp)
        private val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)

        /** The gaps above and below the wide lens (12 dp under the ruler, 12 dp over the word's line), and half a dp for rounding. */
        private val FULL_HEIGHT_MARGINS = 24.5.dp

        private val SLACK = 0.5.dp

        /** Long enough for the compact layout's cross-fade to finish. */
        private const val FADE_MS = 1_000L

        /** Behind the tuner, a colour Ink doesn't use. */
        private val BACKGROUND = Color(0xFF7F00FF)
    }
}
