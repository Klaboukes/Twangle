package io.github.klaboukes.twangle.core.dsp.metronome

import kotlin.math.PI
import kotlin.math.cos

/**
 * Told about every beat as [ClickTrack.render] writes its click.
 *
 * @param index beats since the track started, from 0.
 * @param beatInBar position in the bar, from 0 (the downbeat) to [Meter.beatsPerBar] − 1.
 * @param frame the sample the click starts on, counted from the start of the track.
 */
fun interface BeatListener {
    fun onBeat(index: Long, beatInBar: Int, accent: BeatAccent, frame: Long)

    /**
     * Told about every click between the beats ([Subdivision]) as it is written; nothing by default.
     *
     * @param beat the beat it follows (its index, as in [onBeat]).
     * @param click its place in that beat, from 1 to the beat's [Subdivision.clicksPerBeat] − 1.
     * @param frame the sample the click starts on.
     */
    fun onSubdivision(beat: Long, click: Int, frame: Long) {}
}

/**
 * The metronome's sound: an endless stream of clicks at a tempo and meter, rendered chunk by chunk
 * for an audio output, with softer clicks between the beats when a [subdivision] is set.
 *
 * **Sample-exact timing.** Every click starts on a whole sample worked out from the tempo in
 * integer arithmetic, never by adding up rounded intervals: with the tempo last set at beat a on
 * sample A, click j (from 0, the beat's own) of beat n's k clicks starts on sample
 * `A + round(((n − a) · k + j) · 60 · sampleRate / (bpm · k))`. For j = 0 that is
 * `A + round((n − a) · 60 · sampleRate / bpm)`, so the beats fall on the same samples whatever the
 * subdivision. Each click is at most half a sample from its ideal time and the error never builds
 * up, however long the metronome runs and however the chunks are cut. (At 120 BPM and 48 kHz a beat
 * is exactly 24 000 samples; at 97 BPM and 44.1 kHz the beats are 27 278 or 27 279 samples apart,
 * averaging the exact 27 278.35.)
 *
 * **Changes while playing** take effect from the next beat, which is never moved earlier than the
 * new tempo allows:
 * - [bpm]: the next beat comes one new interval after the last beat that sounded (or at once, if
 *   that moment has passed), and the beats after it follow the new tempo from there. The clicks
 *   left in the beat sounding follow the new tempo too; one whose new moment has passed is left out.
 * - [meter]: the next beat starts a new bar (a downbeat); the timing doesn't change.
 * - [subdivision]: the next beat is split the new way; the beat sounding keeps its clicks.
 * - [sound]: the next click sounds with the new sound; a click still sounding ends as it began.
 *
 * **One voice.** The tempo is clamped to [MIN_BPM]..[MAX_BPM], so beats are always at least 0.24 s
 * apart (a tempo change never brings the next beat closer than one new interval), and without a
 * subdivision every click (0.04–0.18 s, [ClickSounds]) ends before the next starts. With one, clicks
 * come as little as 60 ms apart (250 BPM in sixteenths), so a click still sounding when the next
 * starts is cut: it fades out along half a cosine over the [CUT_FADE_SECONDS] before the next, so it
 * reaches 0 just as that one begins and nothing crackles. A tempo change never puts the next click
 * so close that the fade has no room.
 *
 * [render] and the tempo, meter and subdivision setters allocate nothing. Setting [sound] to one not
 * played yet renders its clicks once ([ClickSounds.prepare], a few milliseconds); after that it
 * allocates nothing either. Not thread-safe: one thread (the audio writer) uses it.
 */
