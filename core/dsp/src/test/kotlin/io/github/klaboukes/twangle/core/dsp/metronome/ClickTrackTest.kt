package io.github.klaboukes.twangle.core.dsp.metronome

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToLong

class ClickTrackTest {

    private data class Beat(val index: Long, val beatInBar: Int, val accent: BeatAccent, val frame: Long)

    private class Recorder : BeatListener {
        val beats = mutableListOf<Beat>()
        override fun onBeat(index: Long, beatInBar: Int, accent: BeatAccent, frame: Long) {
            beats += Beat(index, beatInBar, accent, frame)
        }
    }

    /** Renders [frames] samples in chunks of [chunk] and returns the beats heard. */
    private fun ClickTrack.play(frames: Long, chunk: Int = 480, recorder: Recorder = Recorder()): List<Beat> {
        val buffer = FloatArray(chunk)
        var left = frames
        while (left > 0) {
            val n = minOf(left, chunk.toLong()).toInt()
            render(buffer, 0, n, recorder)
            left -= n
        }
        return recorder.beats
    }

    @Test
    fun `at 120 BPM and 48 kHz a beat lands every 24000 samples from sample 0`() {
        val beats = ClickTrack(48_000, bpm = 120).play(48_000L * 5)
        assertEquals((0L until 10L).map { it * 24_000 }, beats.map { it.frame })
        assertEquals((0L until 10L).toList(), beats.map { it.index })
    }

    @Test
    fun `beats never drift over ten minutes, whatever the tempo, rate or chunk size`() {
        for ((rate, bpm, chunk) in listOf(Triple(44_100, 97, 441), Triple(48_000, 233, 37), Triple(16_000, 31, 4096))) {
            val track = ClickTrack(rate, bpm = bpm)
            val beats = track.play(rate * 600L, chunk)
            val exact = rate * 60.0 / bpm
            assertEquals("beats in ten minutes at $bpm BPM", 10 * bpm, beats.size)
            for (beat in beats) {
                val ideal = beat.index * exact
                assertEquals("beat ${beat.index} at $bpm BPM, $rate Hz", ideal.roundToLong(), beat.frame)
                assertTrue(abs(beat.frame - ideal) <= 0.5)
            }
            assertEquals(rate * 600L, track.framesRendered)
        }
    }

    @Test
    fun `each click starts on its beat's sample and the rest is silence`() {
        val rate = 44_100
        val track = ClickTrack(rate, bpm = 137, meter = Meter.THREE_FOUR)
        val recorder = Recorder()
        val total = rate * 4
        val chunked = FloatArray(total)
        var at = 0
        for (size in generateSequence(1) { it * 3 % 1013 + 1 }) {
            val n = minOf(size, total - at)
            track.render(chunked, at, n, recorder)
            at += n
            if (at == total) break
        }

        val expected = FloatArray(total)
        for (beat in recorder.beats) {
            val click = track.sounds.waveform(beat.accent)
            val start = beat.frame.toInt()
            System.arraycopy(click, 0, expected, start, minOf(click.size, total - start))
        }
        assertEquals(10, recorder.beats.size)
        assertArrayEquals(expected, chunked, 0f)
    }

    @Test
    fun `every sound's clicks play whole at 250 BPM, also when the tempo changes at the worst moment`() {
        val rate = 44_100
        for (sound in ClickSound.entries) {
            val track = ClickTrack(rate, bpm = 250, meter = Meter.SEVEN_EIGHT, sound = sound)
            val recorder = Recorder()
            val total = rate * 3
            val out = FloatArray(total)
            // 250 BPM, then 30 just after a beat, then 250 again 0.4 s later: the next beat comes
            // one new interval (0.24 s) after the last, never sooner.
            val changes = mapOf(rate / 2 to 30, rate / 2 + rate * 2 / 5 to 250)
            var at = 0
            while (at < total) {
                changes[at]?.let { track.bpm = it }
                val n = minOf(441, total - at)
                track.render(out, at, n, recorder)
                at += n
            }
            val frames = recorder.beats.map { it.frame }
            assertTrue("$sound: beats ${frames.zipWithNext { a, b -> b - a }}", frames.zipWithNext().all { (a, b) -> b - a >= rate * 60 / 250 })
            assertTrue(frames.zipWithNext().any { (a, b) -> b - a == (rate * 60 / 250).toLong() })

            val expected = FloatArray(total)
            for (beat in recorder.beats) {
                val click = track.sounds.waveform(sound, beat.accent)
                val start = beat.frame.toInt()
                System.arraycopy(click, 0, expected, start, minOf(click.size, total - start))
            }
            assertArrayEquals("$sound", expected, out, 0f)
        }
    }

