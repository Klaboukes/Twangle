package io.github.klaboukes.twangle.core.dsp.metronome

import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Test
import kotlin.math.abs

/**
 * Print-only: what [OnsetTempoDetector] reports over time for click tracks, drum grooves and a
 * plucked bass line across 30–250 BPM, with noise, and how many it gets right, or at half or double
 * tempo (rock at 50 BPM with eighth-note hi-hats reads as 100, a fair hearing too). Not part of
 * `test`; run with `./gradlew :core:dsp:benchmark`.
 */
class TempoAccuracyReport {

    private class Case(val name: String, val bpm: Double, val signal: FloatArray)

    @Test
    fun report() {
        val cases = ArrayList<Case>()
        for (bpm in listOf(30.0, 36.0, 45.0, 52.0, 60.0, 75.0, 88.0, 97.3, 110.0, 120.0, 133.0, 150.0, 168.0, 185.0, 200.0, 222.0, 240.0, 250.0)) {
            cases += Case("clicks", bpm, TestSignals.withNoise(BeatSignals.clicks(bpm, SECONDS), snrDb = 10f))
        }
        for (bpm in listOf(60.0, 96.0, 120.0, 150.0, 200.0, 240.0)) {
            cases += Case("accented 4/4", bpm, TestSignals.withNoise(BeatSignals.clicks(bpm, SECONDS, accentEvery = 4), snrDb = 10f))
        }
        for (groove in BeatSignals.Groove.entries) {
            for (bpm in listOf(50.0, 70.0, 85.0, 96.0, 110.0, 120.0, 128.0, 140.0, 160.0, 175.0, 200.0)) {
                cases += Case(groove.name, bpm, TestSignals.withNoise(BeatSignals.drums(bpm, SECONDS, groove), snrDb = 15f))
            }
        }
        for (bpm in listOf(60.0, 80.0, 100.0, 125.0, 150.0)) {
            cases += Case("plucked bass", bpm, TestSignals.withNoise(BeatSignals.plucks(bpm, SECONDS), snrDb = 15f))
        }
        for (bpm in listOf(90.0, 130.0, 165.0)) {
            cases += Case("swing+loose", bpm, TestSignals.withNoise(BeatSignals.drums(bpm, SECONDS, swing = 2.0 / 3, jitterSeconds = 0.008), snrDb = 15f))
        }
        cases += Case("noise", 0.0, TestSignals.noise(SECONDS.toInt() * BeatSignals.SAMPLE_RATE, rms = 0.05f))

        var right = 0
        var octave = 0
        var worst = 0.0
        for (case in cases) {
            val detector = OnsetTempoDetector(BeatSignals.SAMPLE_RATE)
            val line = StringBuilder("%-18s %6.1f:".format(case.name, case.bpm))
            var nextMark = 4.0
            BeatSignals.feed(detector, case.signal) { samples ->
                val s = samples.toDouble() / BeatSignals.SAMPLE_RATE
                if (s >= nextMark) {
                    line.append(" %4.0fs %3d %-8s c%.2f alt %3d |".format(nextMark, detector.bpm, detector.status, detector.confidence, detector.alternativeBpm))
                    nextMark += 2.0
                }
            }
            line.append(" exact %.2f".format(detector.exactBpm))
            val error = abs(detector.exactBpm - case.bpm)
            when {
                case.bpm == 0.0 -> {}
                error <= 1.0 -> {
                    right++
                    worst = maxOf(worst, error)
                }
                abs(detector.exactBpm - case.bpm / 2) <= 1.0 || abs(detector.exactBpm - case.bpm * 2) <= 1.0 -> octave++
            }
            println(line)
        }
        val tempos = cases.count { it.bpm > 0 }
        println("$right of $tempos within ±1 BPM at 16 s (largest error among them %.2f BPM); $octave at half or double tempo".format(worst))
    }

    private companion object {
        const val SECONDS = 16.0
    }
}
