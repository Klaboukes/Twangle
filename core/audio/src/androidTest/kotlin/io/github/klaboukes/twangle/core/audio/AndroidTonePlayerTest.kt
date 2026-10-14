package io.github.klaboukes.twangle.core.audio

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.klaboukes.twangle.core.dsp.synth.PluckSynth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Plays real tones through the speaker (at media volume), so it needs a device. */
@RunWith(AndroidJUnit4::class)
class AndroidTonePlayerTest {

    private val player = AndroidTonePlayer()
    private val toneMillis = (PluckSynth.DEFAULT_DURATION_SECONDS * 1000).toLong()

    @Test
    fun playsForTheWholeToneAndStopsByItself() = runBlocking {
        val started = SystemClock.uptimeMillis()
        player.play(440f)
        assertTrue(player.isPlaying.value)

        withTimeout(toneMillis + SLACK_MILLIS) { player.isPlaying.first { !it } }
        val took = SystemClock.uptimeMillis() - started
        assertTrue("stopped after $took ms, before the tone could end", took >= toneMillis)
    }

    @Test
    fun stopEndsTheToneQuickly() = runBlocking {
        player.play(329.63f)
        delay(300)
        assertTrue(player.isPlaying.value)
        val stopped = SystemClock.uptimeMillis()
        player.stop()

        withTimeout(SLACK_MILLIS) { player.isPlaying.first { !it } }
        assertTrue(SystemClock.uptimeMillis() - stopped < SLACK_MILLIS)
    }

    @Test
    fun aNewNoteReplacesTheOneSoundingAndThePlayerCanBeReused() = runBlocking {
        player.play(110f)
        delay(200)
        val switched = SystemClock.uptimeMillis()
        player.play(146.83f)
        delay(200)
        assertTrue(player.isPlaying.value)
        player.stop()
        withTimeout(SLACK_MILLIS) { player.isPlaying.first { !it } }
        assertTrue("the second note must not have ended by itself", SystemClock.uptimeMillis() - switched < toneMillis)

        // Released properly, so it plays again.
        player.play(196f)
        assertTrue(player.isPlaying.value)
        player.stop()
        withTimeout(SLACK_MILLIS) { player.isPlaying.first { !it } }
        assertFalse(player.isPlaying.value)
    }

    @Test
    fun stopWithNothingPlayingDoesNothing() {
        player.stop()
        assertFalse(player.isPlaying.value)
    }

    private companion object {
        const val SLACK_MILLIS = 1_500L
    }
}
