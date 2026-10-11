package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import org.junit.Assert.assertFalse
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
 * The compact landscape layout in each of the seven styles: each style brings its own fonts and
 * dial, so the note column and the meter differ in size. On a phone and a tablet on their side
 * everything fits beside a big meter, clear of the palette and settings buttons in the corners; with
 * 2× font it can still be reached; the meter is drawn in light and dark; a tap (light) or TalkBack
 * (dark) brings back the controls. [TunerContentTest] covers the layout itself in one style.
 */
@RunWith(Parameterized::class)
class CompactLandscapeStylesTest(private val style: VisualStyle) {

    val compose = createComposeRule()

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
                        showTuneHint = true,
                        compactLandscape = true,
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

    private val note get() = compose.onNodeWithText("A")
    private val gauge get() = compose.onNodeWithTag(TunerTestTags.GAUGE)
    private val cents get() = compose.onNodeWithText(context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12))
    private val frequency get() = compose.onNodeWithText(context.getString(R.string.tuner_frequency, 108.47f))
    private val tuneUp get() = compose.onNodeWithText(context.getString(R.string.tuner_tune_up))
    private val targetString get() = compose.onNodeWithText("A2")
    private val modeSwitch get() = compose.onNodeWithText(context.getString(R.string.tuner_mode_guitar))

    /**
     * Guitar mode with the needle, the wave and the strobe, then chromatic mode with the wave and the
     * strobe (the cents and frequency under the note): all of it in view, nothing overlapping.
     */
    @Test
    fun onItsSideEveryDisplayFits() {
        show()
        forEachLook { case ->
            shownState.value = detected()
            shownMode.value = TunerMode.AUTO
            for (display in TunerDisplay.entries) {
                shownDisplay.value = display
                compose.waitForIdle()
                val parts = buildList {
                    add("note" to note)
                    add("tune hint" to tuneUp)
                    add("string" to targetString)
                    // With the wave and the strobe, those take the cents' place and the frequency is left out.
                    if (display == TunerDisplay.NEEDLE) {
                        add("cents" to cents)
                        add("frequency" to frequency)
                    }
                }
                assertBigMeterBeside(parts, "$case $display")
            }
            shownState.value = detected(TunerMode.CHROMATIC)
            shownMode.value = TunerMode.CHROMATIC
            for (display in listOf(TunerDisplay.WAVE, TunerDisplay.STROBE)) {
                shownDisplay.value = display
                compose.waitForIdle()
                targetString.assertDoesNotExist()
                val parts = listOf("note" to note, "tune hint" to tuneUp, "cents" to cents, "frequency" to frequency)
                assertBigMeterBeside(parts, "$case chromatic $display")
            }
        }
    }

    /**
     * Runs [check] on a phone and a tablet on their side, in the light theme or also in the dark one
     * ([bothThemes]). Layout checks need only one: a style's fonts and dial shape are the same in both
     * (`typographyFor(style)`), only the colours change, and each run here costs a quarter of a second.
     */
    private fun forEachLook(bothThemes: Boolean = false, check: (case: String) -> Unit) {
        for (size in listOf(PHONE_LANDSCAPE, TABLET_LANDSCAPE)) {
            for (dark in if (bothThemes) listOf(false, true) else listOf(false)) {
                shownSize.value = size
                shownDark.value = dark
                compose.waitForIdle()
                check("${size.width} x ${size.height}, ${if (dark) "dark" else "light"}:")
            }
        }
    }

    /**
     * The meter takes at least half the width, every one of [parts] sits beside it and in the window
     * (no scrolling), none overlap, and none reach into the corners where the palette and settings
     * buttons sit.
     */
    private fun assertBigMeterBeside(parts: List<Pair<String, SemanticsNodeInteraction>>, case: String) {
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val meter = gauge.assertIsDisplayed().getBoundsInRoot()
        assertTrue("$case: meter ${meter.width} wide in ${window.width}", meter.width >= window.width * 0.5f)
        assertInside(window, "meter", meter, case)
        val bounds = listOf("meter" to meter) + parts.map { (name, node) -> name to node.assertIsDisplayed().getBoundsInRoot() }
        for ((name, rect) in bounds.drop(1)) {
            assertInside(window, name, rect, case)
            assertTrue("$case: $name should be right of the meter", rect.left >= meter.right)
            for (corner in corners(window)) assertFalse("$case: $name $rect is under a corner button", rect.overlaps(corner))
        }
        for (i in bounds.indices) {
            for (j in i + 1 until bounds.size) {
                val (a, first) = bounds[i]
                val (b, second) = bounds[j]
                assertFalse("$case: $a $first overlaps $b $second", first.overlaps(second))
            }
        }
    }

    @Test
    fun withTheLargestFontEverythingCanStillBeReached() {
        shownFontScale.value = 2f
        show()
        forEachLook {
            for (display in TunerDisplay.entries) {
                shownDisplay.value = display
                compose.waitForIdle()
                gauge.assertIsDisplayed()
                note.performScrollTo().assertIsDisplayed()
                tuneUp.performScrollTo().assertIsDisplayed()
                targetString.performScrollTo().assertIsDisplayed()
                if (display == TunerDisplay.NEEDLE) {
                    cents.performScrollTo().assertIsDisplayed()
                    frequency.performScrollTo().assertIsDisplayed()
                }
            }
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
        compose.onNodeWithText(context.getString(R.string.tuning_standard)).assertIsDisplayed()
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

    /**
     * The meter is drawn in the style's own colours, light or dark: clearly there, and not one solid
     * block (Retro's backlit panel covers nearly all of it, so the scale and needle must show on it).
     */
    @Test
    fun theMeterIsDrawn() {
        show()
        forEachLook(bothThemes = true) { case ->
            val pixels = gauge.captureToImage().toPixelMap()
            val sampled = buildList {
                for (x in 0 until pixels.width step 3) for (y in 0 until pixels.height step 3) add(pixels[x, y])
            }
            val marked = sampled.filterNot { it.near(BACKGROUND) }
            val markedShare = marked.size / sampled.size.toFloat()
            assertTrue("$case meter marks ${(markedShare * 100).toInt()} %", markedShare > 0.02f)
            // The most common drawn colour (the face or the scale) doesn't make up everything drawn.
            val face = marked.groupingBy { it.copy(alpha = 1f) }.eachCount().maxBy { it.value }.key
            val detailShare = marked.count { !it.near(face) } / sampled.size.toFloat()
            assertTrue("$case meter details ${(detailShare * 100).toInt()} %", detailShare > 0.01f)
        }
    }

    private fun Color.near(other: Color): Boolean =
        abs(red - other.red) < 0.02f && abs(green - other.green) < 0.02f && abs(blue - other.blue) < 0.02f

    private fun assertInside(window: DpRect, name: String, rect: DpRect, case: String) {
        // Half a dp of slack for rounding.
        assertTrue(
            "$case: $name $rect is outside the window $window",
            rect.left >= window.left - SLACK && rect.top >= window.top - SLACK &&
                rect.right <= window.right + SLACK && rect.bottom <= window.bottom + SLACK,
        )
    }

    /** Where `TunerScreen` puts the palette and settings buttons: 48 dp, 8 dp in from the top corners. */
    private fun corners(window: DpRect) = listOf(
        DpRect(window.left, window.top, window.left + CORNER, window.top + CORNER),
        DpRect(window.right - CORNER, window.top, window.right, window.top + CORNER),
    )

    private fun DpRect.overlaps(other: DpRect) =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = VisualStyle.entries

        private const val WINDOW = "window"

        /** Each test takes a second or two on the S26; 10 s means it hangs. */
        private const val TEST_TIMEOUT_SECONDS = 10L

        /** A Galaxy S26 on its side (360 dp tall), without the status bar, and a tablet on its side. */
        private val PHONE_LANDSCAPE = DpSize(780.dp, 330.dp)
        private val TABLET_LANDSCAPE = DpSize(1280.dp, 760.dp)

        /** The corner button's 48 dp plus its 8 dp inset. */
        private val CORNER = 56.dp
        private val SLACK = 0.5.dp

        /** Long enough for the compact layout's cross-fade to finish. */
        private const val FADE_MS = 1_000L

        /** Behind the tuner, a colour none of the styles uses. */
        private val BACKGROUND = Color(0xFF7F00FF)
    }
}
