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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import io.github.klaboukes.twangle.core.settings.PracticeChart
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
 * Scales in the Chords tool (Phase 32) in each style, light and dark: the Chords · Scales choice
 * switches the view and shares the root, it opens on the saved scale, the buttons pick another (and
 * report it to be saved), the neck follows the picked tuning and notation, TalkBack hears the choice
 * and the scales as radio groups and the neck in words, and it all fits: without scrolling upright
 * and on its side at normal text, wholly shown at 2× text; the neck lies sideways on a phone and
 * upright where that is clearly bigger.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class ScalesScreenTest(private val style: VisualStyle) {

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
    private val savedCharts = mutableListOf<PracticeChart>()
    private val savedRoots = mutableListOf<PitchClass>()
    private val savedTypes = mutableListOf<ScaleType>()

    private fun show(root: PitchClass = PitchClass.C, type: ScaleType = ScaleType.MAJOR, chart: PracticeChart = PracticeChart.SCALES) {
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
                        savedQuality = ChordQuality.MAJOR,
                        onRootChange = { savedRoots += it },
                        onQualityChange = {},
                        header = { PracticeToolSwitch(PracticeTool.CHORDS, {}, listOf(PracticeTool.METRONOME, PracticeTool.CHORDS)) },
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        savedChart = chart,
                        savedScaleType = type,
                        onChartChange = { savedCharts += it },
                        onScaleTypeChange = { savedTypes += it },
                    )
                }
            }
        }
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag)
    private fun neck() = node(PracticeTestTags.NECK)
    private fun spokenNeck(): String = neck().fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString()
    private fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun opensOnTheSavedScaleAndTheButtonsPickAnother() {
        show(PitchClass.A, ScaleType.MINOR_PENTATONIC)
        compose.waitForIdle()
        val spoken = spokenNeck()
        assertTrue(
            spoken,
            spoken.startsWith("A minor pentatonic, notes A, C, D, E, G, E 2 string, open E, fret 3 G, fret 5 A, root, fret 8 C"),
        )
        node(PracticeTestTags.TITLE).assertTextEquals("A minor pentatonic")
        node(PracticeTestTags.chart(PracticeChart.SCALES)).assertIsSelected()
        node(PracticeTestTags.root(PitchClass.A)).assertIsSelected()
        node(PracticeTestTags.scale(ScaleType.MINOR_PENTATONIC)).assertIsSelected()
        assertFalse("no chord chart", exists(PracticeTestTags.CHART))
        assertFalse("no pager", exists(PracticeTestTags.NEXT))

        node(PracticeTestTags.root(PitchClass.G)).performClick()
        compose.waitForIdle()
        assertTrue(spokenNeck(), spokenNeck().startsWith("G minor pentatonic, notes G, B flat, C, D, F, "))
        node(PracticeTestTags.scale(ScaleType.MAJOR)).performClick()
        compose.waitForIdle()
        assertTrue(spokenNeck(), spokenNeck().startsWith("G major, notes G, A, B, C, D, E, F sharp, E 2 string, open E, fret 2 F sharp, fret 3 G, root"))
        node(PracticeTestTags.TITLE).assertTextEquals("G major")
        node(PracticeTestTags.scale(ScaleType.MAJOR)).assertIsSelected()
        node(PracticeTestTags.scale(ScaleType.MINOR_PENTATONIC)).assertIsNotSelected()
        assertEquals("the picks are passed on to be saved", listOf(PitchClass.G), savedRoots)
        assertEquals(listOf(ScaleType.MAJOR), savedTypes)
        assertEquals("the choice wasn't touched", emptyList<PracticeChart>(), savedCharts)
    }

    @Test
    fun theChoiceSwitchesBetweenChordsAndScalesAndTheRootIsShared() {
        show(PitchClass.D, ScaleType.BLUES, chart = PracticeChart.CHORDS)
        compose.waitUntil(WAIT_MS) { exists(PracticeTestTags.CHART) }
        assertFalse("chords first", exists(PracticeTestTags.NECK))
        node(PracticeTestTags.chart(PracticeChart.CHORDS)).assertIsSelected()

        node(PracticeTestTags.chart(PracticeChart.SCALES)).performClick()
        compose.waitForIdle()
        assertTrue("the neck shows", exists(PracticeTestTags.NECK))
        assertFalse("the chord chart has gone", exists(PracticeTestTags.CHART))
        node(PracticeTestTags.TITLE).assertTextEquals("D blues")
        node(PracticeTestTags.root(PitchClass.D)).assertIsSelected()
        node(PracticeTestTags.root(PitchClass.E)).performClick()

        node(PracticeTestTags.chart(PracticeChart.CHORDS)).performClick()
        compose.waitUntil(WAIT_MS) { exists(PracticeTestTags.CHART) }
        node(PracticeTestTags.TITLE).assertTextEquals("E major")
        node(PracticeTestTags.root(PitchClass.E)).assertIsSelected()
        assertEquals("both picks passed on to be saved", listOf(PracticeChart.SCALES, PracticeChart.CHORDS), savedCharts)
        assertEquals(listOf(PitchClass.E), savedRoots)
    }

    @Test
    fun talkBackHearsTheChoiceAndTheScalesAsRadioButtons() {
        show(PitchClass.A_SHARP, ScaleType.MAJOR)
        compose.waitForIdle()
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        node(PracticeTestTags.chart(PracticeChart.CHORDS)).assert(radio).assert(hasContentDescription("Chords"))
        node(PracticeTestTags.chart(PracticeChart.SCALES)).assert(radio).assert(hasContentDescription("Scales"))
        node(PracticeTestTags.scale(ScaleType.MINOR_PENTATONIC)).assert(radio).assert(hasContentDescription("Minor pentatonic"))
        node(PracticeTestTags.root(PitchClass.A_SHARP)).assert(radio).assert(hasContentDescription("B flat"))
        node(PracticeTestTags.root(PitchClass.A_SHARP)).assertTextEquals("B♭")
        node(PracticeTestTags.TITLE).assert(isHeading()).assertTextEquals("B♭ major")
        assertTrue("the scale's name is a polite live region", node(PracticeTestTags.TITLE).fetchSemanticsNode().config.contains(SemanticsProperties.LiveRegion))
        assertFalse("the long neck is not", neck().fetchSemanticsNode().config.contains(SemanticsProperties.LiveRegion))
        assertTrue(spokenNeck(), spokenNeck().startsWith("B flat major, notes B flat, C, D, E flat, F, G, A"))
    }

    @Test
    fun theNeckFollowsTheTuningAndTheNotation() {
        show(PitchClass.D, ScaleType.MAJOR)
        compose.waitForIdle()
        shownTuning.value = Tunings.DADGAD
        compose.waitUntil(WAIT_MS) { spokenNeck().contains("D 2 string, open D, root") && spokenNeck().contains("A 3 string, open A") }
        shownTuning.value = Instruments.UKULELE.defaultTuning
        compose.waitUntil(WAIT_MS) { Regex(" string, ").findAll(spokenNeck()).count() == 4 }
        shownTuning.value = Instruments.GUITAR_8.defaultTuning
        compose.waitUntil(WAIT_MS) { Regex(" string, ").findAll(spokenNeck()).count() == 8 }
        // A tuning of the player's own (open C, low to high C G C G C E).
        shownTuning.value = Tuning("custom_open_c", listOf(36, 43, 48, 55, 60, 64).map { Note(it) })
        compose.waitUntil(WAIT_MS) { spokenNeck().contains("C 2 string, fret 1 C sharp, fret 2 D, root") }
        shownNotation.value = Notation.SOLFEGE
        compose.waitUntil(WAIT_MS) { spokenNeck().startsWith("Re major, notes Re, Mi, Fa sharp, Sol, La, Si, Do sharp") }
        node(PracticeTestTags.TITLE).assertTextEquals("Re major")
        node(PracticeTestTags.root(PitchClass.F_SHARP)).assertTextEquals("Fa♯")
    }

    @Test
    fun itFitsWithoutScrollingUprightAndOnItsSideInLightAndDark() {
        show(PitchClass.G_SHARP, ScaleType.BLUES)
        for (dark in listOf(false, true)) for (size in listOf(XIAOMI, PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownDark.value = dark
            shownSize.value = size
            compose.waitForIdle()
            val case = "$style ${if (dark) "dark" else "light"} $size:"
            val window = node(WINDOW).getBoundsInRoot()
            val tags = listOf(
                PracticeTestTags.SWITCH, PracticeTestTags.TUNING, PracticeTestTags.chart(PracticeChart.SCALES), PracticeTestTags.root(PitchClass.B),
                PracticeTestTags.scale(ScaleType.BLUES), PracticeTestTags.scale(ScaleType.MINOR_PENTATONIC), PracticeTestTags.TITLE,
            )
            for (tag in tags) {
                assertWhollyShown(node(tag), "$case $tag", scroll = false)
                val bounds = node(tag).getBoundsInRoot()
                assertTrue("$case $tag inside the window $bounds", bounds.top >= window.top && bounds.bottom <= window.bottom)
            }
            assertTrue("$case lies sideways on a phone", exists(PracticeTestTags.neckLie(NeckLie.SIDEWAYS)))
            val drawn = node(PracticeTestTags.neckLie(NeckLie.SIDEWAYS)).getBoundsInRoot()
            val area = neck().getBoundsInRoot()
            assertTrue("$case the neck inside its room $drawn $area", drawn.left >= area.left && drawn.right <= area.right && drawn.top >= area.top && drawn.bottom <= area.bottom)
            assertTrue("$case inside the window", drawn.bottom <= window.bottom)
            assertTrue("$case a big neck ${drawn.width}", drawn.width >= 300.dp)
            val title = node(PracticeTestTags.TITLE).getBoundsInRoot()
            assertTrue("$case title over the neck", title.bottom <= area.top + 1.dp)
            fun top(tag: String) = node(tag).getBoundsInRoot().top
            val perRow = if (size == PHONE_LANDSCAPE) 3 else 2
            val first = top(PracticeTestTags.scale(ScaleType.MAJOR))
            assertTrue(
                "$case scales $perRow to a row",
                top(PracticeTestTags.scale(ScaleType.entries[perRow - 1])) == first && top(PracticeTestTags.scale(ScaleType.entries[perRow])) > first,
            )
            if (size == PHONE_LANDSCAPE) {
                val middle = (window.left + window.right) / 2
                assertTrue("$case pickers in the start half", node(PracticeTestTags.scale(ScaleType.BLUES)).getBoundsInRoot().right <= middle)
                assertTrue("$case the choice and the neck in the end half", node(PracticeTestTags.chart(PracticeChart.CHORDS)).getBoundsInRoot().left >= middle && area.left >= middle)
            } else {
                assertTrue("$case the choice under the tuning line", node(PracticeTestTags.TUNING).getBoundsInRoot().bottom <= top(PracticeTestTags.chart(PracticeChart.SCALES)))
            }
        }
    }

    @Test
    fun theNeckLiesUprightWhereThatIsClearlyBigger() {
        shownSize.value = TABLET_UPRIGHT
        show()
        compose.waitForIdle()
        assertTrue("upright on a tall tablet", exists(PracticeTestTags.neckLie(NeckLie.UPRIGHT)))
        assertWhollyShown(node(PracticeTestTags.neckLie(NeckLie.UPRIGHT)), "$style the upright neck", scroll = false)
        shownSize.value = XIAOMI
        compose.waitForIdle()
        assertTrue("sideways on the phone", exists(PracticeTestTags.neckLie(NeckLie.SIDEWAYS)))
    }

    @Test
    fun dutchAtTwiceTheTextSizeIsWhollyShown() {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        shownNotation.value = Notation.SOLFEGE
        show(PitchClass.G_SHARP, ScaleType.MINOR_PENTATONIC)
        for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
            for (tag in listOf(PracticeTestTags.TUNING, PracticeTestTags.chart(PracticeChart.SCALES), PracticeTestTags.root(PitchClass.G_SHARP), PracticeTestTags.scale(ScaleType.MAJOR_PENTATONIC), PracticeTestTags.TITLE, PracticeTestTags.NECK)) {
                assertWhollyShown(node(tag), "$style nl $size $tag at 2x")
            }
            compose.assertEveryTextWhollyShown("$style nl $size at 2x:")
        }
    }

    /** Every language at 2× text, upright on a 360 dp phone and on its side. Only with `--languages`. */
    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        show(PitchClass.G_SHARP, ScaleType.MAJOR_PENTATONIC)
        for (language in shippedLanguages) {
            shownLocale.value = language
            for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                compose.waitForIdle()
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

        /** The Xiaomi 12X less the status and navigation bars, a narrow phone less the bar, on its side less the rail, a 320 dp phone and a tall tablet. */
        private val XIAOMI = DpSize(393.dp, 760.dp)
        private val PHONE_UPRIGHT = DpSize(360.dp, 650.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
        private val NARROW_UPRIGHT = DpSize(320.dp, 640.dp)
        private val TABLET_UPRIGHT = DpSize(700.dp, 1100.dp)
    }
}
