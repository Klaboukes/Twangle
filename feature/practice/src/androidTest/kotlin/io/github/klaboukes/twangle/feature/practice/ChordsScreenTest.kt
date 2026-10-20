package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.then
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
import io.github.klaboukes.twangle.core.settings.PracticeChart
import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.Navigation
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
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
 * The Chords tool (Phase 32) in each style, light and dark: it opens on the saved chord, the name
 * button opens one panel of roots and chord types that picks the chord (and reports it to be saved),
 * staying open for both picks, the chart follows the picked tuning and notation, ‹ › and a swipe
 * step through the shapes, TalkBack hears the name as a heading and a list, the panel's grids as
 * radio groups and the chart in words, and the whole tool fits: without scrolling upright and on its
 * side at normal text, wholly shown at 2× text, the panel too.
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
    private val shownNavigation = mutableStateOf(Navigation.BUTTONS)

    /** The phone's bars around the window: the status bar, and on its side the navigation bar too. */
    private fun bars() = SystemBars.tab(onItsSide = shownSize.value.width > shownSize.value.height, shownNavigation.value)
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
            PhoneScreen(shownSize.value, bars(), shownFontScale.value, shownLocale.value) {
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

    private fun picker() = node(PracticeTestTags.NAME_PICKER)

    /** The name button says [symbol], the quiet line under it [words]. */
    private fun assertName(symbol: String, words: String) {
        picker().assertTextEquals(symbol)
        node(PracticeTestTags.NAME_WORDS).assertTextEquals(words)
    }

    /** Opens the panel and waits for it: a window of its own. */
    private fun openPanel() {
        picker().performClick()
        compose.waitUntil(WAIT_MS) { exists(PracticeTestTags.root(PitchClass.C)) }
    }

    /** Closes the panel with Back and waits for it to go: Back with it shut would close the activity. */
    private fun closePanel() {
        Espresso.pressBack()
        compose.waitUntil(WAIT_MS) { !exists(PracticeTestTags.root(PitchClass.C)) }
    }

    /** Opens the panel, taps each of [options] in it (it stays open), and closes it. */
    private fun pick(vararg options: String) {
        openPanel()
        for (option in options) {
            node(option).performClick()
            compose.waitForIdle()
            assertTrue("a pick leaves the panel open", exists(option))
        }
        closePanel()
    }

    /** Opens the panel, checks that each of [options] is picked, and closes it. */
    private fun assertPicked(vararg options: String) {
        openPanel()
        for (option in options) node(option).assertIsSelected()
        closePanel()
    }

    @Test
    fun opensOnTheSavedChordAndThePanelPicksAnother() {
        show(PitchClass.A, ChordQuality.MINOR)
        assertEquals(
            "A minor, shape 1 of 6, E 2 string, not played, A 2 string, open, D 3 string, fret 2, finger 2, E, " +
                "G 3 string, fret 2, finger 3, A, B 3 string, fret 1, finger 1, C, E 4 string, open",
            awaitChart("A minor, shape 1 of"),
        )
        assertName("Am", "A minor")
        assertFalse("the panel is closed", exists(PracticeTestTags.root(PitchClass.A)) || exists(PracticeTestTags.type(ChordQuality.MINOR)))
        assertPicked(PracticeTestTags.root(PitchClass.A), PracticeTestTags.type(ChordQuality.MINOR))

        pick(PracticeTestTags.root(PitchClass.G))
        awaitChart("G minor, shape 1 of")
        pick(PracticeTestTags.type(ChordQuality.DOMINANT_SEVENTH))
        awaitChart("G seventh, shape 1 of")
        assertName("G7", "G seventh")
        openPanel()
        node(PracticeTestTags.root(PitchClass.G)).assertIsSelected()
        node(PracticeTestTags.root(PitchClass.A)).assertIsNotSelected()
        node(PracticeTestTags.type(ChordQuality.DOMINANT_SEVENTH)).assertIsSelected()
        closePanel()
        assertEquals("the picks are passed on to be saved", listOf(PitchClass.G), savedRoots)
        assertEquals(listOf(ChordQuality.DOMINANT_SEVENTH), savedQualities)
    }

    /** The panel stays open for a root and a type in one go; the name and the chart follow each pick at once. */
    @Test
    fun bothPicksInOneGoShowAtOnce() {
        show()
        awaitChart("C major")
        openPanel()
        node(PracticeTestTags.root(PitchClass.D)).performClick()
        assertName("D", "D major")
        awaitChart("D major, shape 1 of")
        node(PracticeTestTags.type(ChordQuality.MINOR)).performClick()
        assertName("Dm", "D minor")
        awaitChart("D minor, shape 1 of")
        node(PracticeTestTags.root(PitchClass.D)).assertIsSelected()
        node(PracticeTestTags.type(ChordQuality.MINOR)).assertIsSelected()
        closePanel()
        assertEquals(listOf(PitchClass.D), savedRoots)
        assertEquals(listOf(ChordQuality.MINOR), savedQualities)
    }

    @Test
    fun talkBackHearsTheNameAsAListAndThePanelAsRadioButtons() {
        show(PitchClass.A_SHARP, ChordQuality.MAJOR)
        awaitChart("B flat major")
        val list = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)
        picker().assert(list).assert(isHeading()).assert(hasContentDescription("B flat major")).assertTextEquals("B♭")
        assertTrue("the words under it are skipped", node(PracticeTestTags.NAME_WORDS).fetchSemanticsNode().config.contains(SemanticsProperties.HideFromAccessibility))
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        openPanel()
        node(PracticeTestTags.root(PitchClass.A_SHARP)).assert(radio).assert(hasContentDescription("B flat"))
        node(PracticeTestTags.root(PitchClass.F_SHARP)).assert(radio).assert(hasContentDescription("F sharp"))
        node(PracticeTestTags.type(ChordQuality.MINOR_SEVENTH)).assert(radio).assert(hasContentDescription("B flat minor seventh"))
        closePanel()
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
        pick(PracticeTestTags.type(ChordQuality.MINOR))
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
        assertName("Re", "Re major")
        openPanel()
        node(PracticeTestTags.type(ChordQuality.MAJOR)).assertTextEquals("Re")
        node(PracticeTestTags.type(ChordQuality.MINOR)).assertTextEquals("Rem")
        node(PracticeTestTags.type(ChordQuality.SUS2)).performClick()
        assertName("Resus2", "Re suspended second")
        closePanel()
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
            val window = bars().safeArea(size)
            for (tag in listOf(PracticeTestTags.SWITCH, PracticeTestTags.INSTRUMENT_BUTTON, PracticeTestTags.TUNING, PracticeTestTags.NAME_PICKER, PracticeTestTags.NEXT)) {
                assertWhollyShown(node(tag), "$case $tag", scroll = false)
                val bounds = node(tag).getBoundsInRoot()
                assertTrue("$case $tag inside the window $bounds", bounds.top >= window.top && bounds.bottom <= window.bottom)
            }
            assertThePanel(case, wide = size == PHONE_LANDSCAPE)
            val chart = chart().getBoundsInRoot()
            // The short phone leaves the chart near its smallest, 1 dp a unit (156 dp).
            assertTrue("$case a big chart ${chart.height}", chart.height >= if (size == PHONE_UPRIGHT) 156.dp else 170.dp)
            val pager = node(PracticeTestTags.NEXT).getBoundsInRoot()
            val choice = node(PracticeTestTags.chart(PracticeChart.CHORDS)).getBoundsInRoot()
            val name = picker().getBoundsInRoot()
            assertTrue("$case chart, then pager", chart.bottom <= pager.top + 1.dp)
            if (size == PHONE_LANDSCAPE) {
                val middle = (window.left + window.right) / 2
                assertTrue("$case the choice in the start half", choice.right <= middle)
                assertTrue("$case name and chart in the end half", name.left >= middle && chart.left >= middle)
            } else {
                assertTrue("$case the choice over the name", choice.bottom <= name.top + 1.dp)
            }
            assertTrue("$case the name over the chart", name.bottom <= chart.top + 1.dp)
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
            for (tag in listOf(PracticeTestTags.TUNING, PracticeTestTags.NAME_PICKER, PracticeTestTags.COUNT, PracticeTestTags.NEXT)) {
                assertWhollyShown(node(tag), "$style nl $size $tag at 2x")
            }
            compose.assertEveryTextWhollyShown("$style nl $size at 2x:")
            assertThePanel("$style nl $size at 2x:", wide = size == PHONE_LANDSCAPE, texts = true)
        }
    }

    /**
     * The panel under the name (where not [wide]): the roots in two rows of six (C to F, F♯ to B), then the chord types
     * in four rows of four, each wholly shown (scrolling the panel where it must; with [texts], every text in
     * it too); Back closes it and leaves the name as it was.
     */
    private fun assertThePanel(case: String, wide: Boolean, texts: Boolean = false) {
        fun top(tag: String) = node(tag).fetchSemanticsNode().positionInRoot.y
        // The panel is a popup, a window of its own: against the name button on the screen.
        val button = picker().fetchSemanticsNode().let { it.positionOnScreen.y + it.size.height }
        val name = picker().fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        openPanel()
        val first = top(PracticeTestTags.root(PitchClass.C))
        val second = top(PracticeTestTags.root(PitchClass.F_SHARP))
        assertTrue("$case roots in two rows of six", top(PracticeTestTags.root(PitchClass.F)) == first && second > first && top(PracticeTestTags.root(PitchClass.B)) == second)
        // On its side it opens over the start half; where can't be told here: the forced size scales the screen, not the popup's window.
        if (!wide) assertTrue("$case the panel under the name", node(PracticeTestTags.root(PitchClass.C)).fetchSemanticsNode().positionOnScreen.y >= button)
        val types = top(PracticeTestTags.type(ChordQuality.MAJOR))
        assertTrue("$case the types under the roots", types > second)
        // C to Cmaj7, Cm7 to C7sus4, C6 to C9, Cdim to Caug.
        val rows = ChordQuality.entries.chunked(4).map { row -> row.map { top(PracticeTestTags.type(it)) } }
        assertTrue("$case four chord types a row, in four rows", rows.size == 4 && rows.all { row -> row.size == 4 && row.all { it == row[0] } })
        assertTrue("$case the rows one under the other", rows.zipWithNext().all { (above, below) -> below[0] > above[0] })
        for (pitch in PitchClass.entries) assertWhollyShown(node(PracticeTestTags.root(pitch)), "$case root $pitch")
        for (quality in ChordQuality.entries) assertWhollyShown(node(PracticeTestTags.type(quality)), "$case type $quality")
        if (texts) compose.assertEveryTextWhollyShown("$case panel:")
        closePanel()
        assertFalse("$case Back closes the panel", exists(PracticeTestTags.type(ChordQuality.MAJOR)))
        picker().assert(hasContentDescription(name))
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
                // The panel is a window of its own: Back with it shut would close the activity, and a tap while the last
                // one is still going away only shuts that one (the Retro run, 2026-10-19). So wait for it both ways.
                openPanel()
                compose.assertEveryTextWhollyShown("$style $language $size at 2x, panel:")
                compose.assertNoWordBroken("$style $language $size at 2x, panel:")
                closePanel()
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
