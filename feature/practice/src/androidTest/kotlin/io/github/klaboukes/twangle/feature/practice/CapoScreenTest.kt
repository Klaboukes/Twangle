package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.height
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.NAME_PICKER_HEIGHT
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.capo.Capo
import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
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
import io.github.klaboukes.twangle.core.testing.checkedLanguages
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import java.util.concurrent.TimeUnit
import kotlin.math.abs
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
 * The Capo helper (Phase 33) in each style, light and dark: it opens on the saved shapes and fret,
 * ‹ › move the capo and the list picks the shapes (both reported to be saved), the table shows what
 * each shape sounds in the player's notation, a row opens its shape, TalkBack hears every part by
 * what it says or does, and the whole tool fits: without scrolling upright and on its side at
 * normal text, wholly shown at 2× text.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class CapoScreenTest(private val style: VisualStyle) {

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
    private val shownNotation = mutableStateOf(Notation.SHARPS)
    private val shownShortString = mutableStateOf(false)
    private val savedShapes = mutableListOf<SongKey>()
    private val savedFrets = mutableListOf<Int>()
    private val opened = mutableListOf<Chord>()
    private var instrumentTaps = 0
    private val tools = listOf(PracticeTool.METRONOME, PracticeTool.CHORDS, PracticeTool.SCALES, PracticeTool.CAPO)

    private fun show(shapes: SongKey = SongKey(PitchClass.C), fret: Int = 0) {
        compose.setContent {
            PhoneScreen(shownSize.value, bars(), shownFontScale.value, shownLocale.value) {
                TwangleTheme(style = style, darkTheme = shownDark.value, animate = false) {
                    CapoScreen(
                        tuningName = "Guitar · Standard",
                        spokenTuningName = "Guitar, Standard",
                        notation = shownNotation.value,
                        savedShapes = shapes,
                        savedFret = fret,
                        onShapesChange = { savedShapes += it },
                        onFretChange = { savedFrets += it },
                        onOpenChord = { opened += it },
                        header = { PracticeToolButton(PracticeTool.CAPO, {}, tools) },
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        onPickInstrument = { instrumentTaps++ },
                        shortString = shownShortString.value,
                    )
                }
            }
        }
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag)
    private fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun row(root: PitchClass, quality: ChordQuality) = node(PracticeTestTags.capoRow(Chord.of(root, quality)))
    private fun spoken(tag: String): String =
        node(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString()

    /** Each row as TalkBack says it, "C major shape, sounds E flat major", by its shape and the chord it sounds. */
    private fun table(shapes: SongKey): List<String> = capoChordsOf(shapes).map { spoken(PracticeTestTags.capoRow(it)) }

    private fun rows(vararg pairs: Pair<String, String>): List<String> = pairs.map { (shape, sounds) -> "$shape shape, sounds $sounds" }

    @Test
    fun opensOnTheSavedPicksAndTheButtonsMoveTheCapo() {
        show(SongKey(PitchClass.C), 3)
        assertEquals(
            rows("C major" to "E flat major", "D minor" to "F minor", "E minor" to "G minor", "F major" to "A flat major", "G major" to "B flat major", "A minor" to "C minor"),
            table(SongKey(PitchClass.C)),
        )
        assertEquals("Sounds in E flat major", spoken(PracticeTestTags.CAPO_SOUNDS_IN))
        assertEquals("Capo on fret 3, Sounds in E flat major", spoken(PracticeTestTags.CAPO_FRET))

        repeat(3) { node(PracticeTestTags.CAPO_LOWER).performClick() }
        assertEquals("No capo, Sounds in C major", spoken(PracticeTestTags.CAPO_FRET))
        node(PracticeTestTags.CAPO_NONE).assert(hasText("No capo")).assertIsDisplayed()
        assertEquals(
            rows("C major" to "C major", "D minor" to "D minor", "E minor" to "E minor", "F major" to "F major", "G major" to "G major", "A minor" to "A minor"),
            table(SongKey(PitchClass.C)),
        )
        node(PracticeTestTags.CAPO_LOWER).assertIsNotEnabled()
        repeat(11) { node(PracticeTestTags.CAPO_HIGHER).assertIsEnabled().performClick() }
        node(PracticeTestTags.CAPO_HIGHER).assertIsNotEnabled()
        assertEquals("Capo on fret 11, Sounds in B major", spoken(PracticeTestTags.CAPO_FRET))
        assertEquals("every step is passed on to be saved", listOf(2, 1, 0) + (1..11).toList(), savedFrets)
    }

    /**
     * At the nut the stepper says "No capo", not "Capo 0", and the buttons and the slot between them
     * keep their size and place on every fret, in English and in Russian (the longest words) at 2× text.
     */
    @Test
    fun atTheNutItSaysNoCapoAndTheButtonsStayPut() {
        val cases = listOf(
            Triple("en", 1f, XIAOMI) to "No capo",
            Triple("en", 2f, NARROW_UPRIGHT) to "No capo",
            Triple("ru", 2f, NARROW_UPRIGHT) to "Без каподастра",
        )
        show(SongKey(PitchClass.C), 0)
        for ((setting, none) in cases) {
            val (language, scale, size) = setting
            shownLocale.value = language
            shownFontScale.value = scale
            shownSize.value = size
            // Back to the nut from the case before.
            repeat(Capo.HIGHEST_FRET) { node(PracticeTestTags.CAPO_LOWER).performClick() }
            compose.waitForIdle()
            val case = "$style $language ${scale}x $size:"
            node(PracticeTestTags.CAPO_NONE).assert(hasText(none))
            assertWhollyShown(node(PracticeTestTags.CAPO_NONE), "$case \"$none\"")
            val tags = listOf(PracticeTestTags.CAPO_LOWER, PracticeTestTags.CAPO_FRET, PracticeTestTags.CAPO_HIGHER)
            val atTheNut = tags.map { node(it).getBoundsInRoot() }
            for (fret in 1..Capo.HIGHEST_FRET) {
                node(PracticeTestTags.CAPO_HIGHER).performClick()
                node(PracticeTestTags.CAPO_NONE).assertDoesNotExist()
                assertEquals("$case the stepper at fret $fret", atTheNut, tags.map { node(it).getBoundsInRoot() })
            }
            compose.assertEveryTextWhollyShown("$case at fret 11")
        }
    }

    @Test
    fun theListPicksTheShapes() {
        show(SongKey(PitchClass.C), 3)
        node(PracticeTestTags.CAPO_SHAPES).assert(hasContentDescription("Shapes I play: C major")).performClick()
        node(PracticeTestTags.capoShapes(SongKey(PitchClass.A, minor = true))).assert(hasContentDescription("A minor")).performClick()
        compose.waitForIdle()
        assertEquals(listOf(SongKey(PitchClass.A, minor = true)), savedShapes)
        node(PracticeTestTags.CAPO_SHAPES).assert(hasContentDescription("Shapes I play: A minor"))
        assertEquals(
            rows("A minor" to "C minor", "C major" to "E flat major", "D minor" to "F minor", "E minor" to "G minor", "F major" to "A flat major", "G major" to "B flat major"),
            table(SongKey(PitchClass.A, minor = true)),
        )
        assertEquals("Sounds in C minor", spoken(PracticeTestTags.CAPO_SOUNDS_IN))

        // Every shape is offered.
        node(PracticeTestTags.CAPO_SHAPES).performClick()
        for (shapes in CAPO_SHAPES) node(PracticeTestTags.capoShapes(shapes)).assertExists()
        node(PracticeTestTags.capoShapes(SongKey(PitchClass.G))).performClick()
        compose.waitForIdle()
        assertEquals(
            rows("G major" to "B flat major", "A minor" to "C minor", "B minor" to "D minor", "C major" to "E flat major", "D major" to "F major", "E minor" to "G minor"),
            table(SongKey(PitchClass.G)),
        )
    }

    @Test
    fun aTappedRowOpensItsShapeAndTalkBackHearsEveryPart() {
        show(SongKey(PitchClass.C), 3)
        val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        row(PitchClass.F, ChordQuality.MAJOR)
            .assert(button)
            .assert(hasContentDescription("F major shape, sounds A flat major"))
            .assert(SemanticsMatcher("opens in Chords") { it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "open in Chords" })
            .performClick()
        assertEquals(listOf(Chord.of(PitchClass.F, ChordQuality.MAJOR)), opened)
        node(PracticeTestTags.CAPO_SHAPES)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
            .assert(hasContentDescription("Shapes I play: C major"))
            // The one picker face (Phase 63): the key in words, as Chords and Scales write theirs, in their height.
            .assertTextEquals("C major")
        assertEquals(NAME_PICKER_HEIGHT.value, node(PracticeTestTags.CAPO_SHAPES).getBoundsInRoot().height.value, 0.5f)
        node(PracticeTestTags.CAPO_LOWER).assert(hasContentDescription("Capo one fret down"))
        node(PracticeTestTags.CAPO_HIGHER).assert(hasContentDescription("Capo one fret up"))
        node(PracticeTestTags.INSTRUMENT_BUTTON)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Guitar, Standard"))
        assertTrue("the name beside it is skipped", node(PracticeTestTags.TUNING).fetchSemanticsNode().config.contains(SemanticsProperties.HideFromAccessibility))
        assertTrue("the capo is a polite live region", node(PracticeTestTags.CAPO_FRET).fetchSemanticsNode().config.contains(SemanticsProperties.LiveRegion))
    }

    @Test
    fun theNamesFollowTheNotationAndTheSongsKey() {
        shownNotation.value = Notation.SOLFEGE
        show(SongKey(PitchClass.C), 3)
        assertEquals("Do major shape, sounds Mi flat major", spoken(PracticeTestTags.capoRow(Chord.of(PitchClass.C, ChordQuality.MAJOR))))
        node(PracticeTestTags.CAPO_SHAPES).assert(hasContentDescription("Shapes I play: Do major"))
        assertEquals("Sounds in Mi flat major", spoken(PracticeTestTags.CAPO_SOUNDS_IN))
    }

    @Test
    fun theSoundingChordsAreWrittenInTheSongsKey() {
        // D shapes at 4 sound in F♯ major, whose vi is D♯m, not E♭m.
        show(SongKey(PitchClass.D), 4)
        assertEquals(
            rows("D major" to "F sharp major", "E minor" to "G sharp minor", "F sharp minor" to "A sharp minor", "G major" to "B major", "A major" to "C sharp major", "B minor" to "D sharp minor"),
            table(SongKey(PitchClass.D)),
        )
    }

    @Test
    fun itFitsWithoutScrollingUprightAndOnItsSideInLightAndDark() = assertItFits(listOf(XIAOMI, PHONE_UPRIGHT, PHONE_LANDSCAPE), listOf(false, true))

    /** On a tablet, upright and on its side (Phase 52): it fits as on a phone, and all under the top row stands in the middle of the height, as the metronome's column. */
    @Test
    fun onATabletItStandsInTheMiddleOfTheHeight() = assertItFits(listOf(TABLET_UPRIGHT, TABLET_LANDSCAPE), listOf(false))

    private fun assertItFits(sizes: List<DpSize>, themes: List<Boolean>) {
        show(SongKey(PitchClass.C), 3)
        val last = PracticeTestTags.capoRow(Chord.of(PitchClass.A, ChordQuality.MINOR))
        for (dark in themes) for (size in sizes) {
            shownDark.value = dark
            shownSize.value = size
            compose.waitForIdle()
            val case = "$style ${if (dark) "dark" else "light"} $size:"
            val window = bars().safeArea(size)
            for (tag in listOf(PracticeTestTags.TOOL_BUTTON, PracticeTestTags.INSTRUMENT_BUTTON, PracticeTestTags.TUNING, PracticeTestTags.CAPO_SHAPES, PracticeTestTags.CAPO_LOWER, PracticeTestTags.CAPO_FRET, PracticeTestTags.CAPO_HIGHER, last, PracticeTestTags.CAPO_SOUNDS_IN)) {
                assertWhollyShown(node(tag), "$case $tag", scroll = false)
                val bounds = node(tag).getBoundsInRoot()
                assertTrue("$case $tag inside the window $bounds", bounds.top >= window.top && bounds.bottom <= window.bottom)
            }
            // One row: the tool button at the start, the instrument and tuning beside it (Phase 47).
            val toolButton = node(PracticeTestTags.TOOL_BUTTON).getBoundsInRoot()
            val instrument = node(PracticeTestTags.INSTRUMENT_BUTTON).getBoundsInRoot()
            assertTrue(
                "$case the tuning beside the tool button $toolButton $instrument",
                instrument.left >= toolButton.right && instrument.top < toolButton.bottom && instrument.bottom > toolButton.top,
            )
            val middle = (window.left + window.right) / 2
            if (size.width > size.height) {
                assertTrue("$case the capo in the start half", node(PracticeTestTags.CAPO_HIGHER).getBoundsInRoot().right <= middle)
                assertTrue("$case the table in the end half", node(last).getBoundsInRoot().left >= middle)
            }
            val tool = node(PracticeTestTags.TOOL_BUTTON).getBoundsInRoot()
            assertTrue("$case the tool button at the top $tool", tool.top >= window.top && tool.top <= window.top + 16.dp)
            if (size == TABLET_UPRIGHT || size == TABLET_LANDSCAPE) {
                // A tablet (Phase 52): the part under the top row in the middle of the height left, not hanging at the top.
                val first = if (size == TABLET_UPRIGHT) node(PracticeTestTags.CAPO_SHAPES) else node(PracticeTestTags.capoRow(Chord.of(PitchClass.C, ChordQuality.MAJOR)))
                val above = first.getBoundsInRoot().top - tool.bottom
                val below = window.bottom - maxOf(node(PracticeTestTags.CAPO_SOUNDS_IN).getBoundsInRoot().bottom, node(last).getBoundsInRoot().bottom)
                assertTrue("$case in the middle of the height: $above above, $below below", above > 100.dp && below > 100.dp && abs((above - below).value) < 60f)
                if (size == TABLET_LANDSCAPE) {
                    assertTrue("$case the table under the top row", node(PracticeTestTags.capoRow(Chord.of(PitchClass.C, ChordQuality.MAJOR))).getBoundsInRoot().top >= tool.bottom)
                }
            }
        }
    }

    @Test
    fun dutchAtTwiceTheTextSizeIsWhollyShown() {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        shownNotation.value = Notation.SOLFEGE
        show(SongKey(PitchClass.E, minor = true), 11)
        for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
            for (tag in listOf(PracticeTestTags.CAPO_SHAPES, PracticeTestTags.CAPO_LOWER, PracticeTestTags.CAPO_FRET, PracticeTestTags.CAPO_HIGHER, PracticeTestTags.CAPO_SOUNDS_IN)) {
                assertWhollyShown(node(tag), "$style nl $size $tag at 2x")
            }
            compose.assertEveryTextWhollyShown("$style nl $size at 2x:")
        }
    }

    /** Every language at 2× text, upright on a 320 dp phone and on its side. Only with `--languages`. */
    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        // At fret 11 and at the nut, where it says "No capo".
        show(SongKey(PitchClass.G), Capo.HIGHEST_FRET)
        for (fret in listOf(Capo.HIGHEST_FRET, 0)) {
            repeat(Capo.HIGHEST_FRET - fret) { node(PracticeTestTags.CAPO_LOWER).performClick() }
            for (language in checkedLanguages) {
                shownLocale.value = language
                for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
                    shownSize.value = size
                    compose.waitForIdle()
                    compose.assertEveryTextWhollyShown("$style $language $size fret $fret at 2x:")
                    compose.assertNoWordBroken("$style $language $size fret $fret at 2x:")
                }
            }
        }
    }


    /**
     * The banjo's short fifth string (Phase 57): the table's place says Capo doesn't know it yet, the shapes, the
     * capo and "Sounds in" stay where they are; another instrument brings the table straight back. Upright and on
     * its side, at normal and 2x text.
     */
    @Test
    fun theBanjoGetsAQuietLineInTheTablesPlace() {
        val line = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.practice_capo_short_string)
        show(SongKey(PitchClass.C), 3)
        val firstRow = PracticeTestTags.capoRow(Chord.of(PitchClass.C, ChordQuality.MAJOR))
        for (scale in listOf(1f, 2f)) for (size in listOf(XIAOMI, PHONE_LANDSCAPE)) {
            val case = "$style $size at ${scale}x"
            shownFontScale.value = scale
            shownSize.value = size
            compose.waitForIdle()
            val soundsIn = node(PracticeTestTags.CAPO_SOUNDS_IN).getBoundsInRoot()
            val stepper = node(PracticeTestTags.CAPO_FRET).getBoundsInRoot()
            shownShortString.value = true
            compose.waitForIdle()
            assertFalse("$case: no table", exists(firstRow))
            assertWhollyShown(node(PracticeTestTags.SHORT_STRING).assertTextEquals(line), "$case: the banjo's line", scroll = true)
            assertEquals("$case: the capo stays", stepper, node(PracticeTestTags.CAPO_FRET).getBoundsInRoot())
            node(PracticeTestTags.CAPO_SOUNDS_IN).assertExists()
            shownShortString.value = false
            compose.waitForIdle()
            node(firstRow).assertExists()
            assertEquals("$case: Sounds in is back in its place", soundsIn, node(PracticeTestTags.CAPO_SOUNDS_IN).getBoundsInRoot())
        }
    }

    /** The banjo's line in every language at 2× text, upright on a 320 dp phone and on its side. Only with `--languages`. */
    @EveryLanguage
    @Test
    fun theBanjosLineFitsInEveryLanguageAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        shownShortString.value = true
        show()
        for (language in checkedLanguages) {
            shownLocale.value = language
            for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                compose.waitForIdle()
                assertWhollyShown(node(PracticeTestTags.SHORT_STRING), "$style $language $size at 2x: the banjo's line", scroll = true)
                compose.assertEveryTextWhollyShown("$style $language $size at 2x, banjo:")
                compose.assertNoWordBroken("$style $language $size at 2x, banjo:")
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

        /** The Xiaomi 12X less the status and navigation bars, a short narrow phone (360 x 720 dp with navigation buttons) less the bars, on its side less the rail, and a 320 dp phone. */
        private val XIAOMI = DpSize(393.dp, 760.dp)
        private val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
        private val NARROW_UPRIGHT = DpSize(320.dp, 640.dp)

        /** The Lenovo Tab M11 (1280 x 800 dp) upright less its bars and navigation bar, and on its side (Phase 52). */
        private val TABLET_UPRIGHT = DpSize(800.dp, 1120.dp)
        private val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)
    }
}
