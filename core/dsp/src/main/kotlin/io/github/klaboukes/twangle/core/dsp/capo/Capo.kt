package io.github.klaboukes.twangle.core.dsp.capo

import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordSpelling
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass

/**
 * A song in [songKey] played with the capo at [fret]: the player fingers shapes in [shapeKey], and
 * every shape sounds [fret] semitones higher, since a capo shortens every string by the same number
 * of frets (one fret = one equal-tempered semitone). E♭ with the capo at 1 is played with D shapes:
 * the D shape sounds E♭, G sounds A♭, A sounds B♭, Bm sounds Cm.
 *
 * Chords the capo makes sound are written as [songKey] writes them, the shapes as [shapeKey] does
 * ([SongKey.symbolOf]), so a song in F with the capo at 1 reads E → F, A → B♭, C♯m → Dm. Pure.
 */
data class CapoPlan(val songKey: SongKey, val fret: Int) {

    init {
        require(fret in 0..Capo.HIGHEST_FRET) { "capo fret $fret is outside 0..${Capo.HIGHEST_FRET}" }
    }

    /** The key the shapes are in: [songKey] moved down by [fret] semitones (E♭ at fret 1: D). */
    val shapeKey: SongKey = songKey.transposed(-fret)

    /** The chord [shape] sounds with the capo on: [fret] semitones higher (G at fret 3: B♭). */
    fun soundingOf(shape: Chord): Chord = shape.transposed(fret)

    /** The shape to play for [sounding] with the capo on: [fret] semitones lower (B♭ at fret 3: G). */
    fun shapeOf(sounding: Chord): Chord = sounding.transposed(-fret)

    /**
     * What [shape] sounds like, written in [songKey] in [notation]: "B♭", "Cm", "Si♭". [shapeBass] is
     * the bass the shape plays (the E of a C/E shape), or null for the root; it moves up with the capo
     * too ("E♭/G" for C/E at fret 3).
     */
    fun soundingSymbolOf(shape: Chord, notation: Notation, shapeBass: PitchClass? = null): String =
        songKey.symbolOf(soundingOf(shape), shapeBass?.transposed(fret), notation)

    /** [shape]'s own name, written in [shapeKey] in [notation]: "G", "Bm", "D/F♯". */
    fun shapeSymbolOf(shape: Chord, notation: Notation, shapeBass: PitchClass? = null): String =
        shapeKey.symbolOf(shape, shapeBass, notation)
}

/**
 * The capo arithmetic: where the capo goes to play a song with the shapes the player knows. Pure,
 * and small enough that the screen asks again on every change.
 */
object Capo {

    /** The highest fret offered: fret 12 sounds an octave up, the same chords as no capo at all. */
    const val HIGHEST_FRET = 11

    /** The open-position major keys guitarists play in, the shapes offered for a major song: C, A, G, E, D (CAGED). */
    val COMMON_MAJOR_SHAPES: List<PitchClass> = listOf(PitchClass.C, PitchClass.A, PitchClass.G, PitchClass.E, PitchClass.D)

    /** The open-position minor keys, the shapes offered for a minor song: Am, Em, Dm. */
    val COMMON_MINOR_SHAPES: List<PitchClass> = listOf(PitchClass.A, PitchClass.E, PitchClass.D)

    /**
     * The capo fret at which shapes in the key of [shapeTonic] sound in [songKey]:
     * `fret = (song tonic − shape tonic) mod 12`, 0..[HIGHEST_FRET] (E♭ with G shapes: (3 − 7) mod 12 = 8;
     * with D shapes: 1). A shape can only be moved up, so a song lower than the shapes wraps round to
     * the next octave's fret.
     */
    fun fretFor(songKey: SongKey, shapeTonic: PitchClass): Int = (songKey.tonic.ordinal - shapeTonic.ordinal).mod(12)

    /** The plan for [songKey] played with shapes in the key of [shapeTonic] (G shapes: G, C, D, Em …). */
    fun planFor(songKey: SongKey, shapeTonic: PitchClass): CapoPlan = CapoPlan(songKey, fretFor(songKey, shapeTonic))

    /**
     * The ways to play [songKey] with the common shapes ([COMMON_MAJOR_SHAPES] or
     * [COMMON_MINOR_SHAPES]), lowest fret first, so the first is the one to suggest: no capo when the
     * song is in an open key, else the capo nearest the nut, where the neck is easiest to play and the
     * sound is fullest. E♭: D shapes at 1, C at 3, A at 6, G at 8, E at 11.
     */
    fun optionsFor(songKey: SongKey): List<CapoPlan> =
        (if (songKey.minor) COMMON_MINOR_SHAPES else COMMON_MAJOR_SHAPES)
            .map { planFor(songKey, it) }
            .sortedBy { it.fret }

    /**
     * What [shape] sounds like with the capo at [fret], when the song's key isn't known: the
     * chord's own spelling ([ChordSpelling]) in [notation], with [shapeBass] moved up too
     * (G at 3: "B♭"; C/E at 3: "E♭/G").
     */
    fun soundingSymbolOf(shape: Chord, fret: Int, notation: Notation, shapeBass: PitchClass? = null): String =
        ChordSpelling.symbolOf(shape.transposed(fret), shapeBass?.transposed(fret), notation)
}
