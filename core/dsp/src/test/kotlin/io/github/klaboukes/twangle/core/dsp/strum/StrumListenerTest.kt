package io.github.klaboukes.twangle.core.dsp.strum

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

class StrumListenerTest {

    private val rate = StrumChords.SAMPLE_RATE
    private val standard = StrumChords.expectedHz(Tunings.STANDARD)
    private val listener = StrumListener(rate)
    private val hop = listener.hopSize
    private val frameLength = listener.frameLength

    private fun seconds(s: Double) = (s * rate).roundToInt()

    // Quiet room noise (≈ −70 dBFS) with a strum of the open strings at each of [at] seconds.
    private fun take(totalSeconds: Double, vararg at: Pair<Double, FloatArray>, cut: Double = 1.6): FloatArray {
        val out = TestSignals.noise(seconds(totalSeconds), rms = 3e-4f, seed = 3L)
        for ((start, hz) in at) {
            val strum = StrumChords.pluckSynthStrum(hz, seconds(cut), fromSeconds = 0.0)
            val from = seconds(start)
            for (i in strum.indices) if (from + i < out.size) out[from + i] += strum[i]
        }
        return out
    }

    private class Frame(val readyAt: Int, val samples: FloatArray, val strum: Int, val reading: Int)

    // Feeds [input] in chunks of [chunk] samples; returns every frame with the stream position it was ready at.
    private fun listen(input: FloatArray, chunk: Int = hop, with: StrumListener = listener): List<Frame> {
        val frames = mutableListOf<Frame>()
        var consumed = 0
        var start = 0
        while (start < input.size) {
            val length = minOf(chunk, input.size - start)
            var pos = 0
            while (pos < length) {
                val used = with.push(input, start + pos, length - pos)
                pos += used
                consumed += used
                if (with.isFrameReady) {
                    val reading = with.readingsSinceStrum
                    val out = FloatArray(with.frameLength)
                    with.readFrame(out)
                    frames += Frame(consumed, out, with.strumCount, reading)
                }
            }
            start += length
        }
        return frames
    }

    @Test
    fun `frame length follows the detector's, twice for low instruments`() {
        assertEquals(16_384, frameLength)
        assertEquals(1024, hop)
        assertEquals(32_768, StrumListener(rate, PitchRange.LOW).frameLength)
        assertEquals(941, StrumListener(44_100).hopSize)
    }

    @Test
    fun `room noise alone gives no frame`() {
        assertTrue(listen(take(3.0)).isEmpty())
        assertEquals(0, listener.strumCount)
        assertEquals(StrumListener.NO_STRUM, listener.samplesSinceStrum)
        assertTrue(listener.levelDbfs < -60f)
    }

    @Test
    fun `the first frame starts just after the strum and arrives about 0,4 s later`() {
        val strumAt = 0.5
        val input = take(2.5, strumAt to standard)
        val frames = listen(input)
        assertTrue(frames.isNotEmpty())
        val first = frames.first()
        val strum = seconds(strumAt)
        val frameStart = first.readyAt - frameLength
        // The strum is noticed at the end of its hop and the frame is offered at the end of a hop:
        // up to two hops late.
        val attack = seconds(StrumListener.ATTACK_SECONDS)
        assertTrue("frame starts ${frameStart - strum} samples after the strum", frameStart - strum in attack..attack + 2 * hop)
        assertArrayEquals(input.copyOfRange(frameStart, first.readyAt), first.samples, 0f)
        val latency = (first.readyAt - strum).toDouble() / rate
        assertTrue("first reading after $latency s", latency in 0.39..0.44)
        assertEquals(1, first.strum)
        assertEquals(0, first.reading)
    }

    @Test
    fun `readings keep coming every 0,1 s while the strings ring, then stop`() {
        val input = take(3.5, 0.5 to standard, cut = 1.6)
        val frames = listen(input)
        // Ringing from 0.5 to 2.1 s: readings from ≈ 0.9 s every ≈ 0.1 s.
        assertTrue("${frames.size} readings", frames.size in 11..13)
        for (i in 1 until frames.size) {
            val gap = frames[i].readyAt - frames[i - 1].readyAt
            assertTrue("gap $gap", gap in seconds(StrumListener.REREAD_SECONDS)..seconds(StrumListener.REREAD_SECONDS) + hop)
            assertEquals(i, frames[i].reading)
        }
        assertTrue(frames.last().readyAt <= seconds(2.1) + hop)
        assertFalse(listener.isRinging)
        assertEquals(1, listener.strumCount)
    }

    @Test
    fun `a new strum starts over, also while the last one rings`() {
        // The first strum rings on at a third of the second's level: the second rises ≈ 10 dB above it.
        val input = TestSignals.noise(seconds(4.0), rms = 3e-4f, seed = 3L)
        val first = StrumChords.pluckSynthStrum(standard, seconds(3.0), fromSeconds = 0.0)
        val second = StrumChords.pluckSynthStrum(standard, seconds(2.0), fromSeconds = 0.0)
        for (i in first.indices) input[seconds(0.5) + i] += 0.33f * first[i]
        for (i in second.indices) input[seconds(1.5) + i] += second[i]
        val frames = listen(input)
        assertEquals(2, listener.strumCount)
        val afterSecond = frames.filter { it.strum == 2 }
        assertTrue(afterSecond.isNotEmpty())
        assertEquals(0, afterSecond.first().reading)
        assertTrue(afterSecond.first().readyAt - frameLength >= seconds(1.5 + StrumListener.ATTACK_SECONDS))
    }

