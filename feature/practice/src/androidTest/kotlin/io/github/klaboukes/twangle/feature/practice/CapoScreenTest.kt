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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.NAME_PICKER_HEIGHT
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.capo.Capo
import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.testing.Navigation
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertNoWordBroken
import io.github.klaboukes.twangle.core.testing.assertPanelUnder
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

/**
 * The Capo helper (Phase 33) in Ink, light and dark: it opens on the saved shapes and fret,
 * ‹ › move the capo and the list picks the shapes (both reported to be saved), the table shows what
 * each shape sounds in the player's notation, a row opens its shape, TalkBack hears every part by
 * what it says or does, and the whole tool fits: without scrolling upright and on its side at
 * normal text, wholly shown at 2× text.
 */
@RunWith(AndroidJUnit4::class)
class CapoScreenTest {

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
    private val shownChords = mutableStateOf(false)
    private val savedShapes = mutableListOf<SongKey>()
    private val savedFrets = mutableListOf<Int>()
    private val opened = mutableListOf<Chord>()
    private var instrumentTaps = 0

    private fun show(shapes: SongKey = SongKey(PitchClass.C), fret: Int = 0) {
        compose.setContent {
            PhoneScreen(shownSize.value, bars(), shownFontScale.value, shownLocale.value) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
                    // Chords in its place, to compare where the two pills stand.
                    if (shownChords.value) {
                        ChordsScreen(
                            tuning = Tunings.STANDARD,
                            tuningName = "Guitar · Standard",
                            spokenTuningName = "Guitar, Standard",
                            notation = shownNotation.value,
                            savedRoot = PitchClass.C,
                            savedQuality = ChordQuality.MAJOR,
                            onRootChange = {},
                            onQualityChange = {},
                            onBack = {},
                            modifier = Modifier.fillMaxSize().testTag(WINDOW),
                            onPickInstrument = {},
                        )
                        return@TwangleTheme
                    }
                    CapoScreen(
                        tuningName = "Guitar · Standard",
                        spokenTuningName = "Guitar, Standard",
                        notation = shownNotation.value,
                        savedShapes = shapes,
                        savedFret = fret,
                        onShapesChange = { savedShapes += it },
                        onFretChange = { savedFrets += it },
                        onOpenChord = { opened += it },
                        onBack = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        onPickInstrument = { instrumentTaps++ },
                        shortString = shownShortString.value,
                    )
                }
            }
        }
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag)

    /**
     * [tag]'s bounds in the window's dp. A node's own bounds are in its own dp, and upright on a tablet the top row is
     * drawn bigger (TabletTop), with that many more pixels to its dp.
     */
    private fun windowBounds(tag: String): DpRect {
        val pxPerDp = node(WINDOW).fetchSemanticsNode().layoutInfo.density.density
        val r = node(tag).fetchSemanticsNode().boundsInRoot
        return DpRect((r.left / pxPerDp).dp, (r.top / pxPerDp).dp, (r.right / pxPerDp).dp, (r.bottom / pxPerDp).dp)
    }
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
            val case = "$language ${scale}x $size:"
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

        // Every shape is offered, on the shared pick buttons (Phase 77): the five major shapes in one row, the three minor
        // ones under them, by their symbols, heard in words as radio buttons, the one picked marked; under the pill from
        // its start and as wide as it.
        node(PracticeTestTags.CAPO_SHAPES).performClick()
        compose.waitForIdle()
        val pill = node(PracticeTestTags.CAPO_SHAPES).getBoundsInRoot()
        val panel = node(PracticeTestTags.CAPO_SHAPES_PANEL).assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Shapes I play"))
            .getBoundsInRoot()
        assertPanelUnder(node(PracticeTestTags.CAPO_SHAPES), node(PracticeTestTags.CAPO_SHAPES_PANEL), node(WINDOW), "the shapes")
        val caption = compose.onNodeWithText("Shapes I play").getBoundsInRoot()
        assertTrue("under the caption, which stays readable: $panel $caption", panel.top >= caption.bottom)
        assertEquals("from the pill's start", pill.left.value, panel.left.value, 0.5f)
        assertEquals("as wide as the pill", pill.width.value, panel.width.value, 1f)
        val symbols = listOf("C", "D", "E", "G", "A", "Dm", "Em", "Am")
        for ((shapes, symbol) in CAPO_SHAPES.zip(symbols)) {
            val words = (if (shapes.minor) "minor" else "major").let { "${shapes.tonic.name.first()} $it" }
            node(PracticeTestTags.capoShapes(shapes))
                .assert(hasText(symbol))
                .assert(hasContentDescription(words))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            assertWhollyShown(node(PracticeTestTags.capoShapes(shapes)), "shapes $symbol", scroll = false)
        }
        node(PracticeTestTags.capoShapes(SongKey(PitchClass.A, minor = true))).assertIsSelected()
        node(PracticeTestTags.capoShapes(SongKey(PitchClass.C))).assertIsNotSelected()
        fun top(shapes: SongKey) = node(PracticeTestTags.capoShapes(shapes)).getBoundsInRoot().top
        val majors = CAPO_SHAPES.filter { !it.minor }.map(::top)
        val minors = CAPO_SHAPES.filter { it.minor }.map(::top)
        assertTrue("the majors in one row, the minors under them: $majors $minors", majors.distinct().size == 1 && minors.distinct().size == 1 && minors[0] > majors[0])
        node(PracticeTestTags.capoShapes(SongKey(PitchClass.G))).performClick()
        compose.waitForIdle()
        assertFalse("a pick closes it", exists(PracticeTestTags.CAPO_SHAPES_PANEL))
        assertEquals("the pill stays put", pill, node(PracticeTestTags.CAPO_SHAPES).getBoundsInRoot())
        assertEquals(
            rows("G major" to "B flat major", "A minor" to "C minor", "B minor" to "D minor", "C major" to "E flat major", "D major" to "F major", "E minor" to "G minor"),
            table(SongKey(PitchClass.G)),
        )
    }

    /**
     * The shapes' pill stands where Chords has its name (Phase 77), so switching between the tools moves nothing: upright
     * right under the top row, as wide and in the same place, also on a short screen and a tablet; on a tablet on its side
     * in the top row's row; on a phone on its side exactly where Chords' is too (until 2026-11-04 it rose about 16 dp on
     * the Xiaomi to leave the capo and "Sounds in" room under it; "Sounds in" now stands under the table), on a short one,
     * the Xiaomi's and a taller one. "Shapes I play" stands small under its face.
     */
    @Test
    fun theShapesPillStandsWhereChordsHasItsName() {
        show(SongKey(PitchClass.C), 3)
        val caption = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.practice_capo_shapes)
        for (size in listOf(XIAOMI, PHONE_UPRIGHT, NARROW_UPRIGHT, TABLET_UPRIGHT, TABLET_LANDSCAPE, PHONE_LANDSCAPE, XIAOMI_LANDSCAPE, TALL_LANDSCAPE)) {
            shownSize.value = size
            shownChords.value = true
            compose.waitForIdle()
            val chords = node(PracticeTestTags.NAME_PICKER).getBoundsInRoot()
            shownChords.value = false
            compose.waitForIdle()
            val capo = node(PracticeTestTags.CAPO_SHAPES).getBoundsInRoot()
            assertEquals("$size: from where Chords' name starts (a pixel apart at most)", chords.left.value, capo.left.value, 1f)
            assertEquals("$size: as wide as Chords' name", chords.width.value, capo.width.value, 1f)
            assertEquals("$size: as high as Chords' name", chords.top.value, capo.top.value, 0.5f)
            val under = compose.onNodeWithText(caption).getBoundsInRoot()
            assertTrue("$size: the caption under the face $under $capo", under.top >= capo.bottom && under.top <= capo.bottom + 8.dp)
            if (size.width > size.height && size.width < TABLET_LANDSCAPE.width) {
                // A phone on its side: the capo in the middle of the height under the caption, "Sounds in" under the table.
                val stepper = node(PracticeTestTags.CAPO_HIGHER).getBoundsInRoot()
                val window = bars().safeArea(size)
                val middle = (under.bottom + window.bottom - 8.dp) / 2
                assertTrue("$size: the capo under the caption $stepper $under", stepper.top >= under.bottom)
                assertEquals("$size: the capo in the middle of the height under the caption", middle.value, ((stepper.top + stepper.bottom) / 2).value, 1.5f)
                val table = node(PracticeTestTags.capoRow(Chord.of(PitchClass.A, ChordQuality.MINOR))).getBoundsInRoot()
                val soundsIn = node(PracticeTestTags.CAPO_SOUNDS_IN).getBoundsInRoot()
                assertTrue("$size: Sounds in under the table, in the end half $soundsIn $table $stepper", soundsIn.top >= table.bottom && soundsIn.left >= stepper.right)
            }
        }
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
        // The picker chip (Phase 70), heard as the tuner's: the instrument and tuning, a button that changes them.
        node(PracticeTestTags.TUNING)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasContentDescription("Guitar, Standard"))
            .assert(SemanticsMatcher("double-tap changes the instrument or tuning") { it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Change instrument or tuning" })
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
    fun itFitsWithoutScrollingUprightAndOnItsSideInLightAndDark() = assertItFits(listOf(XIAOMI, PHONE_UPRIGHT, PHONE_LANDSCAPE, XIAOMI_LANDSCAPE), listOf(false, true))

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
            val case = "${if (dark) "dark" else "light"} $size:"
            val window = bars().safeArea(size)
            for (tag in listOf(PracticeTestTags.BACK, PracticeTestTags.TUNING, PracticeTestTags.CAPO_SHAPES, PracticeTestTags.CAPO_LOWER, PracticeTestTags.CAPO_FRET, PracticeTestTags.CAPO_HIGHER, last, PracticeTestTags.CAPO_SOUNDS_IN)) {
                assertWhollyShown(node(tag), "$case $tag", scroll = false)
                val bounds = windowBounds(tag)
                assertTrue("$case $tag inside the window $bounds", bounds.top >= window.top && bounds.bottom <= window.bottom)
            }
            // One row, every tab's: the back arrow at the start, the picker chip in the middle (Phase 47, 69, 70; beside the
            // arrow until 2026-11-04), as on the tuner.
            val backArrow = windowBounds(PracticeTestTags.BACK)
            val instrument = windowBounds(PracticeTestTags.TUNING)
            // The tool's name in the top row beside the back arrow, the chip on its own line under it (Phase 77).
            val title = windowBounds(PracticeTestTags.TITLE)
            assertTrue(
                "$case the tool's name beside the back arrow $backArrow $title",
                title.left >= backArrow.right && title.top < backArrow.bottom && title.bottom > backArrow.top,
            )
            assertTrue("$case the tuning on its own line under the top row $backArrow $instrument", instrument.top >= backArrow.bottom)
            // Each centred in whole pixels, so they can be a pixel apart: on a tablet's window forced onto the Xiaomi
            // (0.8 px a dp) that is 1.2 dp (the overnight run of 2026-11-05, after Nunito changed the name's width).
            val near = maxOf(1f, 1.05f / node(PracticeTestTags.TUNING).fetchSemanticsNode().layoutInfo.density.density)
            assertEquals("$case the chip under the name", ((title.left + title.right) / 2).value, ((instrument.left + instrument.right) / 2).value, near)
            if (size.width < size.height) {
                assertEquals("$case the chip in the window's middle", ((window.left + window.right) / 2).value, ((instrument.left + instrument.right) / 2).value, near)
            }
            val middle = (window.left + window.right) / 2
            if (size.width > size.height) {
                assertTrue("$case the capo in the start half", node(PracticeTestTags.CAPO_HIGHER).getBoundsInRoot().right <= middle)
                assertTrue("$case the table in the end half", node(last).getBoundsInRoot().left >= middle)
            }
            val tool = node(PracticeTestTags.BACK).getBoundsInRoot()
            assertTrue("$case the back arrow at the top $tool", tool.top >= window.top && tool.top <= window.top + 16.dp)
            if (size == TABLET_UPRIGHT || size == TABLET_LANDSCAPE) {
                // A tablet (Phase 52): the part under the pill (right under the top row since Phase 77, or in its row on
                // its side) in the middle of the height left, not hanging at the top.
                val first = if (size == TABLET_UPRIGHT) node(PracticeTestTags.CAPO_LOWER) else node(PracticeTestTags.capoRow(Chord.of(PitchClass.C, ChordQuality.MAJOR)))
                val caption = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.practice_capo_shapes)
                val above = first.getBoundsInRoot().top - compose.onNodeWithText(caption).getBoundsInRoot().bottom
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
                assertWhollyShown(node(tag), "nl $size $tag at 2x")
            }
            compose.assertEveryTextWhollyShown("nl $size at 2x:")
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
                    compose.assertEveryTextWhollyShown("$language $size fret $fret at 2x:")
                    compose.assertNoWordBroken("$language $size fret $fret at 2x:")
                }
            }
        }
    }


    /**
     * The banjo's short fifth string (Phase 57): the table's place says why Capo leaves it out, the shapes, the
     * capo and "Sounds in" stay where they are; another instrument brings the table straight back. Since Phase 65
     * the capo's buttons rest and "Sounds in" is heard as unavailable, and a button under the line opens the
     * instrument panel. Upright and on its side, at normal and 2x text.
     */
    @Test
    fun theBanjoGetsAQuietLineInTheTablesPlace() {
        val line = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.practice_capo_short_string)
        show(SongKey(PitchClass.C), 3)
        val firstRow = PracticeTestTags.capoRow(Chord.of(PitchClass.C, ChordQuality.MAJOR))
        for (scale in listOf(1f, 2f)) for (size in listOf(XIAOMI, PHONE_LANDSCAPE)) {
            val case = "$size at ${scale}x"
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
            node(PracticeTestTags.CAPO_LOWER).assertIsNotEnabled()
            node(PracticeTestTags.CAPO_HIGHER).assertIsNotEnabled()
            node(PracticeTestTags.CAPO_SOUNDS_IN).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
            val taps = instrumentTaps
            assertWhollyShown(node(PracticeTestTags.SHORT_STRING_PICK), "$case: the button to another instrument", scroll = true)
            node(PracticeTestTags.SHORT_STRING_PICK).performClick()
            assertEquals("$case: the button opens the instrument panel", taps + 1, instrumentTaps)
            shownShortString.value = false
            compose.waitForIdle()
            node(firstRow).assertExists()
            node(PracticeTestTags.CAPO_LOWER).assertIsEnabled()
            node(PracticeTestTags.CAPO_SOUNDS_IN).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Disabled))
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
                assertWhollyShown(node(PracticeTestTags.SHORT_STRING), "$language $size at 2x: the banjo's line", scroll = true)
                compose.assertEveryTextWhollyShown("$language $size at 2x, banjo:")
                compose.assertNoWordBroken("$language $size at 2x, banjo:")
            }
        }
    }

    /** The picker chip (Phase 70) opens the "What are you tuning?" sheet; the headstock button went. */
    @Test
    fun thePickerChipOpensTheSheet() {
        show()
        node(PracticeTestTags.TUNING).assertIsDisplayed().assert(hasContentDescription("Guitar, Standard")).performClick()
        assertEquals(1, instrumentTaps)
    }
    companion object {
        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 30L

        /** The Xiaomi 12X less the status and navigation bars, a short narrow phone (360 x 720 dp with navigation buttons) less the bars, on its side less the rail, and a 320 dp phone. */
        private val XIAOMI = DpSize(393.dp, 760.dp)
        private val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
        private val NARROW_UPRIGHT = DpSize(320.dp, 640.dp)

        /** The Xiaomi on its side less the status bar, the gesture bar and the rail; a tall phone on its side (not short). */
        private val XIAOMI_LANDSCAPE = DpSize(769.dp, 345.dp)
        private val TALL_LANDSCAPE = DpSize(900.dp, 430.dp)

        /** The Lenovo Tab M11 (1280 x 800 dp) upright less its bars and navigation bar, and on its side (Phase 52). */
        private val TABLET_UPRIGHT = DpSize(800.dp, 1120.dp)
        private val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)
    }
}
