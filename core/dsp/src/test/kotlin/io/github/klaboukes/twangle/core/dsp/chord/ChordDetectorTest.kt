package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.strum.StrumChords
import io.github.klaboukes.twangle.core.dsp.strum.StrumListener
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * The chord detector on synthetic strums ([StrumChords.pluckSynthStrum], strings 15 ms apart, the
 * frame from 50 ms after the first) of open and barre shapes in standard tuning, and on sounds
 * that are no chord. [ChordAccuracyReport] has the wider sweeps behind
 * docs/architecture/strum-and-metronome.md, *Chord naming*.
 */
class ChordDetectorTest {

    private val rate = StrumChords.SAMPLE_RATE
    private val n = ChordShapes.frameLength
    private val detector = ChordDetector(rate)
    private val result = ChordResult()
    private val shapes = ChordShapes.OPEN + ChordShapes.BARRE

    private fun detect(signal: FloatArray, a4Hz: Float = 440f): Chord? = detector.detect(signal, result, a4Hz)

    private fun heard(): String =
        (0 until result.noteCount).joinToString(" ") { "${Note(result.noteMidi[it])} %.2f".format(result.noteStrength[it]) }

    private fun assertNamed(shape: ChordShapes.Shape, signal: FloatArray, label: String = "", a4Hz: Float = 440f) {
        val chord = detect(signal, a4Hz)
        assertSame("$shape $label, heard ${heard()}", shape.chord, chord)
        assertEquals("$shape $label: bass", shape.root, result.bass)
    }

    private fun assertNoName(label: String, signal: FloatArray) {
        val chord = detect(signal)
        assertNull("$label named, heard ${heard()}", chord)
    }

    @Test
    fun `open shapes are named`() {
        for (shape in ChordShapes.OPEN) assertNamed(shape, ChordShapes.strum(shape.midi))
    }

    @Test
    fun `barre shapes are named`() {
        for (shape in ChordShapes.BARRE) assertNamed(shape, ChordShapes.strum(shape.midi))
    }

    @Test
    fun `the notes behind a name are the strings played`() {
        // C, x32010: C3 E3 G3 C4 E4.
        detect(ChordShapes.strum(ChordShapes.OPEN.first { it.name == "C" }.midi))
        val pcs = (0 until result.noteCount).map { result.noteMidi[it] % 12 }.toSet()
        assertEquals(setOf(PitchClass.C.ordinal, PitchClass.E.ordinal, PitchClass.G.ordinal), pcs)
        assertEquals(1f, result.noteStrength[0])
        for (i in 1 until result.noteCount) assertTrue(result.noteStrength[i] <= result.noteStrength[i - 1])
        for (pc in listOf(PitchClass.C, PitchClass.E, PitchClass.G)) assertTrue(result.pitchClassStrength[pc.ordinal] > 0.15f)
        assertEquals(0f, result.pitchClassStrength[PitchClass.F_SHARP.ordinal])
        assertTrue(abs(result.tuningOffsetCents) < 1f)
        assertFalse(result.crowded)
    }

    @Test
    fun `strings a little out of tune, struck unevenly and fast or slow`() {
        val random = Random(30)
        for (shape in shapes) repeat(3) {
            val count = shape.midi.size
            val signal = ChordShapes.strum(
                shape.midi,
                cents = FloatArray(count) { random.nextFloat() * 20 - 10 },
                gains = FloatArray(count) { 0.5f + 0.5f * random.nextFloat() },
                spreadSeconds = 0.005 + 0.025 * random.nextDouble(),
            )
            assertNamed(shape, signal, "random #$it")
        }
    }

    @Test
    fun `an upstroke, a frame from the strum's start and one a second later`() {
        for (shape in shapes) {
            assertNamed(shape, ChordShapes.strum(shape.midi.reversedArray()), "upstroke")
            assertNamed(shape, ChordShapes.strum(shape.midi, fromSeconds = 0.0), "from 0 s")
            assertNamed(shape, ChordShapes.strum(shape.midi, fromSeconds = 1.0), "from 1 s")
        }
    }

