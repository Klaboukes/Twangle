package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.captureToImage
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
import androidx.compose.ui.unit.width
import androidx.test.espresso.Espresso
import io.github.klaboukes.twangle.core.designsystem.NAME_PICKER_HEIGHT
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.testing.Navigation
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertPanelUnder
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * The Chords tool (Phase 32) in Ink, light and dark: it opens on the saved chord, the name
 * button opens one panel of roots and chord types that picks the chord (and reports it to be saved),
 * closing on each pick so the chart shows it, the chart follows the picked tuning and notation, ‹ › and a swipe
 * step through the shapes, TalkBack hears the name as a heading and a list, the panel's grids as
 * radio groups and the chart in words, and the whole tool fits: without scrolling upright and on its
 * side at normal text, wholly shown at 2× text, the panel too; the banjo charted with its short string, in both hands (Phase 59).
 */
@RunWith(AndroidJUnit4::class)
class ChordsScreenTest {

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
    private val shownLeftHanded = mutableStateOf(false)
    private val savedRoots = mutableListOf<PitchClass>()
    private var instrumentTaps = 0
    private val savedQualities = mutableListOf<ChordQuality>()

    private fun show(root: PitchClass = PitchClass.C, quality: ChordQuality = ChordQuality.MAJOR) {
        compose.setContent {
            PhoneScreen(shownSize.value, bars(), shownFontScale.value, shownLocale.value) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
                    ChordsScreen(
                        tuning = shownTuning.value,
                        tuningName = "Guitar · Standard",
                        spokenTuningName = "Guitar, Standard",
                        notation = shownNotation.value,
                        savedRoot = root,
                        savedQuality = quality,
                        onRootChange = { savedRoots += it },
                        onQualityChange = { savedQualities += it },
                        onBack = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        onPickInstrument = { instrumentTaps++ },
                        leftHanded = shownLeftHanded.value,
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

    /** The name button says the chord's full name in [words] (Phase 63: one line, no symbol over it). */
    private fun assertName(words: String) {
        picker().assertTextEquals(words)
    }

    /** Opens the panel and waits for it: it comes with a short fade. */
    private fun openPanel() {
        picker().performClick()
        compose.waitUntil(WAIT_MS) { exists(PracticeTestTags.root(PitchClass.C)) }
    }

    /** Closes the panel with Back and waits for it to go: Back with it shut would close the activity. */
    private fun closePanel() {
        Espresso.pressBack()
        awaitPanelClosed()
    }

    /** Waits for the panel to go: it goes with a short fade. */
    private fun awaitPanelClosed() {
        compose.waitUntil(WAIT_MS) { !exists(PracticeTestTags.root(PitchClass.C)) }
    }

    /** For each of [options]: opens the panel, taps it there, and waits for the panel to close, as a pick closes it. */
    private fun pick(vararg options: String) {
        for (option in options) {
            openPanel()
            node(option).performClick()
            awaitPanelClosed()
        }
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
        assertName("A minor")
        assertFalse("the panel is closed", exists(PracticeTestTags.root(PitchClass.A)) || exists(PracticeTestTags.type(ChordQuality.MINOR)))
        assertPicked(PracticeTestTags.root(PitchClass.A), PracticeTestTags.type(ChordQuality.MINOR))

        pick(PracticeTestTags.root(PitchClass.G))
        awaitChart("G minor, shape 1 of")
        pick(PracticeTestTags.type(ChordQuality.DOMINANT_SEVENTH))
        awaitChart("G seventh, shape 1 of")
        assertName("G seventh")
        openPanel()
        node(PracticeTestTags.root(PitchClass.G)).assertIsSelected()
        node(PracticeTestTags.root(PitchClass.A)).assertIsNotSelected()
        node(PracticeTestTags.type(ChordQuality.DOMINANT_SEVENTH)).assertIsSelected()
        closePanel()
        assertEquals("the picks are passed on to be saved", listOf(PitchClass.G), savedRoots)
        assertEquals(listOf(ChordQuality.DOMINANT_SEVENTH), savedQualities)
    }

    /**
     * Any pick closes the panel, so the chart shows the new chord at once (the owner's report of 2026-10-26):
     * a chord type, a root, and the root already picked, which changes nothing.
     */
    @Test
    fun aPickClosesThePanelAndTheChartShowsIt() {
        show()
        awaitChart("C major")
        openPanel()
        node(PracticeTestTags.type(ChordQuality.MINOR)).performClick()
        awaitPanelClosed()
        assertName("C minor")
        awaitChart("C minor, shape 1 of")
        chart().assertIsDisplayed()
        openPanel()
        node(PracticeTestTags.root(PitchClass.D)).performClick()
        awaitPanelClosed()
        assertName("D minor")
        awaitChart("D minor, shape 1 of")
        openPanel()
        node(PracticeTestTags.root(PitchClass.D)).performClick()
        awaitPanelClosed()
        assertName("D minor")
        assertEquals("the root already picked isn't saved again", listOf(PitchClass.D), savedRoots)
        assertEquals(listOf(ChordQuality.MINOR), savedQualities)
        // One of Phase 49's four: the power chord, its chart with the strings it doesn't play.
        openPanel()
        node(PracticeTestTags.type(ChordQuality.POWER_CHORD)).performClick()
        awaitPanelClosed()
        assertName("D power chord")
        assertTrue("D5 on the D, A and D strings, the rest not played", awaitChart("D power chord, shape 1 of").contains("E 2 string, not played"))
        assertEquals(listOf(ChordQuality.MINOR, ChordQuality.POWER_CHORD), savedQualities)
    }

    @Test
    fun talkBackHearsTheNameAsAListAndThePanelAsRadioButtons() {
        show(PitchClass.A_SHARP, ChordQuality.MAJOR)
        awaitChart("B flat major")
        val list = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)
        picker().assert(list).assert(isHeading()).assert(hasContentDescription("B flat major")).assertTextEquals("B♭ major")
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        openPanel()
        node(PracticeTestTags.root(PitchClass.A_SHARP)).assert(radio).assert(hasContentDescription("B flat"))
        node(PracticeTestTags.root(PitchClass.F_SHARP)).assert(radio).assert(hasContentDescription("F sharp"))
        node(PracticeTestTags.type(ChordQuality.MINOR_SEVENTH)).assert(radio).assert(hasContentDescription("B flat minor seventh"))
        node(PracticeTestTags.type(ChordQuality.POWER_CHORD)).assert(radio).assert(hasContentDescription("B flat power chord")).assertTextEquals("B♭5")
        node(PracticeTestTags.type(ChordQuality.SEVENTH_SHARP_NINE)).assert(radio).assert(hasContentDescription("B flat seven sharp nine")).assertTextEquals("B♭7♯9")
        node(PracticeTestTags.type(ChordQuality.MINOR_NINTH)).assert(radio).assert(hasContentDescription("B flat minor ninth")).assertTextEquals("B♭m9")
        node(PracticeTestTags.type(ChordQuality.MAJOR_NINTH)).assert(radio).assert(hasContentDescription("B flat major ninth")).assertTextEquals("B♭maj9")
        closePanel()
        node(PracticeTestTags.PREVIOUS).assert(hasContentDescription("Previous shape"))
        node(PracticeTestTags.NEXT).assert(hasContentDescription("Next shape"))
        // The picker chip (Phase 70), heard as the tuner's: the instrument and tuning, a button that changes them.
        node(PracticeTestTags.TUNING)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasContentDescription("Guitar, Standard"))
            .assert(SemanticsMatcher("double-tap changes the instrument or tuning") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Change instrument or tuning" })
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
        assertName("Re major")
        openPanel()
        node(PracticeTestTags.type(ChordQuality.MAJOR)).assertTextEquals("Re")
        node(PracticeTestTags.type(ChordQuality.MINOR)).assertTextEquals("Rem")
        node(PracticeTestTags.type(ChordQuality.SUS2)).performClick()
        awaitPanelClosed()
        assertName("Re suspended second")
    }

    /**
     * The banjo (Phase 59, the owner's picks A in docs/design/banjo-charts-preview.html): Chords charts it with its short
     * fifth string, and TalkBack calls that one the "short G 4 string", its frets counted on the neck. The first three
     * chords a beginner learns, G, C and D7, then D, whose first shape is barred at the 7th fret over all five strings.
     * Upright and on its side, at normal and 2x text; another instrument and back changes only the strings.
     */
    @Test
    fun theBanjoIsChartedAndTalkBackNamesItsShortString() {
        shownTuning.value = Instruments.BANJO_5.defaultTuning
        show(PitchClass.G, ChordQuality.MAJOR)
        for (scale in listOf(1f, 2f)) for (size in listOf(XIAOMI, PHONE_LANDSCAPE)) {
            val case = "$size at ${scale}x"
            shownFontScale.value = scale
            shownSize.value = size
            compose.waitForIdle()
            pick(PracticeTestTags.root(PitchClass.G), PracticeTestTags.type(ChordQuality.MAJOR))
            assertEquals(
                "$case: G",
                "G major, shape 1 of 6, short G 4 string, open, D 3 string, open, G 3 string, open, B 3 string, open, D 4 string, open",
                awaitChart("G major, shape 1 of"),
            )
            assertFalse("$case: no line in the chart's place", exists(PracticeTestTags.SHORT_STRING))
            for (tag in listOf(PracticeTestTags.CHART, PracticeTestTags.NAME_PICKER, PracticeTestTags.NEXT)) assertWhollyShown(node(tag), "$case: $tag", scroll = true)
            pick(PracticeTestTags.root(PitchClass.C))
            assertEquals(
                "$case: C",
                "C major, shape 1 of 6, short G 4 string, open, D 3 string, fret 2, finger 2, E, G 3 string, open, " +
                    "B 3 string, fret 1, finger 1, C, D 4 string, fret 2, finger 3, E",
                awaitChart("C major, shape 1 of"),
            )
            pick(PracticeTestTags.root(PitchClass.D), PracticeTestTags.type(ChordQuality.DOMINANT_SEVENTH))
            assertEquals(
                "$case: D7, the open g no D7 note",
                "D seventh, shape 1 of 6, short G 4 string, not played, D 3 string, open, G 3 string, fret 2, finger 2, A, " +
                    "B 3 string, fret 1, finger 1, C, D 4 string, fret 4, finger 4, F sharp",
                awaitChart("D seventh, shape 1 of"),
            )
            pick(PracticeTestTags.type(ChordQuality.MAJOR))
            assertTrue(
                "$case: D barred over the short string at the 7th fret, as banjo tab counts it",
                awaitChart("D major, shape 1 of").contains("barre, fret 7, finger 1, strings G 4 to D 4, short G 4 string, fret 7, finger 1, A"),
            )
            node(PracticeTestTags.COUNT).assertTextEquals("1 of 6")
        }
        // Another instrument: its strings as they always were, no short one; the banjo again: the chart straight back.
        shownTuning.value = Tunings.STANDARD
        assertFalse(awaitChart("D major, shape 1 of 6, E 2 string").contains("short"))
        shownTuning.value = Instruments.BANJO_5.defaultTuning
        awaitChart("D major, shape 1 of 6, barre, fret 7")
        shownFontScale.value = 1f
        shownSize.value = XIAOMI
    }

    /**
     * Chart A drawn: the short string starts at its own small nut on the 5th fret's line, so above it (frets 1 to 4) there
     * is no string, below it there is; on a chart starting above the 5th fret (D, from the 7th) it runs the whole way. Both
     * hands: left-handed the chart is a mirror image, the short string on the right, and TalkBack hears the same words.
     * Upright and on its side.
     */
    @Test
    fun theShortStringStartsAtItsOwnNutInBothHands() {
        val banjo = Instruments.BANJO_5.defaultTuning
        shownTuning.value = banjo
        show(PitchClass.G, ChordQuality.MAJOR)
        for (size in listOf(XIAOMI, PHONE_LANDSCAPE)) {
            shownSize.value = size
            for ((root, quality, words) in listOf(
                Triple(PitchClass.G, ChordQuality.MAJOR, "G major"),
                Triple(PitchClass.C, ChordQuality.MAJOR, "C major"),
                Triple(PitchClass.D, ChordQuality.DOMINANT_SEVENTH, "D seventh"),
                Triple(PitchClass.D, ChordQuality.MAJOR, "D major"),
            )) {
                shownLeftHanded.value = false
                pick(PracticeTestTags.root(root), PracticeTestTags.type(quality))
                val spoken = awaitChart("$words, shape 1 of")
                compose.waitForIdle()
                val case = "$size $words"
                val fromNut = quality != ChordQuality.MAJOR || root != PitchClass.D
                val normal = chart().captureToImage()
                assertShortString(normal, ChartGeometry.of(banjo), fromNut, "$case right-handed")
                shownLeftHanded.value = true
                compose.waitForIdle()
                assertEquals("$case: TalkBack hears the same left-handed", spoken, spokenChart())
                val mirrored = chart().captureToImage()
                assertShortString(mirrored, ChartGeometry.of(banjo, mirrored = true), fromNut, "$case left-handed")
                // D, barred over all five strings, is nearly the same either way round; G, C and D7 show the mirror.
                if (fromNut) assertMirrored(normal, mirrored, case)
            }
        }
        shownLeftHanded.value = false
        shownSize.value = XIAOMI
    }

    /**
     * In the chart [image] (the chart's whole room, the chart centred in it as [ChordChart] draws it with [geometry]), the
     * short string (string 0) is drawn below its own nut at the 5th fret and not above it when the chart starts at the
     * nut ([fromNut]), else the whole way; the long string beside it runs the whole way either way.
     */
    private fun assertShortString(image: ImageBitmap, geometry: ChartGeometry, fromNut: Boolean, case: String) {
        val pixels = IntArray(image.width * image.height).also { image.readPixels(it) }
        val unit = minOf(image.width / geometry.width, image.height / geometry.height)
        val left = (image.width - geometry.width * unit) / 2f
        val top = (image.height - geometry.height * unit) / 2f

        // Whether a string is drawn at [x] units across, [y] units down: a pixel within a unit of it clearly differs from the
        // field [away] units further out, where nothing is drawn.
        fun drawn(x: Float, y: Float, away: Float): Boolean {
            val row = (top + y * unit).roundToInt()
            fun colour(atX: Float) = pixels[row * image.width + (left + atX * unit).roundToInt().coerceIn(0, image.width - 1)]
            val field = colour(x + away)
            val reach = unit.roundToInt().coerceAtLeast(1)
            val middle = (left + x * unit).roundToInt()
            return (middle - reach..middle + reach).any { px ->
                val c = pixels[row * image.width + px.coerceIn(0, image.width - 1)]
                abs((c shr 16 and 0xFF) - (field shr 16 and 0xFF)) + abs((c shr 8 and 0xFF) - (field shr 8 and 0xFF)) + abs((c and 0xFF) - (field and 0xFF)) > CLEAR_INK
            }
        }
        val short = geometry.stringX(0)
        val out = ChartGeometry.STRING_GAP * 0.4f * geometry.outward(0)
        // The first long string, beside it, in the 3rd fret row (nothing pressed there in these four): drawn, a check of this check.
        assertTrue("$case: the long string beside the short one", drawn(geometry.stringX(1), geometry.rowY(2), -out))
        val belowNut = geometry.gridBottom + geometry.run / 2f
        assertTrue("$case: the short string below its own nut", drawn(short, belowNut, out))
        for (row in 1..3) {
            assertEquals("$case: the short string in fret row ${row + 1}", !fromNut, drawn(short, geometry.rowY(row), out))
        }
    }

    /**
     * The banjo's chart in every language at 2x text, upright on a 320 dp phone and on its side: the chart, the name and the
     * pager wholly shown, every text too. Only with `--languages`.
     */
    @EveryLanguage
    @Test
    fun theBanjosChartFitsInEveryLanguageAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        shownTuning.value = Instruments.BANJO_5.defaultTuning
        show(PitchClass.D, ChordQuality.DOMINANT_SEVENTH)
        for (language in checkedLanguages) {
            shownLocale.value = language
            for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                compose.waitForIdle()
                compose.waitUntil(WAIT_MS) { spokenChart().isNotEmpty() }
                for (tag in listOf(PracticeTestTags.CHART, PracticeTestTags.NAME_PICKER, PracticeTestTags.COUNT, PracticeTestTags.NEXT)) {
                    assertWhollyShown(node(tag), "$language $size at 2x, banjo: $tag", scroll = true)
                }
                compose.assertEveryTextWhollyShown("$language $size at 2x, banjo:")
                compose.assertNoWordBroken("$language $size at 2x, banjo:")
            }
        }
    }

