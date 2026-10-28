package io.github.klaboukes.twangle.core.dsp.drone

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.sqrt

class DroneSynthTest {

    private val rate = 48_000

    private fun hz(pitchClass: PitchClass, a4: Float = 440f) = NoteMapper(a4).frequencyOf(DroneSynth.rootNote(pitchClass))

    private fun seconds(s: Double) = (s * rate).toInt()

    /** One configuration of the drone. */
    private data class Drone(val rootHz: Float, val sound: DroneSound, val voicing: DroneVoicing, val lower: Boolean = false)

    private fun DroneSynth.play(d: Drone) = play(d.rootHz, d.sound, d.voicing, d.lower)

    private fun steady(d: Drone, seconds: Double, seed: Long = DroneSynth.DEFAULT_SEED): FloatArray {
        val synth = DroneSynth(rate, seed)
        synth.play(d)
        return DroneMeasures.render(synth, seconds)
    }

    @Test
    fun `a scale's root goes into the drone's octave, G sharp 2 to G3`() {
        assertEquals(Note.of(PitchClass.D, 3), DroneSynth.rootNote(PitchClass.D))
        assertEquals(Note.of(PitchClass.G_SHARP, 2), DroneSynth.rootNote(PitchClass.G_SHARP))
        assertEquals(Note.of(PitchClass.G, 3), DroneSynth.rootNote(PitchClass.G))
        assertEquals(Note.of(PitchClass.C, 3), DroneSynth.rootNote(PitchClass.C))
        assertEquals(Note.of(PitchClass.A, 2), DroneSynth.rootNote(PitchClass.A))
        assertEquals(146.83f, hz(PitchClass.D), 0.01f)
        for (pc in PitchClass.entries) assertEquals(pc, DroneSynth.rootNote(pc).pitchClass)
    }

