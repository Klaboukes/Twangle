package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.dsp.pitch.YinPitchDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.management.ManagementFactory
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * [Pcm16Reads]: a phone that refuses float recording records in 16-bit, and the samples reach the
 * pipelines as the same floats in −1..1, with nothing allocated per chunk.
 */
class Pcm16ReadsTest {

    /** A 16-bit microphone playing [samples] (as floats, turned into 16-bit as a phone would). */
    private class Fake16BitSource(private val samples: (Long) -> Float) : ShortReads {
        var position = 0L
        var largestAsk = 0

        override fun read(buffer: ShortArray, offset: Int, count: Int): Int {
            largestAsk = maxOf(largestAsk, count)
            for (i in 0 until count) {
                buffer[offset + i] = (samples(position++) * 32_768f).roundToInt().coerceIn(-32_768, 32_767).toShort()
            }
            return count
        }
    }

    /** The [MicInput] a 16-bit recording is, as [AndroidAudioRecorder]'s `RecordInput` builds it. */
    private class Fake16BitInput(val source: ShortReads, capacity: Int) : MicInput {
        private val pcm16 = Pcm16Reads(capacity)
        override val isSilenced = false
        override fun read(buffer: FloatArray, offset: Int, count: Int): Int = pcm16.read(buffer, offset, count, source)
    }

    private val chunk = FloatArray(AudioConfig.forSampleRate(RATE).chunkSize)

    @Test
    fun `full scale becomes -1 to 1, and zero stays exactly zero`() {
        val src = shortArrayOf(Short.MIN_VALUE, -16_384, -1, 0, 1, 16_384, Short.MAX_VALUE)
        val dst = FloatArray(src.size)
        Pcm16Reads.toFloats(src, 0, dst, 0, src.size)

        assertEquals(-1f, dst[0], 0f)
        assertEquals(-0.5f, dst[1], 0f)
        assertEquals(-1f / 32_768f, dst[2], 0f)
        assertEquals(0f, dst[3], 0f)
        assertEquals(0.5f, dst[5], 0f)
        assertEquals(32_767f / 32_768f, dst[6], 0f)
    }

    @Test
    fun `every 16-bit value lands in -1 to 1, in order`() {
        val src = ShortArray(65_536) { (it - 32_768).toShort() }
        val dst = FloatArray(src.size)
        Pcm16Reads.toFloats(src, 0, dst, 0, src.size)

        for (i in dst.indices) {
            assertTrue("sample $i was ${dst[i]}", dst[i] >= -1f && dst[i] < 1f)
            if (i > 0) assertTrue("sample $i out of order", dst[i] > dst[i - 1])
        }
    }

    @Test
    fun `samples are written where the caller asks and nowhere else`() {
        val reads = Pcm16Reads(capacity = 8)
        val buffer = FloatArray(10) { 9f }
        val n = reads.read(buffer, 3, 4) { shorts, offset, count ->
            for (i in 0 until count) shorts[offset + i] = 16_384
            count
        }

        assertEquals(4, n)
        assertEquals(listOf(9f, 9f, 9f, 0.5f, 0.5f, 0.5f, 0.5f, 9f, 9f, 9f), buffer.toList())
    }

    @Test
    fun `a read asks for at most the capacity, and the caller's loop gets the rest`() {
        val source = Fake16BitSource { 0.25f }
        val input = Fake16BitInput(source, capacity = 100)
        ChunkReader(input, RATE, pause = {}).fill(chunk)

        assertEquals(100, source.largestAsk)
        assertTrue(chunk.all { it == 0.25f })
    }

    @Test
    fun `errors and empty reads pass through unchanged`() {
        val reads = Pcm16Reads(capacity = 8)
        val buffer = FloatArray(8) { 9f }

        assertEquals(-6, reads.read(buffer, 0, 8) { _, _, _ -> -6 })
        assertEquals(0, reads.read(buffer, 0, 8) { _, _, _ -> 0 })
        assertTrue("nothing written", buffer.all { it == 9f })
    }

