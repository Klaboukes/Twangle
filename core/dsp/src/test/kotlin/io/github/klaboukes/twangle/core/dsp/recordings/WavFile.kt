package io.github.klaboukes.twangle.core.dsp.recordings

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A WAV file read whole, as `PluckRecorder` (`:core:testing`'s on-device tests) writes the real
 * recordings in `src/test/resources/fixtures/`: RIFF/WAVE with a `fmt ` chunk (16-bit PCM, mono),
 * a `data` chunk and, after them, a `LIST`/`INFO` chunk whose `ICMT` says where the file came from.
 * A few lines over the chunks, so the tests need no audio library (and nothing from Android).
 */
class WavFile(
    val sampleRate: Int,
    /** The samples in −1..1, as the pipeline gets them. */
    val samples: FloatArray,
    /** The `ICMT` comment (phone, Android version, input, rate), or "" without one. */
    val comment: String,
) {
    val seconds: Float get() = samples.size / sampleRate.toFloat()

    companion object {
        private const val PCM = 1
        private const val BITS = 16

        fun read(stream: InputStream): WavFile = stream.use { read(it.readBytes()) }

        fun read(bytes: ByteArray): WavFile {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(tag(buffer, 0) == "RIFF" && tag(buffer, 8) == "WAVE") { "not a WAV file" }
            var sampleRate = 0
            var samples: FloatArray? = null
            var comment = ""
            var at = 12
            while (at + 8 <= bytes.size) {
                val id = tag(buffer, at)
                val size = buffer.getInt(at + 4)
                val body = at + 8
                when (id) {
                    "fmt " -> {
                        val format = buffer.getShort(body).toInt()
                        val channels = buffer.getShort(body + 2).toInt()
                        val bits = buffer.getShort(body + 14).toInt()
                        require(format == PCM && channels == 1 && bits == BITS) {
                            "expected 16-bit PCM mono, got format $format, $channels channel(s), $bits bits"
                        }
                        sampleRate = buffer.getInt(body + 4)
                    }
                    "data" -> {
                        val count = minOf(size, bytes.size - body) / 2
                        samples = FloatArray(count) { buffer.getShort(body + 2 * it) / 32768f }
                    }
                    "LIST" -> if (tag(buffer, body) == "INFO") comment = infoComment(buffer, bytes, body + 4, body + size)
                }
                at = body + size + (size and 1)
            }
            require(sampleRate > 0) { "no fmt chunk" }
            return WavFile(sampleRate, requireNotNull(samples) { "no data chunk" }, comment)
        }

        // The ICMT text of an INFO list between [from] and [to] (null-terminated ASCII), or "".
        private fun infoComment(buffer: ByteBuffer, bytes: ByteArray, from: Int, to: Int): String {
            var at = from
            while (at + 8 <= to) {
                val id = tag(buffer, at)
                val size = buffer.getInt(at + 4)
                if (id == "ICMT") {
                    val text = String(bytes, at + 8, minOf(size, bytes.size - at - 8), Charsets.US_ASCII)
                    return text.trimEnd('\u0000')
                }
                at += 8 + size + (size and 1)
            }
            return ""
        }

        private fun tag(buffer: ByteBuffer, at: Int): String =
            String(ByteArray(4) { buffer.get(at + it) }, Charsets.US_ASCII)
    }
}
