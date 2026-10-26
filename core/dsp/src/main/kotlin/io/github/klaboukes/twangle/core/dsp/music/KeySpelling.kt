package io.github.klaboukes.twangle.core.dsp.music

/**
 * Which sign a key writes its black keys with: B♭, E♭ and A♭ in F major, F♯ in G major. Shared by
 * chord symbols ([io.github.klaboukes.twangle.core.dsp.chord.ChordSpelling]) and scales
 * ([io.github.klaboukes.twangle.core.dsp.scale.Scale]), whatever the notation's own sign. Keys with
 * six sharps or flats take the one guitar charts use most: F♯ major and E♭ minor.
 */
object KeySpelling {

    /** The sign of the major key on [root]: flats for F, B♭, E♭, A♭, D♭, and C (whose 7th chord has a B♭). */
    fun ofMajorKey(root: PitchClass): Accidental =
        if (root.ordinal in MAJOR_FLAT_KEYS) Accidental.FLAT else Accidental.SHARP

    /** The sign of the minor key on [root]: flats for D, G, C, F, B♭, E♭, and A (no sign; its blue note is E♭). */
    fun ofMinorKey(root: PitchClass): Accidental =
        if (root.ordinal in MINOR_FLAT_KEYS) Accidental.FLAT else Accidental.SHARP

    /**
     * The sign of a mode on [root] whose parent major key is [parent] (D Dorian's is C, B♭
     * Mixolydian's E♭): the parent's ([ofMajorKey]), so F♯ Dorian has E major's sharps. The one
     * parent with six signs (F♯ = G♭ major) instead writes each mode as its root's own key does,
     * major or minor by the mode's third ([minorThird]): G♯ Dorian, E♭ Aeolian (as E♭ minor),
     * F Locrian (F G♭ …, not F F♯ …), D♭ Mixolydian. So the major and the natural minor scale, the
     * Ionian and Aeolian modes, are written exactly as [ofMajorKey] and [ofMinorKey] write their keys.
     */
    fun ofMode(root: PitchClass, parent: PitchClass, minorThird: Boolean): Accidental = when {
        parent.ordinal != SIX_SIGN_MAJOR_KEY -> ofMajorKey(parent)
        minorThird -> ofMinorKey(root)
        else -> ofMajorKey(root)
    }

    /**
     * Major keys written with flats (pitch-class numbers, C = 0): F, B♭, E♭, A♭, D♭, and C, which has
     * no sign of its own; its 7th is B♭ (C7), so it goes with the flats.
     */
    private val MAJOR_FLAT_KEYS = setOf(0, 1, 3, 5, 8, 10)

    /** Minor keys written with flats: D, G, C, F, B♭, E♭, and A (no sign; Am's tones need none). */
    private val MINOR_FLAT_KEYS = setOf(0, 2, 3, 5, 7, 9, 10)

    /** The major key with six signs either way, F♯ or G♭ (pitch-class number). */
    private const val SIX_SIGN_MAJOR_KEY = 6
}
