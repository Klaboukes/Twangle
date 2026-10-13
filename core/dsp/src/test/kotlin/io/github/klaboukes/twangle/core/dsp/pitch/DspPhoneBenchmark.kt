package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.smoothing.PitchSmoother
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import java.io.File

/**
 * CPU time per frame of the pitch detector on a phone, for the standard (4096-sample) and low-note
 * (8192-sample) windows at 48 kHz. `scripts/dsp-phone-benchmark.sh` dexes `:core:dsp` with this
 * class and runs it with `dalvikvm` on the connected phone: the same ART runtime and JIT as the app,
 * without a debuggable build's slowdown. Plain `main`, so it also runs on the JVM.
 *
 * Per case: [WARM_UP] untimed frames so the JIT compiles the hot loop, then [MEASURED] frames.
 * Reports the median wall time per frame and the thread's CPU time per frame, from
 * `/proc/thread-self/schedstat` where the kernel has it (NaN elsewhere, e.g. on Windows).
 */
object DspPhoneBenchmark {

    private const val WARM_UP = 2_000
    private const val MEASURED = 1_000

    @JvmStatic
    fun main(args: Array<String>) {
        println("DSP benchmark on ${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}")
        for (range in PitchRange.entries) {
            val settings = AnalysisSettings.forSampleRate(48_000, range)
            val n = settings.windowSize
            val frames = listOf(
                TestSignals.weakFundamental(if (range == PitchRange.LOW) 30.87f else 82.41f, n),
                TestSignals.pluck(if (range == PitchRange.LOW) 41.2f else 110f, n),
                TestSignals.withNoise(TestSignals.sawtooth(if (range == PitchRange.LOW) 61.74f else 146.8f, n), snrDb = 20f),
                TestSignals.sine(329.6f, n),
                TestSignals.noise(n, rms = 0.1f),
            )
            val detector = settings.newPitchDetector()
            report("$range detect ($n)", time { i -> detector.detect(frames[i % frames.size]).isPitched })

            val gate = SignalGate()
            val mapper = NoteMapper()
            val smoother = PitchSmoother()
            val window = FloatArray(n)
            report(
                "$range gate+detect+smoother",
                time { i ->
                    frames[i % frames.size].copyInto(window)
                    val result = if (gate.process(window)) detector.detect(window) else PitchResult.NONE
                    if (result.isPitched) smoother.update(mapper.midiOf(result.frequencyHz), result.confidence) else smoother.noReading()
                    smoother.hasReading
                },
            )
        }
    }

    private class Stats(val medianMs: Double, val p95Ms: Double, val cpuMsPerFrame: Double)

    private inline fun time(frame: (Int) -> Boolean): Stats {
        var sink = 0
        repeat(WARM_UP) { if (frame(it)) sink++ }
        val nanos = LongArray(MEASURED)
        val cpuBefore = threadCpuNanos()
        for (i in 0 until MEASURED) {
            val start = System.nanoTime()
            if (frame(i)) sink++
            nanos[i] = System.nanoTime() - start
        }
        val cpu = (threadCpuNanos() - cpuBefore) / MEASURED / 1e6
        check(sink >= 0) // Keeps the JIT from discarding the work.
        nanos.sort()
        return Stats(nanos[MEASURED / 2] / 1e6, nanos[MEASURED * 95 / 100] / 1e6, cpu)
    }

    // First field of schedstat: nanoseconds this thread has run on a CPU.
    private fun threadCpuNanos(): Double = try {
        File("/proc/thread-self/schedstat").readText().trim().split(' ')[0].toDouble()
    } catch (e: Exception) {
        Double.NaN
    }

    private fun report(name: String, stats: Stats) {
        println(
            "BENCHMARK %-30s median %.3f ms · p95 %.3f ms · CPU %.3f ms per frame".format(
                name, stats.medianMs, stats.p95Ms, stats.cpuMsPerFrame,
            ),
        )
    }
}