    @Test
    fun `a new sound is heard from the next beat, and the click sounding plays out`() {
        val rate = 48_000
        val track = ClickTrack(rate, bpm = 240, meter = Meter.FOUR_FOUR, sound = ClickSound.COWBELL)
        val out = FloatArray(rate)
        track.render(out, 0, rate / 20) // 50 ms into the first cowbell
        track.sound = ClickSound.CLAVES
        track.render(out, rate / 20, rate - rate / 20)

        val expected = FloatArray(rate)
        val cowbell = track.sounds.waveform(ClickSound.COWBELL, BeatAccent.DOWNBEAT)
        System.arraycopy(cowbell, 0, expected, 0, cowbell.size)
        for (beat in 1..3) {
            val claves = track.sounds.waveform(ClickSound.CLAVES, BeatAccent.PLAIN)
            System.arraycopy(claves, 0, expected, beat * 12_000, claves.size)
        }
        assertEquals(ClickSound.CLAVES, track.sound)
        assertArrayEquals(expected, out, 0f)
    }

    @Test
    fun `the first beat of every bar is the accent, with group accents in 5-4, 6-8 and 7-8`() {
        val d = BeatAccent.DOWNBEAT
        val g = BeatAccent.GROUP
        val p = BeatAccent.PLAIN
        val bars = mapOf(
            Meter.TWO_FOUR to listOf(d, p),
            Meter.THREE_FOUR to listOf(d, p, p),
            Meter.FOUR_FOUR to listOf(d, p, p, p),
            Meter.FIVE_FOUR to listOf(d, p, p, g, p),
            Meter.SIX_EIGHT to listOf(d, p, p, g, p, p),
            Meter.SEVEN_EIGHT to listOf(d, p, g, p, g, p, p),
        )
        assertEquals(Meter.entries.toSet(), bars.keys)
        for ((meter, bar) in bars) {
            val beats = ClickTrack(48_000, bpm = 240, meter = meter).play(48_000L * 60 / 240 * bar.size * 3)
            assertEquals("$meter", bar + bar + bar, beats.map { it.accent })
            assertEquals("$meter", List(3) { bar.indices.toList() }.flatten(), beats.map { it.beatInBar })
        }
    }

    @Test
    fun `the tempo stays within 30 to 250 BPM`() {
        assertEquals(30, ClickTrack(48_000, bpm = 10).bpm)
        assertEquals(250, ClickTrack(48_000, bpm = 1_000).bpm)
        val track = ClickTrack(48_000)
        track.bpm = 0
        assertEquals(ClickTrack.MIN_BPM, track.bpm)
        track.bpm = 251
        assertEquals(ClickTrack.MAX_BPM, track.bpm)
        track.bpm = 60
        assertEquals(60, track.bpm)
    }