    /**
     * Left-handed (Phase 57): the chart is drawn as a mirror image, the low string on the right, upright and on its side,
     * an open chord and a barre higher up; TalkBack hears the same words, and the rest of the screen stays where it was.
     */
    @Test
    fun leftHandedMirrorsTheChartAndTalkBackHearsTheSame() {
        show(PitchClass.C, ChordQuality.MAJOR)
        for (size in listOf(XIAOMI, PHONE_LANDSCAPE)) {
            shownSize.value = size
            for (chord in listOf("C major", "C major, shape 2 of")) {
                shownLeftHanded.value = false
                if (chord.contains("shape 2")) node(PracticeTestTags.NEXT).performClick()
                val spoken = awaitChart(chord)
                compose.waitForIdle()
                val normal = chart().captureToImage()
                val name = picker().getBoundsInRoot()
                val chartBounds = chart().getBoundsInRoot()
                shownLeftHanded.value = true
                compose.waitForIdle()
                val case = "$size $chord"
                assertEquals("$case: TalkBack hears the same", spoken, spokenChart())
                assertEquals("$case: the name stays", name, picker().getBoundsInRoot())
                assertEquals("$case: the chart's room stays", chartBounds, chart().getBoundsInRoot())
                assertMirrored(normal, chart().captureToImage(), case)
                if (chord.contains("shape 2")) node(PracticeTestTags.PREVIOUS).performClick()
            }
        }
        shownLeftHanded.value = false
        shownSize.value = XIAOMI
    }

