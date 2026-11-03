package io.github.klaboukes.twangle.app.store

import io.github.klaboukes.twangle.core.audio.AudioConfig
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.dsp.synth.PluckSynth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * The fixed sounds of the store screenshots, made here rather than recorded, so every run (and
 * every language) hears exactly the same: a held note (in tune, or a little off), a held note with
 * a vibrato that sets in after a while, and strummed chords through the app's own pluck synthesis.
 * Nothing is played out loud: they reach the tuner through [FixedSoundRecorder].
 */
object StoreSounds {
    const val SAMPLE_RATE = AudioConfig.DEFAULT_SAMPLE_RATE

    /** The frequency of MIDI note [midi] at A4 = 440 Hz, moved by [cents]. */
    fun hz(midi: Int, cents: Double = 0.0): Double = 440.0 * 2.0.pow((midi - 69) / 12.0 + cents / 1200.0)

    /**
     * A held note at [hz] for [seconds], a string-like tone with harmonics at 1/k, fading in over
     * 20 ms. [centsAt] moves its pitch over time (seconds from its start), e.g. for a vibrato.
     */
    fun heldNote(hz: Double, seconds: Double, centsAt: (Double) -> Double = { 0.0 }): FloatArray {
        val out = FloatArray((seconds * SAMPLE_RATE).toInt())
        var phase = 0.0
        val fadeIn = FADE_IN_SECONDS * SAMPLE_RATE
        val levelSum = (1..HARMONICS).sumOf { 1.0 / it }
        for (n in out.indices) {
            val t = n.toDouble() / SAMPLE_RATE
            phase += 2.0 * PI * hz * 2.0.pow(centsAt(t) / 1200.0) / SAMPLE_RATE
            if (phase > 2.0 * PI) phase -= 2.0 * PI
            var sample = 0.0
            for (k in 1..HARMONICS) sample += sin(k * phase) / k
            out[n] = (NOTE_PEAK * sample / levelSum * minOf(1.0, n / fadeIn)).toFloat()
        }
        return out
    }

    /**
     * A held A with a gentle vibrato, as a singer or a guitarist bending lightly would play it: steady
     * for [steadySeconds], then a vibrato of ±[VIBRATO_CENTS] at [VIBRATO_HZ] that grows in over a
     * second.
     */
    fun vibratoNote(hz: Double, seconds: Double, steadySeconds: Double): FloatArray = heldNote(hz, seconds) { t ->
        val depth = ((t - steadySeconds) / VIBRATO_ONSET_SECONDS).coerceIn(0.0, 1.0)
        depth * VIBRATO_CENTS * sin(2.0 * PI * VIBRATO_HZ * (t - steadySeconds))
    }

    /**
     * A downstroke over [midi] (low to high), strings 15 ms apart, [seconds] long, through the app's own pluck synthesis,
     * each string [cents] off its note (none by default).
     */
    fun strum(midi: IntArray, seconds: Double, cents: DoubleArray = DoubleArray(midi.size)): FloatArray {
        val length = (seconds * SAMPLE_RATE).toInt()
        val out = FloatArray(length)
        val tone = FloatArray(length)
        val synth = PluckSynth(SAMPLE_RATE, durationSeconds = (seconds + 1).toFloat())
        midi.forEachIndexed { i, m ->
            val delay = (i * STRUM_SPREAD_SECONDS * SAMPLE_RATE).toInt()
            tone.fill(0f)
            synth.start(hz(m, cents[i]).toFloat())
            synth.render(tone, delay, length - delay)
            for (n in 0 until length) out[n] += tone[n]
        }
        val loudest = out.maxOf { abs(it) }
        for (n in out.indices) out[n] *= STRUM_PEAK / loudest
        return out
    }

    /** Open chord shapes in standard tuning, as MIDI notes low to high: C (x32010), G (320003), A minor (x02210). */
    val C_MAJOR = intArrayOf(48, 52, 55, 60, 64)
    val G_MAJOR = intArrayOf(43, 47, 50, 55, 59, 67)
    val A_MINOR = intArrayOf(45, 52, 57, 60, 64)

    /** The guitar's open strings in standard tuning, E2 A2 D3 G3 B3 E4, as MIDI notes low to high. */
    val OPEN_STRINGS = intArrayOf(40, 45, 50, 55, 59, 64)

    /** How far off each open string is, in cents, in the strum's picture: the A 15 flat, the G 14 sharp, the rest in tune. */
    val OPEN_STRINGS_OFF = doubleArrayOf(0.0, -15.0, 0.0, 14.0, 0.0, 0.0)

    /** MIDI notes: the low E string, the A string, and the A above middle C. */
    const val LOW_E = 40
    const val A_STRING = 45
    const val A4 = 69

    private const val HARMONICS = 8
    private const val NOTE_PEAK = 0.3
    private const val FADE_IN_SECONDS = 0.02
    private const val STRUM_PEAK = 0.4f
    private const val STRUM_SPREAD_SECONDS = 0.015
    private const val VIBRATO_CENTS = 20.0
    private const val VIBRATO_HZ = 5.0
    private const val VIBRATO_ONSET_SECONDS = 1.0
}

/**
 * A stand-in microphone for the store screenshots: it delivers whatever [play] last handed it, once,
 * in real time like `AudioRecord`, then a very faint hiss (so the tuner never takes it for a
 * silenced microphone). The hiss also plays before the first sound.
 */
class FixedSoundRecorder : AudioRecorder {
    override val config = AudioConfig()

    @Volatile private var sound = FloatArray(0)
    @Volatile private var startAt = 0

    /** Plays [samples] from the next chunk on, instead of what played before. */
    fun play(samples: FloatArray) {
        sound = samples
        startAt = -1
    }

    override fun chunks(): Flow<FloatArray> = flow {
        // A pool, as in the real recorder: a chunk is never overwritten while it still waits in the buffer.
        val pool = Array(POOL_SIZE) { FloatArray(config.chunkSize) }
        var next = 0
        val chunkNanos = config.chunkSize * 1_000_000_000L / config.sampleRate
        val start = System.nanoTime()
        var sent = 0L
        var pos = 0
        var noise = 0x2545F491
        while (true) {
            if (startAt == -1) {
                startAt = 0
                pos = 0
            }
            val samples = sound
            val chunk = pool[next]
            next = (next + 1) % POOL_SIZE
            for (i in chunk.indices) {
                // xorshift32 (Marsaglia 2003): uniform noise far below any note.
                noise = noise xor (noise shl 13)
                noise = noise xor (noise ushr 17)
                noise = noise xor (noise shl 5)
                val hiss = HISS * (noise / Int.MAX_VALUE.toFloat())
                chunk[i] = hiss + if (pos < samples.size) samples[pos++] else 0f
            }
            sent++
            val wait = start + sent * chunkNanos - System.nanoTime()
            if (wait > 0) delay(wait / 1_000_000L)
            emit(chunk)
        }
    }.buffer(CHANNEL_CAPACITY).flowOn(Dispatchers.IO.limitedParallelism(1))

    private companion object {
        const val HISS = 0.0005f
        const val CHANNEL_CAPACITY = 2
        const val POOL_SIZE = CHANNEL_CAPACITY + 2
    }
}
