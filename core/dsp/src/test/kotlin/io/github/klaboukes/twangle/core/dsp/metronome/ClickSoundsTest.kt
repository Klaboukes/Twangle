package io.github.klaboukes.twangle.core.dsp.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class ClickSoundsTest {

    @Test
    fun `every click is shorter than a beat at 250 BPM and starts and ends on exactly 0`() {
        for (rate in RATES) {
            val sounds = ClickSounds(rate)
            for (sound in ClickSound.entries) {
                val frames = sounds.frames(sound)
                val expected = rate * LENGTH_MS.getValue(sound) / 1000.0
                assertTrue("$sound at $rate Hz is $frames samples, not $expected", abs(frames - expected) <= 1)
                assertTrue("$sound at $rate Hz is longer than a beat at 250 BPM", frames < rate * 60 / ClickTrack.MAX_BPM)
                for (accent in BeatAccent.entries) {
                    val click = sounds.waveform(sound, accent)
                    assertEquals("$sound $accent at $rate Hz", frames, click.size)
                    assertEquals("$sound $accent at $rate Hz starts at 0", 0f, click.first(), 0f)
                    assertEquals("$sound $accent at $rate Hz ends at 0", 0f, click.last(), 0f)
                }
            }
        }
    }

    @Test
    fun `no click passes 0,8, and the wood block's peaks are as set`() {
        for (rate in RATES) {
            val sounds = ClickSounds(rate)
            for (sound in ClickSound.entries) {
                for (accent in BeatAccent.entries) {
                    val peak = sounds.waveform(sound, accent).maxOf { abs(it) }
                    assertTrue("$sound $accent at $rate Hz peaks at $peak", peak <= ClickSounds.MAX_PEAK)
                    if (sound == ClickSound.WOOD_BLOCK) {
                        assertEquals("$accent peak at $rate Hz", ClickSounds.peakOf(accent), peak.toDouble(), 1e-6)
                    }
                }
            }
        }
    }

    @Test
    fun `beat 1 rings higher and is at least 4,5 dB louder at the peak, a group accent in between`() {
        for (rate in RATES) {
            val sounds = ClickSounds(rate)
            for (sound in ClickSound.entries) {
                val down = sounds.waveform(sound, BeatAccent.DOWNBEAT)
                val group = sounds.waveform(sound, BeatAccent.GROUP)
                val plain = sounds.waveform(sound, BeatAccent.PLAIN)
                val at = "$sound at $rate Hz"

                // Pitch: each accent's click (the one between beats too) is strongest at its own
                // pitch of the sound's main tone.
                val tones = BeatAccent.entries.map { TONE_HZ.getValue(sound) * pitchOf(sound, it) }
                for (accent in BeatAccent.entries) {
                    val click = sounds.waveform(sound, accent)
                    val own = tones[accent.ordinal]
                    val levels = tones.map { bandLevel(click, rate, it) }
                    assertEquals("$at: $accent strongest at $own Hz of $tones ($levels)", own, tones[levels.indexOf(levels.max())], 0.0)
                }

                // Level: beat 1 at least 4.5 dB over a plain beat at the peak, a group accent in between.
                val peaks = listOf(down, group, plain).map { x -> x.maxOf { abs(it) } }
                val step = 20 * log10(peaks[0] / peaks[2].toDouble())
                assertTrue("$at: beat 1 only %+.2f dB over a plain beat".format(step), step >= 4.5)
                assertTrue("$at: peaks $peaks", peaks[0] > peaks[1] && peaks[1] > peaks[2])
            }
        }
    }

    @Test
    fun `a click between the beats is the softest, at least 3 dB under a plain beat, and lower`() {
        for (rate in RATES) {
            val sounds = ClickSounds(rate)
            for (sound in ClickSound.entries) {
                val at = "$sound at $rate Hz"
                val between = sounds.waveform(sound, BeatAccent.SUBDIVISION)
                val plain = sounds.waveform(sound, BeatAccent.PLAIN)
                val peakStep = 20 * log10(plain.maxOf { abs(it) } / between.maxOf { abs(it) }.toDouble())
                val energyStep = 10 * log10(plain.sumOf { it.toDouble() * it } / between.sumOf { it.toDouble() * it })
                assertTrue("$at: only %.2f dB under a plain beat at the peak".format(peakStep), peakStep >= 3.0)
                assertTrue("$at: only %.2f dB under a plain beat by energy".format(energyStep), energyStep >= 3.0)
                assertTrue("$at: not lower than a plain beat", pitchOf(sound, BeatAccent.SUBDIVISION) < 1.0)
            }
        }
    }

    @Test
    fun `every sound is as loud as the wood block, the same at every rate`() {
        // As loud: a bar of 4/4 (beat 1 and three plain beats) has the wood block's RMS, within
        // 1 dB, at 48 kHz, the rate the owner chose the samples at (the rim click is held 0.7 dB
        // under by its 0.8 peak). At the other rates within 0.5 dB of that: the rim click's crack
        // peaks a little higher between the 48 kHz samples, so it comes out up to 0.4 dB quieter.
        val at48k = ClickSound.entries.associateWith { barLevelDb(ClickSounds(48_000), it) }
        for (sound in ClickSound.entries) {
            assertTrue("$sound at 48 kHz: %+.2f dB".format(at48k.getValue(sound)), abs(at48k.getValue(sound)) <= 1.0)
        }
        for (rate in RATES) {
            val sounds = ClickSounds(rate)
            for (sound in ClickSound.entries) {
                val level = barLevelDb(sounds, sound)
                assertTrue("$sound at $rate Hz: %+.2f dB".format(level), abs(level - at48k.getValue(sound)) <= 0.5)
            }
        }
    }

    @Test
    fun `at 48 kHz the app plays the approved samples sample for sample`() {
        // src/test/resources/clicks/<sound>.wav, the sounds the owner approved: one bar of 4/4 at
        // 100 BPM, then a second of silence, 16-bit (ClickCandidatesReport). The app's clicks,
        // through ClickTrack, give the same 16-bit samples, every one.
        val rate = 48_000
        val beat = rate * 60 / 100
        for (sound in ClickSound.entries) {
            val name = sound.name.lowercase().replace('_', '-')
            val wav = requireNotNull(javaClass.getResource("/clicks/$name.wav")) { "no clicks/$name.wav on the class path" }
            val approved = readWav(File(wav.toURI()))
            val bar = FloatArray(4 * beat + rate)
            ClickTrack(rate, bpm = 100, meter = Meter.FOUR_FOUR, sound = sound).render(bar, 0, 4 * beat)
            assertEquals("$name.wav length", bar.size, approved.size)
            val made = ShortArray(bar.size) { (bar[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort() }
            val differing = made.indices.count { made[it] != approved[it] }
            assertEquals("$name: samples unlike the approved one", 0, differing)
        }
    }

    @Test
    fun `the same rate gives the same clicks`() {
        for (rate in listOf(44_100, 48_000)) {
            val a = ClickSounds(rate)
            val b = ClickSounds(rate)
            for (sound in ClickSound.entries) {
                for (accent in BeatAccent.entries) {
                    assertTrue("$sound $accent at $rate Hz", a.waveform(sound, accent).contentEquals(b.waveform(sound, accent)))
                }
            }
        }
    }

    @Test
    fun `an upper mode past 0,45 of the rate is left out, so nothing folds back`() {
        // The downbeat's upper mode (4.85 kHz) would fold back to about 3.15 kHz at 8 kHz, so it is left out.
        val rate = 8_000
        val click = ClickSounds(rate).waveform(BeatAccent.DOWNBEAT)
        val low = magnitude(click, rate, ClickSounds.frequencyOf(BeatAccent.DOWNBEAT))
        for (f in 3_000..3_300 step 10) {
            assertTrue("energy at $f Hz", magnitude(click, rate, f.toDouble()) < low * 0.05)
        }
    }

    @Test
    fun `the wood block's accents are at their pitches`() {
        val sounds = ClickSounds(48_000)
        for (accent in BeatAccent.entries) {
            val strongest = (200..4_000 step 5).maxBy { magnitude(sounds.waveform(accent), 48_000, it.toDouble()) }
            assertEquals("$accent pitch", ClickSounds.frequencyOf(accent), strongest.toDouble(), 25.0)
        }
    }

    // The bar's RMS against the wood block's, in dB.
    private fun barLevelDb(sounds: ClickSounds, sound: ClickSound): Double {
        fun energy(s: ClickSound) = (0 until 4).sumOf { beat ->
            sounds.waveform(s, Meter.FOUR_FOUR.accentOf(beat)).sumOf { it.toDouble() * it }
        }
        return 10 * log10(energy(sound) / energy(ClickSound.WOOD_BLOCK))
    }

    private fun pitchOf(sound: ClickSound, accent: BeatAccent): Double =
        ClickRecipes.of(sound)?.let { PercussionSynth.pitchOf(it, accent) }
            ?: (ClickSounds.frequencyOf(accent) / ClickSounds.frequencyOf(BeatAccent.PLAIN))

    // The spectrum's magnitude summed over ±3 % around hz.
    private fun bandLevel(x: FloatArray, rate: Int, hz: Double): Double = (-3..3).sumOf { magnitude(x, rate, hz * (1 + 0.01 * it)) }

    // Magnitude of the click's spectrum at frequencyHz (one DFT bin, zero-padded resolution).
    private fun magnitude(x: FloatArray, rate: Int, frequencyHz: Double): Double {
        var re = 0.0
        var im = 0.0
        for (n in x.indices) {
            val phase = 2 * PI * frequencyHz * n / rate
            re += x[n] * cos(phase)
            im -= x[n] * sin(phase)
        }
        return sqrt(re * re + im * im)
    }

    // The samples of a 16-bit mono PCM WAV file with a plain 44-byte header.
    private fun readWav(file: File): ShortArray {
        val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(file.readBytes(), 0, 4))
        assertEquals("16-bit", 16, bytes.getShort(34).toInt())
        val size = bytes.getInt(40)
        return ShortArray(size / 2) { bytes.getShort(44 + 2 * it) }
    }

    private companion object {
        val RATES = listOf(16_000, 44_100, 48_000, 96_000)

        val LENGTH_MS = mapOf(
            ClickSound.WOOD_BLOCK to 40,
            ClickSound.COWBELL to 180,
            ClickSound.CLAVES to 120,
            ClickSound.RIM_CLICK to 80,
        )

        // Each sound's main tone on a plain beat: the wood block's bar, the cowbell's lowest mode,
        // the claves' rod, the rim click's hoop.
        val TONE_HZ = mapOf(
            ClickSound.WOOD_BLOCK to 880.0,
            ClickSound.COWBELL to 560.0,
            ClickSound.CLAVES to 2200.0,
            ClickSound.RIM_CLICK to 1200.0,
        )
    }
}
