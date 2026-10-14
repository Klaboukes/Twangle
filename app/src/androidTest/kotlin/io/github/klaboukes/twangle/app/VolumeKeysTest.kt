package io.github.klaboukes.twangle.app

import android.Manifest
import android.media.AudioManager
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * While the tuner is shown the volume keys set the media volume, the reference tone's loudness.
 * (Settings, About and the tuning editor hand them back to the phone; checked on the phone, as
 * the always-moving tuner keeps a Compose test rule from ever being idle.)
 */
@RunWith(AndroidJUnit4::class)
class VolumeKeysTest {

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build())
        .around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO))

    @Test
    fun volumeKeysSetTheMediaVolumeOnTheTuner() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals("on the tuner", AudioManager.STREAM_MUSIC, streamAfterStart(scenario))
        }
    }

    /** The activity's volume stream once the tuner has been composed (up to 3 s after launch). */
    private fun streamAfterStart(scenario: ActivityScenario<MainActivity>): Int {
        scenario.moveToState(Lifecycle.State.RESUMED)
        val end = SystemClock.uptimeMillis() + 3_000
        var stream = AudioManager.USE_DEFAULT_STREAM_TYPE
        while (SystemClock.uptimeMillis() < end) {
            scenario.onActivity { stream = it.volumeControlStream }
            if (stream == AudioManager.STREAM_MUSIC) break
            SystemClock.sleep(100)
        }
        return stream
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 20L
    }
}
