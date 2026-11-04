package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_OUTLINE_INSET
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.ZERO_WIDTH_SPACE
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.Navigation
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
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

/**
 * The Practice cards (2.0, Phase 69) in Ink, light and dark: a card per tool offered, in its order, two
 * to a row upright and four in a row on its side, each one button that opens its tool, the gear before
 * the title upright, and the "last time" row only when a tool was used, naming it and what was picked in
 * it. The page fits without scrolling upright and on its side at the usual text size, on a tablet too,
 * and is wholly shown at 2× text.
 */
@RunWith(AndroidJUnit4::class)
class PracticeCardsTest {

    private val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val shownSize = mutableStateOf(XIAOMI)
    private val shownFontScale = mutableStateOf(1f)
    private val shownDark = mutableStateOf(false)
    private val shownLocale = mutableStateOf("en")
    private val shownTools = mutableStateOf(PracticeTool.entries.toList())
    private val shownLastTime = mutableStateOf<PracticeTool?>(PracticeTool.SCALES)
    private val shownGear = mutableStateOf(true)
    private val opened = mutableListOf<PracticeTool>()

    /** The phone's bars around the window: the status bar, and on its side the navigation bar too. */
    private fun bars() = SystemBars.tab(onItsSide = shownSize.value.width > shownSize.value.height, Navigation.BUTTONS)

