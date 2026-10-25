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
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.woodDecay
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Phase 45, "Hear them first", round 2: renders today's wood block and candidate clicks that sound
 * like real percussion, one bar of 4/4 at 100 BPM each (beat 1 accented, as the app plays it), then
 * a second of silence, into `docs/design/click-sounds-preview/` as 16-bit mono 48 kHz WAV files for
 * the owner to pick from, and prints each one's peak and RMS next to the wood block's.
 *
 * Not a test, and not in the app: run it with the benchmarks
 * (`./gradlew :core:dsp:benchmark --tests '*ClickCandidatesReport*'`). The output is the same on
 * every run (every random number is seeded), so the committed files can be made again byte for byte.
 *
 * The candidates are modal synthesis ([PercussionSynth]): a mallet or stick's contact pulse driving
 * the instrument's modes, a strike-noise burst and a little room. No recordings, so nothing to
 * licence. Each set is scaled so a bar has the wood block's RMS, its peak kept at or under 0.8.
 */
class ClickCandidatesReport {

    @Test
    fun writeCandidates() {
        val dir = File(repoRoot(), "docs/design/click-sounds-preview").apply { mkdirs() }

        // Today's wood block, played by the app's own ClickTrack.
        val woodBlock = renderBar { accent, _ -> ClickSounds(RATE).waveform(accent).map { it.toDouble() }.toDoubleArray() }
        assertArrayEquals("the bar matches ClickTrack's", woodBlockFromClickTrack(), woodBlock.toFloats(1.0), 0f)
        val target = rms(woodBlock)
        val bars = mutableListOf("wood-block" to woodBlock.toFloats(1.0))

        for (sound in SOUNDS) {
            val raw = renderBar { accent, beat -> PercussionSynth.hit(sound, accent, sound.file.hashCode() * 1000L + beat) }
            // Match the wood block's RMS, but never let the downbeat pass 0.8.
            val gain = minOf(target / rms(raw), MAX_PEAK / raw.maxOf { abs(it) })
            bars += sound.file to raw.toFloats(gain)
            val down = PercussionSynth.hit(sound, BeatAccent.DOWNBEAT, 1)
            val plain = PercussionSynth.hit(sound, BeatAccent.PLAIN, 1)
            val dry = PercussionSynth.hit(sound, BeatAccent.DOWNBEAT, 1, ambience = 0.0)
            val room = DoubleArray(dry.size) { down[it] - dry[it] }
            println(
                "%-13s %.2f s, downbeat %+.1f dB over a plain beat, room %.1f dB under the dry sound".format(
                    sound.file, sound.lengthSeconds, 20 * log10(rms(down) / rms(plain)), 20 * log10(rms(dry) / rms(room)),
                ),
            )
        }

        // Only this round's files stay in the folder.
        val names = bars.map { "${it.first}.wav" }.toSet()
        dir.listFiles { f -> f.name.endsWith(".wav") && f.name !in names }?.forEach { it.delete() }

        val failures = mutableListOf<String>()
        println("file           peak   peak dBFS   RMS dBFS   vs wood block")
        for ((name, bar) in bars) {
            val peak = bar.maxOf { abs(it) }
            val vsWood = 20 * log10(rms(bar.map { it.toDouble() }.toDoubleArray()) / target)
            println("%-13s  %5.3f   %7.1f    %7.1f     %+5.1f dB".format(name, peak, db(peak.toDouble()), db(rms(bar.map { it.toDouble() }.toDoubleArray())), vsWood))
            writeWav(File(dir, "$name.wav"), bar)
            if (peak > MAX_PEAK + 1e-6f) failures += "$name clips"
            if (abs(vsWood) > 3.0) failures += "$name is not within 3 dB of the wood block"
            if (bar.first() != 0f) failures += "$name doesn't start on silence"
        }
        println("WAV files in ${dir.canonicalPath}")
        assertTrue(failures.joinToString(), failures.isEmpty())
    }

    // One bar of 4/4 at BPM, each beat's hit on its exact sample, then SILENCE_SECONDS of silence.
    private fun renderBar(hit: (BeatAccent, Int) -> DoubleArray): DoubleArray {
        val beatFrames = RATE * 60 / BPM // 28 800: exact at 100 BPM and 48 kHz.
        val meter = Meter.FOUR_FOUR
        val out = DoubleArray(meter.beatsPerBar * beatFrames + (SILENCE_SECONDS * RATE).roundToInt())
        for (beat in 0 until meter.beatsPerBar) {
            val samples = hit(meter.accentOf(beat), beat)
            check(samples.size <= beatFrames) { "a hit is longer than a beat" }
            for (i in samples.indices) out[beat * beatFrames + i] += samples[i]
        }
        return out
    }

