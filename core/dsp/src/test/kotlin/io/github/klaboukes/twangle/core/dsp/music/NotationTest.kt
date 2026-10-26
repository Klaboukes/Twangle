package io.github.klaboukes.twangle.core.dsp.music

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
    fun `german letters call B H and B flat B`() {
        assertEquals("C C♯ D D♯ E F F♯ G G♯ A A♯ H", names(Notation.GERMAN))
        val flats = PitchClass.entries.joinToString(" ") { Notation.GERMAN.nameOf(it, Accidental.FLAT) }
        assertEquals("C D♭ D E♭ E F G♭ G A♭ A B H", flats)
        assertEquals("E♭ A♭ D♭ G♭ B E♭", strings(Tunings.HALF_STEP_DOWN, Notation.GERMAN))
        assertEquals("E A D G H E", strings(Tunings.STANDARD, Notation.GERMAN))
        assertEquals("H0", Notation.GERMAN.nameOf(Note.of(PitchClass.B, 0)))
    }

    @Test
    fun `each language gets the notation its players usually write`() {
        for (language in listOf("de", "pl", "ru")) assertEquals(language, Notation.GERMAN, Notation.forLanguage(language))
        for (language in listOf("fr", "it", "es", "pt", "tr", "ar")) {
            assertEquals(language, Notation.SOLFEGE, Notation.forLanguage(language))
        }
        for (language in listOf("en", "nl", "zh", "ja", "ko", "hi", "in", "id", "")) {
            assertEquals(language, Notation.SHARPS, Notation.forLanguage(language))
        }
        assertEquals(Notation.DEFAULT, Notation.forLanguage("xx"))
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
    fun `solfege with flats lowers the next note`() {
        val names = PitchClass.entries.joinToString(" ") { Notation.SOLFEGE.nameOf(it, Accidental.FLAT) }
        assertEquals("Do Re♭ Re Mi♭ Mi Fa Sol♭ Sol La♭ La Si♭ Si", names)
    }

    @Test
    fun `each notation spells with its own sign`() {
        assertEquals(Accidental.SHARP, Notation.SHARPS.spelling)
        assertEquals(Accidental.FLAT, Notation.FLATS.spelling)
        assertEquals(Accidental.SHARP, Notation.SOLFEGE.spelling)
        assertEquals(Accidental.SHARP, Notation.GERMAN.spelling)
    }

    private fun strings(tuning: Tuning, notation: Notation) =
        tuning.strings.joinToString(" ") { notation.nameIn(tuning, it.pitchClass) }

    @Test
    fun `half step down is spelled with flats whatever the setting`() {
        val tuning = Tunings.HALF_STEP_DOWN
        assertEquals("E♭ A♭ D♭ G♭ B♭ E♭", strings(tuning, Notation.SHARPS))
        assertEquals("E♭ A♭ D♭ G♭ B♭ E♭", strings(tuning, Notation.FLATS))
        assertEquals("Mi♭ La♭ Re♭ Sol♭ Si♭ Mi♭", strings(tuning, Notation.SOLFEGE))
    }

    @Test
    fun `open D and open E keep their sharps with the flats setting`() {
        assertEquals("D A D F♯ A D", strings(Tunings.OPEN_D, Notation.FLATS))
        assertEquals("E B E G♯ B E", strings(Tunings.OPEN_E, Notation.FLATS))
        assertEquals("Mi Si Mi Sol♯ Si Mi", strings(Tunings.OPEN_E, Notation.SOLFEGE))
    }

    @Test
    fun `every preset is spelled the way players write it`() {
        val expected = mapOf(
            Tunings.STANDARD to "E A D G B E",
            Tunings.DROP_D to "D A D G B E",
            Tunings.HALF_STEP_DOWN to "E♭ A♭ D♭ G♭ B♭ E♭",
            Tunings.FULL_STEP_DOWN to "D G C F A D",
            Tunings.DADGAD to "D A D G A D",
            Tunings.OPEN_G to "D G D G B D",
            Tunings.OPEN_D to "D A D F♯ A D",
            Tunings.OPEN_E to "E B E G♯ B E",
            Tunings.OPEN_C to "C G C G C E",
        )
        assertEquals(Tunings.all.toSet(), expected.keys)
        for (notation in listOf(Notation.SHARPS, Notation.FLATS)) {
            for ((tuning, names) in expected) assertEquals("${tuning.id}, $notation", names, strings(tuning, notation))
        }
    }

    @Test
    fun `a tuning without a spelling follows the notation`() {
        val tuning = Tuning("custom", listOf(Note.of(PitchClass.C_SHARP, 3)))
        assertEquals("C♯", strings(tuning, Notation.SHARPS))
        assertEquals("D♭", strings(tuning, Notation.FLATS))
        assertEquals("Do♯", strings(tuning, Notation.SOLFEGE))
    }

    @Test
    fun `tuning names get their octave`() {
        assertEquals("E♭2", Notation.SHARPS.nameIn(Tunings.HALF_STEP_DOWN, Tunings.HALF_STEP_DOWN.strings[0]))
        assertEquals("Sol♭3", Notation.SOLFEGE.nameIn(Tunings.HALF_STEP_DOWN, Tunings.HALF_STEP_DOWN.strings[3]))
        assertEquals("F♯3", Notation.FLATS.nameIn(Tunings.OPEN_D, Tunings.OPEN_D.strings[3]))
    }

    @Test
    fun `names are always the same instances, so nothing is allocated per frame`() {
        for (notation in Notation.entries) {
            for (pitchClass in PitchClass.entries) {
                assertSame(notation.nameOf(pitchClass), notation.nameOf(pitchClass))
                for (spelling in Accidental.entries) {
                    assertSame(notation.nameOf(pitchClass, spelling), notation.nameOf(pitchClass, spelling))
                }
                assertSame(
                    notation.nameIn(Tunings.HALF_STEP_DOWN, pitchClass),
                    notation.nameIn(Tunings.HALF_STEP_DOWN, pitchClass),
                )
            }
        }
    }
}
