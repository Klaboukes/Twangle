package io.github.klaboukes.twangle.app.navigation

import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.app.R
import io.github.klaboukes.twangle.core.designsystem.NAV_MARK_HEIGHT
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.tokensFor
import io.github.klaboukes.twangle.core.testing.Navigation
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.checkedLanguages
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * The navigation bar (upright) and rail (on its side) in Ink, light and dark: the tabs, Tune · Metronome ·
 * Practice (Phase 69), that TalkBack hears as tabs by name
 * with the open one selected, and nothing else; without Practice (its switch off) the metronome keeps its own tab; a tap on another tab opens it, and one on the open tab only says so
 * (the app's Tuner tab closes the tuner's tools then); the open tab
 * wears its mark in the accent; the bar stays at most [MAX_BAR_HEIGHT] tall up to the owner's
 * 1.25× text (the feature modules' layout tests count on that); in Dutch at 2× text every name is
 * wholly shown. The gear that opens Settings (Phase 69) is in the top start corner: on its side at the
 * rail's top, upright handed to the content for its own top row. The frame hides the bar (and the gear)
 * for Settings and the tuning editor, and the content never reaches under it.
 */
@RunWith(AndroidJUnit4::class)
class NavigationBarTest {

    private val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Set these to change what the window shows without composing it again.
    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownNavigation = mutableStateOf(Navigation.BUTTONS)

    /** The phone's bars around the window: the status bar and the navigation bar. */
    private fun bars() = SystemBars.phone(onItsSide = shownSize.value.width > shownSize.value.height, shownNavigation.value)
    private val shownFontScale = mutableStateOf(1f)
    private val shownDark = mutableStateOf(false)
    private val shownLocale = mutableStateOf("en")
    private val shownSelected = mutableStateOf<Destination?>(Destination.TUNER)
    private val shownPractice = mutableStateOf(true)
    private val picked = mutableListOf<Destination>()
    private val reselected = mutableListOf<Destination>()
    private var contentStarts = 0
    private var settingsOpened = 0

    /** Whether the content was last handed the gear to place itself. */
    private var contentHasGear = false

    private fun show() {
        compose.setContent {
            PhoneScreen(shownSize.value, bars(), shownFontScale.value, shownLocale.value) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
                    AppNavigationFrame(
                        selected = shownSelected.value,
                        onSelect = { picked += it; shownSelected.value = it },
                        onReselect = { reselected += it },
                        destinations = destinationsFor(shownPractice.value),
                        modifier = Modifier.testTag(WINDOW),
                        onOpenSettings = { settingsOpened++ },
                    ) { gear ->
                        // Counts how often the content starts afresh.
                        remember { contentStarts++ }
                        contentHasGear = gear != null
                        // The gear in the content's top start corner, as the app's tabs place it in their top row.
                        Box(Modifier.fillMaxSize().testTag(CONTENT)) { gear?.invoke() }
                    }
                }
            }
        }
    }

    private fun tab(destination: Destination) = compose.onNodeWithTag(NavigationTestTags.tab(destination))

    private fun label(destination: Destination, dutch: Boolean = false): String {
        val id = destination.label
        return if (dutch) dutch(id) else english(id)
    }

    private fun dutch(id: Int): String = inLanguage(id, "nl")

    /** The bar's own language in most tests ([shownLocale]), whatever language the phone gives the app. */
    private fun english(id: Int): String = inLanguage(id, "en")

    private fun inLanguage(id: Int, tag: String): String {
        val configuration = android.content.res.Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return context.createConfigurationContext(configuration).getString(id)
    }

    /** TalkBack, light and dark, bar and rail: each tab is a tab with its name, the open one selected. */
    @Test
    fun talkBackHearsTheTabsByNameWithTheOpenOneSelected() {
        show()
        for ((size, dark) in listOf(PHONE_UPRIGHT to false, PHONE_LANDSCAPE to true)) {
            shownSize.value = size
            shownDark.value = dark
            for (open in Destination.entries) {
                shownSelected.value = open
                compose.waitForIdle()
                for (destination in Destination.entries) {
                    val node = tab(destination)
                        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                        .assert(hasText(label(destination)))
                    if (destination == open) node.assertIsSelected() else node.assertIsNotSelected()
                }
            }
        }
    }

    /**
     * Bar and rail hold the three tabs and, on its side, the gear, nothing else: the instrument and tuning
     * are picked with the headstock button above the tuner's strings, so no item stands among the tabs
     * without being one (the bar's Instrument item until 2026-10-19), and Settings is no tab (Phase 69).
     */
    @Test
    fun theBarAndTheRailHoldOnlyTheTabsAndOnItsSideTheGear() {
        show()
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
            compose.onNodeWithTag("nav_instrument").assertDoesNotExist()
            compose.onNodeWithTag(NavigationTestTags.SETTINGS).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(Destination.entries.size)
            // The gear, one button: in the rail on its side, in the content upright.
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertCountEquals(1)
        }
    }

    /**
     * The gear (Phase 69) is in the top start corner and TalkBack hears it as the button "Settings": on
     * its side at the rail's top, above every tab, with the content handed none; upright handed to the
     * content, which places it in its own top row (the tabs' screens do), none in the bar. A tap opens
     * Settings. Without a tab (Settings itself, the tuning editor) there is no gear.
     */
    @Test
    fun theGearIsInTheTopStartCornerAndOpensSettings() {
        show()
        for (size in listOf(PHONE_LANDSCAPE, PHONE_UPRIGHT)) {
            shownSize.value = size
            compose.waitForIdle()
            val gear = compose.onNodeWithTag(NavigationTestTags.SETTINGS)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(english(R.string.nav_settings))))
            val bounds = gear.getBoundsInRoot()
            val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
            if (size == PHONE_LANDSCAPE) {
                assertFalse("$size: the rail holds the gear, not the content", contentHasGear)
                val rail = compose.onNodeWithTag(NavigationTestTags.RAIL).getBoundsInRoot()
                assertTrue("$size: in the rail $bounds $rail", bounds.left >= rail.left - SLACK.dp && bounds.right <= rail.right + SLACK.dp)
                for (destination in Destination.entries) {
                    assertTrue("$size: above $destination", bounds.bottom <= tab(destination).getBoundsInRoot().top + SLACK.dp)
                }
                assertTrue("$size: near the top $bounds", bounds.top - window.top <= bars().top + GEAR_TOP_MAX)
            } else {
                assertTrue("$size: upright the content holds the gear", contentHasGear)
                val bar = compose.onNodeWithTag(NavigationTestTags.BAR).getBoundsInRoot()
                assertTrue("$size: not in the bar", bounds.bottom <= bar.top + SLACK.dp)
            }
            gear.performClick()
        }
        assertEquals(2, settingsOpened)
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            shownSelected.value = null
            compose.waitForIdle()
            compose.onNodeWithTag(NavigationTestTags.SETTINGS).assertDoesNotExist()
            assertFalse("$size: no gear without a tab", contentHasGear)
        }
        shownSelected.value = Destination.TUNER
    }

    /**
     * The tabs read Tune · Metronome · Practice in that order, in the bar and the rail; without Practice (its
     * switch off) the Metronome tab stays, called Metronome, and Practice is gone.
     */
    @Test
    fun theTabsReadTuneMetronomePracticeAndWithoutPracticeTheMetronomeKeepsItsTab() {
        show()
        for (practice in listOf(true, false)) {
            shownPractice.value = practice
            for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                compose.waitForIdle()
                val case = "${if (practice) "with" else "without"} Practice, $size:"
                assertEquals("$case Tune", "Tune", english(R.string.nav_tuner))
                tab(Destination.METRONOME).assert(hasText(english(R.string.nav_metronome)))
                if (practice) {
                    tab(Destination.PRACTICE).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assert(hasText(english(R.string.nav_practice)))
                } else {
                    tab(Destination.PRACTICE).assertDoesNotExist()
                }
                val tabs = destinationsFor(practice).map { tab(it).getBoundsInRoot() }
                val inOrder = if (size == PHONE_LANDSCAPE) {
                    tabs.zipWithNext().all { (a, b) -> a.bottom <= b.top + SLACK.dp }
                } else {
                    tabs.zipWithNext().all { (a, b) -> a.right <= b.left + SLACK.dp }
                }
                assertTrue("$case in the bar's order $tabs", inOrder)
                compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(destinationsFor(practice).size)
            }
        }
        shownPractice.value = true
    }

    @Test
    fun aTapOnAnotherTabOpensItAndOnTheOpenOneReselectsIt() {
        show()
        tab(Destination.TUNER).performClick()
        tab(Destination.METRONOME).performClick()
        tab(Destination.PRACTICE).performClick()
        tab(Destination.PRACTICE).performClick()
        assertEquals(listOf(Destination.METRONOME, Destination.PRACTICE), picked)
        assertEquals(listOf(Destination.TUNER, Destination.PRACTICE), reselected)
        tab(Destination.PRACTICE).assertIsSelected()
    }

    /** Upright the bar is at the bottom and at most 80 dp tall, on its side the rail is at the start; the content is beside them, never under. */
    @Test
    fun theBarAndTheRailKeepToTheirEdgeAndLeaveTheContentClear() {
        show()
        for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE, PHONE_LANDSCAPE to 1f)) {
            shownSize.value = size
            shownFontScale.value = scale
            for ((dark, navigation) in listOf(false to Navigation.BUTTONS, true to Navigation.GESTURES)) {
                shownDark.value = dark
                shownNavigation.value = navigation
                compose.waitForIdle()
                val case = "${if (dark) "dark" else "light"} $size at ${scale}x $navigation:"
                val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
                val content = compose.onNodeWithTag(CONTENT).getBoundsInRoot()
                if (size == PHONE_LANDSCAPE) {
                    val rail = compose.onNodeWithTag(NavigationTestTags.RAIL).assertIsDisplayed().getBoundsInRoot()
                    assertEquals("$case rail at the start", window.left.value, rail.left.value, SLACK)
                    assertTrue("$case content right of the rail", content.left >= rail.right - SLACK.dp)
                } else {
                    // Its face reaches the bottom edge, under the phone's own navigation bar, and is above it at most 80 dp tall.
                    val bar = compose.onNodeWithTag(NavigationTestTags.BAR).assertIsDisplayed().getBoundsInRoot()
                    assertEquals("$case bar at the bottom", window.bottom.value, bar.bottom.value, SLACK)
                    val above = bar.height - bars().bottom
                    assertTrue("$case bar $above tall above the phone's bar", above <= MAX_BAR_HEIGHT)
                    assertTrue("$case content above the bar", content.bottom <= bar.top + SLACK.dp)
                }
                for (destination in Destination.entries) assertWhollyShown(tab(destination), "$case $destination", scroll = false)
            }
        }
        shownNavigation.value = Navigation.BUTTONS
    }

    /** The longest names, Dutch at 2× text, with and without Practice, are wholly shown in the bar and in the rail (set smaller where they must). */
    @Test
    fun dutchNamesAtTwiceTheTextSizeAreWhollyShown() {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        show()
        for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) for (practice in listOf(false, true)) {
            shownSize.value = size
            shownPractice.value = practice
            compose.waitForIdle()
            for (destination in destinationsFor(practice)) {
                val name = label(destination, dutch = true)
                tab(destination).assert(hasText(name))
                assertWhollyShown(compose.onNode(hasText(name), useUnmergedTree = true), "$size: $name", scroll = false)
            }
        }
    }

    /** Every language's names at 2× text, in the bar and in the rail. Only with `--languages`. */
    @EveryLanguage
    @Test
    fun everyLanguagesNamesAtTwiceTheTextSizeAreWhollyShown() {
        shownFontScale.value = 2f
        show()
        for (language in checkedLanguages) {
            shownLocale.value = language
            for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) for (practice in listOf(false, true)) {
                shownSize.value = size
                shownPractice.value = practice
                compose.waitForIdle()
                compose.assertEveryTextWhollyShown("$language ${size.width} x ${size.height} at 2x${if (practice) "" else " without Practice"}:")
            }
        }
    }

    /** The open tab has its mark under its name, in the accent; the others have none. */
    @Test
    fun theOpenTabWearsTheStylesMarkInTheAccent() {
        show()
        for (dark in listOf(false, true)) {
            shownDark.value = dark
            compose.waitForIdle()
            val accent = accentOf(dark)
            for (destination in Destination.entries) {
                val pixels = tab(destination).captureToImage().toPixelMap()
                // At the density the window is drawn at, which a forced size can lower.
                val markRows = (NAV_MARK_HEIGHT.value * tab(destination).fetchSemanticsNode().layoutInfo.density.density).toInt()
                var lit = 0
                for (y in pixels.height - markRows until pixels.height) {
                    for (x in 0 until pixels.width) if (near(pixels[x, y].toArgb(), accent)) lit++
                }
                val case = "${if (dark) "dark" else "light"} $destination"
                if (destination == Destination.TUNER && lit < MIN_MARK_PIXELS) fail("$case: only $lit accent pixels in the mark")
                if (destination != Destination.TUNER && lit > 0) fail("$case: $lit accent pixels under a tab that isn't open")
            }
        }
    }

    @Test
    fun turningBetweenBarAndRailKeepsTheContentAndItsSavedState() {
        // The content sits at the same place in the composition either way, so its remembered and
        // saved state (keyed by that place) carries over when the phone turns.
        show()
        compose.waitForIdle()
        assertEquals(1, contentStarts)
        for (size in listOf(PHONE_LANDSCAPE, PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
        }
        compose.onNodeWithTag(NavigationTestTags.RAIL).assertExists()
        assertEquals(1, contentStarts)
    }

    /** The tuning editor has no bar or rail: the content takes the whole window. */
    @Test
    fun withoutATabTheContentTakesTheWholeWindow() {
        show()
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            shownSelected.value = null
            compose.waitForIdle()
            compose.onAllNodesWithTag(NavigationTestTags.BAR).assertCountEquals(0)
            compose.onAllNodesWithTag(NavigationTestTags.RAIL).assertCountEquals(0)
            val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
            val content = compose.onNodeWithTag(CONTENT).getBoundsInRoot()
            assertEquals("$size: content width", window.width.value, content.width.value, SLACK)
            assertEquals("$size: content height", window.height.value, content.height.value, SLACK)
            shownSelected.value = Destination.TUNER
        }
    }

    /** The accent the theme gives Material's primary role. */
    private fun accentOf(dark: Boolean): Int = tokensFor(dark).scheme.primary.toArgb()

    private fun near(a: Int, b: Int): Boolean =
        abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) <= COLOR_TOLERANCE &&
            abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) <= COLOR_TOLERANCE &&
            abs((a and 0xFF) - (b and 0xFF)) <= COLOR_TOLERANCE

    companion object {
        private const val WINDOW = "window"
        private const val CONTENT = "content"
        private const val TEST_TIMEOUT_SECONDS = 20L

        /** A Galaxy S26 without the status and navigation bars, upright and on its side, and a narrow 320 dp phone. */
        private val PHONE_UPRIGHT = DpSize(360.dp, 730.dp)
        private val PHONE_LANDSCAPE = DpSize(780.dp, 330.dp)
        private val NARROW_UPRIGHT = DpSize(320.dp, 640.dp)

        /** The owner's Xiaomi 12X upright without the status bar and the button bar, at the owner's text size. */
        private val XIAOMI_UPRIGHT = DpSize(393.dp, 793.dp)
        private const val OWNER_FONT_SCALE = 1.25f

        /** The most the bar may take; `TunerStylesTest`, `StrumCheckStylesTest` and `MetronomeStylesTest` leave this much. */
        private val MAX_BAR_HEIGHT = 80.dp
        private const val SLACK = 0.5f

        /** On its side the gear's touch area starts at most this far under the status bar. */
        private val GEAR_TOP_MAX = 12.dp
        private const val COLOR_TOLERANCE = 6
        private const val MIN_MARK_PIXELS = 20
    }
}
