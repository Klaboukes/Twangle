package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.smoothing.PitchSmoother
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Timing check for the per-frame DSP work: under 2 ms per frame on the JVM (4096 samples, or 8192
 * in the low-note range).
 *
 * Not part of `test` (timings depend on the machine). Run with `./gradlew :core:dsp:benchmark`.
 * Each case is warmed up first so the JIT has compiled the hot loop, then timed frame by frame.
 * The median is reported because it ignores the odd GC or scheduler hiccup.
 */
class YinBenchmark {

    private val frames = listOf(
        TestSignals.sine(82.41f),
        TestSignals.weakFundamental(110f),
        TestSignals.withNoise(TestSignals.pluck(329.6f), snrDb = 20f),
        TestSignals.sawtooth(1318.5f),
        TestSignals.noise(rms = 0.1f),
    )

    @Test
    fun `YIN detect takes under 2 ms per frame`() {
        val yin = YinPitchDetector()
        val stats = time { i -> yin.detect(frames[i % frames.size]).isPitched }
        report("YinPitchDetector.detect", stats)
        assertTrue("median ${stats.medianMs} ms", stats.medianMs < BUDGET_MS)
    }

    @Test
    fun `low-note range (8192 samples) takes under 2 ms per frame`() {
        val settings = AnalysisSettings.forSampleRate(48_000, PitchRange.LOW)
        val detector = settings.newPitchDetector()
        val n = settings.windowSize
        val low = listOf(
            TestSignals.weakFundamental(30.87f, n),
            TestSignals.pluck(41.2f, n),
            TestSignals.withNoise(TestSignals.sawtooth(61.74f, n), snrDb = 20f),
            TestSignals.sine(329.6f, n),
            TestSignals.noise(n, rms = 0.1f),
        )
        val stats = time { i -> detector.detect(low[i % low.size]).isPitched }
        report("low range detect (8192)", stats)
        assertTrue("median ${stats.medianMs} ms", stats.medianMs < BUDGET_MS)
    }

    @Test
    fun `8 kHz headset audio, upsampled to 32 kHz, takes under 2 ms per frame`() {
        val settings = AnalysisSettings.forSampleRate(8_000)
        val detector = settings.newPitchDetector()
        val narrowband = listOf(82.41f, 110f, 329.6f, 987.8f).map { TestSignals.pluck(it, settings.windowSize, 8_000) }
        val stats = time { i -> detector.detect(narrowband[i % narrowband.size]).isPitched }
        report("8 kHz upsampled detect", stats)
        assertTrue("median ${stats.medianMs} ms", stats.medianMs < BUDGET_MS)
    }

    @Test
    fun `whole per-frame chain takes under 2 ms per frame`() {
        val yin = YinPitchDetector()
        val gate = SignalGate()
        val mapper = NoteMapper()
        val smoother = PitchSmoother()
        val window = FloatArray(yin.windowSize)
        val stats = time { i ->
            frames[i % frames.size].copyInto(window)
            if (gate.process(window)) {
                val result = yin.detect(window)
                if (result.isPitched) smoother.update(mapper.midiOf(result.frequencyHz), result.confidence) else smoother.noReading()
            } else {
                smoother.noReading()
            }
            smoother.hasReading
        }
        report("gate + detect + smoother", stats)
        assertTrue("median ${stats.medianMs} ms", stats.medianMs < BUDGET_MS)
    }

    private class Stats(val medianMs: Double, val p95Ms: Double, val minMs: Double)

    // Runs [frame] WARM_UP times untimed, then MEASURED times individually timed.
    private inline fun time(frame: (Int) -> Boolean): Stats {
        var sink = 0
        repeat(WARM_UP) { if (frame(it)) sink++ }
        val nanos = LongArray(MEASURED)
        for (i in 0 until MEASURED) {
            val start = System.nanoTime()
            if (frame(i)) sink++
            nanos[i] = System.nanoTime() - start
        }
        check(sink >= 0) // Keeps the JIT from discarding the work.
        nanos.sort()
        return Stats(
            medianMs = nanos[MEASURED / 2] / 1e6,
            p95Ms = nanos[MEASURED * 95 / 100] / 1e6,
            minMs = nanos[0] / 1e6,
        )
    }

    private fun report(name: String, stats: Stats) {
        println(
            "BENCHMARK %-26s median %.3f ms · p95 %.3f ms · min %.3f ms (budget %.1f ms, %s)".format(
                name, stats.medianMs, stats.p95Ms, stats.minMs, BUDGET_MS, System.getProperty("java.vm.version"),
            ),
        )
    }

    private companion object {
        const val BUDGET_MS = 2.0
        const val WARM_UP = 3_000
        const val MEASURED = 1_000
    }
}
