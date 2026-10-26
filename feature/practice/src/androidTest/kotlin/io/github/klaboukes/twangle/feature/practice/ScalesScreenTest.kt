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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextLayoutResult
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
import io.github.klaboukes.twangle.core.dsp.scale.Scale
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.Navigation
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertMenuUnder
import io.github.klaboukes.twangle.core.testing.assertNoWordBroken
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.checkedLanguages
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
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
 * The Scales tool (Phase 32, a tool of its own since Phase 47) in each style, light and dark: it
 * shares its screen and root with Chords, it opens on the saved scale, the name button's panel picks
 * another (and reports it to be saved) and closes so the neck shows it, the neck follows the picked tuning and notation, TalkBack
 * hears the panel's grids as radio groups, the name as a heading and a list and the
 * neck in words, and it all fits: without scrolling upright and on its side at normal text, wholly
 * shown at 2× text, the panel too; the neck lies upright where that is clearly bigger (a phone held upright), else sideways.
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
    private val shownNavigation = mutableStateOf(Navigation.BUTTONS)

    /** The phone's bars around the window: the status bar, and on its side the navigation bar too. */
    private fun bars() = SystemBars.tab(onItsSide = shownSize.value.width > shownSize.value.height, shownNavigation.value)
    private val shownFontScale = mutableStateOf(1f)
    private val shownDark = mutableStateOf(false)
    private val shownLocale = mutableStateOf("en")
    private val shownTuning = mutableStateOf(Tunings.STANDARD)
    private val shownNotation = mutableStateOf(Notation.SHARPS)
    private val shownScales = mutableStateOf(true)
    private val savedRoots = mutableListOf<PitchClass>()
    private val savedTypes = mutableListOf<ScaleType>()

    private fun show(root: PitchClass = PitchClass.C, type: ScaleType = ScaleType.MAJOR) {
        compose.setContent {
            PhoneScreen(shownSize.value, bars(), shownFontScale.value, shownLocale.value) {
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
                        header = { PracticeToolButton(if (shownScales.value) PracticeTool.SCALES else PracticeTool.CHORDS, {}, TOOLS) },
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        scales = shownScales.value,
                        savedScaleType = type,
                        onScaleTypeChange = { savedTypes += it },
                        onPickInstrument = {},
                    )
                }
            }
        }
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag)
    private fun neck() = node(PracticeTestTags.NECK)
    private fun spokenNeck(): String = neck().fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString()
    private fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun picker() = node(PracticeTestTags.NAME_PICKER)

    /** On Scales the name button says the scale's full [name] in words, nothing under it. */
    private fun assertName(name: String) {
        picker().assertTextEquals(name)
        assertFalse("no words under a scale's name", exists(PracticeTestTags.NAME_WORDS))
    }

    /** On Chords the name button says [symbol], the words in it under the symbol [words]. */
    private fun assertChordName(symbol: String, words: String) {
        picker().assertTextEquals(symbol)
        node(PracticeTestTags.NAME_WORDS).assertTextEquals(words)
    }

    /** How wide the name's first "o" is set, in pixels: its size (the layout's style keeps the size asked, not the one fitted). Every name tried has one. */
    private fun TextLayoutResult.letterWidth(): Float = getBoundingBox(layoutInput.text.text.indexOf('o')).width

    /** The name as the button sets it: its lines and its size. */
    private fun nameLayout(): TextLayoutResult {
        val text = compose.onNode(
            hasAnyAncestor(hasTestTag(PracticeTestTags.NAME_PICKER)) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        text.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    /** Opens the panel and waits for it: a window of its own. */
    private fun openPanel() {
        picker().performClick()
        compose.waitUntil(WAIT_MS) { exists(PracticeTestTags.root(PitchClass.C)) }
    }

    /** Closes the panel with Back and waits for it to go: Back with it shut would close the activity. */
    private fun closePanel() {
        Espresso.pressBack()
        awaitPanelClosed()
    }

    /** Waits for the panel to go: a window of its own, it goes with a short animation. */
    private fun awaitPanelClosed() {
        compose.waitUntil(WAIT_MS) { !exists(PracticeTestTags.root(PitchClass.C)) }
    }

    /** For each of [options]: opens the panel, taps it there, and waits for the panel to close, as a pick closes it. */
    private fun pick(vararg options: String) {
        for (option in options) {
            openPanel()
            node(option).performScrollTo().performClick()
            awaitPanelClosed()
        }
    }

    /** Opens the panel, checks that each of [options] is picked, and closes it. */
    private fun assertPicked(vararg options: String) {
        openPanel()
        for (option in options) node(option).assertIsSelected()
        closePanel()
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
        assertName("A minor pentatonic")
        node(PracticeTestTags.TOOL_BUTTON).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Scales"))
        assertPicked(PracticeTestTags.root(PitchClass.A), PracticeTestTags.scale(ScaleType.MINOR_PENTATONIC))
        assertFalse("no chord chart", exists(PracticeTestTags.CHART))
        assertFalse("no pager", exists(PracticeTestTags.NEXT))

        pick(PracticeTestTags.root(PitchClass.G))
        assertTrue(spokenNeck(), spokenNeck().startsWith("G minor pentatonic, notes G, B flat, C, D, F, "))
        assertName("G minor pentatonic")
        pick(PracticeTestTags.scale(ScaleType.MAJOR))
        assertTrue(spokenNeck(), spokenNeck().startsWith("G major, notes G, A, B, C, D, E, F sharp, E 2 string, open E, fret 2 F sharp, fret 3 G, root"))
        assertName("G major")
        // The natural minor scale is just "minor", as players say it.
        pick(PracticeTestTags.scale(ScaleType.MAJOR_PENTATONIC))
        assertName("G major pentatonic")
        pick(PracticeTestTags.scale(ScaleType.NATURAL_MINOR))
        assertName("G minor")
        pick(PracticeTestTags.scale(ScaleType.MAJOR))
        openPanel()
        node(PracticeTestTags.scale(ScaleType.MAJOR)).assertIsSelected()
        node(PracticeTestTags.scale(ScaleType.MINOR_PENTATONIC)).assertIsNotSelected()
        closePanel()
        assertEquals("the picks are passed on to be saved", listOf(PitchClass.G), savedRoots)
        assertEquals(listOf(ScaleType.MAJOR, ScaleType.MAJOR_PENTATONIC, ScaleType.NATURAL_MINOR, ScaleType.MAJOR), savedTypes)
    }

    /**
     * Any pick closes the panel, so the neck shows the new scale at once (the owner's report of 2026-10-26):
     * a scale, a root, and the scale already picked, which changes nothing.
     */
    @Test
    fun aPickClosesThePanelAndTheNeckShowsIt() {
        show(PitchClass.C, ScaleType.MAJOR)
        compose.waitForIdle()
        node(PracticeTestTags.TOOL_BUTTON).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Scales"))
        openPanel()
        node(PracticeTestTags.scale(ScaleType.MINOR_PENTATONIC)).performScrollTo().performClick()
        awaitPanelClosed()
        assertName("C minor pentatonic")
        assertTrue(spokenNeck(), spokenNeck().startsWith("C minor pentatonic, notes C, E flat, F, G, B flat, "))
        assertTrue("the neck shows", neck().fetchSemanticsNode().boundsInRoot.height > 0f)
        openPanel()
        node(PracticeTestTags.root(PitchClass.A)).performClick()
        awaitPanelClosed()
        assertName("A minor pentatonic")
        assertTrue(spokenNeck(), spokenNeck().startsWith("A minor pentatonic, notes A, C, D, E, G, "))
        openPanel()
        node(PracticeTestTags.scale(ScaleType.MINOR_PENTATONIC)).performScrollTo().performClick()
        awaitPanelClosed()
        assertName("A minor pentatonic")
        assertEquals(listOf(PitchClass.A), savedRoots)
        assertEquals("the scale already picked isn't saved again", listOf(ScaleType.MINOR_PENTATONIC), savedTypes)
    }

    /**
     * The modes and the minors (Phase 47), Phrygian dominant and major blues (Phase 49): each with its full name on the button,
     * spelled in its parent major key (B♭ Mixolydian with E♭ major's A♭), the minor key with its raised notes, Phrygian dominant
     * in the harmonic minor's a fourth above (E F G♯ …), major blues in the major key with its blue note flat (G A B♭ B …); the
     * colour note's places marked for TalkBack as the neck rings them, and none for the scales without one.
     */
    @Test
    fun theModesAndTheMinorsWithTheirColourNote() {
        show(PitchClass.D, ScaleType.MAJOR)
        compose.waitForIdle()
        pick(PracticeTestTags.scale(ScaleType.DORIAN))
        assertName("D Dorian")
        assertTrue(spokenNeck(), spokenNeck().startsWith("D Dorian, notes D, E, F, G, A, B, C, E 2 string, open E, fret 1 F, fret 3 G, fret 5 A, fret 7 B, colour note, fret 8 C"))
        class Case(val root: PitchClass, val type: ScaleType, val words: String, val notes: String)
        val cases = listOf(
            Case(PitchClass.E, ScaleType.PHRYGIAN, "E Phrygian", "E, F, G, A, B, C, D"),
            Case(PitchClass.F, ScaleType.LYDIAN, "F Lydian", "F, G, A, B, C, D, E"),
            Case(PitchClass.A_SHARP, ScaleType.MIXOLYDIAN, "B♭ Mixolydian", "B flat, C, D, E flat, F, G, A flat"),
            Case(PitchClass.F_SHARP, ScaleType.LOCRIAN, "F♯ Locrian", "F sharp, G, A, B, C, D, E"),
            Case(PitchClass.D, ScaleType.HARMONIC_MINOR, "D harmonic minor", "D, E, F, G, A, B flat, C sharp"),
            Case(PitchClass.A, ScaleType.MELODIC_MINOR, "A melodic minor", "A, B, C, D, E, F sharp, G sharp"),
            Case(PitchClass.A, ScaleType.NATURAL_MINOR, "A minor", "A, B, C, D, E, F, G"),
            Case(PitchClass.E, ScaleType.PHRYGIAN_DOMINANT, "E Phrygian dominant", "E, F, G sharp, A, B, C, D"),
            Case(PitchClass.G, ScaleType.MAJOR_BLUES, "G major blues", "G, A, B flat, B, D, E"),
        )
        for (case in cases) {
            pick(PracticeTestTags.root(case.root), PracticeTestTags.scale(case.type))
            assertName(case.words)
            val spoken = spokenNeck()
            assertTrue(spoken, spoken.startsWith("${speechOf(case.words)}, notes ${case.notes}, "))
            assertEquals("${case.type}: a colour note marked or none", Scale(case.root, case.type).colourNote != null, spoken.contains(", colour note"))
        }
    }

    /** [words] as TalkBack's note names say them: "B♭ Mixolydian" is said "B flat Mixolydian". */
    private fun speechOf(words: String) = words.replace("♭", " flat").replace("♯", " sharp")

    @Test
    fun chordsAndScalesShareTheRoot() {
        // The Practice tab shows both tools as this one screen with one saved state, so the root carries across.
        shownScales.value = false
        show(PitchClass.D, ScaleType.BLUES)
        compose.waitUntil(WAIT_MS) { exists(PracticeTestTags.CHART) }
        assertFalse("chords first", exists(PracticeTestTags.NECK))
        node(PracticeTestTags.TOOL_BUTTON).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Chords"))

        shownScales.value = true
        compose.waitForIdle()
        assertTrue("the neck shows", exists(PracticeTestTags.NECK))
        assertFalse("the chord chart has gone", exists(PracticeTestTags.CHART))
        assertName("D blues")
        assertPicked(PracticeTestTags.root(PitchClass.D))
        pick(PracticeTestTags.root(PitchClass.E))

        shownScales.value = false
        compose.waitUntil(WAIT_MS) { exists(PracticeTestTags.CHART) }
        assertChordName("E", "E major")
        assertPicked(PracticeTestTags.root(PitchClass.E), PracticeTestTags.type(ChordQuality.MAJOR))
        assertEquals(listOf(PitchClass.E), savedRoots)
    }

    @Test
    fun talkBackHearsThePanelAsRadioButtons() {
        show(PitchClass.A_SHARP, ScaleType.MAJOR)
        compose.waitForIdle()
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        val list = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)
        picker().assert(list).assert(isHeading()).assert(hasContentDescription("B flat major")).assertTextEquals("B♭ major")
        assertFalse("no words under it", exists(PracticeTestTags.NAME_WORDS))
        assertTrue("the scale's name is a polite live region", picker().fetchSemanticsNode().config.contains(SemanticsProperties.LiveRegion))
        openPanel()
        node(PracticeTestTags.scale(ScaleType.MINOR_PENTATONIC)).assert(radio).assert(hasContentDescription("Minor pentatonic"))
        node(PracticeTestTags.scale(ScaleType.MAJOR)).assert(radio).assert(hasContentDescription("Major")).assertTextEquals("Major")
        node(PracticeTestTags.scale(ScaleType.NATURAL_MINOR)).assert(radio).assert(hasContentDescription("Minor")).assertTextEquals("Minor")
        node(PracticeTestTags.scale(ScaleType.MELODIC_MINOR)).assert(radio).assert(hasContentDescription("Melodic minor"))
        node(PracticeTestTags.scale(ScaleType.MAJOR_BLUES)).assert(radio).assert(hasContentDescription("Major blues")).assertTextEquals("Major blues")
        node(PracticeTestTags.scale(ScaleType.PHRYGIAN_DOMINANT)).assert(radio).assert(hasContentDescription("Phrygian dominant")).assertTextEquals("Phrygian dominant")
        // All fourteen one radio group, "Scale", with no headers.
        for (type in ScaleType.entries) {
            node(PracticeTestTags.scale(type)).assert(hasParent(hasContentDescription("Scale") and SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)))
        }
        for (header in listOf("Modes", "Pentatonic and blues")) assertFalse(header, compose.onAllNodesWithText(header).fetchSemanticsNodes().isNotEmpty())
        node(PracticeTestTags.root(PitchClass.A_SHARP)).assert(radio).assert(hasContentDescription("B flat")).assertTextEquals("B♭")
        closePanel()
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
        assertName("Re major")
        openPanel()
        node(PracticeTestTags.root(PitchClass.D)).assertTextEquals("Re")
        node(PracticeTestTags.root(PitchClass.F_SHARP)).assertTextEquals("Fa♯")
        closePanel()
    }

    @Test
    fun itFitsWithoutScrollingUprightAndOnItsSideInLightAndDark() {
        show(PitchClass.G_SHARP, ScaleType.BLUES)
        for (dark in listOf(false, true)) for (size in listOf(XIAOMI, PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownDark.value = dark
            shownSize.value = size
            compose.waitForIdle()
            val case = "$style ${if (dark) "dark" else "light"} $size:"
            val window = bars().safeArea(size)
            val tags = listOf(
                PracticeTestTags.TOOL_BUTTON, PracticeTestTags.TUNING, PracticeTestTags.NAME_PICKER,
            )
            for (tag in tags) {
                assertWhollyShown(node(tag), "$case $tag", scroll = false)
                val bounds = node(tag).getBoundsInRoot()
                assertTrue("$case $tag inside the window $bounds", bounds.top >= window.top && bounds.bottom <= window.bottom)
            }
            // Upright on the Xiaomi (the owner's choice), sideways on its side; a short phone takes whichever is bigger (sideways).
            val lie = lie()
            if (size == XIAOMI) assertEquals("$case how the neck lies", NeckLie.UPRIGHT, lie)
            if (size == PHONE_LANDSCAPE) assertEquals("$case how the neck lies", NeckLie.SIDEWAYS, lie)
            val drawn = node(PracticeTestTags.neckLie(lie)).getBoundsInRoot()
            val area = neck().getBoundsInRoot()
            assertTrue("$case the neck inside its room $drawn $area", drawn.left >= area.left && drawn.right <= area.right && drawn.top >= area.top && drawn.bottom <= area.bottom)
            assertTrue("$case inside the window", drawn.bottom <= window.bottom)
            assertTrue("$case a big neck $drawn", maxOf(drawn.width, drawn.height) >= 300.dp)
            val tuning = node(PracticeTestTags.TUNING).getBoundsInRoot()
            val name = picker().getBoundsInRoot()
            assertTrue("$case the name over the neck", name.bottom <= area.top + 1.dp)
            if (size == PHONE_LANDSCAPE) {
                val middle = (window.left + window.right) / 2
                assertTrue("$case the tuning line in the start half", tuning.right <= middle)
                assertTrue("$case the name and the neck in the end half", name.left >= middle && area.left >= middle)
            } else {
                assertTrue("$case the tuning line over the name", tuning.bottom <= name.top + 1.dp)
            }
            assertThePanel(case, wide = size == PHONE_LANDSCAPE)
        }
    }

    /**
     * The panel under the name where it fits (where not [wide]): the roots in two rows of six, then the fourteen scales in one list,
     * the most used first, two to a row in seven full rows, one right under the other (no headers between them), all of one size,
     * each wholly shown (scrolling the panel where it must; with [texts], every text in it too); Back closes it.
     */
    private fun assertThePanel(case: String, wide: Boolean, texts: Boolean = false) {
        fun top(tag: String) = node(tag).fetchSemanticsNode().positionInRoot.y
        fun scale(type: ScaleType) = PracticeTestTags.scale(type)
        openPanel()
        val roots = top(PracticeTestTags.root(PitchClass.F_SHARP))
        assertTrue("$case roots in two rows of six", top(PracticeTestTags.root(PitchClass.F)) == top(PracticeTestTags.root(PitchClass.C)) && roots > top(PracticeTestTags.root(PitchClass.C)))
        assertTrue("$case the scales under the roots", top(scale(ScaleType.MAJOR)) > roots)
        val rows = ROWS.map { (first, second) ->
            val y = top(scale(first))
            assertEquals("$case $first and $second on one row", y, top(scale(second)))
            y
        }
        val steps = rows.zipWithNext { a, b -> b - a }
        assertEquals("$case every scale in a row", ScaleType.entries.toSet(), ROWS.flatMap { it.toList() }.toSet())
        assertTrue("$case seven rows, each right under the one before $rows", rows.size == 7 && steps.all { it > 0f } && steps.toSet().size == 1)
        val sizes = ScaleType.entries.map { node(scale(it)).fetchSemanticsNode().size }.toSet()
        assertEquals("$case every scale button of one size", 1, sizes.size)
        // On its side it opens over the start half; where can't be told here: the forced size scales the screen, not the popup's window.
        if (!wide) assertMenuUnder(picker(), node(PracticeTestTags.root(PitchClass.C)), case)
        for (pitch in PitchClass.entries) assertWhollyShown(node(PracticeTestTags.root(pitch)), "$case root $pitch")
        for (type in ScaleType.entries) assertWhollyShown(node(PracticeTestTags.scale(type)), "$case scale $type")
        if (texts) {
            compose.assertEveryTextWhollyShown("$case panel:")
            compose.assertNoWordBroken("$case panel:")
        }
        closePanel()
        assertFalse("$case Back closes the panel", exists(PracticeTestTags.scale(ScaleType.MAJOR)))
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
            for (tag in listOf(PracticeTestTags.TUNING, PracticeTestTags.NAME_PICKER, PracticeTestTags.NECK)) {
                assertWhollyShown(node(tag), "$style nl $size $tag at 2x")
            }
            compose.assertEveryTextWhollyShown("$style nl $size at 2x:")
            compose.assertNoWordBroken("$style nl $size at 2x:")
            assertThePanel("$style nl $size at 2x:", wide = size == PHONE_LANDSCAPE, texts = true)
        }
    }

    /**
     * The name is one button as wide as the row (Phase 47) with the scale's full name in it, large on one line ("D Dorian",
     * "G♯ minor pentatonic" on the Xiaomi), on two lines set smaller only where one doesn't fit (French "Sol♯ pentatonique
     * mineure", Dutch "Sol♯ mineur-pentatonisch" at 2× text on a 320 dp phone and on its side), and one steady height whatever
     * the name, so the neck never moves.
     */
    @Test
    fun theNameIsOneWideButtonOfOneSteadyHeight() {
        class Case(val fontScale: Float, val size: DpSize, val language: String)
        show()
        for (case in listOf(Case(1f, XIAOMI, "en"), Case(2f, NARROW_UPRIGHT, "fr"), Case(2f, PHONE_LANDSCAPE, "nl"))) {
            shownFontScale.value = case.fontScale
            shownSize.value = case.size
            shownLocale.value = case.language
            shownNotation.value = if (case.fontScale > 1f) Notation.SOLFEGE else Notation.SHARPS
            val name = "$style ${case.language} ${case.size} at ${case.fontScale}x:"
            pick(PracticeTestTags.root(PitchClass.C), PracticeTestTags.scale(ScaleType.MAJOR))
            val short = picker().getBoundsInRoot()
            val neckTop = neck().getBoundsInRoot().top
            pick(PracticeTestTags.root(PitchClass.D), PracticeTestTags.scale(ScaleType.DORIAN))
            val dorian = nameLayout()
            if (case.fontScale == 1f) assertEquals("$name D Dorian on one line", 1, dorian.lineCount)
            for (type in listOf(ScaleType.MINOR_PENTATONIC, ScaleType.HARMONIC_MINOR, ScaleType.PHRYGIAN_DOMINANT)) {
                pick(PracticeTestTags.root(PitchClass.G_SHARP), PracticeTestTags.scale(type))
                val long = picker().getBoundsInRoot()
                val what = "$name G♯ $type"
                assertEquals("$what one steady height", short.height.value, long.height.value, 0.5f)
                assertEquals("$what the neck stays put", neckTop.value, neck().getBoundsInRoot().top.value, 0.5f)
                val layout = nameLayout()
                assertTrue("$what on one or two lines", layout.lineCount in 1..2)
                if (case.fontScale == 1f) {
                    assertEquals("$what on one line on the Xiaomi", 1, layout.lineCount)
                    assertEquals("$what as large as D Dorian", dorian.letterWidth(), layout.letterWidth(), 0.5f)
                } else if (layout.lineCount == 2) {
                    assertTrue("$what set smaller on two lines: ${layout.letterWidth()} px an o, D Dorian ${dorian.letterWidth()}", layout.letterWidth() < dorian.letterWidth() - 0.5f)
                }
                if (case.size.width < case.size.height) {
                    val tool = node(PracticeTestTags.TOOL_BUTTON).getBoundsInRoot()
                    assertEquals("$what at the row's start", tool.left.value, long.left.value, 1f)
                    assertTrue("$what as wide as the row ${long.width}", long.width >= case.size.width - 32.dp - 1.dp)
                }
                assertWhollyShown(picker(), "$what the name")
                compose.assertEveryTextWhollyShown(what)
                compose.assertNoWordBroken(what)
            }
        }
    }

    /**
     * Every language at 2× text, upright on a 320 dp phone and on its side, the grid too, with the longest full names on the
     * name button, each in the one steady height: G♯ minor and major pentatonic, harmonic and melodic minor, Mixolydian (the
     * longest of the modes), Phrygian dominant and major blues. Only with `--languages`.
     */
    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        show(PitchClass.G_SHARP, ScaleType.MINOR_PENTATONIC)
        var shown = ScaleType.MINOR_PENTATONIC
        for (language in checkedLanguages) {
            shownLocale.value = language
            for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                compose.waitForIdle()
                assertThePanel("$style $language $size at 2x:", wide = size == PHONE_LANDSCAPE, texts = true)
                val height = picker().getBoundsInRoot().height
                // The one shown first, so none is picked twice.
                for (type in LONG_NAMES.sortedBy { it != shown }) {
                    if (type != shown) pick(PracticeTestTags.scale(type)).also { shown = type }
                    val what = "$style $language $size G♯ $type at 2x:"
                    assertEquals("$what one steady height", height.value, picker().getBoundsInRoot().height.value, 0.5f)
                    assertTrue("$what on one or two lines", nameLayout().lineCount in 1..2)
                    assertWhollyShown(picker(), "$what the name")
                    compose.assertEveryTextWhollyShown(what)
                    compose.assertNoWordBroken(what)
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        /** The panel's rows, two scales each, the most used first. */
        private val ROWS = listOf(
            ScaleType.MAJOR to ScaleType.NATURAL_MINOR,
            ScaleType.MAJOR_PENTATONIC to ScaleType.MINOR_PENTATONIC,
            ScaleType.BLUES to ScaleType.MAJOR_BLUES,
            ScaleType.DORIAN to ScaleType.MIXOLYDIAN,
            ScaleType.HARMONIC_MINOR to ScaleType.LYDIAN,
            ScaleType.PHRYGIAN to ScaleType.PHRYGIAN_DOMINANT,
            ScaleType.MELODIC_MINOR to ScaleType.LOCRIAN,
        )

        /** The longest full names in most languages. */
        private val LONG_NAMES = listOf(
            ScaleType.MINOR_PENTATONIC, ScaleType.MAJOR_PENTATONIC, ScaleType.HARMONIC_MINOR, ScaleType.MELODIC_MINOR, ScaleType.MIXOLYDIAN,
            ScaleType.PHRYGIAN_DOMINANT, ScaleType.MAJOR_BLUES,
        )

        private val TOOLS = listOf(PracticeTool.METRONOME, PracticeTool.CHORDS, PracticeTool.SCALES, PracticeTool.CAPO)
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
