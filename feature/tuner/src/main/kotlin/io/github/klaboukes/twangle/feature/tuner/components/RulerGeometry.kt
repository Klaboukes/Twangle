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
import kotlin.math.tanh

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
 * apart: the scale slides left as the note goes sharp, so the pointer moves right along it. Near the pointer
 * the scale is zoomed in ([rulerWarp]), so the last cents to the note move it far enough to read.
 */
fun rulerNoteX(midi: Int, position: Float, pointerX: Float, step: Float): Float = rulerPlaceX(midi.toFloat(), position, pointerX, step)

/** The x of any place [at] on the scale (in semitones, as [position]), as [rulerNoteX] places the notes' marks. */
fun rulerPlaceX(at: Float, position: Float, pointerX: Float, step: Float): Float = pointerX + rulerWarp(at - position) * step

/**
 * The ruler zoomed in round the pointer (the owner's pick B of docs/design/v2-ruler-needle-preview.html,
 * 2026-11-03): [d] semitones from the pointer are drawn `d + ZOOM · REACH · tanh(d / REACH)` steps away, so the
 * scale is [RULER_ZOOM] + 1 times as wide at the pointer (10 cents ≈ 0.29 steps, not 0.1), eases back to its plain
 * width a semitone out ([RULER_ZOOM_REACH]), and keeps its order (it only ever grows with [d]). At rest [d] is 0.
 */
fun rulerWarp(d: Float): Float = d + RULER_ZOOM * RULER_ZOOM_REACH * tanh(d / RULER_ZOOM_REACH)

/**
 * How much the cent marks round the note show (0..1) for a reading [cents] off it: all within [RULER_MARKS_FULL],
 * none from [RULER_MARKS_NONE] on, so they come in as the note gets close and the ruler reads like a needle. NaN (no
 * note) shows none.
 */
fun rulerMarksShown(cents: Float): Float {
    if (cents.isNaN()) return 0f
    return (1f - (abs(cents) - RULER_MARKS_FULL) / (RULER_MARKS_NONE - RULER_MARKS_FULL)).coerceIn(0f, 1f)
}

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

/**
 * How much of a name at [x] shows on a ruler [width] wide: none within [clear] of either end (focus mode keeps its
 * exit button's corner clear, Phase 73), then fading in over [fade].
 */
fun rulerEdgeFade(x: Float, width: Float, fade: Float, clear: Float = 0f): Float {
    val fromEnd = minOf(x, width - x) - clear
    return if (fade <= 0f) (if (fromEnd >= 0f) 1f else 0f) else (fromEnd / fade).coerceIn(0f, 1f)
}

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

/** How much wider than plain the scale is drawn at the pointer, less one ([rulerWarp]). */
const val RULER_ZOOM = 2f

/** Over how many semitones round the pointer the zoom eases back ([rulerWarp]). */
const val RULER_ZOOM_REACH = 0.22f

/** Within this many cents of the note its cent marks show whole, and from this many on not at all ([rulerMarksShown]). */
const val RULER_MARKS_FULL = 5f
const val RULER_MARKS_NONE = 30f

/** Within this many semitones of the pointer a name is drawn full size. */
const val EMPHASIS_FULL = 0.25f

/** From this many semitones off the pointer a name is drawn small. */
const val EMPHASIS_NONE = 0.9f
