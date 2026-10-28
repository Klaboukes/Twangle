package io.github.klaboukes.twangle.core.dsp.eartraining

import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.smoothing.InTuneDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Play it back's judging (Phase 55): how long a note is held ([HeldNote]) and what that does to an answer ([PlayBackAnswer]). */
class PlayBackTest {

    // ---------- How long a note is held ----------

    @Test
    fun `a note is steady after the in-tune hold and settled after a second, counted in frames`() {
        val held = HeldNote()
        assertEquals(InTuneDetector.DEFAULT_HOLD_SECONDS, held.steadySeconds)
        val g3 = Note.of(PitchClass.G, 3).midi
        val holds = List(60) { held.update(g3, FRAME) }
        val steadyAt = holds.indexOfFirst { it == Hold.STEADY }
        val settledAt = holds.indexOfFirst { it == Hold.SETTLED }
        assertEquals("steady on the frame that makes 0.3 s", framesFor(0.3f) - 1, steadyAt)
        assertEquals("settled on the frame that makes 1 s", framesFor(1f) - 1, settledAt)
        assertTrue("and stays so", holds.drop(settledAt).all { it == Hold.SETTLED })
        assertEquals(g3, held.midi)
    }

    @Test
    fun `another note or none starts the hold afresh`() {
        val held = HeldNote()
        val g3 = Note.of(PitchClass.G, 3).midi
        repeat(framesFor(0.25f)) { held.update(g3, FRAME) }
        assertEquals("a semitone up starts over", Hold.SOUNDING, held.update(g3 + 1, FRAME))
        repeat(framesFor(0.25f)) { held.update(g3 + 1, FRAME) }
        assertEquals(Hold.SOUNDING, held.update(HeldNote.NO_NOTE, FRAME))
        assertEquals(HeldNote.NO_NOTE, held.midi)
        assertEquals("nothing heard never holds", Hold.SOUNDING, held.update(HeldNote.NO_NOTE, 5f))
        assertEquals("back to the start", Hold.SOUNDING, held.update(g3, FRAME))
        repeat(framesFor(0.3f)) { held.update(g3, FRAME) }
        held.reset()
        assertEquals(HeldNote.NO_NOTE, held.midi)
        assertEquals(Hold.SOUNDING, held.update(g3, FRAME))
    }

    @Test
    fun `holds must make sense`() {
        assertThrows(IllegalArgumentException::class.java) { HeldNote(steadySeconds = -0.1f) }
        assertThrows(IllegalArgumentException::class.java) { HeldNote(steadySeconds = 1f, settledSeconds = 0.5f) }
    }

    // ---------- One question's answer ----------

    @Test
    fun `the right note held steady answers the question`() {
        val answer = PlayBackAnswer(question("G3"))
        assertEquals("heard, not held yet", Verdict.NONE, answer.hear(midi("G3"), Hold.SOUNDING, NoteMatch.ANY_OCTAVE))
        assertEquals(Verdict.ALL_FOUND, answer.hear(midi("G3"), Hold.STEADY, NoteMatch.ANY_OCTAVE))
        assertTrue(answer.complete)
        assertFalse("found at once", answer.missed)
        assertEquals("once answered, nothing more", Verdict.NONE, answer.hear(midi("A3"), Hold.SETTLED, NoteMatch.ANY_OCTAVE))
    }

    @Test
    fun `a wrong note counts only once it has settled, and the player plays on until it is found`() {
        val answer = PlayBackAnswer(question("G3"))
        assertEquals("a passing note", Verdict.NONE, answer.hear(midi("F#3"), Hold.STEADY, NoteMatch.ANY_OCTAVE))
        assertFalse(answer.wrongHeld)
        assertEquals(Verdict.WRONG, answer.hear(midi("F#3"), Hold.SETTLED, NoteMatch.ANY_OCTAVE))
        assertTrue(answer.missed && answer.wrongHeld && !answer.complete)
        assertEquals("nothing heard changes nothing", Verdict.NONE, answer.hear(HeldNote.NO_NOTE, Hold.SOUNDING, NoteMatch.ANY_OCTAVE))
        assertTrue("still said", answer.wrongHeld)
        answer.replayed()
        assertFalse("gone when it sounds again", answer.wrongHeld)
        assertEquals(Verdict.ALL_FOUND, answer.hear(midi("G3"), Hold.STEADY, NoteMatch.ANY_OCTAVE))
        assertTrue("found, but missed first", answer.complete && answer.missed)
        assertFalse(answer.wrongHeld)
    }