    @Test
    fun `stiff strings, and low strings with a weak fundamental as a phone hears them`() {
        val weak = TestSignals.WEAK_FUNDAMENTAL_HARMONICS
        for (shape in shapes) {
            val hz = ChordShapes.hz(shape.midi)
            assertNamed(shape, StrumChords.stiffStrum(hz, n, DoubleArray(hz.size) { 1e-4 }), "stiff")
            // The three wound strings' notes (below G3) with the 2nd harmonic louder than the fundamental.
            val signal = StrumChords.stiffStrum(
                hz, n, DoubleArray(hz.size) { 5e-5 },
                harmonicGain = { i, k -> if (shape.midi[i] < 55 && k <= weak.size) weak[k - 1].toDouble() else 1.0 / k },
            )
            assertNamed(shape, signal, "weak fundamentals")
        }
    }

    @Test
    fun `noise at 20 dB SNR`() {
        for (shape in shapes) assertNamed(shape, TestSignals.withNoise(ChordShapes.strum(shape.midi), 20f), "20 dB SNR")
    }

    @Test
    fun `a guitar tuned to itself at A4 = 432 Hz, or 30 cents sharp, is named against A4 = 440`() {
        for (shape in shapes) {
            assertNamed(shape, ChordShapes.strum(shape.midi, a4Hz = 432f), "A4 432")
            assertEquals(1200 * kotlin.math.log2(432.0 / 440).toFloat(), result.tuningOffsetCents, 1f)
            assertNamed(shape, ChordShapes.strum(shape.midi, cents = FloatArray(shape.midi.size) { 30f }), "+30 cents")
            assertEquals(30f, result.tuningOffsetCents, 1f)
        }
        // And named against the A4 it was tuned to.
        val am = ChordShapes.OPEN.first { it.name == "Am" }
        assertNamed(am, ChordShapes.strum(am.midi, a4Hz = 432f), "A4 432 against 432", a4Hz = 432f)
        assertEquals(0f, result.tuningOffsetCents, 1f)
    }

    @Test
    fun `very uneven strums are named right or not at all`() {
        val random = Random(31)
        var named = 0
        var total = 0
        for (shape in shapes) repeat(5) {
            val count = shape.midi.size
            val signal = ChordShapes.strum(
                shape.midi,
                cents = FloatArray(count) { random.nextFloat() * 20 - 10 },
                gains = FloatArray(count) { 0.2f + 0.8f * random.nextFloat() },
            )
            val chord = detect(signal)
            total++
            if (chord != null) {
                named++
                assertSame("$shape uneven, heard ${heard()}", shape.chord, chord)
            }
        }
        assertTrue("named $named of $total", named >= 0.9 * total)
    }

    @Test
    fun `no name for silence, quiet sound or noise`() {
        assertNoName("silence", FloatArray(n))
        assertEquals(0, result.noteCount)
        assertNull(result.bass)
        assertNoName("−90 dBFS noise", TestSignals.noise(n, rms = 3e-5f))
        for (rms in floatArrayOf(0.001f, 0.01f, 0.1f, 0.3f)) {
            for (seed in 1L..5L) assertNoName("noise rms $rms seed $seed", TestSignals.noise(n, rms, seed))
        }
        detect(TestSignals.noise(n, 0.1f))
        assertTrue("loud noise is crowded", result.crowded)
    }

    @Test
    fun `no name for a single note`() {
        // Every note of the first 12 frets, open strings included.
        for (midi in 40..76) assertNoName("single ${Note(midi)}", ChordShapes.strum(intArrayOf(midi)))
    }

