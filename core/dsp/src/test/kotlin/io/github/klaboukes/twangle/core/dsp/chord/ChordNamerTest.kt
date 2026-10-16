package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ChordNamerTest {

    private fun mask(vararg pcs: PitchClass): Int = pcs.fold(0) { m, pc -> m or (1 shl pc.ordinal) }

    @Test
    fun `every chord of every root is named from its own tones, in root position and without a bass`() {
        for (root in PitchClass.entries) {
            for (quality in ChordQuality.entries) {
                val chord = Chord.of(root, quality)
                val tones = ChordNamer.maskOf(chord)
                assertSame("$chord with its root in the bass", chord, ChordNamer.name(tones, root.ordinal))
                // Only the sus chords share their tones with another chord (Dsus2 = Asus4).
                if (quality != ChordQuality.SUS2 && quality != ChordQuality.SUS4) {
                    assertSame("$chord without a bass", chord, ChordNamer.name(tones))
                }
            }
        }
    }

    @Test
    fun `chord tones and qualities`() {
        assertEquals(mask(PitchClass.C, PitchClass.E, PitchClass.G), ChordNamer.maskOf(Chord.of(PitchClass.C, ChordQuality.MAJOR)))
        assertEquals(mask(PitchClass.A, PitchClass.C, PitchClass.E), ChordNamer.maskOf(Chord.of(PitchClass.A, ChordQuality.MINOR)))
        assertEquals(mask(PitchClass.G, PitchClass.B, PitchClass.D, PitchClass.F), ChordNamer.maskOf(Chord.of(PitchClass.G, ChordQuality.DOMINANT_SEVENTH)))
        assertEquals(mask(PitchClass.C, PitchClass.E, PitchClass.G, PitchClass.B), ChordNamer.maskOf(Chord.of(PitchClass.C, ChordQuality.MAJOR_SEVENTH)))
        assertEquals(mask(PitchClass.A, PitchClass.C, PitchClass.E, PitchClass.G), ChordNamer.maskOf(Chord.of(PitchClass.A, ChordQuality.MINOR_SEVENTH)))
        assertEquals(mask(PitchClass.D, PitchClass.E, PitchClass.A), ChordNamer.maskOf(Chord.of(PitchClass.D, ChordQuality.SUS2)))
        assertEquals(mask(PitchClass.D, PitchClass.G, PitchClass.A), ChordNamer.maskOf(Chord.of(PitchClass.D, ChordQuality.SUS4)))
        // Roots near the top of the octave wrap round: B major is B D# F#.
        assertEquals(mask(PitchClass.B, PitchClass.D_SHARP, PitchClass.F_SHARP), ChordNamer.maskOf(Chord.of(PitchClass.B, ChordQuality.MAJOR)))
    }

    @Test
    fun `an inversion is named by its chord`() {
        val c = Chord.of(PitchClass.C, ChordQuality.MAJOR)
        assertSame(c, ChordNamer.name(mask(PitchClass.C, PitchClass.E, PitchClass.G), PitchClass.E.ordinal))
        assertSame(c, ChordNamer.name(mask(PitchClass.C, PitchClass.E, PitchClass.G), PitchClass.G.ordinal))
    }

    @Test
    fun `seventh chords may leave out the fifth, triads may not`() {
        assertSame(Chord.of(PitchClass.C, ChordQuality.DOMINANT_SEVENTH), ChordNamer.name(mask(PitchClass.C, PitchClass.E, PitchClass.A_SHARP), 0))
        assertSame(Chord.of(PitchClass.C, ChordQuality.MAJOR_SEVENTH), ChordNamer.name(mask(PitchClass.C, PitchClass.E, PitchClass.B), 0))
        assertSame(Chord.of(PitchClass.C, ChordQuality.MINOR_SEVENTH), ChordNamer.name(mask(PitchClass.C, PitchClass.D_SHARP, PitchClass.A_SHARP), 0))
        // C and E alone could be C major or A minor without its fifth.
        assertNull(ChordNamer.name(mask(PitchClass.C, PitchClass.E), 0))
        assertNull(ChordNamer.name(mask(PitchClass.C, PitchClass.G), 0))
    }

    @Test
    fun `the bass tells sus chords with the same tones apart`() {
        val tones = mask(PitchClass.D, PitchClass.E, PitchClass.A)
        assertSame(Chord.of(PitchClass.D, ChordQuality.SUS2), ChordNamer.name(tones, PitchClass.D.ordinal))
        assertSame(Chord.of(PitchClass.A, ChordQuality.SUS4), ChordNamer.name(tones, PitchClass.A.ordinal))
        assertNull(ChordNamer.name(tones, PitchClass.E.ordinal))
        assertNull(ChordNamer.name(tones))
        val dsus4 = mask(PitchClass.D, PitchClass.G, PitchClass.A)
        assertSame(Chord.of(PitchClass.D, ChordQuality.SUS4), ChordNamer.name(dsus4, PitchClass.D.ordinal))
        assertSame(Chord.of(PitchClass.G, ChordQuality.SUS2), ChordNamer.name(dsus4, PitchClass.G.ordinal))
    }

    @Test
    fun `no name for too few tones, an extra tone, or a chord it doesn't know`() {
        assertNull(ChordNamer.name(0))
        assertNull(ChordNamer.name(mask(PitchClass.E), PitchClass.E.ordinal))
        // C major with an F#.
        assertNull(ChordNamer.name(mask(PitchClass.C, PitchClass.E, PitchClass.F_SHARP, PitchClass.G), 0))
        // C and D major together.
        assertNull(ChordNamer.name(mask(PitchClass.C, PitchClass.D, PitchClass.E, PitchClass.F_SHARP, PitchClass.G, PitchClass.A), 0))
        // Diminished and augmented triads, add9: not among the chords named yet.
        assertNull(ChordNamer.name(mask(PitchClass.B, PitchClass.D, PitchClass.F), PitchClass.B.ordinal))
        assertNull(ChordNamer.name(mask(PitchClass.C, PitchClass.E, PitchClass.G_SHARP), 0))
        assertNull(ChordNamer.name(mask(PitchClass.C, PitchClass.D, PitchClass.E, PitchClass.G), 0))
        // C6 has Am7's tones and is named Am7: C6 isn't among the chords named.
        assertSame(
            Chord.of(PitchClass.A, ChordQuality.MINOR_SEVENTH),
            ChordNamer.name(mask(PitchClass.C, PitchClass.E, PitchClass.G, PitchClass.A), PitchClass.C.ordinal),
        )
    }

    @Test
    fun `naming allocates nothing`() {
        val tones = mask(PitchClass.D, PitchClass.E, PitchClass.A)
        repeat(1000) { ChordNamer.name(tones, 2) }
        assertEquals(0L, allocatedBytes { repeat(1000) { ChordNamer.name(tones, 2) } })
    }
}