    @Test
    fun `every sound and voicing is on pitch within a cent, lower or not, at any A4`() {
        val cases = buildList {
            for (sound in DroneSound.entries) for (voicing in DroneVoicing.entries) for (lower in listOf(false, true)) {
                add(Drone(hz(PitchClass.D), sound, voicing, lower))
            }
            for (sound in DroneSound.entries) {
                add(Drone(hz(PitchClass.G_SHARP, 415f), sound, DroneVoicing.FIFTH, lower = true))
                add(Drone(hz(PitchClass.G, 466f), sound, DroneVoicing.FIFTH_OCTAVE))
                add(Drone(hz(PitchClass.B, 432f), sound, DroneVoicing.ROOT))
            }
        }
        val failures = mutableListOf<String>()
        // Where the notes' common fundamental (what the detector reads of a chord) is below its range,
        // 28 Hz, it reads nothing; `lower` there only halves the root, which the octave test checks.
        val readable = cases.filter { commonFundamentalHz(it) >= 30.0 }
        assertTrue(readable.size > cases.size * 3 / 4)
        for (d in readable) {
            val x = steady(d, 5.0)
            val readings = DroneMeasures.readings(x, rate, d.rootHz.toDouble(), seconds(2.0) until x.size)
            val share = readings.size / 28.0
            val mean = readings.average()
            if (share < 0.8 || abs(mean) > 1.0) failures += "$d: ${readings.size} readings, mean ${"%+.2f".format(mean)} cents"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** The frequency all the drone's notes are harmonics of: the tanpura's low Sa (and Pa, 3:4) bring it down. */
    private fun commonFundamentalHz(d: Drone): Double {
        val root = if (d.lower) d.rootHz / 2.0 else d.rootHz.toDouble()
        val withFifth = d.voicing != DroneVoicing.ROOT
        return when {
            d.sound == DroneSound.TANPURA && withFifth -> root / 4
            d.sound == DroneSound.TANPURA || withFifth -> root / 2
            else -> root
        }
    }

    @Test
    fun `lower puts the drone an octave down`() {
        for (sound in listOf(DroneSound.FLUTE_ORGAN, DroneSound.HARMONIUM, DroneSound.WARM_PAD)) {
            val root = hz(PitchClass.E)
            val x = steady(Drone(root, sound, DroneVoicing.ROOT, lower = true), 4.0)
            val settings = AnalysisSettings.forSampleRate(rate, PitchRange.LOW)
            val window = x.copyOfRange(seconds(2.5), seconds(2.5) + settings.windowSize)
            val reading = settings.newPitchDetector(refineOnLowestPartials = false).detect(window)
            assertTrue("$sound unpitched", reading.isPitched)
            assertEquals("$sound", 0.0, 1200 * log2(reading.frequencyHz / (root / 2.0)), 1.0)
        }
    }

    @Test
    fun `no sound peaks above 0_8, whatever the root, voicing, octave or seed`() {
        var loudest = 0f
        var where = ""
        fun check(d: Drone, seconds: Double, seed: Long) {
            val p = DroneMeasures.peak(steady(d, seconds, seed))
            if (p > loudest) {
                loudest = p
                where = "$d seed $seed"
            }
        }
        for (sound in DroneSound.entries) for (voicing in DroneVoicing.entries) for (lower in listOf(false, true)) {
            for (pc in listOf(PitchClass.G_SHARP, PitchClass.C, PitchClass.D, PitchClass.G)) {
                check(Drone(hz(pc, 466f), sound, voicing, lower), 10.0, pc.ordinal.toLong())
            }
        }
        // The tanpura's plucks are drawn at random: more seeds, longer.
        for (seed in 100L..104L) check(Drone(hz(PitchClass.D, 466f), DroneSound.TANPURA, DroneVoicing.FIFTH_OCTAVE), 30.0, seed)
        assertTrue("peak $loudest at $where", loudest <= DroneSynth.PEAK_LIMIT)
        assertTrue("peak $loudest at $where: well under the limit, not near 0", loudest > 0.3f)
    }

    @Test
    fun `the four sounds are about equally loud in every voicing`() {
        val held = seconds(3.0) until seconds(12.0)
        val levels = mutableMapOf<String, Double>()
        for (sound in DroneSound.entries) for (voicing in DroneVoicing.entries) {
            levels["$sound $voicing"] = 20 * log10(DroneMeasures.loudness(steady(Drone(hz(PitchClass.D), sound, voicing), 12.0), rate, held))
        }
        val spread = levels.values.max() - levels.values.min()
        assertTrue("loudness (K-weighted dB) $levels spreads $spread dB", spread < 1.5)
    }

    @Test
    fun `the fade in starts from silence and a release ends on exactly 0`() {
        for (sound in DroneSound.entries) {
            val synth = DroneSynth(rate)
            synth.play(hz(PitchClass.D), sound, DroneVoicing.FIFTH)
            val start = FloatArray(10)
            synth.render(start)
            assertEquals("$sound first sample", 0f, start[0], 1e-6f)
            DroneMeasures.render(synth, 2.0)
            synth.release()
            val tail = FloatArray(seconds(1.0))
            val written = synth.render(tail)
            val fade = seconds(DroneSynth.STOP_FADE_SECONDS.toDouble())
            assertTrue("$sound: the fade's length $written, not $fade", abs(written - fade) <= DroneSynth.BLOCK)
            assertEquals("$sound last sample", 0f, tail[written - 1])
            assertTrue("$sound: the fade is under way a moment before its end", abs(tail[written / 2]) > 0f)
            assertFalse(synth.isSounding)
            assertEquals(0, synth.render(tail))
        }
    }

    @Test
    fun `a release during the fade in or a crossfade also ends on exactly 0`() {
        val synth = DroneSynth(rate)
        synth.play(hz(PitchClass.D), DroneSound.WARM_PAD, DroneVoicing.FIFTH)
        DroneMeasures.render(synth, 0.05)
        synth.release(0.1f)
        val tail = FloatArray(seconds(1.0))
        var written = synth.render(tail)
        assertEquals(0f, tail[written - 1])
        assertFalse(synth.isSounding)

        synth.play(hz(PitchClass.D), DroneSound.HARMONIUM, DroneVoicing.FIFTH)
        DroneMeasures.render(synth, 2.0)
        synth.play(hz(PitchClass.A), DroneSound.TANPURA, DroneVoicing.ROOT)
        DroneMeasures.render(synth, 0.1)
        synth.release()
        written = synth.render(tail)
        assertTrue(written < tail.size)
        assertEquals(0f, tail[written - 1])
        assertFalse(synth.isSounding)
    }

    @Test
    fun `a play during the fade out brings the drone back`() {
        val synth = DroneSynth(rate)
        synth.play(hz(PitchClass.D), DroneSound.FLUTE_ORGAN, DroneVoicing.ROOT)
        DroneMeasures.render(synth, 1.0)
        synth.release(1f)
        DroneMeasures.render(synth, 0.3)
        synth.play(hz(PitchClass.D), DroneSound.FLUTE_ORGAN, DroneVoicing.ROOT)
        val after = DroneMeasures.render(synth, 2.0)
        assertTrue(synth.isSounding)
        val before = steady(Drone(hz(PitchClass.D), DroneSound.FLUTE_ORGAN, DroneVoicing.ROOT), 3.0)
        assertEquals(DroneMeasures.rms(before, seconds(2.0) until seconds(3.0)), DroneMeasures.rms(after, seconds(1.0) until seconds(2.0)), 1e-3)
    }

    /**
     * The steady sound's own high-frequency floor ([DroneMeasures.highPassPeak]: the plucks'
     * attacks, the harmonium's air) over [drones], held, after the fade in.
     */
    private fun floor(drones: List<Drone>): Double =
        drones.maxOf { d -> steady(d, 6.0).let { DroneMeasures.highPassPeak(it, rate, seconds(0.5) until it.size) } }

    /** [drones] one after the other, each [hold] seconds, rendered in odd-sized chunks, then a release to silence. */
    private fun sequence(drones: List<Drone>, hold: Double, chunk: Int = 317): FloatArray {
        val synth = DroneSynth(rate)
        val parts = mutableListOf<FloatArray>()
        for (d in drones) {
            synth.play(d)
            parts += DroneMeasures.render(synth, hold, chunk)
        }
        synth.release()
        parts += DroneMeasures.render(synth, 1.0, chunk)
        assertFalse(synth.isSounding)
        val out = FloatArray(parts.sumOf { it.size })
        var at = 0
        for (p in parts) {
            p.copyInto(out, at)
            at += p.size
        }
        return out
    }

    @Test
    fun `a new root, voicing or octave crossfades without a click`() {
        val failures = mutableListOf<String>()
        for (sound in DroneSound.entries) {
            val drones = listOf(
                Drone(hz(PitchClass.D), sound, DroneVoicing.FIFTH),
                Drone(hz(PitchClass.A), sound, DroneVoicing.FIFTH),
                Drone(hz(PitchClass.A), sound, DroneVoicing.FIFTH_OCTAVE),
                Drone(hz(PitchClass.A), sound, DroneVoicing.ROOT, lower = true),
                Drone(hz(PitchClass.G), sound, DroneVoicing.ROOT, lower = true),
                Drone(hz(PitchClass.G_SHARP), sound, DroneVoicing.FIFTH),
            )
            val x = sequence(drones, hold = 2.0)
            val floor = floor(drones)
            val hp = DroneMeasures.highPassPeak(x, rate, x.indices)
            if (hp > 2 * floor + CLICK_FLOOR) failures += "$sound: high-passed peak $hp against the steady sound's $floor"
            assertEquals(0f, x.last())
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `changes asked faster than the crossfade follow without a click, ending on the last one`() {
        for (sound in DroneSound.entries) {
            val synth = DroneSynth(rate)
            val roots = listOf(PitchClass.C, PitchClass.D, PitchClass.E, PitchClass.F, PitchClass.G, PitchClass.A)
            synth.play(hz(PitchClass.C), sound, DroneVoicing.FIFTH)
            val parts = mutableListOf(DroneMeasures.render(synth, 1.5))
            // A new root every 50 ms, as a finger sweeping across the root picker.
            for (pc in roots) {
                synth.play(hz(pc), sound, DroneVoicing.FIFTH)
                parts += DroneMeasures.render(synth, 0.05)
            }
            assertEquals(hz(PitchClass.A), synth.rootHz)
            parts += DroneMeasures.render(synth, 4.0)
            val x = FloatArray(parts.sumOf { it.size })
            var at = 0
            for (p in parts) {
                p.copyInto(x, at)
                at += p.size
            }
            val floor = floor(roots.map { Drone(hz(it), sound, DroneVoicing.FIFTH) })
            val hp = DroneMeasures.highPassPeak(x, rate, x.indices)
            assertTrue("$sound: high-passed peak $hp against $floor", hp <= 2 * floor + CLICK_FLOOR)
            val readings = DroneMeasures.readings(x, rate, hz(PitchClass.A).toDouble(), x.size - seconds(2.0) until x.size)
            assertEquals("$sound ends on A", 0.0, readings.average(), 1.0)
        }
    }

    @Test
    fun `a change of sound crossfades without a click`() {
        val failures = mutableListOf<String>()
        val order = DroneSound.entries + DroneSound.entries.reversed() + DroneSound.TANPURA
        for (pair in order.zipWithNext()) {
            val drones = pair.toList().map { Drone(hz(PitchClass.D), it, DroneVoicing.FIFTH) }
            val x = sequence(drones, hold = 3.0)
            val floor = floor(drones)
            val hp = DroneMeasures.highPassPeak(x, rate, x.indices)
            if (hp > 2 * floor + CLICK_FLOOR) failures += "${pair.first} → ${pair.second}: high-passed peak $hp against $floor"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the click detector hears a step of a thousandth`() {
        val d = Drone(hz(PitchClass.D), DroneSound.WARM_PAD, DroneVoicing.FIFTH)
        val x = steady(d, 4.0)
        val floor = DroneMeasures.highPassPeak(x, rate, seconds(0.5) until x.size)
        for (i in seconds(3.0) until x.size) x[i] += 0.001f
        assertTrue(DroneMeasures.highPassPeak(x, rate, seconds(0.5) until x.size) > 2 * floor + CLICK_FLOOR)
    }

    @Test
    fun `minutes on end it holds its level and pitch, and the tanpura never repeats`() {
        val slow = 16_000
        val synth = DroneSynth(slow)
        val root = hz(PitchClass.D)
        synth.play(root, DroneSound.FLUTE_ORGAN, DroneVoicing.FIFTH)
        val x = DroneMeasures.render(synth, 600.0)
        val early = DroneMeasures.rms(x, 2 * slow until 12 * slow)
        val late = DroneMeasures.rms(x, x.size - 10 * slow until x.size)
        assertEquals(early, late, early * 1e-3)
        assertEquals(0.0, DroneMeasures.readings(x, slow, root.toDouble(), x.size - 10 * slow until x.size).average(), 0.2)

        val tanpura = DroneSynth(slow)
        tanpura.play(root, DroneSound.TANPURA, DroneVoicing.FIFTH)
        val t = DroneMeasures.render(tanpura, 120.0)
        val cycle = (4 * TanpuraEngine.PLUCK_SECONDS * slow).toInt()
        for (n in 2 until 18) {
            val a = t.copyOfRange(n * cycle, (n + 1) * cycle)
            val b = t.copyOfRange((n + 1) * cycle, (n + 2) * cycle)
            var ab = 0.0
            var aa = 0.0
            var bb = 0.0
            for (i in a.indices) {
                ab += a[i] * b[i]
                aa += a[i] * a[i]
                bb += b[i] * b[i]
            }
            assertTrue("cycle $n repeats: correlation ${ab / sqrt(aa * bb)}", ab / sqrt(aa * bb) < 0.9)
        }
        assertTrue(DroneMeasures.rms(t, t.size - 10 * slow until t.size) > 0.02)
    }

    @Test
    fun `the same seed makes the same drone and another seed another tanpura`() {
        fun run(seed: Long): FloatArray {
            val synth = DroneSynth(rate, seed)
            synth.play(hz(PitchClass.D), DroneSound.TANPURA, DroneVoicing.FIFTH)
            val a = DroneMeasures.render(synth, 4.0, chunk = 480)
            synth.play(hz(PitchClass.E), DroneSound.WARM_PAD, DroneVoicing.ROOT)
            val b = DroneMeasures.render(synth, 2.0, chunk = 101)
            return a + b
        }
        assertArrayEquals(run(7), run(7), 0f)
        assertFalse(run(7).contentEquals(run(8)))
    }

    @Test
    fun `play, release and render allocate nothing`() {
        val synth = DroneSynth(rate)
        val out = FloatArray(480)
        val sounds = DroneSound.entries.toTypedArray()
        val d = hz(PitchClass.D)
        val a = hz(PitchClass.A)
        val e = hz(PitchClass.E)
        fun session() {
            for (sound in sounds) {
                synth.play(d, sound, DroneVoicing.FIFTH)
                repeat(200) { synth.render(out) }
                synth.play(a, sound, DroneVoicing.FIFTH_OCTAVE, lower = true)
                repeat(20) { synth.render(out) }
                synth.play(e, sound, DroneVoicing.ROOT)
                repeat(200) { synth.render(out) }
            }
            synth.release()
            do {
                val written = synth.render(out)
            } while (written > 0)
        }
        session() // Warm up: class loading and the JIT's first compilations.
        assertEquals(0L, allocatedBytes { session() })
    }

    @Test
    fun `it refuses a root it can't play`() {
        val synth = DroneSynth(rate)
        for (bad in floatArrayOf(0f, 39f, 1_001f, Float.NaN)) {
            val refused = runCatching { synth.play(bad) }.isFailure
            assertTrue("$bad Hz", refused)
        }
        assertFalse(synth.isSounding)
    }

    private companion object {
        /** A jump the click detector lets through on a sound with no floor of its own: −90 dB, far under hearing. */
        const val CLICK_FLOOR = 3e-5
    }
}
