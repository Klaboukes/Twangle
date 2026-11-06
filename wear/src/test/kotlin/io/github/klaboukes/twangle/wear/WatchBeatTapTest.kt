package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.audio.FakeMetronomePlayer
import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class WatchBeatTapTest {

    @Test
    fun `a plain beat taps as long as the tuner, the downbeat longer`() {
        for (bpm in listOf(0, 30, 120, 171, 250)) {
            assertEquals(TAP_MILLIS, beatTapMillis(BeatAccent.PLAIN, bpm))
            assertEquals(TAP_MILLIS, beatTapMillis(BeatAccent.GROUP, bpm))
        }
        assertEquals(DOWNBEAT_TAP_MILLIS, beatTapMillis(BeatAccent.DOWNBEAT, 0))
        assertEquals(DOWNBEAT_TAP_MILLIS, beatTapMillis(BeatAccent.DOWNBEAT, 30))
        assertEquals(DOWNBEAT_TAP_MILLIS, beatTapMillis(BeatAccent.DOWNBEAT, 120))
        assertEquals(DOWNBEAT_TAP_MILLIS, beatTapMillis(BeatAccent.DOWNBEAT, 171))
    }

    @Test
    fun `at a fast tempo the downbeat shortens to leave a gap, never below a plain tap`() {
        for (bpm in 30..250) {
            val millis = beatTapMillis(BeatAccent.DOWNBEAT, bpm)
            assertTrue("$bpm BPM: $millis ms", millis in TAP_MILLIS..DOWNBEAT_TAP_MILLIS)
            if (millis > TAP_MILLIS) assertTrue("$bpm BPM: no gap", 60_000 / bpm - millis >= TAP_GAP_MILLIS)
        }
        assertEquals(200L, beatTapMillis(BeatAccent.DOWNBEAT, 200))
        assertEquals(TAP_MILLIS, beatTapMillis(BeatAccent.DOWNBEAT, 240))
        assertEquals(TAP_MILLIS, beatTapMillis(BeatAccent.DOWNBEAT, 250))
    }

    @Test
    fun `taps every beat at its moment less the lead, the downbeat's longer`() = runBlocking {
        val player = FakeMetronomePlayer()
        val taps = Collections.synchronizedList(mutableListOf<Tap>())
        val following = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            WatchBeatTaps(leadNanos = LEAD) { beat, millis -> taps += Tap(beat.index, millis, System.nanoTime()) }
                .follow(player.upcomingBeats) { true }
        }
        player.start(120, Meter.FOUR_FOUR)
        val start = System.nanoTime() + 50_000_000L
        val heardAt = (0L until 6L).map { start + it * 30_000_000L }
        for (i in 0L until 6L) player.beat(i, heardAt[i.toInt()])
        withTimeout(2_000) { while (taps.size < 6) delay(10) }
        following.cancel()

        assertEquals((0L until 6L).toList(), taps.map { it.index })
        assertEquals(listOf(250L, 150L, 150L, 150L, 250L, 150L), taps.map { it.millis })
        for (tap in taps) {
            assertTrue("beat ${tap.index} tapped early", tap.atNanos >= heardAt[tap.index.toInt()] - LEAD)
        }
    }

    @Test
    fun `a beat whose moment comes once stopped is not tapped`() = runBlocking {
        val player = FakeMetronomePlayer()
        val taps = AtomicInteger()
        val following = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            WatchBeatTaps(leadNanos = 0) { _, _ -> taps.incrementAndGet() }.follow(player.upcomingBeats) { player.isPlaying.value }
        }
        player.start(120, Meter.FOUR_FOUR)
        player.beat(0, System.nanoTime() + 40_000_000L)
        player.stop()
        delay(150)
        following.cancel()

        assertEquals(0, taps.get())
    }

    private data class Tap(val index: Long, val millis: Long, val atNanos: Long)

    private companion object {
        const val LEAD = 10_000_000L
    }
}