    @Test
    fun `no name for two-note dyads and power chords`() {
        for (root in 40..57) {
            assertNoName("power chord ${Note(root)}5", ChordShapes.strum(intArrayOf(root, root + 7, root + 12)))
            assertNoName("fifth ${Note(root)}", ChordShapes.strum(intArrayOf(root, root + 7)))
            assertNoName("major third ${Note(root)}", ChordShapes.strum(intArrayOf(root, root + 4)))
            assertNoName("minor third and octave ${Note(root)}", ChordShapes.strum(intArrayOf(root, root + 3, root + 12)))
        }
        // E5 and A5 as played: 022xxx, x022xx.
        assertNoName("E5", ChordShapes.strum(intArrayOf(40, 47, 52)))
        assertNoName("A5", ChordShapes.strum(intArrayOf(45, 52, 57)))
    }

    @Test
    fun `no name for a muddled strum`() {
        // Two chords at once: C and D.
        assertNoName("C + D", ChordShapes.strum(intArrayOf(48, 50, 52, 54, 55, 57)))
        // C (x32010) with the top string at the 2nd fret: an F# among C E G.
        assertNoName("C with F#", ChordShapes.strum(intArrayOf(48, 52, 55, 60, 66)))
        // Em (022000) with the B string at the 2nd fret: a C# among E G B.
        assertNoName("Em with C#", ChordShapes.strum(intArrayOf(40, 47, 52, 55, 61, 64)))
        // G (320003) with the G string at the 1st fret: G# among G B D.
        assertNoName("G with G#", ChordShapes.strum(intArrayOf(43, 47, 50, 56, 59, 67)))
        // Clusters: E F F# G, a semitone stack, a whole-tone stack.
        assertNoName("E F F# G", ChordShapes.strum(intArrayOf(52, 53, 54, 55)))
        assertNoName("semitones", ChordShapes.strum(intArrayOf(45, 46, 47, 48, 49, 50)))
        assertNoName("whole tones", ChordShapes.strum(intArrayOf(48, 50, 52, 54, 56, 58)))
        // A diminished and an augmented triad: not among the chords named.
        assertNoName("Bdim", ChordShapes.strum(intArrayOf(47, 50, 53, 59)))
        assertNoName("Caug", ChordShapes.strum(intArrayOf(48, 52, 56, 60)))
        // A strum with every string a quarter tone off its neighbours' grid.
        val c = ChordShapes.OPEN.first { it.name == "C" }.midi
        assertNoName("C, strings ±50 cents", ChordShapes.strum(c, cents = floatArrayOf(0f, 50f, -50f, 50f, -50f)))
    }

    @Test
    fun `a strum through StrumListener is named`() {
        val shape = ChordShapes.OPEN.first { it.name == "Am" }
        val listener = StrumListener(rate)
        val lead = (0.3 * rate).toInt()
        val strum = StrumChords.pluckSynthStrum(ChordShapes.hz(shape.midi), 2 * rate, fromSeconds = 0.0)
        val signal = FloatArray(lead + strum.size).also { strum.copyInto(it, lead) }
        val frame = FloatArray(listener.frameLength)
        var readings = 0
        var offset = 0
        while (offset < signal.size) {
            offset += listener.push(signal, offset, minOf(1024, signal.size - offset))
            if (listener.isFrameReady) {
                listener.readFrame(frame)
                readings++
                assertNamed(shape, frame, "reading $readings")
            }
        }
        assertTrue("readings: $readings", readings >= 5)
    }

    @Test
    fun `detect allocates nothing`() {
        val signal = ChordShapes.strum(ChordShapes.OPEN.first { it.name == "G" }.midi)
        val noise = TestSignals.noise(n, 0.1f)
        repeat(20) {
            detect(signal)
            detect(noise)
        }
        assertEquals(0L, allocatedBytes { detect(signal) })
        assertEquals(0L, allocatedBytes { detect(noise) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a frame shorter than the detector's is refused`() {
        detect(FloatArray(n - 1))
    }

    private fun abs(x: Float) = kotlin.math.abs(x)
}
