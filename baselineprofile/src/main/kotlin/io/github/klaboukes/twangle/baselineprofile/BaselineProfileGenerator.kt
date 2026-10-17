package io.github.klaboukes.twangle.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which classes and methods the app uses at startup, on the tuner screen with its tools, in
 * the Practice tab and in the settings and About, so Android can compile them ahead of time on
 * install instead of interpreting them on first use.
 *
 * Run on demand with a phone connected: `./gradlew :app:generateBaselineProfile`. The result is
 * written to `app/src/release/generated/baselineProfiles/` and committed.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    /** Cold start up to the tuner. Also becomes the startup profile, which orders the app's code. */
    @Test
    fun startup() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        grantMicPermission()
        pressHome()
        startActivityAndWait()
        waitForTuner()
    }

    /** Listening, switching modes and trying each of the tuner's tools. */
    @Test
    fun tunerJourney() = rule.collect(packageName = TARGET_PACKAGE) {
        startAtTuner()
        useTuner()
    }

    /** The Practice tab: the metronome, Chords (chords and scales) and the Capo helper. */
    @Test
    fun practiceJourney() = rule.collect(packageName = TARGET_PACKAGE) {
        startAtTuner()
        usePractice()
    }

    /** The settings, scrolled through, and About. */
    @Test
    fun settingsJourney() = rule.collect(packageName = TARGET_PACKAGE) {
        startAtTuner()
        useSettings()
    }

    private fun MacrobenchmarkScope.startAtTuner() {
        grantMicPermission()
        pressHome()
        startActivityAndWait()
    }
}
