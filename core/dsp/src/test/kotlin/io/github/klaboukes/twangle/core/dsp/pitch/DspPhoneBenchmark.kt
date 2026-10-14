package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.smoothing.PitchSmoother
import io.github.klaboukes.twangle.core.dsp.strum.StrumDetector
import io.github.klaboukes.twangle.core.dsp.strum.StrumResult
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import java.io.File

/**
 * CPU time per frame of the pitch detector on a phone, for the standard (4096-sample) and low-note
 * (8192-sample) windows at 48 kHz, and of the strum detector (16 384 / 32 768 samples). `scripts/dsp-phone-benchmark.sh` dexes `:core:dsp` with this
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
    private const val STRUM_WARM_UP = 300
    private const val STRUM_MEASURED = 100

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

        // Strum check (Phase 18 spike): every string of one strum in one long frame. Fewer frames,
        // since each takes milliseconds.
        for (range in PitchRange.entries) {
            val n = StrumDetector.frameLengthFor(48_000, range)
            val strings = if (range == PitchRange.LOW) {
                floatArrayOf(30.87f, 41.2f, 55f, 73.42f, 98f)
            } else {
                floatArrayOf(82.41f, 110f, 146.83f, 196f, 246.94f, 329.63f)
            }
            val strum = FloatArray(n)
            for (f in strings) TestSignals.pluck(f, n, amplitude = 0.1f).forEachIndexed { i, x -> strum[i] += x }
            val detector = StrumDetector(48_000, n)
            val result = StrumResult(StrumDetector.MAX_STRINGS)
            report(
                "$range strum detect ($n)",
                time(STRUM_WARM_UP, STRUM_MEASURED) { _ ->
                    detector.detect(strum, strings, strings.size, result)
                    result.present[0]
                },
            )
        }
    }

    private class Stats(val medianMs: Double, val p95Ms: Double, val cpuMsPerFrame: Double)

    private inline fun time(warmUp: Int = WARM_UP, measured: Int = MEASURED, frame: (Int) -> Boolean): Stats {
        var sink = 0
        repeat(warmUp) { if (frame(it)) sink++ }
        val nanos = LongArray(measured)
        val cpuBefore = threadCpuNanos()
        for (i in 0 until measured) {
            val start = System.nanoTime()
            if (frame(i)) sink++
            nanos[i] = System.nanoTime() - start
        }
        val cpu = (threadCpuNanos() - cpuBefore) / measured / 1e6
        check(sink >= 0) // Keeps the JIT from discarding the work.
        nanos.sort()
        return Stats(nanos[measured / 2] / 1e6, nanos[measured * 95 / 100] / 1e6, cpu)
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
