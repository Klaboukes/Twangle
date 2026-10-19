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
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * The compact landscape layout (focus mode) in each of the six styles: each style brings its own fonts
 * and dial. On a phone and a tablet on their side, full screen (no rail, no status bar, Phase 40), the
 * meter fills the screen with the small note in its top start corner; with 2× font the note and a tip
 * are wholly shown; a tap (light) or TalkBack (dark) brings back the controls. Every meter look, at its
 * biggest here, is clearly drawn inside its box in the style's colours, light and dark, skipped by
 * TalkBack, and moves nothing else on the screen, also at 2× font.
 * [TunerContentTest] covers the layout itself in one style.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class CompactLandscapeStylesTest(private val style: VisualStyle) {

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
    private val shownDisplay = mutableStateOf(TunerDisplay.NEEDLE)
    private val shownSize = mutableStateOf(PHONE_LANDSCAPE)
    private val shownFontScale = mutableStateOf(1f)
    private val shownDark = mutableStateOf(false)
    private val shownLook = mutableStateOf(MeterLook.DEFAULT)
    private val shownCompact = mutableStateOf(true)

    private fun show() {
        compose.setContent {
            val override = DeviceConfigurationOverride.ForcedSize(shownSize.value) then
                DeviceConfigurationOverride.FontScale(shownFontScale.value)
            DeviceConfigurationOverride(override) {
                TwangleTheme(style = style, darkTheme = shownDark.value, animate = false) {
                    TunerContent(
                        shownState.value,
                        shownMode.value,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = {},
                        onRetry = {},
                        modifier = Modifier.fillMaxSize().background(BACKGROUND).testTag(WINDOW),
                        display = shownDisplay.value,
                        meterLook = shownLook.value,
                        showTuneHint = true,
                        showSignalBars = true,
                        compactLandscape = shownCompact.value,
                        onStrumCheckChange = {},
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

    private val note get() = compose.onNodeWithTag(TunerTestTags.FOCUS_NOTE)
    private val gauge get() = compose.onNodeWithTag(TunerTestTags.GAUGE)
    private val cents get() = compose.onNodeWithText(context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12))
    private val frequency get() = compose.onNodeWithText(context.getString(R.string.tuner_frequency, 108.47f))
    private val targetString get() = compose.onNodeWithText("A2")
    private val signalBars get() = compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS_ICON)
    private val modeSwitch get() = compose.onNodeWithText(context.getString(R.string.tuner_mode_strings))

    /**
     * Guitar mode with the needle and the wave, then chromatic mode: the meter fills the screen, the
     * note in its corner, and nothing else shows (no cents, frequency, strings or signal bars).
     */
    @Test
    fun onItsSideTheMeterFillsTheScreen() {
        show()
        forEachLook { case ->
            shownState.value = detected()
            shownMode.value = TunerMode.AUTO
            for (display in TunerDisplay.entries) {
                shownDisplay.value = display
                compose.waitForIdle()
                assertMeterFillsTheScreen("$case $display")
            }
            shownState.value = detected(TunerMode.CHROMATIC)
            shownMode.value = TunerMode.CHROMATIC
            compose.waitForIdle()
            assertMeterFillsTheScreen("$case chromatic")
        }
    }

    /**
     * Runs [check] on a phone and a tablet on their side, in the light theme or also in the dark one
     * ([bothThemes]). Layout checks need only one: a style's fonts and dial shape are the same in both
     * (`typographyFor(style)`), only the colours change, and each run here costs a quarter of a second.
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
     * The meter is as big as the window allows (as tall as it less its margins, or [METER_SHARE] of its
     * width), the note in its top start corner, within the window, and nothing else of the tuner shows.
     */
    private fun assertMeterFillsTheScreen(case: String) {
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val meter = gauge.assertIsDisplayed().getBoundsInRoot()
        assertTrue(
            "$case: meter ${meter.width} x ${meter.height} in ${window.width} x ${window.height}",
            meter.height >= window.height - FULL_HEIGHT_MARGINS || meter.width >= window.width * METER_SHARE,
        )
        assertInside(window, "meter", meter, case)
        val corner = note.assertIsDisplayed().getBoundsInRoot()
        assertInside(window, "note", corner, case)
        assertTrue(
            "$case: note $corner not in the top start corner",
            corner.right <= window.left + window.width * CORNER_SHARE && corner.bottom <= window.top + window.height * CORNER_SHARE,
        )
        // Nothing else: the meter shows how far off the note is.
        cents.assertDoesNotExist()
        frequency.assertDoesNotExist()
        targetString.assertDoesNotExist()
        signalBars.assertDoesNotExist()
    }

    @Test
    fun withTheLargestFontTheNoteAndATipAreWhollyShown() {
        shownFontScale.value = 2f
        show()
        forEachLook { case ->
            shownState.value = detected()
            compose.waitForIdle()
            assertWhollyShown(compose.onNodeWithText("A"), "$case note", scroll = false)
            // The longest tip, in its line under the note.
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
        modeSwitch.assertIsDisplayed()
        compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS + FADE_MS)
        modeSwitch.assertDoesNotExist()
        shownDark.value = true
        compose.mainClock.advanceTimeBy(FADE_MS)
        // TalkBack hears the reading as one sentence, and the screen is a "Show controls" button.
        val spoken = "A 2, ${context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)}, ${context.getString(R.string.tuner_spoken_tune_up)}"
        compose.onNode(hasContentDescription(spoken)).assertExists()
        compose.onNode(hasContentDescription(context.getString(R.string.tuner_show_controls)))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(FADE_MS)
        modeSwitch.assertIsDisplayed()
    }

    /** A quiet "Tap to show the controls" when the compact layout appears, not for TalkBack, gone by itself. */
    @Test
    fun aQuietLineSaysATapShowsTheControls() {
        compose.mainClock.autoAdvance = false
        show()
        val hint = compose.onNodeWithText(context.getString(R.string.tuner_compact_tap_hint))
        hint.assertIsDisplayed()
        // In the top end corner, which every meter look leaves free.
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val place = hint.getBoundsInRoot()
        assertTrue("hint at $place", place.right >= window.right - 24.dp && place.top <= window.top + 24.dp)
        assertTrue("TalkBack hears the hint", hint.fetchSemanticsNode().config.contains(SemanticsProperties.HideFromAccessibility))
        // Still there just before its time is up (no flicker), then gone.
        compose.mainClock.advanceTimeBy(COMPACT_TAP_HINT_SHOWN_MS - FADE_MS)
        hint.assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2 * FADE_MS)
        hint.assertDoesNotExist()
    }

    /**
     * Every look on a phone (light) and a tablet (dark) on their side. With the test below, each size
     * is seen in both themes; each capture of the window costs about a sixth of a second.
     */
    @Test
    fun onItsSideEveryMeterLookStaysInItsBox() {
        show()
        forEach(PHONE_LANDSCAPE to false, TABLET_LANDSCAPE to true) { case -> assertEveryMeterLookInItsBox(case) }
    }

    /** Every look with 2× font, on a phone (dark) and a tablet (light) on their side. */
    @Test
    fun withTheLargestFontEveryMeterLookStaysInItsBox() {
        shownFontScale.value = 2f
        show()
        forEach(PHONE_LANDSCAPE to true, TABLET_LANDSCAPE to false) { case -> assertEveryMeterLookInItsBox("$case 2x font") }
    }

    /**
     * Shows each meter look in turn, against Classic: every other part of the screen keeps its place
     * and looks as it did (nothing moved, nothing drawn outside the meter's 2:1 box but a look's soft
     * glow fading out round it, which `MeterLooksTest` checks), the box
     * is covered by the look with clear marks on it (Classic too, and Retro's backlit panel must show
     * its scale and marker on it), and TalkBack finds nothing in the meter.
     */
    private fun assertEveryMeterLookInItsBox(case: String) {
        shownLook.value = MeterLook.CLASSIC
        // The tap hint gone first: it fades out on its own.
        compose.mainClock.advanceTimeBy(COMPACT_TAP_HINT_SHOWN_MS + FADE_MS)
        compose.waitForIdle()
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val places = placesOnScreen()
        val classic = compose.onNodeWithTag(WINDOW).captureToImage().toPixelMap()
        val meter = places.getValue("meter")
        val scale = classic.width / window.width.value
        // The 2:1 box, one pixel larger for antialiasing, in the window's pixels.
        val boxLeft = ((meter.left - window.left).value * scale).toInt() - 1
        val boxTop = ((meter.top - window.top).value * scale).toInt() - 1
        val boxRight = ((meter.right - window.left).value * scale).toInt() + 1
        val boxBottom = ((meter.top - window.top + meter.width / 2).value * scale).toInt() + 1
        for (look in MeterLook.entries) {
            shownLook.value = look
            compose.waitForIdle()
            val lookCase = "$case $look"
            assertEquals("$lookCase: the other parts moved", places, placesOnScreen())
            val node = gauge.fetchSemanticsNode()
            assertNull("$lookCase: TalkBack reads the meter", node.config.getOrNull(SemanticsProperties.ContentDescription))
            assertNull("$lookCase: TalkBack reads the meter", node.config.getOrNull(SemanticsProperties.Text))
            assertTrue("$lookCase: something inside the meter for TalkBack", node.children.isEmpty())
            val pixels = if (look == MeterLook.CLASSIC) classic else compose.onNodeWithTag(WINDOW).captureToImage().toPixelMap()
            if (look != MeterLook.CLASSIC) {
                assertSameOutsideTheBox(lookCase, pixels, classic, boxLeft, boxTop, boxRight, boxBottom)
            }
            // In the box (every other pixel): the calm field covers most of it (all of it that is on screen),
            // with clear marks on it. Colours as ARGB ints, as a million pixels a look add up.
            val background = BACKGROUND.toArgb()
            var inBox = 0
            var drawn = 0
            val counts = HashMap<Int, Int>()
            for (y in evenFrom(boxTop)..boxBottom.coerceAtMost(pixels.height - 1) step 2) {
                for (x in evenFrom(boxLeft)..boxRight.coerceAtMost(pixels.width - 1) step 2) {
                    val argb = pixels.argbAt(x, y)
                    inBox++
                    if (channelDistance(argb, background) >= 0.02f * 255) drawn++
                    counts[argb] = (counts[argb] ?: 0) + 1
                }
            }
            assertTrue("$lookCase covers ${drawn * 100 / inBox} % of its box", drawn > inBox * 0.75f)
            val face = counts.maxBy { it.value }.key
            var details = 0
            for (y in boxTop.coerceAtLeast(0)..boxBottom.coerceAtMost(pixels.height - 1) step 2) {
                for (x in boxLeft.coerceAtLeast(0)..boxRight.coerceAtMost(pixels.width - 1) step 2) {
                    if (channelDistance(pixels.argbAt(x, y), face) > 0.25f * 255) details++
                }
            }
            assertTrue("$lookCase details ${details * 100 / inBox} %", details > inBox * 0.01f)
        }
        shownLook.value = MeterLook.DEFAULT
    }

    /** Where the meter and the note in its corner are. */
    private fun placesOnScreen(): Map<String, DpRect> = buildMap {
        put("meter", gauge.getBoundsInRoot())
        put("note", note.getBoundsInRoot())
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
     * Outside the box [left]..[right] x [top]..[bottom], [pixels] is the same as [reference], give or
     * take a rounding step: nothing was drawn there and nothing moved.
     */
    private fun assertSameOutsideTheBox(
        case: String,
        pixels: PixelMap,
        reference: PixelMap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val a = pixels.buffer
        val b = reference.buffer
        for (y in 0 until pixels.height) {
            val rowA = pixels.bufferOffset + y * pixels.stride
            val rowB = reference.bufferOffset + y * reference.stride
            val boxRow = y in top..bottom
            for (x in 0 until pixels.width) {
                if (boxRow && x in left..right) continue
                val pa = a[rowA + x]
                val pb = b[rowB + x]
                val distance = if (pa == pb) 0 else channelDistance(pa, pb)
                if (distance > GLOW_STEP) {
                    throw AssertionError("$case drew outside its box ($left..$right x $top..$bottom), or moved something, at $x, $y (by $distance)")
                }
            }
        }
    }

    /**
     * The signal bars stand out from what is behind them in the style's colours, light and dark, in the
     * full layout on its side (focus mode leaves them out).
     */
    @Test
    fun theSignalBarsAreDrawn() {
        shownCompact.value = false
        show()
        forEachLook(bothThemes = true) { case ->
            for (display in TunerDisplay.entries) {
                shownDisplay.value = display
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
                assertTrue("$case $display: bars cover ${(share * 100).toInt()} % clearly", share > 0.1f)
            }
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
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        private const val WINDOW = "window"

        /**
         * Each test takes up to about 3 s on the Xiaomi 12X; the first of a run, which also opens the
         * window, about 4.5 s. 10 s means it hangs, or the phone's screen is off.
         */
        private const val TEST_TIMEOUT_SECONDS = 10L

        /** A Galaxy S26 (360 dp tall) and a tablet on their side, full screen as focus mode shows the tuner. */
        private val PHONE_LANDSCAPE = DpSize(780.dp, 360.dp)
        private val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)

        /** The full-screen meter's margins above and below it (8 dp each), and half a dp for rounding. */
        private val FULL_HEIGHT_MARGINS = 16.5.dp

        /** The note stays within this share of the window's width and height from its top start corner. */
        private const val CORNER_SHARE = 0.25f

        private val SLACK = 0.5.dp

        /** The meter's least share of the window's width where the width, not the height, limits it (a tablet). */
        private const val METER_SHARE = 0.85f

        /** Long enough for the compact layout's cross-fade to finish. */
        private const val FADE_MS = 1_000L

        /**
         * Outside the meter's box a look may differ from Classic by this much (of 255) at most: a soft
         * glow (Eclipse's sun halo reaches past the box), not a drawn mark or a moved part.
         */
        private const val GLOW_STEP = 24

        /** Behind the tuner, a colour none of the styles uses. */
        private val BACKGROUND = Color(0xFF7F00FF)
    }
}
