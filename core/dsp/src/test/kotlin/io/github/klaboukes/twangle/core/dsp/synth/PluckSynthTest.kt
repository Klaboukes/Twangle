package io.github.klaboukes.twangle.core.dsp.synth

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

class PluckSynthTest {

    private val synth = PluckSynth(RATE)

    @Test
    fun `every preset string sounds within 1 cent of its pitch at any A4`() {
        val notes = Instruments.all.flatMap { instrument -> instrument.tunings.flatMap { it.strings } }.distinct()
        for (a4 in listOf(NoteMapper.MIN_A4_HZ, 432f, NoteMapper.DEFAULT_A4_HZ, NoteMapper.MAX_A4_HZ)) {
            val mapper = NoteMapper(a4)
            for (note in notes) {
                val f = mapper.frequencyOf(note)
                assertPitch("$note at A4 = $a4", f, RATE, startSeconds = 0.3f)
            }
        }
    }

    @Test
    fun `the pitch holds to the end of the tone and at 44_1 kHz`() {
        for (f in listOf(41.2f, 82.41f, 196f, 440f, 659.26f)) {
            assertPitch("$f Hz late in the tone", f, RATE, startSeconds = 3.0f)
            assertPitch("$f Hz at 44.1 kHz", f, 44_100, startSeconds = 0.3f)
        }
    }

    @Test
    fun `stops by itself after the set duration and then stays silent`() {
        val tone = renderAll(synth, 440f)

        assertEquals((PluckSynth.DEFAULT_DURATION_SECONDS * RATE).roundToInt(), tone.size)
        assertFalse(synth.isSounding)
        assertEquals(0, synth.render(FloatArray(64)))

        val short = PluckSynth(RATE, durationSeconds = 1.5f)
        assertEquals((1.5f * RATE).roundToInt(), renderAll(short, 440f).size)
    }

    @Test
    fun `dies away like a plucked string and ends on silence`() {
        for (f in listOf(41.2f, 110f, 440f, 1318.5f)) {
            val tone = renderAll(synth, f)
            val start = rmsDb(tone, 0.05f, 0.5f)
            val beforeFade = rmsDb(tone, PluckSynth.DEFAULT_DURATION_SECONDS - PluckSynth.END_FADE_SECONDS - 0.5f, 0.5f)
            assertTrue("$f Hz: start $start dB, later $beforeFade dB", start - beforeFade > 15)
            assertEquals("$f Hz: last sample", 0f, tone.last(), 1e-7f)
            assertTrue("$f Hz: quiet near the end", rmsDb(tone, PluckSynth.DEFAULT_DURATION_SECONDS - 0.05f, 0.05f) < -60)
        }
    }

    @Test
    fun `never clips and is loud enough to hear`() {
        var f = 25f
        while (f < synth.maxFrequencyHz) {
            val tone = renderAll(synth, f)
            val peak = tone.maxOf { abs(it) }
            assertTrue("$f Hz: peak $peak", peak <= PluckSynth.PEAK)
            assertTrue("$f Hz: peak $peak", peak > 0.25f)
            f *= 1.19f
        }
    }

    @Test
    fun `starts and ends without a click`() {
        for (f in listOf(41.2f, 82.41f, 329.63f, 1318.5f)) {
            val tone = renderAll(synth, f)
            // Harmonics at 1/k starting together make a sawtooth, which jumps at once to its peak;
            // the attack turns that jump into a ramp. The bright start is steeper than later on,
            // so the first millisecond is compared with the next periods.
            assertEquals("$f Hz: first sample", 0f, tone[0], 0f)
            assertTrue("$f Hz: start", maxStep(tone, 0, ms(1)) <= 0.25f * maxStep(tone, ms(5), ms(80)))
            assertTrue("$f Hz: end", maxStep(tone, tone.size - ms(10), tone.size) <= maxStep(tone, ms(20), ms(200)))
        }
    }

