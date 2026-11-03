package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
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
import io.github.klaboukes.twangle.core.audio.DronePlay
import io.github.klaboukes.twangle.core.audio.FakeDronePlayer
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.drone.DroneSound
import io.github.klaboukes.twangle.core.dsp.drone.DroneVoicing
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.scale.Scale
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import io.github.klaboukes.twangle.core.testing.Navigation
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertMenuUnder
import io.github.klaboukes.twangle.core.testing.assertNoWordBroken
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.checkedLanguages
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * The Scales tool (Phase 32, a tool of its own since Phase 47) in Ink, light and dark: it
 * shares its screen and root with Chords, it opens on the saved scale, the name button's panel picks
 * another (and reports it to be saved) and closes so the neck shows it, the neck follows the picked tuning and notation, TalkBack
 * hears the panel's grids as radio groups, the name as a heading and a list and the
 * neck in words, and it all fits: without scrolling upright and on its side at normal text, wholly
 * shown at 2× text, the panel too; the neck lies upright where that is clearly bigger (a phone held upright), else sideways.
 * The drone (Phase 48): its split button at the end of the name's row plays the root and follows it, stops when Scales
 * goes and starts again only on a tap, and its panel's picks are heard and passed on to be saved. The banjo (Phase 59):
 * its neck with the short string faded below its start, in both hands, TalkBack naming the short string.
 */
@RunWith(AndroidJUnit4::class)
class ScalesScreenTest {

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
    private val shownLeftHanded = mutableStateOf(false)
    private val savedRoots = mutableListOf<PitchClass>()
    private val savedTypes = mutableListOf<ScaleType>()
    private val drone = FakeDronePlayer()
    private val shownA4 = mutableStateOf(440f)
    private val savedDrones = mutableListOf<DroneChoice>()