    @Test
    fun `any octave counts, unless the exact note is asked`() {
        val anyOctave = PlayBackAnswer(question("G3"))
        assertEquals(Verdict.ALL_FOUND, anyOctave.hear(midi("G2"), Hold.STEADY, NoteMatch.ANY_OCTAVE))
        val exact = PlayBackAnswer(question("G3"))
        assertEquals(Verdict.NONE, exact.hear(midi("G2"), Hold.STEADY, NoteMatch.EXACT))
        assertEquals(Verdict.WRONG, exact.hear(midi("G2"), Hold.SETTLED, NoteMatch.EXACT))
        assertEquals(Verdict.ALL_FOUND, exact.hear(midi("G3"), Hold.STEADY, NoteMatch.EXACT))
    }

    @Test
    fun `a sequence is found note by note, in order`() {
        val answer = PlayBackAnswer(question("E3 G3 A3"))
        assertEquals("not the first one", Verdict.NONE, answer.hear(midi("G3"), Hold.STEADY, NoteMatch.ANY_OCTAVE))
        assertEquals(Verdict.NOTE_FOUND, answer.hear(midi("E3"), Hold.STEADY, NoteMatch.ANY_OCTAVE))
        assertEquals(1, answer.found)
        assertEquals("the first still ringing is no wrong answer", Verdict.NONE, answer.hear(midi("E3"), Hold.SETTLED, NoteMatch.ANY_OCTAVE))
        assertFalse(answer.missed)
        assertEquals(Verdict.NOTE_FOUND, answer.hear(midi("G3"), Hold.STEADY, NoteMatch.ANY_OCTAVE))
        assertEquals(Verdict.WRONG, answer.hear(midi("B3"), Hold.SETTLED, NoteMatch.ANY_OCTAVE))
        assertEquals("the notes found stay found", 2, answer.found)
        assertEquals(Verdict.ALL_FOUND, answer.hear(midi("A3"), Hold.STEADY, NoteMatch.ANY_OCTAVE))
        assertTrue(answer.complete && answer.missed)
    }

    @Test
    fun `a next note an octave away is never taken from the one just found while it rings`() {
        val answer = PlayBackAnswer(question("G2 G3"))
        assertEquals(Verdict.NOTE_FOUND, answer.hear(midi("G2"), Hold.STEADY, NoteMatch.ANY_OCTAVE))
        assertEquals("still G2", Verdict.NONE, answer.hear(midi("G2"), Hold.SETTLED, NoteMatch.ANY_OCTAVE))
        assertEquals(1, answer.found)
        // Played again after a pause, any G counts once more.
        answer.hear(HeldNote.NO_NOTE, Hold.SOUNDING, NoteMatch.ANY_OCTAVE)
        assertEquals(Verdict.ALL_FOUND, answer.hear(midi("G2"), Hold.STEADY, NoteMatch.ANY_OCTAVE))
    }

    private fun framesFor(seconds: Float): Int = kotlin.math.ceil(seconds / FRAME - 1e-3f).toInt()

    private fun midi(name: String): Int = note(name).midi

    private fun note(name: String): Note {
        val octave = name.last().digitToInt()
        val pitch = PitchClass.entries.first { it.symbol.replace("♯", "#") == name.dropLast(1) }
        return Note.of(pitch, octave)
    }

    private fun question(names: String) = NoteQuestion(names.split(" ").map(::note))

    private companion object {
        /** One hop at 48 kHz, as the tuner's. */
        const val FRAME = 1024f / 48_000f
    }
}
