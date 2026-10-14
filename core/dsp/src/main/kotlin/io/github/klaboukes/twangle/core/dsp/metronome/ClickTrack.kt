package io.github.klaboukes.twangle.core.dsp.metronome

/**
 * Told about every beat as [ClickTrack.render] writes its click.
 *
 * @param index beats since the track started, from 0.
 * @param beatInBar position in the bar, from 0 (the downbeat) to [Meter.beatsPerBar] − 1.
 * @param frame the sample the click starts on, counted from the start of the track.
 */
fun interface BeatListener {
    fun onBeat(index: Long, beatInBar: Int, accent: BeatAccent, frame: Long)
}

/**
 * The metronome's sound: an endless stream of clicks at a tempo and meter, rendered chunk by chunk
 * for an audio output.
 *
 * **Sample-exact timing.** Every beat starts on a whole sample worked out from the tempo in integer
 * arithmetic, never by adding up rounded intervals: with the tempo last set at beat a on sample A,
 * beat n starts on sample `A + round((n − a) · 60 · sampleRate / bpm)`. Each beat is at most half a
 * sample from its ideal time and the error never builds up, however long the metronome runs and
 * however the chunks are cut. (At 120 BPM and 48 kHz a beat is exactly 24 000 samples; at 97 BPM
 * and 44.1 kHz the beats are 27 278 or 27 279 samples apart, averaging the exact 27 278.35.)
 *
 * **Changes while playing** take effect from the next beat, which is never moved earlier than the
 * new tempo allows:
 * - [bpm]: the next beat comes one new interval after the last beat that sounded (or at once, if
 *   that moment has passed), and the beats after it follow the new tempo from there.
 * - [meter]: the next beat starts a new bar (a downbeat); the timing doesn't change.
 *
 * The tempo is clamped to [MIN_BPM]..[MAX_BPM], so beats are always at least 0.24 s apart and a
 * click (0.04 s) always ends before the next starts.
 *
 * [render] and the setters allocate nothing. Not thread-safe: one thread (the audio writer) uses it.
 */
class ClickTrack(
    val sampleRate: Int,
    bpm: Int = DEFAULT_BPM,
    meter: Meter = Meter.DEFAULT,
) {

    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
    }

    /** The clicks this track plays. */
    val sounds = ClickSounds(sampleRate)

    /** Beats per minute, kept within [MIN_BPM]..[MAX_BPM]. Setting it takes effect from the next beat. */
    var bpm: Int = clampBpm(bpm)
        set(value) {
            val clamped = clampBpm(value)
            if (clamped == field) return
            field = clamped
            retime()
        }

    /** The time signature. Setting it makes the next beat a downbeat. */
    var meter: Meter = meter
        set(value) {
            if (value == field) return
            field = value
            barStartBeat = nextBeat
        }

    /** Samples rendered since the start (or the last [reset]); the next sample [render] writes. */
    var framesRendered: Long = 0L
        private set

    // The tempo was last set at beat anchorBeat, on sample anchorFrame.
    private var anchorBeat = 0L
    private var anchorFrame = 0L

    // The next beat to sound and its sample; the sample of the last beat that sounded (-1: none yet).
    private var nextBeat = 0L
    private var nextBeatFrame = 0L
    private var lastBeatFrame = NONE

    // The beat the current bar started on, so a meter change can start a new bar.
    private var barStartBeat = 0L

    // The click still sounding and how far into it we are (null: silence).
    private var voice: FloatArray? = null
    private var voicePosition = 0

    /** Starts over: the next [render] begins at sample 0 with beat 0, a downbeat. Tempo and meter stay. */
    fun reset() {
        framesRendered = 0L
        anchorBeat = 0L
        anchorFrame = 0L
        nextBeat = 0L
        nextBeatFrame = 0L
        lastBeatFrame = NONE
        barStartBeat = 0L
        voice = null
        voicePosition = 0
    }

    /** The sample beat [index] starts on at the current tempo (for beats not yet sounded). */
    fun frameOfBeat(index: Long): Long {
        // round((index − anchorBeat) · 60 · sampleRate / bpm), half up, in exact integer arithmetic.
        val numerator = (index - anchorBeat) * SECONDS_PER_MINUTE * sampleRate
        return anchorFrame + (2 * numerator + bpm) / (2L * bpm)
    }

    /**
     * Writes the next [count] samples into [out] from [offset]: silence with the clicks of every beat
     * that falls among them, each starting on its exact sample, and tells [listener] about each beat
     * in order as it is written.
     */
    fun render(out: FloatArray, offset: Int = 0, count: Int = out.size - offset, listener: BeatListener? = null) {
        require(offset >= 0 && count >= 0 && offset + count <= out.size) { "offset/count outside the array" }
        out.fill(0f, offset, offset + count)
        var i = 0
        while (i < count) {
            // Up to the next beat (or the end of the chunk), the click still sounding goes on.
            val toBeat = nextBeatFrame - (framesRendered + i)
            val end = if (toBeat < count - i) i + toBeat.toInt() else count
            val sounding = voice
            if (sounding != null) {
                val n = minOf(end - i, sounding.size - voicePosition)
                System.arraycopy(sounding, voicePosition, out, offset + i, n)
                voicePosition += n
                if (voicePosition == sounding.size) voice = null
            }
            i = end
            if (i < count) startBeat(framesRendered + i, listener)
        }
        framesRendered += count
    }

    private fun startBeat(frame: Long, listener: BeatListener?) {
        val beatInBar = ((nextBeat - barStartBeat) % meter.beatsPerBar).toInt()
        val accent = meter.accentOf(beatInBar)
        voice = sounds.waveform(accent)
        voicePosition = 0
        listener?.onBeat(nextBeat, beatInBar, accent, frame)
        lastBeatFrame = frame
        nextBeat++
        nextBeatFrame = frameOfBeat(nextBeat)
    }

    // A new tempo: count from the last beat that sounded, never scheduling the next one in the past.
    private fun retime() {
        if (lastBeatFrame == NONE) return // Nothing sounded yet: beat 0 still starts on sample 0.
        anchorBeat = nextBeat - 1
        anchorFrame = lastBeatFrame
        nextBeatFrame = frameOfBeat(nextBeat)
        if (nextBeatFrame < framesRendered) {
            anchorBeat = nextBeat
            anchorFrame = framesRendered
            nextBeatFrame = framesRendered
        }
    }

    companion object {
        /** Slowest tempo. */
        const val MIN_BPM = 30

        /** Fastest tempo. */
        const val MAX_BPM = 250

        /** Tempo of a new metronome. */
        const val DEFAULT_BPM = 120

        private const val SECONDS_PER_MINUTE = 60L
        private const val NONE = -1L

        /** [bpm] kept within [MIN_BPM]..[MAX_BPM]. */
        fun clampBpm(bpm: Int): Int = bpm.coerceIn(MIN_BPM, MAX_BPM)
    }
}
