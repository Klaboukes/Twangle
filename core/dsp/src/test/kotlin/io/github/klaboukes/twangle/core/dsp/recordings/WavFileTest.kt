package io.github.klaboukes.twangle.core.dsp.recordings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** [WavFile] on files built here, as `PluckRecorder` lays them out. */
class WavFileTest {

    @Test
    fun `reads the samples, the rate and the comment, fmt and data first`() {
        val wav = WavFile.read(wav(rate = 48_000, samples = shortArrayOf(0, 16384, -16384, 32767, -32768), comment = "Xiaomi; 48000 Hz"))
        assertEquals(48_000, wav.sampleRate)
        assertEquals(listOf(0f, 0.5f, -0.5f, 32767 / 32768f, -1f), wav.samples.toList())
        assertEquals("Xiaomi; 48000 Hz", wav.comment)
        assertEquals(5 / 48_000f, wav.seconds, 1e-7f)
    }

    @Test
    fun `an odd-sized comment is padded, and a file without one has an empty comment`() {
        assertEquals("abc", WavFile.read(wav(44_100, shortArrayOf(1, 2, 3), comment = "abc")).comment)
        val plain = WavFile.read(wav(44_100, shortArrayOf(1, 2, 3), comment = null))
        assertEquals("", plain.comment)
        assertEquals(3, plain.samples.size)
    }

    @Test
    fun `only 16-bit PCM mono is read`() {
        assertThrows(IllegalArgumentException::class.java) { WavFile.read(wav(48_000, shortArrayOf(1), comment = null, channels = 2)) }
        assertThrows(IllegalArgumentException::class.java) { WavFile.read(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)) }
    }

    // RIFF/WAVE: fmt, data, then a LIST/INFO chunk with an ICMT comment (null-terminated), as the recorder writes.
    private fun wav(rate: Int, samples: ShortArray, comment: String?, channels: Int = 1): ByteArray {
        val body = ByteArrayOutputStream()
        fun chunk(id: String, data: ByteArray) {
            body.write(id.toByteArray(Charsets.US_ASCII))
            body.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size).array())
            body.write(data)
            if (data.size % 2 == 1) body.write(0)
        }
        val fmt = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(1).putShort(channels.toShort()).putInt(rate).putInt(rate * 2 * channels).putShort((2 * channels).toShort()).putShort(16).array()
        chunk("fmt ", fmt)
        val data = ByteBuffer.allocate(2 * samples.size).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort(s)
        chunk("data", data.array())
        if (comment != null) {
            val text = comment.toByteArray(Charsets.US_ASCII) + 0
            val info = ByteArrayOutputStream()
            info.write("INFO".toByteArray(Charsets.US_ASCII))
            info.write("ICMT".toByteArray(Charsets.US_ASCII))
            info.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(text.size).array())
            info.write(text)
            if (text.size % 2 == 1) info.write(0)
            chunk("LIST", info.toByteArray())
        }
        val chunks = body.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(4 + chunks.size).array())
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write(chunks)
        return out.toByteArray()
    }
}