    @Test
    fun `release fades out quickly without a click`() {
        val f = 329.63f
        synth.start(f)
        val before = FloatArray(ms(300))
        assertEquals(before.size, synth.render(before))
        synth.release()
        val after = FloatArray(ms(500))
        val n = synth.render(after)

        assertEquals((PluckSynth.STOP_FADE_SECONDS * RATE).roundToInt(), n)
        assertFalse(synth.isSounding)
        assertEquals(0f, after[n - 1], 1e-7f)
        val joined = before + after.copyOf(n)
        assertTrue(maxStep(joined, before.size - 1, joined.size) <= maxStep(before, before.size - ms(50), before.size))
    }

    @Test
    fun `a longer release never makes a running fade slower or louder`() {
        synth.start(440f)
        synth.render(FloatArray(ms(100)))
        synth.release(0.02f)
        synth.render(FloatArray(ms(10)))
        synth.release(1f) // ignored: the running fade ends sooner
        assertEquals(ms(10), synth.render(FloatArray(ms(500))))
        assertFalse(synth.isSounding)
    }

    @Test
    fun `rendering in chunks gives the same samples as at once`() {
        val whole = renderAll(PluckSynth(RATE), 220f)
        val chunked = PluckSynth(RATE)
        chunked.start(220f)
        val parts = FloatArray(whole.size + 1000)
        var filled = 0
        while (true) {
            val n = chunked.render(parts, filled, minOf(480, parts.size - filled))
            if (n == 0) break
            filled += n
        }
        assertEquals(whole.size, filled)
        assertArrayEquals(whole, parts.copyOf(filled), 0f)
    }

    @Test
    fun `start, render and release allocate nothing`() {
        val buffer = FloatArray(480)
        repeat(3) { synth.start(110f); while (synth.render(buffer) > 0) Unit }

        val bytes = allocatedBytes {
            synth.start(110f)
            repeat(20) { synth.render(buffer) }
            synth.release()
            synth.render(buffer)
        }
        assertEquals(0L, bytes)
    }

    @Test
    fun `rejects frequencies it can't play`() {
        assertThrows(IllegalArgumentException::class.java) { synth.start(0f) }
        assertThrows(IllegalArgumentException::class.java) { synth.start(-440f) }
        assertThrows(IllegalArgumentException::class.java) { synth.start(synth.maxFrequencyHz * 1.01f) }
        assertThrows(IllegalArgumentException::class.java) { PluckSynth(RATE, durationSeconds = 0.5f) }
    }

    private fun assertPitch(label: String, frequencyHz: Float, rate: Int, startSeconds: Float) {
        val settings = AnalysisSettings.forSampleRate(rate, PitchRange.forLowestNote(frequencyHz))
        val tone = renderAll(PluckSynth(rate), frequencyHz)
        val from = (startSeconds * rate).roundToInt()
        val result = settings.newPitchDetector().detect(tone.copyOfRange(from, from + settings.windowSize))
        assertTrue("$label: no pitch", result.isPitched)
        val cents = 1200 * log2(result.frequencyHz / frequencyHz)
        assertTrue("$label: $cents cents", abs(cents) <= 1f)
    }

    private fun renderAll(synth: PluckSynth, frequencyHz: Float): FloatArray {
        synth.start(frequencyHz)
        val out = FloatArray(((synth.durationSeconds + 1) * synth.sampleRate).toInt())
        val n = synth.render(out)
        return out.copyOf(n)
    }

    private fun ms(millis: Int): Int = RATE * millis / 1000

    private fun maxStep(x: FloatArray, from: Int, to: Int): Float {
        var most = 0f
        for (i in maxOf(1, from) until to) most = maxOf(most, abs(x[i] - x[i - 1]))
        return most
    }

    private fun rmsDb(x: FloatArray, fromSeconds: Float, seconds: Float): Double {
        val from = (fromSeconds * RATE).roundToInt()
        val to = from + (seconds * RATE).roundToInt()
        var sum = 0.0
        for (i in from until to) sum += x[i].toDouble() * x[i]
        return 20 * log10(sqrt(sum / (to - from)) + 1e-12)
    }

    private companion object {
        const val RATE = 48_000
    }
}
