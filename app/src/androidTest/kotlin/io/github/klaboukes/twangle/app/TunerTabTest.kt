package io.github.klaboukes.twangle.app

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.klaboukes.twangle.app.navigation.Destination
import io.github.klaboukes.twangle.app.navigation.NavigationTestTags
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.practice.PracticeTestTags
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import io.github.klaboukes.twangle.feature.tuner.TunerTool
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout

/**
 * The bar's Tuner tab always leads to the plain tuner (owner's request, 2026-10-17): a tap on it closes
 * the tuner's tool (here the strum check), also while the tuner shows and when coming back from another
 * tab. Back from another tab is no tap on the tab, so the tool stays.
 */
class TunerTabTest {

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build())
        .around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO))
        .around(FocusModeOffRule())

    private val container = ApplicationProvider.getApplicationContext<TwangleApplication>().container
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var switchesBefore: ToolSwitches? = null
    private var instrumentBefore: Instrument? = null

    @Before
    fun setUp() {
        switchesBefore = container.toolSwitches
        container.toolSwitches = ToolSwitches.ALL_ON
        runBlocking {
            instrumentBefore = container.settingsRepository.settings.first().instrument
            // The tour would cover the tuner and the bar; the guitar offers every tool, so its "more tools" button shows.
            container.settingsRepository.setTourSeen(true)
            container.settingsRepository.setInstrument(Instruments.GUITAR)
            container.settingsRepository.setMode(TunerMode.AUTO)
        }
    }

    @After
    fun tearDown() {
        switchesBefore?.let { container.toolSwitches = it }
        instrumentBefore?.let { runBlocking { container.settingsRepository.setInstrument(it) } }
    }

    private fun shown(tag: String): Boolean = device.wait(Until.hasObject(By.res(tag)), WAIT_MS)
    private fun gone(tag: String): Boolean = device.wait(Until.gone(By.res(tag)), WAIT_MS)
    private fun click(tag: String) = (device.wait(Until.findObject(By.res(tag)), WAIT_MS) ?: error("no $tag")).click()
    private fun tab(destination: Destination) = click(NavigationTestTags.tab(destination))

    private fun turnStrumCheckOn() {
        click(TunerTestTags.MORE_TOOLS)
        click(TunerTestTags.tool(TunerTool.STRUM_CHECK))
        assertTrue("strum check is on", shown(TunerTestTags.STRUM_PANEL))
    }

    @Test
    fun theTunerTabClosesTheToolAndBackKeepsIt() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // While the tuner shows.
            turnStrumCheckOn()
            tab(Destination.TUNER)
            assertTrue("a tap on the open Tuner tab closes it", gone(TunerTestTags.STRUM_PANEL))

            // From another tab.
            turnStrumCheckOn()
            tab(Destination.METRONOME)
            assertTrue("Practice shows", shown(PracticeTestTags.SWITCH))
            tab(Destination.TUNER)
            assertTrue("the tuner shows", shown(TunerTestTags.MORE_TOOLS))
            assertTrue("the Tuner tab from another tab closes it", gone(TunerTestTags.STRUM_PANEL))
            device.waitForIdle()
            assertFalse("and it doesn't come back", device.hasObject(By.res(TunerTestTags.STRUM_PANEL)))

            // Back is no tap on the tab: the tool stays.
            turnStrumCheckOn()
            tab(Destination.SETTINGS)
            assertTrue("Settings shows", gone(TunerTestTags.MORE_TOOLS))
            device.pressBack()
            assertTrue("back on the tuner, still in strum check", shown(TunerTestTags.STRUM_PANEL))
        }
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 40L
        const val WAIT_MS = 5_000L
    }
}
