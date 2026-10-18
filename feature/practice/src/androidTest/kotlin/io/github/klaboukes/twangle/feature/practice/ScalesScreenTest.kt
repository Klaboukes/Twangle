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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.espresso.Espresso
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
 * switches the view and shares the root, it opens on the saved scale, the pickers pick another (and
 * report it to be saved), the neck follows the picked tuning and notation, TalkBack hears the choice
 * and the scales' grid as radio groups, the scale's picker as a list and the neck in words, and it
 * all fits: without scrolling upright and on its side at normal text, wholly shown at 2× text, the
 * grid too; the neck lies upright where that is clearly bigger (a phone held upright), else sideways.
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

    /** How the neck lies now. */
    private fun lie(): NeckLie = NeckLie.entries.single { exists(PracticeTestTags.neckLie(it)) }

    @Test
    fun opensOnTheSavedScaleAndTheButtonsPickAnother() {
        show(PitchClass.A, ScaleType.MINOR_PENTATONIC)
        compose.waitForIdle()
        val spoken = spokenNeck()
        assertTrue(
            spoken,
            spoken.startsWith("A minor pentatonic, notes A, C, D, E, G, E 2 string, open E, fret 3 G, fret 5 A, root, fret 8 C"),
        )
        node(PracticeTestTags.TYPE_PICKER).assertTextEquals("Minor pentatonic")
        node(PracticeTestTags.chart(PracticeChart.SCALES)).assertIsSelected()
        assertPicked(PracticeTestTags.ROOT_PICKER, PracticeTestTags.root(PitchClass.A))
        assertPicked(PracticeTestTags.TYPE_PICKER, PracticeTestTags.scale(ScaleType.MINOR_PENTATONIC))
        assertFalse("no chord chart", exists(PracticeTestTags.CHART))
        assertFalse("no pager", exists(PracticeTestTags.NEXT))

        pick(PracticeTestTags.ROOT_PICKER, PracticeTestTags.root(PitchClass.G))
        assertTrue(spokenNeck(), spokenNeck().startsWith("G minor pentatonic, notes G, B flat, C, D, F, "))
        pick(PracticeTestTags.TYPE_PICKER, PracticeTestTags.scale(ScaleType.MAJOR))
        assertTrue(spokenNeck(), spokenNeck().startsWith("G major, notes G, A, B, C, D, E, F sharp, E 2 string, open E, fret 2 F sharp, fret 3 G, root"))
        node(PracticeTestTags.TYPE_PICKER).assertTextEquals("Major")
        node(PracticeTestTags.TYPE_PICKER).performClick()
        node(PracticeTestTags.scale(ScaleType.MAJOR)).assertIsSelected()
        node(PracticeTestTags.scale(ScaleType.MINOR_PENTATONIC)).assertIsNotSelected()
        node(PracticeTestTags.scale(ScaleType.MAJOR)).performClick()
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
        node(PracticeTestTags.TYPE_PICKER).assertTextEquals("Blues")
        assertPicked(PracticeTestTags.ROOT_PICKER, PracticeTestTags.root(PitchClass.D))
        pick(PracticeTestTags.ROOT_PICKER, PracticeTestTags.root(PitchClass.E))

        node(PracticeTestTags.chart(PracticeChart.CHORDS)).performClick()
        compose.waitUntil(WAIT_MS) { exists(PracticeTestTags.CHART) }
        node(PracticeTestTags.TYPE_PICKER).assertTextEquals("E major")
        assertPicked(PracticeTestTags.ROOT_PICKER, PracticeTestTags.root(PitchClass.E))
        assertEquals("both picks passed on to be saved", listOf(PracticeChart.SCALES, PracticeChart.CHORDS), savedCharts)
        assertEquals(listOf(PitchClass.E), savedRoots)
    }

    @Test
    fun talkBackHearsTheChoiceAndTheScalesAsRadioButtons() {
        show(PitchClass.A_SHARP, ScaleType.MAJOR)
        compose.waitForIdle()
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        val list = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)
        node(PracticeTestTags.chart(PracticeChart.CHORDS)).assert(radio).assert(hasContentDescription("Chords"))
        node(PracticeTestTags.chart(PracticeChart.SCALES)).assert(radio).assert(hasContentDescription("Scales"))
        node(PracticeTestTags.ROOT_PICKER).assert(list).assert(hasContentDescription("Root, B flat")).assertTextEquals("B♭")
        node(PracticeTestTags.TYPE_PICKER).assert(list).assert(hasContentDescription("Scale, B flat major")).assertTextEquals("Major")
        assertTrue("the scale's picker is a polite live region", node(PracticeTestTags.TYPE_PICKER).fetchSemanticsNode().config.contains(SemanticsProperties.LiveRegion))
        node(PracticeTestTags.TYPE_PICKER).performClick()
        node(PracticeTestTags.scale(ScaleType.MINOR_PENTATONIC)).assert(radio).assert(hasContentDescription("Minor pentatonic"))
        node(PracticeTestTags.scale(ScaleType.MAJOR)).performClick()
        node(PracticeTestTags.ROOT_PICKER).performClick()
        node(PracticeTestTags.root(PitchClass.A_SHARP)).assert(radio).assert(hasContentDescription("B flat")).assertTextEquals("B♭")
        node(PracticeTestTags.root(PitchClass.A_SHARP)).performClick()
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
        node(PracticeTestTags.ROOT_PICKER).assertTextEquals("Re")
        node(PracticeTestTags.ROOT_PICKER).performClick()
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
                PracticeTestTags.SWITCH, PracticeTestTags.TUNING, PracticeTestTags.chart(PracticeChart.SCALES), PracticeTestTags.ROOT_PICKER, PracticeTestTags.TYPE_PICKER,
            )
            for (tag in tags) {
                assertWhollyShown(node(tag), "$case $tag", scroll = false)
                val bounds = node(tag).getBoundsInRoot()
                assertTrue("$case $tag inside the window $bounds", bounds.top >= window.top && bounds.bottom <= window.bottom)
            }
            // Upright on a phone held upright, where the pickers leave it the height; sideways on its side.
            val lie = lie()
            assertEquals("$case how the neck lies", if (size == PHONE_LANDSCAPE) NeckLie.SIDEWAYS else NeckLie.UPRIGHT, lie)
            val drawn = node(PracticeTestTags.neckLie(lie)).getBoundsInRoot()
            val area = neck().getBoundsInRoot()
            assertTrue("$case the neck inside its room $drawn $area", drawn.left >= area.left && drawn.right <= area.right && drawn.top >= area.top && drawn.bottom <= area.bottom)
            assertTrue("$case inside the window", drawn.bottom <= window.bottom)
            assertTrue("$case a big neck $drawn", maxOf(drawn.width, drawn.height) >= 300.dp)
            val pickers = node(PracticeTestTags.TYPE_PICKER).getBoundsInRoot()
            if (size == PHONE_LANDSCAPE) {
                val middle = (window.left + window.right) / 2
                assertTrue("$case the choice and pickers in the start half", pickers.right <= middle && node(PracticeTestTags.chart(PracticeChart.CHORDS)).getBoundsInRoot().right <= middle)
                assertTrue("$case the neck in the end half", area.left >= middle)
            } else {
                assertTrue("$case pickers over the neck", pickers.bottom <= area.top + 1.dp)
                assertTrue("$case the choice under the tuning line", node(PracticeTestTags.TUNING).getBoundsInRoot().bottom <= node(PracticeTestTags.chart(PracticeChart.SCALES)).getBoundsInRoot().top)
            }
            assertTheGrid(case)
        }
    }

    /**
     * The scales' grid two to a row (Blues alone on the last), under the pickers, wholly shown (with
     * [texts], every text in it too); Back closes it.
     */
    private fun assertTheGrid(case: String, texts: Boolean = false) {
        fun top(tag: String) = node(tag).getBoundsInRoot().top
        val pickers = node(PracticeTestTags.TYPE_PICKER).fetchSemanticsNode().let { it.positionOnScreen.y + it.size.height }
        node(PracticeTestTags.TYPE_PICKER).performClick()
        val first = top(PracticeTestTags.scale(ScaleType.MAJOR))
        assertTrue(
            "$case scales two to a row",
            top(PracticeTestTags.scale(ScaleType.NATURAL_MINOR)) == first && top(PracticeTestTags.scale(ScaleType.MAJOR_PENTATONIC)) > first,
        )
        // The grid is a popup, a window of its own: against the pickers on the screen.
        assertTrue("$case the scales under the pickers", node(PracticeTestTags.scale(ScaleType.MAJOR)).fetchSemanticsNode().positionOnScreen.y >= pickers)
        for (type in ScaleType.entries) assertWhollyShown(node(PracticeTestTags.scale(type)), "$case scale $type", scroll = false)
        if (texts) {
            compose.assertEveryTextWhollyShown("$case scales:")
            compose.assertNoWordBroken("$case scales:")
        }
        Espresso.pressBack()
        compose.waitForIdle()
        assertFalse("$case Back closes the grid", exists(PracticeTestTags.scale(ScaleType.MAJOR)))
    }

    @Test
    fun theNeckLiesUprightWhereThatIsClearlyBigger() {
        shownSize.value = TABLET_UPRIGHT
        show()
        compose.waitForIdle()
        assertTrue("upright on a tall tablet", exists(PracticeTestTags.neckLie(NeckLie.UPRIGHT)))
        assertWhollyShown(node(PracticeTestTags.neckLie(NeckLie.UPRIGHT)), "$style the upright neck", scroll = false)
        shownSize.value = PHONE_LANDSCAPE
        compose.waitForIdle()
        assertTrue("sideways on a phone on its side", exists(PracticeTestTags.neckLie(NeckLie.SIDEWAYS)))
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
            for (tag in listOf(PracticeTestTags.TUNING, PracticeTestTags.chart(PracticeChart.SCALES), PracticeTestTags.ROOT_PICKER, PracticeTestTags.TYPE_PICKER, PracticeTestTags.NECK)) {
                assertWhollyShown(node(tag), "$style nl $size $tag at 2x")
            }
            compose.assertEveryTextWhollyShown("$style nl $size at 2x:")
            compose.assertNoWordBroken("$style nl $size at 2x:")
            assertTheGrid("$style nl $size at 2x:", texts = true)
        }
    }

    /** Every language at 2× text, upright on a 360 dp phone and on its side, the grid too. Only with `--languages`. */
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
                assertTheGrid("$style $language $size at 2x:", texts = true)
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

        /** The Xiaomi 12X less the status and navigation bars, a short narrow phone (360 x 720 dp with navigation buttons) less the bars, on its side less the rail, a 320 dp phone and a tall tablet. */
        private val XIAOMI = DpSize(393.dp, 760.dp)
        private val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
        private val NARROW_UPRIGHT = DpSize(320.dp, 640.dp)
        private val TABLET_UPRIGHT = DpSize(700.dp, 1100.dp)
    }
}
