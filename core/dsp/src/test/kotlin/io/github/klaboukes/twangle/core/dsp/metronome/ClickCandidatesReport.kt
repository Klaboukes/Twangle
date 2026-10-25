package io.github.klaboukes.twangle.core.dsp.metronome

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Phase 45, "Hear them first": renders today's wood block and the candidate clicks as one bar of 4/4
 * at 100 BPM each (beat 1 accented, as the app plays it), then a second of silence, into
 * `docs/design/click-sounds-preview/` as 16-bit mono 48 kHz WAV files for the owner to pick from,
 * and prints each one's peak and RMS next to the wood block's.
 *
 * Not a test, and not in the app: run it with the benchmarks
 * (`./gradlew :core:dsp:benchmark --tests '*ClickCandidatesReport*'`). The output is the same on
 * every run, so the committed files can be made again byte for byte.
 *
 * The candidates are made the way [ClickSounds] makes the wood block: a few exponentially decaying
 * partials, a raised-cosine attack and the same 8 ms raised-cosine fade ending on exactly 0 after
 * 40 ms. No recordings, so nothing to licence. Each candidate's three clicks (downbeat, group, plain)
 * get the wood block's level steps (0.8 : 0.6 : 0.45 in peak), and the whole set is scaled so a bar
 * has the wood block's RMS, with the downbeat's peak kept at or under 0.8.
 */
class ClickCandidatesReport {

    /** One decaying partial: [ratio] × the click's pitch, starting at [level], with time constant [decaySeconds]. */
    private class Partial(val ratio: Double, val level: Double, val decaySeconds: Double)

    private class Candidate(
        val file: String,
        val attackSeconds: Double,
        val partials: List<Partial>,
        /** Pitch of the downbeat, group and plain click, in Hz. */
        val pitches: Map<BeatAccent, Double>,
    )

    @Test
    fun writeCandidates() {
        val dir = File(repoRoot(), "docs/design/click-sounds-preview").apply { mkdirs() }

        // Today's wood block, played by the app's own ClickTrack.
        val woodBlock = renderBar { accent -> ClickSounds(RATE).waveform(accent) }
        assertArrayEquals("the bar matches ClickTrack's", woodBlockFromClickTrack(), woodBlock, 0f)
        val target = rms(woodBlock)
        val bars = mutableListOf("wood-block" to woodBlock)

        for (candidate in CANDIDATES) {
            val clicks = BeatAccent.entries.associateWith { render(candidate, it) }
            val raw = renderBar { clicks.getValue(it) }
            // Match the wood block's RMS, but never let the downbeat pass 0.8.
            val gain = minOf(target / rms(raw), MAX_PEAK / raw.maxOf { abs(it) }.toDouble())
            bars += candidate.file to FloatArray(raw.size) { (raw[it] * gain).toFloat() }
        }

        println("file             peak   peak dBFS   RMS dBFS   vs wood block")
        for ((name, bar) in bars) {
            val peak = bar.maxOf { abs(it) }
            val vsWood = 20 * log10(rms(bar) / target)
            println("%-15s  %5.3f   %7.1f    %7.1f     %+5.1f dB".format(name, peak, db(peak.toDouble()), db(rms(bar)), vsWood))
            assertTrue("$name clips", peak <= MAX_PEAK + 1e-6f)
            assertTrue("$name is not within 3 dB of the wood block", abs(vsWood) <= 3.0)
            writeWav(File(dir, "$name.wav"), bar)
        }
        println("WAV files in ${dir.canonicalPath}")
    }

    // One bar of 4/4 at BPM, each beat's click on its exact sample, then SILENCE_SECONDS of silence.
    private fun renderBar(click: (BeatAccent) -> FloatArray): FloatArray {
        val beatFrames = RATE * 60 / BPM // 28 800: exact at 100 BPM and 48 kHz.
        val meter = Meter.FOUR_FOUR
        val out = FloatArray(meter.beatsPerBar * beatFrames + (SILENCE_SECONDS * RATE).roundToInt())
        for (beat in 0 until meter.beatsPerBar) {
            val samples = click(meter.accentOf(beat))
            samples.copyInto(out, beat * beatFrames)
        }
        return out
    }

