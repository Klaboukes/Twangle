package io.github.klaboukes.twangle.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [openFirst]: which rate and input the microphone opens with when some refuse. */
class MicOpenerTest {

    private val both = listOf(AudioInput.UNPROCESSED, AudioInput.VOICE_RECOGNITION)
    private val rates = listOf(48_000, 44_100, 16_000)

    private fun open(works: (Int, AudioInput) -> Boolean): Pair<OpenedMic<String>?, List<Pair<Int, AudioInput>>> {
        val tried = mutableListOf<Pair<Int, AudioInput>>()
        val opened = openFirst(rates, both) { rate, input ->
            tried += rate to input
            if (works(rate, input)) "$input@$rate" else null
        }
        return opened to tried
    }

    @Test
    fun `the best input at the first rate wins when it works`() {
        val (opened, tried) = open { _, _ -> true }
        assertEquals(OpenedMic("UNPROCESSED@48000", 48_000, AudioInput.UNPROCESSED), opened)
        assertEquals(1, tried.size)
    }

    @Test
    fun `the fallback input is tried at the same rate first`() {
        val (opened, _) = open { _, input -> input == AudioInput.VOICE_RECOGNITION }
        assertEquals(48_000, opened!!.sampleRate)
        assertEquals(AudioInput.VOICE_RECOGNITION, opened.input)
    }

    @Test
    fun `when the first rate fails the next rate is tried`() {
        val (opened, tried) = open { rate, _ -> rate == 44_100 }
        assertEquals(OpenedMic("UNPROCESSED@44100", 44_100, AudioInput.UNPROCESSED), opened)
        assertEquals(
            listOf(48_000 to AudioInput.UNPROCESSED, 48_000 to AudioInput.VOICE_RECOGNITION, 44_100 to AudioInput.UNPROCESSED),
            tried,
        )
    }

    @Test
    fun `a last-resort rate still opens`() {
        val (opened, tried) = open { rate, input -> rate == 16_000 && input == AudioInput.VOICE_RECOGNITION }
        assertEquals(16_000, opened!!.sampleRate)
        assertEquals(6, tried.size)
    }

    @Test
    fun `null when nothing opens, after trying everything`() {
        val (opened, tried) = open { _, _ -> false }
        assertNull(opened)
        assertEquals(6, tried.size)
    }

    @Test(expected = AudioRecorderException.PermissionMissing::class)
    fun `a failure thrown while opening ends the search`() {
        openFirst<String>(rates, both) { _, _ -> throw AudioRecorderException.PermissionMissing() }
    }

    private val floatRates = listOf(48_000, 44_100)
    private val pcm16Rates = listOf(48_000, 44_100, 16_000)
    private val bothFormats = listOf(SampleFormat.FLOAT, SampleFormat.PCM_16BIT)

    private fun openIn(works: (SampleFormat, Int, AudioInput) -> Boolean): Pair<OpenedMic<String>?, List<Triple<SampleFormat, Int, AudioInput>>> {
        val tried = mutableListOf<Triple<SampleFormat, Int, AudioInput>>()
        val opened = openFirst(bothFormats, { if (it == SampleFormat.FLOAT) floatRates else pcm16Rates }, both) { format, rate, input ->
            tried += Triple(format, rate, input)
            if (works(format, rate, input)) "$input@$rate/$format" else null
        }
        return opened to tried
    }

    @Test
    fun `float recording is kept where both formats open`() {
        val (opened, tried) = openIn { _, _, _ -> true }
        assertEquals(OpenedMic("UNPROCESSED@48000/FLOAT", 48_000, AudioInput.UNPROCESSED, SampleFormat.FLOAT), opened)
        assertEquals(1, tried.size)
    }

    @Test
    fun `float at every rate and input is tried before any 16-bit`() {
        val (opened, tried) = openIn { format, _, _ -> format == SampleFormat.PCM_16BIT }
        assertEquals(OpenedMic("UNPROCESSED@48000/PCM_16BIT", 48_000, AudioInput.UNPROCESSED, SampleFormat.PCM_16BIT), opened)
        assertEquals(floatRates.size * both.size + 1, tried.size)
        assertEquals(List(floatRates.size * both.size) { SampleFormat.FLOAT }, tried.dropLast(1).map { it.first })
    }

    @Test
    fun `a slower rate in float wins over the native rate in 16-bit`() {
        val (opened, _) = openIn { format, rate, _ -> format == SampleFormat.PCM_16BIT || rate == 44_100 }
        assertEquals(44_100, opened!!.sampleRate)
        assertEquals(SampleFormat.FLOAT, opened.format)
    }

    @Test
    fun `each format searches its own rates`() {
        val (opened, tried) = openIn { format, rate, input ->
            format == SampleFormat.PCM_16BIT && rate == 16_000 && input == AudioInput.VOICE_RECOGNITION
        }
        assertEquals(OpenedMic("VOICE_RECOGNITION@16000/PCM_16BIT", 16_000, AudioInput.VOICE_RECOGNITION, SampleFormat.PCM_16BIT), opened)
        assertEquals((floatRates.size + pcm16Rates.size) * both.size, tried.size)
    }

    @Test
    fun `null when nothing opens in any format`() {
        val (opened, tried) = openIn { _, _, _ -> false }
        assertNull(opened)
        assertEquals((floatRates.size + pcm16Rates.size) * both.size, tried.size)
    }
}
