package io.github.klaboukes.twangle.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.sin

/** [ChunkReader] with a fake microphone: the ways a mic stops really listening mid-session. */
class ChunkReaderTest {

    /**
     * A microphone that plays [samples] (a quiet sine by default, like a room's noise) and does
     * what each read's entry in [script] says: a number ≥ 0 caps how much that read delivers, a
     * negative one is returned as an error code. After the script, reads deliver what is asked.
     */
    private class FakeMic(
        private val script: IntArray = IntArray(0),
        private val samples: (Long) -> Float = { i -> 1e-3f * sin(i * 0.05).toFloat() },
    ) : MicInput {
        override var isSilenced = false
        var reads = 0
            private set
        private var position = 0L

        override fun read(buffer: FloatArray, offset: Int, count: Int): Int {
            val rule = script.getOrNull(reads++)
            if (rule != null && rule < 0) return rule
            val n = if (rule == null) count else minOf(rule, count)
            for (i in 0 until n) buffer[offset + i] = samples(position++)
            return n
        }
    }

    private var pauses = 0
    private fun reader(mic: MicInput, rate: Int = RATE) = ChunkReader(mic, rate) { pauses++ }

    private inline fun <reified T : Throwable> failure(block: () -> Unit): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e
            throw e
        }
        fail("expected ${T::class.simpleName}")
        error("unreachable")
    }

    @Test
    fun `short reads are joined into one full chunk`() {
        val mic = FakeMic(script = intArrayOf(100, 0, 300, 1000))
        val chunk = FloatArray(1024)

        reader(mic).fill(chunk)

        val expected = FloatArray(1024) { 1e-3f * sin(it * 0.05).toFloat() }
        assertArrayEquals(expected, chunk, 0f)
        assertEquals(1, pauses)
    }

    @Test
    fun `a negative read ends with ReadFailed and its code`() {
        val e = failure<AudioRecorderException.ReadFailed> { reader(FakeMic(intArrayOf(200, -6))).fill(FloatArray(1024)) }
        assertEquals(-6, e.errorCode)
    }

    @Test
    fun `a run of empty reads fails instead of spinning`() {
        val mic = FakeMic(IntArray(1000))
        val e = failure<AudioRecorderException.ReadFailed> { reader(mic).fill(FloatArray(1024)) }

        assertEquals(0, e.errorCode)
        assertEquals(MAX_EMPTY_STEPS, mic.reads)
        // It waited between empty reads rather than busy-looping.
        assertEquals(MAX_EMPTY_STEPS - 1, pauses)
    }

    @Test
    fun `a few empty reads in between are fine`() {
        val script = IntArray(MAX_EMPTY_STEPS - 1) + intArrayOf(10) + IntArray(MAX_EMPTY_STEPS - 1)
        reader(FakeMic(script)).fill(FloatArray(1024))
    }

    @Test
    fun `being silenced mid-session reads as a busy microphone`() {
        val mic = FakeMic()
        val reader = reader(mic)
        val chunk = FloatArray(1024)
        repeat(100) { reader.fill(chunk) }

        mic.isSilenced = true

        failure<AudioRecorderException.MicrophoneBusy> { reader.fill(chunk) }
    }

    @Test
    fun `two seconds of exact zeros reads as a busy microphone`() {
        var muted = false
        val mic = FakeMic(samples = { i -> if (muted) 0f else 1e-4f * sin(i * 0.1).toFloat() })
        val reader = reader(mic)
        val chunk = FloatArray(1024)
        repeat(50) { reader.fill(chunk) }

        muted = true
        val chunksIn2s = Math.ceil(RATE * ChunkReader.SILENT_SECONDS / 1024.0).toInt()
        repeat(chunksIn2s - 1) { reader.fill(chunk) }
        failure<AudioRecorderException.MicrophoneBusy> { reader.fill(chunk) }
    }

    @Test
    fun `zeros from the very first sample are told apart from a microphone that fell silent`() {
        val chunk = FloatArray(1024)
        val chunksIn2s = Math.ceil(RATE * ChunkReader.SILENT_SECONDS / 1024.0).toInt()

        val never = reader(FakeMic(samples = { 0f }))
        repeat(chunksIn2s - 1) { never.fill(chunk) }
        failure<AudioRecorderException.MicrophoneBusy> { never.fill(chunk) }
        assertFalse(never.heardRoom)
        assertTrue(never.silentFromStart)

        var muted = false
        val fell = reader(FakeMic(samples = { i -> if (muted) 0f else 1e-4f * sin(i * 0.1).toFloat() }))
        fell.fill(chunk)
        assertTrue(fell.heardRoom)
        muted = true
        repeat(chunksIn2s - 1) { fell.fill(chunk) }
        failure<AudioRecorderException.MicrophoneBusy> { fell.fill(chunk) }
        assertFalse(fell.silentFromStart)
    }

    @Test
    fun `a quiet room is not mistaken for a muted microphone`() {
        // The faintest noise a float microphone delivers: one 24-bit step now and then.
        val mic = FakeMic(samples = { i -> if (i % 4096 == 0L) 1.2e-7f else 0f })
        val reader = reader(mic)
        val chunk = FloatArray(1024)
        repeat(RATE * 10 / 1024) { reader.fill(chunk) }
    }

    @Test
    fun `a microphone that starts with some silence is fine once it hears the room`() {
        val warmUp = RATE.toLong()
        val mic = FakeMic(samples = { i -> if (i < warmUp) 0f else 1e-4f })
        val reader = reader(mic)
        val chunk = FloatArray(1024)
        repeat(RATE * 10 / 1024) { reader.fill(chunk) }
    }

    @Test
    fun `the silence limit follows the sample rate`() {
        for (rate in listOf(8_000, 44_100)) {
            val reader = reader(FakeMic(samples = { 0f }), rate)
            val chunk = FloatArray(AudioConfig.forSampleRate(rate).chunkSize)
            var filled = 0
            val e = runCatching { while (true) reader.fill(chunk).also { filled++ } }.exceptionOrNull()
            assertTrue("$rate Hz: $e", e is AudioRecorderException.MicrophoneBusy)
            val seconds = (filled + 1) * chunk.size / rate.toFloat()
            assertEquals("$rate Hz", ChunkReader.SILENT_SECONDS, seconds, 0.03f)
        }
    }

    private companion object {
        const val RATE = 48_000
    }
}
