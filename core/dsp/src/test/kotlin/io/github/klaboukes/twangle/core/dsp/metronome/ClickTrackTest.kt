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

    // Every click: a beat's own (click 0, its accent) or one between beats (SUBDIVISION).
    private data class Click(val beat: Long, val click: Int, val accent: BeatAccent, val frame: Long)

    private class Recorder : BeatListener {
        val beats = mutableListOf<Beat>()
        val clicks = mutableListOf<Click>()
        override fun onBeat(index: Long, beatInBar: Int, accent: BeatAccent, frame: Long) {
            beats += Beat(index, beatInBar, accent, frame)
            clicks += Click(index, 0, accent, frame)
        }

        override fun onSubdivision(beat: Long, click: Int, frame: Long) {
            clicks += Click(beat, click, BeatAccent.SUBDIVISION, frame)
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
    fun `every click of ten minutes lands on its ideal sample, in every subdivision and at odd tempos`() {
        for ((rate, bpm, chunk) in listOf(Triple(44_100, 97, 441), Triple(48_000, 233, 37), Triple(16_000, 31, 4096))) {
            for (subdivision in Subdivision.entries) {
                val k = subdivision.clicksPerBeat
                val recorder = Recorder()
                ClickTrack(rate, bpm = bpm, subdivision = subdivision).play(rate * 600L, chunk, recorder)
                val at = "$subdivision at $bpm BPM, $rate Hz"
                val exact = rate * 60.0 / (bpm * k)
                assertEquals("clicks in ten minutes, $at", 10 * bpm * k, recorder.clicks.size)
                for ((n, click) in recorder.clicks.withIndex()) {
                    assertEquals("click $n, $at", n.toLong(), click.beat * k + click.click)
                    val ideal = n * exact
                    assertEquals("click $n, $at", ideal.roundToLong(), click.frame)
                    assertTrue(abs(click.frame - ideal) <= 0.5)
                }
            }
        }
    }

    @Test
    fun `the clicks between the beats are the softest click, and the beats stay as they were`() {
        val rate = 44_100
        val total = rate * 4
        val alone = Recorder()
        ClickTrack(rate, bpm = 97, meter = Meter.FIVE_FOUR).play(total.toLong(), recorder = alone)
        for (subdivision in Subdivision.entries) {
            val track = ClickTrack(rate, bpm = 97, meter = Meter.FIVE_FOUR, subdivision = subdivision)
            val recorder = Recorder()
            val out = FloatArray(total)
            var at = 0
            for (size in generateSequence(1) { it * 3 % 1013 + 1 }) {
                val n = minOf(size, total - at)
                track.render(out, at, n, recorder)
                at += n
                if (at == total) break
            }
            assertEquals("$subdivision", alone.beats, recorder.beats)
            val k = subdivision.clicksPerBeat
            val heard = generateSequence(0L) { it + 1 }.takeWhile { (it * rate * 60 * 2 / (97 * k) + 1) / 2 < total }.count()
            assertEquals("$subdivision", heard, recorder.clicks.size)

            val expected = FloatArray(total)
            for (click in recorder.clicks) {
                val samples = track.sounds.waveform(click.accent)
                val start = click.frame.toInt()
                System.arraycopy(samples, 0, expected, start, minOf(samples.size, total - start))
            }
            assertArrayEquals("$subdivision", expected, out, 0f)
        }
    }

    @Test
    fun `in 6-8 and 7-8 only none and two split the beat, and the others play as none`() {
        for (meter in Meter.entries) {
            val offered = if (meter.noteValue == 8) listOf(Subdivision.NONE, Subdivision.TWO) else Subdivision.entries
            assertEquals("$meter", offered, Subdivision.offeredIn(meter))
            for (subdivision in Subdivision.entries) {
                val k = if (subdivision in offered) subdivision.clicksPerBeat else 1
                assertEquals(if (k == 1) Subdivision.NONE else subdivision, subdivision.within(meter))
                val recorder = Recorder()
                ClickTrack(48_000, bpm = 120, meter = meter, subdivision = subdivision).play(48_000, recorder = recorder)
                assertEquals("$subdivision in $meter", (0 until 2 * k).map { it * 24_000L / k }, recorder.clicks.map { it.frame })
            }
        }
    }

    @Test
    fun `a new subdivision is heard from the next beat`() {
        val rate = 48_000
        val track = ClickTrack(rate, bpm = 120)
        val recorder = Recorder()
        track.play(rate / 4L, recorder = recorder) // into beat 0
        track.subdivision = Subdivision.FOUR
        track.play(rate * 3L / 4 - rate / 4, recorder = recorder) // into beat 1, after its second click
        track.subdivision = Subdivision.NONE
        track.play(rate.toLong(), recorder = recorder)
        assertEquals(
            listOf(0L, 24_000L, 30_000L, 36_000L, 42_000L, 48_000L, 72_000L),
            recorder.clicks.map { it.frame },
        )
        assertEquals(Subdivision.NONE, track.subdivision)
    }

    @Test
    fun `a new tempo moves the clicks left in the beat, leaving out those already past`() {
        val rate = 48_000
        // Faster: at 120 BPM beat 0's clicks would have been at 6000, 12 000 and 18 000, all past at 20 000.
        val faster = ClickTrack(rate, bpm = 60, subdivision = Subdivision.FOUR)
        val fast = Recorder()
        faster.play(20_000, recorder = fast) // beat 0 and its click at 12 000
        faster.bpm = 120
        faster.play(30_000, recorder = fast)
        assertEquals(listOf(0L, 12_000L, 24_000L, 30_000L, 36_000L, 42_000L, 48_000L), fast.clicks.map { it.frame })

        // Slower: beat 0's third click comes at 60 BPM's 24 000, the fourth at 36 000.
        val slower = ClickTrack(rate, bpm = 120, subdivision = Subdivision.FOUR)
        val slow = Recorder()
        slower.play(7_000, recorder = slow) // beat 0 and its click at 6000
        slower.bpm = 60
        slower.play(43_000, recorder = slow)
        assertEquals(listOf(0L, 6_000L, 24_000L, 36_000L, 48_000L), slow.clicks.map { it.frame })
        assertEquals(listOf(0, 1, 2, 3, 0), slow.clicks.map { it.click })
    }

    @Test
    fun `a new meter starts a new bar on the next beat, the clicks between beats unmoved`() {
        val rate = 48_000
        val track = ClickTrack(rate, bpm = 120, meter = Meter.FOUR_FOUR, subdivision = Subdivision.TWO)
        val recorder = Recorder()
        track.play(rate * 3L / 4 - 1000, recorder = recorder) // beats 0 and 1 and their halves
        track.meter = Meter.SEVEN_EIGHT
        track.play(rate * 5L, recorder = recorder)
        assertEquals((0L until 11L * 2).map { it * 12_000 }, recorder.clicks.map { it.frame }.take(22))
        assertEquals(listOf(0, 1, 0, 1, 2, 3, 4, 5, 6, 0, 1), recorder.beats.map { it.beatInBar }.take(11))
    }

    @Test
    fun `at 250 BPM a click the next one catches up with fades out to 0 just before it, every sound`() {
        val rate = 44_100
        val fade = (ClickTrack.CUT_FADE_SECONDS * rate).toInt()
        for (sound in ClickSound.entries) {
            for (subdivision in listOf(Subdivision.TWO, Subdivision.THREE, Subdivision.FOUR)) {
                val at = "$sound in ${subdivision.clicksPerBeat}s"
                val track = ClickTrack(rate, bpm = 250, meter = Meter.FIVE_FOUR, sound = sound, subdivision = subdivision)
                val recorder = Recorder()
                val total = rate * 3
                val out = FloatArray(total)
                var done = 0
                while (done < total) {
                    val n = minOf(441, total - done)
                    track.render(out, done, n, recorder)
                    done += n
                }
                val clicks = recorder.clicks
                fun frameOf(n: Int) = ((n * rate * 60L * 2 / (250 * subdivision.clicksPerBeat) + 1) / 2).toInt()
                assertEquals(at, (0 until clicks.size).map { frameOf(it).toLong() }, clicks.map { it.frame })
                var cut = 0
                for ((n, click) in clicks.withIndex()) {
                    val samples = track.sounds.waveform(sound, click.accent)
                    val start = click.frame.toInt()
                    val next = frameOf(n + 1) // also for the last: the fade comes before the next click, heard or not
                    val overruns = start + samples.size > next
                    val whole = minOf(if (overruns) next - fade else start + samples.size, total)
                    for (i in start until whole) assertEquals("$at: click $n, sample ${i - start}", samples[i - start], out[i], 0f)
                    if (!overruns || next > total) continue
                    cut++
                    for (i in next - fade until next) {
                        assertTrue("$at: click $n louder in its fade", abs(out[i]) <= abs(samples[i - start]))
                    }
                    assertTrue("$at: click $n ends on ${out[next - 1]}", abs(out[next - 1]) < 1e-3f)
                }
                val interval = rate * 60 / (250 * subdivision.clicksPerBeat)
                assertEquals("$at: clicks cut", track.sounds.frames(sound) > interval, cut > 0)
            }
        }
    }

    @Test
    fun `tempo changes at any moment between subdivided clicks never crackle`() {
        val rate = 48_000
        val random = java.util.Random(62)
        for (sound in ClickSound.entries) {
            val track = ClickTrack(rate, bpm = 250, sound = sound, subdivision = Subdivision.FOUR)
            // The steepest step inside any of the sound's clicks: no step of the track may be much steeper.
            val steepest = BeatAccent.entries.maxOf { a ->
                val click = track.sounds.waveform(sound, a)
                (1 until click.size).maxOf { abs(click[it] - click[it - 1]) }
            }
            val recorder = Recorder()
            val total = rate * 20
            val out = FloatArray(total)
            var done = 0
            while (done < total) {
                if (random.nextInt(4) == 0) track.bpm = 30 + random.nextInt(221)
                if (random.nextInt(20) == 0) track.subdivision = Subdivision.entries[random.nextInt(4)]
                val n = minOf(1 + random.nextInt(600), total - done)
                track.render(out, done, n, recorder)
                done += n
            }
            val worst = (1 until total).maxOf { abs(out[it] - out[it - 1]) }
            assertTrue("$sound: a step of $worst, the clicks' steepest $steepest", worst <= steepest + 0.02f)
            val frames = recorder.clicks.map { it.frame }
            assertTrue("$sound: clicks out of order", frames.zipWithNext().all { (a, b) -> b > a })
        }
    }

    @Test
    fun `rendering and changing tempo, meter, subdivision or a sound played before allocate nothing`() {
        val track = ClickTrack(48_000, bpm = 250, subdivision = Subdivision.FOUR)
        val buffer = FloatArray(480)
        val listener = BeatListener { _, _, _, _ -> }
        for (sound in ClickSound.entries) track.sound = sound // each rendered once
        track.sound = ClickSound.WOOD_BLOCK
        repeat(2_000) { track.render(buffer, listener = listener) }

        val bytes = allocatedBytes {
            repeat(500) { track.render(buffer, listener = listener) }
            track.bpm = 90
            track.meter = Meter.SEVEN_EIGHT
            track.subdivision = Subdivision.TWO
            repeat(500) { track.render(buffer, listener = listener) }
            track.sound = ClickSound.RIM_CLICK
            track.subdivision = Subdivision.THREE
            repeat(500) { track.render(buffer, listener = listener) }
            track.bpm = 250
            track.meter = Meter.FOUR_FOUR
            track.sound = ClickSound.COWBELL
            repeat(250) { track.render(buffer, listener = listener) }
            track.subdivision = Subdivision.NONE
            repeat(250) { track.render(buffer, listener = listener) }
        }
        assertEquals(0L, bytes)
    }

    @Test
    fun `the speed trainer steps on each downbeat, every click on its sample, never past the goal`() {
        for ((rate, meter, subdivision) in listOf(
            Triple(48_000, Meter.FOUR_FOUR, Subdivision.TWO),
            Triple(44_100, Meter.THREE_FOUR, Subdivision.THREE),
            Triple(48_000, Meter.SEVEN_EIGHT, Subdivision.NONE),
        )) {
            val trainer = SpeedTrainer(startBpm = 97, goalBpm = 120, stepBpm = 5, bars = 2)
            val track = ClickTrack(rate, bpm = 60, meter = meter, subdivision = subdivision)
            track.startTrainer(trainer)
            val recorder = Recorder()
            track.play(rate * 120L, chunk = 441, recorder = recorder)

            // The ideal: each bar's beats (and the clicks between them) counted from its first
            // downbeat at the bar's tempo; a bar at the same tempo as the one before goes on counting.
            val k = subdivision.within(meter).clicksPerBeat
            var anchorBeat = 0L
            var anchorFrame = 0L
            var tempo = trainer.startBpm
            var bar = -1L
            fun ideal(beat: Long, click: Int): Long =
                anchorFrame + Math.round(((beat - anchorBeat) * k + click) * 60.0 * rate / (tempo * k))
            val beats = recorder.beats
            for (beat in beats) {
                if (beat.beatInBar == 0) {
                    bar++
                    val next = trainer.bpmOfBar(bar)
                    if (next != tempo) {
                        // The step lands on the downbeat, which keeps the old tempo's place.
                        assertEquals("downbeat ${beat.index}, $rate Hz", ideal(beat.index, 0), beat.frame)
                        anchorBeat = beat.index
                        anchorFrame = beat.frame
                        tempo = next
                    }
                }
                assertEquals("beat ${beat.index}, $rate Hz", ideal(beat.index, 0), beat.frame)
                assertEquals(bar, if (beat.index < beats.last().index) bar else track.trainerBar)
                assertTrue("never past the goal", tempo <= trainer.goalBpm)
            }
            for (click in recorder.clicks.filter { it.click > 0 }) {
                val beatFrame = beats.first { it.index == click.beat }.frame
                val next = beats.firstOrNull { it.index == click.beat + 1 }?.frame ?: continue
                // Between its beat and the next, evenly: the beat's own interval split k ways.
                val exact = beatFrame + (next - beatFrame) * click.click / k.toDouble()
                assertTrue("click ${click.beat}.${click.click}", kotlin.math.abs(click.frame - exact) <= 1.0)
            }
            assertEquals("it holds at the goal", trainer.goalBpm, track.bpm)
            val last = beats.takeLast(2)
            assertEquals(Math.round(60.0 * rate / trainer.goalBpm), last[1].frame - last[0].frame)
        }
    }

    @Test
    fun `a trainer started while playing takes its start tempo from the next beat and counts bars from the next downbeat`() {
        val rate = 48_000
        val track = ClickTrack(rate, bpm = 120, meter = Meter.FOUR_FOUR)
        val recorder = Recorder()
        track.play(24_000L + 100, chunk = 480, recorder = recorder) // beats 0 and 1 sounded
        track.startTrainer(SpeedTrainer(startBpm = 60, goalBpm = 70, stepBpm = 10, bars = 1))
        assertEquals(60, track.bpm)
        assertEquals(-1L, track.trainerBar)
        track.play(rate * 12L, chunk = 480, recorder = recorder)
        val frames = recorder.beats.map { it.frame }
        assertEquals("beat 2 one new interval after beat 1", 24_000L + 48_000L, frames[2])
        assertEquals("beat 3, still before the first downbeat", 24_000L + 2 * 48_000L, frames[3])
        assertEquals("beat 4, the first downbeat, bar 0 at the start", 24_000L + 3 * 48_000L, frames[4])
        // Bar 1 (beat 8) is at 70 BPM: from its downbeat on, 41 143 samples apart.
        assertEquals(frames[7] + 48_000L, frames[8])
        assertEquals(frames[8] + Math.round(60.0 * rate / 70), frames[9])
        assertEquals(70, track.bpm)
    }

    @Test
    fun `ending the trainer keeps the tempo it climbed to, and a reset begins it again from the start`() {
        val track = ClickTrack(48_000, bpm = 90, meter = Meter.TWO_FOUR)
        val trainer = SpeedTrainer(startBpm = 90, goalBpm = 100, stepBpm = 10, bars = 1)
        track.startTrainer(trainer)
        track.play(48_000L * 3)
        assertEquals(100, track.bpm)
        track.reset()
        assertEquals("a reset begins the climb again", 90, track.bpm)
        assertEquals(-1L, track.trainerBar)
        track.play(48_000L * 3)
        assertEquals(100, track.bpm)
        track.startTrainer(null)
        track.play(48_000L * 3)
        assertEquals(100, track.bpm)
        assertEquals(null, track.trainer)
        track.reset()
        assertEquals("without a trainer a reset keeps the tempo", 100, track.bpm)
    }

    @Test
    fun `climbing and starting the trainer allocate nothing`() {
        val track = ClickTrack(48_000, bpm = 120, subdivision = Subdivision.FOUR)
        val buffer = FloatArray(480)
        val listener = BeatListener { _, _, _, _ -> }
        val slow = SpeedTrainer(60, 250, 10, 1)
        val fast = SpeedTrainer(200, 250, 1, 1)
        repeat(1_000) { track.render(buffer, listener = listener) }
        val bytes = allocatedBytes {
            track.startTrainer(slow)
            repeat(2_000) { track.render(buffer, listener = listener) }
            track.startTrainer(fast)
            repeat(2_000) { track.render(buffer, listener = listener) }
            track.startTrainer(null)
            repeat(200) { track.render(buffer, listener = listener) }
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
