package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.music.Accidental
import io.github.klaboukes.twangle.core.dsp.music.KeySpelling
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass

/**
 * How a chord is written: its symbol ("Am", "B♭7", "F♯m", "C/E") in the player's [Notation]
 * (letters, German H and B, or solfège: "Lam"), and its tones. Sharps and flats follow the usual chord
 * spelling, whatever the notation's own sign: the sign of the key the chord is named after, the
 * major key for the major, 7th, major 7th, sus, 6th, add9 and 9th chords, the minor key for the
 * minor and minor 7th ones. So B♭, E♭ and A♭ (not A♯, D♯, G♯), but F♯ and F♯m, C♯m and G♯m; F7 has
 * an E♭, C7 a B♭, A7 a C♯ (docs/architecture/ui-tuner.md, *Chord name view*). Keys with six sharps
 * or flats take the one guitar charts use most: F♯ major and E♭ minor.
 *
 * The chords whose fifth or sixth is altered go by that tone: a diminished fifth is flat on a white
 * root (Edim E G B♭, Adim7 A C E♭ G♭), the minor key otherwise (C♯dim C♯ E G); an augmented fifth is
 * sharp on a white root (Caug C E G♯, Faug F A C♯), the major key otherwise (A♭aug A♭ C E); a minor
 * 6th chord's major sixth takes the sign of the major key a tone below (Am6 A C E F♯, as G major).
 *
 * A tone the chord's sign has no name for (E♯ in F♯maj7, C♭ in D♭7, B𝄫 in Cdim7), or that is
 * written with the other sign (B♭aug's F♯, C♯dim7's B♭), takes the name of the same note in the
 * chord's sign (F, B, A, G♭, A♯). Pure, and precomputed per chord.
 */
object ChordSpelling {

    /** The sign [chord]'s root and tones are written with. */
    fun accidentalOf(chord: Chord): Accidental {
        val root = chord.root
        val white = root.ordinal in WHITE_KEYS
        return when (chord.quality) {
            ChordQuality.MINOR, ChordQuality.MINOR_SEVENTH -> KeySpelling.ofMinorKey(root)
            ChordQuality.DIMINISHED, ChordQuality.DIMINISHED_SEVENTH, ChordQuality.HALF_DIMINISHED ->
                if (white) Accidental.FLAT else KeySpelling.ofMinorKey(root)
            ChordQuality.AUGMENTED -> if (white) Accidental.SHARP else KeySpelling.ofMajorKey(root)
            ChordQuality.MINOR_SIXTH -> KeySpelling.ofMajorKey(root.transposed(-2))
            else -> KeySpelling.ofMajorKey(root)
        }
    }

    /**
     * [chord]'s tones from its root up, the fifth included where a voicing may leave it out: root,
     * third (or the sus chord's second or fourth), fifth and seventh. A sus2 lists its second before
     * the fifth (D E A), a sus4 its fourth (D G A), an add9 and a 9th chord their ninth as a second
     * (C D E G B♭).
     */
    fun tonesOf(chord: Chord): List<PitchClass> = tones[chord.root.ordinal * QUALITIES + chord.quality.ordinal]

    /** The symbol's ending for [quality]: "" (major), "m", "7", "maj7", "m7", "sus2", "sus4", "7sus4", "6", "m6", "add9", "9", "dim", "dim7", "m7♭5", "aug". */
    fun suffixOf(quality: ChordQuality): String = when (quality) {
        ChordQuality.MAJOR -> ""
        ChordQuality.MINOR -> "m"
        ChordQuality.DOMINANT_SEVENTH -> "7"
        ChordQuality.MAJOR_SEVENTH -> "maj7"
        ChordQuality.MINOR_SEVENTH -> "m7"
        ChordQuality.SUS2 -> "sus2"
        ChordQuality.SUS4 -> "sus4"
        ChordQuality.SEVENTH_SUS4 -> "7sus4"
        ChordQuality.SIXTH -> "6"
        ChordQuality.MINOR_SIXTH -> "m6"
        ChordQuality.ADD9 -> "add9"
        ChordQuality.NINTH -> "9"
        ChordQuality.DIMINISHED -> "dim"
        ChordQuality.DIMINISHED_SEVENTH -> "dim7"
        ChordQuality.HALF_DIMINISHED -> "m7♭5"
        ChordQuality.AUGMENTED -> "aug"
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

    /** The white keys' pitch-class numbers, C = 0. */
    private val WHITE_KEYS = setOf(0, 2, 4, 5, 7, 9, 11)

    private val QUALITIES = ChordQuality.entries.size

    private val tones: Array<List<PitchClass>> = Array(PitchClass.entries.size * QUALITIES) { index ->
        val root = index / QUALITIES
        val quality = ChordQuality.entries[index % QUALITIES]
        (0 until 12).filter { quality.mask and (1 shl it) != 0 }.map { PitchClass.entries[(root + it) % 12] }
    }
}
