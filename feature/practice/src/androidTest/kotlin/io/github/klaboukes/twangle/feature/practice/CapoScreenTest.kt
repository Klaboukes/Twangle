package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
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
    private val shownFontScale = mutableStateOf(1f)
    private val shownDark = mutableStateOf(false)
    private val shownLocale = mutableStateOf("en")
    private val shownNotation = mutableStateOf(Notation.SHARPS)
    private val savedShapes = mutableListOf<SongKey>()
    private val savedFrets = mutableListOf<Int>()
    private val opened = mutableListOf<Chord>()
    private val tools = listOf(PracticeTool.METRONOME, PracticeTool.CHORDS, PracticeTool.CAPO)

    private fun show(shapes: SongKey = SongKey(PitchClass.C), fret: Int = 0) {
        compose.setContent {
            val override = DeviceConfigurationOverride.ForcedSize(shownSize.value) then
                DeviceConfigurationOverride.FontScale(shownFontScale.value) then
                DeviceConfigurationOverride.Locales(LocaleList(shownLocale.value))
            DeviceConfigurationOverride(override) {
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
                        header = { PracticeToolSwitch(PracticeTool.CAPO, {}, tools) },
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                    )
                }
            }
        }
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag)
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
        node(PracticeTestTags.CAPO_LOWER).assert(hasContentDescription("Capo one fret down"))
        node(PracticeTestTags.CAPO_HIGHER).assert(hasContentDescription("Capo one fret up"))
        node(PracticeTestTags.TUNING).assert(hasContentDescription("Guitar, Standard"))
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
    fun itFitsWithoutScrollingUprightAndOnItsSideInLightAndDark() {
        show(SongKey(PitchClass.C), 3)
        val last = PracticeTestTags.capoRow(Chord.of(PitchClass.A, ChordQuality.MINOR))
        for (dark in listOf(false, true)) for (size in listOf(XIAOMI, PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownDark.value = dark
            shownSize.value = size
            compose.waitForIdle()
            val case = "$style ${if (dark) "dark" else "light"} $size:"
            val window = node(WINDOW).getBoundsInRoot()
            for (tag in listOf(PracticeTestTags.SWITCH, PracticeTestTags.TUNING, PracticeTestTags.CAPO_SHAPES, PracticeTestTags.CAPO_LOWER, PracticeTestTags.CAPO_FRET, PracticeTestTags.CAPO_HIGHER, last, PracticeTestTags.CAPO_SOUNDS_IN)) {
                assertWhollyShown(node(tag), "$case $tag", scroll = false)
                val bounds = node(tag).getBoundsInRoot()
                assertTrue("$case $tag inside the window $bounds", bounds.top >= window.top && bounds.bottom <= window.bottom)
            }
            if (size == PHONE_LANDSCAPE) {
                val middle = (window.left + window.right) / 2
                assertTrue("$case the capo in the start half", node(PracticeTestTags.CAPO_HIGHER).getBoundsInRoot().right <= middle)
                assertTrue("$case the table in the end half", node(last).getBoundsInRoot().left >= middle)
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
        show(SongKey(PitchClass.G), 11)
        for (language in shippedLanguages) {
            shownLocale.value = language
            for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
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

        /** The Xiaomi 12X less the status and navigation bars, a narrow phone less the bar, on its side less the rail, and a 320 dp phone. */
        private val XIAOMI = DpSize(393.dp, 760.dp)
        private val PHONE_UPRIGHT = DpSize(360.dp, 650.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
        private val NARROW_UPRIGHT = DpSize(320.dp, 640.dp)
    }
}
