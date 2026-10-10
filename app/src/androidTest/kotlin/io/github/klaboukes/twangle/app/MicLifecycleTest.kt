package io.github.klaboukes.twangle.app

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The microphone is open only while the tuner is visible (CLAUDE.md, hard rule 4), checked with
 * Android's own list of active recordings on a real device.
 */
@RunWith(AndroidJUnit4::class)
class MicLifecycleTest {

    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private val audioManager = InstrumentationRegistry.getInstrumentation().targetContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val recording: Boolean get() = audioManager.activeRecordingConfigurations.isNotEmpty()

    /** Waits up to [timeoutMs] for [condition]; returns whether it came true. */
    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }

    @Test
    fun micFollowsTheScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue("mic opens while the tuner is shown", waitFor(3_000) { recording })

            // In the background (onStop): kept for 5 s so a rotation doesn't reopen it, then released.
            scenario.moveToState(Lifecycle.State.CREATED)
            SystemClock.sleep(3_000)
            assertTrue("still open 3 s after leaving", recording)
            assertTrue("released about 5 s after leaving", waitFor(5_000) { !recording })

            scenario.moveToState(Lifecycle.State.RESUMED)
            assertTrue("reopened on return", waitFor(3_000) { recording })
        }
        assertTrue("released once the app is closed", waitFor(7_000) { !recording })
    }
}
