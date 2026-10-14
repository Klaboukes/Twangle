package io.github.klaboukes.twangle.core.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Plays real clicks through the speaker (at media volume), so it needs a device. */
@RunWith(AndroidJUnit4::class)
class AndroidMetronomePlayerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val player = AndroidMetronomePlayer(context)

    private data class Heard(val beat: MetronomeBeat, val emittedAtNanos: Long)

    @After
    fun stopPlayer() = runBlocking<Unit> {
        player.stop()
        withTimeout(SLACK_MILLIS) { player.isPlaying.first { !it } }
    }

    @Test
    fun clicksOnTheBeatWithTheFirstOfEachBarAccented() = runBlocking<Unit> {
        val heard = async(start = CoroutineStart.UNDISPATCHED) {
            player.beats.map { Heard(it, System.nanoTime()) }.take(10).toList()
        }
        player.start(240, Meter.THREE_FOUR)
        assertTrue(player.isPlaying.value)
        val beats = withTimeout(10 * 250L + SLACK_MILLIS) { heard.await() }

        assertEquals((0L until 10L).toList(), beats.map { it.beat.index })
        assertEquals((0 until 10).map { it % 3 }, beats.map { it.beat.beatInBar })
        assertEquals(
            (0 until 10).map { if (it % 3 == 0) BeatAccent.DOWNBEAT else BeatAccent.PLAIN },
            beats.map { it.beat.accent },
        )
        // Once the output's timestamps run, the beats are heard exactly 250 ms apart and told on time.
        val steady = beats.drop(3)
        for ((a, b) in steady.zipWithNext()) {
            val gapMillis = (b.beat.heardAtNanos - a.beat.heardAtNanos) / 1e6
            assertEquals("beats ${a.beat.index} to ${b.beat.index}", 250.0, gapMillis, 2.0)
        }
        for (h in steady) {
            val lateMillis = (h.emittedAtNanos - h.beat.heardAtNanos) / 1e6
            assertTrue("beat ${h.beat.index} told $lateMillis ms from its sound", abs(lateMillis) < 20)
        }
        val gapErrors = steady.zipWithNext { a, b -> (b.beat.heardAtNanos - a.beat.heardAtNanos) / 1e6 - 250.0 }
        val late = steady.map { (it.emittedAtNanos - it.beat.heardAtNanos) / 1e6 }
        Log.i(
            "MetronomeTest",
            "rate ${player.sampleRate} Hz; gap error %.3f..%.3f ms; told %.1f..%.1f ms after the sound"
                .format(gapErrors.min(), gapErrors.max(), late.min(), late.max()),
        )
    }

    @Test
    fun aNewTempoAndMeterTakeOverWhilePlaying() = runBlocking<Unit> {
        val heard = async(start = CoroutineStart.UNDISPATCHED) { player.beats.take(12).toList() }
        player.start(120, Meter.FOUR_FOUR)
        player.beats.first { it.index == 2L }
        player.setTempo(250)
        player.setMeter(Meter.SEVEN_EIGHT)
        val beats = withTimeout(4_000L + SLACK_MILLIS) { heard.await() }

        // 0.24 s apart at the end, and a 7/8 bar (downbeat, then group accents on 3 and 5) underway.
        val gapMillis = (beats[11].heardAtNanos - beats[10].heardAtNanos) / 1e6
        assertEquals(240.0, gapMillis, 2.0)
        val downbeat = beats.indexOfFirst { it.beatInBar == 0 && it.index > 2 }
        assertTrue("no 7/8 bar started", downbeat in 3..7)
        val bar = beats.drop(downbeat).take(5).map { it.accent }
        assertEquals(listOf(BeatAccent.DOWNBEAT, BeatAccent.PLAIN, BeatAccent.GROUP, BeatAccent.PLAIN, BeatAccent.GROUP).take(bar.size), bar)
    }

    @Test
    fun stopEndsTheClicksQuicklyAndThePlayerCanBeReused() = runBlocking<Unit> {
        player.start(200, Meter.FOUR_FOUR)
        player.beats.first { it.index == 1L }
        player.stop()
        withTimeout(SLACK_MILLIS) { player.isPlaying.first { !it } }
        assertNull("a beat after stop", withTimeoutOrNull(800) { player.beats.first() })

        player.start(200, Meter.TWO_FOUR)
        val first = withTimeout(SLACK_MILLIS) { player.beats.first() }
        assertEquals(0L, first.index)
        assertEquals(BeatAccent.DOWNBEAT, first.accent)
    }

    /** Another app taking audio focus for good (here: the test, as another focus owner) stops the clicks. */
    @Test
    fun losingAudioFocusStopsTheClicks() = runBlocking<Unit> {
        player.start(120, Meter.FOUR_FOUR)
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
        } finally {
            audioManager.abandonAudioFocusRequest(other)
        }
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
