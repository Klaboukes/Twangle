package io.github.klaboukes.twangle.core.dsp.synth

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A few notes of one voice together (Ear training's interval played together, a strummed chord):
 * one note is the synth's own tone, two at once hold both pitches, a strum starts its strings in
 * turn, the mix never clips, ends by itself or on a release, and allocates nothing once made.
 */
class StringMixTest {

    private val mix = StringMix(RATE, StringVoices.STEEL_GUITAR)

    @Test
    fun `one note is exactly the synth's own tone`() {
        val synth = StringSynth(RATE, StringVoices.STEEL_GUITAR)
        synth.start(196f)
        val expected = FloatArray(RATE * 5).let { it.copyOf(synth.render(it)) }

        mix.start(floatArrayOf(196f))
        val heard = renderAll(mix)
        assertArrayEquals(expected, heard, 0f)
        assertFalse(mix.isSounding)
    }

    @Test
    fun `two notes at once sound both, as loud as each other, at once`() {
        val low = 220f
        val high = 329.63f // a fifth up
        mix.start(floatArrayOf(low, high))
        val tone = renderAll(mix)
        val lowLevel = partial(tone, low, 0.05f)
        val highLevel = partial(tone, high, 0.05f)
        val between = partial(tone, 270f, 0.05f)
        assertTrue("the low note sounds ($lowLevel vs $between)", lowLevel > 20 * between)
        assertTrue("the high note sounds ($highLevel vs $between)", highLevel > 20 * between)
        assertTrue("both from the first moment", partial(tone, high, 0.01f, 0.03f) > 0.3 * partial(tone, high, 0.04f, 0.03f))
        // 1/√2 of each, so the pair is about as loud as one note.
        val single = StringMix(RATE, StringVoices.STEEL_GUITAR).apply { start(floatArrayOf(low)) }.let(::renderAll)
        assertEquals(1 / sqrt(2.0), lowLevel / partial(single, low, 0.05f), 0.05)
    }

    @Test
    fun `a strummed chord is about as loud as one of its notes`() {
        // E major on a guitar in standard tuning (022100), strummed down as Chord type does.
        val chord = floatArrayOf(82.41f, 123.47f, 164.81f, 207.65f, 246.94f, 329.63f)
        for (voice in StringVoices.all) {
            val strum = StringMix(RATE, voice).apply { start(chord, spreadSeconds = 0.04f) }.let(::renderAll)
            val notes = chord.map { note -> StringMix(RATE, voice).apply { start(floatArrayOf(note)) }.let(::renderAll) }
            val oneNote = notes.map { rms(it, 0f, 2f) }.average()
            val db = 20 * log10(rms(strum, 0f, 2f) / oneNote)
            println("${voice.id}: a six-string strum is ${"%.1f".format(db)} dB from one of its notes (at 1/n it was ${"%.1f".format(db - 10 * log10(6.0))})")
            assertTrue("${voice.id}: $db dB", db in -2.0..2.0)
            assertTrue("${voice.id}: under the peak", strum.all { abs(it) <= StringSynth.PEAK })
        }
    }

    @Test
    fun `the limiter leaves a mix alone below its knee and bends it smoothly under the peak above`() {
        for (x in listOf(0f, 0.1f, -0.4f, StringMix.KNEE, -StringMix.KNEE)) assertEquals(x, StringMix.limit(x), 0f)
        var last = StringMix.KNEE
        for (i in 1..100) {
            val x = StringMix.KNEE + i * 0.005f
            val y = StringMix.limit(x)
            assertTrue("rises at $x", y > last)
            assertTrue("under the peak at $x", y < StringSynth.PEAK)
            assertEquals("the same either side", -y, StringMix.limit(-x), 0f)
            last = y
        }
        // No corner at the knee: just above it, the slope is still 1.
        val step = 1e-3f
        assertEquals(1f, (StringMix.limit(StringMix.KNEE + step) - StringMix.KNEE) / step, 0.01f)
        // Eight notes struck at once in step, the worst case: never past the peak.
        val unison = StringMix(RATE, StringVoices.STEEL_GUITAR).apply { start(FloatArray(StringMix.MAX_NOTES) { 196f }) }.let(::renderAll)
        assertTrue(unison.all { abs(it) <= StringSynth.PEAK })
        assertTrue("the limiter was needed", unison.any { abs(it) > StringMix.KNEE })
    }

    @Test
    fun `a strum starts its strings one after another`() {
        // No string's partials near another's fundamental, so each can be heard apart.
        val strings = floatArrayOf(110f, 155.56f, 207.65f, 369.99f)
        mix.start(strings, spreadSeconds = 0.1f)
        val tone = renderAll(mix)
        // The top string is still silent before its turn (3 × 0.1 s), and sounds after it.
        val before = partial(tone, strings.last(), 0.15f, 0.12f)
        val after = partial(tone, strings.last(), 0.32f, 0.12f)
        assertTrue("the top string waits its turn ($before vs $after)", after > 10 * before)
        assertTrue("ends after the last string's tone", tone.size >= ((StringSynth.DEFAULT_DURATION_SECONDS + 0.3f) * RATE).roundToInt())
    }

    @Test
    fun `never clips, starts on silence and ends on silence`() {
        for (voice in StringVoices.all) {
            val notes = StringMix(RATE, voice)
            notes.start(floatArrayOf(82.41f, 110f, 146.83f, 196f, 246.94f, 329.63f, 392f, 493.88f), spreadSeconds = 0.02f)
            val tone = renderAll(notes)
            assertTrue(voice.id, tone.all { abs(it) <= StringSynth.PEAK })
            assertEquals("${voice.id}: first sample", 0f, tone.first(), 0f)
            assertEquals("${voice.id}: last sample", 0f, tone.last(), 1e-6f)
        }
    }

    @Test
    fun `release fades every note, and strings not yet reached never sound`() {
        mix.start(floatArrayOf(110f, 220f, 330f), spreadSeconds = 0.2f)
        // Before the release, only the first string has begun; after it, the others never do.
        val buffer = FloatArray(RATE / 10) // 0.1 s: only the first string has begun
        assertEquals(buffer.size, mix.render(buffer))
        mix.release()
        var rest = 0
        val out = FloatArray(RATE)
        while (true) {
            val n = mix.render(out, 0, out.size)
            if (n == 0) break
            rest += n
        }
        assertTrue("faded within the stop fade ($rest samples)", rest <= (StringSynth.STOP_FADE_SECONDS * RATE).roundToInt() + 1)
        assertFalse(mix.isSounding)
    }

    @Test
    fun `a new tone replaces the last, fewer notes leaving the others out`() {
        mix.start(floatArrayOf(110f, 165f, 220f))
        mix.render(FloatArray(480))
        mix.start(floatArrayOf(369.99f))
        val tone = renderAll(mix)
        assertTrue(partial(tone, 110f, 0.05f) < 1e-3 * partial(tone, 369.99f, 0.05f))
    }

    @Test
    fun `rejects what it can't play`() {
        assertThrows(IllegalArgumentException::class.java) { mix.start(floatArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { mix.start(FloatArray(StringMix.MAX_NOTES + 1) { 110f }) }
        assertThrows(IllegalArgumentException::class.java) { mix.start(floatArrayOf(110f, 0f)) }
        assertThrows(IllegalArgumentException::class.java) { mix.start(floatArrayOf(110f, mix.maxFrequencyHz * 2)) }
        assertThrows(IllegalArgumentException::class.java) { mix.start(floatArrayOf(110f), spreadSeconds = -1f) }
        assertEquals(0, mix.render(FloatArray(10)))
    }

    @Test
    fun `start, render and release allocate nothing once its synths are made`() {
        val notes = floatArrayOf(110f, 164.81f, 220f, 277.18f, 329.63f, 440f)
        val buffer = FloatArray(480)
        repeat(2) { mix.start(notes, spreadSeconds = 0.03f); while (mix.render(buffer) > 0) Unit }

        val bytes = allocatedBytes {
            mix.start(notes, spreadSeconds = 0.03f)
            repeat(100) { mix.render(buffer) }
            mix.release()
            mix.render(buffer)
            mix.start(notes, count = 1)
            mix.render(buffer)
        }
        assertEquals(0L, bytes)
    }

    private fun renderAll(mix: StringMix): FloatArray {
        val out = FloatArray(RATE * 6)
        var at = 0
        while (at < out.size) {
            val n = mix.render(out, at, minOf(480, out.size - at))
            if (n == 0) break
            at += n
        }
        return out.copyOf(at)
    }

    /** The tone's root-mean-square level over [seconds] from [fromSeconds]. */
    private fun rms(tone: FloatArray, fromSeconds: Float, seconds: Float): Double {
        val from = (fromSeconds * RATE).roundToInt()
        val to = minOf(tone.size, from + (seconds * RATE).roundToInt())
        var sum = 0.0
        for (n in from until to) sum += tone[n].toDouble() * tone[n]
        return sqrt(sum / (to - from))
    }

    /** Magnitude of the tone's spectrum at [hz] over [seconds] from [fromSeconds] (one DFT bin, Hann-windowed so a neighbour doesn't leak in). */
    private fun partial(tone: FloatArray, hz: Float, fromSeconds: Float, seconds: Float = 0.2f): Double {
        val from = (fromSeconds * RATE).roundToInt()
        val to = minOf(tone.size, from + (seconds * RATE).roundToInt())
        var re = 0.0
        var im = 0.0
        val step = 2 * PI * hz / RATE
        val length = to - from
        for (n in from until to) {
            val hann = 0.5 - 0.5 * cos(2 * PI * (n - from) / length)
            re += hann * tone[n] * cos(step * (n - from))
            im += hann * tone[n] * sin(step * (n - from))
        }
        return sqrt(re * re + im * im)
    }

    private companion object {
        const val RATE = 48_000
    }
}
