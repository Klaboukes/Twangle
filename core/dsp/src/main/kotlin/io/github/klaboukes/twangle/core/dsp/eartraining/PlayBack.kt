package io.github.klaboukes.twangle.core.dsp.eartraining

import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.smoothing.InTuneDetector

/** How long the note heard has been held, as [HeldNote] counts it. */
enum class Hold {
    /** Heard, but not long enough to judge (or nothing heard). */
    SOUNDING,

    /** Held [HeldNote.steadySeconds]: long enough to count as the right note. */
    STEADY,

    /** Held [HeldNote.settledSeconds]: long enough to count as a wrong note, so a slide or a passing note never does. */
    SETTLED,
}

/**
 * How long the tuner's note (`PitchSmoother.note`, already steadied by its onset check and its note
 * hysteresis) has stayed the same, counted in analysis frames as [InTuneDetector] counts its hold, so
 * it is exact and needs no clock. Play it back calls a note right once it is [Hold.STEADY] (the in-tune
 * hold, 0.3 s) and wrong only once it is [Hold.SETTLED] (1 s), so finding the note on the neck, sliding
 * up to it or passing other notes on the way is never taken for an answer.
 *
 * Allocation-free and not thread-safe: one listening session feeds it frame by frame.
 */
class HeldNote(
    val steadySeconds: Float = InTuneDetector.DEFAULT_HOLD_SECONDS,
    val settledSeconds: Float = DEFAULT_SETTLED_SECONDS,
) {
    init {
        require(steadySeconds >= 0f) { "steadySeconds must not be negative, was $steadySeconds" }
        require(settledSeconds >= steadySeconds) { "settledSeconds must be at least steadySeconds, was $settledSeconds" }
    }

    /** The note held, as a MIDI number, or [NO_NOTE]. */
    var midi: Int = NO_NOTE
        private set

    private var heldSeconds = 0f

    /** Feeds one frame's note ([NO_NOTE] for none) lasting [frameSeconds], and returns how long it has been held. */
    fun update(midi: Int, frameSeconds: Float): Hold {
        if (midi != this.midi) {
            this.midi = midi
            heldSeconds = 0f
        }
        if (midi == NO_NOTE) return Hold.SOUNDING
        heldSeconds += frameSeconds
        return when {
            // A small epsilon so a hold made of float frame durations still counts in full.
            heldSeconds >= settledSeconds - EPSILON_SECONDS -> Hold.SETTLED
            heldSeconds >= steadySeconds - EPSILON_SECONDS -> Hold.STEADY
            else -> Hold.SOUNDING
        }
    }

    /** Starts over: nothing held. */
    fun reset() {
        midi = NO_NOTE
        heldSeconds = 0f
    }

    companion object {
        /** No note heard. */
        const val NO_NOTE = -1

        /** A wrong note counts once it is held this long. */
        const val DEFAULT_SETTLED_SECONDS = 1f

        private const val EPSILON_SECONDS = 1e-4f
    }
}

/** What one heard note did to a [PlayBackAnswer]. */
enum class Verdict {
    /** Nothing to say yet: not held long enough, the note just found still ringing, or nothing heard. */
    NONE,

    /** The next note of a sequence was found; more are to come. */
    NOTE_FOUND,

    /** The last note was found: the question is answered. */
    ALL_FOUND,

    /** A wrong note was held: the question counts as missed, and the player plays on until it is found. */
    WRONG,
}

/**
 * The player's answer to one Play it back [question] as it goes: its notes are found in order, each by
 * holding it ([Hold.STEADY] for the right note), and the question is answered once all are found. A
 * wrong note held ([Hold.SETTLED]) never ends the question: the player plays on (and may hear it again)
 * until it is found, but it counts as [missed], so the tally counts the questions found at once and a
 * missed one comes back a few questions later ([EarTrainingVisit]).
 *
 * A note just found is not judged again while it rings on, so the next note of a sequence is never
 * taken from it (in [NoteMatch.ANY_OCTAVE], a next note an octave away would otherwise count at once).
 * Fed only when the note heard or its [Hold] changes, a few times a second at most; not thread-safe.
 */
class PlayBackAnswer(val question: NoteQuestion) {

    /** How many of the question's notes were found, in order. */
    var found: Int = 0
        private set

    /** Whether a wrong note was held before every note was found. */
    var missed: Boolean = false
        private set

    /** Whether the last note judged was wrong: "Not that one", until a right one is found or the question sounds again. */
    var wrongHeld: Boolean = false
        private set

    /** Whether every note was found. */
    val complete: Boolean get() = found == question.notes.size

    // The note just found, as heard, while it rings on: not judged again.
    private var ringing = HeldNote.NO_NOTE

    /** Judges the note heard ([HeldNote.NO_NOTE] for none), held as long as [hold] says, by [match]. */
    fun hear(midi: Int, hold: Hold, match: NoteMatch): Verdict {
        if (complete) return Verdict.NONE
        if (midi != ringing) ringing = HeldNote.NO_NOTE
        if (midi == HeldNote.NO_NOTE || midi == ringing) return Verdict.NONE
        val right = question.isRightAt(found, Note(midi), match)
        return when {
            right && hold != Hold.SOUNDING -> {
                found++
                wrongHeld = false
                ringing = midi
                if (complete) Verdict.ALL_FOUND else Verdict.NOTE_FOUND
            }
            !right && hold == Hold.SETTLED -> {
                missed = true
                wrongHeld = true
                Verdict.WRONG
            }
            else -> Verdict.NONE
        }
    }

    /** The question sounds again: "Not that one" goes, the notes found stay found. */
    fun replayed() {
        wrongHeld = false
    }
}
