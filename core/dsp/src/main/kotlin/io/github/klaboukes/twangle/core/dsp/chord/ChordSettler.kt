package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.music.PitchClass

/**
 * A chord as the chord name view shows it: the [chord], and its [bass] (lowest note) when that is sure,
 * else null. A bass that isn't the root makes a slash chord, "C/E" ([isSlash]).
 */
data class SettledChord(val chord: Chord, val bass: PitchClass? = null) {
    /** The bass is sure and isn't the root: written "C/E". */
    val isSlash: Boolean get() = bass != null && bass != chord.root
}

/**
 * Settles [ChordDetector]'s readings into what the chord name view shows, so it never flickers and
 * never guesses (docs/architecture/ui-tuner.md, *Chord name view*). Each reading (one every 0.1 s
 * while a strum rings) is named on its own; here:
 * - a name shows once it wins [WINS] of the last [WINDOW] readings of the strum (about a second after
 *   it), and the bass joins it (a slash chord, "C/E") only when it agrees in [WINS] of them too;
 * - it stays at least [HOLD_SECONDS], and only another chord winning the same way replaces it;
 * - readings without a chord don't erase it: it [faded] as the strum dies away, and while a new strum
 *   hasn't named anything yet;
 * - a strum that names nothing for [UNCLEAR_SECONDS] shows "not a clear chord" ([unclear]), for at
 *   least [UNCLEAR_HOLD_SECONDS]. A strum too short to give a reading (a knock) changes nothing.
 * [recent] holds the last [RECENT] chords shown, the latest last.
 *
 * Fed by [update] with the strum's status and, for a reading, its chord; also between readings (every
 * 0.1 s or so), so time passes while nothing rings. Allocates only when what it shows changes. Not
 * thread-safe.
 */
class ChordSettler {

    /** The chord shown, or null: nothing named yet, or [unclear]. */
    var shown: SettledChord? = null
        private set

    /** The chord shown is from an earlier strum (a new one is being listened to), or the strum has stopped ringing. */
    var faded: Boolean = false
        private set

    /** The last strum named nothing: "not a clear chord". */
    var unclear: Boolean = false
        private set

    /** A strum is heard and hasn't named a chord yet (or said it's not a clear one). */
    var listening: Boolean = false
        private set

    /** Any strum has been heard since the start. */
    var heardAny: Boolean = false
        private set

    /** The last chords shown, at most [RECENT], the latest last (also when it is no longer shown). */
    var recent: List<SettledChord> = emptyList()
        private set

    // The strum's readings: chord (ordinal in Chord's table, or −1) and bass (ordinal or −1).
    private val chords = IntArray(WINDOW)
    private val basses = IntArray(WINDOW)
    private var count = 0
    private var next = 0

    private var strum = 0
    private var strumAt = 0.0
    private var strumReadings = 0
    private var strumNamed = false
    private var shownStrum = 0
    private var shownAt = 0.0

    /** Back to the start: nothing heard, and no time remembered (the clock may start over with it). */
    fun reset() {
        shown = null
        faded = false
        unclear = false
        listening = false
        heardAny = false
        recent = emptyList()
        count = 0
        next = 0
        strum = 0
        strumAt = 0.0
        strumReadings = 0
        strumNamed = false
        shownStrum = 0
        shownAt = 0.0
        unclearStrum = 0
    }

    /**
     * At [seconds] (any steady clock, e.g. the audio's own time) the listener has heard [strumCount]
     * strums and the latest one [ringing]; with [reading], one reading of it named [chord] (null: no
     * chord) with [bass] its lowest note. Returns whether anything shown changed.
     */
    fun update(seconds: Double, strumCount: Int, ringing: Boolean, reading: Boolean = false, chord: Chord? = null, bass: PitchClass? = null): Boolean {
        val shownBefore = shown
        val fadedBefore = faded
        val unclearBefore = unclear
        val listeningBefore = listening
        val heardBefore = heardAny
        if (strumCount < strum) reset()
        if (strumCount > strum) {
            strum = strumCount
            strumAt = seconds
            strumReadings = 0
            strumNamed = false
            count = 0
            next = 0
            heardAny = true
        }
        if (reading && strum > 0) {
            chords[next] = if (chord == null) NONE else indexOf(chord)
            basses[next] = if (bass == null) NONE else bass.ordinal
            next = (next + 1) % WINDOW
            if (count < WINDOW) count++
            strumReadings++
        }
        val held = seconds - shownAt >= if (unclear) UNCLEAR_HOLD_SECONDS else HOLD_SECONDS
        val winner = winner()
        if (winner != NONE) {
            strumNamed = true
            val named = Chord.of(PitchClass.entries[winner / QUALITIES], ChordQuality.entries[winner % QUALITIES])
            val agreed = bassOf(winner)
            val current = shown
            if (current != null && current.chord == named) {
                // The same chord again: it brightens; a bass that is now sure (and another) joins it in time.
                shownStrum = strum
                if (agreed != null && agreed != current.bass && held) show(SettledChord(named, agreed), seconds)
            } else if ((current == null && !unclear) || held) {
                show(SettledChord(named, agreed), seconds)
            }
        } else if (!strumNamed && strumReadings > 0 && seconds - strumAt >= UNCLEAR_SECONDS && !unclearFor(strum) && held) {
            shown = null
            unclear = true
            unclearStrum = strum
            shownAt = seconds
        }
        listening = strum > 0 && shownStrum != strum && !unclearFor(strum) && (ringing || strumReadings > 0)
        faded = shown != null && (!ringing || shownStrum != strum)
        return shownBefore != shown || fadedBefore != faded || unclearBefore != unclear || listeningBefore != listening || heardBefore != heardAny
    }

    private var unclearStrum = 0

    private fun unclearFor(strumCount: Int) = unclear && unclearStrum == strumCount

    private fun show(chord: SettledChord, seconds: Double) {
        shown = chord
        unclear = false
        shownStrum = strum
        shownAt = seconds
        if (recent.lastOrNull() != chord) recent = (recent + chord).takeLast(RECENT)
    }

    /** The chord (index) winning [WINS] of the window, or [NONE]. */
    private fun winner(): Int {
        for (i in 0 until count) {
            val c = chords[i]
            if (c == NONE) continue
            var votes = 0
            for (j in 0 until count) if (chords[j] == c) votes++
            if (votes >= WINS) return c
        }
        return NONE
    }

    /** The bass that agrees in [WINS] of the window's readings of chord [index], or null. */
    private fun bassOf(index: Int): PitchClass? {
        for (i in 0 until count) {
            if (chords[i] != index || basses[i] == NONE) continue
            var votes = 0
            for (j in 0 until count) if (chords[j] == index && basses[j] == basses[i]) votes++
            if (votes >= WINS) return PitchClass.entries[basses[i]]
        }
        return null
    }

    private fun indexOf(chord: Chord) = chord.root.ordinal * QUALITIES + chord.quality.ordinal

    companion object {
        /** Readings looked at: the strum's last six (0.6 s). */
        const val WINDOW = 6

        /** A chord shows once it is named in this many of them. */
        const val WINS = 4

        /** A chord shown stays at least this long. */
        const val HOLD_SECONDS = 1.5

        /** A strum that names nothing this long after it is "not a clear chord"… */
        const val UNCLEAR_SECONDS = 1.5

        /** …which stays at least this long. */
        const val UNCLEAR_HOLD_SECONDS = 2.0

        /** Chords kept in [recent]. */
        const val RECENT = 3

        private const val NONE = -1
        private val QUALITIES = ChordQuality.entries.size
    }
}
