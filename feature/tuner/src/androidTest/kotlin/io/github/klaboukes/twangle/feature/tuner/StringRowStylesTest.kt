package io.github.klaboukes.twangle.feature.tuner

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.feature.tuner.components.instrumentNameRes
import io.github.klaboukes.twangle.feature.tuner.components.tuningNameRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The string row from a 4-string bass to an 8-string guitar, in each of the six styles: upright, in
 * the full layout and the compact layout on its side, at normal and 2× text. Every button is in the
 * window, wholly shown with its whole name, clear of its neighbours; every one can be tapped (also
 * just beside it) and TalkBack names it. The string-lock tip points at a bass's first string.
 * [TunerStylesTest] covers the six-string guitar.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class StringRowStylesTest(private val style: VisualStyle) {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    // Set these to change what the window shows without composing it again.
    private val shownTuning = mutableStateOf(Instruments.GUITAR_8.defaultTuning)
    private val shownNotation = mutableStateOf(Notation.SHARPS)
    private val shownLayout = mutableStateOf(Layout.UPRIGHT)
    private val shownFontScale = mutableStateOf(1f)
    private val shownLocale = mutableStateOf("en")
    private val clicked = mutableListOf<Int>()

    private enum class Layout(val size: DpSize, val compact: Boolean) {
        UPRIGHT(DpSize(360.dp, 730.dp), compact = false),
        FULL_ON_ITS_SIDE(DpSize(780.dp, 330.dp), compact = false),
        COMPACT_ON_ITS_SIDE(DpSize(780.dp, 330.dp), compact = true),
    }

    private fun show(listening: Boolean = false, tip: Boolean = false) {
        compose.setContent {
            val layout = shownLayout.value
            val override = DeviceConfigurationOverride.ForcedSize(layout.size) then
                DeviceConfigurationOverride.FontScale(shownFontScale.value) then
                DeviceConfigurationOverride.Locales(LocaleList(shownLocale.value))
            DeviceConfigurationOverride(override) {
                TwangleTheme(style = style, darkTheme = false, animate = false) {
                    val tuning = shownTuning.value
                    val notation = shownNotation.value
                    val first = tuning.strings.first()
                    val state = if (listening) {
                        TunerUiState.Listening(TunerMode.AUTO, tuning, notation = notation)
                    } else {
                        TunerUiState.Detected(
                            mode = TunerMode.AUTO,
                            tuning = tuning,
                            noteName = notation.nameIn(tuning, first.pitchClass),
                            octave = first.octave,
                            frequencyHz = 100f,
                            cents = -12.3f,
                            targetString = 0,
                            inTune = false,
                            notation = notation,
                        )
                    }
                    TunerContent(
                        state,
                        TunerMode.AUTO,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = { clicked += it },
                        onRetry = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        showStringLockTip = tip,
                        compactLandscape = layout.compact,
                        // As in the app: the strum check's switch shares the row with the instrument-and-tuning button.
                        onStrumCheckChange = {},
                    )
                }
            }
        }
    }

    /** The names on [tuning]'s buttons, as the row shows them ("F♯1"). */
    private fun labels(tuning: Tuning, notation: Notation) = tuning.strings.map { notation.nameIn(tuning, it) }

    /** Every button's name is in the window, wholly shown, and no two buttons overlap. */
    @Test
    fun everyRowFitsAtNormalText() {
        show()
        for (layout in Layout.entries) {
            shownLayout.value = layout
            for (tuning in TUNINGS) {
                shownTuning.value = tuning
                compose.waitForIdle()
                assertRowFits("$style $layout ${tuning.id} at 1x", tuning, Notation.SHARPS, scroll = false)
            }
        }
    }

    /** The longest names (solfège) on the most strings, and a 5-string bass, at 2× text: scrolled to, every name is whole. */
    @Test
    fun theLongestNamesFitAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        shownNotation.value = Notation.SOLFEGE
        show()
        for (layout in Layout.entries) {
            shownLayout.value = layout
            for (tuning in listOf(Instruments.GUITAR_8.defaultTuning, Instruments.BASS_5.defaultTuning)) {
                shownTuning.value = tuning
                compose.waitForIdle()
                assertRowFits("$style $layout ${tuning.id} solfège at 2x", tuning, Notation.SOLFEGE, scroll = true)
            }
        }
    }

    /**
     * On the most strings, upright and in the full layout on its side: every button can be tapped,
     * a tap just beside a button (between two) still reaches one of them, and TalkBack names each.
     */
    @Test
    fun everyStringCanBeTappedAndHeard() {
        val tuning = Instruments.GUITAR_8.defaultTuning
        val spoken = listOf("F sharp 1", "B 1", "E 2", "A 2", "D 3", "G 3", "B 3", "E 4")
        show(listening = true)
        val labels = labels(tuning, Notation.SHARPS)
        for (layout in listOf(Layout.UPRIGHT, Layout.FULL_ON_ITS_SIDE)) {
            shownLayout.value = layout
            compose.waitForIdle()
            labels.forEachIndexed { index, label ->
                val chip = compose.onNodeWithText(label).assertHasClickAction()
                val description = chip.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
                assertEquals("$style $layout: TalkBack's name for $label", listOf(spoken[index]), description)
                clicked.clear()
                chip.performClick()
                assertEquals("$style $layout: tapping $label", listOf(index), clicked)
            }
            for (index in 0 until labels.size - 1) {
                // In the root's pixels: a forced size larger than the phone gets a smaller density.
                val left = compose.onNodeWithText(labels[index]).fetchSemanticsNode().boundsInRoot
                val right = compose.onNodeWithText(labels[index + 1]).fetchSemanticsNode().boundsInRoot
                val between = Offset((left.right + right.left) / 2f, left.center.y)
                clicked.clear()
                compose.onRoot().performTouchInput { click(between) }
                compose.waitForIdle()
                if (clicked.size != 1 || clicked.single() !in index..index + 1) {
                    fail("$style $layout: a tap between ${labels[index]} and ${labels[index + 1]} reached $clicked")
                }
            }
        }
    }

    /** Upright, a bass's four strings spread wider than a guitar's six: the tip in the corner still points at the first. */
    @Test
    fun theTipPointsAtABasssFirstString() {
        shownTuning.value = Instruments.BASS_4.defaultTuning
        show(listening = true, tip = true)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tip = compose.onNodeWithText(context.getString(R.string.tuner_string_lock_tip)).assertIsDisplayed().getBoundsInRoot()
        val picker = compose.onNodeWithTag(TunerTestTags.PICKER).getBoundsInRoot()
        val chips = labels(shownTuning.value, Notation.SHARPS).map { compose.onNodeWithText(it).getBoundsInRoot() }
        chips.forEachIndexed { index, chip ->
            if (tip.overlaps(chip)) fail("$style: the tip $tip covers string $index $chip")
        }
        val first = chips.first()
        // In the corner beside the button (not above it, where a wide button leaves no room), its
        // pointer (32 dp from its start) lands on the first string.
        if (tip.right <= picker.left) {
            val pointer = tip.left + 32.dp
            assertTrue("$style: the tip points at $pointer, the first string is ${first.left}..${first.right}", pointer in first.left..first.right)
        }
    }

    /**
     * Every instrument with its tunings (the guitar's longest names), in English and Dutch, upright
     * and in the full layout on its side: the instrument-and-tuning button keeps to one line, its
     * label wholly shown ("8-snarige gitaar · Standaard"), the button in the window, above the strings.
     */
    @Test
    fun everyInstrumentsButtonKeepsToOneLineInEnglish() = assertButtonsKeepToOneLine("en")

    @Test
    fun everyInstrumentsButtonKeepsToOneLineInDutch() = assertButtonsKeepToOneLine("nl")

    private fun assertButtonsKeepToOneLine(locale: String) {
        shownLocale.value = locale
        show(listening = true)
        val resources = resourcesFor(locale)
        for (layout in listOf(Layout.UPRIGHT, Layout.FULL_ON_ITS_SIDE)) {
            shownLayout.value = layout
            for (tuning in PICKER_TUNINGS) {
                shownTuning.value = tuning
                compose.waitForIdle()
                val case = "$style $locale $layout ${tuning.id}"
                val label = assertButtonFits(case, buttonLabel(resources, tuning), scroll = false)
                val layouts = mutableListOf<TextLayoutResult>()
                label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals("$case: lines on the button", 1, layouts.single().lineCount)
            }
        }
    }

    /** At 2x text the longest Dutch names, scrolled to, are wholly shown (they may wrap), the button in the window. */
    @Test
    fun theLongestPickerNamesFitAtTwiceTheTextSize() {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        show(listening = true)
        val resources = resourcesFor("nl")
        for (layout in listOf(Layout.UPRIGHT, Layout.FULL_ON_ITS_SIDE)) {
            shownLayout.value = layout
            for (tuning in listOf(Tunings.HALF_STEP_DOWN, Instruments.GUITAR_8.defaultTuning, Instruments.BASS_5.defaultTuning)) {
                shownTuning.value = tuning
                compose.waitForIdle()
                assertButtonFits("$style nl $layout ${tuning.id} at 2x", buttonLabel(resources, tuning), scroll = true)
            }
        }
    }

    /** The button's [label] wholly shown, the button in the window and above the strings; the label's node. */
    private fun assertButtonFits(case: String, label: String, scroll: Boolean): SemanticsNodeInteraction {
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val labelNode = compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(TunerTestTags.PICKER)), useUnmergedTree = true)
        assertWhollyShown(labelNode, "$case: $label", scroll = scroll)
        val button = compose.onNodeWithTag(TunerTestTags.PICKER).getBoundsInRoot()
        if (button.left < window.left - SLACK || button.right > window.right + SLACK) fail("$case: the button $button is outside the window $window")
        val strum = compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE).getBoundsInRoot()
        val besideOrAbove = strum.left >= button.right - SLACK || strum.bottom <= button.top + SLACK
        if (!besideOrAbove) fail("$case: the button $button reaches the strum check's switch $strum")
        if (strum.right > window.right + SLACK) fail("$case: the strum check's switch $strum is outside the window $window")
        // Where they are, also when scrolled out of view (bounds in the root are cut to the window).
        val buttonNode = compose.onNodeWithTag(TunerTestTags.PICKER).fetchSemanticsNode()
        val stringNode = compose.onNodeWithText(labels(shownTuning.value, Notation.SHARPS).first()).fetchSemanticsNode()
        val buttonBottom = buttonNode.positionInRoot.y + buttonNode.size.height
        if (buttonBottom > stringNode.positionInRoot.y + 1f) fail("$case: the button ends at $buttonBottom px, the strings start at ${stringNode.positionInRoot.y}")
        return labelNode
    }

    /** What the button says for [tuning] in the language of [resources], e.g. "Gitaar · Halve toon lager". */
    private fun buttonLabel(resources: Resources, tuning: Tuning): String {
        val (tuningName, instrumentName) = pickerNames(resources, tuning)
        return "$instrumentName · $tuningName"
    }

    /** The tuning's and its instrument's names in the language of [resources]. */
    private fun pickerNames(resources: Resources, tuning: Tuning): Pair<String, String> {
        val instrument = Instruments.ofTuning(tuning.id)!!
        return resources.getString(tuningNameRes(tuning.id)!!) to resources.getString(instrumentNameRes(instrument.id)!!)
    }

    private fun resourcesFor(locale: String): Resources {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(locale)) },
        ).resources
    }

    /** Each of [tuning]'s buttons in the window, wholly shown, clear of the others. */
    private fun assertRowFits(case: String, tuning: Tuning, notation: Notation, scroll: Boolean) {
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val chips = labels(tuning, notation).map { label ->
            // The name in its button, unmerged: its own text box, not the button's.
            assertWhollyShown(compose.onNode(hasText(label), useUnmergedTree = true), "$case: $label", scroll = scroll)
            label to compose.onNodeWithText(label).assertIsDisplayed().getBoundsInRoot()
        }
        for ((label, chip) in chips) {
            if (chip.left < window.left - SLACK || chip.right > window.right + SLACK) fail("$case: $label $chip is outside the window $window")
        }
        for (i in 0 until chips.size - 1) {
            val (a, first) = chips[i]
            val (b, second) = chips[i + 1]
            if (first.overlaps(second)) fail("$case: $a $first overlaps $b $second")
            if (second.left < first.left) fail("$case: $b is left of $a")
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
        private val SLACK = 0.5.dp

        /** 4, 5, 7 and 8 strings, and the ukulele's re-entrant four. */
        private val TUNINGS = listOf(
            Instruments.BASS_4.defaultTuning,
            Instruments.BASS_5.defaultTuning,
            Instruments.UKULELE.defaultTuning,
            Instruments.GUITAR_7.defaultTuning,
            Instruments.GUITAR_8.defaultTuning,
        )

        /** Each instrument's tunings, with the guitar's longest names and the ukulele's low G. */
        private val PICKER_TUNINGS = Instruments.all.map { it.defaultTuning } +
            listOf(Tunings.HALF_STEP_DOWN, Tunings.FULL_STEP_DOWN, Instruments.UKULELE.tunings.last())
    }
}