    @Test
    fun `a new tempo counts from the last beat that sounded`() {
        val rate = 48_000
        val track = ClickTrack(rate, bpm = 60)
        val recorder = Recorder()
        track.play(rate + rate / 4L, recorder = recorder) // beats at 0 and 1 s, now 1.25 s
        track.bpm = 120 // next beat 0.5 s after the one at 1 s
        track.play(rate * 2L, recorder = recorder)
        assertEquals(listOf(0L, 48_000L, 72_000L, 96_000L, 120_000L, 144_000L), recorder.beats.map { it.frame })

        // Slowing down mid-beat: one new interval (1 s) after the last beat, then steady at 60.
        track.bpm = 60
        track.play(rate * 3L, recorder = recorder)
        assertEquals(listOf(192_000L, 240_000L, 288_000L), recorder.beats.drop(6).map { it.frame })
    }

    @Test
    fun `a new tempo whose next beat has already passed plays it at once`() {
        val rate = 48_000
        val track = ClickTrack(rate, bpm = 30)
        val recorder = Recorder()
        track.play(rate * 3L / 2, recorder = recorder) // beat at 0, now 1.5 s; next due at 2 s
        track.bpm = 240 // one interval after 0 is 0.25 s, long gone
        track.play(rate.toLong(), recorder = recorder)
        assertEquals(listOf(0L, 72_000L, 84_000L, 96_000L, 108_000L), recorder.beats.map { it.frame })
    }

    @Test
    fun `a tempo set before the first beat keeps beat 0 on sample 0`() {
        val track = ClickTrack(48_000, bpm = 60)
        track.bpm = 120
        assertEquals(listOf(0L, 24_000L), track.play(48_000).map { it.frame })
    }

    @Test
    fun `a new meter starts a new bar on the next beat without moving it`() {
        val rate = 48_000
        val track = ClickTrack(rate, bpm = 120, meter = Meter.FOUR_FOUR)
        val recorder = Recorder()
        track.play(rate * 3L / 4, recorder = recorder) // beats 0 and 1 of 4/4
        track.meter = Meter.SIX_EIGHT
        track.play(rate * 7L / 2, recorder = recorder)
        assertEquals(listOf(0, 1, 0, 1, 2, 3, 4, 5, 0), recorder.beats.map { it.beatInBar })
        assertEquals((0L until 9L).map { it * 24_000 }, recorder.beats.map { it.frame })
    }

    @Test
    fun `reset starts over from sample 0 with a downbeat`() {
        val track = ClickTrack(48_000, bpm = 120, meter = Meter.THREE_FOUR)
        track.play(30_000)
        track.reset()
        assertEquals(0L, track.framesRendered)
        val beats = track.play(48_000)
        assertEquals(listOf(Beat(0, 0, BeatAccent.DOWNBEAT, 0), Beat(1, 1, BeatAccent.PLAIN, 24_000)), beats)
    }

    @Test
    fun `rendering and changing tempo, meter or a sound played before allocate nothing`() {
        val track = ClickTrack(48_000, bpm = 250)
        val buffer = FloatArray(480)
        val listener = BeatListener { _, _, _, _ -> }
        for (sound in ClickSound.entries) track.sound = sound // each rendered once
        track.sound = ClickSound.WOOD_BLOCK
        repeat(2_000) { track.render(buffer, listener = listener) }

        val bytes = allocatedBytes {
            repeat(500) { track.render(buffer, listener = listener) }
            track.bpm = 90
            track.meter = Meter.SEVEN_EIGHT
            repeat(500) { track.render(buffer, listener = listener) }
            track.sound = ClickSound.RIM_CLICK
            repeat(500) { track.render(buffer, listener = listener) }
            track.bpm = 250
            track.meter = Meter.FOUR_FOUR
            track.sound = ClickSound.COWBELL
            repeat(500) { track.render(buffer, listener = listener) }
        }
        assertEquals(0L, bytes)
    }

    @Test
    fun `rejects a bad sample rate or range`() {
        assertThrows(IllegalArgumentException::class.java) { ClickTrack(0) }
        val track = ClickTrack(48_000)
        assertThrows(IllegalArgumentException::class.java) { track.render(FloatArray(10), 5, 6) }
        assertThrows(IllegalArgumentException::class.java) { Meter.FOUR_FOUR.accentOf(4) }
    }
}
