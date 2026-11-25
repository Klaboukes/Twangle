package io.github.klaboukes.twangle.core.dsp.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The sound guide's stand-in sounds (Phase 94): round 2's pair C and its bell, as `scripts/guide-sounds.py` makes them.
 * Their lengths, the pitch of each blip and the bell, up for sharp and down for flat, no clicks at the ends, the peak,
 * no DC, and no note on an open string's pitch.
 */
class GuideSoundsTest {

    @Test
    fun `pair C is two 60 ms blips 85 ms apart, 145 ms in all, at any rate`() {
        for (rate in RATES) {
            for (sound in listOf(GuideSounds.sharp(rate), GuideSounds.flat(rate))) {
                assertEquals(samples(0.085f, rate) + samples(0.06f, rate), sound.size)
                assertEquals(0.145, sound.size.toDouble() / rate, 1.0 / rate)
            }
        }
    }

    @Test
    fun `the bell is as long as the player picked`() {
        for (seconds in listOf(0.3f, 1f, 2f)) {
            assertEquals(samples(seconds, RATE), GuideSounds.inTune(seconds, RATE).size)
        }
    }

    @Test
    fun `sharp steps up from F sharp 5 to A sharp 5, flat steps down`() {
        val sharp = GuideSounds.sharp(RATE)
        assertPitch(GuideSounds.FS5_HZ, firstBlip(sharp), "sharp's first blip")
        assertPitch(GuideSounds.AS5_HZ, secondBlip(sharp), "sharp's second blip")
        val flat = GuideSounds.flat(RATE)
        assertPitch(GuideSounds.AS5_HZ, firstBlip(flat), "flat's first blip")
        assertPitch(GuideSounds.FS5_HZ, secondBlip(flat), "flat's second blip")
    }

    @Test
    fun `the bell rings on C5 at every length`() {
        for (seconds in listOf(0.3f, 1f, 2f)) {
            val bell = GuideSounds.inTune(seconds, RATE)
            assertPitch(GuideSounds.C5_HZ, bell.copyOfRange(0, samples(0.25f, RATE)), "the $seconds s bell")
        }
    }

    @Test
    fun `each pulse has its sound`() {
        assertTrue(GuideSounds.of(GuidePulse.LOOSEN, RATE).contentEquals(GuideSounds.sharp(RATE)))
        assertTrue(GuideSounds.of(GuidePulse.TIGHTEN, RATE).contentEquals(GuideSounds.flat(RATE)))
        assertTrue(GuideSounds.of(GuidePulse.IN_TUNE, RATE, 1f).contentEquals(GuideSounds.inTune(1f, RATE)))
        assertEquals(0, GuideSounds.of(GuidePulse.NONE, RATE).size)
    }

    @Test
    fun `every sound starts and ends on 0 with no jump, so nothing clicks`() {
        for ((name, x) in allSounds()) {
            assertEquals("$name's first sample", 0f, x.first())
            assertEquals("$name's last sample", 0f, x.last())
            // The script's check: the step off 0 at either end is a hair, not a jump.
            val edge = maxOf(abs(x[1] - x[0]), abs(x[x.size - 1] - x[x.size - 2]))
            assertTrue("$name: a jump of $edge at an end", edge < 0.002f)
        }
    }

    @Test
    fun `every sound peaks at -12 dBFS and has no DC`() {
        for ((name, x) in allSounds()) {
            val peak = x.maxOf { abs(it) }
            assertEquals("$name's peak", GuideSounds.PEAK_DB, 20f * log10(peak), 0.05f)
            val dc = x.sum() / x.size
            assertTrue("$name: DC $dc", abs(dc) < 0.001f)
        }
    }

    @Test
    fun `no note of the guide sits on an open string's pitch`() {
        // E2 A2 D3 G3 B3 E4 and their octaves: every note of the guide is at least half a semitone from one.
        val strings = listOf(82.41f, 110f, 146.83f, 196f, 246.94f, 329.63f)
        for (note in listOf(GuideSounds.FS5_HZ, GuideSounds.AS5_HZ, GuideSounds.C5_HZ)) {
            val nearest = strings.flatMap { s -> (0..5).map { s * (1 shl it) } }.minOf { abs(1200f * log2(note / it)) }
            assertTrue("$note Hz is $nearest cents from an open string", nearest >= 50f)
        }
    }

    private fun allSounds(): List<Pair<String, FloatArray>> = RATES.flatMap { rate ->
        listOf(
            "sharp at $rate Hz" to GuideSounds.sharp(rate),
            "flat at $rate Hz" to GuideSounds.flat(rate),
            "short bell at $rate Hz" to GuideSounds.inTune(0.3f, rate),
            "1 s bell at $rate Hz" to GuideSounds.inTune(1f, rate),
            "2 s bell at $rate Hz" to GuideSounds.inTune(2f, rate),
        )
    }

    private fun firstBlip(x: FloatArray) = x.copyOfRange(0, samples(GuideSounds.BLIP_SECONDS, RATE))

    private fun secondBlip(x: FloatArray) = x.copyOfRange(samples(GuideSounds.STEP_SECONDS, RATE), x.size)

    /**
     * The strongest frequency in [x] between 300 and 1,500 Hz, by the magnitude of its discrete-time Fourier transform
     * (Goertzel's sum at each candidate, 0.25 Hz apart): one dominant partial's peak sits at its frequency.
     */
    private fun strongestHz(x: FloatArray): Float {
        var best = 0f
        var bestPower = -1.0
        var f = 300f
        while (f <= 1500f) {
            var re = 0.0
            var im = 0.0
            val w = 2 * PI * f / RATE
            for (i in x.indices) {
                re += x[i] * cos(w * i)
                im -= x[i] * sin(w * i)
            }
            val power = re * re + im * im
            if (power > bestPower) {
                bestPower = power
                best = f
            }
            f += 0.25f
        }
        return best
    }

    /** [x]'s pitch within 5 cents of [expectedHz]. */
    private fun assertPitch(expectedHz: Float, x: FloatArray, what: String) {
        val heard = strongestHz(x)
        val cents = 1200f * log2(heard / expectedHz)
        assertTrue("$what: $heard Hz, $cents cents from $expectedHz", abs(cents) < 5f)
    }

    private fun samples(seconds: Float, rate: Int) = (seconds.toDouble() * rate).roundToInt()

    private companion object {
        const val RATE = 48_000
        val RATES = listOf(22_050, 44_100, 48_000)
    }
}
