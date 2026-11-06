package io.github.klaboukes.twangle.core.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.dsp.drone.DroneSound
import io.github.klaboukes.twangle.core.dsp.drone.DroneVoicing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Plays the real drone through the speaker (at media volume, about a second at a time), so it needs a device. */
@RunWith(AndroidJUnit4::class)
class AndroidDronePlayerTest {

    /** A screen in the foreground: Android 15 (the Lenovo tablet) refuses audio focus to an app without one. */
    @get:Rule
    val foreground = ActivityScenarioRule(ForegroundActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val player = AndroidDronePlayer(context)

    @After
    fun stopPlayer() = runBlocking<Unit> {
        player.stop()
        withTimeout(SLACK_MILLIS) { player.holdsSpeaker.first { !it } }
    }

    @Test
    fun playsUntilStoppedThenFadesOutAndLetsTheSpeakerGo() = runBlocking {
        player.play(D3, DroneSound.TANPURA, DroneVoicing.FIFTH)
        assertTrue(player.isPlaying.value)
        assertTrue(player.holdsSpeaker.value)

        // A drone never ends by itself.
        delay(1_200)
        assertTrue(player.isPlaying.value)
        assertTrue(player.holdsSpeaker.value)

        val stopped = SystemClock.uptimeMillis()
        player.stop()
        assertFalse("the button follows the stop at once", player.isPlaying.value)
        withTimeout(SLACK_MILLIS) { player.holdsSpeaker.first { !it } }
        val took = SystemClock.uptimeMillis() - stopped
        assertTrue("let the speaker go $took ms after the stop, before its fade could be heard", took >= FADE_MILLIS)
        assertEquals("the speaker ran dry", 0, player.lastUnderruns)
    }

    @Test
    fun everyChangeCrossfadesInTheSameTrackWithoutAGap() = runBlocking {
        player.play(D3, DroneSound.TANPURA, DroneVoicing.FIFTH)
        delay(400)
        // Each change crossfades for 0.4 s, which doubles the synth's work: the dearest is the pad,
        // root fifth and octave, an octave down.
        player.play(A2, DroneSound.WARM_PAD, DroneVoicing.FIFTH_OCTAVE, lower = true)
        delay(500)
        player.play(G3, DroneSound.HARMONIUM, DroneVoicing.ROOT)
        delay(500)
        player.play(G3, DroneSound.FLUTE_ORGAN, DroneVoicing.FIFTH_OCTAVE)
        delay(300)
        assertTrue(player.isPlaying.value)
        player.stop()
        withTimeout(SLACK_MILLIS) { player.holdsSpeaker.first { !it } }
        assertEquals("the speaker ran dry", 0, player.lastUnderruns)
    }

    @Test
    fun playDuringTheFadeOutBringsItBackAndThePlayerCanBeReused() = runBlocking {
        player.play(D3)
        delay(300)
        player.stop()
        player.play(D3, DroneSound.HARMONIUM)
        assertTrue(player.isPlaying.value)
        delay(800)
        assertTrue("it came back rather than ending", player.holdsSpeaker.value)
        assertTrue(player.isPlaying.value)
        player.stop()
        withTimeout(SLACK_MILLIS) { player.holdsSpeaker.first { !it } }

        // Let go properly, so it plays again.
        player.play(A2, DroneSound.WARM_PAD)
        assertTrue(player.isPlaying.value)
        delay(300)
        player.stop()
        withTimeout(SLACK_MILLIS) { player.holdsSpeaker.first { !it } }
        assertFalse(player.isPlaying.value)
    }

    /** Another app taking audio focus for good (here: the test, as another focus owner) stops the drone. */
    @Test
    fun losingAudioFocusStopsTheDrone() = runBlocking {
        player.play(D3)
        delay(300)
        assertTrue(player.isPlaying.value)
        val audioManager = context.getSystemService(AudioManager::class.java)
        val other = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener {}
            .build()
        try {
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audioManager.requestAudioFocus(other))
            withTimeout(SLACK_MILLIS) { player.isPlaying.first { !it } }
            withTimeout(SLACK_MILLIS) { player.holdsSpeaker.first { !it } }
        } finally {
            audioManager.abandonAudioFocusRequest(other)
        }
        assertFalse(player.isPlaying.value)
    }

    @Test
    fun stopWithNothingPlayingDoesNothing() {
        player.stop()
        assertFalse(player.isPlaying.value)
        assertFalse(player.holdsSpeaker.value)
    }

    @Test
    fun aRootOutsideTheDronesRangeIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { player.play(20f) }
        assertThrows(IllegalArgumentException::class.java) { player.play(2_000f) }
        assertFalse(player.isPlaying.value)
        assertFalse(player.holdsSpeaker.value)
    }

    private companion object {
        const val SLACK_MILLIS = 2_000L

        /** The stop's fade (0.25 s): the speaker can't be let go sooner. */
        const val FADE_MILLIS = 250L

        // At A4 = 440 Hz.
        const val D3 = 146.83f
        const val A2 = 110f
        const val G3 = 196f
    }
}
