package io.github.klaboukes.twangle.app.tour

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.klaboukes.twangle.app.FocusModeOffRule
import io.github.klaboukes.twangle.app.MainActivity
import io.github.klaboukes.twangle.app.TwangleApplication
import io.github.klaboukes.twangle.app.navigation.Destination
import io.github.klaboukes.twangle.app.navigation.NavigationTestTags
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.settings.SHOW_TOUR_TAG
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import io.github.klaboukes.twangle.feature.tuner.tour.TourTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/**
 * The tour in the real app, with its saved settings, as UI Automator (and TalkBack) sees it: due, it
 * shows on the tuner and hides the tuner; Back ends it for good (also after the app starts again)
 * and leaves the app open; "Show me around" in Settings opens the tuner and starts it again, and Skip
 * tour ends that. It leaves the tour seen. (A Compose test rule can't start `MainActivity`: its test
 * clock holds the opening screen.)
 */
class TourFlowTest {

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build())
        .around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO))
        .around(FocusModeOffRule())

    private val repository = ApplicationProvider.getApplicationContext<TwangleApplication>().container.settingsRepository
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun shown(tag: String): Boolean = device.wait(Until.hasObject(By.res(tag)), WAIT_MS)
    private fun gone(tag: String): Boolean = device.wait(Until.gone(By.res(tag)), WAIT_MS)
    private fun click(tag: String) = (device.wait(Until.findObject(By.res(tag)), WAIT_MS) ?: error("no $tag")).click()
    private val saved get() = runBlocking { repository.settings.first() }

    @Test
    fun backEndsTheTourForGoodAndShowMeAroundStartsItAgain() {
        runBlocking { repository.setTourSeen(false) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue("the tour shows", shown(TourTestTags.BALLOON))
            assertFalse("the tuner below is hidden from TalkBack", device.hasObject(By.res(TUNER_SHOWN)))

            // Back ends the tour, not the app.
            device.pressBack()
            assertTrue("Back ends it", gone(TourTestTags.BALLOON))
            assertTrue("the tuner is there again", shown(TUNER_SHOWN))
            assertTrue("seen for good", waitUntil { saved.tourSeen })

            // It stays gone when the app starts again.
            scenario.recreate()
            assertTrue(shown(TUNER_SHOWN))
            assertFalse("not again", shown(TourTestTags.BALLOON))

            // Settings → Show me around: the tuner, with the tour from its first step.
            click(NavigationTestTags.tab(Destination.SETTINGS))
            assertTrue("Settings opened", gone(TUNER_SHOWN))
            val row = device.findObject(By.res(SHOW_TOUR_TAG))
                ?: device.wait(Until.findObject(By.scrollable(true)), WAIT_MS)?.scrollUntil(Direction.DOWN, Until.findObject(By.res(SHOW_TOUR_TAG)))
                ?: error("no Show me around")
            row.click()
            assertTrue("the tour starts again", shown(TourTestTags.BALLOON))
            click(TourTestTags.NEXT)
            click(TourTestTags.SKIP)
            assertTrue("Skip tour ends it", gone(TourTestTags.BALLOON))
            assertTrue(shown(TUNER_SHOWN))
            assertTrue(saved.tourSeen)
        }
    }

    private fun waitUntil(condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 40L
        const val WAIT_MS = 5_000L

        /** A part of the tuner screen itself, so its being found means the tuner shows (and TalkBack reaches it). */
        val TUNER_SHOWN = TunerTestTags.mode(TunerMode.CHROMATIC)
    }
}