    @Test
    fun `reading allocates nothing per chunk, into the caller's own array`() {
        // A second of A2 in 16 bits, played round and round (a fake that allocates nothing itself).
        val second = ShortArray(RATE) { (A2(it.toLong()) * 32_768f).roundToInt().toShort() }
        var position = 0
        val source = ShortReads { buffer, offset, count ->
            for (i in 0 until count) {
                buffer[offset + i] = second[position]
                position = (position + 1) % second.size
            }
            count
        }
        val reader = ChunkReader(Fake16BitInput(source, chunk.size), RATE, pause = {})
        val before = chunk
        repeat(200) { reader.fill(chunk) } // Warm up: class loading and JIT don't count.

        val bytes = allocatedBytes { repeat(100) { reader.fill(chunk) } }

        assertEquals("bytes allocated for 100 chunks", 0L, bytes)
        assertTrue(before === chunk)
    }

    @Test
    fun `a 16-bit microphone of only zeros is busy after 2 s, silent from its start`() {
        val reader = ChunkReader(Fake16BitInput(Fake16BitSource { 0f }, chunk.size), RATE, pause = {})
        val chunksIn2s = Math.ceil(RATE * ChunkReader.SILENT_SECONDS / chunk.size.toDouble()).toInt()

        repeat(chunksIn2s - 1) { reader.fill(chunk) }
        try {
            reader.fill(chunk)
            fail("expected MicrophoneBusy")
        } catch (e: AudioRecorderException.MicrophoneBusy) {
            assertTrue(reader.silentFromStart)
        }
    }

    @Test
    fun `a quiet room one step from zero in 16 bits is heard`() {
        // ±1 step (−90 dBFS), quieter than any phone's room noise: still not exact zeros.
        val reader = ChunkReader(Fake16BitInput(Fake16BitSource { i -> if (i % 2 == 0L) 1f / 32_768f else 0f }, chunk.size), RATE, pause = {})
        val chunksIn3s = (RATE * 3) / chunk.size
        repeat(chunksIn3s) { reader.fill(chunk) }
        assertTrue(reader.heardRoom)
    }

    @Test
    fun `in 16 bits a silent input gives way to the next one, which reads an A2`() {
        val opened = mutableListOf<AudioInput>()
        fun open(input: AudioInput): Fake16BitInput {
            opened += input
            val sound = if (input == AudioInput.UNPROCESSED) ({ _: Long -> 0f }) else A2
            return Fake16BitInput(Fake16BitSource(sound), chunk.size)
        }
        val both = listOf(AudioInput.UNPROCESSED, AudioInput.VOICE_RECOGNITION)
        val skipped = SkippedInputs()
        val reader = FallbackReader(
            first = open(AudioInput.UNPROCESSED),
            firstInput = AudioInput.UNPROCESSED,
            inputs = both,
            sampleRate = RATE,
            skipped = skipped,
            open = ::open,
            release = {},
            pause = {},
        )
        val chunksIn2s = Math.ceil(RATE * ChunkReader.SILENT_SECONDS / chunk.size.toDouble()).toInt()
        repeat(chunksIn2s) { reader.fill(chunk) }
        assertEquals(AudioInput.VOICE_RECOGNITION, reader.input)

        val detector = YinPitchDetector(sampleRate = RATE)
        val frame = FloatArray(detector.windowSize)
        var filled = 0
        while (filled < frame.size) {
            reader.fill(chunk)
            val n = minOf(chunk.size, frame.size - filled)
            chunk.copyInto(frame, filled, 0, n)
            filled += n
        }
        assertEquals(110f, detector.detect(frame).frequencyHz, 0.1f)
        assertTrue(AudioInput.UNPROCESSED in skipped)
        assertEquals(both, opened)
    }

    private companion object {
        const val RATE = 48_000

        /** A2, 110 Hz, with a little of its second harmonic. */
        val A2: (Long) -> Float = { i ->
            val t = 2 * PI * 110.0 * i / RATE
            (0.3 * sin(t) + 0.1 * sin(2 * t)).toFloat()
        }

        /** Bytes the current thread allocated while running [block], the least of 3 runs (as `:core:dsp`'s tests count). */
        inline fun allocatedBytes(block: () -> Unit): Long {
            val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
            bean.currentThreadAllocatedBytes
            var least = Long.MAX_VALUE
            repeat(3) {
                val before = bean.currentThreadAllocatedBytes
                block()
                least = minOf(least, bean.currentThreadAllocatedBytes - before)
            }
            return least
        }
    }
}