    private fun show(root: PitchClass = PitchClass.C, type: ScaleType = ScaleType.MAJOR, droneChoice: DroneChoice = DroneChoice()) {
        compose.setContent {
            PhoneScreen(shownSize.value, bars(), shownFontScale.value, shownLocale.value) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
                    ChordsScreen(
                        tuning = shownTuning.value,
                        tuningName = "Guitar · Standard",
                        spokenTuningName = "Guitar, Standard",
                        notation = shownNotation.value,
                        savedRoot = root,
                        savedQuality = ChordQuality.MAJOR,
                        onRootChange = { savedRoots += it },
                        onQualityChange = {},
                        onBack = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        scales = shownScales.value,
                        savedScaleType = type,
                        onScaleTypeChange = { savedTypes += it },
                        onPickInstrument = {},
                        drone = drone,
                        a4Hz = shownA4.value,
                        savedDrone = droneChoice,
                        onDroneChange = { savedDrones += it },
                        leftHanded = shownLeftHanded.value,
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

    /** On Scales the name button says the scale's full [name] in words. */
    private fun assertName(name: String) {
        picker().assertTextEquals(name)
    }

    /** On Chords the name button says the chord's full name in [words]. */
    private fun assertChordName(words: String) {
        picker().assertTextEquals(words)
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
        assertTrue("the Scales tool shows", exists(PracticeTestTags.NECK))
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
        assertTrue("the Scales tool shows", exists(PracticeTestTags.NECK))
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
        assertTrue("the Chords tool shows", exists(PracticeTestTags.CHART))

        shownScales.value = true
        compose.waitForIdle()
        assertTrue("the neck shows", exists(PracticeTestTags.NECK))
        assertFalse("the chord chart has gone", exists(PracticeTestTags.CHART))
        assertName("D blues")
        assertPicked(PracticeTestTags.root(PitchClass.D))
        pick(PracticeTestTags.root(PitchClass.E))

        shownScales.value = false
        compose.waitUntil(WAIT_MS) { exists(PracticeTestTags.CHART) }
        assertChordName("E major")
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

    /**
     * The banjo (Phase 59, the owner's picks in docs/design/banjo-charts-preview.html): Scales draws its neck, no quiet line
     * in its place, and TalkBack calls the short fifth string the "short G 4 string" (pick A), its open note first and its
     * frets counted on the neck, none below its start. G major on open G, C major on double C, D major on double D
     * ("short F sharp 4 string"). Upright and on its side, at normal and 2x text; another instrument and back changes only
     * the strings.
     */
    @Test
    fun theBanjoIsOnTheNeckAndTalkBackNamesItsShortString() {
        val banjo = Instruments.BANJO_5.defaultTuning
        shownTuning.value = banjo
        show(PitchClass.G, ScaleType.MAJOR)
        for (scale in listOf(1f, 2f)) for (size in listOf(XIAOMI, PHONE_LANDSCAPE)) {
            val case = "$size at ${scale}x"
            shownFontScale.value = scale
            shownSize.value = size
            compose.waitForIdle()
            val spoken = spokenNeck()
            assertTrue(
                "$case: $spoken",
                spoken.startsWith(
                    "G major, notes G, A, B, C, D, E, F sharp, short G 4 string, open G, root, fret 7 A, fret 9 B, fret 10 C, fret 12 D, " +
                        "D 3 string, open D, fret 2 E, ",
                ),
            )
            assertEquals("$case: five strings, one of them short", 5, Regex(" string, ").findAll(spoken).count())
            assertFalse("$case: no line in the neck's place", exists(PracticeTestTags.SHORT_STRING))
            for (tag in listOf(PracticeTestTags.neckLie(lie()), PracticeTestTags.NAME_PICKER, PracticeTestTags.DRONE_PLAY)) {
                assertWhollyShown(node(tag), "$case: $tag", scroll = true)
            }
        }
        shownFontScale.value = 1f
        shownSize.value = XIAOMI
        shownTuning.value = Instruments.BANJO_5.tunings.single { it.id == "banjo_double_c" }
        pick(PracticeTestTags.root(PitchClass.C))
        compose.waitUntil(WAIT_MS) { spokenNeck().startsWith("C major") }
        assertTrue(spokenNeck(), spokenNeck().contains("C major, notes C, D, E, F, G, A, B, short G 4 string, open G, fret 7 A, fret 9 B, fret 10 C, root, fret 12 D, C 3 string, open C, root"))
        shownTuning.value = Instruments.BANJO_5.tunings.single { it.id == "banjo_double_d" }
        pick(PracticeTestTags.root(PitchClass.D))
        compose.waitUntil(WAIT_MS) { spokenNeck().startsWith("D major") }
        assertTrue(spokenNeck(), spokenNeck().contains("short F sharp 4 string, open F sharp, fret 6 G, fret 8 A, fret 10 B, fret 12 C sharp, D 3 string, open D, root"))
        // Another instrument: its strings as they always were, no short one; the banjo again: straight back.
        shownTuning.value = Tunings.STANDARD
        compose.waitUntil(WAIT_MS) { spokenNeck().contains("E 2 string") }
        assertFalse(spokenNeck(), spokenNeck().contains("short"))
        shownTuning.value = banjo
        compose.waitUntil(WAIT_MS) { spokenNeck().contains("short G 4 string, open G, fret 7 A, fret 9 B, fret 11 C sharp, fret 12 D, root") }
    }

    /**
     * Neck B drawn (the owner's pick): G major on the banjo, its short string (string 0) solid from its start at the 5th
     * fret, faded and dashed from the nut to there with no notes on it, and its open g's dot just before its start, in the
     * 5th fret's space; the long string beside it solid the whole way. Upright and on its side, both hands: left-handed a
     * mirror image, in the same place and size, TalkBack hearing the same words.
     */
    @Test
    fun theShortStringIsFadedBelowItsStartInBothHands() {
        val banjo = Instruments.BANJO_5.defaultTuning
        shownTuning.value = banjo
        show(PitchClass.G, ScaleType.MAJOR)
        val lies = mutableSetOf<NeckLie>()
        for (size in listOf(XIAOMI, PHONE_LANDSCAPE)) {
            shownLeftHanded.value = false
            shownSize.value = size
            compose.waitUntil(WAIT_MS) { spokenNeck().contains("short G 4 string") }
            compose.waitForIdle()
            val lie = lie()
            lies += lie
            val drawing = node(PracticeTestTags.neckLie(lie))
            val spoken = spokenNeck()
            val bounds = drawing.getBoundsInRoot()
            val normal = drawing.captureToImage()
            val case = "$size $lie"
            assertShortStringFaded(normal, NeckGeometry.of(banjo), lie, "$case right-handed")
            shownLeftHanded.value = true
            compose.waitForIdle()
            assertEquals("$case: TalkBack hears the same left-handed", spoken, spokenNeck())
            assertEquals("$case: in the same place and size", bounds, drawing.getBoundsInRoot())
            val mirrored = drawing.captureToImage()
            assertShortStringFaded(mirrored, NeckGeometry.of(banjo, mirrored = true), lie, "$case left-handed")
            assertMirrored(normal, mirrored, case)
        }
        assertEquals("both lies checked", NeckLie.entries.toSet(), lies)
        shownLeftHanded.value = false
        shownSize.value = XIAOMI
    }

    /**
     * In the neck [image] (the drawing's own size, [geometry] lying [lie]) of G major on the banjo: string 0, the short one,
     * is drawn solid in the 6th and 8th fret's spaces (no notes there), fainter and broken by the dashes' gaps in the 1st to
     * the 4th (no notes there either), and the open g's dot covers it in the 5th; the long string beside it is solid in the
     * 1st and 3rd fret's spaces (none of its notes there), a check of this check.
     */
    private fun assertShortStringFaded(image: ImageBitmap, geometry: NeckGeometry, lie: NeckLie, case: String) {
        val pixels = IntArray(image.width * image.height).also { image.readPixels(it) }
        val unit = image.width / geometry.width(lie)
        fun colour(along: Float, across: Float): Int {
            val p = geometry.point(lie, along, across) * unit
            val x = p.x.roundToInt().coerceIn(0, image.width - 1)
            val y = p.y.roundToInt().coerceIn(0, image.height - 1)
            return pixels[y * image.width + x]
        }
        fun difference(a: Int, b: Int) =
            abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) + abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))
        // How strongly something is drawn on string [string] at [along]: the largest difference across the string's width
        // from the field, by default a little way towards the thinner strings, where nothing is drawn.
        fun ink(string: Int, along: Float, field: Int = colour(along, geometry.stringLine(string) + FIELD_AWAY)): Int {
            val across = geometry.stringLine(string)
            val step = 1f / unit
            return (-ACROSS_REACH..ACROSS_REACH).maxOf { difference(colour(along, across + it * step), field) }
        }
        // Along fret [fret]'s space, clear of the fret lines and the nut.
        fun inkAlong(string: Int, fret: Int): List<Int> {
            val from = geometry.fretLine(fret - 1) + SPACE_MARGIN
            val to = geometry.fretLine(fret) - SPACE_MARGIN
            return (0..SAMPLES).map { ink(string, from + (to - from) * it / SAMPLES) }
        }
        val solid = inkAlong(0, 6) + inkAlong(0, 8)
        val level = solid.sorted()[solid.size / 2]
        if (level < DRAWN_INK) fail("$case: the short string from its start not drawn ($level)")
        if (solid.min() < level * 0.6f) fail("$case: the short string from its start not solid: $solid")
        val long = inkAlong(1, 1) + inkAlong(1, 3)
        if (long.min() < level * 0.6f) fail("$case: the long string beside it not solid: $long")
        val faded = (1..4).flatMap { inkAlong(0, it) }
        if (faded.max() > level * 0.75f) fail("$case: the short string below its start not faded, or a note there: $faded (solid $level)")
        if (faded.count { it > level * 0.2f } < 3) fail("$case: the short string below its start not drawn: $faded (solid $level)")
        if (faded.count { it < level * 0.15f } < 3) fail("$case: the short string below its start not dashed: $faded (solid $level)")
        // The dot off its middle (clear of its name), against the field beside the 4th fret's space, clear of every dot.
        val field = colour(geometry.fretMiddle(4), geometry.stringLine(0) + FIELD_AWAY)
        val open = ink(0, geometry.noteMiddle(0, 0) - NeckGeometry.DOT_RADIUS * 0.6f, field)
        if (open < level * 0.6f) fail("$case: no dot for the open g in the 5th fret's space ($open, solid $level)")
    }

    /**
     * The banjo's neck in every language at 2x text, upright on a 320 dp phone and on its side: the neck, the name and the
     * drone button wholly shown, every text too. Only with `--languages`.
     */
    @EveryLanguage
    @Test
    fun theBanjosNeckFitsInEveryLanguageAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        shownTuning.value = Instruments.BANJO_5.defaultTuning
        show(PitchClass.G, ScaleType.MAJOR)
        for (language in checkedLanguages) {
            shownLocale.value = language
            for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                compose.waitForIdle()
                val case = "$language $size at 2x, banjo:"
                for (tag in listOf(PracticeTestTags.neckLie(lie()), PracticeTestTags.NAME_PICKER, PracticeTestTags.DRONE_PLAY, PracticeTestTags.DRONE_SOUND)) {
                    assertWhollyShown(node(tag), "$case $tag", scroll = true)
                }
                compose.assertEveryTextWhollyShown(case)
                compose.assertNoWordBroken(case)
            }
        }
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
    fun itFitsWithoutScrollingUprightAndOnItsSideInLightAndDark() = assertItFits(listOf(XIAOMI, PHONE_UPRIGHT, PHONE_LANDSCAPE), listOf(false, true))

