package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.smoothing.InTuneDetector
import kotlin.math.abs

/**
 * The word under the note (2.0, Phase 75): what to do now, in the phone's words ("Tighten", "Loosen", "In tune";
 * its `TuneWord` with strings, as the watch is worn by a player with a guitar). A copy of the phone's rule
 * (`tunerHintFor` and `tuneWordFor` in `:feature:tuner`, which the watch can't use: docs/architecture/v2-plan.md,
 * `:wear`), small and pure: in tune while the watch says so; otherwise flat or sharp by the whole cents shown, but
 * within [margin] cents of the note the word stays what it was for this note (none if there was none), so a reading
 * wobbling across the note doesn't swap Tighten and Loosen.
 */
enum class WatchWord {
    TIGHTEN,
    LOOSEN,
    IN_TUNE,
    ;

    companion object {
        /** The word for [reading], given the [previous] word shown for the same note. */
        fun of(reading: WatchTunerState.Detected, previous: WatchWord?, margin: Float = InTuneDetector.DEFAULT_ENTER_CENTS): WatchWord? {
            if (reading.inTune) return IN_TUNE
            val kept = previous?.takeIf { it != IN_TUNE }
            if (abs(reading.cents) <= margin) return kept
            return if (reading.cents < 0) TIGHTEN else LOOSEN
        }
    }
}

/**
 * The word last shown and its note, so the next reading of that note keeps its direction near the note
 * ([WatchWord.of]) and another note starts afresh. Not state: nothing redraws for it. Not thread-safe.
 */
class HeldWatchWord {
    private var note: Note? = null
    private var word: WatchWord? = null

    /** The word for [reading] (null: none, a note just heard right on the mark), remembered for the next one. */
    fun next(reading: WatchTunerState.Detected?): WatchWord? {
        if (reading == null) {
            note = null
            word = null
            return null
        }
        val previous = if (reading.note == note) word else null
        return WatchWord.of(reading, previous).also {
            note = reading.note
            word = it
        }
    }
}
