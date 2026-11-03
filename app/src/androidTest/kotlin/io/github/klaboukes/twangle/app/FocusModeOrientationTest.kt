package io.github.klaboukes.twangle.app

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.provider.Settings
import android.view.View
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout

/**
 * Focus mode (Phase 36; 2.0, Phase 73: the tuner's button alone, held by the app, not saved) asks Android to keep the
 * whole app on its side, either way round, also with auto-rotate off, and the request holds on every tab. The tuner
 * takes the whole screen (Phase 40), its wide layout without the pegs: no rail and no status bar until a tap brings
 * back its controls (the pegs) and the rail; other tabs show both, and
 * Settings, opened from the gear at the rail's top, the status bar. Switched off, it is turned back upright (with auto-rotate off) and the request is let
 * go, so the screen follows the phone again; a large screen is let go at once. On the
 * phone or tablet this really turns the screen. Afterwards nothing lies over the status bar: the app
 * stays edge to edge.
 */
class FocusModeOrientationTest {

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build())
        .around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO))
        .around(QuietMicrophoneRule())

    private val focusMode = ApplicationProvider.getApplicationContext<TwangleApplication>().container.focusMode
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    private val autoRotateBefore = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
    private val lockBefore = Settings.System.getInt(resolver, Settings.System.USER_ROTATION, 0)

    @Before
    fun setUp() {
        focusMode.value = false
    }

    @After
    fun tearDown() {
        focusMode.value = false
        // The phone's rotation as it was (the large screen test moves the lock).
        device.executeShellCommand("settings put system user_rotation $lockBefore")
        device.executeShellCommand("settings put system accelerometer_rotation $autoRotateBefore")
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

    private fun autoRotate(): Boolean = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    /** Edge to edge: the app's content starts at the screen's top, behind the status bar, with no band over it. */
    private fun ActivityScenario<MainActivity>.edgeToEdge(): Boolean {
        var top = -1
        onActivity { activity ->
            val place = IntArray(2)
            activity.findViewById<View>(android.R.id.content).getLocationOnScreen(place)
            top = place[1]
        }
        return top == 0
    }

    private fun ActivityScenario<MainActivity>.largeScreen(): Boolean {
        var large = false
        onActivity { large = it.resources.configuration.smallestScreenWidthDp >= LARGE_SCREEN_DP }
        return large
    }

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

            focusMode.value = true
            assertTrue("focus mode asks for either side", waitFor { scenario.requested() == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE })
            assertTrue("and the screen turns", waitFor { scenario.onItsSide() })
            assertTrue("the full-screen tuner shows", shown(TunerTestTags.COMPACT))
            assertTrue("in the wide layout", shown(TunerTestTags.WIDE))
            assertTrue("over the whole screen: no rail", device.wait(Until.gone(By.res(NavigationTestTags.RAIL)), WAIT_MS))
            assertTrue("and no status bar", waitFor { !scenario.statusBarShown() })

            // A tap brings back the controls and the rail, so the other tabs are one tap away.
            (device.findObject(By.res(TunerTestTags.COMPACT)) ?: error("no full-screen tuner")).click()
            assertTrue("the rail comes back", shown(NavigationTestTags.RAIL))
            assertTrue("and the status bar", waitFor { scenario.statusBarShown() })

            // Another tab keeps the request: the screen doesn't turn on a tab switch.
            (device.wait(Until.findObject(By.res(NavigationTestTags.tab(Destination.METRONOME))), WAIT_MS) ?: error("no Metronome tab")).click()
            assertTrue("the metronome shows", device.wait(Until.gone(By.res(TunerTestTags.GAUGE)), WAIT_MS))
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, scenario.requested())
            assertTrue("still on its side", scenario.onItsSide())
            // With its rail and the status bar, after the controls' time is up too.
            SystemClock.sleep(CONTROLS_GONE_MS)
            assertTrue("the metronome has the rail", shown(NavigationTestTags.RAIL))
            assertTrue("and the status bar", scenario.statusBarShown())

            // The gear at the rail's top opens Settings (Phase 69), still on its side, without the rail.
            (device.wait(Until.findObject(By.res(NavigationTestTags.SETTINGS)), WAIT_MS) ?: error("no gear")).click()
            assertTrue("Settings shows, without the rail", device.wait(Until.gone(By.res(NavigationTestTags.RAIL)), WAIT_MS))
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, scenario.requested())
            assertTrue("Settings still on its side", scenario.onItsSide())
            assertTrue("and the status bar", scenario.statusBarShown())

            focusMode.value = false
            assertTrue("off, the request is let go", waitFor { scenario.requested() == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED })
            // With auto-rotate off (as the test run holds the phone) it is first turned back upright, as it was.
            if (!autoRotate()) assertTrue("back upright", waitFor { !scenario.onItsSide() })
            SystemClock.sleep(SETTLE_MS)
            assertTrue("edge to edge after focus mode", scenario.edgeToEdge())
        }
    }

    /**
     * A large screen is let go at once when focus mode goes off: asked for upright, the Lenovo Tab M11
     * (Android 15) gave a letterbox wherever its rotation lock was on its side, and leaving it left a
     * grey band behind the status bar on every tab until the tablet turned. The lock is put on its side
     * during focus mode here, as UI Automator's freeze (and its `uiautomator dump`) does.
     */
    @Test
    fun aLargeScreenIsLetGoAtOnceWithNothingOverTheStatusBar() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assumeTrue("a large screen", scenario.largeScreen())
            focusMode.value = true
            assertTrue("focus mode turns it on its side", waitFor { scenario.onItsSide() })
            device.freezeRotation()

            focusMode.value = false
            var askedUpright = false
            assertTrue("off, the request is let go", waitFor {
                if (scenario.requested() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) askedUpright = true
                scenario.requested() == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            })
            SystemClock.sleep(SETTLE_MS)
            assertFalse("never asked for upright", askedUpright)
            assertTrue("edge to edge after focus mode", scenario.edgeToEdge())
        }
    }

    /**
     * The tuner's full-screen button turns focus mode on; the button among the controls a tap brings
     * up goes back to full screen; the exit button in the corner, and Back, switch it off, and the
     * app keeps running.
     */
    @Test
    fun theTunersButtonsAndBackTurnFocusModeOnAndOff() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            (device.wait(Until.findObject(By.res(TunerTestTags.ENTER_FOCUS)), WAIT_MS) ?: error("no full-screen button")).click()
            assertTrue("the button switches focus mode on", waitFor { focusMode.value })
            assertTrue("and the screen turns", waitFor { scenario.onItsSide() })
            assertTrue("the full-screen tuner shows", shown(TunerTestTags.COMPACT))
            // A tap brings the controls; their button takes them away again, still in focus mode.
            (device.findObject(By.res(TunerTestTags.COMPACT)) ?: error("no full-screen tuner")).click()
            (device.wait(Until.findObject(By.res(TunerTestTags.HIDE_CONTROLS)), WAIT_MS) ?: error("no full-screen button among the controls")).click()
            assertTrue("the controls go", device.wait(Until.gone(By.res(TunerTestTags.HIDE_CONTROLS)), WAIT_MS))
            assertTrue("still in focus mode", focusMode.value)
            // The exit button in the full-screen tuner's corner.
            (device.wait(Until.findObject(By.res(TunerTestTags.EXIT_FOCUS)), WAIT_MS) ?: error("no exit button")).click()
            assertTrue("the button switches focus mode off", waitFor { !focusMode.value })
            assertTrue("off, the request is let go", waitFor { scenario.requested() == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED })

            focusMode.value = true
            assertTrue("the full-screen tuner shows again", shown(TunerTestTags.COMPACT))
            device.pressBack()
            assertTrue("Back switches focus mode off", waitFor { !focusMode.value })
            assertTrue("and the app stays open", shown(NavigationTestTags.RAIL) || shown(NavigationTestTags.BAR))
        }
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 40L
        const val WAIT_MS = 5_000L

        /** Long enough for a letterbox to come and go after focus mode, had there been one. */
        const val SETTLE_MS = 2_000L

        const val LARGE_SCREEN_DP = 600

        /** Longer than the tuner's controls stay shown after a tap (5 s), had they stayed in charge. */
        const val CONTROLS_GONE_MS = 6_000L
    }
}