    /** On a tablet, upright and on its side (Phase 52): it fits as on a phone, and the neck takes the whole width under the rows, and on its side the name shares the top row. */
    @Test
    fun onATabletTheNeckUsesTheWholeScreen() = assertItFits(listOf(TABLET_UPRIGHT, TABLET_LANDSCAPE), listOf(false))

    private fun assertItFits(sizes: List<DpSize>, themes: List<Boolean>) {
        show(PitchClass.G_SHARP, ScaleType.BLUES)
        for (dark in themes) for (size in sizes) {
            shownDark.value = dark
            shownSize.value = size
            compose.waitForIdle()
            val case = "${if (dark) "dark" else "light"} $size:"
            val window = bars().safeArea(size)
            val tags = listOf(
                PracticeTestTags.BACK, PracticeTestTags.TUNING, PracticeTestTags.NAME_PICKER,
                PracticeTestTags.DRONE_PLAY, PracticeTestTags.DRONE_SOUND,
            )
            for (tag in tags) {
                assertWhollyShown(node(tag), "$case $tag", scroll = false)
                val bounds = node(tag).getBoundsInRoot()
                assertTrue("$case $tag inside the window $bounds", bounds.top >= window.top && bounds.bottom <= window.bottom)
            }
            // Upright on the Xiaomi (the owner's choice), sideways on its side; a short phone takes whichever is bigger (sideways).
            val lie = lie()
            if (size == XIAOMI) assertEquals("$case how the neck lies", NeckLie.UPRIGHT, lie)
            if (size == PHONE_LANDSCAPE || size == TABLET_LANDSCAPE) assertEquals("$case how the neck lies", NeckLie.SIDEWAYS, lie)
            if (size == TABLET_UPRIGHT) assertEquals("$case how the neck lies", NeckLie.UPRIGHT, lie)
            val drawn = node(PracticeTestTags.neckLie(lie)).getBoundsInRoot()
            val area = neck().getBoundsInRoot()
            assertTrue("$case the neck inside its room $drawn $area", drawn.left >= area.left && drawn.right <= area.right && drawn.top >= area.top && drawn.bottom <= area.bottom)
            assertTrue("$case inside the window", drawn.bottom <= window.bottom)
            assertTrue("$case a big neck $drawn", maxOf(drawn.width, drawn.height) >= 300.dp)
            val tuning = node(PracticeTestTags.TUNING).getBoundsInRoot()
            val name = picker().getBoundsInRoot()
            if (size != PHONE_LANDSCAPE) assertTrue("$case the name over the neck", name.bottom <= area.top + 1.dp)
            assertTheDroneButtonBesideTheName(case)
            val middle = (window.left + window.right) / 2
            if (size == PHONE_LANDSCAPE) {
                // Both halves used (Phase 63): the top row, then the name with the drone button, in the start half; the neck alone in the end half.
                assertTrue("$case the tuning line in the start half", tuning.right <= middle)
                assertTrue("$case the name in the start half, under the top row", name.right <= middle && name.top >= tuning.bottom)
                assertTrue("$case the neck in the end half", area.left >= middle)
            } else if (size == TABLET_LANDSCAPE) {
                // A tablet on its side (Phase 52): the tuning line and the name share the top row, the neck runs the whole width under it.
                val tool = node(PracticeTestTags.BACK).getBoundsInRoot()
                assertTrue("$case the tuning line in the start half", tuning.right <= middle)
                assertTrue("$case the name in the end half, beside the back arrow", name.left >= middle && name.top < tool.bottom)
                assertTrue("$case the neck across the whole width $drawn", drawn.left < middle && drawn.right > middle && drawn.width >= (window.right - window.left) * 0.85f)
            } else {
                assertTrue("$case the tuning line over the name", tuning.bottom <= name.top + 1.dp)
            }
            if (size == TABLET_UPRIGHT) {
                assertTrue("$case the neck as tall as its room $drawn $area", drawn.height >= area.height - 1.dp)
            }
            // The tablets' forced windows are scaled to fit the screen and their panels (windows of their own) are not. On a
            // tablet on its side the panels drop under the name as upright (Phase 52): the drone panel in one column.
            val tablet = size == TABLET_LANDSCAPE || size == TABLET_UPRIGHT
            assertThePanel(case, wide = size == PHONE_LANDSCAPE || tablet)
            assertTheDronePanel(case, wide = size == PHONE_LANDSCAPE, under = !tablet && size != PHONE_LANDSCAPE)
        }
    }

