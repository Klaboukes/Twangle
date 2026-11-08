package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.dsp.pitch.YinPitchDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * [FallbackReader] with fake phones: an input that records only zeros gives way to the next one
 * within the session, and is skipped for good only once that one hears the room.
 */
class FallbackReaderTest {

    /** One input of a fake phone, playing [samples] from its first sample on. */
    private class FakeInput(val input: AudioInput, private val samples: (Long) -> Float) : MicInput {
        override val isSilenced = false
        var released = false
        private var position = 0L

        override fun read(buffer: FloatArray, offset: Int, count: Int): Int {
            check(!released) { "$input read after it was released" }
            for (i in 0 until count) buffer[offset + i] = samples(position++)
            return count
        }
    }

    /**
     * A fake phone whose inputs play what [sound] gives each; it remembers every input it opened.
     * Inputs missing from [sound] refuse to open.
     */
    private class FakePhone(private val sound: Map<AudioInput, (Long) -> Float>) {
        val opened = mutableListOf<FakeInput>()

        fun open(input: AudioInput): FakeInput? = sound[input]?.let { FakeInput(input, it) }?.also { opened += it }
    }

    private val both = listOf(AudioInput.UNPROCESSED, AudioInput.VOICE_RECOGNITION)
    private val skipped = SkippedInputs()

    /** Starts a session the way [AndroidAudioRecorder] does: the first usable input that opens. */
    private fun session(phone: FakePhone, inputs: List<AudioInput> = both): FallbackReader<FakeInput> {
        val usable = skipped.usable(inputs)
        val firstInput = usable.first { phone.open(it) != null }
        return FallbackReader(
            first = phone.opened.last(),
            firstInput = firstInput,
            inputs = usable,
            sampleRate = RATE,
            skipped = skipped,
            open = phone::open,
            release = { it.released = true },
            pause = {},
        )
    }

    private val chunk = FloatArray(AudioConfig.forSampleRate(RATE).chunkSize)
    private val chunksIn2s = Math.ceil(RATE * ChunkReader.SILENT_SECONDS / chunk.size.toDouble()).toInt()

    private inline fun busy(block: () -> Unit) {
        try {
            block()
        } catch (e: AudioRecorderException.MicrophoneBusy) {
            return
        }
        fail("expected MicrophoneBusy")
    }

    @Test
    fun `an unprocessed input of zeros gives way to the speech input, which reads notes in the same session`() {
        val phone = FakePhone(mapOf(AudioInput.UNPROCESSED to ZEROS, AudioInput.VOICE_RECOGNITION to A2))
        val reader = session(phone)

        // Two seconds of zeros from the unprocessed input, then the speech input's A2.
        repeat(chunksIn2s) { reader.fill(chunk) }
        assertEquals(AudioInput.VOICE_RECOGNITION, reader.input)
        assertTrue("the silent input is let go", phone.opened.first().released)

        val detector = YinPitchDetector(sampleRate = RATE)
        val frame = FloatArray(detector.windowSize)
        var filled = 0
        while (filled < frame.size) {
            reader.fill(chunk)
            val n = minOf(chunk.size, frame.size - filled)
            chunk.copyInto(frame, filled, 0, n)
            filled += n
        }
        val pitch = detector.detect(frame)
        assertEquals(110f, pitch.frequencyHz, 0.1f)

        // Remembered: the next session starts on the speech input straight away.
        assertTrue(AudioInput.UNPROCESSED in skipped)
        val next = FakePhone(mapOf(AudioInput.UNPROCESSED to ZEROS, AudioInput.VOICE_RECOGNITION to A2))
        assertEquals(AudioInput.VOICE_RECOGNITION, session(next).input)
        assertEquals(listOf(AudioInput.VOICE_RECOGNITION), next.opened.map { it.input })
    }

    @Test
    fun `a muted microphone still says busy, and the unprocessed input is tried again next time`() {
        val phone = FakePhone(mapOf(AudioInput.UNPROCESSED to ZEROS, AudioInput.VOICE_RECOGNITION to ZEROS))
        val reader = session(phone)

        // Two seconds on each input, then busy as before.
        repeat(2 * chunksIn2s - 2) { reader.fill(chunk) }
        busy { reader.fill(chunk) }
        assertEquals(listOf(AudioInput.UNPROCESSED, AudioInput.VOICE_RECOGNITION), phone.opened.map { it.input })
        assertTrue(phone.opened.all { it.released })

        assertFalse(AudioInput.UNPROCESSED in skipped)
        val next = FakePhone(mapOf(AudioInput.UNPROCESSED to ROOM, AudioInput.VOICE_RECOGNITION to ROOM))
        assertEquals(AudioInput.UNPROCESSED, session(next).input)
    }

