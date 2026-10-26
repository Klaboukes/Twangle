package io.github.klaboukes.twangle.core.dsp.drone

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Timing check for the drone ([DroneSynth]): the CPU one 10 ms chunk (480 samples at 48 kHz, what
 * a player writes at a time) costs, for every sound in its dearest case (the root, fifth and octave,
 * an octave lower, so the most partials), on the JVM. Budget: under [BUDGET_MS] per chunk, a tenth
 * of the chunk's own 10 ms; on the phone `scripts/dsp-phone-benchmark.sh` times the same.
 *
 * Not part of `test` (timings depend on the machine). Run with `./gradlew :core:dsp:benchmark`.
 */
class DroneSynthBenchmark {

    @Test
    fun `a 10 ms chunk of every sound renders in under a tenth of its time`() {
        val failures = mutableListOf<String>()
        for (sound in DroneSound.entries) {
            for (lower in listOf(false, true)) {
                val ms = msPerChunk(sound, lower)
                println("BENCHMARK drone %-12s fifth+octave%s  %.3f ms per 10 ms chunk (%.1f %% of real time)".format(sound, if (lower) ", lower" else "       ", ms, ms * 10))
                if (ms > BUDGET_MS) failures += "$sound lower=$lower: $ms ms"
            }
        }
        assertTrue(failures.joinToString(), failures.isEmpty())
    }

    companion object {
        const val RATE = 48_000
        const val CHUNK = 480
        const val BUDGET_MS = 1.0

        /** The median time of one [CHUNK] of [sound] (root, fifth and octave), after warming up past the start (all six tanpura plucks ringing). */
        fun msPerChunk(sound: DroneSound, lower: Boolean, warmUpChunks: Int = 3_000, measuredChunks: Int = 2_000): Double {
            val synth = DroneSynth(RATE)
            synth.play(146.83f, sound, DroneVoicing.FIFTH_OCTAVE, lower)
            val out = FloatArray(CHUNK)
            var sink = 0f
            repeat(warmUpChunks) { synth.render(out); sink += out[0] }
            val nanos = LongArray(measuredChunks)
            for (i in nanos.indices) {
                val start = System.nanoTime()
                synth.render(out)
                nanos[i] = System.nanoTime() - start
                sink += out[0]
            }
            check(!sink.isNaN())
            nanos.sort()
            return nanos[measuredChunks / 2] / 1e6
        }
    }
}
