package io.github.klaboukes.twangle.core.dsp.strum

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordShapes
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.recordings.RealPlucks
import io.github.klaboukes.twangle.core.dsp.synth.PluckSynth
import io.github.klaboukes.twangle.core.dsp.synth.StringSynth
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * [StrumWatcher]: a strum of open strings and a fretted chord are told from one string, on synthetic strums
 * and chords, and never on the real guitar's plucks (`src/test/resources/fixtures/`), alone or one after
 * another while the others ring, nor on the reference tone. docs/architecture/strum-and-metronome.md,
 * *The strum check's button*.
 */
class StrumWatcherTest {

    private val rate = StrumChords.SAMPLE_RATE
    private val standard = StrumChords.expectedHz(Tunings.STANDARD)

    /** One sudden sound the watcher noticed: what it was decided to be, and its readings. */
    private class Heard(val strum: Int) {
        var kind: StrumKind? = null
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

    private fun watch(
        input: FloatArray,
        open: FloatArray? = standard,
        nameChords: Boolean = true,
        range: PitchRange = PitchRange.STANDARD,
        a4Hz: Float = 440f,
        sampleRate: Int = rate,
        chunk: Int = 1024,
        watcher: StrumWatcher = StrumWatcher(sampleRate),
        everySound: Boolean = false,
        everyChord: Boolean = false,
    ): List<Heard> {
        watcher.start(open, a4Hz, nameChords, range, everySoundIsAStrum = everySound, everySoundIsAChord = everyChord)
        val heard = mutableListOf<Heard>()
        var start = 0
        while (start < input.size) {
            val length = minOf(chunk, input.size - start)
            var pos = 0
            while (pos < length) {
                pos += watcher.push(input, start + pos, length - pos)
                while (true) {
                    when (watcher.next()) {
                        StrumWatcher.Event.NOTHING -> break
                        // With every sound a strum (the strum check's button) its kind is known at once.
                        StrumWatcher.Event.STRUM -> heard += Heard(watcher.strumCount).also { it.kind = watcher.kind }
                        StrumWatcher.Event.DECIDED -> heard.last().kind = watcher.kind
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
            start += length
        }
        return heard
    }

    private fun assertNoStrum(label: String, heard: List<Heard>) {
        val strums = heard.filter { it.kind == StrumKind.OPEN_STRINGS || it.kind == StrumKind.CHORD }
        assertTrue("$label taken for a strum or chord: $strums", strums.isEmpty())
    }

    private fun only(label: String, heard: List<Heard>): Heard {
        val decided = heard.filter { it.kind != null }
        assertEquals("$label: one sound decided, heard $heard", 1, decided.size)
        return decided.single()
    }

    // --- The real guitar: never a strum or a chord. ---

    @Test
    fun `no real pluck is taken for a strum or a chord`() {
        for (pluck in RealPlucks.all) {
            assertNoStrum(pluck.name, watch(pluck.samples, sampleRate = pluck.sampleRate))
            // Nor in another tuning whose open strings include the note, or in any-note mode (chords only).
            assertNoStrum("${pluck.name}, drop D", watch(pluck.samples, StrumChords.expectedHz(Tunings.DROP_D), sampleRate = pluck.sampleRate))
            assertNoStrum("${pluck.name}, chords only", watch(pluck.samples, open = null, sampleRate = pluck.sampleRate))
        }
    }

    @Test
    fun `real strings plucked one after another while the others ring are no strum`() {
        // Tuning string by string: each pluck lets the strings before it ring on, the way a tuner is used.
        for (touch in listOf("pick", "finger")) {
            for (gap in listOf(0.6, 1.0)) {
                val plucks = RealPlucks.strings.map { string -> RealPlucks.all.first { it.string == string && it.touch.fileName == touch } }
                val sounds = plucks.mapIndexed { i, pluck -> 0.5 + i * gap to pluck.samples.copyOfRange(pluck.onsetSample, pluck.samples.size) }
                // Then the A string again, and the low E, while all ring.
                val again = listOf(plucks[1], plucks[0]).mapIndexed { i, pluck ->
                    0.5 + (plucks.size + i) * gap to pluck.samples.copyOfRange(pluck.onsetSample, pluck.samples.size)
                }
                val input = room(0.5 + (plucks.size + 3) * gap + 3.0, *(sounds + again).toTypedArray())
                assertNoStrum("$touch, ${gap}s apart", watch(input))
                assertNoStrum("$touch, ${gap}s apart, chords only", watch(input, open = null))
            }
        }
    }

    @Test
    fun `the reference tone of every guitar string is no strum`() {
        val mapper = NoteMapper()
        for (instrument in listOf(Instruments.GUITAR, Instruments.GUITAR_12, Instruments.UKULELE, Instruments.BASS_4)) {
            val tuning = instrument.tunings.first()
            val open = FloatArray(tuning.stringCount) { mapper.frequencyOf(tuning.strings[it]) }
            val range = PitchRange.forLowestNote(open.min())
            for (i in open.indices) {
                val synth = StringSynth(rate, instrument.voice)
                synth.start(open[i], octavePair = tuning.hasOctaveString(i))
                val tone = FloatArray(seconds(synth.durationSeconds.toDouble()))
                synth.render(tone)
                assertNoStrum("$instrument string ${tuning.strings[i]}", watch(room(5.0, 0.5 to tone), open, range = range))
            }
        }
    }

    @Test
    fun `single synthetic notes, stiff or with a phone's weak fundamental, are no strum`() {
        val synth = PluckSynth(rate, durationSeconds = 3f)
        for (midi in 40..76) {
            val hz = (440.0 * Math.pow(2.0, (midi - 69) / 12.0)).toFloat()
            val note = FloatArray(seconds(3.0))
            synth.start(hz)
            synth.render(note, 0, note.size)
            assertNoStrum("note $midi", watch(room(4.0, 0.5 to note)))
            val weak = StrumChords.stiffStrum(
                floatArrayOf(hz), seconds(3.0), doubleArrayOf(1.5e-4),
                harmonicGain = { _, k -> if (k == 1) 0.33 else 1.0 / (k - 1) },
                fromSeconds = 0.0,
            )
            assertNoStrum("stiff note $midi, weak fundamental", watch(room(4.0, 0.5 to weak)))
        }
    }

    @Test
    fun `noise bursts and clicks are no strum`() {
        val burst = TestSignals.noise(seconds(0.6), rms = 0.05f, seed = 9L)
        val click = TestSignals.tick(floatArrayOf(1800f, 3100f, 4700f), decaySeconds = 0.004, length = seconds(0.05), amplitude = 0.5f)
        assertNoStrum("noise", watch(room(3.0, 0.5 to burst)))
        assertNoStrum("click", watch(room(3.0, 0.5 to click, 1.5 to click)))
    }

    // --- Strums of open strings. ---

    @Test
    fun `a strum of the open strings is one, in tune or not, also unevenly struck or with a string left out`() {
        val cases = listOf(
            "in tune" to standard,
            "A −20, G +15" to StrumChords.detuned(standard, floatArrayOf(0f, -20f, 0f, 15f, 0f, 0f)),
            "every string ±30" to StrumChords.detuned(standard, floatArrayOf(30f, -25f, 20f, -30f, 10f, -15f)),
        )
        for ((label, hz) in cases) {
            val heard = only(label, watch(room(4.0, 0.5 to strum(hz))))
            assertSame(label, StrumKind.OPEN_STRINGS, heard.kind)
            assertTrue("$label: readings ${heard.cents.size}", heard.cents.size >= 5)
            // The first reading already reads the clear strings within a cent.
            for (s in 0..3) {
                val expected = 1200f * kotlin.math.log2(hz[s] / standard[s])
                assertTrue("$label string $s: ${heard.cents[0][s]} vs $expected", abs(heard.cents[0][s] - expected) < 1f)
            }
        }
        val uneven = only("uneven", watch(room(4.0, 0.5 to strum(standard, floatArrayOf(1f, 0.5f, 0.8f, 0.4f, 0.6f, 0.3f)))))
        assertSame(StrumKind.OPEN_STRINGS, uneven.kind)
        val missing = only("B left out", watch(room(4.0, 0.5 to strum(standard, floatArrayOf(1f, 1f, 1f, 1f, 0f, 1f)))))
        assertSame(StrumKind.OPEN_STRINGS, missing.kind)
        assertTrue("B heard: ${missing.present[0].toList()}", !missing.present[0][4])
    }

    @Test
    fun `a strum of the open strings over strings still ringing, and strummed again`() {
        val first = strum(standard, length = 3.0)
        val heard = watch(room(6.0, 0.5 to first, 2.0 to strum(standard)))
        val decided = heard.filter { it.kind != null }
        assertEquals("$heard", listOf(StrumKind.OPEN_STRINGS, StrumKind.OPEN_STRINGS), decided.map { it.kind })
        // A pluck of one string over a ringing strum is no new strum.
        val single = PluckSynth(rate, durationSeconds = 3f).run {
            start(standard[1])
            FloatArray(seconds(3.0)).also { render(it, 0, it.size) }
        }
        val over = watch(room(6.0, 0.5 to first, 2.0 to single))
        assertEquals("$over", listOf(StrumKind.OPEN_STRINGS, StrumKind.NONE), over.filter { it.kind != null }.map { it.kind })
    }

    @Test
    fun `open tunings, and only the top three strings`() {
        for (tuning in listOf(Tunings.DROP_D, Tunings.DADGAD, Tunings.OPEN_G, Tunings.OPEN_D)) {
            val hz = StrumChords.expectedHz(tuning)
            // Open strings that make a chord (open G is G major) are a strum of open strings first.
            assertSame("$tuning", StrumKind.OPEN_STRINGS, only("$tuning", watch(room(4.0, 0.5 to strum(hz)), hz)).kind)
        }
        val top = strum(standard.copyOfRange(3, 6))
        assertSame(StrumKind.OPEN_STRINGS, only("G B E", watch(room(4.0, 0.5 to top))).kind)
    }

    @Test
    fun `a bass strum in the low range, and a ukulele's`() {
        val bass = Instruments.BASS_4.tunings.first()
        val bassHz = StrumChords.expectedHz(bass)
        // The low strings beat, so the level dips and rises again while they ring: rises before the first frame
        // count with the strum, later ones are no new strum.
        val heard = watch(room(5.0, 0.5 to strum(bassHz, length = 3.5)), bassHz, nameChords = false, range = PitchRange.LOW)
        assertSame("$heard", StrumKind.OPEN_STRINGS, heard.first().kind)
        assertTrue("$heard", heard.drop(1).all { it.kind != StrumKind.OPEN_STRINGS && it.kind != StrumKind.CHORD })
        val uke = StrumChords.expectedHz(Instruments.UKULELE.tunings.first())
        assertSame(StrumKind.OPEN_STRINGS, only("ukulele", watch(room(4.0, 0.5 to strum(uke)), uke)).kind)
    }

    @Test
    fun `two open strings side by side are no chord, also plucked a moment apart`() {
        for (low in 0 until 4) {
            val pair = floatArrayOf(standard[low], standard[low + 1])
            assertNoStrum("strings $low and ${low + 1} together", watch(room(4.0, 0.5 to strum(pair))))
            val first = strum(floatArrayOf(pair[0]), length = 3.0)
            val second = strum(floatArrayOf(pair[1]), length = 3.0)
            assertNoStrum("strings $low and ${low + 1}, 0.3 s apart", watch(room(5.0, 0.5 to first, 0.8 to second)))
        }
    }

    // --- Fretted chords. ---

    @Test
    fun `fretted chords are chords and named, open, barre and power chords`() {
        for (shape in ChordShapes.OPEN + ChordShapes.BARRE + ChordShapes.POWER) {
            val heard = only("$shape", watch(room(4.0, 0.5 to strum(ChordShapes.hz(shape.midi)))))
            assertSame("$shape", StrumKind.CHORD, heard.kind)
            val named = heard.chords.count { it === shape.chord }
            assertTrue("$shape named ${heard.chords}", named * 2 > heard.chords.size)
        }
    }

    @Test
    fun `a chord is no strum of open strings, and with chord names off it is nothing`() {
        val g = ChordShapes.OPEN.first { it.name == "G" }
        val heard = only("G", watch(room(4.0, 0.5 to strum(ChordShapes.hz(g.midi))), nameChords = false))
        assertSame(StrumKind.NONE, heard.kind)
        // Open strings with the open-string check off are no chord either: E A D G B E names nothing.
        assertSame(StrumKind.NONE, only("open strings, chords only", watch(room(4.0, 0.5 to strum(standard)), open = null)).kind)
    }

    @Test
    fun `a strum is decided about half a second after it`() {
        val watcher = StrumWatcher(rate)
        val input = room(3.0, 0.5 to strum(standard))
        watcher.start(standard, 440f, nameChords = true)
        var decidedAt = Double.NaN
        var pos = 0
        while (pos < input.size && decidedAt.isNaN()) {
            pos += watcher.push(input, pos, minOf(1024, input.size - pos))
            while (true) {
                val event = watcher.next()
                if (event == StrumWatcher.Event.NOTHING) break
                if (event == StrumWatcher.Event.DECIDED) decidedAt = watcher.seconds - 0.5
            }
        }
        assertTrue("decided ${"%.2f".format(decidedAt)} s after the strum", decidedAt in 0.4..0.6)
    }

    // --- The strum check's button: every sound is a strum. ---

    @Test
    fun `after the button every sound is a strum of the open strings, read from its first frame`() {
        val hz = StrumChords.detuned(standard, floatArrayOf(0f, -20f, 0f, 15f, 0f, 0f))
        val heard = watch(room(4.0, 0.5 to strum(hz)), nameChords = false, everySound = true)
        assertEquals("$heard", 1, heard.size)
        val strum = heard.single()
        assertSame(StrumKind.OPEN_STRINGS, strum.kind)
        for (s in 0..3) {
            val expected = 1200f * kotlin.math.log2(hz[s] / standard[s])
            assertTrue("string $s: ${strum.cents[0][s]} vs $expected", abs(strum.cents[0][s] - expected) < 1f)
        }
        // Not decided: one string is a strum too (the strum check then waits: it hears too few strings).
        val single = PluckSynth(rate, durationSeconds = 3f).run {
            start(standard[1])
            FloatArray(seconds(3.0)).also { render(it, 0, it.size) }
        }
        val pluck = watch(room(4.0, 0.5 to single), nameChords = false, everySound = true)
        assertSame("$pluck", StrumKind.OPEN_STRINGS, pluck.first().kind)
        assertTrue("$pluck", pluck.first().present.isNotEmpty() && pluck.first().present[0].count { it } < 3)
        // Without open strings there is nothing to strum: sounds are decided as before.
        assertTrue(watch(room(4.0, 0.5 to strum(standard)), open = null, everySound = true).none { it.kind == StrumKind.OPEN_STRINGS })
    }

    @Test
    fun `with Listen in Chords every sound is a chord, named from its first frame`() {
        for (shape in ChordShapes.OPEN + ChordShapes.BARRE + ChordShapes.POWER) {
            val heard = watch(room(4.0, 0.5 to strum(ChordShapes.hz(shape.midi))), open = null, everyChord = true)
            assertEquals("$shape: $heard", 1, heard.size)
            val chord = heard.single()
            assertSame("$shape", StrumKind.CHORD, chord.kind)
            assertTrue("$shape named ${chord.chords}", chord.chords.count { it === shape.chord } * 2 > chord.chords.size)
        }
        // A string alone is read as a chord too, and names none (ChordDetector wants a chord's notes).
        val single = PluckSynth(rate, durationSeconds = 3f).run {
            start(standard[1])
            FloatArray(seconds(3.0)).also { render(it, 0, it.size) }
        }
        val pluck = watch(room(4.0, 0.5 to single), open = null, everyChord = true).single()
        assertSame(StrumKind.CHORD, pluck.kind)
        // Chord names off, nothing is a chord.
        assertTrue(watch(room(4.0, 0.5 to strum(standard)), open = null, nameChords = false, everyChord = true).none { it.kind == StrumKind.CHORD })
    }

    @Test
    fun `after the button a strum is read as soon as its first frame is`() {
        val watcher = StrumWatcher(rate)
        val input = room(3.0, 0.5 to strum(standard))
        watcher.start(standard, 440f, nameChords = false, everySoundIsAStrum = true)
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

    @Test
    fun `nothing allocates after start`() {
        val watcher = StrumWatcher(rate)
        val input = room(6.0, 0.5 to strum(standard), 3.0 to strum(ChordShapes.hz(ChordShapes.OPEN.first { it.name == "C" }.midi)))
        fun run() {
            // Deciding, then every sound a strum (an index loop: a list would allocate).
            for (pass in 0..1) {
                val everySound = pass == 1
                watcher.start(standard, 440f, nameChords = !everySound, everySoundIsAStrum = everySound)
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
        watcher.start(standard, 440f, nameChords = true, range = PitchRange.LOW)
        assertSame(PitchRange.LOW, watcher.range)
        assertNotNull(watcher.notes)
    }
}
