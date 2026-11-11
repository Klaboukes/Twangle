package io.github.klaboukes.twangle.app

import io.github.klaboukes.twangle.core.audio.AudioConfig
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.audioThreadDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * A stand-in for the microphone, used only by the `benchmarkRelease` build (see
 * `BuildConfig.SYNTHETIC_AUDIO` in [AppContainer]) so frame-time measurements get the same input on
 * every run: a tone around A4 that sweeps slowly across about ±30 cents ([SweepTone]), with a
 * little noise, delivered in real time like `AudioRecord` would. Release builds never select it,
 * and R8 removes it from them.
 *
 * Chunks come from a pool allocated once per collection, as in `AndroidAudioRecorder`, so it
 * creates no garbage per chunk, on a thread of its own at audio priority like that one's reads, so
 * the frame times measured include what that thread takes from the screen.
 */
class SyntheticAudioRecorder(
    override val config: AudioConfig = AudioConfig(),
) : AudioRecorder {

    private val dispatcher = audioThreadDispatcher("AudioRecorder")

    override fun chunks(): Flow<FloatArray> = flow {
        val tone = SweepTone(config.sampleRate)
        val pool = Array(POOL_SIZE) { FloatArray(config.chunkSize) }
        val chunkNanos = config.chunkSize * 1_000_000_000L / config.sampleRate
        val start = System.nanoTime()
        var next = 0
        var sent = 0L
        while (true) {
            val chunk = pool[next]
            next = (next + 1) % POOL_SIZE
            tone.fill(chunk)
            // Like a blocking AudioRecord.read(): a chunk is ready once it has been "recorded".
            sent++
            val waitNanos = start + sent * chunkNanos - System.nanoTime()
            if (waitNanos > 0) delay(waitNanos / 1_000_000L)
            emit(chunk)
        }
    }
        // As in AndroidAudioRecorder: the pool is never overwritten while a chunk is still in use.
        .buffer(CHANNEL_CAPACITY)
        .flowOn(dispatcher)

    private companion object {
        const val CHANNEL_CAPACITY = 2
        const val POOL_SIZE = CHANNEL_CAPACITY + 2
    }
}

/**
 * The benchmark's test signal: a sine around [baseHz] whose pitch follows [centsAt], plus white
 * noise 20 dB below the tone. Phase is carried across chunks, so the tone is continuous. Not
 * thread-safe; allocation-free.
 */
class SweepTone(
    private val sampleRate: Int,
    private val baseHz: Double = A4_HZ,
    private val amplitude: Float = AMPLITUDE,
) {
    private var sample = 0L
    private var phase = 0.0
    private var noiseState = 0x2545F491

    // Uniform noise in ±a has an RMS of a/√3; the tone's RMS is amplitude/√2.
    private val noiseAmplitude = amplitude / Math.sqrt(2.0).toFloat() * NOISE_RATIO * Math.sqrt(3.0).toFloat()

    /** Fills [chunk] with the next samples. */
    fun fill(chunk: FloatArray) {
        for (i in chunk.indices) {
            val seconds = sample.toDouble() / sampleRate
            val hz = baseHz * 2.0.pow(centsAt(seconds) / 1200.0)
            phase += 2.0 * PI * hz / sampleRate
            if (phase > 2.0 * PI) phase -= 2.0 * PI
            chunk[i] = amplitude * sin(phase).toFloat() + noiseAmplitude * nextNoise()
            sample++
        }
    }

    /** Uniform noise in −1..1 (xorshift32, Marsaglia 2003). */
    private fun nextNoise(): Float {
        var x = noiseState
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        noiseState = x
        return x / Int.MAX_VALUE.toFloat()
    }

    companion object {
        const val A4_HZ = 440.0

        /** About −26 dBFS at its peak, a little louder than a guitar a hand's width from the phone. */
        const val AMPLITUDE = 0.05f

        /** Noise RMS relative to the tone's: −20 dB. */
        const val NOISE_RATIO = 0.1f

        /** One slow sweep from flat to sharp and back, in seconds. */
        const val SWEEP_SECONDS = 8.0

        /** A small wobble on top, so the meter never stands still. */
        const val WOBBLE_SECONDS = 1.3
        const val SWEEP_CENTS = 30.0
        const val WOBBLE_CENTS = 2.0

        /**
         * How far the tone is from [A4_HZ] after [seconds], in cents: 30·sin³ of a slow 8 s sweep
         * plus a 2-cent wobble. The cube lingers near 0, so twice per sweep the note stays within
         * the tuner's ±5 cents for about a second (the in-tune glow lights and fades again), and
         * between those it swings out to about ±30 cents.
         */
        fun centsAt(seconds: Double): Double {
            val sweep = sin(2.0 * PI * seconds / SWEEP_SECONDS)
            return SWEEP_CENTS * sweep * sweep * sweep + WOBBLE_CENTS * sin(2.0 * PI * seconds / WOBBLE_SECONDS)
        }
    }
}
