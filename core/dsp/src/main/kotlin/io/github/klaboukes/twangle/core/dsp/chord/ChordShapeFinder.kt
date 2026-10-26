package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.music.Tuning

/**
 * Finds playable finger shapes of a chord on any tuning, the presets' and the player's own alike, and
 * ranks them easiest first, so standard tuning's C comes out as the open x32010 before the barre at
 * the 3rd fret. Pure; meant for the chord charts, not the audio loop (a chord takes well under a
 * millisecond, see `ChordShapeFinderTest`).
 *
 * **Search.** Every string is muted, open (if its open note is a chord tone) or pressed at a chord
 * tone within [MAX_SPAN] frets of the lowest fret pressed, up to [HIGHEST_FRET]. A shape must sound
 * every tone of the chord and no other (a 6th, 7th or 9th chord may leave out its fifth, as in
 * [ChordQuality.fifthOptional]), play a run of strings ending on the last string (strings are only
 * left out on the thick side, as chord charts do, so it can be strummed), at least [minPlayed] of
 * them (all four on a ukulele or mandolin), and on five strings or more have the root as its lowest
 * note, except an open tuning's own chord: every string open or under one barre with the fifth in
 * the bass (open G's G 000000 and C 555555, which slide players strum whole). Fewer strings
 * (ukulele, mandolin) may sound an inversion, as their charts do (ukulele G is 0232), but a root in
 * the bass ranks higher. A chord with no such shape on five strings or more is looked for again with
 * the thinnest string muted as well, as jazz charts voice it (D♭maj9 x4354x, the ring finger's tip
 * damping the top string).
 *
 * **Power chords** ([ChordQuality.POWER_CHORD]) are played on two or three neighbouring strings with
 * the rest muted, the root lowest and on one of the thick strings (the lowest three of a guitar, the
 * first two of a ukulele, mandolin or bass), so they sound full: E5 022xxx, F5 133xxx, A5 x022xx, D5 xx023x.
 *
 * **Fingers** ([fingeringOf]): one finger per fret, counted from the lowest fret pressed, or from the
 * 1st fret when the shape rings open strings and that fits exactly (C is x32010 with fingers 3 2 1,
 * Cmaj7 x32000 with 3 2). A barre (index finger across) when more than four strings are pressed
 * (F 133211), or when the lowest fret holds the last two strings or more side by side (Dm7 xx0211,
 * ukulele B♭ 3211). Five strings pressed without that barre may take a short one by a later finger
 * across the last strings at the highest fret (Cm9 x31333, the ring finger across). Thumb-over
 * shapes are not suggested.
 *
 * **Ranking** ([effortOf]): roughly how hard a shape is, lowest first. Each finger costs 1, a barre
 * 0.5 more (1.5 by a finger other than the index, so C6 stays x32210 before x35555), each fret
 * of stretch 0.5, each fret up the neck 0.5, each string left out 0.7, a fifth left out 0.25 (C7 x32310 still before x35353), an inversion 1. A power chord's muted strings cost
 * nothing (it mutes them all), but one on two strings costs 2 more than root, fifth and octave on
 * three (E5 022xxx before 02xxxx). Open strings ringing while a finger is above the 3rd fret cost 2 a
 * fret: the hand has left the open position (that keeps x20402 below the barre Bm x24432). Fingers
 * arching over open strings to a higher fret cost 1 a fret ([archOf]: x10331 below the barre B♭
 * x13331, 102210 below Fmaj7 xx3210, xx1023 below E♭7 xx1323). Weighed so the shapes chord books show first come
 * first in standard tuning (`ChordShapeFinderTest`).
 */
object ChordShapeFinder {

    /** The comfortable stretch: the highest fret pressed at most this far above the lowest (four frets). */
    const val MAX_SPAN = 3

    /** No finger goes above this fret. */
    const val HIGHEST_FRET = 12

    /** How many shapes [shapesOf] gives by default: enough to swipe through, few enough to stay useful. */
    const val DEFAULT_LIMIT = 6

    private const val FINGERS = 4

