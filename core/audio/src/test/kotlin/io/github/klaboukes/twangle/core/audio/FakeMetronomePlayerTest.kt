package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeMetronomePlayerTest {

    private val player = FakeMetronomePlayer()

    @Test
    fun `follows start, tempo, meter and stop like the real player`() {
        assertFalse(player.isPlaying.value)
        player.setTempo(90) // ignored while stopped
        assertEquals(120, player.bpm)

        player.start(300, Meter.SIX_EIGHT)
        assertTrue(player.isPlaying.value)
        assertEquals(250, player.bpm)
        assertEquals(Meter.SIX_EIGHT, player.meter)

        player.setTempo(10)
        player.setMeter(Meter.THREE_FOUR)
        assertEquals(30, player.bpm)
        assertEquals(Meter.THREE_FOUR, player.meter)

        player.stop()
        assertFalse(player.isPlaying.value)
        assertEquals(1, player.startCount)
        assertEquals(1, player.stopCount)
    }

    @Test
    fun `emits beats with the meter's accents`() = runTest(UnconfinedTestDispatcher()) {
        player.start(120, Meter.SEVEN_EIGHT)
        val heard = mutableListOf<MetronomeBeat>()
        val job = launch { player.beats.take(7).toList(heard) }
        for (i in 0L until 7L) player.beat(i, heardAtNanos = i * 1_000)
        job.join()
        assertEquals((0 until 7).toList(), heard.map { it.beatInBar })
        assertEquals(BeatAccent.DOWNBEAT, heard[0].accent)
        assertEquals(listOf(2, 4), heard.withIndex().filter { it.value.accent == BeatAccent.GROUP }.map { it.index })
        assertEquals(6_000L, heard[6].heardAtNanos)
    }
}
