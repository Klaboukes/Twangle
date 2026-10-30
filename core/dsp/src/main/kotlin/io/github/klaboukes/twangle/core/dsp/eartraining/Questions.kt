package io.github.klaboukes.twangle.core.dsp.eartraining

import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordShape
import io.github.klaboukes.twangle.core.dsp.chord.ChordShapeFinder
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import kotlin.math.abs
import kotlin.random.Random

/**
 * Where an exercise's questions come from: the player's choices of what is asked, drawn with the
 * visit's [Random] so a seed repeats a visit. [EarTrainingVisit] asks it for each new question.
 */
interface QuestionSource<Q : Any> {

    /** A new question, never equal to [previous] (the one just asked) unless it is the only one there is. */
    fun draw(random: Random, previous: Q?): Q

    /** Whether [question] is one these choices could ask, so a missed one is still asked after the choices change. */
    fun offers(question: Q): Boolean
}

/**
 * Play it back: Twangle plays [notes] (one to four, in this order) and the player plays them back on
 * the instrument. [isRightAt] checks the note the tuner hears against the one asked.
 */
data class NoteQuestion(val notes: List<Note>) {

    init {
        require(notes.size in 1..NoteQuestions.MAX_LENGTH) { "One to ${NoteQuestions.MAX_LENGTH} notes, not ${notes.size}" }
    }

    /** Whether [heard] (the tuner's note) answers note [index] of this question, by [match]. */
    fun isRightAt(index: Int, heard: Note, match: NoteMatch): Boolean = match.accepts(notes[index], heard)
}

/**
 * The questions for Play it back: [length] notes in a row from the notes [choice] gives on [tuning].
 * Each note after the first differs from the one before and lies within an octave of it, so a
 * sequence sounds like a short tune rather than jumps across the neck (where the choice leaves a note
 * no neighbour within an octave, any other note will do). A new question's first note differs from
 * the previous question's, which is what keeps two questions in a row apart.
 */
class NoteQuestions(val tuning: Tuning, val choice: NoteChoice, val length: Int = 1) : QuestionSource<NoteQuestion> {

    init {
        require(length in 1..MAX_LENGTH) { "One to $MAX_LENGTH notes in a row, not $length" }
    }

    /** The notes asked for, lowest first ([NoteChoice.notesOn]). */
    val notes: List<Note> = choice.notesOn(tuning)

    private val midis = notes.map { it.midi }.toSet()

    override fun draw(random: Random, previous: NoteQuestion?): NoteQuestion {
        val first = pickOther(random, notes, previous?.notes?.first())
        val sequence = ArrayList<Note>(length)
        sequence += first
        while (sequence.size < length) {
            val last = sequence.last()
            val near = notes.filter { it != last && abs(it.midi - last.midi) <= Note.SEMITONES_PER_OCTAVE }
            sequence += if (near.isNotEmpty()) near.random(random) else pickOther(random, notes, last)
        }
        return NoteQuestion(sequence)
    }

    override fun offers(question: NoteQuestion): Boolean = question.notes.size == length && question.notes.all { it.midi in midis }

    companion object {
        /** The most notes in a row: four. */
        const val MAX_LENGTH = 4
    }
}

/**
 * Intervals: [low] and the note [interval] above it, played as [direction] says. [notes] are in the
 * order they sound (both at once for [IntervalDirection.TOGETHER]).
 */
data class IntervalQuestion(val interval: Interval, val direction: IntervalDirection, val low: Note) {

    val high: Note get() = Note(low.midi + interval.semitones)

    val notes: List<Note> get() = if (direction == IntervalDirection.DOWN) listOf(high, low) else listOf(low, high)

    fun isRight(answer: Interval): Boolean = answer == interval
}

/**
 * The questions for Intervals: one of the player's [intervals], played in one of their [directions],
 * with both notes on [tuning] up to the 12th fret ([NoteChoice.ALL_TO_12TH]), in the instrument's own
 * register. The interval and direction are drawn first, each pair equally often, then the lower note,
 * so small intervals (with more room on the neck) don't crowd out the octave.
 */
