package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.music.Accidental
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.A
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.A_SHARP
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.B
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.C
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.C_SHARP
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.D
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.D_SHARP
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.E
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.F
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.F_SHARP
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.G
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.G_SHARP
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.ADD9
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.AUGMENTED
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.DIMINISHED
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.DIMINISHED_SEVENTH
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.DOMINANT_SEVENTH
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.HALF_DIMINISHED
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.MAJOR
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.MAJOR_SEVENTH
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.MINOR
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.MINOR_SEVENTH
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.MAJOR_NINTH
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.MINOR_NINTH
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.MINOR_SIXTH
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.POWER_CHORD
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.SEVENTH_SHARP_NINE
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.NINTH
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.SEVENTH_SUS4
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.SIXTH
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.SUS2
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.SUS4
import org.junit.Assert.assertEquals
import org.junit.Test

class ChordSpellingTest {

    private fun symbol(root: PitchClass, quality: ChordQuality, bass: PitchClass? = null, notation: Notation = Notation.SHARPS) =
        ChordSpelling.symbolOf(Chord.of(root, quality), bass, notation)

    private fun tones(root: PitchClass, quality: ChordQuality, notation: Notation = Notation.SHARPS): String {
        val chord = Chord.of(root, quality)
        return ChordSpelling.tonesOf(chord).joinToString(" ") { ChordSpelling.nameOf(it, chord, notation) }
    }

    @Test
    fun `symbols of the common chords`() {
        assertEquals("Am", symbol(A, MINOR))
        assertEquals("C", symbol(C, MAJOR))
        assertEquals("G7", symbol(G, DOMINANT_SEVENTH))
        assertEquals("Cmaj7", symbol(C, MAJOR_SEVENTH))
        assertEquals("Em7", symbol(E, MINOR_SEVENTH))
        assertEquals("Dsus2", symbol(D, SUS2))
        assertEquals("Dsus4", symbol(D, SUS4))
    }

    @Test
    fun `symbols of the further chords`() {
        assertEquals("G7sus4", symbol(G, SEVENTH_SUS4))
        assertEquals("C6", symbol(C, SIXTH))
        assertEquals("Am6", symbol(A, MINOR_SIXTH))
        assertEquals("Cadd9", symbol(C, ADD9))
        assertEquals("E9", symbol(E, NINTH))
        assertEquals("Bdim", symbol(B, DIMINISHED))
        assertEquals("C♯dim7", symbol(C_SHARP, DIMINISHED_SEVENTH))
        assertEquals("Bm7♭5", symbol(B, HALF_DIMINISHED))
        assertEquals("E♭aug", symbol(D_SHARP, AUGMENTED))
        assertEquals("Lam6", symbol(A, MINOR_SIXTH, notation = Notation.SOLFEGE))
        assertEquals("Hdim", symbol(B, DIMINISHED, notation = Notation.GERMAN))
    }

    @Test
    fun `power chords, the 7 sharp 9 and the 9ths`() {
        assertEquals("E5", symbol(E, POWER_CHORD))
        assertEquals("B♭5", symbol(A_SHARP, POWER_CHORD))
        assertEquals("F♯5", symbol(F_SHARP, POWER_CHORD))
        assertEquals("Do5", symbol(C, POWER_CHORD, notation = Notation.SOLFEGE))
        assertEquals("E5/B", symbol(E, POWER_CHORD, bass = B))
        assertEquals("E7♯9", symbol(E, SEVENTH_SHARP_NINE))
        assertEquals("Cm9", symbol(C, MINOR_NINTH))
        assertEquals("Cmaj9", symbol(C, MAJOR_NINTH))
        assertEquals("F♯m9", symbol(F_SHARP, MINOR_NINTH))
        assertEquals("B♭maj9", symbol(A_SHARP, MAJOR_NINTH))
        assertEquals("Hm9", symbol(B, MINOR_NINTH, notation = Notation.GERMAN))

        assertEquals("E B", tones(E, POWER_CHORD))
        assertEquals("C G", tones(C, POWER_CHORD))
        assertEquals("B♭ F", tones(A_SHARP, POWER_CHORD))
        assertEquals("F♯ C♯", tones(F_SHARP, POWER_CHORD))
        // The ninth as a second, as in the 9th chord; the minor 9th in the minor key.
        assertEquals("C D E♭ G B♭", tones(C, MINOR_NINTH))
        assertEquals("A B C E G", tones(A, MINOR_NINTH))
        assertEquals("E F♯ G B D", tones(E, MINOR_NINTH))
        assertEquals("C D E G B", tones(C, MAJOR_NINTH))
        assertEquals("F G A C E", tones(F, MAJOR_NINTH))
        assertEquals("D E F♯ A C♯", tones(D, MAJOR_NINTH))
        // The sharp ninth is sharp in a flat key too, and the note it sounds when the key has no such sharp.
        assertEquals("C D♯ E G B♭", tones(C, SEVENTH_SHARP_NINE))
        assertEquals("F G♯ A C E♭", tones(F, SEVENTH_SHARP_NINE))
        assertEquals("B♭ C♯ D F A♭", tones(A_SHARP, SEVENTH_SHARP_NINE))
        assertEquals("E G G♯ B D", tones(E, SEVENTH_SHARP_NINE))
        assertEquals("A C C♯ E G", tones(A, SEVENTH_SHARP_NINE))
        assertEquals("G A♯ B D F", tones(G, SEVENTH_SHARP_NINE))
        assertEquals("Mi Sol Sol♯ Si Re", tones(E, SEVENTH_SHARP_NINE, Notation.SOLFEGE))
        assertEquals("Do Re♯ Mi Sol Si♭", tones(C, SEVENTH_SHARP_NINE, Notation.SOLFEGE))
    }

