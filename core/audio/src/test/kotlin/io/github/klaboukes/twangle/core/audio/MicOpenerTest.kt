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
}
