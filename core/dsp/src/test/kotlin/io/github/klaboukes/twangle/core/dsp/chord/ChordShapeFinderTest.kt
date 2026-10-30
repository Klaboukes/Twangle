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
import io.github.klaboukes.twangle.core.dsp.music.Note
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
    fun `a lowered tuning plays the same shapes for the chords as far down`() {
        // Drop C (Drop D a step down): Drop D's D shape is a C, its G shape an F, and so on.
        for (lowered in 1..5) {
            for (root in PitchClass.entries) {
                for (quality in listOf(MAJOR, MINOR, DOMINANT_SEVENTH, POWER_CHORD)) {
                    assertEquals(
                        "$root $quality, Drop D lowered $lowered",
                        ChordShapeFinder.shapesOf(Chord.of(root, quality), Tunings.DROP_D).map { "$it ${it.fingerString()}" },
                        ChordShapeFinder.shapesOf(Chord.of(root.transposed(-lowered), quality), Tunings.DROP_D.lowered(lowered)).map { "$it ${it.fingerString()}" },
                    )
                }
            }
        }
    }

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
    fun `baritone ukulele shapes are the guitar's top four strings`() {
        val baritone = Instruments.UKULELE.tuningById("ukulele_baritone")!!
        assertEquals("0003", first(G, MAJOR, baritone).toString())
        assertEquals("2010", first(C, MAJOR, baritone).toString())
        assertEquals("0232", first(D, MAJOR, baritone).toString())
        assertEquals("2210", first(A, MINOR, baritone).toString())
        assertEquals("2000", first(E, MINOR, baritone).toString())
        for (shape in ChordShapeFinder.shapesOf(Chord.of(D, MAJOR), baritone)) {
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
        assertEquals("000000", first(C, MAJOR, Tunings.OPEN_C).toString())
        assertEquals("555555", first(F, MAJOR, Tunings.OPEN_C).toString())
        // The player's own tuning: open A.
        assertEquals("000000", first(A, MAJOR, tuningOf("my_open_a", "E2 A2 E3 A3 C#4 E4")).toString())
    }

    @Test
    fun `every shape sounds exactly the chord on every preset`() {
        // The banjo's too: its short fifth string follows the drone rule (checkDroneRule).
        val tunings = Instruments.all.flatMap { it.tunings }
        val strummedStandards = Instruments.all.filter(ChordInstruments::offers).map { it.tunings.first() }
        for (tuning in tunings) for (quality in ChordQuality.entries) for (root in PitchClass.entries) {
            val chord = Chord.of(root, quality)
            val shapes = ChordShapeFinder.shapesOf(chord, tuning)
            // The chords heard of up to four tones everywhere; the five-tone 9ths and the chart-only
            // chords on the strummed instruments' standard tunings; elsewhere a few are out of reach
            // (open G's F♯add9, DADGAD's C♯m9), and the chart says so. A ukulele's four strings have no
            // G♯m9 or F♯maj9 within four frets with the root in it, and the banjo's open G no B♭m9 or
            // D♭maj9 (BANJO_NINTHS_OUT_OF_REACH).
            val everywhere = quality.detected && Integer.bitCount(quality.mask) <= 4
            val ukuleleGap = tuning in Instruments.UKULELE.tunings && chord in UKULELE_NINTHS_OUT_OF_REACH
            val banjoGap = tuning == Instruments.BANJO_5.defaultTuning && chord in BANJO_NINTHS_OUT_OF_REACH
            val reachable = (everywhere || tuning in strummedStandards) && !ukuleleGap && !banjoGap
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
                // Five strings from the nut or more: the root in the bass. A banjo has four, so it may
                // play an inversion, as a ukulele does.
                if (ChordShapeFinder.stringsFromNut(tuning).size >= 5) {
                    val bass = shape.bassIn(tuning).pitchClass
                    assertTrue("$label has ${bass} in the bass", bass == root || shape.frets.distinct().size == 1)
                }
                checkDroneRule(shape, tuning, label)
            }
        }
    }

    /**
     * A string that starts up the neck (the banjo's short fifth) is never pressed at or below its start;
     * it rings open where its open note is a chord tone, and is left out only where no chord tone on it
     * lies within the shape's span with a finger to spare (the owner's drone rule, option B).
     */
    private fun checkDroneRule(shape: ChordShape, tuning: Tuning, label: String) {
        for (s in 0 until tuning.stringCount) {
            val start = tuning.startFretOf(s)
            if (start == 0) continue
            val fret = shape.frets[s]
            assertTrue("$label: string $s pressed at $fret, at or below its start at $start", fret <= 0 || fret > start)
            if (shape.chord.quality == POWER_CHORD) {
                assertTrue("$label: a power chord leaves the short string out", shape.isMuted(s))
                continue
            }
            val tones = ChordSpelling.tonesOf(shape.chord).toSet()
            val openIsTone = tuning.strings[s].pitchClass in tones
            assertEquals("$label: string $s rings open exactly where its open note is a chord tone", openIsTone, shape.isOpen(s))
            if (!shape.isMuted(s)) continue
            // Within the span of the frets pressed, anywhere above its start when none is.
            val pressed = shape.frets.filter { it > 0 }
            val from = maxOf(start + 1, (pressed.maxOrNull() ?: 0) - ChordShapeFinder.MAX_SPAN)
            val to = minOf((pressed.minOrNull() ?: ChordShapeFinder.HIGHEST_FRET) + ChordShapeFinder.MAX_SPAN, ChordShapeFinder.HIGHEST_FRET)
            for (f in from..to) {
                if (Note(tuning.midiAt(s, f)).pitchClass !in tones) continue
                val frets = shape.frets.toIntArray().also { it[s] = f }
                assertNull("$label: string $s left out, though fret $f sounds a chord tone", ChordShapeFinder.fingeringOf(frets))
            }
        }
    }

    @Test
    fun `the banjo's first chords come out as banjo books show them`() {
        val openG = Instruments.BANJO_5.defaultTuning
        // Short string first: g D G B D. The beginner's first three, with their fingers.
        val g = first(G, MAJOR, openG)
        assertEquals("00000", g.toString())
        assertEquals(listOf("G4", "D3", "G3", "B3", "D4"), g.notesIn(openG).map { it.toString() })
        val c = first(C, MAJOR, openG)
        assertEquals("02012", c.toString())
        assertEquals("-2-13", c.fingerString())
        assertEquals("E3 in the bass, as banjo books play it", listOf("G4", "E3", "G3", "C4", "E4"), c.notesIn(openG).map { it.toString() })
        assertNull(c.barre)
        val d7 = first(D, DOMINANT_SEVENTH, openG)
        // The open g is no D7 tone, and no D7 tone on the short string lies within four frets of 1–4.
        assertEquals("x0214", d7.toString())
        assertEquals("--214", d7.fingerString())
        assertEquals(listOf(null, "D3", "A3", "C4", "F#4"), d7.notesIn(openG).map { it?.toString() })
        // The next ones a beginner learns.
        val expected = listOf(
            Triple(E, MINOR, "02002"),
            Triple(A, MINOR, "x2212"),
            Triple(A, MAJOR, "x2222"),
            Triple(F, MAJOR, "x3213"),
            Triple(D, MINOR, "x0233"),
            Triple(G, DOMINANT_SEVENTH, "00003"),
            Triple(C, DOMINANT_SEVENTH, "02312"),
            Triple(E, DOMINANT_SEVENTH, "x2100"),
            Triple(B, DOMINANT_SEVENTH, "x1201"),
        )
        for ((root, quality, frets) in expected) assertEquals("$root $quality", frets, first(root, quality, openG).toString())
        // Double C and double D strum their own chord open, or nearly.
        assertEquals("00002", first(C, MAJOR, Instruments.BANJO_5.tuningById("banjo_double_c")!!).toString())
        assertEquals("00000", first(D, MAJOR, Instruments.BANJO_5.tuningById("banjo_double_d")!!).toString())
    }

    @Test
    fun `the banjo's short string is fretted above its start where its open note isn't in the chord`() {
        val openG = Instruments.BANJO_5.defaultTuning
        // A shape up the neck frets the short string at the 7th fret: its a, D7's fifth.
        val d7 = ChordShapeFinder.shapesOf(Chord.of(D, DOMINANT_SEVENTH), openG).first { it.frets[0] > 0 }
        assertEquals("$d7", 7, d7.frets[0])
        assertEquals(A, d7.notesIn(openG)[0]!!.pitchClass)
        // D up the neck: the whole neck barred at the 7th, the short string's a under the barre.
        val d = ChordShapeFinder.shapesOf(Chord.of(D, MAJOR), openG).first { it.toString() == "77777" }
        assertEquals(Barre(fret = 7, firstString = 0, lastString = 4), d.barre)
        assertEquals(listOf("A4", "A3", "D4", "F#4", "A4"), d.notesIn(openG).map { it.toString() })
        // A7: the open g is its 7th, so it rings over the barre at the 2nd fret.
        assertEquals("02222", first(A, DOMINANT_SEVENTH, openG).toString())
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
                assertTrue("$label: on the thick strings", played.first() in ChordShapeFinder.powerFirstStrings(tuning))
                assertTrue("$label: on strings from the nut", played.all { tuning.startFretOf(it) == 0 })
                assertEquals("$label: the root lowest", root, shape.bassIn(tuning).pitchClass)
            }
        }
        // A banjo's start on its 4th or 3rd string, the short one left out.
        assertEquals(listOf(1, 2), ChordShapeFinder.powerFirstStrings(Instruments.BANJO_5.defaultTuning))
        assertEquals(listOf(0, 1, 2), ChordShapeFinder.powerFirstStrings(Tunings.STANDARD))
        assertEquals("x023x", first(D, POWER_CHORD, Instruments.BANJO_5.defaultTuning).toString())
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

        /**
         * On the banjo's open G, B♭m9 and D♭maj9 need four tones on the four long strings, none of
         * them open, and the short string pressed too would be a fifth finger with no barre to share.
         */
        val BANJO_NINTHS_OUT_OF_REACH = setOf(Chord.of(A_SHARP, MINOR_NINTH), Chord.of(C_SHARP, MAJOR_NINTH))
    }
}