    @Test
    fun itFitsWithoutScrollingUprightAndOnItsSideInLightAndDark() = assertItFits(listOf(XIAOMI, PHONE_UPRIGHT, PHONE_LANDSCAPE), listOf(false, true))

    /** The banjo's chart (Phase 59), a fret row taller: it fits as the guitar's, upright and on its side. */
    @Test
    fun theBanjoFitsWithoutScrollingUprightAndOnItsSide() =
        assertItFits(listOf(XIAOMI, PHONE_UPRIGHT, PHONE_LANDSCAPE), listOf(false), Instruments.BANJO_5.defaultTuning)

    /** On a tablet, upright and on its side (Phase 52): it fits as on a phone, and the chart takes the whole width under the rows, and on its side the name shares the top row. */
    @Test
    fun onATabletTheChartUsesTheWholeScreen() = assertItFits(listOf(TABLET_UPRIGHT, TABLET_LANDSCAPE), listOf(false))

    private fun assertItFits(sizes: List<DpSize>, themes: List<Boolean>, tuning: Tuning = Tunings.STANDARD) {
        shownTuning.value = tuning
        show()
        for (dark in themes) for (size in sizes) {
            shownDark.value = dark
            shownSize.value = size
            compose.waitForIdle()
            awaitChart("C major")
            val case = "${if (dark) "dark" else "light"} $size:"
            val window = bars().safeArea(size)
            for (tag in listOf(PracticeTestTags.BACK, PracticeTestTags.TUNING, PracticeTestTags.NAME_PICKER, PracticeTestTags.NEXT)) {
                assertWhollyShown(node(tag), "$case $tag", scroll = false)
                val bounds = node(tag).getBoundsInRoot()
                assertTrue("$case $tag inside the window $bounds", bounds.top >= window.top && bounds.bottom <= window.bottom)
            }
            // One row, every tab's: the back arrow at the start, the picker chip in the middle (Phase 47, 69, 70; beside the
            // arrow until 2026-11-04), as on the tuner.
            val backArrow = node(PracticeTestTags.BACK).getBoundsInRoot()
            val instrument = node(PracticeTestTags.TUNING).getBoundsInRoot()
            assertTrue(
                "$case the tuning beside the back arrow $backArrow $instrument",
                instrument.left >= backArrow.right && instrument.top < backArrow.bottom && instrument.bottom > backArrow.top,
            )
            if (size.width < size.height) {
                assertEquals("$case the chip in the row's middle", ((window.left + window.right) / 2).value, ((instrument.left + instrument.right) / 2).value, 1f)
            }
            // On a tablet the panel drops under the name, on its side too (Phase 52); since Phase 77 it floats on the screen's
            // own layer, so the tablets' forced windows show where (Material's menu, a window of its own, wasn't scaled with them).
            assertThePanel(case, wide = size == PHONE_LANDSCAPE)
            val chart = chart().getBoundsInRoot()
            // The short phone leaves the chart near its smallest, 1 dp a unit (156 dp; the banjo's, a fret row taller, 194 dp).
            val least = if (tuning.hasShortString) 194.dp else if (size == PHONE_UPRIGHT) 156.dp else 170.dp
            assertTrue("$case a big chart ${chart.height}", chart.height >= least - 0.5.dp)
            val pager = node(PracticeTestTags.NEXT).getBoundsInRoot()
            val tuning = node(PracticeTestTags.TUNING).getBoundsInRoot()
            val name = picker().getBoundsInRoot()
            assertTrue("$case chart, then pager", chart.bottom <= pager.top + 1.dp)
            val middle = (window.left + window.right) / 2
            if (size == PHONE_LANDSCAPE) {
                // Both halves used (Phase 63): the top row, then the name, in the start half; the chart alone in the end half.
                assertTrue("$case the tuning line in the start half", tuning.right <= middle)
                assertTrue("$case the name in the start half, under the top row", name.right <= middle && name.top >= tuning.bottom)
                assertTrue("$case the chart in the end half", chart.left >= middle)
            } else if (size == TABLET_LANDSCAPE) {
                // A tablet on its side (Phase 52): the tuning line and the name share the top row, the chart runs the whole width under it.
                assertTrue("$case the tuning line in the start half", tuning.right <= middle)
                assertTrue("$case the name in the end half, beside the back arrow", name.left >= middle && name.top < backArrow.bottom)
                assertTrue("$case the chart's room the whole width $chart", chart.left <= window.left + 25.dp && chart.right >= window.right - 25.dp)
                assertTrue("$case a large chart's room ${chart.height}", chart.height >= 500.dp)
            } else {
                assertTrue("$case the tuning line over the name", tuning.bottom <= name.top + 1.dp)
            }
            if (size == TABLET_UPRIGHT) {
                // Upright on a tablet the rows keep the phone's column and the chart takes the whole width.
                assertTrue("$case the name in the column ${name.width}", name.width <= 448.dp + 1.dp)
                assertTrue("$case the chart's room the whole width $chart", chart.width >= size.width - 32.dp - 1.dp)
            }
            if (size != PHONE_LANDSCAPE) assertTrue("$case the name over the chart", name.bottom <= chart.top + 1.dp)
        }
        shownTuning.value = Tunings.STANDARD
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
                assertWhollyShown(node(tag), "nl $size $tag at 2x")
            }
            compose.assertEveryTextWhollyShown("nl $size at 2x:")
            assertThePanel("nl $size at 2x:", wide = size == PHONE_LANDSCAPE, texts = true)
        }
    }

    /**
     * The panel under the name where it fits (where not [wide]): the roots in two rows of six (C to F, F♯ to B), then the chord types
     * in five rows of four, each wholly shown (scrolling the panel where it must; with [texts], every text in
     * it too); Back closes it and leaves the name as it was.
     */
    private fun assertThePanel(case: String, wide: Boolean, texts: Boolean = false) {
        fun top(tag: String) = node(tag).fetchSemanticsNode().positionInRoot.y
        val name = picker().fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        openPanel()
        val first = top(PracticeTestTags.root(PitchClass.C))
        val second = top(PracticeTestTags.root(PitchClass.F_SHARP))
        assertTrue("$case roots in two rows of six", top(PracticeTestTags.root(PitchClass.F)) == first && second > first && top(PracticeTestTags.root(PitchClass.B)) == second)
        // Upright under the name; on its side over the start half, from its top.
        val panel = node(PracticeTestTags.NAME_PANEL).getBoundsInRoot()
        val window = node(WINDOW).getBoundsInRoot()
        if (!wide) {
            assertPanelUnder(picker(), node(PracticeTestTags.NAME_PANEL), node(WINDOW), case)
        } else {
            assertTrue("$case the panel $panel over the start half of $window", panel.left < (window.left + window.right) / 2 && panel.top < picker().getBoundsInRoot().top)
        }
        val types = top(PracticeTestTags.type(ChordQuality.MAJOR))
        assertTrue("$case the types under the roots", types > second)
        // C to C7, Cmaj7 to Csus4, C7sus4 to Cadd9, C9 to C7♯9, Cdim to Caug.
        val rows = ChordQuality.inPanel.chunked(4).map { row -> row.map { top(PracticeTestTags.type(it)) } }
        assertTrue("$case four chord types a row, in five rows", rows.size == 5 && rows.all { row -> row.size == 4 && row.all { it == row[0] } })
        assertTrue("$case the power chord in the top row", top(PracticeTestTags.type(ChordQuality.POWER_CHORD)) == rows[0][0])
        assertTrue("$case the rows one under the other", rows.zipWithNext().all { (above, below) -> below[0] > above[0] })
        for (pitch in PitchClass.entries) assertWhollyShown(node(PracticeTestTags.root(pitch)), "$case root $pitch")
        for (quality in ChordQuality.inPanel) assertWhollyShown(node(PracticeTestTags.type(quality)), "$case type $quality")
        if (texts) compose.assertEveryTextWhollyShown("$case panel:")
        closePanel()
        assertFalse("$case Back closes the panel", exists(PracticeTestTags.type(ChordQuality.MAJOR)))
        picker().assert(hasContentDescription(name))
    }

    /**
     * Every language at 2× text, upright on a 320 dp phone and on its side, the grids too, with a long name in words
     * (Russian "G♯ большой мажорный септаккорд"). Only with `--languages`.
     */
    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        show(PitchClass.G_SHARP, ChordQuality.MAJOR_SEVENTH)
        for (language in checkedLanguages) {
            shownLocale.value = language
            for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                compose.waitForIdle()
                compose.waitUntil(WAIT_MS) { spokenChart().isNotEmpty() }
                compose.assertEveryTextWhollyShown("$language $size at 2x:")
                compose.assertNoWordBroken("$language $size at 2x:")
                // Back with the panel shut would close the activity, and a tap while the last one is still going away only
                // shuts that one (the Retro run, 2026-10-19, when it was a window of its own). So wait for it both ways.
                openPanel()
                compose.assertEveryTextWhollyShown("$language $size at 2x, panel:")
                compose.assertNoWordBroken("$language $size at 2x, panel:")
                closePanel()
            }
        }
    }


    /**
     * The name is one button as wide as the row (Phase 47), the full name in words on it (Phase 63), and one steady
     * height whatever the name, so the chart never moves: C major and the longest name in words (Russian
     * "Sol♯ большой мажорный септаккорд" at 2× text on a 320 dp phone) alike, upright and on its side.
     */
    @Test
    fun theNameIsOneWideButtonOfOneSteadyHeight() {
        class Case(val fontScale: Float, val size: DpSize, val language: String)
        show()
        for (case in listOf(Case(1f, XIAOMI, "en"), Case(2f, NARROW_UPRIGHT, "ru"), Case(2f, PHONE_LANDSCAPE, "ru"))) {
            shownFontScale.value = case.fontScale
            shownSize.value = case.size
            shownLocale.value = case.language
            shownNotation.value = if (case.fontScale > 1f) Notation.SOLFEGE else Notation.SHARPS
            val name = "${case.language} ${case.size} at ${case.fontScale}x:"
            pick(PracticeTestTags.root(PitchClass.C), PracticeTestTags.type(ChordQuality.MAJOR))
            val short = picker().getBoundsInRoot()
            val chartTop = chart().getBoundsInRoot().top
            pick(PracticeTestTags.root(PitchClass.G_SHARP), PracticeTestTags.type(ChordQuality.MAJOR_SEVENTH))
            val long = picker().getBoundsInRoot()
            assertEquals("$name one steady height", short.height.value, long.height.value, 0.5f)
            assertEquals("$name the one picker face's height", NAME_PICKER_HEIGHT.value, long.height.value, 0.5f)
            assertEquals("$name the chart stays put", chartTop.value, chart().getBoundsInRoot().top.value, 0.5f)
            if (case.size.width < case.size.height) {
                val tool = node(PracticeTestTags.BACK).getBoundsInRoot()
                assertEquals("$name at the row's start", tool.left.value, long.left.value, ARROW_INSET)
                assertTrue("$name as wide as the row ${long.width}", long.width >= case.size.width - 32.dp - 1.dp)
            }
            assertWhollyShown(picker(), "$name the name")
            compose.assertEveryTextWhollyShown(name)
            compose.assertNoWordBroken(name)
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
        private const val WAIT_MS = 5_000L

        /** Of a pixel's three 0..255 colours together: a string's line against the field around it, as [assertMirrored]'s. */
        private const val CLEAR_INK = 150

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

/** The back arrow's face stands this far (in dp, give or take one) inside the row's start: 2 dp in its 48 dp touch area. */
internal const val ARROW_INSET = 3f
