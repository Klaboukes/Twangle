package com.intune.core.dsp.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NotationTest {

    private fun names(notation: Notation) = PitchClass.entries.joinToString(" ") { notation.nameOf(it) }

    @Test
    fun `sharps name every pitch class`() {
        assertEquals("C C♯ D D♯ E F F♯ G G♯ A A♯ B", names(Notation.SHARPS))
    }

    @Test
    fun `flats spell the black keys as flats`() {
        assertEquals("C D♭ D E♭ E F G♭ G A♭ A B♭ B", names(Notation.FLATS))
    }

    @Test
    fun `solfege is fixed do with Si for B`() {
        assertEquals("Do Do♯ Re Re♯ Mi Fa Fa♯ Sol Sol♯ La La♯ Si", names(Notation.SOLFEGE))
    }

    @Test
    fun `sharps and flats name the same pitch as enharmonic equivalents`() {
        // C♯ = D♭: a semitone above the natural before it, a semitone below the one after.
        for (pitchClass in PitchClass.entries) {
            val sharp = Notation.SHARPS.nameOf(pitchClass)
            val flat = Notation.FLATS.nameOf(pitchClass)
            if (sharp.length == 1) {
                assertEquals(sharp, flat)
            } else {
                assertEquals(PitchClass.entries[pitchClass.ordinal - 1].symbol, sharp.take(1))
                assertEquals(PitchClass.entries[pitchClass.ordinal + 1].symbol, flat.take(1))
            }
        }
    }

    @Test
    fun `notes get their octave`() {
        assertEquals("E2", Notation.SHARPS.nameOf(Tunings.STANDARD.strings[0]))
        assertEquals("G♭3", Notation.FLATS.nameOf(Tunings.HALF_STEP_DOWN.strings[3]))
        assertEquals("La2", Notation.SOLFEGE.nameOf(Tunings.STANDARD.strings[1]))
    }

    @Test
    fun `names are always the same instances, so nothing is allocated per frame`() {
        for (notation in Notation.entries) {
            for (pitchClass in PitchClass.entries) {
                assertSame(notation.nameOf(pitchClass), notation.nameOf(pitchClass))
            }
        }
    }
}
