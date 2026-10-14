package io.github.klaboukes.twangle.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** The app under test (`:app`). */
const val TARGET_PACKAGE = "io.github.klaboukes.twangle"

// Test tags from `TunerTestTags` in :feature:tuner and `NavigationTestTags` in :app. UI Automator sees
// them as resource ids, which stay the same in every app language.
private const val SETTINGS_TAB_TAG = "nav_settings"
private const val MODE_CHROMATIC_TAG = "tuner_mode_chromatic"
private const val MODE_GUITAR_TAG = "tuner_mode_auto"

/** "Skip tour" in the first-start tour's balloon (`TourTestTags` in :feature:tuner), which a fresh install shows. */
const val TOUR_SKIP_TAG = "tour_skip"

private const val TIMEOUT_MS = 10_000L

/** How long to let the tuner listen, so the mic, the DSP loop and the gauge drawing all run. */
private const val LISTEN_MS = 3_000L

/**
 * Grants the microphone up front, so the tuner opens straight away instead of the permission
 * prompt. Installing the app for a run doesn't keep earlier grants.
 */
fun MacrobenchmarkScope.grantMicPermission() {
    device.executeShellCommand("pm grant $packageName android.permission.RECORD_AUDIO")
}

/**
 * Waits until the tuner screen is shown (its mode switch is a stable landmark on an upright phone),
 * skipping the first-start tour that a fresh install shows over it.
 */
fun MacrobenchmarkScope.waitForTuner() {
    val tunerOrTour = By.res(Pattern.compile("$MODE_CHROMATIC_TAG|$TOUR_SKIP_TAG"))
    check(device.wait(Until.hasObject(tunerOrTour), TIMEOUT_MS)) { "Tuner screen didn't appear" }
    device.findObject(By.res(TOUR_SKIP_TAG))?.click()
    check(device.wait(Until.hasObject(By.res(MODE_CHROMATIC_TAG)), TIMEOUT_MS)) { "Tuner screen didn't appear" }
}

/**
 * What a user does after the tuner opens: listens, switches to chromatic and back, opens the
 * settings and returns. A short tone burst near the phone during the run also covers the "note
 * detected" readout; the profile is still useful without one.
 */
fun MacrobenchmarkScope.useTuner() {
    waitForTuner()
    Thread.sleep(LISTEN_MS)

    clickTag(MODE_CHROMATIC_TAG)
    Thread.sleep(LISTEN_MS)
    clickTag(MODE_GUITAR_TAG)
    Thread.sleep(LISTEN_MS)

    clickTag(SETTINGS_TAB_TAG)
    check(device.wait(Until.gone(By.res(MODE_CHROMATIC_TAG)), TIMEOUT_MS)) { "Settings didn't open" }
    device.waitForIdle()
    device.pressBack()
    waitForTuner()
    Thread.sleep(LISTEN_MS)
}

private fun MacrobenchmarkScope.clickTag(tag: String) {
    val node = device.wait(Until.findObject(By.res(tag)), TIMEOUT_MS) ?: error("No element tagged $tag")
    node.click()
}
