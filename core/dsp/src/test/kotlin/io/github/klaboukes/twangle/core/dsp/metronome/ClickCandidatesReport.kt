package io.github.klaboukes.twangle.core.dsp.metronome

import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.Burst
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.MEMBRANE
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.Mode
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.RATE
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.Sound
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.barRatios
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.barShape
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.besselJ
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.ringRatio
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.sign
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.woodDecay
import io.github.klaboukes.twangle.core.dsp.synth.StringSynth
import io.github.klaboukes.twangle.core.dsp.synth.StringVoices
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Phase 45, "Hear them first", round 3: clicks to keep time by. Renders today's wood block and the
 * candidates into `docs/design/click-sounds-preview/` as 16-bit mono 48 kHz WAV files for the owner
 * to pick from, each twice:
 * - `<sound>.wav`: one bar of 4/4 at 100 BPM (beat 1 accented, as the app plays it), then a second
 *   of silence;
 * - `<sound>-with-guitar.wav`: the same click under two bars of a strummed steel-string guitar
 *   (down and up eighths, E minor then C, ending on E minor), the same guitar under every sound.
 * It prints each one's peak and RMS next to the wood block's.
 *
 * Not a test, and not in the app: run it with the benchmarks
 * (`./gradlew :core:dsp:benchmark --tests '*ClickCandidatesReport*'`). The output is the same on
 * every run, so the committed files can be made again byte for byte.
 *
 * Round 3's rules, from the owner (2026-10-25): a click that helps a musician keep time, not a
 * realistic instrument. A sharp attack (the peak within 1–2 ms), short (about 40 dB down within
 * 30–60 ms, at most 80 ms in all, no room, no ring), identical on every beat, unpitched or only
 * weakly pitched (inharmonic modes and noise, no clear note to clash with the key), beat 1 a little
 * higher, brighter and 3–4 dB louder. Round 2's texture stays (contact pulse, strike noise, a few
 * inharmonic modes, [PercussionSynth]), damped hard. Each set is scaled so a bar has the wood
 * block's RMS, its peak kept at or under 0.8.
 */
class ClickCandidatesReport {

    @Test
    fun writeCandidates() {
        val dir = File(repoRoot(), "docs/design/click-sounds-preview").apply { mkdirs() }

        // Today's wood block, played by the app's own ClickTrack.
        val woodClicks = BeatAccent.entries.associateWith { a -> ClickSounds(RATE).waveform(a).map { it.toDouble() }.toDoubleArray() }
        val woodBlock = renderBar { woodClicks.getValue(it) }
        assertArrayEquals("the bar matches ClickTrack's", woodBlockFromClickTrack(), woodBlock.toFloats(1.0), 0f)
        val target = rms(woodBlock)
        val clickSets = mutableListOf("wood-block" to woodClicks)

        println("sound          length   downbeat over plain")
        for (sound in SOUNDS) {
            // Where the click is heard sets each mode's sign, and so how far the modes add up in
            // the first instant. Of 300 places, take the one with the least peak for its energy
            // (the most loudness under the 0.8 peak) among those whose energy still peaks within
            // ATTACK_MS of the onset on every accent, so the beat's moment stays exact.
            val seed = (0L until 300L)
                .filter { seed -> BeatAccent.entries.all { peakMs(PercussionSynth.hit(sound, it, seed)) <= ATTACK_MS } }
                .minBy { crest(PercussionSynth.hit(sound, BeatAccent.DOWNBEAT, it)) }
            val raw = BeatAccent.entries.associateWith { PercussionSynth.hit(sound, it, seed) }
            val bar = renderBar { raw.getValue(it) }
            // Match the wood block's RMS, but never let the downbeat pass 0.8.
            val gain = minOf(target / rms(bar), MAX_PEAK / bar.maxOf { abs(it) })
            clickSets += sound.file to raw.mapValues { (_, x) -> DoubleArray(x.size) { x[it] * gain } }
            val down = raw.getValue(BeatAccent.DOWNBEAT)
            val plain = raw.getValue(BeatAccent.PLAIN)
            println("%-13s  %3.0f ms   %+.1f dB   (heard from place %d)".format(sound.file, sound.lengthSeconds * 1000, 20 * log10(rms(down) / rms(plain)), seed))
        }

        val guitar = guitarTrack()
        val strummed = 8 * BEAT_FRAMES // the two bars
        val files = mutableListOf<Pair<String, FloatArray>>()
        for ((name, clicks) in clickSets) {
            files += name to renderBar { clicks.getValue(it) }.toFloats(1.0)
            // The guitar GUITAR_OVER_CLICK_DB louder than the click by RMS over the two bars.
            val track = renderClicks(guitar.size, 9) { clicks.getValue(it) }
            val guitarGain = rms(track, strummed) / rms(guitar, strummed) * 10.0.pow(GUITAR_OVER_CLICK_DB / 20)
            val mix = DoubleArray(guitar.size) { guitarGain * guitar[it] + track[it] }
            val mixGain = minOf(1.0, MIX_PEAK / mix.maxOf { abs(it) })
            files += "$name-with-guitar" to mix.toFloats(mixGain)
            val guitarInMix = DoubleArray(guitar.size) { guitarGain * guitar[it] }
            println(
                "%-13s  with guitar: mix scaled %+.1f dB to stay under %.2f; %.0f %% of the click's energy is in bands where it is louder than the guitar".format(
                    name, 20 * log10(mixGain), MIX_PEAK, 100 * shareAboveGuitar(track, guitarInMix, 9),
                ),
            )
        }

        // Only this round's files stay in the folder.
        val names = files.map { "${it.first}.wav" }.toSet()
        dir.listFiles { f -> f.name.endsWith(".wav") && f.name !in names }?.forEach { it.delete() }

        val failures = mutableListOf<String>()
        println("file                        peak   peak dBFS   RMS dBFS   vs wood block")
        for ((name, bar) in files) {
            val peak = bar.maxOf { abs(it) }
            val r = rms(DoubleArray(bar.size) { bar[it].toDouble() })
            val vsWood = 20 * log10(r / target)
            println("%-26s  %5.3f   %7.1f    %7.1f     %+5.1f dB".format(name, peak, db(peak.toDouble()), db(r), vsWood))
            writeWav(File(dir, "$name.wav"), bar)
            if (bar.first() != 0f || bar.last() != 0f) failures += "$name doesn't start and end on silence"
            if (name.endsWith("-with-guitar")) continue
            if (peak > MAX_PEAK + 1e-6f) failures += "$name clips"
            if (abs(vsWood) > 1.0) failures += "$name is not within 1 dB of the wood block"
        }
        println("WAV files in ${dir.canonicalPath}")
        assertTrue(failures.joinToString(), failures.isEmpty())
    }

