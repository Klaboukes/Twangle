package com.intune.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which classes and methods the app uses at startup and on the tuner screen, so Android can
 * compile them ahead of time on install instead of interpreting them on first use.
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

    /** Listening, switching modes and visiting the settings. */
    @Test
    fun tunerJourney() = rule.collect(packageName = TARGET_PACKAGE) {
        grantMicPermission()
        pressHome()
        startActivityAndWait()
        useTuner()
    }
}
