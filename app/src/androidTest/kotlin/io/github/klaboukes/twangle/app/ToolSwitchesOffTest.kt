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
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.settings.TunerMode
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
 * With every after-v1.0 tool switched off (Phase 28), the debug app is the v1.0 app the Play build
 * shows: the tuner's two modes and the bar's Tuner · Instrument · Metronome · Settings, nothing more.
 * The hook for any UI test that needs the v1.0 screen: set [AppContainer.toolSwitches] before the
 * activity starts, and put it back after. A later phase adds what its tool must not show here.
 */
class ToolSwitchesOffTest {

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build())
        .around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO))

    private val container = ApplicationProvider.getApplicationContext<TwangleApplication>().container
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun shown(tag: String): Boolean = device.wait(Until.hasObject(By.res(tag)), WAIT_MS)
    private fun tagsLike(pattern: String): Set<String> =
        device.findObjects(By.res(Pattern.compile(pattern))).map { it.resourceName }.toSet()

    @Test
    fun everySwitchOffShowsTheV10App() {
        val before = container.toolSwitches
        // The tour would cover the tuner.
        runBlocking { container.settingsRepository.setTourSeen(true) }
        container.toolSwitches = ToolSwitches.ALL_OFF
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                assertTrue("the tuner shows", shown(TunerTestTags.mode(TunerMode.CHROMATIC)))
                assertEquals(
                    "the tuner's v1.0 modes only",
                    setOf(TunerTestTags.mode(TunerMode.AUTO), TunerTestTags.mode(TunerMode.CHROMATIC)),
                    tagsLike("tuner_mode_.*"),
                )
                assertEquals(
                    "the v1.0 bar only",
                    (listOf(Destination.TUNER, Destination.METRONOME, Destination.SETTINGS).map(NavigationTestTags::tab) + NavigationTestTags.INSTRUMENT).toSet(),
                    tagsLike("nav_(?!bar$|rail$).*"),
                )
            }
        } finally {
            container.toolSwitches = before
        }
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 30L
        const val WAIT_MS = 5_000L
    }
}