class IntervalQuestions(
    val tuning: Tuning,
    val intervals: Set<Interval>,
    val directions: Set<IntervalDirection> = setOf(IntervalDirection.UP),
) : QuestionSource<IntervalQuestion> {

    private val range = NoteChoice.ALL_TO_12TH.notesOn(tuning)
    private val rangeMidis = range.map { it.midi }.toSet()

    // Each interval and direction asked, with the lower notes that keep both notes on the neck.
    private val kinds: List<Kind> = Interval.entries.filter { it in intervals }.flatMap { interval ->
        IntervalDirection.entries.filter { it in directions }.map { direction ->
            Kind(interval, direction, range.filter { it.midi + interval.semitones in rangeMidis })
        }
    }.filter { it.lows.isNotEmpty() }

    init {
        require(intervals.isNotEmpty()) { "Choose at least one interval" }
        require(directions.isNotEmpty()) { "Choose at least one direction" }
        require(kinds.isNotEmpty()) { "No chosen interval fits on '${tuning.id}' up to the 12th fret" }
    }

    override fun draw(random: Random, previous: IntervalQuestion?): IntervalQuestion {
        val kind = kinds.random(random)
        val lows = if (previous != null && kind.matches(previous)) kind.lows - previous.low else kind.lows
        if (lows.isNotEmpty()) return IntervalQuestion(kind.interval, kind.direction, lows.random(random))
        // That kind's one question was just asked: another kind, or the same again if there is no other.
        val others = kinds - kind
        if (others.isEmpty()) return previous!!
        val other = others.random(random)
        return IntervalQuestion(other.interval, other.direction, other.lows.random(random))
    }

    override fun offers(question: IntervalQuestion): Boolean =
        question.interval in intervals && question.direction in directions && question.low.midi in rangeMidis && question.high.midi in rangeMidis

    private class Kind(val interval: Interval, val direction: IntervalDirection, val lows: List<Note>) {
        fun matches(question: IntervalQuestion) = question.interval == interval && question.direction == direction
    }
}

/**
 * Chord type: [chord] strummed as [shape] (the Chords tool's first shape for it on the tuning); the
 * player names its type.
 */
data class ChordTypeQuestion(val chord: Chord, val shape: ChordShape) {

    val quality: ChordQuality get() = chord.quality

    fun isRight(answer: ChordQuality): Boolean = answer == chord.quality
}

/**
 * The questions for Chord type: one of the player's [qualities] (any of the Chords tool's twenty,
 * [ChordQuality.inPanel]) on a root drawn afresh, strummed as [ChordShapeFinder]'s easiest shape of
 * it on [tuning]. The root changes every question, so the type, not the pitch, is what is heard. A
 * chord with no shape within reach on the tuning (a player's own tuning may lack some, the banjo's
 * open G two 9ths) is skipped, so only chords the Chords tool can chart are asked. On a banjo the
 * shape follows the short string's drone rule ([ChordShapeFinder]): open where its note is in the
 * chord, else fretted above its start, else not played.
 */
class ChordTypeQuestions(val tuning: Tuning, val qualities: Set<ChordQuality>) : QuestionSource<ChordTypeQuestion> {

    init {
        require(qualities.isNotEmpty()) { "Choose at least one chord type" }
    }

    private val chosen = ChordQuality.inPanel.filter { it in qualities }

    // Shapes found so far, null where a chord has none on the tuning; each takes well under a millisecond.
    private val shapes = HashMap<Chord, ChordShape?>()

    override fun draw(random: Random, previous: ChordTypeQuestion?): ChordTypeQuestion {
        val all = chosen.flatMap { quality -> PitchClass.entries.map { Chord.of(it, quality) } }
        val otherRoots = all.filter { it.root != previous?.chord?.root }
        return firstWithShape(random, otherRoots)
            ?: firstWithShape(random, all.filter { it != previous?.chord })
            ?: previous
            ?: error("None of the chosen chord types has a shape on '${tuning.id}'")
    }

    override fun offers(question: ChordTypeQuestion): Boolean = question.quality in qualities && question.shape == shapeOf(question.chord)

    private fun shapeOf(chord: Chord): ChordShape? =
        if (chord in shapes) shapes[chord] else ChordShapeFinder.shapesOf(chord, tuning, limit = 1).firstOrNull().also { shapes[chord] = it }

    private fun firstWithShape(random: Random, chords: List<Chord>): ChordTypeQuestion? {
        val left = chords.toMutableList()
        while (left.isNotEmpty()) {
            val chord = left.removeAt(random.nextInt(left.size))
            val shape = shapeOf(chord)
            if (shape != null) return ChordTypeQuestion(chord, shape)
        }
        return null
    }

    companion object {
        /** What a fresh install asks (the mock-up's): major, minor and 7, in the Chords panel's order. */
        val DEFAULT: Set<ChordQuality> = linkedSetOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DOMINANT_SEVENTH)
    }
}

/** One of [items], not [avoid] when there is another to pick. */
private fun <T> pickOther(random: Random, items: List<T>, avoid: T?): T {
    val others = if (avoid == null) items else items.filter { it != avoid }
    return (others.ifEmpty { items }).random(random)
}
