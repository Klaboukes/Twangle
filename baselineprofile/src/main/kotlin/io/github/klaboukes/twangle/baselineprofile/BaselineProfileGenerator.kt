package io.github.klaboukes.twangle.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which classes and methods the app uses at startup and on 2.0's screens (the tuner with its
 * picker sheet and focus mode, the Metronome tab, the Practice cards and their tools, Settings and
 * About), so Android can compile them ahead of time on install instead of interpreting them on first use.
 *
 * Run on demand with a phone connected and held upright (docs/architecture/performance.md, *Baseline
 * Profile*): `./gradlew :app:generateBaselineProfile` with the class filter. The result is written to
 * `app/src/release/generated/baselineProfiles/` and committed.
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

    /** Listening, Any note on and off through the picker sheet, and focus mode on and off. */
    @Test
    fun tunerJourney() = rule.collect(packageName = TARGET_PACKAGE) {
        startAtTuner()
        useTuner()
    }

    /** The Metronome tab (its ruler, Start, tap tempo), then the Practice cards: Chords, Scales, Capo and Ear training. */
    @Test
    fun practiceJourney() = rule.collect(packageName = TARGET_PACKAGE) {
        startAtTuner()
        usePractice()
    }

    /** The gear to Settings, scrolled through, and About. */
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
