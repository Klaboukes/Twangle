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
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.practice.PracticeTestTags
import io.github.klaboukes.twangle.feature.settings.R as SettingsR
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
 * tab (the Metronome and Practice tabs, Phase 69). Back from Settings is no tap on the tab, so the tool
 * stays. The gear on each tab opens Settings, and Back returns to that tab (Phase 69).
 */
class TunerTabTest {

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build())
        .around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO))
        .around(FocusModeOffRule())

    private val container = ApplicationProvider.getApplicationContext<TwangleApplication>().container
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var instrumentBefore: Instrument? = null

    @Before
    fun setUp() {
        runBlocking {
            instrumentBefore = container.settingsRepository.settings.first().instrument
            // The guitar offers every tool, so its "more tools" button shows.
            container.settingsRepository.setInstrument(Instruments.GUITAR)
            container.settingsRepository.setMode(TunerMode.AUTO)
        }
    }

    @After
    fun tearDown() {
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

            // From another tab: the metronome's, then Practice's.
            turnStrumCheckOn()
            tab(Destination.METRONOME)
            assertTrue("the metronome shows", shown(MetronomeTestTags.DIAL))
            tab(Destination.PRACTICE)
            assertTrue("Practice shows", shown(PracticeTestTags.TOOL_BUTTON))
            tab(Destination.TUNER)
            assertTrue("the tuner shows", shown(TunerTestTags.MORE_TOOLS))
            assertTrue("the Tuner tab from another tab closes it", gone(TunerTestTags.STRUM_PANEL))
            device.waitForIdle()
            assertFalse("and it doesn't come back", device.hasObject(By.res(TunerTestTags.STRUM_PANEL)))

            // Back is no tap on the tab: the tool stays.
            turnStrumCheckOn()
            click(NavigationTestTags.SETTINGS)
            assertTrue("Settings shows", gone(TunerTestTags.MORE_TOOLS))
            device.pressBack()
            assertTrue("back on the tuner, still in strum check", shown(TunerTestTags.STRUM_PANEL))
        }
    }

    /**
     * Settings is behind the gear on every tab (Phase 69), not a tab: it opens without the bar, and Back,
     * or its back arrow, returns to the tab whose gear opened it.
     */
    @Test
    fun theGearOnEveryTabOpensSettingsAndBackReturnsToThatTab() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var backArrow = ""
            scenario.onActivity { backArrow = it.getString(SettingsR.string.settings_back) }
            assertTrue("the tuner shows", shown(TunerTestTags.MORE_TOOLS))
            for ((destination, landmark) in listOf(
                Destination.TUNER to TunerTestTags.MORE_TOOLS,
                Destination.METRONOME to MetronomeTestTags.DIAL,
                Destination.PRACTICE to PracticeTestTags.TOOL_BUTTON,
            )) {
                for (arrow in listOf(false, true)) {
                    tab(destination)
                    assertTrue("$destination shows", shown(landmark))
                    click(NavigationTestTags.SETTINGS)
                    assertTrue("Settings from $destination: no bar", gone(NavigationTestTags.BAR))
                    assertTrue("Settings from $destination: $destination gone", gone(landmark))
                    if (arrow) click(By.desc(backArrow)) else device.pressBack()
                    assertTrue("back ${if (arrow) "arrow" else ""} from Settings on $destination", shown(landmark))
                    assertTrue("with the bar", shown(NavigationTestTags.BAR))
                }
            }
        }
    }

    private fun click(selector: androidx.test.uiautomator.BySelector) =
        (device.wait(Until.findObject(selector), WAIT_MS) ?: error("no $selector")).click()

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 40L
        const val WAIT_MS = 5_000L
    }
}
