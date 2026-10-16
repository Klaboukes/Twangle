package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.music.Accidental
import io.github.klaboukes.twangle.core.dsp.music.KeySpelling
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass

/**
 * How a chord is written: its symbol ("Am", "B♭7", "F♯m", "C/E") in the player's [Notation]
 * (letters, German H and B, or solfège: "Lam"), and its tones. Sharps and flats follow the usual chord
 * spelling, whatever the notation's own sign: the sign of the key the chord is named after, the
 * major key for the major, 7th, major 7th and sus chords, the minor key for the minor ones. So B♭,
 * E♭ and A♭ (not A♯, D♯, G♯), but F♯ and F♯m, C♯m and G♯m; F7 has an E♭, C7 a B♭, A7 a C♯
 * (docs/architecture/ui-tuner.md, *Chord name view*). Keys with six sharps or flats take the one
 * guitar charts use most: F♯ major and E♭ minor.
 *
 * A tone the key would write with a sign this has no name for (E♯ in F♯maj7, C♭ in D♭7) takes the
 * name of the same note (F, B). Pure, and precomputed per chord.
 */
object ChordSpelling {

    /** The sign [chord]'s root and tones are written with. */
    fun accidentalOf(chord: Chord): Accidental = if (minorFamily(chord.quality)) {
        KeySpelling.ofMinorKey(chord.root)
    } else {
        KeySpelling.ofMajorKey(chord.root)
    }

    /**
     * [chord]'s tones from its root up, the fifth included where a voicing may leave it out: root,
     * third (or the sus chord's second or fourth), fifth and seventh. A sus2 lists its second before
     * the fifth (D E A), a sus4 its fourth (D G A).
     */
    fun tonesOf(chord: Chord): List<PitchClass> = tones[chord.root.ordinal * QUALITIES + chord.quality.ordinal]

    /** The symbol's ending for [quality]: "" (major), "m", "7", "maj7", "m7", "sus2", "sus4". */
    fun suffixOf(quality: ChordQuality): String = when (quality) {
        ChordQuality.MAJOR -> ""
        ChordQuality.MINOR -> "m"
        ChordQuality.DOMINANT_SEVENTH -> "7"
        ChordQuality.MAJOR_SEVENTH -> "maj7"
        ChordQuality.MINOR_SEVENTH -> "m7"
        ChordQuality.SUS2 -> "sus2"
        ChordQuality.SUS4 -> "sus4"
    }

    /** [pitchClass] as one of [chord]'s notes (its root, a tone, its bass) in [notation]: "B♭", "Si♭", "B" (German). */
    fun nameOf(pitchClass: PitchClass, chord: Chord, notation: Notation): String =
        notation.nameOf(pitchClass, accidentalOf(chord))

    /** [chord]'s root in [notation]: "A", "B♭", "La", "H". */
    fun rootOf(chord: Chord, notation: Notation): String = nameOf(chord.root, chord, notation)

    /**
     * The symbol: root and ending ("Am", "Lam", "Hm"), then "/" and the bass when [bass] is given and
     * isn't the root ("C/E", "Do/Mi").
     */
    fun symbolOf(chord: Chord, bass: PitchClass?, notation: Notation): String {
        val symbol = rootOf(chord, notation) + suffixOf(chord.quality)
        return if (bass == null || bass == chord.root) symbol else symbol + "/" + nameOf(bass, chord, notation)
    }

    private fun minorFamily(quality: ChordQuality): Boolean =
        quality == ChordQuality.MINOR || quality == ChordQuality.MINOR_SEVENTH

    private const val QUALITIES = 7

    private val tones: Array<List<PitchClass>> = Array(PitchClass.entries.size * QUALITIES) { index ->
        val root = index / QUALITIES
        val quality = ChordQuality.entries[index % QUALITIES]
        (0 until 12).filter { quality.mask and (1 shl it) != 0 }.map { PitchClass.entries[(root + it) % 12] }
    }
}
