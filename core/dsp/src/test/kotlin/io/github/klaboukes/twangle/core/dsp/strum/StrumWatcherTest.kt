package io.github.klaboukes.twangle.core.dsp.strum

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordShapes
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.synth.PluckSynth
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * [StrumWatcher]: after the strum check's button every sudden sound is a strum of the open strings, read within a
 * cent from its first frame, and with Listen in Chords every one is a chord, named, on synthetic strums and chords.
 * docs/architecture/strum-and-metronome.md, *The strum check's button*. (Until 2026-11-03 it also decided by itself
 * what a sound was; those tests went with the deciding.)
 */
class StrumWatcherTest {

    private val rate = StrumChords.SAMPLE_RATE
    private val standard = StrumChords.expectedHz(Tunings.STANDARD)

    /** One sudden sound the watcher noticed: what it was read as, and its readings. */
    private class Heard(val strum: Int, val kind: StrumKind) {
        val chords = mutableListOf<Chord?>()
        val cents = mutableListOf<FloatArray>()
        val present = mutableListOf<BooleanArray>()
        override fun toString() = "strum $strum: $kind, chords $chords"
    }

    private fun seconds(s: Double) = (s * rate).roundToInt()

    /** Quiet room noise (≈ −70 dBFS) [totalSeconds] long, with [sounds] added at their start times. */
    private fun room(totalSeconds: Double, vararg sounds: Pair<Double, FloatArray>): FloatArray {
        val out = TestSignals.noise(seconds(totalSeconds), rms = 3e-4f, seed = 3L)
        for ((start, sound) in sounds) {
            val from = seconds(start)
            for (i in sound.indices) if (from + i < out.size) out[from + i] += sound[i]
        }
        return out
    }

    private fun strum(hz: FloatArray, gains: FloatArray = FloatArray(hz.size) { 1f }, length: Double = 2.5) =
        StrumChords.pluckSynthStrum(hz, seconds(length), fromSeconds = 0.0, gains = gains)

    private fun pluck(hz: Float) = PluckSynth(rate, durationSeconds = 3f).run {
        start(hz)
        FloatArray(seconds(3.0)).also { render(it, 0, it.size) }
    }

    private fun watch(
        input: FloatArray,
        open: FloatArray? = standard,
        every: StrumKind = StrumKind.OPEN_STRINGS,
        range: PitchRange = PitchRange.STANDARD,
        watcher: StrumWatcher = StrumWatcher(rate),
    ): List<Heard> {
        watcher.start(open, 440f, every, range)
        val heard = mutableListOf<Heard>()
        var pos = 0
        while (pos < input.size) {
            pos += watcher.push(input, pos, minOf(1024, input.size - pos))
            while (true) {
                when (watcher.next()) {
                    StrumWatcher.Event.NOTHING -> break
                    StrumWatcher.Event.STRUM -> heard += Heard(watcher.strumCount, watcher.kind)
                    StrumWatcher.Event.READING -> heard.last().apply {
                        chords += watcher.readingChord
                        if (watcher.kind == StrumKind.OPEN_STRINGS) {
                            cents += watcher.strings.cents.copyOf(watcher.stringCount)
                            present += watcher.strings.present.copyOf(watcher.stringCount)
                        }
                    }
                    StrumWatcher.Event.QUIET -> Unit
                }
            }
        }
        return heard
    }

    private fun only(label: String, heard: List<Heard>): Heard {
        assertEquals("$label: one sound, heard $heard", 1, heard.size)
        return heard.single()
    }

    // --- The strum check's button: every sound is a strum of the open strings. ---

    @Test
    fun `after the button every sound is a strum of the open strings, read within a cent from its first frame`() {
        val cases = listOf(
            "in tune" to standard,
            "A −20, G +15" to StrumChords.detuned(standard, floatArrayOf(0f, -20f, 0f, 15f, 0f, 0f)),
            "every string ±30" to StrumChords.detuned(standard, floatArrayOf(30f, -25f, 20f, -30f, 10f, -15f)),
        )
        for ((label, hz) in cases) {
            val heard = only(label, watch(room(4.0, 0.5 to strum(hz))))
            assertSame(label, StrumKind.OPEN_STRINGS, heard.kind)
            assertTrue("$label: readings ${heard.cents.size}", heard.cents.size >= 5)
            for (s in 0..3) {
                val expected = 1200f * kotlin.math.log2(hz[s] / standard[s])
                assertTrue("$label string $s: ${heard.cents[0][s]} vs $expected", abs(heard.cents[0][s] - expected) < 1f)
            }
        }
    }

    @Test
    fun `an uneven strum and one with a string left out are read too`() {
        val uneven = only("uneven", watch(room(4.0, 0.5 to strum(standard, floatArrayOf(1f, 0.5f, 0.8f, 0.4f, 0.6f, 0.3f)))))
        assertTrue("uneven: ${uneven.present[0].toList()}", uneven.present[0].count { it } >= 5)
        val missing = only("B left out", watch(room(4.0, 0.5 to strum(standard, floatArrayOf(1f, 1f, 1f, 1f, 0f, 1f)))))
        assertTrue("B heard: ${missing.present[0].toList()}", !missing.present[0][4])
    }

    @Test
    fun `a strum over strings still ringing, and strummed again, is two`() {
        val heard = watch(room(6.0, 0.5 to strum(standard, length = 3.0), 2.0 to strum(standard)))
        assertEquals("$heard", listOf(StrumKind.OPEN_STRINGS, StrumKind.OPEN_STRINGS), heard.map { it.kind })
        assertTrue("$heard", heard.all { it.cents.isNotEmpty() })
    }

