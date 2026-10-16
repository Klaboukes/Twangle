package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.strum.StrumChords
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Test
import java.util.Random

/**
 * How often [ChordDetector] names synthetic strums right, not at all, or wrongly, and how often it
 * names sounds that are no chord: the numbers behind docs/architecture/strum-and-metronome.md,
 * *Chord naming*. Prints only, asserts nothing (that is [ChordDetectorTest]'s job).
 * Not part of `test`; run with `./gradlew :core:dsp:benchmark`.
 */
class ChordAccuracyReport {

    private val detector = ChordDetector(StrumChords.SAMPLE_RATE)
    private val n = ChordShapes.frameLength
    private val shapes = ChordShapes.OPEN + ChordShapes.BARRE

    private fun describe(r: ChordResult): String {
        val notes = (0 until r.noteCount).joinToString(" ") { "${Note(r.noteMidi[it])}:${"%.2f".format(r.noteStrength[it])}" }
        return "${r.chord} bass=${r.bass} offset=${"%.1f".format(r.tuningOffsetCents)} crowded=${r.crowded} | $notes"
    }

    @Test
    fun shapes() {
        val random = Random(3)
        val weak = TestSignals.WEAK_FUNDAMENTAL_HARMONICS
        println("=== ${shapes.size} shapes (${ChordShapes.OPEN.size} open, ${ChordShapes.BARRE.size} barre): right / no name / wrong")
        val kinds = linkedMapOf<String, (IntArray) -> FloatArray>(
            "as played" to { m -> ChordShapes.strum(m) },
            "±10 cents, gains 0.5-1, 5-30 ms apart (x10)" to { m ->
                ChordShapes.strum(
                    m, cents = FloatArray(m.size) { random.nextFloat() * 20 - 10 },
                    gains = FloatArray(m.size) { 0.5f + 0.5f * random.nextFloat() }, spreadSeconds = 0.005 + 0.025 * random.nextDouble(),
                )
            },
            "±10 cents, gains 0.3-1 (x10)" to { m ->
                ChordShapes.strum(m, cents = FloatArray(m.size) { random.nextFloat() * 20 - 10 }, gains = FloatArray(m.size) { 0.3f + 0.7f * random.nextFloat() })
            },
            "±10 cents, gains 0.2-1 (x10)" to { m ->
                ChordShapes.strum(m, cents = FloatArray(m.size) { random.nextFloat() * 20 - 10 }, gains = FloatArray(m.size) { 0.2f + 0.8f * random.nextFloat() })
            },
            "stiff strings (B 1e-4)" to { m -> StrumChords.stiffStrum(ChordShapes.hz(m), n, DoubleArray(m.size) { 1e-4 }) },
            "weak fundamentals below G3" to { m ->
                StrumChords.stiffStrum(ChordShapes.hz(m), n, DoubleArray(m.size) { 5e-5 }, harmonicGain = { i, k -> if (m[i] < 55 && k <= weak.size) weak[k - 1].toDouble() else 1.0 / k })
            },
            "20 dB SNR" to { m -> TestSignals.withNoise(ChordShapes.strum(m), 20f) },
            "10 dB SNR" to { m -> TestSignals.withNoise(ChordShapes.strum(m), 10f) },
            "tuned to A4 = 432" to { m -> ChordShapes.strum(m, a4Hz = 432f) },
            "upstroke" to { m -> ChordShapes.strum(m.reversedArray()) },
            "frame from the strum's start" to { m -> ChordShapes.strum(m, fromSeconds = 0.0) },
            "frame from 1 s" to { m -> ChordShapes.strum(m, fromSeconds = 1.0) },
            "frame from 2 s" to { m -> ChordShapes.strum(m, fromSeconds = 2.0) },
        )
        for ((kind, make) in kinds) {
            var right = 0
            var none = 0
            var wrong = 0
            val reps = if ("x10" in kind) 10 else 1
            for (shape in shapes) repeat(reps) {
                val r = ChordResult()
                val chord = detector.detect(make(shape.midi), r)
                when {
                    chord === shape.chord -> right++
                    chord == null -> { none++; println("    no name: $shape ${describe(r)}") }
                    else -> { wrong++; println("    WRONG: $shape ${describe(r)}") }
                }
            }
            println("  $kind: $right / $none / $wrong")
        }
    }

