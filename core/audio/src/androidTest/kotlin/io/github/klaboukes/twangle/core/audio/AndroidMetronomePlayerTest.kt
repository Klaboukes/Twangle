package io.github.klaboukes.twangle.core.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
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
        withTimeout(SLACK_MILLIS) { player.isPreviewing.first { !it } }
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
    fun aNewSoundIsHeardFromTheNextBeat() = runBlocking<Unit> {
        val heard = async(start = CoroutineStart.UNDISPATCHED) { player.beats.take(10).toList() }
        player.start(240, Meter.FOUR_FOUR, ClickSound.CLAVES)
        player.beats.first { it.index == 2L }
        player.setSound(ClickSound.COWBELL)
        val beats = withTimeout(10 * 250L + SLACK_MILLIS) { heard.await() }

        val sounds = beats.map { it.sound }
        assertEquals("starts with the sound asked for", ClickSound.CLAVES, sounds.first())
        // The change comes within the queued buffer (≈ 0.12 s, under a beat), so by beat 4 at the latest.
        val first = sounds.indexOf(ClickSound.COWBELL)
        assertTrue("the cowbell came at beat $first", first in 3..4)
        assertTrue("one change, no going back: $sounds", sounds.drop(first).all { it == ClickSound.COWBELL })
        assertTrue(sounds.take(first).all { it == ClickSound.CLAVES })
    }

    @Test
    fun aPreviewPlaysOneBarOnceAndTellsNoBeats() = runBlocking<Unit> {
        val anyBeat = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(1_000L + SLACK_MILLIS) { player.beats.first() } }
        val startedAt = System.nanoTime()
        player.previewBar(240, Meter.FOUR_FOUR, ClickSound.RIM_CLICK) // one bar: 1 s
        assertTrue(player.isPreviewing.value)
        assertFalse("a preview isn't the metronome playing", player.isPlaying.value)
        player.setTempo(60) // ignored: the preview keeps its bar
        withTimeout(1_000L + SLACK_MILLIS) { player.isPreviewing.first { !it } }
        val millis = (System.nanoTime() - startedAt) / 1e6
        assertTrue("the preview ended after $millis ms", millis >= 1_000)
        assertFalse(player.isPlaying.value)
        assertNull("a preview's clicks aren't told as beats", anyBeat.await())
    }

    @Test
    fun aNewPreviewStartsOverAndStartOrStopEndsIt() = runBlocking<Unit> {
        player.previewBar(120, Meter.TWO_FOUR, ClickSound.COWBELL) // one bar: 1 s
        delay(600)
        player.previewBar(120, Meter.TWO_FOUR, ClickSound.CLAVES)
        delay(700) // the first bar would be over by now; the second plays on until about 1.6 s
        assertTrue("the second preview still plays", player.isPreviewing.value)
        withTimeout(1_000L + SLACK_MILLIS) { player.isPreviewing.first { !it } }

        val first = async(start = CoroutineStart.UNDISPATCHED) { player.beats.first() }
        player.previewBar(120, Meter.TWO_FOUR, ClickSound.COWBELL)
        delay(300)
        player.start(200, Meter.THREE_FOUR, ClickSound.WOOD_BLOCK)
        assertFalse("Start ends the preview", player.isPreviewing.value)
        assertTrue(player.isPlaying.value)
        val beat = withTimeout(SLACK_MILLIS) { first.await() }
        assertEquals("the metronome starts at beat 0", 0L, beat.index)
        assertEquals(BeatAccent.DOWNBEAT, beat.accent)
        assertEquals(ClickSound.WOOD_BLOCK, beat.sound)
        player.stop()
        withTimeout(SLACK_MILLIS) { player.isPlaying.first { !it } }

        player.previewBar(60, Meter.FOUR_FOUR, ClickSound.CLAVES) // one bar: 4 s
        delay(200)
        player.stop()
        withTimeout(500) { player.isPreviewing.first { !it } }
    }

    @Test
    fun aPreviewWhilePlayingOnlySwitchesTheSound() = runBlocking<Unit> {
        player.start(240, Meter.FOUR_FOUR)
        player.beats.first { it.index == 1L }
        player.previewBar(60, Meter.TWO_FOUR, ClickSound.RIM_CLICK)
        assertFalse(player.isPreviewing.value)
        assertTrue(player.isPlaying.value)
        val later = withTimeout(SLACK_MILLIS) { player.beats.first { it.index == 5L } }
        assertEquals(ClickSound.RIM_CLICK, later.sound)
        assertEquals("the meter stays 4/4", 1, later.beatInBar)
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

    /**
     * The clicks play over other apps' music: another app taking audio focus for good (here: the
     * test, as another focus owner, like a music app starting a backing track) doesn't stop them.
     */
    @Test
    fun anotherAppTakingAudioFocusLeavesTheClicksPlaying() = runBlocking<Unit> {
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
            assertNull("still playing", withTimeoutOrNull(SLACK_MILLIS) { player.isPlaying.first { !it } })
        } finally {
            audioManager.abandonAudioFocusRequest(other)
        }
        assertTrue(player.isPlaying.value)
    }

    @Test
    fun upcomingBeatsAreToldAheadOfTheirSound() = runBlocking<Unit> {
        val upcoming = async(start = CoroutineStart.UNDISPATCHED) {
            player.upcomingBeats.map { Heard(it, System.nanoTime()) }.take(8).toList()
        }
        val heard = async(start = CoroutineStart.UNDISPATCHED) { player.beats.take(8).toList() }
        player.start(240, Meter.FOUR_FOUR)
        val early = withTimeout(8 * 250L + SLACK_MILLIS) { upcoming.await() }
        val beats = withTimeout(SLACK_MILLIS) { heard.await() }

        assertEquals(beats, early.map { it.beat })
        // Once the output's timestamps run, each is told well before it is heard.
        for (h in early.drop(3)) {
            val aheadMillis = (h.beat.heardAtNanos - h.emittedAtNanos) / 1e6
            assertTrue("beat ${h.beat.index} told $aheadMillis ms ahead", aheadMillis > 20)
        }
    }

    @Test
    fun silentClicksKeepTheBeatsOnTimeEitherWay() = runBlocking<Unit> {
        player.setSilent(true)
        val heard = async(start = CoroutineStart.UNDISPATCHED) { player.beats.take(12).toList() }
        player.start(240, Meter.FOUR_FOUR)
        player.beats.first { it.index == 5L }
        player.setSilent(false) // heard again mid-play: the clock doesn't move
        val beats = try {
            withTimeout(12 * 250L + SLACK_MILLIS) { heard.await() }
        } finally {
            player.setSilent(false)
        }

        assertEquals((0L until 12L).toList(), beats.map { it.index })
        for ((a, b) in beats.drop(3).zipWithNext()) {
            assertEquals("beats ${a.index} to ${b.index}", 250.0, (b.heardAtNanos - a.heardAtNanos) / 1e6, 2.0)
        }
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