    /** The drone button at the end of the name's row: as tall as the name, after it, both halves of one size and meeting. */
    private fun assertTheDroneButtonBesideTheName(case: String) {
        val name = picker().getBoundsInRoot()
        val play = node(PracticeTestTags.DRONE_PLAY).getBoundsInRoot()
        val sound = node(PracticeTestTags.DRONE_SOUND).getBoundsInRoot()
        assertEquals("$case the drone button as tall as the name", name.height.value, play.height.value, 0.5f)
        assertEquals("$case both halves as tall", play.height.value, sound.height.value, 0.5f)
        // Within a pixel of the shown window: split into whole pixels the halves can differ by one (at the tablet's 1.5 px
        // a dp 88 and 89 px). A tablet's window forced onto a phone gets fewer px a dp (0.8 on the Xiaomi), so a pixel is more dp.
        val pixel = 1f / node(PracticeTestTags.DRONE_PLAY).fetchSemanticsNode().layoutInfo.density.density
        assertEquals("$case both halves as wide", play.width.value, sound.width.value, pixel * 1.05f)
        assertEquals("$case level with the name", name.top.value, play.top.value, 0.5f)
        assertTrue("$case after the name $name, $play, $sound", name.right <= play.left && play.right <= sound.left)
        // One pill split in two (Phase 75): the halves meet at the 1 dp line between them, no gap.
        assertEquals("$case the halves meet $play, $sound", 1f, (sound.left - play.right).value, pixel * 1.05f)
    }

