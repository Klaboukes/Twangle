package io.github.klaboukes.twangle.core.dsp.strum

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sqrt

/**
 * Phase 18 spike: the strum detector on synthetic strums of [PluckSynth][io.github.klaboukes.twangle.core.dsp.synth.PluckSynth]
 * strings, at the default 341 ms frame. Limits are set a little above what was measured (see
 * [StrumAccuracyReport] and ARCHITECTURE.md, *Strum check*): strings with a clear partial within
 * [CLEAR_CENTS], shadowed ones (B3 and E4 in standard tuning, under E2's 3rd and 4th harmonic)
 * within [SHADOWED_CENTS].
 */
class StrumDetectorTest {

    private val rate = StrumChords.SAMPLE_RATE
    private val n = StrumDetector.frameLengthFor(rate)
    private val detector = StrumDetector(rate, n)
    private val standard = StrumChords.expectedHz(Tunings.STANDARD)

    private fun detect(signal: FloatArray, expected: FloatArray = standard, with: StrumDetector = detector): StrumResult =
        StrumResult(StrumDetector.MAX_STRINGS).also { with.detect(signal, expected, expected.size, it) }

    private fun assertReadings(
        result: StrumResult,
        trueCents: FloatArray,
        label: String,
        skip: Int = -1,
        clearCents: Float = CLEAR_CENTS,
    ) {
        for (s in 0 until result.stringCount) {
            if (s == skip) continue
            assertTrue("$label: string $s not heard", result.present[s])
            val limit = if (result.shadowed[s]) SHADOWED_CENTS else clearCents
            val error = abs(result.cents[s] - trueCents[s])
            assertTrue("$label: string $s reads ${result.cents[s]}, true ${trueCents[s]}", error <= limit)
        }
    }

    @Test
    fun `default frame is 16384 samples at 48 kHz, twice that for low instruments`() {
        assertEquals(16_384, StrumDetector.frameLengthFor(48_000))
        assertEquals(32_768, StrumDetector.frameLengthFor(48_000, PitchRange.LOW))
        assertEquals(15_053, StrumDetector.frameLengthFor(44_100))
    }

    @Test
    fun `all six strings in tune`() {
        val result = detect(StrumChords.pluckSynthStrum(standard, n))
        assertEquals(6, result.stringCount)
        assertReadings(result, FloatArray(6), "in tune")
        for (s in 0 until 4) assertFalse("string $s has clear partials", result.shadowed[s])
        // B3 ≈ 3 × E2 and E4 = 4 × E2: every partial of theirs sits on one of E2's.
        assertTrue(result.shadowed[4])
        assertTrue(result.shadowed[5])
        for (s in 0 until 6) assertTrue("level of string $s", result.levelDb[s] in -12f..0f)
    }

    @Test
    fun `one string off by 10 to 30 cents`() {
        for (off in floatArrayOf(-30f, -20f, -10f, 10f, 20f, 30f)) {
            for (s in 0 until 6) {
                val cents = FloatArray(6).also { it[s] = off }
                assertReadings(detect(StrumChords.pluckSynthStrum(StrumChords.detuned(standard, cents), n)), cents, "string $s at $off")
            }
        }
    }

    @Test
    fun `every string off by up to 30 cents`() {
        val random = Random(11)
        repeat(30) {
            val cents = FloatArray(6) { (random.nextDouble() * 60 - 30).toFloat() }
            assertReadings(detect(StrumChords.pluckSynthStrum(StrumChords.detuned(standard, cents), n)), cents, cents.contentToString())
        }
    }

    @Test
    fun `a missing string is not heard and the others still read right`() {
        for (missing in 0 until 6) {
            val gains = FloatArray(6) { 1f }.also { it[missing] = 0f }
            val result = detect(StrumChords.pluckSynthStrum(standard, n, gains = gains))
            assertFalse("missing string $missing heard", result.present[missing])
            assertTrue(result.cents[missing].isNaN())
            assertReadings(result, FloatArray(6), "without string $missing", skip = missing)
        }
    }

    @Test
    fun `noise, a late frame and uneven strings`() {
        val zero = FloatArray(6)
        assertReadings(detect(TestSignals.withNoise(StrumChords.pluckSynthStrum(standard, n), 20f)), zero, "20 dB SNR")
        assertReadings(detect(StrumChords.pluckSynthStrum(standard, n, fromSeconds = 1.5)), zero, "1.5 s after the strum")
        assertReadings(
            detect(StrumChords.pluckSynthStrum(standard, n, gains = floatArrayOf(.3f, .3f, .5f, 1f, 1f, 1f))),
            zero, "soft bass strings",
        )
    }

    @Test
    fun `stiff strings with weak low fundamentals read their fundamental`() {
        val b = doubleArrayOf(1.5e-4, 1e-4, 6e-5, 4e-5, 3e-5, 2e-5)
        val weak = TestSignals.WEAK_FUNDAMENTAL_HARMONICS
        val signal = StrumChords.stiffStrum(
            standard, n, b,
            harmonicGain = { s, k -> if (s < 2 && k <= weak.size) weak[k - 1].toDouble() else 1.0 / k },
        )
        // Partial 1 of a stiff string sits at f·√(1 + B), a fraction of a cent above f. Partial k
        // is sharper still (≈ 866·B·(k² − 1) cents), so with the 2nd–4th partials carrying a weak
        // fundamental's string the reading comes out up to ≈ 0.6 cent sharp.
        val fundamental = FloatArray(6) { (1200 * log2(sqrt(1 + b[it]))).toFloat() }
        assertReadings(detect(signal), fundamental, "stiff strings", clearCents = 1f)
    }

    @Test
    fun `strings may be listed in any order`() {
        val reversed = standard.reversedArray()
        val result = detect(StrumChords.pluckSynthStrum(standard, n), reversed)
        val inOrder = detect(StrumChords.pluckSynthStrum(standard, n))
        for (s in 0 until 6) assertEquals(inOrder.cents[5 - s], result.cents[s], 1e-4f)
    }

    @Test
    fun `other instruments, with the long frame below the guitar`() {
        for (tuning in listOf(Tunings.DROP_D, Instruments.UKULELE.defaultTuning, Instruments.BASS_5.defaultTuning, Instruments.GUITAR_7.defaultTuning)) {
            val expected = StrumChords.expectedHz(tuning)
            val length = StrumDetector.frameLengthFor(rate, PitchRange.forLowestNote(expected.min()))
            val cents = FloatArray(expected.size) { if (it % 2 == 0) 15f else -20f }
            val result = detect(StrumChords.pluckSynthStrum(StrumChords.detuned(expected, cents), length), expected, StrumDetector(rate, length))
            assertReadings(result, cents, tuning.id)
        }
    }

    @Test
    fun `silence hears no string`() {
        val result = detect(FloatArray(n))
        for (s in 0 until 6) assertFalse(result.present[s])
    }

    @Test
    fun `detect allocates nothing`() {
        val signal = StrumChords.pluckSynthStrum(StrumChords.detuned(standard, floatArrayOf(5f, -10f, 0f, 20f, -15f, 8f)), n)
        val result = StrumResult(StrumDetector.MAX_STRINGS)
        repeat(20) { detector.detect(signal, standard, 6, result) } // Warm up the JIT.
        assertEquals(0L, allocatedBytes { detector.detect(signal, standard, 6, result) })
    }

    private companion object {
        const val CLEAR_CENTS = 0.5f
        const val SHADOWED_CENTS = 3f
    }
}