    @Test
    fun `one string after the button is a strum too, with too few strings heard`() {
        // The strum check then waits: it hears too few strings.
        val single = only("A string", watch(room(4.0, 0.5 to pluck(standard[1]))))
        assertSame(StrumKind.OPEN_STRINGS, single.kind)
        assertTrue("$single", single.present.isNotEmpty() && single.present[0].count { it } < 3)
    }

    @Test
    fun `open tunings, and only the top three strings`() {
        for (tuning in listOf(Tunings.DROP_D, Tunings.DADGAD, Tunings.OPEN_G, Tunings.OPEN_D)) {
            val hz = StrumChords.expectedHz(tuning)
            val heard = only("$tuning", watch(room(4.0, 0.5 to strum(hz)), hz))
            assertTrue("$tuning: ${heard.present[0].toList()}", heard.present[0].count { it } >= 5)
        }
        val top = only("G B E", watch(room(4.0, 0.5 to strum(standard.copyOfRange(3, 6)))))
        assertTrue("G B E: ${top.present[0].toList()}", (3..5).all { top.present[0][it] })
    }

    @Test
    fun `a bass strum in the low range, and a ukulele's`() {
        val bassHz = StrumChords.expectedHz(Instruments.BASS_4.tunings.first())
        // The low strings beat, so the level dips and rises again while they ring: rises before the first frame
        // count with the strum.
        val heard = watch(room(5.0, 0.5 to strum(bassHz, length = 3.5)), bassHz, range = PitchRange.LOW)
        assertTrue("$heard", heard.isNotEmpty() && heard.first().cents.isNotEmpty())
        val uke = StrumChords.expectedHz(Instruments.UKULELE.tunings.first())
        val ukulele = only("ukulele", watch(room(4.0, 0.5 to strum(uke)), uke))
        assertTrue("ukulele: ${ukulele.present[0].toList()}", ukulele.present[0].count { it } == 4)
    }

    @Test
    fun `after the button a strum is read as soon as its first frame is`() {
        val watcher = StrumWatcher(rate)
        val input = room(3.0, 0.5 to strum(standard))
        watcher.start(standard, 440f, StrumKind.OPEN_STRINGS)
        var readAt = Double.NaN
        var pos = 0
        while (pos < input.size && readAt.isNaN()) {
            pos += watcher.push(input, pos, minOf(1024, input.size - pos))
            while (true) {
                val event = watcher.next()
                if (event == StrumWatcher.Event.NOTHING) break
                if (event == StrumWatcher.Event.READING && readAt.isNaN()) readAt = watcher.readingSeconds - 0.5
            }
        }
        // As 1.1's strum check: the first reading 0.39–0.44 s after the strum, a frame's length.
        assertTrue("read ${"%.2f".format(readAt)} s after the strum", readAt in 0.3..0.5)
    }

    // --- Listen in Chords: every sound is a chord. ---

    @Test
    fun `with Listen in Chords every sound is a chord, named from its first frame`() {
        for (shape in ChordShapes.OPEN + ChordShapes.BARRE + ChordShapes.POWER) {
            val chord = only("$shape", watch(room(4.0, 0.5 to strum(ChordShapes.hz(shape.midi))), open = null, every = StrumKind.CHORD))
            assertSame("$shape", StrumKind.CHORD, chord.kind)
            assertTrue("$shape named ${chord.chords}", chord.chords.count { it === shape.chord } * 2 > chord.chords.size)
        }
        // A string alone is read as a chord too, and names none (ChordDetector wants a chord's notes).
        val single = only("A string", watch(room(4.0, 0.5 to pluck(standard[1])), open = null, every = StrumKind.CHORD))
        assertSame(StrumKind.CHORD, single.kind)
    }

    // --- Starting. ---

    @Test
    fun `it is told what every sound is, and a strum of open strings needs them`() {
        val watcher = StrumWatcher(rate)
        assertThrows(IllegalArgumentException::class.java) { watcher.start(standard, 440f, StrumKind.NONE) }
        assertThrows(IllegalArgumentException::class.java) { watcher.start(null, 440f, StrumKind.OPEN_STRINGS) }
        watcher.start(standard, 440f, StrumKind.CHORD)
        assertEquals("chords check no open strings", 0, watcher.stringCount)
        assertSame("nothing heard yet", StrumKind.NONE, watcher.kind)
    }

    @Test
    fun `nothing allocates after start`() {
        val watcher = StrumWatcher(rate)
        val input = room(6.0, 0.5 to strum(standard), 3.0 to strum(ChordShapes.hz(ChordShapes.OPEN.first { it.name == "C" }.midi)))
        fun run() {
            // Strums, then chords (an index loop: a list would allocate).
            for (pass in 0..1) {
                watcher.start(standard, 440f, if (pass == 0) StrumKind.OPEN_STRINGS else StrumKind.CHORD)
                var pos = 0
                while (pos < input.size) {
                    pos += watcher.push(input, pos, minOf(1024, input.size - pos))
                    while (watcher.next() != StrumWatcher.Event.NOTHING) Unit
                }
            }
        }
        repeat(3) { run() }
        assertEquals(0L, allocatedBytes { run() })
    }

    @Test
    fun `a new range makes frames of its length`() {
        val watcher = StrumWatcher(rate)
        watcher.start(standard, 440f, StrumKind.OPEN_STRINGS, range = PitchRange.LOW)
        assertSame(PitchRange.LOW, watcher.range)
        assertNotNull(watcher.notes)
    }
}