class ClickTrack(
    val sampleRate: Int,
    bpm: Int = DEFAULT_BPM,
    meter: Meter = Meter.DEFAULT,
    sound: ClickSound = ClickSound.DEFAULT,
    subdivision: Subdivision = Subdivision.DEFAULT,
) {

    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
    }

    /** The clicks this track plays. */
    val sounds = ClickSounds(sampleRate)

    /** The instrument the clicks sound with. Setting it takes effect from the next click. */
    var sound: ClickSound = sound.also { sounds.prepare(it) }
        set(value) {
            if (value == field) return
            sounds.prepare(value)
            field = value
        }

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

    /**
     * The clicks between the beats; in a meter that doesn't offer it, none ([Subdivision.within]).
     * Setting it takes effect from the next beat.
     */
    var subdivision: Subdivision = subdivision

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

    // How many clicks the beat that sounded last has, and which of them sounds next (beatClicks:
    // none left, the next click is the next beat's); the sample the next click starts on.
    private var beatClicks = 1
    private var nextClick = 1
    private var nextClickFrame = 0L

    // The click still sounding and how far into it we are (null: silence), and the sample it must
    // have faded out by: the next click's, unless a tempo change moved that after the fade began.
    private var voice: FloatArray? = null
    private var voicePosition = 0
    private var cutFrame = 0L

    // cutFade[d − 1]: the gain of a click d samples before the next click cuts it, 1 → 0 along half a cosine.
    private val cutFade = (CUT_FADE_SECONDS * sampleRate).toInt().coerceAtLeast(1).let { size ->
        FloatArray(size) { i -> (0.5 - 0.5 * cos(PI * (i + 1) / (size + 1))).toFloat() }
    }

    /** Starts over: the next [render] begins at sample 0 with beat 0, a downbeat. Tempo, meter, subdivision and sound stay. */
    fun reset() {
        framesRendered = 0L
        anchorBeat = 0L
        anchorFrame = 0L
        nextBeat = 0L
        nextBeatFrame = 0L
        lastBeatFrame = NONE
        barStartBeat = 0L
        beatClicks = 1
        nextClick = 1
        nextClickFrame = 0L
        voice = null
        voicePosition = 0
        cutFrame = 0L
    }

    /** The sample beat [index] starts on at the current tempo (for beats not yet sounded). */
    fun frameOfBeat(index: Long): Long = frameOfClick(index, 0, 1)

    // The sample click [click] of beat [beat]'s [clicks] starts on at the current tempo:
    // round(((beat − anchorBeat) · clicks + click) · 60 · sampleRate / (bpm · clicks)), half up, in
    // exact integer arithmetic.
    private fun frameOfClick(beat: Long, click: Int, clicks: Int): Long {
        val numerator = ((beat - anchorBeat) * clicks + click) * SECONDS_PER_MINUTE * sampleRate
        val denominator = bpm.toLong() * clicks
        return anchorFrame + (2 * numerator + denominator) / (2 * denominator)
    }

    /**
     * Writes the next [count] samples into [out] from [offset]: silence with the clicks of every beat,
     * and of every click between beats, that falls among them, each starting on its exact sample, and
     * tells [listener] about each in order as it is written.
     */
    fun render(out: FloatArray, offset: Int = 0, count: Int = out.size - offset, listener: BeatListener? = null) {
        require(offset >= 0 && count >= 0 && offset + count <= out.size) { "offset/count outside the array" }
        out.fill(0f, offset, offset + count)
        var i = 0
        while (i < count) {
            // Up to the next click (or the end of the chunk), the click still sounding goes on.
            val now = framesRendered + i
            val toClick = nextClickFrame - now
            val end = if (toClick < count - i) i + toClick.toInt() else count
            val sounding = voice
            if (sounding != null) {
                val left = sounding.size - voicePosition
                val n = minOf(end - i, left, (cutFrame - now).coerceIn(0L, left.toLong()).toInt())
                System.arraycopy(sounding, voicePosition, out, offset + i, n)
                // Still sounding at the cut: fade out over the samples before it.
                if (now + left > cutFrame) {
                    var k = (cutFrame - cutFade.size - now).coerceIn(0L, n.toLong()).toInt()
                    while (k < n) {
                        out[offset + i + k] *= cutFade[(cutFrame - now - k).toInt() - 1]
                        k++
                    }
                }
                voicePosition += n
                if (voicePosition == sounding.size || now + n >= cutFrame) voice = null
            }
            i = end
            if (i < count) startClick(framesRendered + i, listener)
        }
        framesRendered += count
    }

    private fun startClick(frame: Long, listener: BeatListener?) {
        if (nextClick < beatClicks) {
            voice = sounds.waveform(sound, BeatAccent.SUBDIVISION)
            voicePosition = 0
            listener?.onSubdivision(nextBeat - 1, nextClick, frame)
            nextClick++
        } else {
            startBeat(frame, listener)
        }
        nextClickFrame = if (nextClick < beatClicks) frameOfClick(nextBeat - 1, nextClick, beatClicks) else nextBeatFrame
        cutFrame = nextClickFrame
    }

    private fun startBeat(frame: Long, listener: BeatListener?) {
        val beatInBar = ((nextBeat - barStartBeat) % meter.beatsPerBar).toInt()
        val accent = meter.accentOf(beatInBar)
        voice = sounds.waveform(sound, accent)
        voicePosition = 0
        listener?.onBeat(nextBeat, beatInBar, accent, frame)
        lastBeatFrame = frame
        beatClicks = subdivision.within(meter).clicksPerBeat
        nextClick = 1
        nextBeat++
        nextBeatFrame = frameOfBeat(nextBeat)
    }

    // A new tempo: count from the last beat that sounded, never scheduling a click in the past, nor
    // so soon that the click sounding has no room to fade out before it. A fade already begun goes on
    // to its end, and the next click comes no sooner.
    private fun retime() {
        if (lastBeatFrame == NONE) return // Nothing sounded yet: beat 0 still starts on sample 0.
        anchorBeat = nextBeat - 1
        anchorFrame = lastBeatFrame
        val sounding = voice
        val left = if (sounding == null) 0 else sounding.size - voicePosition
        val fading = sounding != null && cutFrame - framesRendered < cutFade.size && framesRendered + left > cutFrame
        val earliest = if (fading) cutFrame else framesRendered + minOf(left, cutFade.size)
        while (nextClick < beatClicks && frameOfClick(anchorBeat, nextClick, beatClicks) < earliest) nextClick++
        nextBeatFrame = frameOfBeat(nextBeat)
        if (nextBeatFrame < earliest) {
            anchorBeat = nextBeat
            anchorFrame = earliest
            nextBeatFrame = earliest
        }
        nextClickFrame = if (nextClick < beatClicks) frameOfClick(nextBeat - 1, nextClick, beatClicks) else nextBeatFrame
        if (!fading) cutFrame = nextClickFrame
    }

    companion object {
        /** Slowest tempo. */
        const val MIN_BPM = 30

        /** Fastest tempo. */
        const val MAX_BPM = 250

        /** Tempo of a new metronome. */
        const val DEFAULT_BPM = 120

        /** How long a click still sounding fades out for before the next click cuts it. */
        const val CUT_FADE_SECONDS = 0.003

        private const val SECONDS_PER_MINUTE = 60L
        private const val NONE = -1L

        /** [bpm] kept within [MIN_BPM]..[MAX_BPM]. */
        fun clampBpm(bpm: Int): Int = bpm.coerceIn(MIN_BPM, MAX_BPM)
    }
}
