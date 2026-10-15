package io.github.klaboukes.twangle.core.dsp.metronome

import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Timing check for [OnsetTempoDetector]: the cost of one microphone chunk (1024 samples ≈ 21 ms at
 * 48 kHz, as the recorder delivers them), and of the chunks that also run an estimate (every
 * 0.25 s, about one chunk in twelve).
 *
 * Not part of `test` (timings depend on the machine). Run with `./gradlew :core:dsp:benchmark`.
 */
class TempoDetectorBenchmark {

    @Test
    fun `one microphone chunk costs well under a millisecond on average`() {
        val rate = BeatSignals.SAMPLE_RATE
        val music = TestSignals.withNoise(BeatSignals.drums(123.0, 30.0), snrDb = 15f)
        val detector = OnsetTempoDetector(rate)
        val chunks = music.size / CHUNK
        val nanos = LongArray(MEASURED)
        val estimating = BooleanArray(MEASURED)
        var sink = 0L
        // Warm up (the JIT compiles the hot loop), then time chunk by chunk, starting over at the end.
        repeat(WARM_UP) { i ->
            if (i % chunks == 0) detector.reset()
            detector.process(music, (i % chunks) * CHUNK, CHUNK)
        }
        detector.reset()
        for (i in 0 until MEASURED) {
            if (i % chunks == 0) detector.reset()
            val updatesBefore = detector.steadySeconds + detector.exactBpm
            val start = System.nanoTime()
            detector.process(music, (i % chunks) * CHUNK, CHUNK)
            nanos[i] = System.nanoTime() - start
            estimating[i] = detector.steadySeconds + detector.exactBpm != updatesBefore
            sink += detector.bpm
        }
        check(sink >= 0)
        val all = nanos.copyOf().also { it.sort() }
        val updates = nanos.filterIndexed { i, _ -> estimating[i] }.sorted()
        val meanMs = nanos.average() / 1e6
        println(
            "BENCHMARK tempo detector, 1024-sample chunk at 48 kHz: mean %.3f ms · median %.3f ms · p99 %.3f ms · max %.3f ms (%s)".format(
                meanMs, all[MEASURED / 2] / 1e6, all[MEASURED * 99 / 100] / 1e6, all.last() / 1e6, System.getProperty("java.vm.version"),
            ),
        )
        if (updates.isNotEmpty()) {
            println("BENCHMARK tempo detector, chunk with an estimate: median %.3f ms (%d of %d chunks)".format(updates[updates.size / 2] / 1e6, updates.size, MEASURED))
        }
        println("BENCHMARK tempo detector: %.1f %% of one core while listening".format(meanMs / (CHUNK * 1000.0 / rate) * 100))
        assertTrue("mean $meanMs ms", meanMs < BUDGET_MS)
    }

    private companion object {
        const val CHUNK = 1024
        const val WARM_UP = 20_000
        const val MEASURED = 5_000
        const val BUDGET_MS = 0.5
    }
}