    private fun woodBlockFromClickTrack(): FloatArray {
        val track = ClickTrack(RATE, BPM, Meter.FOUR_FOUR)
        val bar = FloatArray(Meter.FOUR_FOUR.beatsPerBar * RATE * 60 / BPM)
        track.render(bar)
        return bar + FloatArray((SILENCE_SECONDS * RATE).roundToInt())
    }

    private fun DoubleArray.toFloats(gain: Double) = FloatArray(size) { (this[it] * gain).toFloat() }

    private fun rms(x: DoubleArray): Double {
        var sum = 0.0
        for (s in x) sum += s * s
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
        const val BPM = 100
        const val SILENCE_SECONDS = 1.0
        const val MAX_PEAK = 0.8f

        fun pitches(downbeat: Double, group: Double, plain: Double) =
            mapOf(BeatAccent.DOWNBEAT to downbeat, BeatAccent.GROUP to group, BeatAccent.PLAIN to plain)

        /** The same body for every accent: only how hard it is hit tells them apart. */
        val SAME = pitches(1.0, 1.0, 1.0)

        // Two hollow wooden blocks, high for beat 1 and low for the others, hit with a hard rubber
        // mallet. Each block: the wooden lip's bending modes as a thick hardwood bar (Timoshenko
        // estimate, see barRatios), struck near its middle, two torsional modes, and the hollow's
        // air (Helmholtz) resonance carved to sit just under the lip's pitch, which gives the block
        // its hollow "tok". Wood's loss rises with frequency (woodDecay).
        val TEMPLE_BLOCKS = Sound(
            file = "temple-blocks",
            lengthSeconds = 0.25,
            fadeSeconds = 0.06,
            contactSeconds = 0.00045,
            pitches = pitches(1046.5, 880.0, 784.0), // C6, A5, G5: the high block, then the low one a fourth down.
            bursts = listOf(Burst(2800.0, 0.9, 0.0012, 0.4)),
            ambience = 0.25,
        ) { f ->
            val ratios = barRatios(5, gyrationOverLength = 0.06, eOverKappaG = 12.0)
            val x = 0.44
            buildList {
                for (n in ratios.indices) {
                    val hz = f * ratios[n]
                    add(Mode(hz, (if (n == 0) 1.0 else 0.5) * abs(barShape(n, x)), woodDecay(hz, 7.8, 4.7e-6)))
                }
                add(Mode(f * 0.993, 0.45, 0.05)) // the air in the hollow
                add(Mode(f * 0.42, 0.3, 0.015)) // the whole block's body: a short, low thud (an estimate)
                for (t in 1..2) add(Mode(f * 1.62 * t, 0.12 / t, woodDecay(f * 1.62 * t, 7.8, 4.7e-6)))
            }
        }

        // A pair of rosewood claves (20 cm × 25 mm rods): one rod held loosely at its node in a
        // cupped hand, struck near its middle by the other. Bending modes of a thick free rod
        // (Timoshenko estimate; the second sits at about 2.3× the first, not the thin bar's 2.756),
        // torsional modes from the off-centre blow (c_t/2L, wood's shear speed), the hand damping
        // the upper modes. Very hard contact: wood on wood.
        val CLAVES = Sound(
            file = "claves",
            lengthSeconds = 0.3,
            fadeSeconds = 0.08,
            contactSeconds = 0.0002,
            pitches = pitches(2489.0, 2349.3, 2093.0), // D♯7, D7, C7: beat 1 on the higher clave.
            bursts = listOf(Burst(5000.0, 0.8, 0.0005, 0.25)),
            ambience = 0.4,
        ) { f ->
            val ratios = barRatios(5, gyrationOverLength = 0.03125, eOverKappaG = 13.5)
            val x = 0.42
            buildList {
                for (n in ratios.indices) {
                    val hz = f * ratios[n]
                    add(Mode(hz, abs(barShape(n, x)), woodDecay(hz, 5.0, 8.4e-7)))
                }
                for (t in 1..3) add(Mode(f * 1.48 * t, 0.12, woodDecay(f * 1.48 * t, 5.0, 8.4e-7)))
            }
        }

        // A snare drum's side stick (cross-stick): the stick lies on the head and its shaft hits the
        // metal hoop. The stick's own bending modes (a thin hickory rod, 345 Hz, held, so damped),
        // the 14" batter head's membrane modes (ideal-membrane Bessel ratios, f01 = 190 Hz, driven
        // at the edge where the hoop is), the steel hoop's ring modes as slightly split pairs (the
        // metallic edge, beating), and a short rattle of the snare wires.
        val SIDE_STICK = Sound(
            file = "side-stick",
            lengthSeconds = 0.25,
            fadeSeconds = 0.06,
            contactSeconds = 0.00025,
            pitches = SAME,
            bursts = listOf(Burst(2200.0, 1.0, 0.0008, 0.3), Burst(5500.0, 0.5, 0.04, 0.05, delaySeconds = 0.0015, attackSeconds = 0.002)),
            ambience = 0.25,
        ) { _ ->
            buildList {
                val stick = barRatios(6, gyrationOverLength = 0.00875, eOverKappaG = 13.0)
                for (n in stick.indices) {
                    val hz = 345.0 * stick[n]
                    add(Mode(hz, abs(barShape(n, 0.3)), woodDecay(hz, 25.0, 2e-6)))
                }
                // Each mode's sign where the listener is: fixed, but not all alike, so the modes don't
                // all add up in the first instant as they would only right under the stick.
                val listener = Random(1402)
                for ((m, _, j) in MEMBRANE) {
                    val hz = 190.0 * j / MEMBRANE[0].third
                    val decay = if (m == 0 && j < 3) 0.03 else 0.08 * sqrt(190.0 / hz)
                    add(Mode(hz, 2.0 * besselJ(m, j * 0.93) * listener.sign(), decay))
                }
                for (n in 5..13) {
                    val hz = 62.0 * ringRatio(n)
                    val amp = 0.15 * 5.0 / n
                    val decay = 0.06 * (1000.0 / hz).pow(0.3)
                    add(Mode(hz * 0.9965, amp * listener.sign(), decay))
                    add(Mode(hz * 1.0035, amp * listener.sign(), decay))
                }
            }
        }

        // A closed hi-hat, tipped with a stick: two cymbals pressed together. Plate modes by
        // Chladni's law, f ∝ (m + 2n)² (Fletcher & Rossing ch. 3), spread a few percent (the dome
        // and the hammering, an estimate) and each split into a beating pair; the pressed-together
        // cymbals damp them fast. Most of the "tss" is noise: the chick of the two edges and the
        // sizzle above 6 kHz.
        val HI_HAT_MODES: List<Mode> = run {
            val rnd = Random(1414)
            buildList {
                for (k in 10..22) {
                    for (n in 0..k / 2) {
                        val hz = 40.0 * k * k * (1 + 0.04 * rnd.nextGaussian())
                        // A thin plate radiates poorly below its critical frequency (a few kHz for a
                        // cymbal), rising about as f there; above it, evenly.
                        val radiation = minOf(1.0, hz / 8000.0)
                        val amp = 0.25 * radiation * (0.3 + 0.7 * rnd.nextDouble()) / 2
                        val decay = 0.025 * (5000.0 / hz).pow(0.4)
                        add(Mode(hz * 0.9975, amp * rnd.sign(), decay))
                        add(Mode(hz * 1.0025, amp * rnd.sign(), decay))
                    }
                }
            }
        }

        val HI_HAT = Sound(
            file = "hi-hat",
            lengthSeconds = 0.2,
            fadeSeconds = 0.05,
            contactSeconds = 0.00015,
            pitches = SAME,
            bursts = listOf(Burst(3200.0, 1.2, 0.0025, 0.25), Burst(9000.0, 0.6, 0.02, 0.4)),
            ambience = 0.3,
            strikeJitter = 0.1,
        ) { _ -> HI_HAT_MODES }

        // A low rosewood marimba bar with its tube, hit with a rubber mallet: warm and low
        // for players who hear high sounds poorly. Bending modes tuned 1 : 4 : 10, as marimba bars
        // are (Rossing, *Science of Percussion Instruments*, 2000), the next two untuned (17 and
        // 25.5, an estimate), struck a little off centre (so the 4:1 mode sounds a little; on the
        // centre line torsion isn't excited at all). The quarter-wave tube under the bar adds its
        // own resonance at the fundamental, a hair higher. Loss as Chaigne & Doutaut's bars, a little damped
        // (τ ≈ 0.12 s for the fundamental instead of a free-ringing bar's 0.3–1 s) to stay a click.
        val MARIMBA = Sound(
            file = "marimba",
            lengthSeconds = 0.4,
            fadeSeconds = 0.12,
            contactSeconds = 0.0006,
            pitches = pitches(440.0, 370.0, 293.66), // A4, F♯4, D4: beat 1 a fifth above the others.
            bursts = listOf(Burst(450.0, 0.7, 0.003, 0.12)), // the mallet's soft thud
            ambience = 0.2,
        ) { f ->
            val ratios = doubleArrayOf(1.0, 3.99, 9.92, 17.0, 25.5)
            val x = 0.40
            buildList {
                for (n in ratios.indices) {
                    val hz = f * ratios[n]
                    add(Mode(hz, abs(barShape(n, x)), woodDecay(hz, 7.8, 1.95e-6)))
                }
                add(Mode(f * 1.003, 0.4, 0.15)) // the tube
                add(Mode(f * 3.03, 0.03, 0.05))
            }
        }

        // +1 or −1: a mode's sign at the listener's place.
        fun Random.sign() = if (nextBoolean()) 1.0 else -1.0

        val SOUNDS = listOf(TEMPLE_BLOCKS, CLAVES, SIDE_STICK, HI_HAT, MARIMBA)
    }
}
