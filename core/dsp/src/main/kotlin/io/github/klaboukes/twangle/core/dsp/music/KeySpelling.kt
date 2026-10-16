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
     * Major keys written with flats (pitch-class numbers, C = 0): F, B♭, E♭, A♭, D♭, and C, which has
     * no sign of its own; its 7th is B♭ (C7), so it goes with the flats.
     */
    private val MAJOR_FLAT_KEYS = setOf(0, 1, 3, 5, 8, 10)

    /** Minor keys written with flats: D, G, C, F, B♭, E♭, and A (no sign; Am's tones need none). */
    private val MINOR_FLAT_KEYS = setOf(0, 2, 3, 5, 7, 9, 10)
}
