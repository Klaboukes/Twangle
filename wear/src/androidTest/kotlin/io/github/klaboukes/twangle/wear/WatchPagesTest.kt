package io.github.klaboukes.twangle.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The watch app's two pages (Phase 80) with stand-ins for the tuner and the metronome: the dots, the swipe,
 * only the page shown composed once a swipe settles (so the tuner's microphone is let go), a page asked
 * for from outside, and the tuner alone with the metronome's switch off.
 */
@RunWith(AndroidJUnit4::class)
class WatchPagesTest {

    @get:Rule
    val compose = createComposeRule()

    private var tools by mutableStateOf(WatchToolSwitches.ALL_ON)
    private var request by mutableStateOf<WatchPage?>(null)
    private var tunerShown = false
    private var metronomeShown = false

    private fun show(opening: WatchPage = WatchPage.TUNER) {
        compose.setContent {
            WatchTheme {
                Box(Modifier.size(198.dp).background(Color.Black)) {
                    WatchAppPages(
                        tools = tools,
                        opening = opening,
                        request = request,
                        onRequestDone = { request = null },
                        tunerPage = {
                            DisposableEffect(Unit) {
                                tunerShown = true
                                onDispose { tunerShown = false }
                            }
                            Box(Modifier.fillMaxSize()) { Text("tuner page") }
                        },
                        metronomePage = {
                            DisposableEffect(Unit) {
                                metronomeShown = true
                                onDispose { metronomeShown = false }
                            }
                            Box(Modifier.fillMaxSize()) { Text("metronome page") }
                        },
                    )
                }
            }
        }
    }

    @Test
    fun theTunerFirstWithTwoDots() {
        show()
        compose.onNodeWithText("tuner page").assertIsDisplayed()
        compose.onNodeWithTag(WatchPageDots.TAG).assertIsDisplayed()
        assertTrue(tunerShown)
        assertFalse(metronomeShown)
    }

    @Test
    fun aSwipeLeftShowsTheMetronomeAndLetsTheTunerGo() {
        show()
        compose.onNodeWithTag(WatchPageDots.PAGER_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("metronome page").assertIsDisplayed()
        assertTrue(metronomeShown)
        assertFalse("the tuner page is gone, its microphone with it", tunerShown)

        compose.onNodeWithTag(WatchPageDots.PAGER_TAG).performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("tuner page").assertIsDisplayed()
        assertTrue(tunerShown)
        assertFalse(metronomeShown)
    }

    @Test
    fun aPageAskedForFromOutsideShows() {
        show()
        request = WatchPage.METRONOME
        compose.waitForIdle()
        compose.onNodeWithText("metronome page").assertIsDisplayed()
        assertNull(request)
    }

    @Test
    fun itCanOpenOnTheMetronome() {
        show(opening = WatchPage.METRONOME)
        compose.onNodeWithText("metronome page").assertIsDisplayed()
        assertFalse(tunerShown)
    }

    @Test
    fun withTheSwitchOffOnlyTheTunerNoDotsNoSwipe() {
        tools = WatchToolSwitches.ALL_OFF
        show()
        compose.onNodeWithText("tuner page").assertIsDisplayed()
        compose.onAllNodesWithTag(WatchPageDots.TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(WatchPageDots.PAGER_TAG).assertCountEquals(0)
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("tuner page").assertIsDisplayed()
        assertFalse(metronomeShown)

        request = WatchPage.METRONOME
        compose.waitForIdle()
        assertNull("the request is let go", request)
        assertEquals(false, metronomeShown)
    }
}
