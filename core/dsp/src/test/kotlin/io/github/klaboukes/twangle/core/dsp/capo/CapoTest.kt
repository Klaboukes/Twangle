package io.github.klaboukes.twangle.core.dsp.capo

import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.DOMINANT_SEVENTH
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.MAJOR
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.MINOR
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CapoTest {

    private fun major(root: PitchClass) = Chord.of(root, MAJOR)
    private fun minor(root: PitchClass) = Chord.of(root, MINOR)

    /** "1 D, 3 C …": each option's fret and its shapes' key, lowest fret first. */
    private fun options(key: SongKey) =
        Capo.optionsFor(key).joinToString(", ") { "${it.fret} ${it.shapeKey.symbolOf(Notation.SHARPS)}" }

    /** The song's chords as the capo makes the [shapes] sound, written in the song's key. */
    private fun sounding(plan: CapoPlan, vararg shapes: Chord, notation: Notation = Notation.SHARPS) =
        shapes.joinToString(" ") { plan.soundingSymbolOf(it, notation) }

    @Test
    fun `transposing wraps round the octave`() {
        assertEquals(G, E.transposed(3))
        assertEquals(C, B.transposed(1))
        assertEquals(A_SHARP, C.transposed(-2))
        assertEquals(D, D.transposed(24))
        assertSame(Chord.of(A_SHARP, DOMINANT_SEVENTH), Chord.of(G, DOMINANT_SEVENTH).transposed(3))
    }

    @Test
    fun `the fret for a key and a set of shapes`() {
        val eFlat = SongKey(D_SHARP)
        assertEquals(1, Capo.fretFor(eFlat, D))
        assertEquals(3, Capo.fretFor(eFlat, C))
        assertEquals(6, Capo.fretFor(eFlat, A))
        assertEquals(8, Capo.fretFor(eFlat, G))
        assertEquals(11, Capo.fretFor(eFlat, E))
        assertEquals(0, Capo.fretFor(SongKey(G), G))
        assertEquals(2, Capo.fretFor(SongKey(A), G))
        assertEquals(5, Capo.fretFor(SongKey(F_SHARP, minor = true), C_SHARP))
    }

    @Test
    fun `every song key with every shape key gives a fret from 0 to 11 that moves the shapes onto the song`() {
        for (song in PitchClass.entries) for (shapes in PitchClass.entries) for (isMinor in listOf(false, true)) {
            val plan = Capo.planFor(SongKey(song, isMinor), shapes)
            assertTrue(plan.fret in 0..Capo.HIGHEST_FRET)
            assertEquals(shapes, plan.shapeKey.tonic)
            assertEquals(isMinor, plan.shapeKey.minor)
            assertSame(plan.songKey.tonicChord, plan.soundingOf(plan.shapeKey.tonicChord))
        }
    }

    @Test
    fun `the options for a major song, lowest fret first`() {
        assertEquals("1 D, 3 C, 6 A, 8 G, 11 E", options(SongKey(D_SHARP)))
        assertEquals("0 G, 3 E, 5 D, 7 C, 10 A", options(SongKey(G)))
        assertEquals("1 E, 3 D, 5 C, 8 A, 10 G", options(SongKey(F)))
        assertEquals("1 A, 3 G, 6 E, 8 D, 10 C", options(SongKey(A_SHARP)))
        assertEquals("1 C, 4 A, 6 G, 9 E, 11 D", options(SongKey(C_SHARP)))
    }

    @Test
    fun `the options for a minor song use the open minor keys`() {
        assertEquals("1 Am, 6 Em, 8 Dm", options(SongKey(A_SHARP, minor = true)))
        assertEquals("0 Em, 2 Dm, 7 Am", options(SongKey(E, minor = true)))
        assertEquals("3 Am, 8 Em, 10 Dm", options(SongKey(C, minor = true)))
        assertEquals("2 Em, 4 Dm, 9 Am", options(SongKey(F_SHARP, minor = true)))
    }

    @Test
    fun `the first option is no capo whenever the song is in a common shape's key`() {
        for (tonic in Capo.COMMON_MAJOR_SHAPES) assertEquals(0, Capo.optionsFor(SongKey(tonic)).first().fret)
        for (tonic in Capo.COMMON_MINOR_SHAPES) assertEquals(0, Capo.optionsFor(SongKey(tonic, minor = true)).first().fret)
    }

    @Test
    fun `every key has one option per common shape at different frets, the lowest near the nut`() {
        for (tonic in PitchClass.entries) for (isMinor in listOf(false, true)) {
            val frets = Capo.optionsFor(SongKey(tonic, isMinor)).map { it.fret }
            val shapes = if (isMinor) Capo.COMMON_MINOR_SHAPES else Capo.COMMON_MAJOR_SHAPES
            assertEquals(shapes.size, frets.toSet().size)
            assertEquals(frets.sorted(), frets)
            assertTrue("$tonic minor=$isMinor: $frets", frets.first() <= if (isMinor) 4 else 2)
        }
    }

    @Test
    fun `what the shapes sound like, spelled for the song's key`() {
        // E♭ with the capo at 1 and D shapes: D G A Bm sound E♭ A♭ B♭ Cm.
        val eFlat = CapoPlan(SongKey(D_SHARP), 1)
        assertEquals("E♭ A♭ B♭ Cm", sounding(eFlat, major(D), major(G), major(A), minor(B)))
        // E♭ with G shapes at 8: G C D Em sound the same chords.
        val eFlatG = Capo.planFor(SongKey(D_SHARP), G)
        assertEquals("E♭ A♭ B♭ Cm", sounding(eFlatG, major(G), major(C), major(D), minor(E)))
        // F with E shapes at 1.
        assertEquals("F B♭ C Dm", sounding(CapoPlan(SongKey(F), 1), major(E), major(A), major(B), minor(C_SHARP)))
        // A with G shapes at 2: the song's sharps.
        assertEquals("A D E F♯m", sounding(CapoPlan(SongKey(A), 2), major(G), major(C), major(D), minor(E)))
        // C♯m with Am shapes at 4.
        assertEquals("C♯m F♯m G♯m A E", sounding(CapoPlan(SongKey(C_SHARP, minor = true), 4), minor(A), minor(D), minor(E), major(F), major(C)))
    }

    @Test
    fun `the key's own sign wins over the chord's usual one for chords in the key`() {
        // F♯ major's vi is A♯m (ChordSpelling alone writes B♭m), D♭ major's IV is G♭ (alone: F♯).
        assertEquals("F♯ B C♯ A♯m D♯m", sounding(CapoPlan(SongKey(F_SHARP), 2), major(E), major(A), major(B), minor(G_SHARP), minor(C_SHARP)))
        assertEquals("D♭ G♭ A♭ B♭m", sounding(CapoPlan(SongKey(C_SHARP), 1), major(C), major(F), major(G), minor(A)))
        // A chord from outside the key keeps its usual spelling: D major's borrowed B♭ (an F shape at 5), C major's A♭.
        assertEquals("B♭", CapoPlan(SongKey(D), 5).soundingSymbolOf(major(F), Notation.SHARPS))
        assertEquals("A♭", CapoPlan(SongKey(C), 0).soundingSymbolOf(major(G_SHARP), Notation.SHARPS))
    }

    @Test
    fun `the shapes are written in their own key`() {
        // F with E shapes at 1: the shapes are E's, so C♯m, not D♭m.
        val plan = CapoPlan(SongKey(F), 1)
        assertEquals("C♯m", plan.shapeSymbolOf(plan.shapeOf(minor(D)), Notation.SHARPS))
        assertEquals("E A B C♯m", listOf(major(F), major(A_SHARP), major(C), minor(D)).joinToString(" ") {
            plan.shapeSymbolOf(plan.shapeOf(it), Notation.SHARPS)
        })
        assertEquals(major(G), CapoPlan(SongKey(D_SHARP), 3).shapeOf(Chord.of(A_SHARP, MAJOR)))
    }

    @Test
    fun `the bass moves up with the capo`() {
        val plan = CapoPlan(SongKey(D_SHARP), 3)
        assertEquals("E♭/G", plan.soundingSymbolOf(major(C), Notation.SHARPS, shapeBass = E))
        assertEquals("C/E", plan.shapeSymbolOf(major(C), Notation.SHARPS, shapeBass = E))
        assertEquals("E♭", plan.soundingSymbolOf(major(C), Notation.SHARPS, shapeBass = C))
        assertEquals("D/F♯", CapoPlan(SongKey(D), 0).shapeSymbolOf(major(D), Notation.SHARPS, shapeBass = F_SHARP))
    }

    @Test
    fun `names follow the player's notation`() {
        val plan = CapoPlan(SongKey(D_SHARP), 1)
        assertEquals("Mi♭ La♭ Si♭ Dom", sounding(plan, major(D), major(G), major(A), minor(B), notation = Notation.SOLFEGE))
        assertEquals("E♭ A♭ B Cm", sounding(plan, major(D), major(G), major(A), minor(B), notation = Notation.GERMAN))
        assertEquals("E♭ A♭ B♭ Cm", sounding(plan, major(D), major(G), major(A), minor(B), notation = Notation.FLATS))
        assertEquals("Hm", CapoPlan(SongKey(B, minor = true), 0).songKey.symbolOf(Notation.GERMAN))
        assertEquals("Re", plan.shapeKey.symbolOf(Notation.SOLFEGE))
    }

    @Test
    fun `keys are written with their own sign`() {
        assertEquals("E♭", SongKey(D_SHARP).symbolOf(Notation.SHARPS))
        assertEquals("B♭", SongKey(A_SHARP).symbolOf(Notation.SHARPS))
        assertEquals("F♯", SongKey(F_SHARP).symbolOf(Notation.FLATS))
        assertEquals("C♯m", SongKey(C_SHARP, minor = true).symbolOf(Notation.FLATS))
        assertEquals("G♯m", SongKey(G_SHARP, minor = true).symbolOf(Notation.SHARPS))
        assertEquals("E♭m", SongKey(D_SHARP, minor = true).symbolOf(Notation.SHARPS))
        assertEquals("Cm", SongKey(C, minor = true).symbolOf(Notation.SHARPS))
    }

    @Test
    fun `without the song's key a shape is named in the chord's usual spelling`() {
        assertEquals("B♭", Capo.soundingSymbolOf(major(G), 3, Notation.SHARPS))
        assertEquals("E♭/G", Capo.soundingSymbolOf(major(C), 3, Notation.SHARPS, shapeBass = E))
        assertEquals("F♯m", Capo.soundingSymbolOf(minor(E), 2, Notation.FLATS))
        assertEquals("C", Capo.soundingSymbolOf(major(C), 0, Notation.SHARPS))
    }

    @Test
    fun `shapeOf undoes soundingOf at every fret`() {
        for (fret in 0..Capo.HIGHEST_FRET) {
            val plan = CapoPlan(SongKey(C), fret)
            for (root in PitchClass.entries) for (quality in ChordQuality.entries) {
                val chord = Chord.of(root, quality)
                assertSame(chord, plan.shapeOf(plan.soundingOf(chord)))
            }
        }
    }

    @Test
    fun `a capo fret outside 0 to 11 is refused`() {
        assertThrows(IllegalArgumentException::class.java) { CapoPlan(SongKey(C), -1) }
        assertThrows(IllegalArgumentException::class.java) { CapoPlan(SongKey(C), 12) }
    }
}
