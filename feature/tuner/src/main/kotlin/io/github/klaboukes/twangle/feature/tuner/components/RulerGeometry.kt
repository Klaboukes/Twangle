package io.github.klaboukes.twangle.feature.tuner.components

import io.github.klaboukes.twangle.core.dsp.music.Accidental
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.settings.TunerMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

// Where the ruler's chromatic scale sits (2.0, Phase 71; docs/architecture/v2-plan.md, *The ruler*): pure
// numbers, so they are tested on the JVM (RulerGeometryTest). Nothing here allocates but [rulerNames].

/**
 * The reading as a place on the chromatic scale, in semitones (MIDI numbers): the target note plus its
 * cents, so 40.25 is E2 25 cents sharp, or F2 75 cents flat. Every note's mark sits at its own MIDI number.
 */
fun rulerPosition(targetMidi: Int, cents: Float): Float = targetMidi + cents / CENTS_PER_SEMITONE

/** The note nearest [position]: the heard note, drawn big. Half way between two it is the higher one. */
fun rulerHeardMidi(position: Float): Int = floor(position + 0.5f).toInt()

/**
 * The x of note [midi]'s mark when the scale sits at [position] under the pointer at [pointerX], [step]
 * apart: the scale slides left as the note goes sharp, so the pointer moves right along it.
 */
fun rulerNoteX(midi: Int, position: Float, pointerX: Float, step: Float): Float = pointerX + (midi - position) * step

/** The lowest note whose mark (and its name, up to a [step] wide) can be seen on a ruler [width] wide. */
fun rulerFirstMidi(position: Float, width: Float, step: Float): Int = floor(position - (width / 2f + step) / step).toInt()

/** The highest note that can be seen, as [rulerFirstMidi]. */
fun rulerLastMidi(position: Float, width: Float, step: Float): Int = ceil(position + (width / 2f + step) / step).toInt()

/**
 * How big note [midi]'s name is drawn, 0 (a small neighbour) to 1 (the heard note), from its distance to
 * the pointer: full within [EMPHASIS_FULL] of a semitone, small from [EMPHASIS_NONE] on, eased between
 * (smoothstep). So the name grows and shrinks as the scale slides instead of jumping, and half way
 * between two notes both are drawn the same, a little smaller than the heard one.
 */
fun rulerEmphasis(midi: Int, position: Float): Float {
    val t = ((abs(midi - position) - EMPHASIS_FULL) / (EMPHASIS_NONE - EMPHASIS_FULL)).coerceIn(0f, 1f)
    return 1f - t * t * (3f - 2f * t)
}

/** How much of a name at [x] shows on a ruler [width] wide: it fades out over the last [fade] at each end. */
fun rulerEdgeFade(x: Float, width: Float, fade: Float): Float =
    if (fade <= 0f) 1f else (minOf(x, width - x) / fade).coerceIn(0f, 1f)

/**
 * The 12 names the ruler writes, by [PitchClass.ordinal], in the notation setting's names: a string of
 * [tuning] keeps the tuning's spelling, as the strings do (E♭ in Half step down); in Any note, or with no
 * string ([guitar] false), the notation's own.
 */
fun rulerNames(notation: Notation, tuning: Tuning, guitar: Boolean): List<String> {
    val spelling: Accidental = if (guitar) tuning.spelling ?: notation.spelling else notation.spelling
    return PitchClass.entries.map { notation.nameOf(it, spelling) }
}

/**
 * Where the ruler rests while nothing is heard: on the [locked] string, else on the note [lastHeard]
 * (null before any), else on the tuning's first string in guitar mode, or A4 in Any note.
 */
fun rulerRestMidi(mode: TunerMode, tuning: Tuning, locked: Int?, lastHeard: Int?): Int =
    locked?.let { tuning.strings.getOrNull(it)?.midi }
        ?: lastHeard
        ?: if (mode == TunerMode.AUTO) tuning.strings.firstOrNull()?.midi ?: A4.midi else A4.midi

private val A4 = Note(69)

private const val CENTS_PER_SEMITONE = 100f

/** Within this many semitones of the pointer a name is drawn full size. */
const val EMPHASIS_FULL = 0.25f

/** From this many semitones off the pointer a name is drawn small. */
const val EMPHASIS_NONE = 0.9f
