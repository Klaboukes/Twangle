package io.github.klaboukes.twangle.core.dsp.drone

import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * The drone under the Scales tool (Phase 48): a steady low note, the scale's root, alone or with
 * its fifth and octave ([DroneVoicing]), in one of four sounds ([DroneSound]), for as long as it is
 * asked. It is rendered on and on, sample after sample, never a recording and never a loop, so it
 * can't click at a seam or drift: every partial is a phasor at its exact frequency ([PartialBank]),
 * and what moves (a tanpura's plucks, the harmonium's bellows, the pad's filter) is made afresh as
 * it goes, the random parts from a [seed], so the same calls make the same sound.
 *
 * **Pitch** comes in Hz: the caller turns the root into a frequency with its
 * [io.github.klaboukes.twangle.core.dsp.music.NoteMapper], so the drone follows the A4 setting as
 * the reference tones do. [rootNote] places a scale's root in the drone's octave (G♯2 to G3, around
 * D3); `lower` puts the whole drone an octave below that.
 *
 * **Fades:** [play] from silence fades in over [FADE_IN_SECONDS]; [play] while it sounds, with
 * anything changed (the root, the sound, the voicing, lower), crossfades from the old drone to the
 * new over [CROSSFADE_SECONDS] (a change asked during a crossfade waits for it to end, and only
 * the last one asked is kept); [release] fades out over the time asked for and the last sample
 * written is exactly 0. The tanpura needs no fade in: it is silent until its first pluck, and each
 * pluck has its own 4 ms attack.
 *
 * **Level:** each sound and voicing has its own gain ([LEVELS]), so all twelve are about equally
 * loud (K-weighted, as LUFS) and none peaks above [PEAK_LIMIT].
 *
 * Works in blocks of [BLOCK] samples (levels are set once a block and ramped across it), whatever
 * [render] is asked for. Two layers, each with one of every sound, are made at construction, so
 * [play], [release] and [render] allocate nothing (hard rule 2). Not thread-safe: one thread calls
 * all of it.
 */
class DroneSynth(
    val sampleRate: Int,
    val seed: Long = DEFAULT_SEED,
) {

    init {
        require(sampleRate >= MIN_SAMPLE_RATE) { "sampleRate must be at least $MIN_SAMPLE_RATE, was $sampleRate" }
    }

    /** A gain that moves from one level to another along half a cosine, a sample at a time. */
    private class Ramp {
        var value = 1.0
            private set
        private var from = 1.0
        private var to = 1.0
        private var position = 0
        private var length = 0

        val target: Double get() = to
        val isMoving: Boolean get() = length > 0
        val samplesLeft: Int get() = length - position

        fun hold(level: Double) {
            value = level
            from = level
            to = level
            length = 0
        }

        fun moveTo(level: Double, samples: Int) {
            from = value
            to = level
            position = 0
            length = samples.coerceAtLeast(1)
        }

        /** The gain for the next sample; at the end of a move it is exactly the target. */
        fun step(): Double {
            if (length == 0) return value
            position++
            if (position >= length) {
                value = to
                length = 0
            } else {
                value = from + (to - from) * (0.5 - 0.5 * cos(PI * position / length))
            }
            return value
        }
    }

    private inner class Layer {
        val engines = arrayOf(TanpuraEngine(sampleRate), HarmoniumEngine(sampleRate), FluteOrganEngine(sampleRate), WarmPadEngine(sampleRate))
        val buffer = DoubleArray(BLOCK)
        val ramp = Ramp()
        var active = false
        var rootHz = 0f
        var sound = DroneSound.TANPURA
        var voicing = DroneVoicing.FIFTH
        var lower = false
        var level = 0.0

        fun plays(rootHz: Float, sound: DroneSound, voicing: DroneVoicing, lower: Boolean) =
            this.rootHz == rootHz && this.sound == sound && this.voicing == voicing && this.lower == lower

        fun begin(rootHz: Float, sound: DroneSound, voicing: DroneVoicing, lower: Boolean, startSeconds: Double) {
            this.rootHz = rootHz
            this.sound = sound
            this.voicing = voicing
            this.lower = lower
            level = LEVELS[sound.ordinal][voicing.ordinal]
            val hz = if (lower) 0.5 * rootHz else rootHz.toDouble()
            engines[sound.ordinal].begin(hz, voicing, startSeconds, random)
            active = true
        }

        fun renderBlock() {
            buffer.fill(0.0)
            engines[sound.ordinal].renderBlock(buffer)
        }
    }

    private val random = DroneRandom(seed)
    private val layers = arrayOf(Layer(), Layer())
    private var current = 0
    private val master = Ramp()
    private var playing = false
    private var releasing = false
    private var blocks = 0L

    // A change asked during a crossfade, played when it ends.
    private var pending = false
    private var pendingRootHz = 0f
    private var pendingSound = DroneSound.TANPURA
    private var pendingVoicing = DroneVoicing.FIFTH
    private var pendingLower = false

    // The block being handed out: mix[mixPosition until mixEnd] is still to be written.
    private val mix = FloatArray(BLOCK)
    private var mixPosition = 0
    private var mixEnd = 0

    /** True from [play] until a [release] has faded to silence and every sample is written. */
    val isSounding: Boolean get() = playing || mixPosition < mixEnd

    /** The root last asked for, in Hz (0 before the first [play]). */
    val rootHz: Float get() = if (pending) pendingRootHz else layers[current].rootHz

    /** The sound last asked for. */
    val sound: DroneSound get() = if (pending) pendingSound else layers[current].sound

    /** The voicing last asked for. */
    val voicing: DroneVoicing get() = if (pending) pendingVoicing else layers[current].voicing

    /** Whether the drone last asked for is an octave down. */
    val lower: Boolean get() = if (pending) pendingLower else layers[current].lower

    /**
     * Plays the drone on [rootHz] ([MIN_ROOT_HZ]..[MAX_ROOT_HZ]) in [sound] and [voicing], an
     * octave lower if [lower]: from silence it fades in; while it sounds it crossfades to the new
     * drone (nothing happens if nothing changed); while it fades out it comes back.
     */
    fun play(
        rootHz: Float,
        sound: DroneSound = DroneSound.TANPURA,
        voicing: DroneVoicing = DroneVoicing.FIFTH,
        lower: Boolean = false,
    ) {
        require(rootHz in MIN_ROOT_HZ..MAX_ROOT_HZ) { "rootHz must be in $MIN_ROOT_HZ..$MAX_ROOT_HZ, was $rootHz" }
        if (!playing) {
            for (layer in layers) layer.active = false
            blocks = 0
            current = 0
            pending = false
            val layer = layers[0]
            layer.begin(rootHz, sound, voicing, lower, startSeconds = 0.0)
            layer.ramp.hold(1.0)
            master.hold(0.0)
            master.moveTo(1.0, (FADE_IN_SECONDS * sampleRate).roundToInt())
            playing = true
            releasing = false
            return
        }
        if (releasing) {
            releasing = false
            master.moveTo(1.0, (FADE_IN_SECONDS * sampleRate).roundToInt())
        }
        if (crossfading) {
            pending = !layers[current].plays(rootHz, sound, voicing, lower)
            pendingRootHz = rootHz
            pendingSound = sound
            pendingVoicing = voicing
            pendingLower = lower
            return
        }
        if (layers[current].plays(rootHz, sound, voicing, lower)) return
        crossfadeTo(rootHz, sound, voicing, lower)
    }

    /**
     * Fades the drone out over [seconds] (at least one sample), ending on exactly 0. A fade already
     * running that ends sooner is kept; a longer one is cut short smoothly, from its current level.
     */
    fun release(seconds: Float = STOP_FADE_SECONDS) {
        if (!playing) return
        val length = (seconds * sampleRate).roundToInt().coerceAtLeast(1)
        if (releasing && master.samplesLeft <= length) return
        releasing = true
        pending = false
        master.moveTo(0.0, length)
    }

    /**
     * Writes up to [count] samples into [out] from [offset] and returns how many it wrote: fewer
     * than [count] when a [release] ends (its last sample exactly 0), 0 once it has (or before
     * [play]).
     */
    fun render(out: FloatArray, offset: Int = 0, count: Int = out.size - offset): Int {
        require(offset >= 0 && count >= 0 && offset + count <= out.size) { "offset/count outside the array" }
        var written = 0
        while (written < count) {
            if (mixPosition >= mixEnd) {
                if (!playing) break
                fillBlock()
            }
            val n = minOf(count - written, mixEnd - mixPosition)
            mix.copyInto(out, offset + written, mixPosition, mixPosition + n)
            mixPosition += n
            written += n
        }
        return written
    }

    private val crossfading: Boolean get() = layers[1 - current].active

    private fun crossfadeTo(rootHz: Float, sound: DroneSound, voicing: DroneVoicing, lower: Boolean) {
        val outgoing = layers[current]
        val incoming = layers[1 - current]
        val samples = (CROSSFADE_SECONDS * sampleRate).roundToInt()
        if (sound == DroneSound.TANPURA) {
            // Silent until its first pluck: it needs no fade in, and its plucks start afresh.
            incoming.begin(rootHz, sound, voicing, lower, startSeconds = 0.0)
            incoming.ramp.hold(1.0)
        } else {
            // A steady sound takes up the drone's clock, so it doesn't swell in again: the crossfade is its fade in.
            incoming.begin(rootHz, sound, voicing, lower, startSeconds = blocks * BLOCK.toDouble() / sampleRate)
            incoming.ramp.hold(0.0)
            incoming.ramp.moveTo(1.0, samples)
        }
        outgoing.ramp.moveTo(0.0, samples)
        current = 1 - current
    }

    private fun fillBlock() {
        for (layer in layers) if (layer.active) layer.renderBlock()
        var end = BLOCK
        val a = layers[0]
        val b = layers[1]
        for (s in 0 until BLOCK) {
            var v = 0.0
            if (a.active) v += a.buffer[s] * a.level * a.ramp.step()
            if (b.active) v += b.buffer[s] * b.level * b.ramp.step()
            val m = master.step()
            // At the end of a release m is exactly 0: the sample is +0, whatever the sign of v.
            mix[s] = if (m == 0.0) 0f else (v * m).toFloat()
            if (releasing && !master.isMoving) {
                end = s + 1
                break
            }
        }
        mixPosition = 0
        mixEnd = end
        blocks++
        if (releasing && !master.isMoving) {
            playing = false
            releasing = false
            pending = false
            for (layer in layers) layer.active = false
            return
        }
        for (layer in layers) if (layer.active && !layer.ramp.isMoving && layer.ramp.target == 0.0) layer.active = false
        if (pending && !crossfading) {
            pending = false
            if (!layers[current].plays(pendingRootHz, pendingSound, pendingVoicing, pendingLower)) {
                crossfadeTo(pendingRootHz, pendingSound, pendingVoicing, pendingLower)
            }
        }
    }

    companion object {
        /** Samples per block: levels are set once a block and ramped across it (1.3 ms at 48 kHz). */
        const val BLOCK = 64

        /** From silence to full level at [play]. */
        const val FADE_IN_SECONDS = 0.1f

        /** From one drone to the next when something changes. */
        const val CROSSFADE_SECONDS = 0.4f

        /** The default fade-out of [release]: quick, but no click. */
        const val STOP_FADE_SECONDS = 0.25f

        /** No sample of any sound, voicing, root or seed passes this (the clicks' limit too). */
        const val PEAK_LIMIT = 0.8f

        const val MIN_ROOT_HZ = 40f
        const val MAX_ROOT_HZ = 1_000f
        const val MIN_SAMPLE_RATE = 8_000

        const val DEFAULT_SEED = 1L

        /** The drone's octave: a root goes from G♯2 (MIDI 44) up to G3 (55), D3 in its middle. */
        const val LOWEST_ROOT_MIDI = 44

        /** [pitchClass] in the drone's octave, G♯2 to G3; turn it into Hz with the player's `NoteMapper`. */
        fun rootNote(pitchClass: PitchClass): Note =
            Note(LOWEST_ROOT_MIDI + (pitchClass.ordinal - LOWEST_ROOT_MIDI).mod(Note.SEMITONES_PER_OCTAVE))

        /**
         * Each sound's gain per voicing (ROOT, FIFTH, FIFTH_OCTAVE): all twelve about equally loud
         * (K-weighted, measured at D3 by `DroneSynthReport`), the loudest the peaks allow under
         * [PEAK_LIMIT] with a margin, at every root of the octave, lower or not, any seed.
         */
        internal val LEVELS = arrayOf(
            doubleArrayOf(0.0373, 0.0367, 0.0388), // TANPURA
            doubleArrayOf(0.0217, 0.0167, 0.0144), // HARMONIUM
            doubleArrayOf(0.1064, 0.0866, 0.0794), // FLUTE_ORGAN
            doubleArrayOf(0.0722, 0.0590, 0.0548), // WARM_PAD
        )
    }
}