    // One bar of 4/4 at BPM, each beat's click on its exact sample, then SILENCE_SECONDS of silence.
    private fun renderBar(click: (BeatAccent) -> DoubleArray): DoubleArray =
        renderClicks(Meter.FOUR_FOUR.beatsPerBar * BEAT_FRAMES + (SILENCE_SECONDS * RATE).roundToInt(), 4, click)

    // [beats] clicks in 4/4 from sample 0, in a track of [frames] samples.
    private fun renderClicks(frames: Int, beats: Int, click: (BeatAccent) -> DoubleArray): DoubleArray {
        val out = DoubleArray(frames)
        for (beat in 0 until beats) {
            val samples = click(Meter.FOUR_FOUR.accentOf(beat % 4))
            check(samples.size <= BEAT_FRAMES) { "a click is longer than a beat" }
            for (i in samples.indices) out[beat * BEAT_FRAMES + i] += samples[i]
        }
        return out
    }

    private fun woodBlockFromClickTrack(): FloatArray {
        val track = ClickTrack(RATE, BPM, Meter.FOUR_FOUR)
        val bar = FloatArray(Meter.FOUR_FOUR.beatsPerBar * BEAT_FRAMES)
        track.render(bar)
        return bar + FloatArray((SILENCE_SECONDS * RATE).roundToInt())
    }

