package io.github.klaboukes.twangle.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.klaboukes.twangle.core.designsystem.TwangleButton
import io.github.klaboukes.twangle.core.designsystem.TwangleIconButton
import io.github.klaboukes.twangle.core.designsystem.TwangleTextButton
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.TimeUnit

/**
 * Phase 27's press effect on the buttons outside the metronome: Material's text, filled and icon
 * buttons through the Twangle buttons, and a plain row through the theme's light. Each, in each
 * style, light and dark: held down it lights up (in place of Material's ripple), released it fades
 * back to how it was; a tap and TalkBack's double tap (a click without a touch) each click once; the
 * buttons keep Material's 48 dp touch area, and a text button's light stays on its 40 dp pill.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class PressEffectTest(private val style: String) {

    private val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val dark = mutableStateOf(false)
    private val clicks = mutableIntStateOf(0)

    private fun show() {
        compose.setContent {
            TwangleTheme(darkTheme = dark.value, animate = false) {
                Column(Modifier.padding(16.dp)) {
                    // Each button in a slot of its own: the room it takes, its touch area round it. (The
                    // button's own node is its face, as with Material's buttons.)
                    Box(Modifier.testTag(slot(TEXT))) {
                        TwangleTextButton(onClick = { clicks.intValue++ }, modifier = Modifier.testTag(TEXT)) { Text("Cancel") }
                    }
                    Box(Modifier.testTag(slot(FILLED))) {
                        TwangleButton(onClick = { clicks.intValue++ }, modifier = Modifier.testTag(FILLED)) { Text("Next") }
                    }
                    Box(Modifier.testTag(slot(ICON))) {
                        TwangleIconButton(onClick = { clicks.intValue++ }, modifier = Modifier.testTag(ICON)) { Text("+") }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag(ROW)
                            .selectable(selected = false, role = Role.RadioButton) { clicks.intValue++ },
                    ) { Text("A row", Modifier.padding(16.dp)) }
                }
            }
        }
    }

    private fun each(check: (tag: String, case: String) -> Unit) {
        show()
        for (isDark in listOf(false, true)) {
            dark.value = isDark
            compose.waitForIdle()
            for (tag in listOf(TEXT, FILLED, ICON, ROW)) check(tag, "$style ${if (isDark) "dark" else "light"} $tag:")
        }
    }

    private fun slot(tag: String) = "$tag-slot"

    private fun shot(tag: String): ImageBitmap = compose.onNodeWithTag(tag).captureToImage()

    private fun ImageBitmap.differsFrom(other: ImageBitmap): Boolean {
        val a = toPixelMap()
        val b = other.toPixelMap()
        if (a.width != b.width || a.height != b.height) return true
        for (y in 0 until a.height) for (x in 0 until a.width) if (a[x, y] != b[x, y]) return true
        return false
    }

    @Test
    fun aHeldPressLightsTheButtonAndItFadesBackAfterRelease() = each { tag, case ->
        compose.mainClock.autoAdvance = false
        try {
            val idle = shot(tag)
            compose.onNodeWithTag(tag).performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(HELD_MS)
            assertTrue("$case nothing lights up while held", shot(tag).differsFrom(idle))
            compose.onNodeWithTag(tag).performTouchInput { up() }
            compose.mainClock.advanceTimeBy(FADED_MS)
            assertFalse("$case still lit after the fade", shot(tag).differsFrom(idle))
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun aTapAndTalkBacksDoubleTapEachClickOnce() = each { tag, case ->
        clicks.intValue = 0
        compose.onNodeWithTag(tag).performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals("$case a tap", 1, clicks.intValue)
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
        assertEquals("$case TalkBack's double tap", 2, clicks.intValue)
    }

    @Test
    fun theButtonsKeepMaterialsTouchArea() {
        show()
        for (tag in listOf(TEXT, FILLED, ICON)) {
            val bounds = compose.onNodeWithTag(slot(tag)).getUnclippedBoundsInRoot()
            assertTrue("$style $tag: ${bounds.height} tall", bounds.height >= 48.dp - 0.5.dp)
            assertTrue("$style $tag: ${bounds.width} wide", bounds.width >= 48.dp - 0.5.dp)
        }
        val icon = compose.onNodeWithTag(slot(ICON)).getUnclippedBoundsInRoot()
        assertEquals("$style icon button's height", 48f, icon.height.value, 0.5f)
    }

    /** The light lies on the 40 dp pill, not across the 48 dp touch area round it. */
    @Test
    fun aTextButtonsLightStaysOnItsPill() {
        show()
        compose.mainClock.autoAdvance = false
        try {
            val idle = shot(slot(TEXT)).toPixelMap()
            compose.onNodeWithTag(TEXT).performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(HELD_MS)
            val held = shot(slot(TEXT)).toPixelMap()
            compose.onNodeWithTag(TEXT).performTouchInput { up() }
            compose.mainClock.advanceTimeBy(FADED_MS)
            // The top 2 dp of the touch area stay as they were.
            val margin = (2 * compose.density.density).toInt()
            for (y in 0 until margin) for (x in 0 until idle.width) {
                assertEquals("$style pixel $x, $y above the pill", idle[x, y], held[x, y])
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<String> = listOf("Ink")

        private const val TEXT = "text"
        private const val FILLED = "filled"
        private const val ICON = "icon"
        private const val ROW = "row"

        /** Well into the hold, past the sink's 35 ms smoothing. */
        private const val HELD_MS = 150L

        /** Past the 280 ms fade and the sink's return. */
        private const val FADED_MS = 600L

        private const val TEST_TIMEOUT_SECONDS = 30L
    }
}
