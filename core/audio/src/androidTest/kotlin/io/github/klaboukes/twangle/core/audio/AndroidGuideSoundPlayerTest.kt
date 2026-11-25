package io.github.klaboukes.twangle.core.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.dsp.guide.GuidePulse
import io.github.klaboukes.twangle.core.dsp.guide.GuideSounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The sound guide's player (Phase 94) on a real speaker (at media volume, a few short soft sounds): [isSounding][GuideSoundPlayer.isSounding]
 * covers each sound from [play][GuideSoundPlayer.play] until it has been played, which the tuner's set-aside relies on.
 */
@RunWith(AndroidJUnit4::class)
class AndroidGuideSoundPlayerTest {

    /** A screen in the foreground, as for the reference tone's test. */
    @get:Rule
    val foreground = ActivityScenarioRule(ForegroundActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val player = AndroidGuideSoundPlayer(context)

    @Test
    fun aStepSoundsFromPlayUntilItHasBeenPlayed() = runBlocking {
        for (pulse in listOf(GuidePulse.LOOSEN, GuidePulse.TIGHTEN)) {
            val started = SystemClock.uptimeMillis()
            player.play(pulse, GuideSounds.SHORT_IN_TUNE_SECONDS)
            assertTrue("$pulse: sounding at once", player.isSounding.value)
            withTimeout(SLACK_MILLIS) { player.isSounding.first { !it } }
            val took = SystemClock.uptimeMillis() - started
            assertTrue("$pulse ended after $took ms, before its 145 ms could be played", took >= STEP_MILLIS)
            delay(300)
        }
    }

    @Test
    fun theBellRingsForItsLength() = runBlocking {
        val started = SystemClock.uptimeMillis()
        player.play(GuidePulse.IN_TUNE, 1f)
        withTimeout(1_000L + SLACK_MILLIS) { player.isSounding.first { !it } }
        val took = SystemClock.uptimeMillis() - started
        assertTrue("the 1 s bell ended after $took ms", took >= 1_000L)
    }

    @Test
    fun writesOnAThreadOfItsOwnAtAudioPriority() = runBlocking {
        player.play(GuidePulse.IN_TUNE, 1f)
        delay(200)
        val priorities = audioThreadPriorities("GuideSoundPlayer")
        assertTrue("no GuideSoundPlayer thread", priorities.isNotEmpty())
        for (priority in priorities) assertEquals(android.os.Process.THREAD_PRIORITY_AUDIO, priority)
        player.stop()
        withTimeout(SLACK_MILLIS) { player.isSounding.first { !it } }
        Unit
    }

    @Test
    fun stopEndsTheBellQuickly() = runBlocking {
        player.play(GuidePulse.IN_TUNE, 2f)
        delay(300)
        assertTrue(player.isSounding.value)
        val stopped = SystemClock.uptimeMillis()
        player.stop()
        withTimeout(SLACK_MILLIS) { player.isSounding.first { !it } }
        assertTrue(SystemClock.uptimeMillis() - stopped < 1_000L)
    }

    /** Its sounds come up to three times a second: another app's music must not pause and resume with each. */
    @Test
    fun takesNoAudioFocus() = runBlocking {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val changes = CopyOnWriteArrayList<Int>()
        val music = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener({ changes += it }, Handler(Looper.getMainLooper()))
            .build()
        try {
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audioManager.requestAudioFocus(music))
            player.play(GuidePulse.TIGHTEN, GuideSounds.SHORT_IN_TUNE_SECONDS)
            withTimeout(SLACK_MILLIS) { player.isSounding.first { !it } }
            delay(200)
            assertEquals("the other app kept its focus", emptyList<Int>(), changes.toList())
        } finally {
            audioManager.abandonAudioFocusRequest(music)
        }
    }

    @Test
    fun stopWithNothingPlayingDoesNothing() {
        player.stop()
        assertFalse(player.isSounding.value)
    }

    private companion object {
        const val SLACK_MILLIS = 1_500L
        const val STEP_MILLIS = 145L
    }
}
