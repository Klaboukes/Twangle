package io.github.klaboukes.twangle.feature.practice

import io.github.klaboukes.twangle.core.dsp.capo.Capo
import io.github.klaboukes.twangle.core.dsp.capo.CapoPlan
import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.Notation

/**
 * The shapes the Capo helper's "Shapes I play" offers, by their key, from C up: the open major keys
 * (C, D, E, G, A; [Capo.COMMON_MAJOR_SHAPES]), then the open minor ones (Dm, Em, Am;
 * [Capo.COMMON_MINOR_SHAPES]).
 */
internal val CAPO_SHAPES: List<SongKey> =
    Capo.COMMON_MAJOR_SHAPES.sortedBy { it.ordinal }.map { SongKey(it) } +
        Capo.COMMON_MINOR_SHAPES.sortedBy { it.ordinal }.map { SongKey(it, minor = true) }

/** The shapes to open on: the [saved] ones while offered ([CAPO_SHAPES]), else C. */
internal fun capoShapesToOpen(saved: SongKey): SongKey = if (saved in CAPO_SHAPES) saved else CAPO_SHAPES.first()

/** The capo's fret to open on: the [saved] one while it is a fret the helper offers, else no capo. */
internal fun capoFretToOpen(saved: Int): Int = if (saved in 0..Capo.HIGHEST_FRET) saved else 0

/**
 * [shapes] played with the capo at [fret]: the song then sounds in their key [fret] semitones
 * higher (C shapes at 3: E♭), and the plan's shape key is [shapes] again.
 */
internal fun capoPlanOf(shapes: SongKey, fret: Int): CapoPlan = CapoPlan(shapes.transposed(fret), fret)

/**
 * The rows of the table: the key's six usual chords, its diatonic triads without the diminished one
 * (rarely played in open position). A major key's I ii iii IV V vi (C: C Dm Em F G Am); a minor
 * key's i III iv v VI VII from its natural minor scale (Am: Am C Dm Em F G).
 */
internal fun capoChordsOf(shapes: SongKey): List<Chord> {
    val notes = shapes.scale.notes
    val degrees = if (shapes.minor) MINOR_DEGREES else MAJOR_DEGREES
    return degrees.map { (degree, quality) -> Chord.of(notes[degree], quality) }
}

/**
 * [chord]'s root as [key] writes it in [notation] ("E♭" in E♭, "A♯" in F♯ major), for TalkBack to
 * say it with the same spelling the table shows.
 */
internal fun rootInKey(key: SongKey, chord: Chord, notation: Notation): String =
    key.symbolOf(Chord.of(chord.root, ChordQuality.MAJOR), null, notation)

/** Scale degree (from 0) and quality of each row, major keys and minor keys. */
private val MAJOR_DEGREES = listOf(
    0 to ChordQuality.MAJOR,
    1 to ChordQuality.MINOR,
    2 to ChordQuality.MINOR,
    3 to ChordQuality.MAJOR,
    4 to ChordQuality.MAJOR,
    5 to ChordQuality.MINOR,
)
private val MINOR_DEGREES = listOf(
    0 to ChordQuality.MINOR,
    2 to ChordQuality.MAJOR,
    3 to ChordQuality.MINOR,
    4 to ChordQuality.MINOR,
    5 to ChordQuality.MAJOR,
    6 to ChordQuality.MAJOR,
)