    @Test
    fun `the Chords panel holds all twenty, in five full rows of four, the most used first`() {
        val panel = ChordQuality.inPanel
        assertEquals("each chord type once", ChordQuality.entries.toSet(), panel.toSet())
        assertEquals(ChordQuality.entries.size, panel.size)
        assertEquals("five full rows of four", 0, panel.size % 4)
        assertEquals(5, panel.size / 4)
        assertEquals(
            listOf("C Cm C5 C7", "Cmaj7 Cm7 Csus2 Csus4", "C7sus4 C6 Cm6 Cadd9", "C9 Cm9 Cmaj9 C7♯9", "Cdim Cdim7 Cm7♭5 Caug"),
            panel.chunked(4).map { row -> row.joinToString(" ") { symbol(C, it) } },
        )
        assertEquals("the power chord in the top row", 0, panel.indexOf(POWER_CHORD) / 4)
    }

    @Test
    fun `altered fifths and sixths are spelled by their sign`() {
        // A diminished fifth is flat on a white root, the minor key's sign otherwise.
        assertEquals("E G B♭", tones(E, DIMINISHED))
        assertEquals("A C E♭ G♭", tones(A, DIMINISHED_SEVENTH))
        assertEquals("B D F A", tones(B, HALF_DIMINISHED))
        assertEquals("C♯ E G", tones(C_SHARP, DIMINISHED))
        assertEquals("E♭ G♭ A C", tones(D_SHARP, DIMINISHED_SEVENTH))
        // An augmented fifth is sharp on a white root, the major key's sign otherwise.
        assertEquals("C E G♯", tones(C, AUGMENTED))
        assertEquals("F A C♯", tones(F, AUGMENTED))
        assertEquals("A♭ C E", tones(G_SHARP, AUGMENTED))
        // A minor 6th's sixth is major: Am6 has an F♯, Gm6 a B♭ and an E.
        assertEquals("A C E F♯", tones(A, MINOR_SIXTH))
        assertEquals("G B♭ D E", tones(G, MINOR_SIXTH))
        assertEquals("C♯ E G♯ A♯", tones(C_SHARP, MINOR_SIXTH))
        assertEquals("C D E G B♭", tones(C, NINTH))
        assertEquals("F B♭ C E♭", tones(F, SEVENTH_SUS4))
    }

    @Test
    fun `sharps and flats follow the usual chord spelling whatever the notation's own sign`() {
        for (notation in listOf(Notation.SHARPS, Notation.FLATS)) {
            assertEquals("B♭", symbol(A_SHARP, MAJOR, notation = notation))
            assertEquals("E♭", symbol(D_SHARP, MAJOR, notation = notation))
            assertEquals("A♭", symbol(G_SHARP, MAJOR, notation = notation))
            assertEquals("D♭", symbol(C_SHARP, MAJOR, notation = notation))
            assertEquals("F♯", symbol(F_SHARP, MAJOR, notation = notation))
            assertEquals("F♯m", symbol(F_SHARP, MINOR, notation = notation))
            assertEquals("C♯m7", symbol(C_SHARP, MINOR_SEVENTH, notation = notation))
            assertEquals("G♯m", symbol(G_SHARP, MINOR, notation = notation))
            assertEquals("E♭m", symbol(D_SHARP, MINOR, notation = notation))
            assertEquals("B♭m", symbol(A_SHARP, MINOR, notation = notation))
            assertEquals("B♭maj7", symbol(A_SHARP, MAJOR_SEVENTH, notation = notation))
            assertEquals("F♯7", symbol(F_SHARP, DOMINANT_SEVENTH, notation = notation))
        }
    }

