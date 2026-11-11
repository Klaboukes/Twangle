package io.github.klaboukes.twangle.core.dsp.smoothing

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class PitchSmootherTest {

    private val smoother = PitchSmoother()

    @Test
    fun `a new reading starts once three readings agree, at their median`() {
        assertFalse(smoother.hasReading)
        assertTrue(smoother.update(E2 + 0.30f))
        assertTrue(smoother.update(E2 + 0.12f))
        assertFalse(smoother.hasReading)
        assertTrue(smoother.update(E2 + 0.10f))
        assertTrue(smoother.hasReading)
        assertEquals(Note(40), smoother.note)
        assertEquals(12f, smoother.cents, 0.01f)
    }

    @Test
    fun `one or two readings on their own, like a tick, show nothing`() {
        repeat(5) {
            repeat(2) { smoother.update(E4 + 7f) }
            repeat(3) { smoother.noReading() }
            assertFalse(smoother.hasReading)
        }
    }

    @Test
    fun `a gap or a reading far from the last starts the onset over`() {
        repeat(2) { smoother.update(E2) }
        smoother.noReading()
        repeat(2) { smoother.update(E2) }
        assertFalse(smoother.hasReading)
        // More than 50 cents from the last: these two don't count with the ones before.
        repeat(2) { smoother.update(A2) }
        assertFalse(smoother.hasReading)
        smoother.update(A2 + 0.05f)
        assertEquals(Note(45), smoother.note)
        assertEquals(0f, smoother.cents, 0.01f)
    }

    @Test
    fun `a passed tick neither starts a reading nor ends one`() {
        // Before a reading: a tick's frame, then the frames after it has died away.
        repeat(5) {
            smoother.update(E4 + 7f)
            repeat(3) { smoother.noReading(sustained = false) }
        }
        assertFalse(smoother.hasReading)
        // While one is shown: frames hiding the string under a tick, for longer than a dropout may
        // last, and it is still there.
        repeat(5) { smoother.update(E2) }
        smoother.noReading()
        repeat(2 * PitchSmoother.DEFAULT_MAX_MISSED_FRAMES) { smoother.noReading(sustained = false) }
        assertTrue(smoother.hasReading)
        assertEquals(Note(40), smoother.note)
        // Sustained frames without a pitch are a dropout as usual.
        repeat(PitchSmoother.DEFAULT_MAX_MISSED_FRAMES) { smoother.noReading() }
        assertFalse(smoother.hasReading)
    }

    @Test
    fun `frames too quiet to open the gate keep a reading going but cannot start one`() {
        repeat(10) { assertFalse(smoother.update(E4 + 7f, loudEnough = false)) }
        assertFalse(smoother.hasReading)
        repeat(2) { smoother.update(E2) }
        smoother.update(E2, loudEnough = false)
        smoother.update(E2)
        assertFalse("the quiet frame broke the onset", smoother.hasReading)
        repeat(2) { smoother.update(E2) }
        assertTrue(smoother.hasReading)
        repeat(20) { assertTrue(smoother.update(E2 + 0.1f, loudEnough = false)) }
        assertEquals(10f, smoother.cents, 0.5f)
    }

    @Test
    fun `with an onset of one frame the first reading is shown as is`() {
        val immediate = PitchSmoother(onsetFrames = 1)
        immediate.update(E2 + 0.12f)
        assertTrue(immediate.hasReading)
        assertEquals(12f, immediate.cents, 0.01f)
    }

    @Test
    fun `jitter is reduced`() {
        val random = Random(1)
        val input = FloatArray(200) { E2 + 0.05f + 0.03f * random.nextGaussian().toFloat() }
        val output = FloatArray(input.size)
        for (i in input.indices) {
            smoother.update(input[i])
            output[i] = smoother.cents
        }
        val inputSpread = spread(input.drop(20).map { 100 * (it - E2) })
        val outputSpread = spread(output.drop(20).toList())
        assertTrue("spread $inputSpread → $outputSpread cents", outputSpread < inputSpread / 2)
        assertEquals(5f, output.drop(20).average().toFloat(), 0.5f)
    }

    @Test
    fun `a steady change is followed within a few frames`() {
        repeat(10) { smoother.update(E2) }
        repeat(15) { smoother.update(E2 + 0.2f) }
        assertEquals(20f, smoother.cents, 0.5f)
    }

    @Test
    fun `a one-frame spike is ignored`() {
        repeat(10) { smoother.update(E2) }
        smoother.update(E2 + 3f)
        assertEquals(Note(40), smoother.note)
        assertEquals(0f, smoother.cents, 0.01f)
    }

    @Test
    fun `octave glitches are rejected`() {
        repeat(10) { smoother.update(E2 + 0.03f) }
        assertFalse(smoother.update(E2 + 12.03f))
        assertFalse(smoother.update(E2 - 11.9f))
        assertTrue(smoother.update(E2 + 0.03f))
        assertFalse(smoother.update(E2 + 24f))
        assertEquals(Note(40), smoother.note)
        assertEquals(3f, smoother.cents, 0.01f)
    }

    @Test
    fun `a real octave change is accepted after it persists`() {
        repeat(10) { smoother.update(E2) }
        assertFalse(smoother.update(E2 + 12f))
        assertFalse(smoother.update(E2 + 12f))
        assertTrue(smoother.update(E2 + 12.05f))
        assertEquals(Note(52), smoother.note)
        assertEquals(5f, smoother.cents, 0.01f)
    }

    @Test
    fun `an octave up in frames too quiet to start a reading is the fundamental dying, not a new note`() {
        // A picked high E through a phone: its 2nd partial is louder than its fundamental, which
        // sinks into the room's noise about 2 s in, and the detector then reads E5 at −52 dBFS, under
        // the gate's threshold, until the gate closes. The reading stays E4.
        repeat(10) { smoother.update(E4 + 0.02f) }
        repeat(30) { assertFalse(smoother.update(E4 + 12.01f, loudEnough = false)) }
        assertTrue(smoother.hasReading)
        assertEquals(Note(64), smoother.note)
        assertEquals(2f, smoother.cents, 0.01f)
        // A real octave played (the 12th-fret harmonic) comes loud enough, and is followed as before.
        assertFalse(smoother.update(E4 + 12.01f, loudEnough = true))
        assertFalse(smoother.update(E4 + 12.01f, loudEnough = true))
        assertTrue(smoother.update(E4 + 12.01f, loudEnough = true))
        assertEquals(Note(76), smoother.note)
    }

    @Test
    fun `a string sinking into a noisy room reads a multiple of its period without an attack, and the needle holds`() {
        // Phase 46's recordings, a TV across the room: the high E, 1.3 s in and within 8 dB of the
        // TV, read A2 (three of its periods) for 16 frames at −42 dBFS, above the gate, with its
        // level falling or flat; the G string read G2 and then C2, the B string B2. Then nothing.
        var level = -26f
        repeat(10) { smoother.update(E4 + 0.02f, levelDbfs = level); level -= 0.3f }
        level = -42f
        repeat(16) { assertFalse(smoother.update(E4 - TWELFTH + 0.01f, loudEnough = true, levelDbfs = level)); level -= 0.1f }
        assertEquals(Note(64), smoother.note)
        assertEquals(2f, smoother.cents, 0.01f)
        // As the G string went: an octave down (G2), a gap, then a twelfth down (C2, three periods).
        repeat(2) { assertFalse(smoother.update(E4 - 12f, levelDbfs = -42f)) }
        repeat(3) { smoother.noReading(levelDbfs = -42.5f) }
        repeat(5) { assertFalse(smoother.update(E4 - TWELFTH, levelDbfs = -43f)) }
        assertEquals(Note(64), smoother.note)
        // Lost for good: the needle goes blank, and nothing else was shown.
        repeat(PitchSmoother.DEFAULT_MAX_MISSED_FRAMES + 1) { smoother.noReading(levelDbfs = -44f) }
        assertFalse(smoother.hasReading)
    }

    @Test
    fun `a new note an octave away comes with an attack and is followed within three frames`() {
        // The 12th-fret harmonic after the open string: the touch mutes the string for a frame or
        // two, then the pluck rises well over that; or the next string plucked over the last one's tail.
        repeat(10) { smoother.update(E2, levelDbfs = -30f) }
        repeat(2) { smoother.noReading(levelDbfs = -52f) }
        assertFalse(smoother.update(E2 + 12.05f, levelDbfs = -36f))
        assertFalse(smoother.update(E2 + 12.05f, levelDbfs = -36.2f))
        assertTrue(smoother.update(E2 + 12.05f, levelDbfs = -36.4f))
        assertEquals(Note(52), smoother.note)
        assertEquals(5f, smoother.cents, 0.01f)
        // A twelfth up, plucked 12 dB over the ringing note: followed too.
        repeat(10) { smoother.update(E2 + 12f, levelDbfs = -36f) }
        repeat(2) { assertFalse(smoother.update(E2 + 12f + TWELFTH, levelDbfs = -24f)) }
        assertTrue(smoother.update(E2 + 12f + TWELFTH, levelDbfs = -24.3f))
        assertEquals(Note(71), smoother.note)
    }

    @Test
    fun `a lasting reading a whole number of periods away is followed after a second even without an attack`() {
        // A sound that holds an octave down for a second is a sound of its own (a TV singing it, a
        // bowed string slurring down), so the needle follows it in the end.
        repeat(10) { smoother.update(E4, levelDbfs = -30f) }
        repeat(PitchSmoother.DEFAULT_OCTAVE_HOLD_FRAMES - 1) { assertFalse(smoother.update(E4 - 12f, levelDbfs = -30f)) }
        assertEquals(Note(64), smoother.note)
        assertTrue(smoother.update(E4 - 12f, levelDbfs = -30f))
        assertEquals(Note(52), smoother.note)
        // A rise that the room's talk makes (6 dB) is no attack; a pluck's (10 dB and more) is.
        smoother.reset()
        repeat(10) { smoother.update(E4, levelDbfs = -30f) }
        repeat(3) { assertFalse(smoother.update(E4 - 12f, levelDbfs = -24f)) }
        assertEquals(Note(64), smoother.note)
        // The attack comes: the three frames pending already are enough.
        assertTrue(smoother.update(E4 - 12f, levelDbfs = -20f))
        assertEquals(Note(52), smoother.note)
    }

    @Test
    fun `in guitar mode another string two octaves or a twelfth away, plucked a little louder than the ringing one, is followed within three frames`() {
        // The high E plucked over a low E still ringing a second after its pluck, its first 85 ms
        // only 6 dB louder than the low E (StringChangeTest): the frame level rose 8 dB, short of a
        // lone pluck's 10, and the needle held the low E for as long as the high E was heard.
        for ((from, to) in listOf(E2 to E4, E2 to E2 + TWELFTH, A2 to E4)) {
            val guitar = PitchSmoother().apply { strings = Tunings.STANDARD }
            val chromatic = PitchSmoother()
            for (s in listOf(guitar, chromatic)) {
                repeat(10) { s.update(from, levelDbfs = -41f) }
                repeat(2) { s.update(from, levelDbfs = -38f) }
            }
            assertFalse(guitar.update(to, levelDbfs = -34f))
            assertFalse(guitar.update(to, levelDbfs = -33f))
            assertTrue("$from → $to", guitar.update(to, levelDbfs = -32f))
            assertEquals(Note(to.roundToInt()), guitar.note)
            // In any-note mode, where no string tells a string change from a slip, it is held as before.
            repeat(30) { assertFalse(chromatic.update(to, levelDbfs = -33f)) }
            assertEquals(Note(from.roundToInt()), chromatic.note)
        }
    }

    @Test
    fun `in guitar mode a jump onto another string without such a rise is still held`() {
        // The high E sinking into a TV read A2, three periods, a string of the tuning, with its level
        // flat or falling (Phase 46's noisy take: a rise of 0.1 dB at most); the room's talk rose up
        // to 6 dB. Neither is a string change.
        val guitar = PitchSmoother().apply { strings = Tunings.STANDARD }
        var level = -26f
        repeat(10) { guitar.update(E4 + 0.02f, levelDbfs = level); level -= 0.3f }
        level = -42f
        repeat(16) { assertFalse(guitar.update(E4 - TWELFTH + 0.01f, levelDbfs = level)); level -= 0.1f }
        repeat(5) { assertFalse(guitar.update(E4 - 24f, levelDbfs = level + 6f)) }
        assertEquals(Note(64), guitar.note)
        // An octave that is no string of the tuning (E3 in standard) needs the pluck's 10 dB, as ever.
        guitar.reset()
        repeat(10) { guitar.update(E4, levelDbfs = -41f) }
        repeat(10) { assertFalse(guitar.update(E4 - 12f, levelDbfs = -33f)) }
        assertEquals(Note(64), guitar.note)
        // A 12-string's octave string is its course, the same string: a slip to it is held too.
        val twelve = PitchSmoother().apply { strings = Instruments.GUITAR_12.tunings.first() }
        repeat(10) { twelve.update(E2, levelDbfs = -41f) }
        repeat(10) { assertFalse(twelve.update(E2 + 12f, levelDbfs = -33f)) }
        assertEquals(Note(40), twelve.note)
    }

    @Test
    fun `a string change needs two frames on the new string that rise, and restarts on one of them`() {
        // One frame rising 7 dB is a peak of the room's talk or a re-plucked string's slip, not the
        // next string's attack, which lasts several frames.
        val guitar = PitchSmoother().apply { strings = Tunings.STANDARD }
        repeat(12) { guitar.update(E2, levelDbfs = -41f) }
        assertFalse(guitar.update(E4, levelDbfs = -33f))
        repeat(10) { assertFalse(guitar.update(E4, levelDbfs = -36f)) }
        assertEquals(Note(40), guitar.note)
        // Two rising frames on the high E, then a frame at E3 (no string of the tuning, an octave over
        // the low E): the jump doesn't restart there, but on the next frame on the high E.
        guitar.reset()
        repeat(12) { guitar.update(E2, levelDbfs = -41f) }
        assertFalse(guitar.update(E4, levelDbfs = -33f))
        assertFalse(guitar.update(E4, levelDbfs = -33f))
        assertFalse(guitar.update(E2 + 12f, levelDbfs = -33f))
        assertEquals(Note(40), guitar.note)
        assertTrue(guitar.update(E4, levelDbfs = -34f))
        assertEquals(Note(64), guitar.note)
        // With a pluck's full rise any frame of the jump may restart it, as in any-note mode.
        guitar.reset()
        repeat(12) { guitar.update(E2, levelDbfs = -41f) }
        assertFalse(guitar.update(E4, levelDbfs = -30f))
        assertFalse(guitar.update(E2 + 12f, levelDbfs = -30f))
        assertTrue(guitar.update(E2 + 12f, levelDbfs = -30f))
        assertEquals(Note(52), guitar.note)
    }

    @Test
    fun `in Drop D and Open G a jump of one octave up onto the other string needs a pluck's full rise`() {
        // The two D strings of Drop D and Open G, and Open G's two G strings, are an octave apart: a
        // ringing low D re-plucked a little harder and read an octave up (YIN's commonest slip on a
        // low string) lands on a string.
        for (tuning in listOf(Tunings.DROP_D, Tunings.OPEN_G)) {
            for ((from, to) in listOf(D2 to D2 + 12f, G2 to G2 + 12f).filter { (from, _) -> tuning.strings.any { it.midi == from.roundToInt() } }) {
                val guitar = PitchSmoother().apply { strings = tuning }
                repeat(12) { guitar.update(from, levelDbfs = -41f) }
                repeat(10) { assertFalse("${tuning.id}: $from → $to", guitar.update(to, levelDbfs = -33f)) }
                assertEquals(Note(from.roundToInt()), guitar.note)
                // With a pluck's full rise it is followed within three frames, as in any-note mode.
                guitar.reset()
                repeat(12) { guitar.update(from, levelDbfs = -41f) }
                repeat(2) { assertFalse(guitar.update(to, levelDbfs = -30f)) }
                assertTrue(guitar.update(to, levelDbfs = -30f))
                assertEquals(Note(to.roundToInt()), guitar.note)
            }
        }
        // Two octaves stay a string change: Open G's low D to its high D.
        val openG = PitchSmoother().apply { strings = Tunings.OPEN_G }
        repeat(12) { openG.update(D2, levelDbfs = -41f) }
        repeat(2) { assertFalse(openG.update(D2 + 24f, levelDbfs = -33f)) }
        assertTrue(openG.update(D2 + 24f, levelDbfs = -33f))
        assertEquals(Note(62), openG.note)
    }

    @Test
    fun `in Drop D and Open G the lower string plucked an octave under the ringing one is a string change`() {
        // The low D plucked over the D string still ringing, 6 dB louder (StringChangeTest): the
        // detector reads the low D at once, the two strings repeating together at its period, and the
        // frames rise less than a pluck's 10 dB. A slip an octave down is rare on a fresh pluck, so this jump keeps the
        // string change's quick rule; held for a pluck's 10 dB, the needle stayed on the D string a second.
        for (tuning in listOf(Tunings.DROP_D, Tunings.OPEN_G)) {
            for ((from, to) in listOf(D2 + 12f to D2, G2 + 12f to G2).filter { (_, to) -> tuning.strings.any { it.midi == to.roundToInt() } }) {
                val guitar = PitchSmoother().apply { strings = tuning }
                repeat(12) { guitar.update(from, levelDbfs = -41f) }
                repeat(2) { assertFalse(guitar.update(to, levelDbfs = -33f)) }
                assertTrue("${tuning.id}: $from → $to", guitar.update(to, levelDbfs = -33f))
                assertEquals(Note(to.roundToInt()), guitar.note)
            }
        }
    }

    @Test
    fun `what is left of a lost note cannot start a reading at a multiple of its period for a second, unless an attack comes`() {
        // A synthetic high E sinking into a murmur (EdgeCaseTest): once the reading was lost, the
        // detector read E2 and E3 from what was left of it for a few frames, and the needle came
        // back with E2. Only something with an attack, or a note that is no multiple, starts afresh.
        repeat(10) { smoother.update(E4, levelDbfs = -40f) }
        repeat(PitchSmoother.DEFAULT_MAX_MISSED_FRAMES + 1) { smoother.noReading(levelDbfs = -46f) }
        assertFalse(smoother.hasReading)
        repeat(10) { assertFalse(smoother.update(E4 - 24f, levelDbfs = -46f)) }
        repeat(10) { assertFalse(smoother.update(E4 - 12f, levelDbfs = -46.5f)) }
        assertFalse(smoother.hasReading)
        // A fifth away is another note: it starts as ever.
        repeat(3) { assertTrue(smoother.update(E4 - 5f, levelDbfs = -46f)) }
        assertEquals(Note(59), smoother.note)
        // Lost again; a second later the octave below is a note of its own.
        repeat(PitchSmoother.DEFAULT_MAX_MISSED_FRAMES + 1) { smoother.noReading(levelDbfs = -46f) }
        repeat(PitchSmoother.DEFAULT_OCTAVE_HOLD_FRAMES) { smoother.noReading(levelDbfs = -46f) }
        repeat(3) { assertTrue(smoother.update(E4 - 5f - 12f, levelDbfs = -46f)) }
        assertEquals(Note(47), smoother.note)
        // Lost once more; the octave below with an attack (a new pluck) starts at once.
        repeat(PitchSmoother.DEFAULT_MAX_MISSED_FRAMES + 1) { smoother.noReading(levelDbfs = -46f) }
        repeat(3) { assertTrue(smoother.update(E4 - 5f - 24f, levelDbfs = -30f)) }
        assertEquals(Note(35), smoother.note)
    }

    @Test
    fun `a fifth or a fourth away is another string and is snapped to at once, attack or not`() {
        repeat(10) { smoother.update(E2, levelDbfs = -30f) }
        repeat(3) { smoother.update(A2, levelDbfs = -34f) }
        assertEquals(Note(45), smoother.note)
        repeat(3) { smoother.update(E2 + 7f, levelDbfs = -36f) }
        assertEquals(Note(47), smoother.note)
    }

    @Test
    fun `plucking another string jumps straight to it`() {
        repeat(10) { smoother.update(E2) }
        val readings = mutableListOf<Float>()
        repeat(5) {
            smoother.update(A2 - 0.1f)
            readings += smoother.midi
        }
        // Each reading is either still E2 (median not yet flipped) or right on A2; never in between.
        for (midi in readings) assertTrue("slid through $midi", abs(midi - E2) < 0.01f || abs(midi - (A2 - 0.1f)) < 0.01f)
        assertEquals(Note(45), smoother.note)
        assertEquals(-10f, smoother.cents, 0.01f)
    }

    @Test
    fun `a note between two semitones does not flicker`() {
        repeat(10) { smoother.update(E2 + 0.45f) }
        assertEquals(Note(40), smoother.note)
        // Wandering across the half-way point and up to +60 cents keeps E2 (shown as sharp).
        for (offset in listOf(0.48f, 0.52f, 0.55f, 0.6f, 0.5f, 0.62f)) repeat(5) { smoother.update(E2 + offset) }
        assertEquals(Note(40), smoother.note)
        assertTrue(smoother.cents > 50f)
        // Clearly past the hysteresis band, it moves to F2.
        repeat(10) { smoother.update(E2 + 0.7f) }
        assertEquals(Note(41), smoother.note)
        assertEquals(-30f, smoother.cents, 1f)
    }

    @Test
    fun `unsure readings move the needle less than confident ones`() {
        val unsure = PitchSmoother()
        repeat(10) {
            smoother.update(E2, confidence = 1f)
            unsure.update(E2, confidence = 1f)
        }
        repeat(3) {
            smoother.update(E2 + 0.2f, confidence = 0.99f)
            unsure.update(E2 + 0.2f, confidence = 0.85f)
        }
        // Same median input; the confident one has moved further towards +20 cents.
        assertTrue("${smoother.cents} vs ${unsure.cents}", smoother.cents > 2 * unsure.cents)
        assertTrue(unsure.cents > 0f)
        // Both still get there.
        repeat(60) {
            smoother.update(E2 + 0.2f, confidence = 0.99f)
            unsure.update(E2 + 0.2f, confidence = 0.85f)
        }
        assertEquals(20f, smoother.cents, 0.1f)
        assertEquals(20f, unsure.cents, 0.5f)
    }

    @Test
    fun `a reading whose confidence is not a number counts as the least sure, never blanking the needle`() {
        val leastSure = PitchSmoother()
        repeat(10) {
            smoother.update(E2)
            leastSure.update(E2)
        }
        repeat(3) {
            smoother.update(E2 + 0.2f, confidence = Float.NaN)
            leastSure.update(E2 + 0.2f, confidence = 0f)
            assertTrue("still shown", smoother.hasReading)
            assertFalse("a number: ${smoother.cents}", smoother.cents.isNaN())
        }
        assertTrue(smoother.cents > 0f)
        assertEquals(leastSure.cents, smoother.cents, 1e-4f)
    }

    @Test
    fun `an unsure reading of a new string still snaps to it`() {
        repeat(10) { smoother.update(E2) }
        repeat(5) { smoother.update(A2, confidence = 0.8f) }
        assertEquals(Note(45), smoother.note)
        assertEquals(0f, smoother.cents, 0.01f)
    }

    @Test
    fun `short dropouts keep the reading and long ones clear it`() {
        repeat(10) { smoother.update(E2) }
        repeat(PitchSmoother.DEFAULT_MAX_MISSED_FRAMES) { smoother.noReading() }
        assertTrue(smoother.hasReading)
        smoother.update(E2)
        repeat(PitchSmoother.DEFAULT_MAX_MISSED_FRAMES + 1) { smoother.noReading() }
        assertFalse(smoother.hasReading)
        // After clearing, the next note starts afresh with no octave check against the old one.
        repeat(PitchSmoother.DEFAULT_ONSET_FRAMES) { assertTrue(smoother.update(E2 + 12f)) }
        assertEquals(Note(52), smoother.note)
    }

    @Test
    fun `reset clears the reading`() {
        repeat(PitchSmoother.DEFAULT_ONSET_FRAMES) { smoother.update(E2) }
        assertTrue(smoother.hasReading)
        smoother.reset()
        assertFalse(smoother.hasReading)
        assertTrue(smoother.midi.isNaN())
    }

    @Test
    fun `update does not allocate`() {
        val random = Random(3)
        val input = FloatArray(1_000) { E2 + 0.1f * random.nextGaussian().toFloat() }
        fun run() {
            for (m in input) smoother.update(m)
            smoother.noReading()
        }
        repeat(20) { run() }
        assertEquals(0L, allocatedBytes { run() })
    }

    @Test
    fun `invalid settings and input are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { PitchSmoother(medianSize = 0) }
        assertThrows(IllegalArgumentException::class.java) { PitchSmoother(emaAlpha = 0f) }
        assertThrows(IllegalArgumentException::class.java) { PitchSmoother(hysteresisCents = 60f) }
        assertThrows(IllegalArgumentException::class.java) { PitchSmoother(octaveConfirmFrames = 0) }
        assertThrows(IllegalArgumentException::class.java) { PitchSmoother(octaveConfirmFrames = 3, octaveHoldFrames = 2) }
        assertThrows(IllegalArgumentException::class.java) { PitchSmoother(onsetFrames = 0) }
        assertThrows(IllegalArgumentException::class.java) { PitchSmoother(onsetFrames = 6) }
        assertThrows(IllegalArgumentException::class.java) { smoother.update(Float.NaN) }
    }

    private fun spread(values: List<Float>): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    private companion object {
        const val D2 = 38f
        const val E2 = 40f
        const val G2 = 43f
        const val A2 = 45f
        const val E4 = 64f

        /** Three periods: 12 · log2(3) semitones. */
        const val TWELFTH = 19.0196f
    }
}