    @Test
    fun noChord() {
        val random = Random(5)
        var named = 0
        var total = 0
        fun check(label: String, signal: FloatArray) {
            val r = ChordResult()
            total++
            if (detector.detect(signal, r) != null) {
                named++
                println("    named $label: ${describe(r)}")
            }
        }
        println("=== Sounds that are no chord: how many got a name")
        for (m in 40..76) check("single $m", ChordShapes.strum(intArrayOf(m)))
        for (m in 40..57) check("power chord $m", ChordShapes.strum(intArrayOf(m, m + 7, m + 12)))
        for (m in 40..57) check("fifth $m", ChordShapes.strum(intArrayOf(m, m + 7)))
        for (m in 40..60) check("third $m", ChordShapes.strum(intArrayOf(m, m + 4)))
        for (m in 40..60) check("minor third + octave $m", ChordShapes.strum(intArrayOf(m, m + 3, m + 12)))
        println("  single notes, fifths, power chords, thirds: $named of $total")
        val before = named
        val beforeTotal = total
        repeat(100) {
            val a = shapes[random.nextInt(shapes.size)]
            val b = shapes[random.nextInt(shapes.size)]
            val tones = ChordNamer.maskOf(a.chord) or ChordNamer.maskOf(b.chord)
            if (ChordNamer.name(tones, a.midi[0] % 12) != null) return@repeat
            check("$a + $b", ChordShapes.strum((a.midi + b.midi).sortedArray()))
        }
        repeat(100) {
            val a = shapes[random.nextInt(shapes.size)]
            var extra: Int
            do extra = 48 + random.nextInt(24) while (ChordNamer.maskOf(a.chord) and (1 shl (extra % 12)) != 0)
            if (ChordNamer.name(ChordNamer.maskOf(a.chord) or (1 shl (extra % 12)), a.midi[0] % 12) != null) return@repeat
            check("$a + ${Note(extra)}", ChordShapes.strum((a.midi + extra).sortedArray()))
        }
        repeat(100) {
            val count = 3 + random.nextInt(4)
            val midi = IntArray(count) { 40 + random.nextInt(37) }.sortedArray()
            var tones = 0
            for (m in midi) tones = tones or (1 shl (m % 12))
            if (ChordNamer.name(tones, midi[0] % 12) != null) return@repeat
            check("cluster ${midi.map { Note(it) }}", ChordShapes.strum(midi, cents = FloatArray(count) { random.nextFloat() * 20 - 10 }))
        }
        println("  two chords at once, a chord with a wrong note, random clusters: ${named - before} of ${total - beforeTotal}")
        val beforeNoise = named
        val beforeNoiseTotal = total
        for (rms in floatArrayOf(0.0003f, 0.001f, 0.01f, 0.1f, 0.3f)) for (seed in 1L..20L) check("noise $rms", TestSignals.noise(n, rms, seed))
        println("  white noise: ${named - beforeNoise} of ${total - beforeNoiseTotal}")
    }

    @Test
    fun time() {
        val signal = ChordShapes.strum(ChordShapes.OPEN.first { it.name == "G" }.midi)
        val noise = TestSignals.noise(n, 0.1f)
        val r = ChordResult()
        for (sound in listOf("G" to signal, "noise" to noise)) {
            repeat(200) { detector.detect(sound.second, r) }
            val start = System.nanoTime()
            val runs = 500
            repeat(runs) { detector.detect(sound.second, r) }
            println("=== One detection (${sound.first}): ${"%.2f".format((System.nanoTime() - start) / 1e6 / runs)} ms")
        }
    }
}
