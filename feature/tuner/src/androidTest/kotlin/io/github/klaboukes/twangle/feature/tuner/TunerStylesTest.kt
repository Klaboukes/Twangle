package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.TimeUnit

/**
 * The tuner upright and in its full layout on its side, in each of the six styles, light and dark:
 * each style brings its own fonts and string-button shapes, so the parts differ in size. Upright and
 * on its side everything fits without scrolling and nothing overlaps or reaches under the settings
 * button; at the owner's 1.25× text on the Xiaomi 12X too. At 2× text every part can be scrolled to
 * and is then wholly shown, and the string buttons' names (solfège flats, the longest) fit. TalkBack
 * hears the reading once, skips the meter and the wave, and hears the mode switch and every string
 * by name with which one is picked. [TunerContentTest] covers each behaviour in one style, and
 * [CompactLandscapeStylesTest] the compact layout on its side.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class TunerStylesTest(private val style: VisualStyle) {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Set these to change what the window shows without composing it again.
    private val shownState = mutableStateOf<TunerUiState>(detected())
    private val shownDisplay = mutableStateOf(TunerDisplay.NEEDLE)
    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
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
                        TunerMode.AUTO,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = {},
                        onRetry = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        display = shownDisplay.value,
                        showTuneHint = true,
                        showSignalBars = true,
                        compactLandscape = false,
                        onStrumCheckChange = {},
                    )
                }
            }
        }
    }

    private fun detected() = TunerUiState.Detected(
        mode = TunerMode.AUTO,
        tuning = Tunings.STANDARD,
        noteName = "A",
        octave = 2,
        frequencyHz = 108.47f,
        cents = -12.3f,
        targetString = 1,
        inTune = false,
    )

    private val twelveCentsFlat get() = context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)
    private val gauge get() = compose.onNodeWithTag(TunerTestTags.GAUGE)
    private val wave get() = compose.onNodeWithTag(TunerTestTags.WAVE, useUnmergedTree = true)
    private val guitar get() = compose.onNodeWithTag(TunerTestTags.mode(TunerMode.AUTO))
    private val chromatic get() = compose.onNodeWithTag(TunerTestTags.mode(TunerMode.CHROMATIC))
    private val strings = listOf("E2", "A2", "D3", "G3", "B3", "E4")

    /** Every part of the screen with [display], by name: the meter or the wave first. */
    private fun parts(display: TunerDisplay): List<Pair<String, SemanticsNodeInteraction>> = buildList {
        add("mode switch, guitar" to guitar)
        add("mode switch, chromatic" to chromatic)
        if (display == TunerDisplay.NEEDLE) add("meter" to gauge) else add("wave" to wave)
        add("note" to compose.onNodeWithText("A"))
        if (display == TunerDisplay.NEEDLE) {
            add("cents" to compose.onNodeWithText(twelveCentsFlat))
            add("frequency" to compose.onNodeWithText(context.getString(R.string.tuner_frequency, 108.47f)))
        }
        add("tune hint" to compose.onNodeWithText(context.getString(R.string.tuner_tune_up)))
        add("signal bars" to compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS))
        add("instrument and tuning button" to compose.onNodeWithTag(TunerTestTags.PICKER))
        add("strum check switch" to compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE))
        strings.forEach { add("string $it" to compose.onNodeWithText(it)) }
    }

    /** Upright on a narrow phone at normal text, light and dark, with the meter and with the wave. */
    @Test
    fun uprightEverythingFitsWithoutScrolling() {
        show()
        forEachCase(PHONE_UPRIGHT, 1f) { case -> assertAllInView(case) }
    }

    /** The owner's phone and text size: the Xiaomi 12X upright at 1.25× text. */
    @Test
    fun uprightAtTheOwnersTextSizeEverythingFitsWithoutScrolling() {
        shownFontScale.value = OWNER_FONT_SCALE
        show()
        forEachCase(XIAOMI_UPRIGHT, OWNER_FONT_SCALE) { case -> assertAllInView(case) }
    }

    /** The full layout on a phone on its side: the readout beside the controls, all in view. */
    @Test
    fun onItsSideEverythingFitsWithoutScrolling() {
        shownSize.value = PHONE_LANDSCAPE
        show()
        forEachCase(PHONE_LANDSCAPE, 1f) { case ->
            assertAllInView(case)
            val note = compose.onNodeWithText("A").getBoundsInRoot()
            for (label in strings) {
                val chip = compose.onNodeWithText(label).getBoundsInRoot()
                if (chip.left < note.right) fail("$case: string $label should be right of the note")
            }
        }
    }

    @Test
    fun uprightAtTwiceTheTextSizeEveryPartIsWhollyShown() {
        shownFontScale.value = 2f
        show()
        forEachCase(PHONE_UPRIGHT, 2f) { case -> assertAllWhollyShown(case) }
    }

    @Test
    fun onItsSideAtTwiceTheTextSizeEveryPartIsWhollyShown() {
        shownSize.value = PHONE_LANDSCAPE
        shownFontScale.value = 2f
        show()
        forEachCase(PHONE_LANDSCAPE, 2f) { case -> assertAllWhollyShown(case) }
    }

    /**
     * The string buttons have a fixed size and let their names grow only to 1.3×: the longest names
     * (solfège flats, "Sol♭3") still fit in this style's buttons at 2× text, upright and on its side.
     */
    @Test
    fun theLongestStringNamesFitTheirButtonsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        shownState.value = TunerUiState.Listening(TunerMode.AUTO, Tunings.HALF_STEP_DOWN, notation = Notation.SOLFEGE)
        show()
        val labels = listOf("Mi♭2", "La♭2", "Re♭3", "Sol♭3", "Si♭3", "Mi♭4")
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
            for (label in labels) {
                // The name in its button, unmerged: its own text box, not the button's.
                assertWhollyShown(compose.onNode(hasText(label), useUnmergedTree = true), "$style $size: $label")
            }
        }
    }

    /**
     * TalkBack, light and dark: the reading is one sentence, the meter and the wave say nothing, the
     * mode switch and strings have their names, a click action and which one is picked.
     */
    @Test
    fun talkBackHearsEveryPartByName() {
        show()
        for (dark in listOf(false, true)) {
            shownDark.value = dark
            for (display in TunerDisplay.entries) {
                shownDisplay.value = display
                compose.waitForIdle()
                val case = "$style ${if (dark) "dark" else "light"} $display"
                val spoken = "A 2, $twelveCentsFlat, ${context.getString(R.string.tuner_spoken_tune_up)}"
                compose.onNode(hasContentDescription(spoken)).assertExists()
                val picture = if (display == TunerDisplay.NEEDLE) gauge else wave
                val node = picture.fetchSemanticsNode()
                assertNull("$case: TalkBack reads the picture", node.config.getOrNull(SemanticsProperties.ContentDescription))
                assertNull("$case: TalkBack reads the picture", node.config.getOrNull(SemanticsProperties.Text))
                assertTrue("$case: something inside the picture for TalkBack", node.children.isEmpty())
                guitar.assertHasClickAction().assertIsSelected()
                chromatic.assertHasClickAction().assertIsNotSelected()
                listOf("E 2", "A 2", "D 3", "G 3", "B 3", "E 4").forEachIndexed { index, name ->
                    val chip = compose.onNode(hasContentDescription(name)).assertHasClickAction()
                    if (index == 1) chip.assertIsSelected() else chip.assertIsNotSelected()
                }
                compose.onNodeWithTag(TunerTestTags.PICKER).assertHasClickAction()
            }
        }
    }

    /** Runs [check] with the meter and the wave, in the light and the dark theme, at [size] and [fontScale]. */
    private fun forEachCase(size: DpSize, fontScale: Float, check: (case: String) -> Unit) {
        shownSize.value = size
        shownFontScale.value = fontScale
        for (dark in listOf(false, true)) {
            shownDark.value = dark
            for (display in TunerDisplay.entries) {
                shownDisplay.value = display
                compose.waitForIdle()
                check("$style ${if (dark) "dark" else "light"} $display ${size.width} x ${size.height} at ${fontScale}x:")
            }
        }
    }

    /**
     * Every part is on screen without scrolling, inside the window, clear of the settings button's
     * corner, and no two overlap.
     */
    private fun assertAllInView(case: String) {
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val corner = DpRect(window.right - CORNER, window.top, window.right, window.top + CORNER)
        val bounds = parts(shownDisplay.value).map { (name, node) -> name to node.assertIsDisplayed().getBoundsInRoot() }
        for ((name, rect) in bounds) {
            if (rect.left < window.left - SLACK || rect.top < window.top - SLACK || rect.right > window.right + SLACK || rect.bottom > window.bottom + SLACK) {
                fail("$case $name $rect is outside the window $window")
            }
            if (rect.overlaps(corner)) fail("$case $name $rect is under the settings button")
        }
        for (i in bounds.indices) {
            for (j in i + 1 until bounds.size) {
                val (a, first) = bounds[i]
                val (b, second) = bounds[j]
                // The mode switch's two segments share their border.
                if (a.startsWith("mode switch") && b.startsWith("mode switch")) continue
                if (first.overlaps(second)) fail("$case $a $first overlaps $b $second")
            }
        }
    }

    /** Every part can be scrolled to and is then wholly shown. */
    private fun assertAllWhollyShown(case: String) {
        for ((name, node) in parts(shownDisplay.value)) {
            // The meter and the wave are pictures: scrolled to, they must be wholly in view too.
            assertWhollyShown(node, "$case $name")
        }
    }

    private fun DpRect.overlaps(other: DpRect) =
        left < other.right - SLACK && other.left < right - SLACK && top < other.bottom - SLACK && other.top < bottom - SLACK

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 10L

        /** A narrow phone (a Galaxy S26) without the status and navigation bars, upright and on its side. */
        private val PHONE_UPRIGHT = DpSize(360.dp, 730.dp)
        private val PHONE_LANDSCAPE = DpSize(780.dp, 330.dp)

        /** The owner's Xiaomi 12X upright (393 x 873 dp) without the status bar and the button bar. */
        private val XIAOMI_UPRIGHT = DpSize(393.dp, 793.dp)
        private const val OWNER_FONT_SCALE = 1.25f

        /** The settings button's 48 dp plus its 8 dp inset, in the window's top end corner. */
        private val CORNER = 56.dp
        private val SLACK = 0.5.dp
    }
}