    /** Up to [limit] shapes of [chord] on [tuning], easiest first; empty if it has none within reach. */
    fun shapesOf(chord: Chord, tuning: Tuning, limit: Int = DEFAULT_LIMIT): List<ChordShape> {
        val opens = IntArray(tuning.stringCount) { tuning.strings[it].midi }
        var search = Search(opens, chord, topMuted = false)
        for (lowest in 1..HIGHEST_FRET) search.fromFret(lowest)
        if (search.found.isEmpty() && opens.size >= 5) {
            search = Search(opens, chord, topMuted = true)
            for (lowest in 1..HIGHEST_FRET) search.fromFret(lowest)
        }
        return search.found.sortedWith(compareBy({ it.first }, { it.second.lowestFret }, { it.second.toString() }))
            .take(limit)
            .map { it.second }
    }

    /**
     * The fewest strings a shape plays on an instrument with [strings] strings: every one on four
     * strings or fewer (ukulele and mandolin charts strum them all), else four (D is xx0232 on a
     * guitar, xxx0232 on a 7-string).
     */
    internal fun minPlayed(strings: Int): Int = minOf(strings, 4)

    /** The pitch-class mask (bit i = i semitones above C) of [mask] built on [root] instead of C. */
    internal fun rotate(mask: Int, root: Int): Int = ((mask shl root) or (mask ushr (12 - root))) and 0xFFF

    private class Search(val opens: IntArray, val chord: Chord, val topMuted: Boolean) {
        val strings = opens.size
        val root = chord.root.ordinal
        val tones = rotate(chord.quality.mask, root)
        val required = rotate(
            if (chord.quality.fifthOptional) chord.quality.maskWithoutFifth else chord.quality.mask,
            root,
        )
        val fifth = 1 shl ((root + 7) % 12)
        val maxMuted = strings - minPlayed(strings) - (if (topMuted) 1 else 0)
        val power = chord.quality == ChordQuality.POWER_CHORD
        val frets = IntArray(strings)

        // A power chord's strings: from powerFirst to powerLast, the rest muted.
        var powerFirst = 0
        var powerLast = 0
        val seen = HashSet<String>()
        val found = ArrayList<Pair<Double, ChordShape>>()

        fun fromFret(lowest: Int) {
            if (!power) return place(0, lowest, minOf(lowest + MAX_SPAN, HIGHEST_FRET))
            for (first in 0..powerFirstStringMax(strings)) for (count in POWER_STRINGS) {
                if (first + count > strings) continue
                powerFirst = first
                powerLast = first + count - 1
                place(0, lowest, minOf(lowest + MAX_SPAN, HIGHEST_FRET))
            }
        }

        private fun place(string: Int, low: Int, high: Int) {
            if (string == strings) return judge()
            if (power && string !in powerFirst..powerLast) {
                frets[string] = ChordShape.MUTED
                return place(string + 1, low, high)
            }
            if (!power && string < maxMuted && (string == 0 || frets[string - 1] == ChordShape.MUTED)) {
                frets[string] = ChordShape.MUTED
                place(string + 1, low, high)
            }
            if (topMuted && string == strings - 1) {
                frets[string] = ChordShape.MUTED
                return place(string + 1, low, high)
            }
            if (isTone(opens[string])) {
                frets[string] = 0
                place(string + 1, low, high)
            }
            for (fret in low..high) {
                if (!isTone(opens[string] + fret)) continue
                frets[string] = fret
                place(string + 1, low, high)
            }
        }

        private fun isTone(midi: Int) = tones and (1 shl midi.mod(12)) != 0

