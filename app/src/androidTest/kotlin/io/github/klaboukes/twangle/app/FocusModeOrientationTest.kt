package io.github.klaboukes.twangle.app

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.provider.Settings
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.klaboukes.twangle.app.navigation.Destination
import io.github.klaboukes.twangle.app.navigation.NavigationTestTags
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout

/**
 * Focus mode (Phase 36) asks Android to keep the whole app on its side, either way round, also with
 * auto-rotate off, and the request holds on every tab. The tuner takes the whole screen (Phase 40): no
 * rail and no status bar until a tap brings back its controls and the rail; other tabs show both. Switched off, it is turned back upright (with auto-rotate off) and the request is let
 * go, so the screen follows the phone again. On the
 * phone this really turns the screen.
 */
class FocusModeOrientationTest {

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build())
        .around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO))

    private val settings = ApplicationProvider.getApplicationContext<TwangleApplication>().container.settingsRepository
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var focusModeBefore: Boolean? = null

    @Before
    fun setUp() {
        runBlocking {
            focusModeBefore = settings.settings.first().focusMode
            // The tour would cover the tuner and the rail.
            settings.setTourSeen(true)
            settings.setFocusMode(false)
        }
    }

    @After
    fun tearDown() {
        focusModeBefore?.let { runBlocking { settings.setFocusMode(it) } }
    }

    private fun ActivityScenario<MainActivity>.requested(): Int {
        var orientation = Int.MIN_VALUE
        onActivity { orientation = it.requestedOrientation }
        return orientation
    }

    private fun ActivityScenario<MainActivity>.onItsSide(): Boolean {
        var side = false
        onActivity { side = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        return side
    }

    /** Waits up to [WAIT_MS] for [condition]; returns whether it came true. */
    private fun waitFor(condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + WAIT_MS
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }

    private fun autoRotate(): Boolean = Settings.System.getInt(
        InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,
        Settings.System.ACCELEROMETER_ROTATION,
        0,
    ) == 1

    private fun ActivityScenario<MainActivity>.statusBarShown(): Boolean {
        var shown = true
        onActivity { shown = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.statusBars()) != false }
        return shown
    }

    private fun shown(tag: String): Boolean = device.wait(Until.hasObject(By.res(tag)), WAIT_MS)

    @Test
    fun focusModeKeepsTheAppOnItsSideAndLetsGoWhenOff() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue("no side asked for at first", waitFor { scenario.requested() == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED })

            runBlocking { settings.setFocusMode(true) }
            assertTrue("focus mode asks for either side", waitFor { scenario.requested() == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE })
            assertTrue("and the screen turns", waitFor { scenario.onItsSide() })
            assertTrue("the compact tuner shows", shown(TunerTestTags.COMPACT))
            assertTrue("over the whole screen: no rail", device.wait(Until.gone(By.res(NavigationTestTags.RAIL)), WAIT_MS))
            assertTrue("and no status bar", waitFor { !scenario.statusBarShown() })

            // A tap brings back the controls and the rail, so the other tabs are one tap away.
            (device.findObject(By.res(TunerTestTags.COMPACT)) ?: error("no compact tuner")).click()
            assertTrue("the rail comes back", shown(NavigationTestTags.RAIL))
            assertTrue("and the status bar", waitFor { scenario.statusBarShown() })

            // Another tab keeps the request: the screen doesn't turn on a tab switch.
            (device.wait(Until.findObject(By.res(NavigationTestTags.tab(Destination.SETTINGS))), WAIT_MS) ?: error("no Settings tab")).click()
            assertTrue("Settings shows", device.wait(Until.gone(By.res(TunerTestTags.GAUGE)), WAIT_MS))
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, scenario.requested())
            assertTrue("still on its side", scenario.onItsSide())
            // With its rail and the status bar, after the controls' time is up too.
            SystemClock.sleep(CONTROLS_GONE_MS)
            assertTrue("Settings has the rail", shown(NavigationTestTags.RAIL))
            assertTrue("and the status bar", scenario.statusBarShown())

            runBlocking { settings.setFocusMode(false) }
            assertTrue("off, the request is let go", waitFor { scenario.requested() == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED })
            // With auto-rotate off (as the test run holds the phone) it is first turned back upright, as it was.
            if (!autoRotate()) assertTrue("back upright", !scenario.onItsSide())
        }
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 40L
        const val WAIT_MS = 5_000L

        /** Longer than the tuner's controls stay shown after a tap (5 s), had they stayed in charge. */
        const val CONTROLS_GONE_MS = 6_000L
    }
}