    private fun woodBlockFromClickTrack(): FloatArray {
        val track = ClickTrack(RATE, BPM, Meter.FOUR_FOUR)
        val bar = FloatArray(Meter.FOUR_FOUR.beatsPerBar * RATE * 60 / BPM)
        track.render(bar)
        return bar + FloatArray((SILENCE_SECONDS * RATE).roundToInt())
    }

    // As ClickSounds.render, with any partials: each decays from its level, a partial that would pass
    // 0.45 × the sample rate is left out, and the click is scaled to the wood block's peak for accent.
    private fun render(candidate: Candidate, accent: BeatAccent): FloatArray {
        val frames = (ClickSounds.CLICK_SECONDS * RATE).roundToInt()
        val attack = (candidate.attackSeconds * RATE).coerceAtLeast(1.0)
        val endFade = (ClickSounds.END_FADE_SECONDS * RATE).coerceAtLeast(1.0)
        val f0 = candidate.pitches.getValue(accent)
        val partials = candidate.partials.filter { f0 * it.ratio < 0.45 * RATE }
        val samples = DoubleArray(frames) { n ->
            val t = n.toDouble() / RATE
            var s = 0.0
            for (p in partials) s += p.level * sin(2 * PI * f0 * p.ratio * t) * exp(-t / p.decaySeconds)
            var gain = if (n < attack) raisedCosine(n / attack) else 1.0
            val left = frames - 1 - n
            if (left < endFade) gain *= raisedCosine(left / endFade)
            s * gain
        }
        val loudest = samples.maxOf { abs(it) }
        val peak = ClickSounds.peakOf(accent)
        return FloatArray(frames) { (samples[it] * peak / loudest).toFloat() }
    }

    private fun raisedCosine(x: Double): Double = 0.5 - 0.5 * cos(PI * x)

    private fun rms(x: FloatArray): Double {
        var sum = 0.0
        for (s in x) sum += s.toDouble() * s
        return sqrt(sum / x.size)
    }

    private fun db(x: Double): Double = 20 * log10(x + 1e-12)

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        return checkNotNull(dir) { "no settings.gradle.kts above ${System.getProperty("user.dir")}" }
    }

    private fun writeWav(file: File, samples: FloatArray) {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort())
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
        const val BPM = 100
        const val SILENCE_SECONDS = 1.0
        const val MAX_PEAK = 0.8f

        // The wood block's accent steps: the group click a fourth under the downbeat, the plain one an
        // octave under it.
        fun pitchesFrom(downbeatHz: Double) = mapOf(
            BeatAccent.DOWNBEAT to downbeatHz,
            BeatAccent.GROUP to downbeatHz * 0.749154, // 2^(−5/12)
            BeatAccent.PLAIN to downbeatHz / 2,
        )

        val CANDIDATES = listOf(
            // A soft electronic beep: one sine, a gentle 2 ms attack and a slow decay, so it sounds
            // like a short tone rather than a hit. E6 / B5 / E5.
            Candidate(
                file = "soft-beep",
                attackSeconds = 0.002,
                partials = listOf(Partial(1.0, 1.0, 0.025)),
                pitches = pitchesFrom(1318.51),
            ),
            // A sharp, high click that cuts through an amp: a small stiff bar struck hard, its first
            // three bending modes 1 : 2.756 : 5.404 (Fletcher & Rossing §2.15) dying fast. Its pitch,
            // A7 / E7 / A6, sits around 2–4 kHz, where the ear is most sensitive and a guitar amp has
            // little left.
            Candidate(
                file = "sharp-click",
                attackSeconds = 0.0003,
                partials = listOf(
                    Partial(1.0, 1.0, 0.007),
                    Partial(2.756, 0.45, 0.0035),
                    Partial(5.404, 0.2, 0.0018),
                ),
                pitches = pitchesFrom(3520.0),
            ),
            // A low, round knock for players who hear high sounds poorly: the first three modes of a
            // struck drum skin, 1 : 1.594 : 2.136 (zeros of the Bessel functions J0, J1, J2; Fletcher
            // & Rossing ch. 3), the upper two dying faster. E5 / B4 / E4: low, yet still high enough
            // for a phone's small speaker.
            Candidate(
                file = "low-knock",
                attackSeconds = 0.001,
                partials = listOf(
                    Partial(1.0, 1.0, 0.014),
                    Partial(1.594, 0.4, 0.008),
                    Partial(2.136, 0.2, 0.005),
                ),
                pitches = pitchesFrom(659.26),
            ),
        )
    }
}