        private fun judge() {
            var sounded = 0
            var bass = Int.MAX_VALUE
            for (s in 0 until strings) {
                if (frets[s] == ChordShape.MUTED) continue
                val midi = opens[s] + frets[s]
                sounded = sounded or (1 shl midi.mod(12))
                bass = minOf(bass, midi)
            }
            if (sounded and required != required) return
            // Every string open or under one barre, with the fifth in the bass: the open tuning's own
            // chord (open G strummed open is 000000, its C 555555), played as a root-position chord.
            val wholeNeck = !power && frets.all { it == frets[0] } && bass.mod(12) == (root + 7) % 12
            val rootInBass = bass.mod(12) == root || wholeNeck
            if (!rootInBass && (strings >= 5 || power)) return
            val key = frets.joinToString(",")
            if (key in seen) return
            val fingering = fingeringOf(frets) ?: return
            seen += key
            val shape = ChordShape(chord, frets.toList(), fingering.fingers.toList(), fingering.barre)
            found += effortOf(shape, rootInBass, hasFifth = sounded and fifth != 0) to shape
        }
    }

    /** A power chord's lowest string, counted from the thickest: one of the lowest three of a guitar, the first two on four strings. */
    internal fun powerFirstStringMax(strings: Int): Int = maxOf(1, strings - 4)

    /** A power chord sounds on two or three strings: root and fifth, or root, fifth and octave. */
    private val POWER_STRINGS = 2..3

    /** Suggested fingers per string and the barre, or null if four fingers can't hold [frets]. */
    internal class Fingering(val fingers: IntArray, val barre: Barre?)

    /** See the class comment, *Fingers*. Null when the shape needs more than four fingers. */
    internal fun fingeringOf(frets: IntArray): Fingering? {
        val fingers = IntArray(frets.size)
        val pressed = frets.indices.filter { frets[it] > 0 }.sortedWith(compareBy({ frets[it] }, { it }))
        if (pressed.isEmpty()) return Fingering(fingers, null)
        val lowest = frets[pressed[0]]
        val barre = barreOf(frets, lowest, needed = pressed.size > FINGERS)
        if (barre == null) {
            if (pressed.size > FINGERS) return highBarreFingering(frets, pressed, lowest)
            val openPosition = frets.any { it == 0 } && assign(pressed, frets, anchor = 1, firstFinger = 1, fingers)
            if (!openPosition) assign(pressed, frets, anchor = lowest, firstFinger = 1, fingers)
            return Fingering(fingers, null)
        }
        for (s in barre.firstString..barre.lastString) if (frets[s] == lowest) fingers[s] = barre.finger
        val rest = pressed.filter { fingers[it] == 0 }
        if (rest.size > FINGERS - 1) return null
        assign(rest, frets, anchor = lowest, firstFinger = 2, fingers)
        return Fingering(fingers, barre)
    }

    /**
     * Five strings pressed with no barre at the lowest fret: a short barre by a later finger across
     * the last strings at the highest fret, the rest a finger each below it (Cm9 x31333: index
     * on the D string, middle on the A, ring across G, B and E), at most [HIGH_BARRE_REACH] frets
     * above the lowest. Null if that doesn't fit.
     */
    private fun highBarreFingering(frets: IntArray, pressed: List<Int>, lowest: Int): Fingering? {
        val last = frets.lastIndex
        val top = frets[last]
        if (last < 1 || top <= lowest || top - lowest > HIGH_BARRE_REACH || frets[last - 1] != top || pressed.any { frets[it] > top }) return null
        var first = last - 1
        while (first > 0 && frets[first - 1] == top) first--
        val rest = pressed.filter { it < first }
        if (rest.size > FINGERS - 1) return null
        // The fingers below it in turn, lowest fret first, so the barre's finger is the next one.
        val fingers = IntArray(frets.size)
        for ((k, string) in rest.withIndex()) fingers[string] = k + 1
        val finger = rest.size + 1
        for (s in first..last) fingers[s] = finger
        return Fingering(fingers, Barre(top, first, last, finger))
    }