    /**
     * The drone panel, opened from the drone button: under it where it fits (where not [wide]); Sound with the four sounds,
     * Notes with the three, then Lower, each wholly shown (with [texts], every text too); Back closes it.
     */
    private fun assertTheDronePanel(case: String, wide: Boolean, texts: Boolean = false, under: Boolean = !wide) {
        openDronePanel()
        val rows = DroneSound.entries.map { PracticeTestTags.droneSound(it) } + DroneVoicing.entries.map { PracticeTestTags.droneVoicing(it) } +
            PracticeTestTags.DRONE_LOWER
        for (row in rows) assertWhollyShown(node(row), "$case $row")
        fun top(tag: String) = node(tag).fetchSemanticsNode().positionInRoot.y
        fun left(tag: String) = node(tag).fetchSemanticsNode().positionInRoot.x
        val sounds = DroneSound.entries.map { top(PracticeTestTags.droneSound(it)) }
        assertTrue("$case the sounds in their order, one under the other $sounds", sounds.zipWithNext().all { (a, b) -> b > a })
        val firstNote = PracticeTestTags.droneVoicing(DroneVoicing.ROOT)
        if (wide) {
            assertTrue("$case on its side the notes beside the sounds", left(firstNote) > left(PracticeTestTags.droneSound(DroneSound.TANPURA)))
        } else {
            assertTrue("$case upright the notes under the sounds", top(firstNote) > sounds.last())
            if (under) assertMenuUnder(node(PracticeTestTags.DRONE_SOUND), node(PracticeTestTags.droneSound(DroneSound.TANPURA)), case)
        }
        assertTrue("$case Lower under the notes", top(PracticeTestTags.DRONE_LOWER) > top(PracticeTestTags.droneVoicing(DroneVoicing.FIFTH_OCTAVE)))
        if (texts) {
            compose.assertEveryTextWhollyShown("$case drone panel:")
            compose.assertNoWordBroken("$case drone panel:")
        }
        closeDronePanel()
    }

    private fun openDronePanel() {
        node(PracticeTestTags.DRONE_SOUND).performClick()
        compose.waitUntil(WAIT_MS) { exists(PracticeTestTags.droneSound(DroneSound.TANPURA)) }
    }

    private fun closeDronePanel() {
        Espresso.pressBack()
        compose.waitUntil(WAIT_MS) { !exists(PracticeTestTags.droneSound(DroneSound.TANPURA)) }
    }

    private fun spoken(tag: String): String =
        node(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString()

    private fun breathing(): Boolean =
        node(PracticeTestTags.neckLie(lie())).fetchSemanticsNode().config.getOrElse(RootsBreathing) { false }

    /**
     * ▶ sounds the scale's root (D3 for D, the tanpura with the fifth by default) and turns into ■, the roots breathing;
     * a new root follows at once (G3), a new scale on the same root changes nothing, a new A4 is followed too; ■ stops it,
     * and while it is still nothing starts by itself. The button keeps its size, and shows ▶ again when the drone stopped
     * by itself (a call). TalkBack hears "Play the drone" / "Stop the drone" and "Drone sound: Tanpura".
     */
    @Test
    fun theDronePlaysTheRootAndFollowsIt() {
        show(PitchClass.D, ScaleType.DORIAN)
        compose.waitForIdle()
        val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        val list = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)
        node(PracticeTestTags.DRONE_PLAY).assert(button).assert(hasContentDescription("Play the drone"))
        node(PracticeTestTags.DRONE_SOUND).assert(list).assert(hasContentDescription("Drone sound: Tanpura"))
        assertFalse("still at first", drone.isPlaying.value)
        assertFalse("the roots still", breathing())
        val still = node(PracticeTestTags.DRONE_PLAY).getBoundsInRoot()

        node(PracticeTestTags.DRONE_PLAY).performClick()
        compose.waitForIdle()
        assertEquals(listOf(DronePlay(146.83f, DroneSound.TANPURA, DroneVoicing.FIFTH, lower = false)), drone.plays.map { it.rounded() })
        node(PracticeTestTags.DRONE_PLAY).assert(hasContentDescription("Stop the drone"))
        assertTrue("the roots breathe", breathing())
        assertEquals("one steady size", still, node(PracticeTestTags.DRONE_PLAY).getBoundsInRoot())

        pick(PracticeTestTags.root(PitchClass.G))
        compose.waitForIdle()
        assertEquals("a new root follows", 196f, drone.lastPlay!!.rootHz, 0.01f)
        val plays = drone.plays.size
        pick(PracticeTestTags.scale(ScaleType.MAJOR))
        compose.waitForIdle()
        assertEquals("a new scale on the same root changes nothing", plays, drone.plays.size)
        shownA4.value = 432f
        compose.waitForIdle()
        assertEquals("a new A4 is followed", 192.43f, drone.lastPlay!!.rootHz, 0.01f)

        node(PracticeTestTags.DRONE_PLAY).performClick()
        compose.waitForIdle()
        assertFalse("■ stops it", drone.isPlaying.value)
        node(PracticeTestTags.DRONE_PLAY).assert(hasContentDescription("Play the drone"))
        assertFalse("the roots still again", breathing())
        val stopped = drone.plays.size
        pick(PracticeTestTags.root(PitchClass.A))
        compose.waitForIdle()
        assertEquals("nothing starts by itself", stopped, drone.plays.size)

        node(PracticeTestTags.DRONE_PLAY).performClick()
        compose.waitForIdle()
        assertEquals("A2 at 432", 108f, drone.lastPlay!!.rootHz, 0.01f)
        drone.interrupt()
        compose.waitForIdle()
        node(PracticeTestTags.DRONE_PLAY).assert(hasContentDescription("Play the drone"))
        assertFalse(breathing())
    }

