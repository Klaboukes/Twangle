package io.github.klaboukes.twangle.app

import android.Manifest
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.klaboukes.twangle.app.navigation.Destination
import io.github.klaboukes.twangle.app.navigation.NavigationTestTags
import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.practice.PracticeTestTags
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout

/**
 * The Practice tab (Phase 32) in the real app, with its switch on (every debug build): the bar's
 * Metronome tab is called Practice, the tool switch at its top picks the metronome or chords, the
 * pick is saved at once, and the tab opens on the tool used last, also after the app starts again.
 */
class PracticeTabTest {

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build())
        .around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO))

    private val container = ApplicationProvider.getApplicationContext<TwangleApplication>().container
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var switchesBefore: ToolSwitches? = null

    @Before
    fun setUp() {
        switchesBefore = container.toolSwitches
        container.toolSwitches = ToolSwitches.ALL_ON
        runBlocking {
            // The tour would cover the tuner and the bar.
            container.settingsRepository.setTourSeen(true)
            container.settingsRepository.setPracticeTool(PracticeTool.METRONOME)
        }
    }

    @After
    fun tearDown() {
        switchesBefore?.let { container.toolSwitches = it }
        runBlocking { container.settingsRepository.setPracticeTool(PracticeTool.METRONOME) }
    }

    private fun shown(tag: String): Boolean = device.wait(Until.hasObject(By.res(tag)), WAIT_MS)
    private fun gone(tag: String): Boolean = device.wait(Until.gone(By.res(tag)), WAIT_MS)
    private fun click(tag: String) = device.findObject(By.res(tag)).click()

    private fun savedTool(): PracticeTool = runBlocking { container.settingsRepository.settings.first().practiceTool }

    /** Waits up to [WAIT_MS] for the saved tool to become [tool]. */
    private fun waitForSaved(tool: PracticeTool): PracticeTool {
        val end = SystemClock.uptimeMillis() + WAIT_MS
        while (savedTool() != tool && SystemClock.uptimeMillis() < end) SystemClock.sleep(50)
        return savedTool()
    }

    private fun openPractice() {
        val tab = NavigationTestTags.tab(Destination.METRONOME)
        assertTrue("the bar shows", shown(tab))
        assertTrue(
            "the tab is called Practice",
            device.findObject(By.res(tab)).hasObject(By.text(context.getString(R.string.nav_practice))),
        )
        click(tab)
        assertTrue("the tool switch shows", shown(PracticeTestTags.SWITCH))
    }

    @Test
    fun theSwitchPicksTheToolAndPracticeOpensOnTheOneUsedLast() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openPractice()
            assertTrue("a fresh Practice opens on the metronome", shown(MetronomeTestTags.DIAL))
            assertTrue("both tools", shown(PracticeTestTags.tool(PracticeTool.METRONOME)) && shown(PracticeTestTags.tool(PracticeTool.CHORDS)))

            click(PracticeTestTags.tool(PracticeTool.CHORDS))
            assertTrue("chords show", shown(PracticeTestTags.CHORDS))
            assertTrue("the metronome has gone", gone(MetronomeTestTags.DIAL))
            assertEquals("the pick is saved", PracticeTool.CHORDS, waitForSaved(PracticeTool.CHORDS))

            // Another tab and back: still chords.
            click(NavigationTestTags.tab(Destination.SETTINGS))
            assertTrue(gone(PracticeTestTags.SWITCH))
            click(NavigationTestTags.tab(Destination.METRONOME))
            assertTrue("back on chords", shown(PracticeTestTags.CHORDS))
        }
        // A fresh start opens Practice on the tool used last.
        ActivityScenario.launch(MainActivity::class.java).use {
            openPractice()
            assertTrue("opens on chords", shown(PracticeTestTags.CHORDS))
            assertFalse("not on the metronome", device.hasObject(By.res(MetronomeTestTags.DIAL)))
            click(PracticeTestTags.tool(PracticeTool.METRONOME))
            assertTrue("the metronome shows", shown(MetronomeTestTags.DIAL))
            assertEquals("the pick is saved", PracticeTool.METRONOME, waitForSaved(PracticeTool.METRONOME))
        }
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 40L
        const val WAIT_MS = 5_000L
    }
}