    /**
     * The barre at [lowest], if the shape takes one. When it is [needed] (more strings pressed than
     * fingers), from the first string played to the last pressed there, with no string after it open:
     * the E and A shapes (133211, x13331), not a barre in the middle with thicker strings pressed
     * higher. Otherwise a short one across the last strings, when two or more side by side are pressed
     * there (xx3211). Null if there is none, or a string under it would be open or muted.
     */
    private fun barreOf(frets: IntArray, lowest: Int, needed: Boolean): Barre? {
        val last = frets.lastIndex
        val first: Int
        val end: Int
        if (needed) {
            first = frets.indexOfFirst { it != ChordShape.MUTED }
            end = frets.indexOfLast { it == lowest }
            if (frets[first] != lowest || (end + 1..last).any { frets[it] == 0 }) return null
        } else {
            if (last < 1 || frets[last] != lowest || frets[last - 1] != lowest) return null
            var start = last - 1
            while (start > 0 && frets[start - 1] == lowest) start--
            first = start
            end = last
        }
        if (first == end) return null
        for (s in first..end) if (frets[s] < lowest) return null
        return Barre(lowest, first, end)
    }

    /**
     * Gives the strings in [order] (sorted by fret, then string) fingers from [firstFinger] up: ideally
     * the fret's place counted from [anchor] (one finger per fret), a later string at the same fret
     * the next finger, kept rising and within four. Writes them into [fingers]; true if every string
     * got its ideal finger.
     */
    private fun assign(order: List<Int>, frets: IntArray, anchor: Int, firstFinger: Int, fingers: IntArray): Boolean {
        var exact = true
        var previous = firstFinger - 1
        var sameFret = 0
        for ((k, string) in order.withIndex()) {
            sameFret = if (k > 0 && frets[order[k - 1]] == frets[string]) sameFret + 1 else 0
            val ideal = frets[string] - anchor + 1 + sameFret
            val finger = ideal.coerceIn(previous + 1, FINGERS - (order.size - 1 - k))
            if (finger != ideal) exact = false
            fingers[string] = finger
            previous = finger
        }
        return exact
    }

    /** See the class comment, *Ranking*: how hard [shape] is to play, lower is easier. */
    internal fun effortOf(shape: ChordShape, rootInBass: Boolean, hasFifth: Boolean): Double {
        val fingersUsed = shape.fingers.filter { it > 0 }.toSet().size
        val power = shape.chord.quality == ChordQuality.POWER_CHORD
        val played = shape.frets.count { it != ChordShape.MUTED }
        val muted = if (power) 0 else shape.frets.count { it == ChordShape.MUTED }
        val open = shape.frets.any { it == 0 }
        val span = if (shape.lowestFret == 0) 0 else shape.highestFret - shape.lowestFret
        val outOfOpenPosition = if (open) (shape.highestFret - OPEN_POSITION).coerceAtLeast(0) else 0
        return fingersUsed * 1.0 +
            (if (shape.barre != null) 0.5 else 0.0) +
            (if ((shape.barre?.finger ?: 1) > 1) 1.0 else 0.0) +
            span * 0.5 +
            shape.lowestFret * 0.5 +
            muted * 0.7 +
            outOfOpenPosition * 2.0 +
            archOf(shape.frets) * 1.0 +
            (if (rootInBass) 0.0 else 1.0) +
            (if (hasFifth) 0.0 else 0.25) +
            (if (power && played < 3) 2.0 else 0.0)
    }

    /**
     * Frets the fingers climb while reaching over open strings: for each run of open strings between
     * two pressed ones, how much higher the string after it is pressed than the one before. 0 for C
     * x32010 (the fingers come down towards the nut), 1 for G 320003 and Fmaj7 102210, 2 for B♭
     * x10331, whose fingers arch over the open string while they stretch.
     */
    internal fun archOf(frets: List<Int>): Int {
        var arch = 0
        var before = -1
        var gap = false
        for (fret in frets) {
            when {
                fret > 0 -> {
                    if (gap && before > 0) arch += (fret - before).coerceAtLeast(0)
                    before = fret
                    gap = false
                }
                fret == 0 -> gap = before > 0
            }
        }
        return arch
    }

    /** How far above the lowest fret a barre by a later finger lies at most (Cm9 x31333: two frets). */
    private const val HIGH_BARRE_REACH = 2

    /** The open position: frets a hand at the nut reaches while open strings ring. */
    private const val OPEN_POSITION = 3
}
