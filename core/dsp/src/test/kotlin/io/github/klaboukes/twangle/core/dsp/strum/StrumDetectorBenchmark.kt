package io.github.klaboukes.twangle.core.dsp.strum

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Time per [StrumDetector.detect] on the JVM: standard tuning with the 16 384-sample frame, and a
 * five-string bass with the 32 768-sample one. Phone figures come from
 * `scripts/dsp-phone-benchmark.sh`. Not part of `test`; run with `./gradlew :core:dsp:benchmark`.
 */
class StrumDetectorBenchmark {

    @Test
    fun `standard tuning, 341 ms frame`() = measure(StrumChords.expectedHz(Tunings.STANDARD), PitchRange.STANDARD)

    @Test
    fun `five-string bass, 683 ms frame`() = measure(StrumChords.expectedHz(Instruments.BASS_5.defaultTuning), PitchRange.LOW)

    private fun measure(expected: FloatArray, range: PitchRange) {
        val n = StrumDetector.frameLengthFor(StrumChords.SAMPLE_RATE, range)
        val strum = StrumChords.pluckSynthStrum(StrumChords.detuned(expected, FloatArray(expected.size) { 7f * it - 15f }), n)
        val detector = StrumDetector(StrumChords.SAMPLE_RATE, n)
        val result = StrumResult(StrumDetector.MAX_STRINGS)
        var sink = 0
        repeat(WARM_UP) { detector.detect(strum, expected, expected.size, result); if (result.present[0]) sink++ }
        val nanos = LongArray(MEASURED)
        for (i in 0 until MEASURED) {
            val start = System.nanoTime()
            detector.detect(strum, expected, expected.size, result)
            nanos[i] = System.nanoTime() - start
            if (result.present[0]) sink++
        }
        check(sink >= 0) // Keeps the JIT from discarding the work.
        nanos.sort()
        val median = nanos[MEASURED / 2] / 1e6
        println(
            "BENCHMARK strum detect %-8s (%d samples) median %.2f ms · p95 %.2f ms (%s)".format(
                range, n, median, nanos[MEASURED * 95 / 100] / 1e6, System.getProperty("java.vm.version"),
            ),
        )
        assertTrue("median $median ms", median < BUDGET_MS * range.windowMultiple)
    }

    private companion object {
        // A strum check reads a few frames a second, not 47: 10 ms per frame is 5 % of a core at 5/s.
        const val BUDGET_MS = 10.0
        const val WARM_UP = 300
        const val MEASURED = 200
    }
}
