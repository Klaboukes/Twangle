package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.buffer.FrameAssembler
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.music.centsBetween
import io.github.klaboukes.twangle.core.dsp.music.nearestNote
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class YinPitchDetectorTest {

    private val yin = YinPitchDetector()
    private val mapper = NoteMapper()

    @Test
    fun `sines from 62 to 1500 Hz are within 1 cent at any phase`() {
        for (f in sweep(121)) {
            for (phase in listOf(0.0, 1.3, 4.0)) {
                val result = yin.detect(TestSignals.sine(f, phase = phase))
                assertWithinCents("sine $f Hz, phase $phase", f, result, ACCURACY_CENTS)
                assertTrue("confidence at $f Hz", result.confidence > 0.95f)
            }
        }
    }

    @Test
    fun `harmonic-rich tones from 62 to 1500 Hz are within 1 cent`() {
        val signals = mapOf<String, (Float) -> FloatArray>(
            "sawtooth" to { TestSignals.sawtooth(it) },
            "square" to { TestSignals.square(it) },
            "pluck" to { TestSignals.pluck(it) },
            "weak fundamental" to { TestSignals.weakFundamental(it) },
        )
        for ((name, generate) in signals) {
            for (f in sweep(61)) assertWithinCents("$name $f Hz", f, yin.detect(generate(f)), ACCURACY_CENTS)
        }
    }

    @Test
    fun `weak fundamental on the low strings reads the fundamental, not the octave above`() {
        for (note in Tunings.STANDARD.strings + Tunings.DROP_D.strings[0]) {
            val f = mapper.frequencyOf(note)
            val result = yin.detect(TestSignals.weakFundamental(f))
            assertEquals("$note", note, mapper.nearestNote(result.frequencyHz))
            assertWithinCents("$note", f, result, ACCURACY_CENTS)
        }
    }

    @Test
    fun `tones with background hiss at 20 dB SNR stay within 1 cent`() {
        for (f in sweep(41)) {
            val seed = f.toBits().toLong()
            for ((name, clean) in listOf(
                "sine" to TestSignals.sine(f),
                "sawtooth" to TestSignals.sawtooth(f),
                "weak fundamental" to TestSignals.weakFundamental(f),
            )) {
                val noisy = TestSignals.withNoise(clean, snrDb = 20f, seed = seed)
                assertWithinCents("$name $f Hz + noise", f, yin.detect(noisy), ACCURACY_CENTS)
            }
        }
    }

    @Test
    fun `heavy hiss at 10 dB SNR still gives the right note`() {
        for (f in sweep(41)) {
            val noisy = TestSignals.withNoise(TestSignals.weakFundamental(f), snrDb = 10f, seed = 42)
            assertWithinCents("$f Hz + heavy noise", f, yin.detect(noisy), 5f)
        }
    }

    @Test
    fun `silence, noise and DC have no pitch`() {
        assertFalse(yin.detect(FloatArray(4096)).isPitched)
        assertFalse(yin.detect(FloatArray(4096) { 0.3f }).isPitched)
        for (seed in 1L..5L) {
            val result = yin.detect(TestSignals.noise(rms = 0.2f, seed = seed))
            assertFalse("noise seed $seed gave $result", result.isPitched)
        }
        assertEquals(PitchResult.NONE, yin.detect(FloatArray(4096)))
    }

    @Test
    fun `notes below the detection range are not reported`() {
        assertFalse(yin.detect(TestSignals.sine(40f)).isPitched)
        // Just below 62 Hz the dip is still falling at the longest lag: not a note in range.
        for (f in listOf(55f, 60f, 61f)) assertFalse("$f Hz", yin.detect(TestSignals.sine(f)).isPitched)
        assertFalse("60 Hz hum", yin.detect(TestSignals.hum(60f)).isPitched)
        assertWithinCents("drop C", 65.41f, yin.detect(TestSignals.weakFundamental(65.41f)), ACCURACY_CENTS)
    }

    @Test
    fun `tones above the detection range are not reported as a lower note`() {
        // They used to read as the first multiple of their period in range: 2 kHz as 1000 Hz,
        // 3 kHz as 1500 Hz, 5 kHz as 1250 Hz. A whistle, a beep or a tick's ring is not a note.
        for (f in listOf(1600f, 2000f, 2500f, 3000f, 3500f, 5000f)) {
            assertFalse("sine $f Hz", yin.detect(TestSignals.sine(f)).isPitched)
            assertFalse("sine $f Hz, 20 dB SNR", yin.detect(TestSignals.withNoise(TestSignals.sine(f), 20f, seed = 5)).isPitched)
            val bright = TestSignals.harmonics(f, floatArrayOf(1f, 0.5f, 0.3f), maxHarmonicHz = 20_000f)
            assertFalse("bright tone $f Hz", yin.detect(bright).isPitched)
        }
        // The top of the range still reads.
        for (f in listOf(1318.5f, 1450f, 1500f)) {
            assertWithinCents("sine $f Hz", f, yin.detect(TestSignals.sine(f)), ACCURACY_CENTS)
            assertWithinCents("weak fundamental $f Hz", f, yin.detect(TestSignals.weakFundamental(f)), ACCURACY_CENTS)
        }
    }

    @Test
    fun `detect leaves the caller's frame untouched`() {
        val frame = TestSignals.sawtooth(220f)
        val copy = frame.copyOf()
        yin.detect(frame)
        assertArrayEquals(copy, frame, 0f)
    }

    @Test
    fun `detect does not allocate`() {
        val frame = TestSignals.pluck(110f)
        repeat(WARM_UP) { yin.detect(frame) }
        var result = PitchResult.NONE
        val bytes = allocatedBytes { repeat(100) { result = yin.detect(frame) } }
        assertTrue(result.isPitched)
        assertEquals("bytes allocated by 100 detect() calls", 0L, bytes)
    }

    @Test
    fun `full chain from chunks to pitch follows a plucked string`() {
        val f = mapper.frequencyOf(Tunings.STANDARD.strings[1]) // A2, 110 Hz
        val silence = FloatArray(9_600)
        val note = TestSignals.withNoise(TestSignals.pluck(f, length = 72_000, decaySeconds = 0.8), 30f)
        val stream = silence + note + silence

        val assembler = FrameAssembler()
        val gate = SignalGate()
        val window = FloatArray(assembler.windowSize)
        var framesInNote = 0
        var position = 0
        for (start in stream.indices step 480) { // 10 ms chunks, as AudioRecord might deliver them
            val chunk = stream.copyOfRange(start, minOf(start + 480, stream.size))
            var pos = 0
            while (pos < chunk.size) {
                val consumed = assembler.push(chunk, pos, chunk.size - pos)
                pos += consumed
                position += consumed
                if (!assembler.isFrameReady) continue
                assembler.readFrame(window)
                val frameStart = position - window.size
                val fullyInNote = frameStart >= silence.size && position <= silence.size + note.size
                if (!gate.process(window)) {
                    assertFalse("gate closed mid-note at $position", fullyInNote)
                    continue
                }
                val result = yin.detect(window)
                if (fullyInNote) {
                    framesInNote++
                    assertWithinCents("frame ending at $position", f, result, ACCURACY_CENTS)
                } else if (result.isPitched) {
                    // Frames straddling the attack or the end may be rough, but never the wrong note.
                    assertWithinCents("edge frame ending at $position", f, result, 50f)
                }
            }
        }
        assertTrue("analysed $framesInNote frames", framesInNote > 60)
    }

    @Test
    fun `invalid settings and frames are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { YinPitchDetector(windowSize = 1024) }
        assertThrows(IllegalArgumentException::class.java) { YinPitchDetector(minFrequencyHz = 2000f) }
        assertThrows(IllegalArgumentException::class.java) { YinPitchDetector(threshold = 0f) }
        assertThrows(IllegalArgumentException::class.java) { YinPitchDetector(lowPassHz = 2000f) }
        assertThrows(IllegalArgumentException::class.java) { yin.detect(FloatArray(4095)) }
    }

    @Test
    fun `pitch result packs frequency and confidence without loss`() {
        val result = PitchResult.of(82.4069f, 0.987f)
        assertEquals(82.4069f, result.frequencyHz)
        assertEquals(0.987f, result.confidence)
        assertTrue(result.isPitched)
        assertFalse(PitchResult.NONE.isPitched)
    }

    // [count] frequencies spaced evenly in pitch from 62 to 1500 Hz, both ends included.
    private fun sweep(count: Int): List<Float> =
        (0 until count).map { (MIN_HZ * (MAX_HZ / MIN_HZ).pow(it / (count - 1.0))).toFloat() }

    private fun assertWithinCents(message: String, expectedHz: Float, result: PitchResult, toleranceCents: Float) {
        assertTrue("$message: no pitch found", result.isPitched)
        val error = NoteMapper.centsBetween(result.frequencyHz, expectedHz)
        assertTrue("$message: off by $error cents (got ${result.frequencyHz} Hz)", abs(error) <= toleranceCents)
    }

    private companion object {
        const val ACCURACY_CENTS = 1f
        const val MIN_HZ = 62.0
        const val MAX_HZ = 1500.0
        const val WARM_UP = 300
    }
}
