package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.AUGMENTED
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.DIMINISHED_SEVENTH
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.DOMINANT_SEVENTH
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.HALF_DIMINISHED
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.MAJOR
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.MAJOR_SEVENTH
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.MINOR
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.MAJOR_NINTH
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.MINOR_NINTH
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.MINOR_SIXTH
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.POWER_CHORD
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.SEVENTH_SHARP_NINE
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.SIXTH
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality.SUS4
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.A
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.A_SHARP
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.B
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.C
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.C_SHARP
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.D
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.E
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.F
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.F_SHARP
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.G
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.G_SHARP
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.music.tuningOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordShapeFinderTest {

    private fun first(root: PitchClass, quality: ChordQuality, tuning: Tuning = Tunings.STANDARD): ChordShape =
        ChordShapeFinder.shapesOf(Chord.of(root, quality), tuning).first()

    private fun ChordShape.fingerString() = fingers.joinToString("") { if (it == 0) "-" else it.toString() }

    @Test
    fun `standard tuning gives the chord books' first shapes`() {
        val expected = listOf(
            Triple(C, MAJOR, "x32010"),
            Triple(G, MAJOR, "320003"),
            Triple(D, MAJOR, "xx0232"),
            Triple(A, MAJOR, "x02220"),
            Triple(E, MAJOR, "022100"),
            Triple(A, MINOR, "x02210"),
            Triple(E, MINOR, "022000"),
            Triple(D, MINOR, "xx0231"),
            Triple(F, MAJOR, "133211"),
            Triple(B, MINOR, "x24432"),
            Triple(E, DOMINANT_SEVENTH, "020100"),
            Triple(C, DOMINANT_SEVENTH, "x32310"),
            Triple(C, MAJOR_SEVENTH, "x32000"),
            Triple(D, SUS4, "xx0233"),
            Triple(F, MAJOR_SEVENTH, "xx3210"),
        )
        for ((root, quality, frets) in expected) assertEquals("$root $quality", frets, first(root, quality).toString())
    }

    @Test
    fun `the further chords come out as the chord books' open shapes`() {
        val expected = listOf(
            Triple(C, SIXTH, "x32210"),
            Triple(D, SIXTH, "xx0202"),
            Triple(E, SIXTH, "022120"),
            Triple(G, SIXTH, "320000"),
            Triple(A, SIXTH, "x02222"),
            Triple(E, MINOR_SIXTH, "022020"),
            Triple(D, HALF_DIMINISHED, "xx0111"),
            Triple(B, HALF_DIMINISHED, "x20201"),
            Triple(D, DIMINISHED_SEVENTH, "xx0101"),
            Triple(D, AUGMENTED, "xx0332"),
        )
        for ((root, quality, frets) in expected) assertEquals("$root $quality", frets, first(root, quality).toString())
    }

    @Test
    fun `open shapes get one finger per fret from the nut`() {
        assertEquals("-32-1-", first(C, MAJOR).fingerString())
        assertEquals("-231--", first(E, MAJOR).fingerString())
        assertEquals("--231-", first(A, MINOR).fingerString())
        assertEquals("---132", first(D, MAJOR).fingerString())
        assertEquals("---231", first(D, MINOR).fingerString())
        assertEquals("-2-1--", first(E, DOMINANT_SEVENTH).fingerString())
        assertEquals("-32---", first(C, MAJOR_SEVENTH).fingerString())
        assertNull(first(C, MAJOR).barre)
    }

    @Test
    fun `F and Bm are barre chords with the index finger across`() {
        val f = first(F, MAJOR)
        assertEquals(Barre(fret = 1, firstString = 0, lastString = 5), f.barre)
        assertEquals("134211", f.fingerString())
        val bm = first(B, MINOR)
        assertEquals(Barre(fret = 2, firstString = 1, lastString = 5), bm.barre)
        assertEquals("-13421", bm.fingerString())
        assertEquals("fits the first four frets, drawn from the nut", 1, bm.startFret())
        assertEquals(1, f.startFret())
    }

    @Test
    fun `a chord higher up the neck starts its chart at its lowest fret`() {
        val c = ChordShapeFinder.shapesOf(Chord.of(C, MAJOR), Tunings.STANDARD).first { it.toString() == "8(10)(10)988" }
        assertEquals(8, c.lowestFret)
        assertEquals(10, c.highestFret)
        assertEquals(8, c.startFret())
        assertEquals(Barre(fret = 8, firstString = 0, lastString = 5), c.barre)
    }

    @Test
    fun `ukulele shapes play every string`() {
        val ukulele = Instruments.UKULELE.defaultTuning
        assertEquals("0003", first(C, MAJOR, ukulele).toString())
        assertEquals("---3", first(C, MAJOR, ukulele).fingerString())
        assertEquals("0232", first(G, MAJOR, ukulele).toString())
        assertEquals("2010", first(F, MAJOR, ukulele).toString())
        assertEquals("2000", first(A, MINOR, ukulele).toString())
        val bFlat = first(A_SHARP, MAJOR, ukulele)
        assertEquals("3211", bFlat.toString())
        assertEquals(Barre(fret = 1, firstString = 2, lastString = 3), bFlat.barre)
        for (shape in ChordShapeFinder.shapesOf(Chord.of(D, MAJOR), ukulele)) {
            assertTrue("$shape mutes a string", (0 until 4).none(shape::isMuted))
        }
    }

    @Test
    fun `mandolin G is 0023`() {
        assertEquals("0023", first(G, MAJOR, Instruments.MANDOLIN.defaultTuning).toString())
    }

    @Test
    fun `DADGAD gets DADGAD shapes with its open strings ringing`() {
        val d = first(D, MAJOR, Tunings.DADGAD)
        assertEquals("004200", d.toString())
        assertEquals("--42--", d.fingerString())
        assertEquals("003200", first(D, MINOR, Tunings.DADGAD).toString())
        assertEquals(
            "a G with open strings among the first two",
            true,
            ChordShapeFinder.shapesOf(Chord.of(G, MAJOR), Tunings.DADGAD).take(2).any { s -> s.frets.count { it == 0 } >= 3 },
        )
    }

    @Test
    fun `an open tuning strums its own chord on every string`() {
        assertEquals("000000", first(G, MAJOR, Tunings.OPEN_G).toString())
        assertEquals("555555", first(C, MAJOR, Tunings.OPEN_G).toString())
        assertEquals("000000", first(D, MAJOR, Tunings.OPEN_D).toString())
        // The player's own tuning: open C.
        assertEquals("000000", first(C, MAJOR, tuningOf("my_open_c", "C2 G2 C3 G3 C4 E4")).toString())
    }

    @Test
    fun `every shape sounds exactly the chord on every preset`() {
        val tunings = Instruments.all.flatMap { it.tunings }
        val strummedStandards = Instruments.all.filter(ChordInstruments::offers).map { it.tunings.first() }
        for (tuning in tunings) for (quality in ChordQuality.entries) for (root in PitchClass.entries) {
            val chord = Chord.of(root, quality)
            val shapes = ChordShapeFinder.shapesOf(chord, tuning)
            // The chords heard of up to four tones everywhere; the five-tone 9ths and the chart-only
            // chords on the strummed instruments' standard tunings; elsewhere a few are out of reach
            // (open G's F♯add9, DADGAD's C♯m9), and the chart says so. A ukulele's four strings have no
            // G♯m9 or F♯maj9 within four frets with the root in it.
            val everywhere = quality.detected && Integer.bitCount(quality.mask) <= 4
            val ukuleleGap = tuning in Instruments.UKULELE.tunings && chord in UKULELE_NINTHS_OUT_OF_REACH
            val reachable = (everywhere || tuning in strummedStandards) && !ukuleleGap
            if (tuning.stringCount >= 4 && reachable) assertTrue("${tuning.id} has no $root $quality", shapes.isNotEmpty())
            val tones = ChordSpelling.tonesOf(chord).toSet()
            for (shape in shapes) {
                val sounded = shape.notesIn(tuning).filterNotNull().map { it.pitchClass }.toSet()
                val label = "${tuning.id} $root $quality $shape"
                assertTrue("$label sounds a note outside the chord", tones.containsAll(sounded))
                val needed = if (quality.fifthOptional) tones - PitchClass.entries[(root.ordinal + 7) % 12] else tones
                assertTrue("$label leaves out a tone", sounded.containsAll(needed))
                assertTrue("$label stretches too far", shape.lowestFret == 0 || shape.highestFret - shape.lowestFret <= ChordShapeFinder.MAX_SPAN)
                assertTrue("$label goes above the 12th fret", shape.highestFret <= ChordShapeFinder.HIGHEST_FRET)
                assertTrue("$label needs a fifth finger", shape.fingers.all { it in 0..4 })
                for (s in 0 until shape.stringCount) {
                    assertEquals("$label: a finger only on a pressed string", shape.frets[s] > 0, shape.fingers[s] > 0)
                }
                if (tuning.stringCount >= 5) {
                    val bass = shape.bassIn(tuning).pitchClass
                    assertTrue("$label has ${bass} in the bass", bass == root || shape.frets.distinct().size == 1)
                }
            }
        }
    }

    @Test
    fun `power chords are root, fifth and octave on the thick strings, the rest muted`() {
        val expected = listOf(
            E to "022xxx", F to "133xxx", G to "355xxx", A to "x022xx", B to "x244xx", C to "x355xx", D to "xx023x",
        )
        for ((root, frets) in expected) assertEquals("${root}5", frets, first(root, POWER_CHORD).toString())
        assertEquals("134---", first(F, POWER_CHORD).fingerString())
        assertNull(first(F, POWER_CHORD).barre)
        assertEquals("00xx", first(C, POWER_CHORD, Instruments.UKULELE.defaultTuning).toString())
        assertEquals("00xx", first(G, POWER_CHORD, Instruments.MANDOLIN.defaultTuning).toString())
    }

    @Test
    fun `every power chord plays two or three neighbouring strings, the root lowest`() {
        for (tuning in Instruments.all.flatMap { it.tunings }) for (root in PitchClass.entries) {
            for (shape in ChordShapeFinder.shapesOf(Chord.of(root, POWER_CHORD), tuning)) {
                val label = "${tuning.id} ${root}5 $shape"
                val played = shape.frets.indices.filter { !shape.isMuted(it) }
                assertTrue(label, played.size in 2..3)
                assertEquals("$label: neighbouring strings", played.size - 1, played.last() - played.first())
                assertTrue("$label: on the thick strings", played.first() <= ChordShapeFinder.powerFirstStringMax(tuning.stringCount))
                assertEquals("$label: the root lowest", root, shape.bassIn(tuning).pitchClass)
            }
        }
    }

    @Test
    fun `the 9ths and the 7 sharp 9 come out as chord book shapes`() {
        val expected = listOf(
            Triple(C, MINOR_NINTH, "x31333"),
            Triple(E, MINOR_NINTH, "020002"),
            Triple(A, MINOR_NINTH, "575557"),
            Triple(C, MAJOR_NINTH, "x30000"),
            Triple(F, MAJOR_NINTH, "102010"),
            Triple(E, SEVENTH_SHARP_NINE, "020133"),
        )
        for ((root, quality, frets) in expected) assertEquals("$root $quality", frets, first(root, quality).toString())
        val cm9 = first(C, MINOR_NINTH)
        assertEquals("the ring finger across the last three strings", Barre(fret = 3, firstString = 3, lastString = 5, finger = 3), cm9.barre)
        assertEquals("-21333", cm9.fingerString())
        // No shape that strums to the top string: the top string muted too.
        assertEquals("x4354x", first(C_SHARP, MAJOR_NINTH).toString())
        for (quality in listOf(SEVENTH_SHARP_NINE, MINOR_NINTH, MAJOR_NINTH)) for (root in PitchClass.entries) {
            assertTrue("$root $quality", ChordShapeFinder.shapesOf(Chord.of(root, quality), Tunings.STANDARD).isNotEmpty())
        }
    }

    @Test
    fun `strings are left out only on the thick side`() {
        for (quality in ChordQuality.entries - POWER_CHORD) for (root in PitchClass.entries) {
            // D♭maj9 has no shape that strums to the top string; its top string is muted too.
            if (root == C_SHARP && quality == MAJOR_NINTH) continue
            for (shape in ChordShapeFinder.shapesOf(Chord.of(root, quality), Tunings.STANDARD)) {
                val played = shape.frets.indexOfFirst { it != ChordShape.MUTED }
                assertTrue("$shape", (played until shape.stringCount).none(shape::isMuted))
                assertTrue("$shape plays fewer than four strings", shape.stringCount - played >= 4)
            }
        }
    }

    @Test
    fun `fewer shapes when asked and none for a single string`() {
        assertEquals(2, ChordShapeFinder.shapesOf(Chord.of(C, MAJOR), Tunings.STANDARD, limit = 2).size)
        assertTrue(ChordShapeFinder.shapesOf(Chord.of(C, MAJOR), tuningOf("one", "C3")).isEmpty())
    }

    @Test
    fun `notes and chart text`() {
        val c = first(C, MAJOR)
        assertEquals(listOf(null, "C3", "E3", "G3", "C4", "E4"), c.notesIn(Tunings.STANDARD).map { it?.toString() })
        assertEquals("C3", c.bassIn(Tunings.STANDARD).toString())
        assertTrue(c.isMuted(0))
        assertTrue(c.isOpen(3))
        assertFalse(c.isOpen(1))
        assertEquals("x(10)(12)", ChordShape(c.chord, listOf(-1, 10, 12), listOf(0, 1, 3)).toString())
    }

    @Test
    fun `the fingers arch over open strings`() {
        assertEquals(0, ChordShapeFinder.archOf(listOf(-1, 3, 2, 0, 1, 0)))
        assertEquals(1, ChordShapeFinder.archOf(listOf(3, 2, 0, 0, 0, 3)))
        assertEquals(1, ChordShapeFinder.archOf(listOf(1, 0, 2, 2, 1, 0)))
        assertEquals(2, ChordShapeFinder.archOf(listOf(-1, 1, 0, 3, 3, 1)))
    }

    @Test
    fun `a whole chord list is quick`() {
        val start = System.nanoTime()
        for (quality in ChordQuality.entries) for (root in PitchClass.entries) ChordShapeFinder.shapesOf(Chord.of(root, quality), Tunings.STANDARD)
        val perChordMs = (System.nanoTime() - start) / 1e6 / (ChordQuality.entries.size * 12)
        assertTrue("$perChordMs ms a chord", perChordMs < 20.0)
    }

    private companion object {
        val UKULELE_NINTHS_OUT_OF_REACH = setOf(Chord.of(G_SHARP, MINOR_NINTH), Chord.of(F_SHARP, MAJOR_NINTH))
    }
}
