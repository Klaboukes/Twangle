package io.github.klaboukes.twangle.core.testing

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Writes [samples] (mono, −1..1) as a 16-bit PCM WAV file at [sampleRate], for [PluckRecorder].
 *
 * The layout is the plain one (RIFF, `fmt `, `data` at byte 36), so the simplest reader finds the
 * samples, followed by a `LIST`/`INFO` chunk whose `ICMT` holds [comment] (the phone, the input and
 * the rate), which readers skip. Samples are clamped to −1..1 and rounded to ±32767.
 */
internal fun writeWav(file: File, samples: FloatArray, sampleRate: Int, comment: String) {
    val dataBytes = samples.size * 2
    // INFO strings end in a 0 byte, and every chunk is padded to an even length.
    val text = comment.toByteArray(Charsets.US_ASCII) + 0.toByte()
    val textBytes = if (text.size % 2 == 0) text else text + 0.toByte()
    val listBytes = 4 + 8 + textBytes.size // "INFO" + the ICMT chunk
    val buffer = ByteBuffer.allocate(44 + dataBytes + 8 + listBytes).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".toByteArray()).putInt(buffer.capacity() - 8).put("WAVE".toByteArray())
    buffer.put("fmt ".toByteArray()).putInt(16)
        .putShort(1) // PCM
        .putShort(1) // mono
        .putInt(sampleRate)
        .putInt(sampleRate * 2) // bytes a second
        .putShort(2) // bytes a frame
        .putShort(16) // bits a sample
    buffer.put("data".toByteArray()).putInt(dataBytes)
    for (sample in samples) {
        buffer.putShort((sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort())
    }
    buffer.put("LIST".toByteArray()).putInt(listBytes).put("INFO".toByteArray())
    buffer.put("ICMT".toByteArray()).putInt(textBytes.size).put(textBytes)
    // Written whole under another name first, so a run stopped halfway never leaves half a file.
    val partial = File(file.parentFile, file.name + ".part")
    partial.writeBytes(buffer.array())
    check(partial.renameTo(file)) { "Could not rename $partial to $file" }
}