    private fun show() {
        compose.setContent {
            PhoneScreen(shownSize.value, bars(), shownFontScale.value, shownLocale.value) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
                    PracticeCards(
                        title = "Practice",
                        tools = shownTools.value,
                        lastTime = shownLastTime.value,
                        notation = Notation.SHARPS,
                        savedRoot = PitchClass.A,
                        savedQuality = ChordQuality.MINOR,
                        savedScaleType = ScaleType.MINOR_PENTATONIC,
                        onOpen = { opened += it },
                        modifier = Modifier.fillMaxSize(),
                        settingsButton = if (shownGear.value) {
                            { Box(Modifier.size(48.dp).testTag(GEAR)) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag)
    private fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun card(tool: PracticeTool) = node(PracticeTestTags.card(tool))
    private fun spoken(tag: String): String =
        node(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString()

    @Test
    fun eachCardOpensItsToolAndTheLastTimeRowTheOneUsedLast() {
        show()
        for (tool in PracticeTool.entries) card(tool).performClick()
        node(PracticeTestTags.LAST_TIME).performClick()
        assertEquals(PracticeTool.entries + PracticeTool.SCALES, opened)
    }

    @Test
    fun withNothingUsedYetThereIsNoLastTimeRow() {
        shownLastTime.value = null
        show()
        compose.waitForIdle()
        assertFalse(exists(PracticeTestTags.LAST_TIME))
        assertTrue(exists(PracticeTestTags.card(PracticeTool.CHORDS)))
    }

    /** Each tool's switch hides its card (CLAUDE.md, *Every tool has a switch*); the others keep their order. */
    @Test
    fun aToolsSwitchHidesItsCard() {
        shownTools.value = listOf(PracticeTool.CHORDS, PracticeTool.CAPO, PracticeTool.EAR_TRAINING)
        show()
        compose.waitForIdle()
        assertFalse("no Scales card", exists(PracticeTestTags.card(PracticeTool.SCALES)))
        val chords = card(PracticeTool.CHORDS).getBoundsInRoot()
        val capo = card(PracticeTool.CAPO).getBoundsInRoot()
        val ears = card(PracticeTool.EAR_TRAINING).getBoundsInRoot()
        assertEquals("Capo beside Chords", chords.top.value, capo.top.value, 0.5f)
        assertTrue("Ear training under them", ears.top >= chords.bottom)
        // Alone in its row it is the wide, low card across it (Phase 78).
        assertEquals("the lone card across the row", (capo.right - chords.left).value, ears.width.value, 0.5f)
    }

    @Test
    fun talkBackHearsEachCardAsOneButtonAndTheRowWithWhatWasPicked() {
        show()
        compose.waitForIdle()
        card(PracticeTool.CHORDS).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val chords = card(PracticeTool.CHORDS).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString()
        // The line may end after its hyphen (an unseen, unspoken zero-width space).
        assertEquals("Chords, Every shape, left-handed too", chords.replace(ZERO_WIDTH_SPACE.toString(), ""))
        node(PracticeTestTags.LAST_TIME).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertEquals("Last time, Scales · A minor pentatonic", spoken(PracticeTestTags.LAST_TIME))
        shownLastTime.value = PracticeTool.CHORDS
        compose.waitForIdle()
        assertEquals("Last time, Chords · A minor", spoken(PracticeTestTags.LAST_TIME))
        shownLastTime.value = PracticeTool.CAPO
        compose.waitForIdle()
        assertEquals("Last time, Capo", spoken(PracticeTestTags.LAST_TIME))
    }

    @Test
    fun itFitsWithoutScrollingUprightAndOnItsSideInLightAndDark() = assertItFits(listOf(XIAOMI, PHONE_UPRIGHT, PHONE_LANDSCAPE), listOf(false, true))

    @Test
    fun onATabletItFitsUprightAndOnItsSide() = assertItFits(listOf(TABLET_UPRIGHT, TABLET_LANDSCAPE), listOf(false))

    private fun assertItFits(sizes: List<DpSize>, themes: List<Boolean>) {
        show()
        for (dark in themes) for (size in sizes) {
            shownDark.value = dark
            shownSize.value = size
            compose.waitForIdle()
            val case = "${if (dark) "dark" else "light"} $size:"
            val window = bars().safeArea(size)
            val tags = PracticeTool.entries.map(PracticeTestTags::card) + PracticeTestTags.LAST_TIME + GEAR
            for (tag in tags) {
                assertWhollyShown(node(tag), "$case $tag", scroll = false)
                val bounds = node(tag).getBoundsInRoot()
                assertTrue("$case $tag inside the window $bounds", bounds.top >= window.top && bounds.bottom <= window.bottom)
            }
            // Upright the grid's four, and the fifth alone in the last row (the pitch trail, Phase 78): wide and low across
            // it. On its side all five in one row.
            val side = size.width > size.height
            val inGrid = if (side) PracticeTool.entries.size else GRID
            val cards = PracticeTool.entries.take(inGrid).map { card(it).getBoundsInRoot() }
            val lone = PracticeTool.entries.drop(inGrid).map { card(it).getBoundsInRoot() }
            // At the top start of the cards' column, its face's outline (inside its 48 dp touch area) in line with the cards, as on every tab.
            val gear = node(GEAR).getBoundsInRoot()
            assertTrue("$case the gear at the top start $gear", gear.top <= window.top + 16.dp && abs((gear.left + TOP_BAR_OUTLINE_INSET - cards[0].left).value) < 1f)
            if (side) {
                assertTrue("$case on its side all in one row", cards.all { abs((it.top - cards[0].top).value) < 0.5f })
            } else {
                assertEquals("$case upright two to a row", cards[0].top.value, cards[1].top.value, 0.5f)
                assertTrue("$case upright two to a row", cards[2].top >= cards[0].bottom)
            }
            for (bounds in cards) assertEquals("$case one size", cards[0].width.value, bounds.width.value, 1f)
            for (bounds in lone) {
                assertTrue("$case the lone card under the grid", bounds.top >= cards.maxOf { it.bottom })
                // Within a few dp: the grid's cards are each rounded to the pixel.
                assertEquals("$case the lone card across the row", (cards.maxOf { it.right } - cards[0].left).value, bounds.width.value, 5f)
                assertTrue("$case the lone card low", bounds.height < cards[0].height)
            }
            val row = node(PracticeTestTags.LAST_TIME).getBoundsInRoot()
            assertTrue("$case the last time row under the cards", row.top >= (cards + lone).maxOf { it.bottom })
        }
    }

    @Test
    fun onItsSideTheGearIsAtTheRailsTopSoTheTitleStandsAtTheStart() {
        shownGear.value = false
        shownSize.value = PHONE_LANDSCAPE
        show()
        compose.waitForIdle()
        assertFalse(exists(GEAR))
        assertWhollyShown(card(PracticeTool.CHORDS), "without the gear", scroll = false)
    }

    @Test
    fun dutchAtTwiceTheTextSizeIsWhollyShown() {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        show()
        for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
            compose.assertEveryTextWhollyShown("nl $size at 2x:")
            compose.assertNoWordBroken("nl $size at 2x:")
        }
    }

    /** Every language at 2× text, upright on a 320 dp phone and on its side. Only with `--languages`. */
    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        show()
        for (language in checkedLanguages) {
            shownLocale.value = language
            for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                compose.waitForIdle()
                compose.assertEveryTextWhollyShown("$language $size at 2x:")
                compose.assertNoWordBroken("$language $size at 2x:")
            }
        }
    }

    companion object {
        /** The cards in the grid; the rest stand alone in the last row, wide. */
        const val GRID = 4

        private const val GEAR = "gear"
        private const val TEST_TIMEOUT_SECONDS = 30L

        /** As the other Practice tests: the Xiaomi 12X less its bars, a short narrow phone, on its side less the rail, a 320 dp phone. */
        private val XIAOMI = DpSize(393.dp, 760.dp)
        private val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
        private val NARROW_UPRIGHT = DpSize(320.dp, 640.dp)

        /** The Lenovo Tab M11 (1280 x 800 dp) upright less its bars, and on its side. */
        private val TABLET_UPRIGHT = DpSize(800.dp, 1120.dp)
        private val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)
    }
}
