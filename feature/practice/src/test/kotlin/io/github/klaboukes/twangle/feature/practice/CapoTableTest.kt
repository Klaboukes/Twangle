package io.github.klaboukes.twangle.feature.practice

import io.github.klaboukes.twangle.core.dsp.capo.Capo
import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapoTableTest {

    private fun symbols(shapes: SongKey, fret: Int, notation: Notation = Notation.SHARPS): List<Pair<String, String>> {
        val plan = capoPlanOf(shapes, fret)
        return capoChordsOf(shapes).map { plan.shapeSymbolOf(it, notation) to plan.soundingSymbolOf(it, notation) }
    }

    @Test
    fun `the shapes offered are the open keys from C up, major then minor`() {
        assertEquals(
            listOf("C", "D", "E", "G", "A", "Dm", "Em", "Am"),
            CAPO_SHAPES.map { it.symbolOf(Notation.SHARPS) },
        )
        assertEquals(Capo.COMMON_MAJOR_SHAPES.toSet(), CAPO_SHAPES.filterNot { it.minor }.map { it.tonic }.toSet())
        assertEquals(Capo.COMMON_MINOR_SHAPES.toSet(), CAPO_SHAPES.filter { it.minor }.map { it.tonic }.toSet())
    }

    @Test
    fun `the mock-up's table, C shapes at the 3rd fret, sounds in E flat`() {
        assertEquals(
            listOf("C" to "E♭", "Dm" to "Fm", "Em" to "Gm", "F" to "A♭", "G" to "B♭", "Am" to "Cm"),
            symbols(SongKey(PitchClass.C), 3),
        )
        assertEquals("E♭", capoPlanOf(SongKey(PitchClass.C), 3).songKey.symbolOf(Notation.SHARPS))
    }

    @Test
    fun `minor shapes list the minor key's chords`() {
        assertEquals(
            listOf("Am" to "Cm", "C" to "E♭", "Dm" to "Fm", "Em" to "Gm", "F" to "A♭", "G" to "B♭"),
            symbols(SongKey(PitchClass.A, minor = true), 3),
        )
        assertEquals("Cm", capoPlanOf(SongKey(PitchClass.A, minor = true), 3).songKey.symbolOf(Notation.SHARPS))
    }

    @Test
    fun `no capo sounds the shapes themselves`() {
        for (shapes in CAPO_SHAPES) {
            assertTrue("$shapes", symbols(shapes, 0).all { (shape, sounding) -> shape == sounding })
            assertEquals(shapes, capoPlanOf(shapes, 0).songKey)
        }
    }

    @Test
    fun `every fret keeps the shapes' own key and moves the song up by the fret`() {
        for (shapes in CAPO_SHAPES) for (fret in 0..Capo.HIGHEST_FRET) {
            val plan = capoPlanOf(shapes, fret)
            assertEquals("$shapes at $fret", shapes, plan.shapeKey)
            assertEquals("$shapes at $fret", fret, Capo.fretFor(plan.songKey, shapes.tonic))
            assertEquals(6, capoChordsOf(shapes).size)
        }
    }

    @Test
    fun `the sounding chords are written in the song's key and the player's notation`() {
        // D shapes at the 4th fret: F♯ major, whose vi is D♯m (not E♭m).
        assertEquals(
            listOf("D" to "F♯", "Em" to "G♯m", "F♯m" to "A♯m", "G" to "B", "A" to "C♯", "Bm" to "D♯m"),
            symbols(SongKey(PitchClass.D), 4),
        )
        assertEquals(
            listOf("Do" to "Mi♭", "Rem" to "Fam", "Mim" to "Solm", "Fa" to "La♭", "Sol" to "Si♭", "Lam" to "Dom"),
            symbols(SongKey(PitchClass.C), 3, Notation.SOLFEGE),
        )
        // German: G shapes at 3 sound B♭ major, written B; its iii is Dm, its vi Gm.
        assertEquals("B", capoPlanOf(SongKey(PitchClass.G), 3).songKey.symbolOf(Notation.GERMAN))
        assertEquals("H", capoPlanOf(SongKey(PitchClass.G), 4).songKey.symbolOf(Notation.GERMAN))
    }

    @Test
    fun `TalkBack's root follows the key's spelling`() {
        val fSharp = SongKey(PitchClass.F_SHARP)
        assertEquals("A♯", rootInKey(fSharp, Chord.of(PitchClass.A_SHARP, ChordQuality.MINOR), Notation.SHARPS))
        assertEquals("E♭", rootInKey(SongKey(PitchClass.D_SHARP), SongKey(PitchClass.D_SHARP).tonicChord, Notation.SHARPS))
    }

    @Test
    fun `a saved choice the helper doesn't offer opens on C shapes and no capo`() {
        assertEquals(SongKey(PitchClass.G), capoShapesToOpen(SongKey(PitchClass.G)))
        assertEquals(SongKey(PitchClass.C), capoShapesToOpen(SongKey(PitchClass.F_SHARP)))
        assertEquals(SongKey(PitchClass.C), capoShapesToOpen(SongKey(PitchClass.C, minor = true)))
        assertEquals(7, capoFretToOpen(7))
        assertEquals(0, capoFretToOpen(12))
        assertEquals(0, capoFretToOpen(-1))
    }

    @Test
    fun `every row and shape has its own test tag`() {
        assertEquals(CAPO_SHAPES.size, CAPO_SHAPES.map(PracticeTestTags::capoShapes).toSet().size)
        assertEquals("practice_capo_shapes_a_minor", PracticeTestTags.capoShapes(SongKey(PitchClass.A, minor = true)))
        for (shapes in CAPO_SHAPES) assertEquals(6, capoChordsOf(shapes).map(PracticeTestTags::capoRow).toSet().size)
    }
}
