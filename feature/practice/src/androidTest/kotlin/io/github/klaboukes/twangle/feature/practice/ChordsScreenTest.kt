package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.espresso.Espresso
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertNoWordBroken
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.shippedLanguages
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The Chords tool (Phase 32) in each style, light and dark: it opens on the saved chord, the root
 * and type pickers open their grids and pick the chord (and report it to be saved), the chart
 * follows the picked tuning and notation, ‹ › and a swipe step through the shapes, TalkBack hears
 * the pickers as lists with what is picked, their grids as radio groups and the chart in words, and
 * the whole tool fits: without scrolling upright and on its side at normal text, wholly shown at 2×
 * text, the grids too.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class ChordsScreenTest(private val style: VisualStyle) {

    private val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val shownSize = mutableStateOf(XIAOMI)
    private val shownFontScale = mutableStateOf(1f)
    private val shownDark = mutableStateOf(false)
    private val shownLocale = mutableStateOf("en")
    private val shownTuning = mutableStateOf(Tunings.STANDARD)
    private val shownNotation = mutableStateOf(Notation.SHARPS)
    private val savedRoots = mutableListOf<PitchClass>()
    private var instrumentTaps = 0
    private val savedQualities = mutableListOf<ChordQuality>()

    private fun show(root: PitchClass = PitchClass.C, quality: ChordQuality = ChordQuality.MAJOR) {
        compose.setContent {
            val override = DeviceConfigurationOverride.ForcedSize(shownSize.value) then
                DeviceConfigurationOverride.FontScale(shownFontScale.value) then
                DeviceConfigurationOverride.Locales(LocaleList(shownLocale.value))
            DeviceConfigurationOverride(override) {
                TwangleTheme(style = style, darkTheme = shownDark.value, animate = false) {
                    ChordsScreen(
                        tuning = shownTuning.value,
                        tuningName = "Guitar · Standard",
                        spokenTuningName = "Guitar, Standard",
                        notation = shownNotation.value,
                        savedRoot = root,
                        savedQuality = quality,
                        onRootChange = { savedRoots += it },
                        onQualityChange = { savedQualities += it },
                        header = { PracticeToolSwitch(PracticeTool.CHORDS, {}, listOf(PracticeTool.METRONOME, PracticeTool.CHORDS)) },
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        onPickInstrument = { instrumentTaps++ },
                    )
                }
            }
        }
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag)
    private fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun chart() = node(PracticeTestTags.CHART)
    private fun spokenChart(): String = chart().fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString()

    /** Waits for the chart to say [start] (the shapes are worked out off the main thread), then gives all it says. */
    private fun awaitChart(start: String): String {
        compose.waitUntil(WAIT_MS) { spokenChart().startsWith(start) }
        return spokenChart()
    }

    /** Opens [picker]'s grid and taps [option] in it, which closes the grid. */
    private fun pick(picker: String, option: String) {
        node(picker).performClick()
        node(option).performClick()
        compose.waitForIdle()
        assertFalse("a pick closes the grid", exists(option))
    }

    /** Opens [picker]'s grid, checks that [option] is the one picked, and closes the grid by tapping it again. */
    private fun assertPicked(picker: String, option: String) {
        node(picker).performClick()
        node(option).assertIsSelected()
        node(option).performClick()
        compose.waitForIdle()
    }

    @Test
    fun opensOnTheSavedChordAndThePickersPickAnother() {
        show(PitchClass.A, ChordQuality.MINOR)
        assertEquals(
            "A minor, shape 1 of 6, E 2 string, not played, A 2 string, open, D 3 string, fret 2, finger 2, E, " +
                "G 3 string, fret 2, finger 3, A, B 3 string, fret 1, finger 1, C, E 4 string, open",
            awaitChart("A minor, shape 1 of"),
        )
        node(PracticeTestTags.ROOT_PICKER).assertTextEquals("A")
        node(PracticeTestTags.TYPE_PICKER).assertTextEquals("Am")
        node(PracticeTestTags.TITLE).assertTextEquals("A minor")
        assertFalse("the grids are closed", exists(PracticeTestTags.root(PitchClass.A)) || exists(PracticeTestTags.type(ChordQuality.MINOR)))
        assertPicked(PracticeTestTags.ROOT_PICKER, PracticeTestTags.root(PitchClass.A))
        assertPicked(PracticeTestTags.TYPE_PICKER, PracticeTestTags.type(ChordQuality.MINOR))

        pick(PracticeTestTags.ROOT_PICKER, PracticeTestTags.root(PitchClass.G))
        awaitChart("G minor, shape 1 of")
        pick(PracticeTestTags.TYPE_PICKER, PracticeTestTags.type(ChordQuality.DOMINANT_SEVENTH))
        awaitChart("G seventh, shape 1 of")
        node(PracticeTestTags.ROOT_PICKER).assertTextEquals("G")
        node(PracticeTestTags.TYPE_PICKER).assertTextEquals("G7")
        node(PracticeTestTags.TITLE).assertTextEquals("G seventh")
        node(PracticeTestTags.ROOT_PICKER).performClick()
        node(PracticeTestTags.root(PitchClass.G)).assertIsSelected()
        node(PracticeTestTags.root(PitchClass.A)).assertIsNotSelected()
        node(PracticeTestTags.root(PitchClass.G)).performClick()
        assertPicked(PracticeTestTags.TYPE_PICKER, PracticeTestTags.type(ChordQuality.DOMINANT_SEVENTH))
        assertEquals("the picks are passed on to be saved", listOf(PitchClass.G), savedRoots)
        assertEquals(listOf(ChordQuality.DOMINANT_SEVENTH), savedQualities)
    }

    @Test
    fun talkBackHearsThePickersAsListsAndTheirGridsAsRadioButtons() {
        show(PitchClass.A_SHARP, ChordQuality.MAJOR)
        awaitChart("B flat major")
        val list = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)
        node(PracticeTestTags.ROOT_PICKER).assert(list).assert(hasContentDescription("Root, B flat"))
        node(PracticeTestTags.TYPE_PICKER).assert(list).assert(hasContentDescription("Chord type, B flat major"))
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        node(PracticeTestTags.ROOT_PICKER).performClick()
        node(PracticeTestTags.root(PitchClass.A_SHARP)).assert(radio).assert(hasContentDescription("B flat"))
        node(PracticeTestTags.root(PitchClass.F_SHARP)).assert(radio).assert(hasContentDescription("F sharp"))
        node(PracticeTestTags.root(PitchClass.A_SHARP)).performClick()
        node(PracticeTestTags.TYPE_PICKER).performClick()
        node(PracticeTestTags.type(ChordQuality.MINOR_SEVENTH)).assert(radio).assert(hasContentDescription("B flat minor seventh"))
        node(PracticeTestTags.type(ChordQuality.MAJOR)).performClick()
        node(PracticeTestTags.PREVIOUS).assert(hasContentDescription("Previous shape"))
        node(PracticeTestTags.NEXT).assert(hasContentDescription("Next shape"))
        node(PracticeTestTags.INSTRUMENT_BUTTON)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Guitar, Standard"))
        assertTrue("the name beside it is skipped", node(PracticeTestTags.TUNING).fetchSemanticsNode().config.contains(SemanticsProperties.HideFromAccessibility))
        assertTrue("a chart is a polite live region", chart().fetchSemanticsNode().config.contains(SemanticsProperties.LiveRegion))
    }

    @Test
    fun theArrowsAndASwipeStepThroughTheShapes() {
        show()
        awaitChart("C major, shape 1 of 6")
        node(PracticeTestTags.PREVIOUS).assertIsNotEnabled()
        node(PracticeTestTags.COUNT).assertTextEquals("1 of 6")
        node(PracticeTestTags.NEXT).assertIsEnabled().performClick()
        awaitChart("C major, shape 2 of 6")
        node(PracticeTestTags.COUNT).assertTextEquals("2 of 6")
        chart().performTouchInput { swipeLeft() }
        awaitChart("C major, shape 3 of 6")
        chart().performTouchInput { swipeRight() }
        awaitChart("C major, shape 2 of 6")
        node(PracticeTestTags.PREVIOUS).assertIsEnabled().performClick()
        awaitChart("C major, shape 1 of 6")
        repeat(5) { node(PracticeTestTags.NEXT).performClick() }
        awaitChart("C major, shape 6 of 6")
        node(PracticeTestTags.NEXT).assertIsNotEnabled()
        // Another chord starts at its first shape again.
        pick(PracticeTestTags.TYPE_PICKER, PracticeTestTags.type(ChordQuality.MINOR))
        awaitChart("C minor, shape 1 of")
    }

    @Test
    fun theChartFollowsTheTuningAndTheNotation() {
        show(PitchClass.D, ChordQuality.MAJOR)
        awaitChart("D major")
        shownTuning.value = Tunings.DADGAD
        compose.waitUntil(WAIT_MS) { spokenChart().contains("D 2 string") && spokenChart().contains("A 3 string") }
        shownTuning.value = Instruments.UKULELE.defaultTuning
        compose.waitUntil(WAIT_MS) { Regex(" string, ").findAll(spokenChart()).count() == 4 }
        // A tuning of the player's own (open C, low to high C G C G C E): its shapes, not standard's.
        shownTuning.value = Tuning("custom_open_c", listOf(36, 43, 48, 55, 60, 64).map { Note(it) })
        compose.waitUntil(WAIT_MS) { spokenChart().contains("C 2 string") }
        shownNotation.value = Notation.SOLFEGE
        awaitChart("Re major")
        node(PracticeTestTags.TITLE).assertTextEquals("Re major")
        node(PracticeTestTags.TYPE_PICKER).assertTextEquals("Re")
        node(PracticeTestTags.TYPE_PICKER).performClick()
        node(PracticeTestTags.type(ChordQuality.MINOR)).assertTextEquals("Rem")
    }

    @Test
    fun itFitsWithoutScrollingUprightAndOnItsSideInLightAndDark() {
        show()
        for (dark in listOf(false, true)) for (size in listOf(XIAOMI, PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownDark.value = dark
            shownSize.value = size
            compose.waitForIdle()
            awaitChart("C major")
            val case = "$style ${if (dark) "dark" else "light"} $size:"
            val window = node(WINDOW).getBoundsInRoot()
            for (tag in listOf(PracticeTestTags.SWITCH, PracticeTestTags.INSTRUMENT_BUTTON, PracticeTestTags.TUNING, PracticeTestTags.ROOT_PICKER, PracticeTestTags.TYPE_PICKER, PracticeTestTags.TITLE, PracticeTestTags.NEXT)) {
                assertWhollyShown(node(tag), "$case $tag", scroll = false)
                val bounds = node(tag).getBoundsInRoot()
                assertTrue("$case $tag inside the window $bounds", bounds.top >= window.top && bounds.bottom <= window.bottom)
            }
            assertTheGrids(case)
            val chart = chart().getBoundsInRoot()
            // The short phone leaves the chart near its smallest, 1 dp a unit (156 dp).
            assertTrue("$case a big chart ${chart.height}", chart.height >= if (size == PHONE_UPRIGHT) 156.dp else 170.dp)
            val pager = node(PracticeTestTags.NEXT).getBoundsInRoot()
            val pickers = node(PracticeTestTags.TYPE_PICKER).getBoundsInRoot()
            val title = node(PracticeTestTags.TITLE).getBoundsInRoot()
            assertTrue("$case chart, then pager", chart.bottom <= pager.top + 1.dp)
            if (size == PHONE_LANDSCAPE) {
                val middle = (window.left + window.right) / 2
                assertTrue("$case pickers in the start half", pickers.right <= middle)
                assertTrue("$case title and chart in the end half", title.left >= middle && chart.left >= middle)
            } else {
                assertTrue("$case pickers over the title", pickers.bottom <= title.top + 1.dp)
            }
            assertTrue("$case the title over the chart", title.bottom <= chart.top + 1.dp)
        }
    }

    @Test
    fun dutchAtTwiceTheTextSizeIsWhollyShown() {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        shownNotation.value = Notation.SOLFEGE
        show(PitchClass.G_SHARP, ChordQuality.SUS4)
        for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
            awaitChart("La")
            for (tag in listOf(PracticeTestTags.TUNING, PracticeTestTags.ROOT_PICKER, PracticeTestTags.TYPE_PICKER, PracticeTestTags.TITLE, PracticeTestTags.COUNT, PracticeTestTags.NEXT)) {
                assertWhollyShown(node(tag), "$style nl $size $tag at 2x")
            }
            compose.assertEveryTextWhollyShown("$style nl $size at 2x:")
            assertTheGrids("$style nl $size at 2x:", texts = true)
        }
    }

    /**
     * The roots' grid in two rows of six (C to F, F♯ to B) and the chord types' four and three, each
     * under the pickers and wholly shown (with [texts], every text in it too); a tap on the picked
     * root closes the first, Back the second.
     */
    private fun assertTheGrids(case: String, texts: Boolean = false) {
        fun top(tag: String) = node(tag).getBoundsInRoot().top
        // The grid is a popup, a window of its own: against the pickers on the screen.
        fun screenTop(tag: String) = node(tag).fetchSemanticsNode().positionOnScreen.y
        val pickers = node(PracticeTestTags.ROOT_PICKER).fetchSemanticsNode().let { it.positionOnScreen.y + it.size.height }
        val root = node(PracticeTestTags.ROOT_PICKER).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        node(PracticeTestTags.ROOT_PICKER).performClick()
        val first = top(PracticeTestTags.root(PitchClass.C))
        val second = top(PracticeTestTags.root(PitchClass.F_SHARP))
        assertTrue("$case roots in two rows of six", top(PracticeTestTags.root(PitchClass.F)) == first && second > first && top(PracticeTestTags.root(PitchClass.B)) == second)
        assertTrue("$case the roots under the pickers", screenTop(PracticeTestTags.root(PitchClass.C)) >= pickers)
        for (pitch in PitchClass.entries) assertWhollyShown(node(PracticeTestTags.root(pitch)), "$case root $pitch", scroll = false)
        if (texts) compose.assertEveryTextWhollyShown("$case roots:")
        val picked = PitchClass.entries.first { compose.onNodeWithTag(PracticeTestTags.root(it)).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Selected) { false } }
        node(PracticeTestTags.root(picked)).performClick()
        compose.waitForIdle()
        assertFalse("$case a tap closes the grid", exists(PracticeTestTags.root(PitchClass.C)))
        node(PracticeTestTags.ROOT_PICKER).assert(hasContentDescription(root))

        node(PracticeTestTags.TYPE_PICKER).performClick()
        val types = top(PracticeTestTags.type(ChordQuality.MAJOR))
        assertTrue("$case four chord types a row", top(PracticeTestTags.type(ChordQuality.MAJOR_SEVENTH)) == types && top(PracticeTestTags.type(ChordQuality.MINOR_SEVENTH)) > types)
        assertTrue("$case the types under the pickers", screenTop(PracticeTestTags.type(ChordQuality.MAJOR)) >= pickers)
        for (quality in ChordQuality.entries) assertWhollyShown(node(PracticeTestTags.type(quality)), "$case type $quality", scroll = false)
        if (texts) compose.assertEveryTextWhollyShown("$case types:")
        Espresso.pressBack()
        compose.waitForIdle()
        assertFalse("$case Back closes the grid", exists(PracticeTestTags.type(ChordQuality.MAJOR)))
    }

    /** Every language at 2× text, upright on a 360 dp phone and on its side, the grids too. Only with `--languages`. */
    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        show(PitchClass.G_SHARP, ChordQuality.MINOR_SEVENTH)
        for (language in shippedLanguages) {
            shownLocale.value = language
            for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                compose.waitForIdle()
                compose.waitUntil(WAIT_MS) { spokenChart().isNotEmpty() }
                compose.assertEveryTextWhollyShown("$style $language $size at 2x:")
                compose.assertNoWordBroken("$style $language $size at 2x:")
                // The grid is a window of its own: Back with it shut would close the activity, and a tap while the last
                // one is still going away only shuts that one (the Retro run, 2026-10-19). So wait for it both ways.
                val types = PracticeTestTags.type(ChordQuality.MAJOR)
                node(PracticeTestTags.TYPE_PICKER).performClick()
                compose.waitUntil(WAIT_MS) { exists(types) }
                compose.assertEveryTextWhollyShown("$style $language $size at 2x, types:")
                compose.assertNoWordBroken("$style $language $size at 2x, types:")
                Espresso.pressBack()
                compose.waitUntil(WAIT_MS) { !exists(types) }
            }
        }
    }


    /** The instrument button before the tuning line, and the line itself, open the instrument and tuning panel. */
    @Test
    fun theInstrumentButtonAndTheTuningLineOpenThePanel() {
        show()
        node(PracticeTestTags.INSTRUMENT_BUTTON).assertIsDisplayed().performClick()
        node(PracticeTestTags.TUNING).performClick()
        assertEquals(2, instrumentTaps)
    }
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 30L
        private const val WAIT_MS = 5_000L

        /** The Xiaomi 12X less the status and navigation bars, a short narrow phone (360 x 720 dp with navigation buttons) less the bars, on its side less the rail, and a 320 dp phone. */
        private val XIAOMI = DpSize(393.dp, 760.dp)
        private val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
        private val NARROW_UPRIGHT = DpSize(320.dp, 640.dp)
    }
}
