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
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout

/**
 * With every music tool switched off (Phase 28, [ToolSwitchesOffRule]), the debug app is the app
 * the Play build shows until the tools go live: the tuner's two modes and the bar's Tuner ·
 * Instrument · Metronome · Settings, and the Metronome tab the metronome alone (no Practice, no tool
 * switch, no chords), nothing more. A later phase adds what its tool must not show here.
 */
class ToolSwitchesOffTest {

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build())
        .around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO))
        .around(ToolSwitchesOffRule())

    private val container = ApplicationProvider.getApplicationContext<TwangleApplication>().container
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun shown(tag: String): Boolean = device.wait(Until.hasObject(By.res(tag)), WAIT_MS)
    private fun tagsLike(pattern: String): Set<String> =
        device.findObjects(By.res(Pattern.compile(pattern))).map { it.resourceName }.toSet()

    @Test
    fun everySwitchOffShowsTheV10App() {
        // The tour would cover the tuner.
        runBlocking { container.settingsRepository.setTourSeen(true) }
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue("the tuner shows", shown(TunerTestTags.mode(TunerMode.CHROMATIC)))
            assertEquals(
                "the tuner's v1.0 modes only",
                setOf(TunerTestTags.mode(TunerMode.AUTO), TunerTestTags.mode(TunerMode.CHROMATIC)),
                tagsLike("tuner_mode_.*"),
            )
            // Phase 29: the strum check keeps its own switch; no "more tools" button, no 12th-fret check.
            // Phase 30: no chord name either.
            // Phase 31: nor the pitch trail.
            assertTrue("the strum check's own switch", shown(TunerTestTags.STRUM_TOGGLE))
            assertEquals("no more tools", emptySet<String>(), tagsLike("tuner_more_tools.*|tuner_tool_.*|tuner_intonation_.*|tuner_chord_.*|tuner_trail_.*"))
            assertEquals(
                "the v1.0 bar only",
                (listOf(Destination.TUNER, Destination.METRONOME, Destination.SETTINGS).map(NavigationTestTags::tab) + NavigationTestTags.INSTRUMENT).toSet(),
                tagsLike("nav_(?!bar$|rail$).*"),
            )
            // Phase 32: the Metronome tab is called Metronome and shows the metronome alone.
            val metronomeTab = device.findObject(By.res(NavigationTestTags.tab(Destination.METRONOME)))
            assertTrue("the tab is called Metronome", metronomeTab.hasObject(By.text(context.getString(R.string.nav_metronome))))
            metronomeTab.click()
            assertTrue("the metronome shows", shown(MetronomeTestTags.DIAL))
            assertEquals("no Practice", emptySet<String>(), tagsLike("practice_.*"))
            assertTrue("no Practice name", device.findObjects(By.text(context.getString(R.string.nav_practice))).isEmpty())
        }
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 30L
        const val WAIT_MS = 5_000L
    }
}
