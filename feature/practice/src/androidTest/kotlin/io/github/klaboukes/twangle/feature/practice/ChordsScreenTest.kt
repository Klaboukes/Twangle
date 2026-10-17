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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The Chords tool (Phase 32) in each style, light and dark: it opens on the saved chord, the root
 * and type buttons pick the chord (and report it to be saved), the chart follows the picked tuning
 * and notation, ‹ › and a swipe step through the shapes, TalkBack hears the pickers as radio groups
 * and the chart in words, and the whole tool fits: without scrolling upright and on its side at
 * normal text, wholly shown at 2× text.
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
                    )
                }
            }
        }
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag)
    private fun chart() = node(PracticeTestTags.CHART)
    private fun spokenChart(): String = chart().fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString()

    /** Waits for the chart to say [start] (the shapes are worked out off the main thread), then gives all it says. */
    private fun awaitChart(start: String): String {
        compose.waitUntil(WAIT_MS) { spokenChart().startsWith(start) }
        return spokenChart()
    }

    @Test
    fun opensOnTheSavedChordAndTheButtonsPickAnother() {
        show(PitchClass.A, ChordQuality.MINOR)
        assertEquals(
            "A minor, shape 1 of 6, E 2 string, not played, A 2 string, open, D 3 string, fret 2, finger 2, E, " +
                "G 3 string, fret 2, finger 3, A, B 3 string, fret 1, finger 1, C, E 4 string, open",
            awaitChart("A minor, shape 1 of"),
        )
        node(PracticeTestTags.TITLE).assertTextEquals("A minor")
        node(PracticeTestTags.root(PitchClass.A)).assertIsSelected()
        node(PracticeTestTags.type(ChordQuality.MINOR)).assertIsSelected()

        node(PracticeTestTags.root(PitchClass.G)).performClick()
        awaitChart("G minor, shape 1 of")
        node(PracticeTestTags.type(ChordQuality.DOMINANT_SEVENTH)).performClick()
        awaitChart("G seventh, shape 1 of")
        node(PracticeTestTags.root(PitchClass.G)).assertIsSelected()
        node(PracticeTestTags.root(PitchClass.A)).assertIsNotSelected()
        node(PracticeTestTags.type(ChordQuality.DOMINANT_SEVENTH)).assertIsSelected()
        assertEquals("the picks are passed on to be saved", listOf(PitchClass.G), savedRoots)
        assertEquals(listOf(ChordQuality.DOMINANT_SEVENTH), savedQualities)
    }

    @Test
    fun talkBackHearsThePickersAsRadioButtonsByTheirSpokenNames() {
        show(PitchClass.A_SHARP, ChordQuality.MAJOR)
        awaitChart("B flat major")
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        node(PracticeTestTags.root(PitchClass.A_SHARP)).assert(radio).assert(hasContentDescription("B flat"))
        node(PracticeTestTags.root(PitchClass.F_SHARP)).assert(radio).assert(hasContentDescription("F sharp"))
        node(PracticeTestTags.type(ChordQuality.MINOR_SEVENTH)).assert(radio).assert(hasContentDescription("B flat minor seventh"))
        node(PracticeTestTags.PREVIOUS).assert(hasContentDescription("Previous shape"))
        node(PracticeTestTags.NEXT).assert(hasContentDescription("Next shape"))
        node(PracticeTestTags.TUNING).assert(hasContentDescription("Guitar, Standard"))
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
        node(PracticeTestTags.type(ChordQuality.MINOR)).performClick()
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
            for (tag in listOf(PracticeTestTags.SWITCH, PracticeTestTags.TUNING, PracticeTestTags.root(PitchClass.B), PracticeTestTags.type(ChordQuality.SUS4), PracticeTestTags.TITLE, PracticeTestTags.NEXT)) {
                assertWhollyShown(node(tag), "$case $tag", scroll = false)
                val bounds = node(tag).getBoundsInRoot()
                assertTrue("$case $tag inside the window $bounds", bounds.top >= window.top && bounds.bottom <= window.bottom)
            }
            assertSixRootsARow(case)
            val chart = chart().getBoundsInRoot()
            assertTrue("$case a big chart ${chart.height}", chart.height >= 170.dp)
            val pager = node(PracticeTestTags.NEXT).getBoundsInRoot()
            val title = node(PracticeTestTags.TITLE).getBoundsInRoot()
            assertTrue("$case title, chart and pager in order", title.bottom <= chart.top + 1.dp && chart.bottom <= pager.top + 1.dp)
            if (size == PHONE_LANDSCAPE) {
                val middle = (window.left + window.right) / 2
                assertTrue("$case pickers in the start half", node(PracticeTestTags.root(PitchClass.B)).getBoundsInRoot().right <= middle)
                assertTrue("$case chart in the end half", chart.left >= middle)
            }
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
            for (tag in listOf(PracticeTestTags.TUNING, PracticeTestTags.root(PitchClass.G_SHARP), PracticeTestTags.type(ChordQuality.MAJOR_SEVENTH), PracticeTestTags.TITLE, PracticeTestTags.COUNT, PracticeTestTags.NEXT)) {
                assertWhollyShown(node(tag), "$style nl $size $tag at 2x")
            }
            compose.assertEveryTextWhollyShown("$style nl $size at 2x:")
            assertSixRootsARow("$style nl $size at 2x:")
        }
    }

    /** The roots in two rows of six (C to F, F♯ to B), the chord types four and three. */
    private fun assertSixRootsARow(case: String) {
        fun top(tag: String) = node(tag).getBoundsInRoot().top
        val first = top(PracticeTestTags.root(PitchClass.C))
        val second = top(PracticeTestTags.root(PitchClass.F_SHARP))
        assertTrue("$case roots in two rows of six", top(PracticeTestTags.root(PitchClass.F)) == first && second > first && top(PracticeTestTags.root(PitchClass.B)) == second)
        val types = top(PracticeTestTags.type(ChordQuality.MAJOR))
        assertTrue("$case four chord types a row", top(PracticeTestTags.type(ChordQuality.MAJOR_SEVENTH)) == types && top(PracticeTestTags.type(ChordQuality.MINOR_SEVENTH)) > types)
    }

    /** Every language at 2× text, upright on a 360 dp phone and on its side. Only with `--languages`. */
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
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 30L
        private const val WAIT_MS = 5_000L

        /** The Xiaomi 12X less the status and navigation bars, a narrow phone less the bar, on its side less the rail, and a 320 dp phone. */
        private val XIAOMI = DpSize(393.dp, 760.dp)
        private val PHONE_UPRIGHT = DpSize(360.dp, 650.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
        private val NARROW_UPRIGHT = DpSize(320.dp, 640.dp)
    }
}