    /**
     * Two bars of steady eighth-note strums at [BPM] on a steel-string guitar (the app's own
     * reference-tone synth, [StringSynth] with [StringVoices.STEEL_GUITAR]): E minor, then C, then
     * one last E minor on the next downbeat left to ring. Downstrokes play every string of the chord
     * low to high, 9 ms apart, centred on the beat; upstrokes the top four, high to low, 7 ms apart
     * and softer. A string is damped (8 ms) when it is struck again or the chord changes; the last
     * chord rings and fades out. Then silence.
     */
    private fun guitarTrack(): DoubleArray {
        class Pluck(val string: Int, val hz: Double, val onset: Int, val gain: Double)

        val em = intArrayOf(40, 47, 52, 55, 59, 64) // E2 B2 E3 G3 B3 E4 (MIDI): 0 2 2 0 0 0
        val c = intArrayOf(-1, 48, 52, 55, 60, 64) //  –  C3 E3 G3 C4 E4: x 3 2 0 1 0
        val plucks = mutableListOf<Pluck>()
        fun strum(chord: IntArray, beatAt: Int, down: Boolean) {
            val strings = chord.indices.filter { chord[it] >= 0 }.let { if (down) it else it.takeLast(4).reversed() }
            val spread = (if (down) 0.009 else 0.007) * RATE
            val first = beatAt - spread * (strings.size - 1) / 2
            for ((i, s) in strings.withIndex()) {
                val hz = 440.0 * 2.0.pow((chord[s] - 69) / 12.0)
                plucks += Pluck(s, hz, (first + i * spread).roundToInt().coerceAtLeast(0), if (down) 1.0 else 0.6)
            }
        }
        for (eighth in 0 until 16) {
            val chord = if (eighth < 8) em else c
            strum(chord, eighth * BEAT_FRAMES / 2, down = eighth % 2 == 0)
        }
        val lastChord = 8 * BEAT_FRAMES
        strum(em, lastChord, down = true)

        val ringFrames = (1.6 * RATE).roundToInt()
        val total = lastChord + ringFrames + (0.6 * RATE).roundToInt()
        val chordChange = 4 * BEAT_FRAMES
        val damp = (0.008 * RATE).roundToInt()
        val out = DoubleArray(total)
        val synth = StringSynth(RATE, StringVoices.STEEL_GUITAR)
        val tone = FloatArray(ringFrames)
        for (p in plucks) {
            val next = plucks.filter { it.string == p.string && it.onset > p.onset }.minOfOrNull { it.onset }
            val change = if (p.onset < chordChange) chordChange else Int.MAX_VALUE
            val last = p.onset >= lastChord - BEAT_FRAMES / 4
            val end = if (last) p.onset + ringFrames else minOf(next ?: Int.MAX_VALUE, change) + damp
            val fade = if (last) (0.4 * RATE).roundToInt() else damp
            val length = minOf(end - p.onset, ringFrames, total - p.onset)
            synth.start(p.hz.toFloat())
            tone.fill(0f)
            val written = synth.render(tone, 0, length)
            for (i in 0 until written) {
                val left = length - 1 - i
                val gain = if (left < fade) 0.5 - 0.5 * cos(PI * left / fade) else 1.0
                out[p.onset + i] += p.gain * gain * tone[i]
            }
        }
        return out
    }

    /**
     * How well the click stands out from the guitar: over the first 80 ms after each of [beats]
     * beats, the share of the click's energy that lies in third-octave bands (50 Hz–16 kHz) where
     * the click has more energy than the guitar. A rough masking measure, not a hearing model.
     */
    private fun shareAboveGuitar(click: DoubleArray, guitar: DoubleArray, beats: Int): Double {
        val window = (0.08 * RATE).roundToInt()
        fun bandEnergy(x: DoubleArray, centerHz: Double): Double {
            // RBJ band-pass, a third of an octave wide (Q ≈ 4.32).
            val w0 = 2 * PI * centerHz / RATE
            val alpha = kotlin.math.sin(w0) / (2 * 4.32)
            val a0 = 1 + alpha
            val b0 = alpha / a0
            val a1 = -2 * cos(w0) / a0
            val a2 = (1 - alpha) / a0
            var x1 = 0.0
            var x2 = 0.0
            var y1 = 0.0
            var y2 = 0.0
            var energy = 0.0
            for (n in x.indices) {
                val y = b0 * x[n] - b0 * x2 - a1 * y1 - a2 * y2
                x2 = x1
                x1 = x[n]
                y2 = y1
                y1 = y
                if ((n % BEAT_FRAMES) < window && n / BEAT_FRAMES < beats) energy += y * y
            }
            return energy
        }
        var above = 0.0
        var all = 0.0
        var center = 50.0
        while (center < 16_000) {
            val c = bandEnergy(click, center)
            if (c > bandEnergy(guitar, center)) above += c
            all += c
            center *= 2.0.pow(1.0 / 3)
        }
        return above / all
    }

    private fun crest(x: DoubleArray): Double = x.maxOf { abs(it) } / rms(x)

