package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClarityTest {

    @Test
    fun `zero at YIN's threshold and without a pitch, one for a 99 percent periodic tone`() {
        assertEquals(0f, Clarity.of(PitchResult.NONE.confidence))
        assertEquals(0f, Clarity.of(-1f))
        assertEquals(0f, Clarity.of(1f - YinPitchDetector.DEFAULT_THRESHOLD), 1e-5f)
        assertEquals(0f, Clarity.of(0.5f))
        assertEquals(1f, Clarity.of(0.99f), 1e-5f)
        assertEquals(1f, Clarity.of(0.999f))
        assertEquals(1f, Clarity.of(1f))
        assertEquals(7.53f, Clarity.UNCLEAR_DB, 0.01f)
        assertEquals(19.96f, Clarity.CLEAR_DB, 0.01f)
    }

    @Test
    fun `rises steadily with confidence between the two ends`() {
        var last = -1f
        for (i in 0..1000) {
            val clarity = Clarity.of(0.85f + 0.14f * i / 1000)
            assertTrue("not rising at step $i", clarity >= last)
            last = clarity
        }
        // Half way in dB: 10^(13.74 / 10) = 23.7, so c = 23.7 / 24.7.
        assertEquals(0.5f, Clarity.of(0.9595f), 0.01f)
    }

    @Test
    fun `clean notes are fully clear`() {
        val yin = YinPitchDetector()
        for (f in GUITAR_RANGE) for ((name, tone) in tones(f)) {
            val result = yin.detect(tone)
            assertTrue("$name at $f Hz: no pitch", result.isPitched)
            assertTrue("$name at $f Hz: clarity ${Clarity.of(result.confidence)}", Clarity.of(result.confidence) >= 0.95f)
        }
    }

    @Test
    fun `clarity falls as noise rises, the same for every pitch and timbre`() {
        // White-noise SNR (dB) → the clarity range every pitched frame must fall in. Measured:
        // 20 dB → 1, 10 dB → 0.74…0.88, 6 dB → 0.42…0.55, 3 dB → 0.18…0.32, 0 dB → 0…0.09.
        val expected = mapOf(
            20f to 0.95f..1f,
            10f to 0.7f..0.92f,
            6f to 0.38f..0.6f,
            3f to 0.14f..0.36f,
            0f to 0f..0.13f,
        )
        val yin = YinPitchDetector()
        for (f in GUITAR_RANGE) for ((name, tone) in tones(f)) for ((snr, range) in expected) for (seed in 1L..4L) {
            val result = yin.detect(TestSignals.withNoise(tone, snr, seed))
            if (!result.isPitched) {
                assertTrue("$name at $f Hz, $snr dB: no pitch", snr <= 0f)
                continue
            }
            val clarity = Clarity.of(result.confidence)
            assertTrue("$name at $f Hz, $snr dB, seed $seed: clarity $clarity not in $range", clarity in range)
        }
    }

    @Test
    fun `of allocates nothing`() {
        var sum = 0f
        repeat(1000) { sum += Clarity.of(it / 1000f) }
        val bytes = allocatedBytes {
            for (i in 0 until 10_000) sum += Clarity.of(i / 10_000f)
        }
        assertEquals(0L, bytes)
        assertTrue(sum > 0f)
    }

    private fun tones(f: Float) = mapOf(
        "sine" to TestSignals.sine(f),
        "sawtooth" to TestSignals.sawtooth(f),
        "weak fundamental" to TestSignals.weakFundamental(f),
        "pluck" to TestSignals.pluck(f),
    )

    private companion object {
        val GUITAR_RANGE = listOf(73.42f, 82.41f, 110f, 196f, 329.63f, 659.26f, 1046.5f)
    }
}
