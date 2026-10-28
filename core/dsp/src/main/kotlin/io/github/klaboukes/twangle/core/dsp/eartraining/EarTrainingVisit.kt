package io.github.klaboukes.twangle.core.dsp.eartraining

import kotlin.random.Random

/** This visit's quiet count: [right] answers of [asked] questions answered ("7 of 9"). Never saved. */
data class Tally(val right: Int = 0, val asked: Int = 0)

/**
 * One visit to an Ear training exercise: the questions keep coming until the player leaves, a quiet
 * [tally] counts this visit only, and a question missed is asked again [MISSED_RETURN_MIN] to
 * [MISSED_RETURN_MAX] questions later (the same notes, unmarked), then mixed in as usual. No question
 * is asked twice in a row, a returning one included, unless the choices leave only one. A tool, not a
 * game: no rounds, levels or scores kept; dropping the visit drops it all.
 *
 * Everything random comes from [random], so the same seed repeats a visit question for question.
 * Not thread-safe: one screen's ViewModel drives it.
 */
class EarTrainingVisit<Q : Any>(source: QuestionSource<Q>, private val random: Random) {

    /** Where new questions come from: the player's current choices ([change]). */
    var source: QuestionSource<Q> = source
        private set

    /** The question being asked, null before the first [next]. */
    var current: Q? = null
        private set

    /** Whether [current] has been answered ([answer]). */
    var answered: Boolean = false
        private set

    var tally: Tally = Tally()
        private set

    // Questions asked so far; a missed one is due at a question number.
    private var count = 0
    private val missed = mutableListOf<Missed<Q>>()

    /** The next question: a missed one that is due, else a new one from [source]. */
    fun next(): Q {
        count++
        val previous = current
        val due = missed.filter { it.dueAt <= count && it.question != previous }.minByOrNull { it.dueAt }
        val question = due?.question ?: source.draw(random, previous)
        // Asked now, so no longer waiting to come back (a miss now sends it back again).
        missed.removeAll { it.question == question }
        current = question
        answered = false
        return question
    }

    /**
     * Records the answer to [current], once: the tally, and a missed question sent back a few
     * questions later.
     */
    fun answer(right: Boolean) {
        val question = checkNotNull(current) { "No question asked yet" }
        check(!answered) { "This question was answered already" }
        answered = true
        tally = Tally(tally.right + if (right) 1 else 0, tally.asked + 1)
        if (!right) missed += Missed(question, count + random.nextInt(MISSED_RETURN_MIN, MISSED_RETURN_MAX + 1))
    }

    /**
     * The player changed what is asked: new questions come from [source], the tally stays, and the
     * missed questions the new choices no longer offer are dropped.
     */
    fun change(source: QuestionSource<Q>) {
        this.source = source
        missed.removeAll { !source.offers(it.question) }
    }

    private class Missed<Q>(val question: Q, val dueAt: Int)

    companion object {
        /** A missed question comes back as the 3rd to the 5th question after it. */
        const val MISSED_RETURN_MIN = 3
        const val MISSED_RETURN_MAX = 5
    }
}
