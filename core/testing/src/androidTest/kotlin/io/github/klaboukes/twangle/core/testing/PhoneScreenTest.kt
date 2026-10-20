package io.github.klaboukes.twangle.core.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [PhoneScreen] shows the same window on every phone: a window of the area plus the bars, in its own dp
 * whether the phone's screen is larger or smaller, and the bars as the content's insets, never the
 * phone's own bars.
 */
@RunWith(AndroidJUnit4::class)
class PhoneScreenTest {

    @get:Rule
    val compose = createSharedActivityComposeRule()

    @Test
    fun theWindowAndItsBarsAreTheSameInItsOwnDpOnEveryPhone() {
        val shownArea = mutableStateOf(DpSize(360.dp, 570.dp))
        val shownBars = mutableStateOf(SystemBars.NONE)
        compose.setContent {
            PhoneScreen(shownArea.value, shownBars.value) {
                Box(Modifier.fillMaxSize().testTag(WINDOW)) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag(SAFE))
                }
            }
        }
        // A small phone, a phone on its side, and a tablet larger than any phone's screen.
        for (area in listOf(DpSize(320.dp, 640.dp), DpSize(700.dp, 330.dp), DpSize(1280.dp, 800.dp))) {
            val onItsSide = area.width > area.height
            val bars = listOf(SystemBars.NONE) + Navigation.entries.flatMap { listOf(SystemBars.phone(onItsSide, it), SystemBars.tab(onItsSide, it)) }
            for (bar in bars) {
                shownArea.value = area
                shownBars.value = bar
                compose.waitForIdle()
                val case = "${area.width} x ${area.height} with $bar"
                val window = bar.around(area)
                assertNear("$case: the window", DpRect(0.dp, 0.dp, window.width, window.height), compose.onNodeWithTag(WINDOW).getBoundsInRoot())
                assertNear("$case: inside the bars", bar.safeArea(area), compose.onNodeWithTag(SAFE).getBoundsInRoot())
            }
        }
    }

    private fun assertNear(what: String, expected: DpRect, actual: DpRect) {
        // A px of rounding, which on a tablet window shrunk onto a phone is up to about 1 dp.
        val slack = 1.2f
        assertEquals("$what left of $actual", expected.left.value, actual.left.value, slack)
        assertEquals("$what top of $actual", expected.top.value, actual.top.value, slack)
        assertEquals("$what right of $actual", expected.right.value, actual.right.value, slack)
        assertEquals("$what bottom of $actual", expected.bottom.value, actual.bottom.value, slack)
    }

    private companion object {
        const val WINDOW = "window"
        const val SAFE = "safe"
    }
}