    @Test
    fun `a chord's tones are spelled in its key`() {
        assertEquals("A C E", tones(A, MINOR))
        assertEquals("C E G", tones(C, MAJOR))
        assertEquals("G B D F", tones(G, DOMINANT_SEVENTH))
        assertEquals("C E G B♭", tones(C, DOMINANT_SEVENTH))
        assertEquals("F A C E♭", tones(F, DOMINANT_SEVENTH))
        assertEquals("A C♯ E G", tones(A, DOMINANT_SEVENTH))
        assertEquals("E G♯ B D", tones(E, DOMINANT_SEVENTH))
        assertEquals("B D♯ F♯ A", tones(B, DOMINANT_SEVENTH))
        assertEquals("G B♭ D", tones(G, MINOR))
        assertEquals("F A♭ C", tones(F, MINOR))
        assertEquals("B♭ D F", tones(A_SHARP, MAJOR))
        assertEquals("F♯ A C♯", tones(F_SHARP, MINOR))
        assertEquals("D E A", tones(D, SUS2))
        assertEquals("D G A", tones(D, SUS4))
        assertEquals("F B♭ C", tones(F, SUS4))
        assertEquals("B E F♯", tones(B, SUS4))
        // E♯ and C♭ have no name here: the same note's (F, B).
        assertEquals("F♯ A♯ C♯ F", tones(F_SHARP, MAJOR_SEVENTH))
    }

    @Test
    fun `slash chords name the bass in the chord's spelling, and not when it is the root`() {
        assertEquals("C/E", symbol(C, MAJOR, E))
        assertEquals("C", symbol(C, MAJOR, C))
        assertEquals("D/F♯", symbol(D, MAJOR, F_SHARP))
        assertEquals("Gm/B♭", symbol(G, MINOR, A_SHARP))
        assertEquals("Am7/G", symbol(A, MINOR_SEVENTH, G))
    }

    @Test
    fun `German letters write B as H and B flat as B, solfege writes La`() {
        assertEquals("Hm", symbol(B, MINOR, notation = Notation.GERMAN))
        assertEquals("B", symbol(A_SHARP, MAJOR, notation = Notation.GERMAN))
        assertEquals("H7", symbol(B, DOMINANT_SEVENTH, notation = Notation.GERMAN))
        assertEquals("F♯m", symbol(F_SHARP, MINOR, notation = Notation.GERMAN))
        assertEquals("Lam", symbol(A, MINOR, notation = Notation.SOLFEGE))
        assertEquals("Do", symbol(C, MAJOR, notation = Notation.SOLFEGE))
        assertEquals("Sol7", symbol(G, DOMINANT_SEVENTH, notation = Notation.SOLFEGE))
        assertEquals("Si♭", symbol(A_SHARP, MAJOR, notation = Notation.SOLFEGE))
        assertEquals("Do/Mi", symbol(C, MAJOR, E, notation = Notation.SOLFEGE))
    }

    @Test
    fun `every chord has its tones from the root, and one sign`() {
        for (root in PitchClass.entries) {
            for (quality in ChordQuality.entries) {
                val chord = Chord.of(root, quality)
                val tones = ChordSpelling.tonesOf(chord)
                assertEquals(root, tones.first())
                assertEquals(Integer.bitCount(quality.mask), tones.size)
                assertEquals(ChordNamer.maskOf(chord), tones.fold(0) { m, pc -> m or (1 shl pc.ordinal) })
                val sign = ChordSpelling.accidentalOf(chord)
                // A 7♯9's sharp ninth is sharp in a flat key too (C7♯9's D♯).
                val ninth = if (quality == SEVENTH_SHARP_NINE) root.transposed(3) else null
                val names = tones.filter { it != ninth }.map { ChordSpelling.nameOf(it, chord, Notation.SHARPS) }
                val other = if (sign == Accidental.SHARP) "♭" else "♯"
                assertEquals("$chord: $names", false, names.any { other in it })
            }
        }
    }

    @Test
    fun `the chord name is offered for strummed instruments only`() {
        val offered = Instruments.all.filter(ChordInstruments::offers)
        assertEquals(listOf(Instruments.GUITAR, Instruments.GUITAR_7, Instruments.GUITAR_8, Instruments.UKULELE, Instruments.MANDOLIN, Instruments.BANJO_5), offered)
    }
}