    /** Scales going (another tool takes its place) stops the drone; back on Scales it stays still until a tap. */
    @Test
    fun theDroneStopsWhenScalesGoes() {
        show(PitchClass.E, ScaleType.MINOR_PENTATONIC)
        compose.waitForIdle()
        node(PracticeTestTags.DRONE_PLAY).performClick()
        compose.waitForIdle()
        assertTrue(drone.isPlaying.value)
        val stops = drone.stopCount
        shownScales.value = false
        compose.waitUntil(WAIT_MS) { exists(PracticeTestTags.CHART) }
        assertFalse("Chords has no drone button", exists(PracticeTestTags.DRONE_PLAY))
        assertFalse("leaving Scales stops the drone", drone.isPlaying.value)
        assertTrue("stopped, not just shown still", drone.stopCount > stops)
        val plays = drone.plays.size
        shownScales.value = true
        compose.waitUntil(WAIT_MS) { exists(PracticeTestTags.DRONE_PLAY) }
        node(PracticeTestTags.DRONE_PLAY).assert(hasContentDescription("Play the drone"))
        assertEquals("back on Scales it stays still", plays, drone.plays.size)
        assertFalse(drone.isPlaying.value)
    }

    /**
     * The panel opens on the saved choice, its sounds and notes radio groups and Lower a switch; each pick is heard at once
     * while the drone sounds and passed on to be saved, the panel staying open for another; the button shows the sound.
     */
    @Test
    fun theDronePanelPicksAreHeardAndSaved() {
        show(PitchClass.C, ScaleType.MAJOR, DroneChoice(DroneSound.WARM_PAD, DroneVoicing.ROOT, lower = true))
        compose.waitForIdle()
        node(PracticeTestTags.DRONE_SOUND).assert(hasContentDescription("Drone sound: Warm pad"))
        node(PracticeTestTags.DRONE_PLAY).performClick()
        compose.waitForIdle()
        assertEquals(DronePlay(130.81f, DroneSound.WARM_PAD, DroneVoicing.ROOT, lower = true), drone.lastPlay!!.rounded())

        openDronePanel()
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        node(PracticeTestTags.droneSound(DroneSound.WARM_PAD)).assertIsSelected().assert(radio).assertTextEquals("Warm pad")
        node(PracticeTestTags.droneSound(DroneSound.TANPURA)).assertIsNotSelected()
        node(PracticeTestTags.droneVoicing(DroneVoicing.ROOT)).assertIsSelected().assert(radio).assertTextEquals("Root")
        node(PracticeTestTags.droneVoicing(DroneVoicing.FIFTH)).assertTextEquals("+ fifth")
        node(PracticeTestTags.droneVoicing(DroneVoicing.FIFTH_OCTAVE)).assertTextEquals("+ fifth and octave")
        node(PracticeTestTags.DRONE_LOWER).assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        for (sound in DroneSound.entries) {
            node(PracticeTestTags.droneSound(sound)).assert(hasParent(hasContentDescription("Sound") and SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)))
        }
        for (voicing in DroneVoicing.entries) {
            node(PracticeTestTags.droneVoicing(voicing)).assert(hasParent(hasContentDescription("Notes") and SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)))
        }

        node(PracticeTestTags.droneSound(DroneSound.HARMONIUM)).performClick()
        compose.waitForIdle()
        assertTrue("the panel stays open", exists(PracticeTestTags.droneSound(DroneSound.TANPURA)))
        node(PracticeTestTags.droneSound(DroneSound.HARMONIUM)).assertIsSelected()
        assertEquals("heard at once", DronePlay(130.81f, DroneSound.HARMONIUM, DroneVoicing.ROOT, lower = true), drone.lastPlay!!.rounded())
        node(PracticeTestTags.droneVoicing(DroneVoicing.FIFTH_OCTAVE)).performClick()
        node(PracticeTestTags.DRONE_LOWER).performClick()
        compose.waitForIdle()
        node(PracticeTestTags.DRONE_LOWER).assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        assertEquals(DronePlay(130.81f, DroneSound.HARMONIUM, DroneVoicing.FIFTH_OCTAVE, lower = false), drone.lastPlay!!.rounded())
        // The one picked already changes nothing.
        node(PracticeTestTags.droneSound(DroneSound.HARMONIUM)).performClick()
        closeDronePanel()
        assertEquals(
            "the picks are passed on to be saved",
            listOf(
                DroneChoice(DroneSound.HARMONIUM, DroneVoicing.ROOT, lower = true),
                DroneChoice(DroneSound.HARMONIUM, DroneVoicing.FIFTH_OCTAVE, lower = true),
                DroneChoice(DroneSound.HARMONIUM, DroneVoicing.FIFTH_OCTAVE, lower = false),
            ),
            savedDrones,
        )
        node(PracticeTestTags.DRONE_SOUND).assert(hasContentDescription("Drone sound: Harmonium"))
        assertTrue("still playing", drone.isPlaying.value)
    }

    /** [DronePlay] with its Hz to two decimals, as the tests write them. */
    private fun DronePlay.rounded() = copy(rootHz = Math.round(rootHz * 100f) / 100f)

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

    /**
     * Left-handed (Phase 57): the neck is drawn as a mirror image, upright (the thickest string and the fret numbers on
     * the right) and sideways (the nut on the right), in the same place and size; TalkBack hears the same words.
     */
    @Test
    fun leftHandedMirrorsTheNeckUprightAndSideways() {
        show(PitchClass.G, ScaleType.MAJOR)
        val lies = mutableSetOf<NeckLie>()
        for (size in listOf(XIAOMI, PHONE_LANDSCAPE)) {
            shownLeftHanded.value = false
            shownSize.value = size
            compose.waitUntil(WAIT_MS) { spokenNeck().startsWith("G major") }
            compose.waitForIdle()
            val lie = lie()
            lies += lie
            val drawing = node(PracticeTestTags.neckLie(lie))
            val spoken = spokenNeck()
            val bounds = drawing.getBoundsInRoot()
            val normal = drawing.captureToImage()
            shownLeftHanded.value = true
            compose.waitForIdle()
            val case = "$size $lie"
            assertEquals("$case: lies the same way", lie, lie())
            assertEquals("$case: TalkBack hears the same", spoken, spokenNeck())
            assertEquals("$case: in the same place and size", bounds, drawing.getBoundsInRoot())
            assertMirrored(normal, drawing.captureToImage(), case)
        }
        assertEquals("both lies checked", NeckLie.entries.toSet(), lies)
        shownLeftHanded.value = false
        shownSize.value = XIAOMI
    }

    @Test
    fun theNeckLiesUprightWhereThatIsClearlyBigger() {
        shownSize.value = TABLET_UPRIGHT
        show()
        compose.waitForIdle()
        assertTrue("upright on a tall tablet", exists(PracticeTestTags.neckLie(NeckLie.UPRIGHT)))
        assertWhollyShown(node(PracticeTestTags.neckLie(NeckLie.UPRIGHT)), "the upright neck", scroll = false)
        shownSize.value = PHONE_LANDSCAPE
        compose.waitForIdle()
        assertTrue("sideways on a phone on its side", exists(PracticeTestTags.neckLie(NeckLie.SIDEWAYS)))
    }

    /**
     * Every scale's name in the panel keeps room at its sides, the longest too ("Phrygian dominant" ran edge to edge at the
     * Lenovo tablet's 1.3× text, Phase 52), set a little smaller where it must: upright on the Xiaomi and on a tablet on its side.
     */
    @Test
    fun everyScaleNameKeepsRoomAtItsSides() {
        shownFontScale.value = 1.3f
        show()
        for (size in listOf(XIAOMI, TABLET_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
            openPanel()
            for (type in ScaleType.entries) {
                val button = node(PracticeTestTags.scale(type)).fetchSemanticsNode()
                val text = compose.onNode(
                    hasAnyAncestor(hasTestTag(PracticeTestTags.scale(type))) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                val results = mutableListOf<TextLayoutResult>()
                text.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                val layout = results.single()
                val widest = (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }
                val room = (button.size.width - widest) / 2 / button.layoutInfo.density.density
                // From the touch area's edge: 3 dp to the face, 3 dp inside it, and at least 5 dp more.
                assertTrue("$size at 1.3x: $type keeps room at its sides, $room dp", room >= 11f)
            }
            closePanel()
        }
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
                assertWhollyShown(node(tag), "nl $size $tag at 2x")
            }
            compose.assertEveryTextWhollyShown("nl $size at 2x:")
            compose.assertNoWordBroken("nl $size at 2x:")
            assertThePanel("nl $size at 2x:", wide = size == PHONE_LANDSCAPE, texts = true)
            assertWhollyShown(node(PracticeTestTags.DRONE_PLAY), "nl $size the drone button at 2x")
            assertWhollyShown(node(PracticeTestTags.DRONE_SOUND), "nl $size the drone's sound at 2x")
            assertTheDronePanel("nl $size at 2x:", wide = size == PHONE_LANDSCAPE, texts = true)
        }
    }

    /**
     * The name is one button (Phase 47) that with the drone button after it (Phase 48) is as wide as the row, with the scale's
     * full name in it, large on one line ("D Dorian" on the Xiaomi), on two lines set smaller only where one doesn't fit
     * ("G♯ Phrygian dominant" beside the drone button, French "Sol♯ pentatonique mineure", Dutch "Sol♯ mineur-pentatonisch"
     * at 2× text on a 320 dp phone and on its side), and one steady height whatever the name, so the neck never moves.
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
            val name = "${case.language} ${case.size} at ${case.fontScale}x:"
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
                assertTrue("$what on one or two lines (three at 2x text)", layout.lineCount in 1..(if (case.fontScale > 1f) 3 else 2))
                // Beside the drone button a long name is set smaller on the Xiaomi too, on one line or two, never larger.
                assertTrue("$what no larger than D Dorian", layout.letterWidth() <= dorian.letterWidth() + 0.5f)
                if (layout.lineCount == 2) {
                    assertTrue("$what set smaller on two lines: ${layout.letterWidth()} px an o, D Dorian ${dorian.letterWidth()}", layout.letterWidth() < dorian.letterWidth() - 0.5f)
                }
                if (case.size.width < case.size.height) {
                    val tool = node(PracticeTestTags.BACK).getBoundsInRoot()
                    assertEquals("$what at the row's start", tool.left.value, long.left.value, ARROW_INSET)
                    val end = node(PracticeTestTags.DRONE_SOUND).getBoundsInRoot().right
                    assertTrue("$what with the drone button as wide as the row ${long.left} to $end", end - long.left >= case.size.width - 32.dp - 1.dp)
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
                assertThePanel("$language $size at 2x:", wide = size == PHONE_LANDSCAPE, texts = true)
                assertTheDronePanel("$language $size at 2x:", wide = size == PHONE_LANDSCAPE, texts = true)
                val height = picker().getBoundsInRoot().height
                // The one shown first, so none is picked twice.
                for (type in LONG_NAMES.sortedBy { it != shown }) {
                    if (type != shown) pick(PracticeTestTags.scale(type)).also { shown = type }
                    val what = "$language $size G♯ $type at 2x:"
                    assertEquals("$what one steady height", height.value, picker().getBoundsInRoot().height.value, 0.5f)
                    assertTrue("$what on one to three lines", nameLayout().lineCount in 1..3)
                    assertWhollyShown(picker(), "$what the name")
                    compose.assertEveryTextWhollyShown(what)
                    compose.assertNoWordBroken(what)
                }
            }
        }
    }

    companion object {
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

        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 30L
        private const val WAIT_MS = 5_000L

        /**
         * The short string's pixel check ([assertShortStringFaded]), in the neck's units: the field this far from a string,
         * towards the thinner ones (clear of the next string's dots); the pixels either side of the string looked at; the
         * part of a fret's space left out at each end (the fret lines, the nut); how many steps along a space; and the
         * least difference from the field that is a string drawn in full (of the three colours' 0..255 together).
         */
        private const val FIELD_AWAY = 6f
        private const val ACROSS_REACH = 3
        private const val SPACE_MARGIN = 3f
        private const val SAMPLES = 28
        private const val DRAWN_INK = 60

        /** The Xiaomi 12X less the status and navigation bars, a short narrow phone (360 x 720 dp with navigation buttons) less the bars, on its side less the rail, a 320 dp phone and a tall tablet. */
        private val XIAOMI = DpSize(393.dp, 760.dp)
        private val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
        private val NARROW_UPRIGHT = DpSize(320.dp, 640.dp)
        private val TABLET_UPRIGHT = DpSize(700.dp, 1100.dp)

        /** The Lenovo Tab M11 (1280 x 800 dp) on its side (Phase 52). */
        private val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)
    }
}
