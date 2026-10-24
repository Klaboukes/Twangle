package io.github.klaboukes.twangle.core.dsp.synth

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Prints what the app's own detector reads from every instrument's reference tone, and writes each
 * open string of every instrument to `build/reference-tones/` as a WAV file to listen to. Not a
 * test: run with the benchmarks (`./gradlew :core:dsp:benchmark --tests '*StringSynthReport*'`).
 */
class StringSynthReport {

    @Test
    fun printReadingsAndWriteTones() {
        val dir = File("build/reference-tones").apply { mkdirs() }
        val mapper = NoteMapper(NoteMapper.DEFAULT_A4_HZ)
        println("instrument  note     Hz   YIN 0.3s  0.5s   1s    3s  (cents)   peak  first20ms  noise  strings  rms0.1s rms0.5s  rms2s  rms3s")
        for (instrument in Instruments.all) {
            val synth = StringSynth(RATE, instrument.voice)
            for (note in instrument.defaultTuning.strings) {
                val f = mapper.frequencyOf(note)
                val tone = renderAll(synth, f)
                val readings = listOf(0.3f, 0.5f, 1f, 3f).map { centsAt(tone, f, it) }
                val peak = tone.maxOf { abs(it) }
                val first = tone.copyOf(RATE * 20 / 1000).maxOf { abs(it) }
                println(
                    "%-11s %-4s %7.2f   %6.2f %6.2f %6.2f %6.2f        %5.3f  %5.3f    %5.3f  %5.3f   %6.1f  %6.1f  %6.1f %6.1f".format(
                        instrument.id, note.toString(), f, readings[0], readings[1], readings[2], readings[3], peak, first,
                        synth.noisePeak, synth.harmonicPeak, rmsDb(tone, 0.1f), rmsDb(tone, 0.5f), rmsDb(tone, 2f), rmsDb(tone, 3f),
                    ),
                )
                writeWav(File(dir, "${instrument.id}-$note.wav"), tone)
            }
        }
        println("WAV files in ${dir.absolutePath}")
    }

    private fun centsAt(tone: FloatArray, frequencyHz: Float, seconds: Float): Double {
        val settings = AnalysisSettings.forSampleRate(RATE, PitchRange.forLowestNote(frequencyHz))
        val from = (seconds * RATE).roundToInt()
        val result = settings.newPitchDetector().detect(tone.copyOfRange(from, from + settings.windowSize))
        if (!result.isPitched) return Double.NaN
        return 1200 * log2(result.frequencyHz / frequencyHz.toDouble())
    }

    private fun renderAll(synth: StringSynth, frequencyHz: Float): FloatArray {
        synth.start(frequencyHz)
        val out = FloatArray(((synth.durationSeconds + 1) * synth.sampleRate).toInt())
        val n = synth.render(out)
        return out.copyOf(n)
    }

    private fun rmsDb(x: FloatArray, fromSeconds: Float, seconds: Float = 0.1f): Double {
        val from = (fromSeconds * RATE).roundToInt()
        val to = minOf(x.size, from + (seconds * RATE).roundToInt())
        var sum = 0.0
        for (i in from until to) sum += x[i].toDouble() * x[i]
        return 20 * log10(sqrt(sum / (to - from)) + 1e-12)
    }

    private fun writeWav(file: File, samples: FloatArray) {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
        DataOutputStream(file.outputStream().buffered()).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt(36 + data.capacity()).put("WAVE".toByteArray())
            header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
            header.put("data".toByteArray()).putInt(data.capacity())
            out.write(header.array())
            out.write(data.array())
        }
    }

    private companion object {
        const val RATE = 48_000
    }
}