    @Test
    fun `the skip is remembered only once the next input hears the room`() {
        // The speech input starts with a second of silence before it hears anything.
        val phone = FakePhone(
            mapOf(AudioInput.UNPROCESSED to ZEROS, AudioInput.VOICE_RECOGNITION to { i -> if (i < RATE) 0f else ROOM(i) }),
        )
        val reader = session(phone)
        repeat(chunksIn2s) { reader.fill(chunk) }
        assertEquals(AudioInput.VOICE_RECOGNITION, reader.input)
        assertFalse(AudioInput.UNPROCESSED in skipped)

        repeat(RATE / chunk.size + 2) { reader.fill(chunk) }
        assertTrue(AudioInput.UNPROCESSED in skipped)
    }

    @Test
    fun `an input that heard the room and then went silent is busy, with no switch`() {
        var muted = false
        val phone = FakePhone(
            mapOf(AudioInput.UNPROCESSED to { i -> if (muted) 0f else ROOM(i) }, AudioInput.VOICE_RECOGNITION to ROOM),
        )
        val reader = session(phone)
        repeat(50) { reader.fill(chunk) }

        muted = true
        repeat(chunksIn2s - 1) { reader.fill(chunk) }
        busy { reader.fill(chunk) }
        assertEquals(listOf(AudioInput.UNPROCESSED), phone.opened.map { it.input })
        assertFalse(AudioInput.UNPROCESSED in skipped)
    }

    @Test
    fun `Android silencing the recording for another app is busy, with no switch`() {
        val phone = FakePhone(mapOf(AudioInput.UNPROCESSED to ZEROS, AudioInput.VOICE_RECOGNITION to ROOM))
        val silenced = object : MicInput {
            override val isSilenced = true
            override fun read(buffer: FloatArray, offset: Int, count: Int): Int {
                buffer.fill(0f, offset, offset + count)
                return count
            }
        }
        val reader = FallbackReader<MicInput>(silenced, AudioInput.UNPROCESSED, both, RATE, skipped, phone::open, {}, pause = {})
        busy { reader.fill(chunk) }
        assertTrue(phone.opened.isEmpty())
    }

    @Test
    fun `with no input left to try, a silent one is busy as before`() {
        val phone = FakePhone(mapOf(AudioInput.VOICE_RECOGNITION to ZEROS))
        val reader = session(phone, listOf(AudioInput.VOICE_RECOGNITION))
        repeat(chunksIn2s - 1) { reader.fill(chunk) }
        busy { reader.fill(chunk) }
        assertTrue(phone.opened.single().released)
    }

    @Test
    fun `when the next input won't open, the silent one is busy`() {
        val phone = FakePhone(mapOf(AudioInput.UNPROCESSED to ZEROS))
        val reader = session(phone)
        repeat(chunksIn2s - 1) { reader.fill(chunk) }
        busy { reader.fill(chunk) }
        assertFalse(AudioInput.UNPROCESSED in skipped)
    }

    @Test
    fun `close lets go of the input recording now`() {
        val phone = FakePhone(mapOf(AudioInput.UNPROCESSED to ZEROS, AudioInput.VOICE_RECOGNITION to ROOM))
        val reader = session(phone)
        repeat(chunksIn2s + 3) { reader.fill(chunk) }
        reader.close()
        assertTrue(phone.opened.all { it.released })
    }

    @Test
    fun `skipped inputs never leave nothing to try`() {
        skipped.skip(AudioInput.UNPROCESSED)
        assertEquals(listOf(AudioInput.VOICE_RECOGNITION), skipped.usable(both))
        skipped.skip(AudioInput.VOICE_RECOGNITION)
        assertEquals(both, skipped.usable(both))
    }

    private companion object {
        const val RATE = 48_000
        val ZEROS: (Long) -> Float = { 0f }

        /** A quiet room's noise floor, as a faint wobble. */
        val ROOM: (Long) -> Float = { i -> 1e-4f * sin(i * 0.05).toFloat() }

        /** A2, 110 Hz, with a little of its second harmonic. */
        val A2: (Long) -> Float = { i ->
            val t = 2 * PI * 110.0 * i / RATE
            (0.3 * sin(t) + 0.1 * sin(2 * t)).toFloat()
        }
    }
}