    // When the energy peaks: the loudest millisecond (a 1 ms moving RMS), in ms from the onset.
    private fun peakMs(x: DoubleArray): Double {
        val w = RATE / 1000
        var sum = 0.0
        var best = 0.0
        var at = 0
        for (n in x.indices) {
            sum += x[n] * x[n]
            if (n >= w) sum -= x[n - w] * x[n - w]
            if (sum > best) {
                best = sum
                at = n
            }
        }
        return at * 1000.0 / RATE
    }

    private fun DoubleArray.toFloats(gain: Double) = FloatArray(size) { (this[it] * gain).toFloat() }

    private fun rms(x: DoubleArray, frames: Int = x.size): Double {
        var sum = 0.0
        for (i in 0 until frames) sum += x[i] * x[i]
        return sqrt(sum / frames)
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
        const val BPM = 100
        const val BEAT_FRAMES = RATE * 60 / BPM // 28 800: exact at 100 BPM and 48 kHz.
        const val SILENCE_SECONDS = 1.0
        const val MAX_PEAK = 0.8f
        const val MIX_PEAK = 0.9

        /** The energy of every click peaks within this many ms of its onset. */
        const val ATTACK_MS = 1.6

        /** How much louder (RMS over the two bars) the guitar is than the click in the mixes. */
        const val GUITAR_OVER_CLICK_DB = 14.0

        // A better wood block, the everyday default: a slotted hardwood block hit with a hard stick
        // near a node of its lowest mode, so no single note stands out. The bar's bending modes as a
        // thick hardwood bar (Timoshenko estimate, 1 : 2.36 : 3.91 : 5.49) and two torsional modes
        // (1.5, 3.0, an estimate), all damped hard (τ 6–12 ms, the loss rising with frequency as
        // Chaigne & Doutaut's), and a woody strike noise around 2 kHz. Energy 1.5–3 kHz.
        val WOODY_CLICK = Sound(
            file = "woody-click",
            lengthSeconds = 0.07,
            fadeSeconds = 0.02,
            contactSeconds = 0.0003,
            plainVelocity = 0.66,
            downbeatPitch = 1.08,
            bursts = listOf(Burst(2000.0, 1.0, 0.0025, 0.3)),
        ) { p, listener ->
            val f = 900.0 * p
            val ratios = barRatios(4, gyrationOverLength = 0.03, eOverKappaG = 12.0)
            buildList {
                for (n in ratios.indices) {
                    val hz = f * ratios[n]
                    add(Mode(hz, barShape(n, 0.3) * listener.sign(), woodDecay(hz, 80.0, 3e-6)))
                }
                add(Mode(f * 1.5, 0.35 * listener.sign(), woodDecay(f * 1.5, 80.0, 3e-6)))
                add(Mode(f * 3.0, 0.2 * listener.sign(), woodDecay(f * 3.0, 80.0, 3e-6)))
            }
        }

        // A dry snare cross-stick: the stick's shaft hits the metal hoop. The stick's bending modes
        // (a thin hickory rod, 345 Hz, Euler–Bernoulli 1 : 2.76 : 5.40 …, held, so damped), the
        // steel hoop's ring modes (f ∝ n(n² − 1)/√(n² + 1)) as slightly split pairs, the head's
        // first membrane modes (Bessel ratios, f01 = 190 Hz) as a short thump, and only a 6 ms
        // trace of the snare wires.
        val RIM_CLICK = Sound(
            file = "rim-click",
            lengthSeconds = 0.07,
            fadeSeconds = 0.02,
            contactSeconds = 0.0002,
            plainVelocity = 0.69,
            downbeatPitch = 1.06,
            bursts = listOf(
                Burst(2500.0, 0.8, 0.0012, 0.3),
                Burst(5500.0, 0.6, 0.006, 0.04, delaySeconds = 0.001, attackSeconds = 0.001),
            ),
        ) { p, listener ->
            buildList {
                val stick = barRatios(6, gyrationOverLength = 0.00875, eOverKappaG = 13.0)
                for (n in stick.indices) {
                    val hz = 345.0 * p * stick[n]
                    add(Mode(hz, barShape(n, 0.3) * listener.sign(), woodDecay(hz, 80.0, 2e-6)))
                }
                for (n in 6..14) {
                    val hz = 62.0 * p * ringRatio(n)
                    val amp = 0.12 * 6.0 / n
                    val decay = 0.015 * (1000.0 / hz).pow(0.3)
                    val sign = listener.sign() // a split pair starts together, then beats
                    add(Mode(hz * 0.9965, amp * sign, decay))
                    add(Mode(hz * 1.0035, amp * sign, decay))
                }
                for ((m, _, j) in MEMBRANE) {
                    add(Mode(190.0 * j / MEMBRANE[0].third, 1.5 * besselJ(m, j * 0.93) * listener.sign(), 0.012))
                }
            }
        }

        // A small, high cowbell (or agogo bell) struck with a stick near its open mouth and choked
        // with a hand: the bright, clanky metal "tonk" that sits above a guitar. No published table
        // of cowbell or agogo modes was at hand, so the modes come from the physics of a thin bell
        // shell: its (m, 0) modes, where the mouth bends into m waves round the rim, follow a thin
        // ring's inextensional bending, f ∝ m(m² − 1)/√(m² + 1), 1 : 2.83 : 5.42 for m = 2, 3, 4
        // (Rayleigh; Fletcher & Rossing ch. 2 and 21), a hit near the mouth exciting them most; and
        // between them a cluster of modes with one nodal line across the shell, at 1.62, 1.94, 2.37
        // and 3.38 × the lowest (an estimate). Every mode is a pair split by the shell's flattened,
        // uneven shape (±1.2–1.5 %, an estimate), so it beats. The lowest at 1.1 kHz, above the
        // guitar's main energy, the strongest cluster between 1.5 and 4 kHz. A hard stick on metal
        // (0.15 ms) and a strike noise around 4.5 kHz; the hand chokes the ring (τ ≈ 24 ms at the
        // bottom, shorter higher up).
        val COWBELL_TONK = Sound(
            file = "cowbell-tonk",
            lengthSeconds = 0.08,
            fadeSeconds = 0.015,
            contactSeconds = 0.00015,
            plainVelocity = 0.66,
            downbeatPitch = 1.06,
            bursts = listOf(Burst(4500.0, 1.0, 0.0015, 0.2)),
        ) { p, listener ->
            val f = 1100.0 * p
            // (ratio, strength, pair split)
            val modes = listOf(
                Triple(ringRatio(2), 0.7, 0.015),
                Triple(1.62, 0.8, 0.012),
                Triple(1.94, 0.9, 0.012),
                Triple(2.37, 1.0, 0.012),
                Triple(ringRatio(3), 1.0, 0.015),
                Triple(3.38, 0.8, 0.012),
                Triple(ringRatio(4), 0.4, 0.015),
            )
            buildList {
                for ((ratio, amp, split) in modes) {
                    val hz = f * ratio
                    val decay = 0.024 * sqrt(f / hz)
                    val sign = listener.sign() // a split pair starts together, then beats
                    add(Mode(hz * (1 - split), amp * sign, decay))
                    add(Mode(hz * (1 + split), amp * sign, decay))
                }
            }
        }

        // A high, crisp tick for loud or distorted guitar: a stick tip on a small, stiff metal plate.
        // Plate modes by Chladni's law, f ∝ (m + 2n)² (Fletcher & Rossing ch. 3), spread a few
        // percent (an estimate) in split pairs, so there are many and none is a tone; they die within
        // about 40 ms. Above 8 kHz they are weighted down, and the contact (0.16 ms) has a natural low-pass,
        // so it is bright without being harsh. Energy 4–8 kHz.
        val BRIGHT_TICK_MODES: (Double, Random) -> List<Mode> = { p, listener ->
            val rnd = Random(4747)
            buildList {
                for (k in 6..12) {
                    for (n in 0..k / 2) {
                        val hz = 110.0 * p * k * k * (1 + 0.04 * rnd.nextGaussian())
                        val weight = if (hz < 8000) 1.0 else (8000 / hz).pow(2)
                        val amp = weight * (0.3 + 0.7 * rnd.nextDouble()) / 2
                        val decay = 0.011 * sqrt(6000 / hz)
                        val sign = listener.sign()
                        add(Mode(hz * 0.997, amp * sign, decay))
                        add(Mode(hz * 1.003, amp * sign, decay))
                    }
                }
            }
        }

        val BRIGHT_TICK = Sound(
            file = "bright-tick",
            lengthSeconds = 0.065,
            fadeSeconds = 0.02,
            contactSeconds = 0.00016,
            plainVelocity = 0.66,
            downbeatPitch = 1.06,
            bursts = listOf(Burst(6000.0, 1.2, 0.003, 0.15)),
            modes = BRIGHT_TICK_MODES,
        )

        val SOUNDS = listOf(WOODY_CLICK, RIM_CLICK, COWBELL_TONK, BRIGHT_TICK)
    }
}
