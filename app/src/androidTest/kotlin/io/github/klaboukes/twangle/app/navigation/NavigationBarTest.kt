package io.github.klaboukes.twangle.app.navigation

import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
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
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.app.R
import io.github.klaboukes.twangle.core.designsystem.NAV_MARK_HEIGHT
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.tokensFor
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * The navigation bar (upright) and rail (on its side) in each style, light and dark: three tabs that
 * TalkBack hears as tabs by name with the open one selected, and the Tuning item after the Tuner tab,
 * a button that is never selected; a tap on another tab opens it; the open tab
 * wears the style's mark in the accent; the bar stays at most [MAX_BAR_HEIGHT] tall up to the owner's
 * 1.25× text (the feature modules' layout tests count on that); in Dutch at 2× text every name is
 * wholly shown. The frame hides the bar for the tuning editor and the compact tuner, and the content
 * never reaches under it.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class NavigationBarTest(private val style: VisualStyle) {

    private val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Set these to change what the window shows without composing it again.
    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownFontScale = mutableStateOf(1f)
    private val shownDark = mutableStateOf(false)
    private val shownLocale = mutableStateOf("en")
    private val shownSelected = mutableStateOf<Destination?>(Destination.TUNER)
    private val shownHidden = mutableStateOf(false)
    private val picked = mutableListOf<Destination>()
    private var tuningTaps = 0

    private fun show() {
        compose.setContent {
            val override = DeviceConfigurationOverride.ForcedSize(shownSize.value) then
                DeviceConfigurationOverride.FontScale(shownFontScale.value) then
                DeviceConfigurationOverride.Locales(androidx.compose.ui.text.intl.LocaleList(shownLocale.value))
            DeviceConfigurationOverride(override) {
                TwangleTheme(style = style, darkTheme = shownDark.value, animate = false) {
                    AppNavigationFrame(
                        selected = shownSelected.value,
                        onSelect = { picked += it; shownSelected.value = it },
                        onTuning = { tuningTaps++ },
                        tuningState = TUNING_STATE,
                        hidden = shownHidden.value,
                        modifier = Modifier.testTag(WINDOW),
                    ) {
                        Box(Modifier.fillMaxSize().testTag(CONTENT))
                    }
                }
            }
        }
    }

    private fun tab(destination: Destination) = compose.onNodeWithTag(NavigationTestTags.tab(destination))
    private val tuning get() = compose.onNodeWithTag(NavigationTestTags.TUNING)

    private fun label(destination: Destination, dutch: Boolean = false): String {
        val id = when (destination) {
            Destination.TUNER -> R.string.nav_tuner
            Destination.METRONOME -> R.string.nav_metronome
            Destination.SETTINGS -> R.string.nav_settings
        }
        return if (dutch) dutch(id) else context.getString(id)
    }

    private fun dutch(id: Int): String {
        val configuration = android.content.res.Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(Locale.forLanguageTag("nl")))
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
     * The Tuning item, bar and rail: after the Tuner tab, before Metronome; TalkBack hears a button
     * named "Tuning" holding the picked instrument and tuning, and what a double tap does; a tap
     * calls it, from every tab, and it is never selected.
     */
    @Test
    fun theTuningItemIsAButtonAfterTheTunerTab() {
        show()
        val change = context.getString(io.github.klaboukes.twangle.feature.tuner.R.string.tuner_change_instrument_or_tuning)
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            for (open in Destination.entries) {
                shownSelected.value = open
                compose.waitForIdle()
                tuning.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                    .assert(hasText(context.getString(R.string.nav_tuning)))
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, TUNING_STATE))
                    .assert(SemanticsMatcher("click label is \"$change\"") { it.config.getOrNull(SemanticsActions.OnClick)?.label == change })
                    .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
                val before = tuningTaps
                tuning.performClick()
                assertEquals("$size from $open", before + 1, tuningTaps)
                assertEquals("$size: a tap opens no tab", open, shownSelected.value)
            }
            val tuner = tab(Destination.TUNER).getBoundsInRoot()
            val item = tuning.getBoundsInRoot()
            val metronome = tab(Destination.METRONOME).getBoundsInRoot()
            if (size == PHONE_LANDSCAPE) {
                assertTrue("$size: under the Tuner tab", item.top >= tuner.bottom - SLACK.dp)
                assertTrue("$size: above Metronome", item.bottom <= metronome.top + SLACK.dp)
            } else {
                assertTrue("$size: right of the Tuner tab", item.left >= tuner.right - SLACK.dp)
                assertTrue("$size: left of Metronome", item.right <= metronome.left + SLACK.dp)
            }
        }
        shownSelected.value = Destination.TUNER
    }

    @Test
    fun aTapOnAnotherTabOpensItAndOnTheOpenOneDoesNothing() {
        show()
        tab(Destination.METRONOME).performClick()
        tab(Destination.SETTINGS).performClick()
        tab(Destination.SETTINGS).performClick()
        assertEquals(listOf(Destination.METRONOME, Destination.SETTINGS), picked)
        tab(Destination.SETTINGS).assertIsSelected()
    }

    /** Upright the bar is at the bottom and at most 80 dp tall, on its side the rail is at the start; the content is beside them, never under. */
    @Test
    fun theBarAndTheRailKeepToTheirEdgeAndLeaveTheContentClear() {
        show()
        for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE, PHONE_LANDSCAPE to 1f)) {
            shownSize.value = size
            shownFontScale.value = scale
            for (dark in listOf(false, true)) {
                shownDark.value = dark
                compose.waitForIdle()
                val case = "$style ${if (dark) "dark" else "light"} $size at ${scale}x:"
                val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
                val content = compose.onNodeWithTag(CONTENT).getBoundsInRoot()
                if (size == PHONE_LANDSCAPE) {
                    val rail = compose.onNodeWithTag(NavigationTestTags.RAIL).assertIsDisplayed().getBoundsInRoot()
                    assertEquals("$case rail at the start", window.left.value, rail.left.value, SLACK)
                    assertTrue("$case content right of the rail", content.left >= rail.right - SLACK.dp)
                } else {
                    val bar = compose.onNodeWithTag(NavigationTestTags.BAR).assertIsDisplayed().getBoundsInRoot()
                    assertEquals("$case bar at the bottom", window.bottom.value, bar.bottom.value, SLACK)
                    assertTrue("$case bar ${bar.height} tall", bar.height <= MAX_BAR_HEIGHT)
                    assertTrue("$case content above the bar", content.bottom <= bar.top + SLACK.dp)
                }
                for (destination in Destination.entries) assertWhollyShown(tab(destination), "$case $destination", scroll = false)
                assertWhollyShown(tuning, "$case Tuning", scroll = false)
            }
        }
    }

    /** The longest names, Dutch at 2× text, are wholly shown in the bar and in the rail (set smaller where they must). */
    @Test
    fun dutchNamesAtTwiceTheTextSizeAreWhollyShown() {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        show()
        for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
            for (destination in Destination.entries) {
                val name = label(destination, dutch = true)
                tab(destination).assert(hasText(name))
                assertWhollyShown(compose.onNode(hasText(name), useUnmergedTree = true), "$style $size: $name", scroll = false)
            }
            val name = dutch(R.string.nav_tuning)
            tuning.assert(hasText(name))
            assertWhollyShown(compose.onNode(hasText(name), useUnmergedTree = true), "$style $size: $name", scroll = false)
        }
    }

    /** The open tab has the style's mark under its name, in the accent; the others have none. */
    @Test
    fun theOpenTabWearsTheStylesMarkInTheAccent() {
        show()
        for (dark in listOf(false, true)) {
            shownDark.value = dark
            compose.waitForIdle()
            val accent = accentOf(dark)
            for (destination in Destination.entries) {
                val pixels = tab(destination).captureToImage().toPixelMap()
                val markRows = (NAV_MARK_HEIGHT.value * context.resources.displayMetrics.density).toInt()
                var lit = 0
                for (y in pixels.height - markRows until pixels.height) {
                    for (x in 0 until pixels.width) if (near(pixels[x, y].toArgb(), accent)) lit++
                }
                val case = "$style ${if (dark) "dark" else "light"} $destination"
                if (destination == Destination.TUNER && lit < MIN_MARK_PIXELS) fail("$case: only $lit accent pixels in the mark")
                if (destination != Destination.TUNER && lit > 0) fail("$case: $lit accent pixels under a tab that isn't open")
            }
            // The Tuning item is never open: no accent at all.
            val pixels = tuning.captureToImage().toPixelMap()
            var lit = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) if (near(pixels[x, y].toArgb(), accent)) lit++
            if (lit > 0) fail("$style ${if (dark) "dark" else "light"} Tuning: $lit accent pixels")
        }
    }

    /** The tuning editor has no bar, and the compact tuner hides the rail: the content takes the whole window. */
    @Test
    fun withoutATabOrWhileHiddenTheContentTakesTheWholeWindow() {
        show()
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            for ((selected, hidden) in listOf(null to false, Destination.TUNER to true)) {
                shownSelected.value = selected
                shownHidden.value = hidden
                compose.waitForIdle()
                compose.onAllNodesWithTag(NavigationTestTags.BAR).assertCountEquals(0)
                compose.onAllNodesWithTag(NavigationTestTags.RAIL).assertCountEquals(0)
                val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
                val content = compose.onNodeWithTag(CONTENT).getBoundsInRoot()
                assertEquals("$size: content width", window.width.value, content.width.value, SLACK)
                assertEquals("$size: content height", window.height.value, content.height.value, SLACK)
            }
            shownSelected.value = Destination.TUNER
            shownHidden.value = false
        }
    }

    /** The accent the theme gives Material's primary role. */
    private fun accentOf(dark: Boolean): Int = tokensFor(style, dark).scheme.primary.toArgb()

    private fun near(a: Int, b: Int): Boolean =
        abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) <= COLOR_TOLERANCE &&
            abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) <= COLOR_TOLERANCE &&
            abs((a and 0xFF) - (b and 0xFF)) <= COLOR_TOLERANCE

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        private const val WINDOW = "window"
        private const val CONTENT = "content"
        private const val TEST_TIMEOUT_SECONDS = 20L
        private const val TUNING_STATE = "Guitar, Drop D"

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
        private const val COLOR_TOLERANCE = 6
        private const val MIN_MARK_PIXELS = 20
    }
}
