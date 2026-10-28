package io.github.klaboukes.twangle.core.dsp.synth

/**
 * A few notes of one [voice] sounding together, for the reference-tone player: an interval's two
 * notes at once (Ear training's Intervals), or a chord's strings strummed one after another (Chord
 * type). Each note is a [StringSynth] of its own; the mix is their sum scaled by 1 / the number of
 * notes, so, each note staying under [StringSynth.PEAK], the mix never clips. One note is exactly
 * a [StringSynth]'s tone, sample for sample, so the tuner's reference tone sounds as before.
 *
 * With a spread ([start]'s `spreadSeconds`), note i begins i × spread after the first, as a pick
 * crossing the strings does. Every note ends by itself ([StringSynth.durationSeconds] after it
 * began) or fades out on [release]; the mix ends when the last has.
 *
 * A synth per note is made the first time that many notes are asked for, and kept, as the player
 * keeps one per voice; after that [start], [release] and [render] allocate nothing. Not
 * thread-safe: one thread renders.
 */
class StringMix(
    val sampleRate: Int,
    val voice: StringVoice,
    val durationSeconds: Float = StringSynth.DEFAULT_DURATION_SECONDS,
) {

    private val synths = arrayOfNulls<StringSynth>(MAX_NOTES)
    private val synth0 = synthAt(0)

    /** Highest frequency [start] accepts for any note. */
    val maxFrequencyHz: Float = synth0.maxFrequencyHz

    // The notes of the tone started last: how many, where each begins (in samples from the start),
    // and those dropped by a release before they began.
    private var notes = 0
    private val startAt = IntArray(MAX_NOTES)
    private val dropped = BooleanArray(MAX_NOTES)
    private var position = 0
    private var scale = 1f

    // Each note is rendered here, then added into the output.
    private val scratch = FloatArray(SCRATCH_FRAMES)

    /** True from [start] until every note has faded to silence. */
    val isSounding: Boolean
        get() {
            for (i in 0 until notes) if (!dropped[i] && synths[i]!!.isSounding) return true
            return false
        }

    /**
     * Starts [count] notes, [frequenciesHz] from index 0 (each 0 < f ≤ [maxFrequencyHz]), dropping
     * any tone still sounding: all at once, or note i [spreadSeconds] × i after the first. Where
     * [octavePairs] says so, a note sounds as an octave pair ([StringSynth.start]). To change notes
     * without a click, [release] the old ones and start the new ones once [isSounding] is false.
     */
    fun start(
        frequenciesHz: FloatArray,
        count: Int = frequenciesHz.size,
        spreadSeconds: Float = 0f,
        octavePairs: BooleanArray? = null,
    ) {
        require(count in 1..MAX_NOTES && count <= frequenciesHz.size) { "One to $MAX_NOTES notes, not $count" }
        require(spreadSeconds >= 0f && spreadSeconds <= MAX_SPREAD_SECONDS) { "spreadSeconds must be in 0..$MAX_SPREAD_SECONDS, was $spreadSeconds" }
        for (i in 0 until count) {
            val f = frequenciesHz[i]
            require(f > 0f && f <= maxFrequencyHz) { "frequencyHz must be in (0, $maxFrequencyHz], was $f" }
        }
        val spread = Math.round(spreadSeconds * sampleRate)
        for (i in 0 until count) {
            synthAt(i).start(frequenciesHz[i], octavePairs != null && i < octavePairs.size && octavePairs[i])
            startAt[i] = i * spread
            dropped[i] = false
        }
        // Notes of an earlier, larger tone are left out from here on.
        notes = count
        position = 0
        scale = 1f / count
    }

    /**
     * Fades every note out over [seconds], ending on silence (as [StringSynth.release]); notes of a
     * strum that hadn't begun yet never do.
     */
    fun release(seconds: Float = StringSynth.STOP_FADE_SECONDS) {
        for (i in 0 until notes) {
            if (position < startAt[i]) dropped[i] = true else synths[i]!!.release(seconds)
        }
    }

    /**
     * Writes up to [count] samples (−[StringSynth.PEAK]..[StringSynth.PEAK]) into [out] from
     * [offset] and returns how many it wrote: fewer than [count] when the last note ends, 0 once it
     * has (or before [start]).
     */
    fun render(out: FloatArray, offset: Int = 0, count: Int = out.size - offset): Int {
        require(offset >= 0 && count >= 0 && offset + count <= out.size) { "offset/count outside the array" }
        if (notes == 1 && startAt[0] == 0) {
            // One note: the synth's own samples, untouched.
            val written = if (dropped[0]) 0 else synth0.render(out, offset, count)
            position += written
            return written
        }
        out.fill(0f, offset, offset + count)
        var written = 0
        for (i in 0 until notes) {
            val synth = synths[i]!!
            if (dropped[i] || !synth.isSounding) continue
            // Where this note's samples begin in this block: a strum's later strings wait their turn.
            val from = (startAt[i] - position).coerceAtLeast(0)
            if (from >= count) {
                written = count
                continue
            }
            var at = from
            while (at < count) {
                val n = synth.render(scratch, 0, minOf(SCRATCH_FRAMES, count - at))
                if (n == 0) break
                for (k in 0 until n) out[offset + at + k] += scratch[k] * scale
                at += n
            }
            written = maxOf(written, at)
        }
        position += written
        return written
    }

    /** The synth for note [index], made the first time a tone has that many notes. */
    private fun synthAt(index: Int): StringSynth =
        synths[index] ?: StringSynth(sampleRate, voice, durationSeconds).also { synths[index] = it }

    companion object {
        /** Most notes at once: an 8-string guitar's chord. */
        const val MAX_NOTES = 8

        /** The widest spread between notes: a slow strum. */
        const val MAX_SPREAD_SECONDS = 0.5f

        private const val SCRATCH_FRAMES = 256
    }
}
