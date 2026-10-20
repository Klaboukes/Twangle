package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.music.PitchClass

/**
 * Names a set of pitch classes as one of the [ChordQuality.detected] chords, or not at all. Pure and
 * allocation-free.
 *
 * The set must be exactly a chord's: every tone there and nothing else (a [ChordQuality.fifthOptional]
 * chord may leave out its fifth). Two notes are never a chord (C and E could be C major or A minor
 * without its fifth), and an extra note makes it some other chord this doesn't know (C E G A is C6
 * or Am7, … ). Two chords can share a set: Dsus2 (D E A) is Asus4 (A D E), Dsus4 (D G A) is Gsus2.
 * Then the bass decides: the one whose root is the lowest note, else none.
 */
object ChordNamer {

    private val qualities = ChordQuality.entries.filter { it.detected }.toTypedArray()

    /**
     * The chord whose tones are exactly [pitchClasses] (a 12-bit mask, bit i = pitch class i from
     * C), or null. [bass] is the lowest note's pitch class (0..11, C = 0), or −1 when unknown.
     */
    fun name(pitchClasses: Int, bass: Int = -1): Chord? {
        var match: Chord? = null
        var matches = 0
        var bassMatch: Chord? = null
        var bassMatches = 0
        for (root in 0 until PITCH_CLASSES) {
            if (pitchClasses and (1 shl root) == 0) continue
            for (quality in qualities) {
                val fits = pitchClasses == rotate(quality.mask, root) ||
                    (quality.fifthOptional && pitchClasses == rotate(quality.maskWithoutFifth, root))
                if (!fits) continue
                val chord = Chord.of(PitchClass.entries[root], quality)
                match = chord
                matches++
                if (root == bass) {
                    bassMatch = chord
                    bassMatches++
                }
            }
        }
        return when {
            matches == 1 -> match
            bassMatches == 1 -> bassMatch
            else -> null
        }
    }

    /** The 12-bit mask of [chord]'s tones. */
    fun maskOf(chord: Chord): Int = rotate(chord.quality.mask, chord.root.ordinal)

    // A mask relative to C moved up to root.
    private fun rotate(mask: Int, root: Int): Int = ((mask shl root) or (mask ushr (PITCH_CLASSES - root))) and ALL

    private const val PITCH_CLASSES = 12
    private const val ALL = (1 shl PITCH_CLASSES) - 1
}
