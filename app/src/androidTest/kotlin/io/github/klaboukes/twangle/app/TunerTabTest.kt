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
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.practice.PracticeTestTags
import io.github.klaboukes.twangle.feature.settings.SETTINGS_BACK_TAG
import io.github.klaboukes.twangle.feature.settings.R as SettingsR
import io.github.klaboukes.twangle.feature.tuner.R as TunerR
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
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
 * The bar's Tuner tab always leads to the plain tuner (owner's request, 2026-10-17): a tap on it closes
 * the tuner's tool (here the strum check), also while the tuner shows and when coming back from another
 * tab (the Metronome and Practice tabs, Phase 69). Back from Settings is no tap on the tab, so the tool
 * stays; Back on the tuner then ends it (2.0, Phase 71). The gear on each tab opens Settings, and Back returns to that tab (Phase 69).
 */
class TunerTabTest {

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build())
        .around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO))
        .around(FocusModeOffRule())
        .around(QuietMicrophoneRule())

    private val container = ApplicationProvider.getApplicationContext<TwangleApplication>().container
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var instrumentBefore: Instrument? = null

    @Before
    fun setUp() {
        runBlocking {
            val before = container.settingsRepository.settings.first()
            instrumentBefore = before.instrument
            // The guitar has the 12th-fret check, the tuner's one tool since 2.0's Phase 74, opened from the picker sheet.
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

    /**
     * From the picker sheet's last row (2.0, Phase 71), found by its name in the app's language: the sheet is a window
     * of its own, whose tags UI Automator doesn't see.
     */
    private fun turnTheCheckOn(scenario: ActivityScenario<MainActivity>) {
        var name = ""
        scenario.onActivity { name = it.getString(TunerR.string.tuner_intonation_check) }
        click(TunerTestTags.PICKER_CHIP)
        (device.wait(Until.findObject(By.text(name)), WAIT_MS) ?: error("no row \"$name\" in the sheet")).click()
        assertTrue("the 12th-fret check is on", shown(TunerTestTags.INTONATION_PANEL))
    }

    @Test
    fun theTunerTabClosesTheToolAndBackKeepsIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // While the tuner shows.
            turnTheCheckOn(scenario)
            tab(Destination.TUNER)
            assertTrue("a tap on the open Tuner tab closes it", gone(TunerTestTags.INTONATION_PANEL))

            // From another tab: the metronome's, then Practice's.
            turnTheCheckOn(scenario)
            tab(Destination.METRONOME)
            assertTrue("the metronome shows", shown(MetronomeTestTags.RULER))
            tab(Destination.PRACTICE)
            assertTrue("Practice shows", shown(PracticeTestTags.CARDS))
            tab(Destination.TUNER)
            assertTrue("the tuner shows", shown(TunerTestTags.PICKER_CHIP))
            assertTrue("the Tuner tab from another tab closes it", gone(TunerTestTags.INTONATION_PANEL))
            device.waitForIdle()
            assertFalse("and it doesn't come back", device.hasObject(By.res(TunerTestTags.INTONATION_PANEL)))

            // Back is no tap on the tab: the tool stays.
            turnTheCheckOn(scenario)
            click(NavigationTestTags.SETTINGS)
            assertTrue("Settings shows", gone(TunerTestTags.PICKER_CHIP))
            device.pressBack()
            assertTrue("back on the tuner, still in the 12th-fret check", shown(TunerTestTags.INTONATION_PANEL))
            // On the tuner, Back ends the check, and the app stays.
            device.pressBack()
            assertTrue("Back ends the 12th-fret check", gone(TunerTestTags.INTONATION_PANEL))
            assertTrue("on the plain tuner", shown(TunerTestTags.PICKER_CHIP))
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
            assertTrue("the tuner shows", shown(TunerTestTags.PICKER_CHIP))
            for ((destination, landmark) in listOf(
                Destination.TUNER to TunerTestTags.PICKER_CHIP,
                Destination.METRONOME to MetronomeTestTags.RULER,
                Destination.PRACTICE to PracticeTestTags.CARDS,
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

    /**
     * Every tab's top row is one row (the owner's review of 2026-11-03: switching from the tuner to the metronome moved
     * the gear and the end button a little, and the metronome's button was bigger): the gear stands in the same place
     * on every tab, and the tuner's focus mode button and the metronome's ⋯ are one size in one place; a Practice tool's
     * and Settings' back arrow stand in the gear's place, as large.
     */
    @Test
    fun theTopRowStandsStillAcrossTheTabs() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue("the tuner shows", shown(TunerTestTags.PICKER_CHIP))
            val gears = mutableMapOf<Destination, android.graphics.Rect>()
            val ends = mutableMapOf<Destination, android.graphics.Rect>()
            for ((destination, end) in listOf(
                Destination.TUNER to TunerTestTags.ENTER_FOCUS,
                Destination.METRONOME to MetronomeTestTags.OPTIONS,
                Destination.PRACTICE to null,
            )) {
                tab(destination)
                gears[destination] = boundsOf(NavigationTestTags.SETTINGS)
                if (end != null) ends[destination] = boundsOf(end)
            }
            assertEquals("the metronome's gear", gears[Destination.TUNER], gears[Destination.METRONOME])
            assertEquals("Practice's gear", gears[Destination.TUNER], gears[Destination.PRACTICE])
            // The pages without the gear have their back arrow in its place, as large (the owner's review of 2026-11-04).
            click(PracticeTestTags.card(PracticeTool.CHORDS))
            assertEquals("a Practice tool's back arrow", gears[Destination.TUNER], boundsOf(PracticeTestTags.BACK))
            click(PracticeTestTags.BACK)
            assertTrue("back on the cards", shown(PracticeTestTags.CARDS))
            click(NavigationTestTags.SETTINGS)
            assertEquals("Settings' back arrow", gears[Destination.TUNER], boundsOf(SETTINGS_BACK_TAG))
            device.pressBack()
            assertTrue("back on Practice", shown(PracticeTestTags.CARDS))
            // Both tags are on the buttons' faces.
            assertEquals("the ⋯ where focus mode's button is, as large", ends[Destination.TUNER], ends[Destination.METRONOME])
            assertEquals("the end button's face as the gear's", gears.getValue(Destination.TUNER).width(), ends.getValue(Destination.TUNER).width())
        }
    }

    /** [tag]'s bounds, found afresh while the tuner's screen keeps changing round it (a found node can go stale at once). */
    private fun boundsOf(tag: String): android.graphics.Rect {
        assertTrue("$tag shows", shown(tag))
        repeat(BOUNDS_TRIES) {
            try {
                device.findObject(By.res(tag))?.let { return it.visibleBounds }
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                // Found again on the next try.
            }
            SystemClock.sleep(BOUNDS_RETRY_MS)
        }
        error("$tag kept going stale")
    }

    private fun click(selector: androidx.test.uiautomator.BySelector) =
        (device.wait(Until.findObject(selector), WAIT_MS) ?: error("no $selector")).click()

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 40L
        const val WAIT_MS = 5_000L
        const val BOUNDS_TRIES = 40
        const val BOUNDS_RETRY_MS = 50L
    }
}
