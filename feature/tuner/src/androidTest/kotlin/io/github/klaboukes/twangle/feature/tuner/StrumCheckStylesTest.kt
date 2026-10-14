package io.github.klaboukes.twangle.feature.tuner

import android.os.LocaleList
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import org.junit.Assert.assertEquals
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
 * The strum check in each of the six styles: its panel in the meter's place, the line under it, and
 * its switch beside the instrument-and-tuning button. Upright (a narrow phone, and the owner's
 * Xiaomi 12X at 1.25× text) and on its side (full and compact layout), every part fits without
 * scrolling, in the window, clear of the settings button and of each other, and nothing moves from
 * one phase to the next. At 2× text in Dutch every part can be scrolled to and is wholly shown.
 * TalkBack hears each string with its reading, the line as a live region and the switch with its
 * state. Each string's marker is drawn in the style's own colours, light and dark.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class StrumCheckStylesTest(private val style: VisualStyle) {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Set these to change what the window shows without composing it again.
    private val shownState = mutableStateOf<TunerUiState>(ringing(StrumPhase.RINGING))
    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownFontScale = mutableStateOf(1f)
    private val shownDark = mutableStateOf(false)
    private val shownLocale = mutableStateOf("en")
    private val shownCompact = mutableStateOf(false)
    private val switched = mutableListOf<Boolean>()
    private val stringClicks = mutableListOf<Int>()

    // The style's colours, read while composing.
    private var accent = Color.Unspecified
    private var inTune = Color.Unspecified

    private fun show() {
        compose.setContent {
            val override = DeviceConfigurationOverride.ForcedSize(shownSize.value) then
                DeviceConfigurationOverride.FontScale(shownFontScale.value) then
                DeviceConfigurationOverride.Locales(androidx.compose.ui.text.intl.LocaleList(shownLocale.value))
            DeviceConfigurationOverride(override) {
                TwangleTheme(style = style, darkTheme = shownDark.value, animate = false) {
                    accent = MaterialTheme.colorScheme.primary
                    inTune = MaterialTheme.styleTokens.inTune
                    TunerContent(
                        shownState.value,
                        TunerMode.AUTO,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = { stringClicks += it },
                        onRetry = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        compactLandscape = shownCompact.value,
                        strumCheck = true,
                        onStrumCheckChange = { switched += it },
                    )
                }
            }
        }
    }

    private val panel get() = compose.onNodeWithTag(TunerTestTags.STRUM_PANEL)
    private val status get() = compose.onNodeWithTag(TunerTestTags.STRUM_STATUS)
    private val toggle get() = compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE)
    private val picker get() = compose.onNodeWithTag(TunerTestTags.PICKER)
    private val strings = listOf("E2", "A2", "D3", "G3", "B3", "E4")

    private fun parts(controls: Boolean): List<Pair<String, SemanticsNodeInteraction>> = buildList {
        add("panel" to panel)
        add("status" to status)
        if (controls) {
            add("strum check switch" to toggle)
            add("instrument and tuning button" to picker)
            strings.forEach { add("string $it" to compose.onNodeWithText(it)) }
        }
    }

    /** Upright at normal text and at the owner's 1.25× on the Xiaomi, light and dark, in every phase: all in view, nothing moves. */
    @Test
    fun uprightEverythingFitsAndNothingMovesBetweenPhases() {
        show()
        for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE)) {
            shownSize.value = size
            shownFontScale.value = scale
            for (dark in listOf(false, true)) {
                shownDark.value = dark
                // Heights only: a shorter line is centred, which moves nothing.
                var where: List<Pair<Float, Float>>? = null
                for (phase in StrumPhase.entries) {
                    shownState.value = ringing(phase)
                    compose.waitForIdle()
                    val case = "$style ${if (dark) "dark" else "light"} $phase ${size.width} x ${size.height} at ${scale}x:"
                    val bounds = assertAllInView(case, parts(controls = true)).map { it.top.value to it.bottom.value }
                    if (where == null) where = bounds
                    if (bounds != where) fail("$case moved: $bounds, was $where")
                }
            }
        }
    }

    /** On its side, in the full layout and the compact one (where the controls are hidden): all in view. */
    @Test
    fun onItsSideEverythingFits() {
        shownSize.value = PHONE_LANDSCAPE
        show()
        for (compact in listOf(false, true)) {
            shownCompact.value = compact
            compose.waitForIdle()
            val case = "$style on its side ${if (compact) "compact" else "full"}:"
            assertAllInView(case, parts(controls = !compact))
            if (compact) toggle.assertDoesNotExist()
            val panelBounds = panel.getBoundsInRoot()
            val statusBounds = status.getBoundsInRoot()
            // The panel beside or above the line, never under it.
            if (statusBounds.top < panelBounds.top - SLACK) fail("$case the line $statusBounds starts above the panel $panelBounds")
        }
    }

    /** At 2× text in Dutch, upright and in the full layout on its side: every part, scrolled to, is wholly shown. */
    @Test
    fun atTwiceTheTextSizeInDutchEveryPartIsWhollyShown() {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        show()
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            for (phase in listOf(StrumPhase.WAITING, StrumPhase.DONE)) {
                shownState.value = ringing(phase)
                compose.waitForIdle()
                for ((name, node) in parts(controls = true)) assertWhollyShown(node, "$style nl $phase $size at 2x: $name")
                val texts = listOf(
                    nl(R.string.tuner_strum_waiting).takeIf { phase == StrumPhase.WAITING },
                    nl(R.string.tuner_strum_again).takeIf { phase == StrumPhase.DONE },
                )
                for (text in texts.filterNotNull()) assertWhollyShown(compose.onNode(hasText(text), useUnmergedTree = true), "$style $size: $text")
            }
        }
    }

    /** TalkBack hears every string with its reading, the line as a live region, and the switch with its state; tapping it switches off. */
    @Test
    fun talkBackHearsEveryStringAndTheSwitch() {
        show()
        val flat = context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)
        val sharp = context.resources.getQuantityString(R.plurals.tuner_cents_sharp, 8, 8)
        val inTuneWord = context.getString(R.string.tuner_in_tune)
        val expected = listOf(
            "E 2, $inTuneWord",
            "A 2, $flat, ${context.getString(R.string.tuner_spoken_tune_up)}",
            "D 3, $inTuneWord",
            "G 3, $sharp, ${context.getString(R.string.tuner_spoken_tune_down)}",
            context.getString(R.string.tuner_strum_spoken_not_heard, "B 3"),
            context.getString(R.string.tuner_strum_spoken_less_sure, "E 4, $inTuneWord"),
        )
        expected.forEachIndexed { index, spoken ->
            val node = compose.onNodeWithTag(TunerTestTags.strumString(index)).fetchSemanticsNode()
            assertEquals("string $index", listOf(spoken), node.config.getOrNull(SemanticsProperties.ContentDescription))
        }
        val line = status.fetchSemanticsNode().config
        assertEquals(androidx.compose.ui.semantics.LiveRegionMode.Polite, line.getOrNull(SemanticsProperties.LiveRegion))
        status.assert(hasText(context.resources.getQuantityString(R.plurals.tuner_strum_to_tune, 2, 2), substring = true))

        val switch = toggle.fetchSemanticsNode().config
        assertEquals(listOf(context.getString(R.string.tuner_strum_check)), switch.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(Role.Switch, switch.getOrNull(SemanticsProperties.Role))
        assertEquals(ToggleableState.On, switch.getOrNull(SemanticsProperties.ToggleableState))
        toggle.performClick()
        assertEquals(listOf(false), switched)
        // A string is still a button: tapping it tunes that string.
        compose.onNodeWithText("A2").performClick()
        assertEquals(listOf(1), stringClicks)
    }

    /**
     * Light and dark: the in-tune strings' markers are drawn in the style's "in tune" colour at the
     * centre, the flat one's in its accent below the centre and the sharp one's above it, and a
     * string not heard has no marker.
     */
    @Test
    fun markersAreDrawnInTheStylesColours() {
        show()
        for (dark in listOf(false, true)) {
            shownDark.value = dark
            compose.waitForIdle()
            val case = "$style ${if (dark) "dark" else "light"}"
            val image = panel.captureToImage().toPixelMap()
            val panelBounds = panel.getBoundsInRoot()
            fun count(index: Int, colour: Color, fromShare: Float, toShare: Float): Int {
                val cell = compose.onNodeWithTag(TunerTestTags.strumString(index)).getBoundsInRoot()
                val scale = image.width / (panelBounds.right - panelBounds.left).value
                val left = ((cell.left - panelBounds.left).value * scale).toInt().coerceIn(0, image.width - 1)
                val right = ((cell.right - panelBounds.left).value * scale).toInt().coerceIn(left + 1, image.width)
                val top = (image.height * fromShare).toInt()
                val bottom = (image.height * toShare).toInt()
                val wanted = colour.toArgb()
                var found = 0
                for (y in top until bottom) for (x in left until right) if (near(image[x, y].toArgb(), wanted)) found++
                return found
            }
            // The scale runs from 26 % to 80 % of the panel's height, its centre at 53 %.
            if (count(0, inTune, 0.48f, 0.58f) < MARKER_PIXELS) fail("$case: no in-tune marker on E2")
            if (count(1, accent, 0.54f, 0.80f) < MARKER_PIXELS) fail("$case: no marker below the centre on the flat A2")
            if (count(3, accent, 0.26f, 0.52f) < MARKER_PIXELS) fail("$case: no marker above the centre on the sharp G3")
            val stray = count(4, accent, 0.26f, 0.80f) + count(4, inTune, 0.26f, 0.80f)
            if (stray > STRAY_PIXELS) fail("$case: $stray marker pixels on B3, which wasn't heard")
        }
    }

    private fun nl(id: Int): String {
        val configuration = android.content.res.Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(Locale.forLanguageTag("nl")))
        return context.createConfigurationContext(configuration).getString(id)
    }

    /** Every part is in view without scrolling, in the window, clear of the settings button and of each other. */
    private fun assertAllInView(case: String, parts: List<Pair<String, SemanticsNodeInteraction>>): List<DpRect> {
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val corner = DpRect(window.right - CORNER, window.top, window.right, window.top + CORNER)
        val bounds = parts.map { (name, node) -> name to node.assertIsDisplayed().getBoundsInRoot() }
        for ((name, rect) in bounds) {
            if (rect.left < window.left - SLACK || rect.top < window.top - SLACK || rect.right > window.right + SLACK || rect.bottom > window.bottom + SLACK) {
                fail("$case $name $rect is outside the window $window")
            }
            if (rect.overlaps(corner)) fail("$case $name $rect is under the settings button")
        }
        for (i in bounds.indices) {
            for (j in i + 1 until bounds.size) {
                val (a, first) = bounds[i]
                val (b, second) = bounds[j]
                if (first.overlaps(second)) fail("$case $a $first overlaps $b $second")
            }
        }
        return bounds.map { it.second }
    }

    private fun DpRect.overlaps(other: DpRect) =
        left < other.right - SLACK && other.left < right - SLACK && top < other.bottom - SLACK && other.top < bottom - SLACK

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        /** Standard tuning: E2 in tune, A2 12 cents flat, D3 in tune, G3 8 sharp, B3 not heard, E4 in tune but less sure. */
        fun ringing(phase: StrumPhase) = TunerUiState.StrumCheck(
            tuning = Tunings.STANDARD,
            phase = phase,
            strings = if (phase == StrumPhase.WAITING) {
                emptyList()
            } else {
                listOf(
                    StrumString(StrumVerdict.IN_TUNE, 1f),
                    StrumString(StrumVerdict.FLAT, -12f),
                    StrumString(StrumVerdict.IN_TUNE, 0f),
                    StrumString(StrumVerdict.SHARP, 8f),
                    StrumString(StrumVerdict.NOT_HEARD),
                    StrumString(StrumVerdict.IN_TUNE, 3f, sure = false),
                )
            },
        )

        /** ARGB colours within a few steps per channel: the marker's own colour, not blended with what is under it. */
        private fun near(a: Int, b: Int): Boolean =
            abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) <= 6 &&
                abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) <= 6 &&
                abs((a and 0xFF) - (b and 0xFF)) <= 6

        private const val MARKER_PIXELS = 20
        private const val STRAY_PIXELS = 4

        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 15L

        /** A narrow phone (a Galaxy S26) without the status and navigation bars, upright and on its side. */
        private val PHONE_UPRIGHT = DpSize(360.dp, 730.dp)
        private val PHONE_LANDSCAPE = DpSize(780.dp, 330.dp)

        /** The owner's Xiaomi 12X upright (393 x 873 dp) without the status bar and the button bar. */
        private val XIAOMI_UPRIGHT = DpSize(393.dp, 793.dp)
        private const val OWNER_FONT_SCALE = 1.25f

        /** The settings button's 48 dp plus its 8 dp inset, in the window's top end corner. */
        private val CORNER = 56.dp
        private val SLACK = 0.5.dp
    }
}