    // A 1 ms click of [amplitude] added at [at] seconds: a knock, or strings stopped at once.
    private fun click(input: FloatArray, at: Double, amplitude: Float = 0.3f) {
        val from = seconds(at)
        for (i in 0 until seconds(0.001)) input[from + i] += if (i % 2 == 0) amplitude else -amplitude
    }

    @Test
    fun `a click over strings dying away is noticed but not read`() {
        // The tail of a strum at ≈ −53 dBFS, still ringing (above −56) but under the gate, and a
        // knock or the click of muting them: the case seen on the phone.
        val tail = TestSignals.sawtooth(110f, length = seconds(2.5), amplitude = 0.004f)
        val noise = TestSignals.noise(tail.size, rms = 3e-5f, seed = 3L)
        for (i in tail.indices) tail[i] += noise[i]
        click(tail, 1.0)
        assertTrue(listen(tail).isEmpty())
        assertEquals(1, listener.strumCount)
        assertTrue("tail at ${listener.levelDbfs} dBFS", listener.levelDbfs in -56f..-50f)
        assertFalse(listener.isRinging)
    }

    @Test
    fun `a soft strum just above the first frame's limit is read`() {
        val loud = listen(take(2.0, 0.5 to standard))
        val full = levelDbfs(loud.first().samples)
        assertTrue("first frame at $full dBFS", full > -30f)
        // frameLevelDbfs tells the level of the last frame offered.
        assertEquals(levelDbfs(loud.last().samples), listener.frameLevelDbfs, 0.1f)
        val gain = Math.pow(10.0, (StrumListener.MIN_FIRST_FRAME_DBFS + 3f - full) / 20.0).toFloat()
        val soft = TestSignals.noise(seconds(2.0), rms = 3e-5f, seed = 3L)
        val strum = StrumChords.pluckSynthStrum(standard, seconds(1.4), fromSeconds = 0.0)
        for (i in strum.indices) soft[seconds(0.5) + i] += gain * strum[i]
        val frames = listen(soft, with = StrumListener(rate))
        assertTrue(frames.isNotEmpty())
        assertEquals(StrumListener.MIN_FIRST_FRAME_DBFS + 3f, levelDbfs(frames.first().samples), 0.5f)
    }

    private fun levelDbfs(frame: FloatArray): Float =
        (20 * log10(sqrt(frame.sumOf { it.toDouble() * it } / frame.size))).toFloat()

    @Test
    fun `chunk size changes nothing`() {
        val input = take(2.5, 0.5 to standard)
        val byHop = listen(input).map { it.readyAt }
        assertEquals(byHop, listen(input, chunk = 700, with = StrumListener(rate)).map { it.readyAt })
        assertEquals(byHop, listen(input, chunk = 4096, with = StrumListener(rate)).map { it.readyAt })
    }

    @Test
    fun `from microphone chunks to verdicts`() {
        val cents = floatArrayOf(0f, 0f, -20f, 0f, 15f, 0f)
        val frames = listen(take(2.0, 0.5 to StrumChords.detuned(standard, cents)))
        val detector = StrumDetector(rate, frameLength)
        val result = StrumResult(StrumDetector.MAX_STRINGS)
        detector.detect(frames.first().samples, standard, 6, result)
        val verdicts = (0 until 6).map { StrumVerdict.of(result, it) }
        assertEquals(
            listOf(StrumVerdict.IN_TUNE, StrumVerdict.IN_TUNE, StrumVerdict.FLAT, StrumVerdict.IN_TUNE, StrumVerdict.SHARP, StrumVerdict.IN_TUNE),
            verdicts,
        )
        for (s in 0 until 6) assertEquals("string $s", cents[s], result.cents[s], 3f)
    }

    @Test
    fun `a sound already there when listening starts is no strum`() {
        // A steady note at about −20 dBFS from the first sample, and strings still ringing after a reset.
        assertTrue(listen(TestSignals.sawtooth(110f, length = seconds(1.0), amplitude = 0.15f)).isEmpty())
        assertEquals(0, listener.strumCount)

        val ringing = take(2.5, 0.2 to standard)
        val later = ringing.copyOfRange(seconds(0.4), ringing.size)
        listener.reset()
        assertTrue(listen(later).isEmpty())
        assertEquals(0, listener.strumCount)

        // The next strum still counts.
        listener.reset()
        val restrum = take(3.5, 0.2 to standard, 2.0 to standard)
        assertEquals(1, listen(restrum.copyOfRange(seconds(0.4), restrum.size)).map { it.strum }.distinct().size)
        assertEquals(1, listener.strumCount)
    }

    @Test
    fun `reset forgets the strum`() {
        listen(take(1.2, 0.5 to standard))
        assertEquals(1, listener.strumCount)
        listener.reset()
        assertEquals(0, listener.strumCount)
        assertEquals(StrumListener.NO_STRUM, listener.samplesSinceStrum)
        assertFalse(listener.isFrameReady)
        assertThrows(IllegalStateException::class.java) { listener.readFrame(FloatArray(frameLength)) }
    }

    @Test
    fun `push and readFrame allocate nothing`() {
        val input = take(2.0, 0.3 to standard)
        val out = FloatArray(frameLength)
        val l = StrumListener(rate)
        fun run() {
            l.reset()
            var start = 0
            while (start < input.size) {
                val length = minOf(hop, input.size - start)
                var pos = 0
                while (pos < length) {
                    pos += l.push(input, start + pos, length - pos)
                    if (l.isFrameReady) l.readFrame(out)
                }
                start += length
            }
        }
        repeat(5) { run() }
        assertEquals(0L, allocatedBytes { run() })
    }
}
