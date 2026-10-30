package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Tuning

/**
 * One finger shape of [chord] on a tuning, as a chord chart draws it. Per string, in the tuning's
 * instrument order (thickest string first, see [Tuning]): [frets] holds [MUTED] for a string not
 * played, 0 for one played open, or the fret pressed, counted on the neck (the banjo's short string
 * is pressed only above its start, at the 6th fret or higher, [Tuning.startFretOf]); [fingers] the
 * suggested finger for a pressed string (1 index … 4 little finger), 0 for an open or muted one. A
 * [barre] is one finger lying across several strings at one fret; the strings it holds down show its
 * finger in [fingers] too (a banjo's barre at the 7th fret holds its short string there too).
 *
 * Made by [ChordShapeFinder]; [toString] writes it the way chord charts do, e.g. "x32010".
 */
data class ChordShape(
    val chord: Chord,
    val frets: List<Int>,
    val fingers: List<Int>,
    val barre: Barre? = null,
) {

    init {
        require(frets.size == fingers.size) { "A fret and a finger per string" }
    }

    val stringCount: Int get() = frets.size

    fun isMuted(string: Int): Boolean = frets[string] == MUTED

    fun isOpen(string: Int): Boolean = frets[string] == 0

    /** The lowest fret pressed, or 0 when every string played is open. */
    val lowestFret: Int = frets.filter { it > 0 }.minOrNull() ?: 0

    /** The highest fret pressed, or 0 when every string played is open. */
    val highestFret: Int = frets.maxOrNull()?.coerceAtLeast(0) ?: 0

    /**
     * The fret a chart of [rows] fret rows starts at: 1 (drawn with the nut) when the shape fits the
     * first [rows] frets, otherwise its [lowestFret] ("3fr" beside the first row).
     */
    fun startFret(rows: Int = CHART_ROWS): Int = if (highestFret <= rows) 1 else lowestFret

    /**
     * The note each string sounds in [tuning], null for a muted string. A string that starts up the
     * neck (the banjo's short fifth, [Tuning.startFretOf]) has its frets counted on the neck, so its
     * 7th fret sounds two frets above its open note ([Tuning.midiAt]).
     */
    fun notesIn(tuning: Tuning): List<Note?> {
        require(tuning.stringCount == stringCount) { "Shape has $stringCount strings, '${tuning.id}' has ${tuning.stringCount}" }
        return frets.mapIndexed { string, fret -> if (fret == MUTED) null else Note(tuning.midiAt(string, fret)) }
    }

    /** The lowest note sounded in [tuning] (not always on the first string played, see [Tuning]). */
    fun bassIn(tuning: Tuning): Note = Note(notesIn(tuning).filterNotNull().minOf { it.midi })

    /** As chord charts write it, thickest string first: "x32010"; frets from 10 in brackets, "x(10)(12)…". */
    override fun toString(): String = frets.joinToString("") {
        when {
            it == MUTED -> "x"
            it < 10 -> it.toString()
            else -> "($it)"
        }
    }

    companion object {
        /** [frets] value of a string not played. */
        const val MUTED = -1

        /** Fret rows a chord chart shows: the comfortable span ([ChordShapeFinder.MAX_SPAN]) and one more. */
        const val CHART_ROWS = 4
    }
}

/**
 * One finger ([finger], the index finger in practice) pressing every string from [firstString] to
 * [lastString] (instrument order, inclusive) at [fret]. Strings between them may be pressed higher by
 * other fingers; none of them is open or muted.
 */
data class Barre(val fret: Int, val firstString: Int, val lastString: Int, val finger: Int = 1) {
    init {
        require(fret > 0 && firstString < lastString) { "A barre lies across at least two strings at a fret" }
    }
}
