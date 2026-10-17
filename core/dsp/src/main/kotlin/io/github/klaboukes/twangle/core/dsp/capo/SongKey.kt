package io.github.klaboukes.twangle.core.dsp.capo

import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordSpelling
import io.github.klaboukes.twangle.core.dsp.music.Accidental
import io.github.klaboukes.twangle.core.dsp.music.KeySpelling
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.scale.Scale
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType

/**
 * The key a song is in, e.g. E♭ major or C minor: the key the Capo helper is asked about, and the key
 * the player's shapes are in ([CapoPlan.shapeKey]). It writes itself with the key's own sign
 * ([KeySpelling], as chord symbols and scales do): E♭ (not D♯), F♯m, G♯m, and, as guitar charts do
 * with six signs, F♯ (not G♭) and E♭m (not D♯m).
 */
data class SongKey(val tonic: PitchClass, val minor: Boolean = false) {

    /** The key's tonic chord: E♭ for E♭ major, Cm for C minor. */
    val tonicChord: Chord = Chord.of(tonic, if (minor) ChordQuality.MINOR else ChordQuality.MAJOR)

    /** The key's notes: its major or natural minor scale. */
    val scale: Scale = Scale(tonic, if (minor) ScaleType.NATURAL_MINOR else ScaleType.MAJOR)

    /** The sign the key writes its black keys with: flats in E♭, F and Cm, sharps in D, E and F♯m. */
    val accidental: Accidental = if (minor) KeySpelling.ofMinorKey(tonic) else KeySpelling.ofMajorKey(tonic)

    /** The key [semitones] higher (lower when negative), major or minor as this one. */
    fun transposed(semitones: Int): SongKey = SongKey(tonic.transposed(semitones), minor)

    /** The key's name in [notation]: "E♭", "Cm", "Mi♭", "Hm" (German B minor). */
    fun symbolOf(notation: Notation): String = ChordSpelling.symbolOf(tonicChord, null, notation)

    /**
     * [chord]'s symbol as written in this key, with [bass] after a "/" when it isn't the root: the
     * key's sign for a root or bass that belongs to the key (A♯m in F♯ major, G♭ in D♭ major, D/F♯ in
     * D), the chord's own spelling for one borrowed from outside ([ChordSpelling]: B♭ in D major,
     * A♭ in C).
     */
    fun symbolOf(chord: Chord, bass: PitchClass?, notation: Notation): String {
        val symbol = nameOf(chord.root, chord, notation) + ChordSpelling.suffixOf(chord.quality)
        return if (bass == null || bass == chord.root) symbol else symbol + "/" + nameOf(bass, chord, notation)
    }

    private fun nameOf(pitchClass: PitchClass, chord: Chord, notation: Notation): String =
        if (pitchClass in scale) notation.nameOf(pitchClass, accidental) else ChordSpelling.nameOf(pitchClass, chord, notation)
}
