package io.github.klaboukes.twangle.core.dsp.synth

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
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
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The reference tone in every instrument's voice: on pitch, the right shape (a pluck dies away, a
 * bow holds), the sounds that make it an instrument (the pick's noise, the body, the pluck point's
 * comb, stiff steel strings, a mandolin's beating pair, a 12-string's octave pairs), and the invariants of a tone generator
 * (no clipping, no clicks, no allocation, the same samples in chunks or at once).
 */
class StringSynthTest {

    private val guitar = StringSynth(RATE, StringVoices.STEEL_GUITAR)

    /** Every (voice, open string) pair the tuner can play, each once. */
    private val presetStrings = Instruments.all
        .flatMap { instrument -> instrument.tunings.flatMap { tuning -> tuning.strings.map { instrument.voice to it } } }
        .distinct()

    @Test
    fun `every preset string of every instrument is exactly on pitch at any A4`() {
        // Without the string's stiffness, which puts the upper partials a little sharp on purpose
        // (checked below): the fundamental, the glide, the courses, the body and the noise alone.
        for (a4 in listOf(NoteMapper.MIN_A4_HZ, NoteMapper.DEFAULT_A4_HZ, NoteMapper.MAX_A4_HZ)) {
            val mapper = NoteMapper(a4)
            for ((voice, note) in presetStrings) {
                val f = mapper.frequencyOf(note)
                val tone = renderAll(StringSynth(RATE, voice.copy(stiffness = 0.0)), f)
                assertCents("${voice.id} $note at A4 = $a4, 0.3 s", tone, f, RATE, 0.3f, 1f)
                assertCents("${voice.id} $note at A4 = $a4, 3 s", tone, f, RATE, 3f, 1f)
            }
        }
    }

    @Test
    fun `the shipped voices read on pitch once the brightest partials have died, a little sharp before on stiff strings`() {
        // Steel strings are stiff, so their upper partials sit sharp of exact multiples and YIN, which
        // weighs the whole waveform, reads the start of a low note sharp, as it does a real string
        // (ROADMAP Phase 46). The fundamental is exact throughout; by 3 s the detector agrees.
        val mapper = NoteMapper(NoteMapper.DEFAULT_A4_HZ)
        for ((voice, note) in presetStrings) {
            val f = mapper.frequencyOf(note)
            val tone = renderAll(StringSynth(RATE, voice), f)
            assertCents("${voice.id} $note, 3 s", tone, f, RATE, 3f, 1f)
            assertCents("${voice.id} $note, 1 s", tone, f, RATE, 1f, 2.5f)
            if (voice.stiffness == 0.0) assertCents("${voice.id} $note, 0.3 s", tone, f, RATE, 0.3f, 1f)
        }
    }

    @Test
    fun `the pitch holds at 44_1 kHz`() {
        for (voice in listOf(StringVoices.STEEL_GUITAR, StringVoices.ELECTRIC_BASS, StringVoices.VIOLIN, StringVoices.MANDOLIN)) {
            for (f in listOf(41.2f, 110f, 440f, 659.26f)) {
                val tone = renderAll(StringSynth(44_100, voice.copy(stiffness = 0.0)), f)
                assertCents("${voice.id} $f Hz at 44.1 kHz", tone, f, 44_100, 0.3f, 1f)
            }
        }
    }

    @Test
    fun `a plucked note starts a little sharp and settles, a bowed one starts in tune`() {
        for (voice in StringVoices.all) {
            val tone = renderAll(StringSynth(RATE, voice.copy(stiffness = 0.0)), 440f)
            val early = centsAt(tone, 440f, RATE, 0.01f)
            val settled = centsAt(tone, 440f, RATE, 1f)
            if (voice.glideCents > 0) {
                assertTrue("${voice.id}: early $early, settled $settled", early - settled > 0.4f)
                assertTrue("${voice.id}: early $early, settled $settled", early - settled < voice.glideCents)
            } else {
                assertEquals("${voice.id}: early $early, settled $settled", settled, early, 0.5f)
            }
        }
    }

    @Test
    fun `stops by itself after the set duration and then stays silent`() {
        for (voice in StringVoices.all) {
            val synth = StringSynth(RATE, voice)
            val tone = renderAll(synth, 220f)
            assertEquals(voice.id, (StringSynth.DEFAULT_DURATION_SECONDS * RATE).roundToInt(), tone.size)
            assertFalse(voice.id, synth.isSounding)
            assertEquals(voice.id, 0, synth.render(FloatArray(64)))
        }
        val short = StringSynth(RATE, StringVoices.CELLO, durationSeconds = 1.5f)
        assertEquals((1.5f * RATE).roundToInt(), renderAll(short, 220f).size)
    }

    @Test
    fun `a plucked note dies away like a string and ends on silence`() {
        for (voice in StringVoices.all.filterNot { it.bowed }) {
            for (f in listOf(41.2f, 110f, 440f, 1318.5f)) {
                val tone = renderAll(StringSynth(RATE, voice), f)
                val start = rmsDb(tone, 0.05f, 0.5f)
                val later = rmsDb(tone, StringSynth.DEFAULT_DURATION_SECONDS - StringSynth.END_FADE_SECONDS - 0.5f, 0.5f)
                // A bass string sings on (its sustain is over 2 s), the others have clearly died away.
                val expected = if (voice.sustainSeconds >= 2) 8 else 15
                assertTrue("${voice.id} $f Hz: start $start dB, later $later dB", start - later > expected)
                assertSilentEnd(voice, f, tone)
            }
        }
    }

    @Test
    fun `a bowed note swells in, holds its level and rings out on silence`() {
        for (voice in StringVoices.all.filter { it.bowed }) {
            for (f in listOf(65.41f, 196f, 440f, 659.26f)) {
                val tone = renderAll(StringSynth(RATE, voice), f)
                val onset = rmsDb(tone, 0.01f, 0.02f)
                val held = listOf(0.5f, 1.5f, 2.5f, 3.2f).map { rmsDb(tone, it, 0.1f) }
                assertTrue("${voice.id} $f Hz: onset $onset dB, held ${held[0]} dB", held[0] - onset > 10)
                assertTrue("${voice.id} $f Hz: held $held", held.max() - held.min() < 2)
                assertSilentEnd(voice, f, tone)
            }
        }
    }

    @Test
    fun `never clips and is loud enough to hear`() {
        for (voice in StringVoices.all) {
            val synth = StringSynth(RATE, voice)
            assertTrue("${voice.id}: strings get ${synth.harmonicPeak}", synth.harmonicPeak >= StringSynth.PEAK * StringSynth.MIN_HARMONIC_SHARE)
            var f = 30f
            while (f < synth.maxFrequencyHz) {
                val tone = renderSeconds(synth, f, 1.5f)
                val peak = tone.maxOf { abs(it) }
                assertTrue("${voice.id} $f Hz: peak $peak", peak <= StringSynth.PEAK)
                assertTrue("${voice.id} $f Hz: peak $peak", peak > 0.2f)
                f *= 1.26f
            }
        }
    }

    @Test
    fun `starts and ends without a click`() {
        for (voice in StringVoices.all) {
            for (f in listOf(41.2f, 329.63f, 1318.5f)) {
                val tone = renderAll(StringSynth(RATE, voice), f)
                // Nothing jumps before the sound has begun: the strings fade in, the noise ramps up.
                assertEquals("${voice.id} $f Hz: first sample", 0f, tone[0], 0f)
                assertTrue("${voice.id} $f Hz: start", maxStep(tone, 0, us(500)) <= maxStep(tone, us(1_000), ms(80)))
                assertTrue("${voice.id} $f Hz: end", maxStep(tone, tone.size - ms(10), tone.size) <= maxStep(tone, ms(20), ms(200)))
            }
        }
    }

    @Test
    fun `release fades out quickly without a click`() {
        for (voice in listOf(StringVoices.STEEL_GUITAR, StringVoices.VIOLIN)) {
            val synth = StringSynth(RATE, voice)
            val f = 329.63f
            synth.start(f)
            val before = FloatArray(ms(300))
            assertEquals(before.size, synth.render(before))
            synth.release()
            val after = FloatArray(ms(500))
            val n = synth.render(after)

            assertEquals(voice.id, (StringSynth.STOP_FADE_SECONDS * RATE).roundToInt(), n)
            assertFalse(voice.id, synth.isSounding)
            assertEquals(voice.id, 0f, after[n - 1], 1e-7f)
            // No step across the release or during the fade beyond what the tone itself was making
            // just before (with a margin: a bowed note's noise makes every window's biggest step differ).
            val joined = before + after.copyOf(n)
            val fading = maxStep(joined, before.size - 1, joined.size)
            val playing = maxStep(before, before.size - ms(100), before.size)
            assertTrue("${voice.id}: fading $fading, playing $playing", fading <= 1.3f * playing)
        }
    }

    @Test
    fun `a longer release never makes a running fade slower or louder`() {
        guitar.start(440f)
        guitar.render(FloatArray(ms(100)))
        guitar.release(0.02f)
        guitar.render(FloatArray(ms(10)))
        guitar.release(1f) // ignored: the running fade ends sooner
        assertEquals(ms(10), guitar.render(FloatArray(ms(500))))
        assertFalse(guitar.isSounding)
    }

    @Test
    fun `rendering in chunks gives the same samples as at once`() {
        for (voice in StringVoices.all) {
            val whole = renderAll(StringSynth(RATE, voice), 220f)
            val chunked = StringSynth(RATE, voice)
            chunked.start(220f)
            val parts = FloatArray(whole.size + 1000)
            var filled = 0
            while (true) {
                val n = chunked.render(parts, filled, minOf(480, parts.size - filled))
                if (n == 0) break
                filled += n
            }
            assertEquals(voice.id, whole.size, filled)
            assertArrayEquals(voice.id, whole, parts.copyOf(filled), 0f)
        }
    }

    @Test
    fun `start, render and release allocate nothing`() {
        for (voice in StringVoices.all) {
            val synth = StringSynth(RATE, voice)
            val buffer = FloatArray(480)
            repeat(3) { synth.start(110f); while (synth.render(buffer) > 0) Unit }

            val bytes = allocatedBytes {
                synth.start(110f)
                repeat(400) { synth.render(buffer) } // through the glide and well into the note
                synth.release()
                synth.render(buffer)
            }
            assertEquals(voice.id, 0L, bytes)
        }
    }

    @Test
    fun `rejects frequencies it can't play and voices too noisy to hear the strings`() {
        assertThrows(IllegalArgumentException::class.java) { guitar.start(0f) }
        assertThrows(IllegalArgumentException::class.java) { guitar.start(-440f) }
        assertThrows(IllegalArgumentException::class.java) { guitar.start(guitar.maxFrequencyHz * 1.01f) }
        assertThrows(IllegalArgumentException::class.java) { StringSynth(RATE, StringVoices.STEEL_GUITAR, durationSeconds = 0.5f) }
        assertThrows(IllegalArgumentException::class.java) { StringSynth(RATE, StringVoices.VIOLIN.copy(bowNoise = 2.0)) }
        assertThrows(IllegalArgumentException::class.java) { StringVoice("bad", pluckPoint = 0.7) }
        assertThrows(IllegalArgumentException::class.java) { StringVoice("bad", courses = 3) }
    }

    // What makes it an instrument.

    @Test
    fun `the pick's scrape and the body's knock are heard at the start and not later`() {
        for (voice in StringVoices.all.filterNot { it.bowed }) {
            val f = 110f
            val withNoise = renderAll(StringSynth(RATE, voice), f)
            val silentStrings = renderAll(StringSynth(RATE, voice.copy(attackNoise = 0.0, bodyThump = 0.0)), f)
            // The two synths leave the strings different room, so compare shapes: the start of the
            // noisy tone differs from the quiet one far more than its middle does.
            val scale = (silentStrings.copyOfRange(ms(500), ms(600)).maxOf { abs(it) } / withNoise.copyOfRange(ms(500), ms(600)).maxOf { abs(it) })
            val difference = FloatArray(withNoise.size) { withNoise[it] * scale - silentStrings[it] }
            val atStart = rmsDb(difference, 0f, 0.02f)
            val later = rmsDb(difference, 0.3f, 0.1f)
            assertTrue("${voice.id}: start $atStart dB, later $later dB", atStart - later > 20)
            val synth = StringSynth(RATE, voice)
            assertTrue("${voice.id}: noise peaks at ${synth.noisePeak}", synth.noisePeak > 0.01 && synth.noisePeak < 0.25 * StringSynth.PEAK)
        }
    }

    @Test
    fun `the pluck point leaves its hole in the spectrum`() {
        // A guitar picked at 0.17 of the string: sin(6π·0.17) ≈ −0.06, so the 6th partial nearly
        // vanishes while the 5th (0.45) and 7th (−0.56) ring. The strings alone, no body.
        val voice = StringVoices.STEEL_GUITAR.copy(body = emptyList(), stiffness = 0.0, attackNoise = 0.0, bodyThump = 0.0)
        val tone = renderAll(StringSynth(RATE, voice), 110f)
        val p5 = partial(tone, 110f * 5)
        val p6 = partial(tone, 110f * 6)
        val p7 = partial(tone, 110f * 7)
        assertTrue("5th $p5, 6th $p6, 7th $p7", p6 < 0.3 * minOf(p5, p7))
    }

    @Test
    fun `the body raises the partials on its resonances`() {
        // The guitar's air resonance sits at 98 Hz: G2's fundamental comes out stronger against its
        // 3rd partial (294 Hz, between resonances) than the same string without a body gives.
        val f = 98f
        val withBody = renderAll(StringSynth(RATE, StringVoices.STEEL_GUITAR.copy(stiffness = 0.0)), f)
        val without = renderAll(StringSynth(RATE, StringVoices.STEEL_GUITAR.copy(stiffness = 0.0, body = emptyList())), f)
        val ratioWith = partial(withBody, f) / partial(withBody, 3 * f)
        val ratioWithout = partial(without, f) / partial(without, 3 * f)
        assertTrue("with body $ratioWith, without $ratioWithout", ratioWith / ratioWithout > 1.8)
    }

    @Test
    fun `a mandolin's pair of strings beats, a single string does not`() {
        // The pair sit 2.4 cents apart: at E5 (659 Hz) they beat at 0.91 Hz, so the fundamental is
        // nearly silent 0.55 s in and back by 0.8 s; a single string's just fades.
        val f = 659.26f
        for (voice in listOf(StringVoices.MANDOLIN, StringVoices.STEEL_GUITAR)) {
            val tone = renderAll(StringSynth(RATE, voice.copy(stiffness = 0.0)), f)
            val early = partial(tone, f, 0.275f, 0.05f)
            val mid = partial(tone, f, 0.525f, 0.05f)
            val late = partial(tone, f, 0.775f, 0.05f)
            val dip = mid / sqrt(early * late)
            if (voice.courses == 2) assertTrue("${voice.id}: dip $dip", dip < 0.35) else assertTrue("${voice.id}: dip $dip", dip > 0.7)
        }
    }

    @Test
    fun `a 12-string's lower pairs sound an octave apart, its top two in unison`() {
        val mapper = NoteMapper(NoteMapper.DEFAULT_A4_HZ)
        val twelve = Instruments.GUITAR_12.defaultTuning
        val synth = StringSynth(RATE, StringVoices.TWELVE_STRING)
        twelve.strings.forEachIndexed { index, note ->
            val f = mapper.frequencyOf(note)
            val pair = twelve.hasOctaveString(index)
            synth.start(f, octavePair = pair)
            val tone = FloatArray(((synth.durationSeconds + 1) * RATE).toInt()).let { it.copyOf(synth.render(it)) }
            val single = renderAll(StringSynth(RATE, StringVoices.STEEL_GUITAR), f)
            // The octave string puts its weight on the second partial, against the fundamental.
            val octaveWeight = (partial(tone, 2 * f) / partial(tone, f)) / (partial(single, 2 * f) / partial(single, f))
            if (pair) assertTrue("$note: octave weight $octaveWeight", octaveWeight > 1.4) else assertEquals("$note: octave weight", 1.0, octaveWeight, 0.1)
            // Read as the course's own note, the main string exact (measured: within 1.6 cents at 0.3 s, 0.3 at 3 s).
            assertCents("$note pair, 0.3 s", tone, f, RATE, 0.3f, 2.5f)
            assertCents("$note pair, 3 s", tone, f, RATE, 3f, 1f)
            assertTrue("$note: peak", tone.maxOf { abs(it) } <= StringSynth.PEAK)
            assertSilentEnd(StringVoices.TWELVE_STRING, f, tone)
        }
    }

    @Test
    fun `steel strings' upper partials sit a little sharp, nylon's do not`() {
        // Partial k of a stiff string sits at k·f·√(1 + B·k²) (the fundamental exact): the bass's
        // 10th partial on the low E, B = 6·10⁻⁵, is 5.1 cents sharp; the ukulele's is harmonic.
        val bass = renderAll(StringSynth(RATE, StringVoices.ELECTRIC_BASS), 41.2f)
        val sharp = sharpestCents(bass, 41.2f * 10)
        assertTrue("bass 10th partial $sharp cents sharp", sharp > 3.5 && sharp < 7)
        val ukulele = renderAll(StringSynth(RATE, StringVoices.UKULELE), 261.63f)
        assertEquals("ukulele 10th partial", 0.0, sharpestCents(ukulele, 261.63f * 10), 1.0)
    }

    // Helpers

    private fun assertCents(label: String, tone: FloatArray, frequencyHz: Float, rate: Int, startSeconds: Float, toleranceCents: Float) {
        val cents = centsAt(tone, frequencyHz, rate, startSeconds)
        assertTrue("$label: $cents cents", abs(cents) <= toleranceCents)
    }

    /** What the app's own detector reads from the tone from [startSeconds] on, in cents from [frequencyHz]. */
    private fun centsAt(tone: FloatArray, frequencyHz: Float, rate: Int, startSeconds: Float): Float {
        val settings = AnalysisSettings.forSampleRate(rate, PitchRange.forLowestNote(frequencyHz))
        val from = (startSeconds * rate).roundToInt()
        val result = settings.newPitchDetector().detect(tone.copyOfRange(from, from + settings.windowSize))
        assertTrue("$frequencyHz Hz at $startSeconds s: no pitch", result.isPitched)
        return 1200 * log2(result.frequencyHz / frequencyHz)
    }

    private fun assertSilentEnd(voice: StringVoice, f: Float, tone: FloatArray) {
        assertEquals("${voice.id} $f Hz: last sample", 0f, tone.last(), 1e-7f)
        assertTrue("${voice.id} $f Hz: quiet near the end", rmsDb(tone, StringSynth.DEFAULT_DURATION_SECONDS - 0.05f, 0.05f) < -60)
    }

    private fun renderAll(synth: StringSynth, frequencyHz: Float): FloatArray = renderSeconds(synth, frequencyHz, synth.durationSeconds + 1)

    private fun renderSeconds(synth: StringSynth, frequencyHz: Float, seconds: Float): FloatArray {
        synth.start(frequencyHz)
        val out = FloatArray((seconds * synth.sampleRate).toInt())
        val n = synth.render(out)
        return out.copyOf(n)
    }

    /** Magnitude of the tone's spectrum at [hz] over [seconds] from [fromSeconds] (a plain DFT bin, Goertzel-style). */
    private fun partial(tone: FloatArray, hz: Float, fromSeconds: Float = 0.05f, seconds: Float = 0.2f): Double {
        val from = (fromSeconds * RATE).roundToInt()
        val to = from + (seconds * RATE).roundToInt()
        var re = 0.0
        var im = 0.0
        val step = 2 * PI * hz / RATE
        for (n in from until to) {
            re += tone[n] * cos(step * (n - from))
            im += tone[n] * sin(step * (n - from))
        }
        return sqrt(re * re + im * im)
    }

    /** Where the tone's spectrum peaks near [hz], in cents from it, searched 0.2–0.7 s in. */
    private fun sharpestCents(tone: FloatArray, hz: Float): Double {
        var best = 0.0
        var bestCents = 0.0
        var cents = -8.0
        while (cents <= 12.0) {
            val magnitude = partial(tone, (hz * 2.0.pow(cents / 1200)).toFloat(), 0.2f, 0.5f)
            if (magnitude > best) {
                best = magnitude
                bestCents = cents
            }
            cents += 0.25
        }
        return bestCents
    }

    private fun ms(millis: Int): Int = RATE * millis / 1000

    private fun us(micros: Int): Int = RATE * micros / 1_000_000

    private fun maxStep(x: FloatArray, from: Int, to: Int): Float {
        var most = 0f
        for (i in maxOf(1, from) until to) most = maxOf(most, abs(x[i] - x[i - 1]))
        return most
    }

    private fun rmsDb(x: FloatArray, fromSeconds: Float, seconds: Float): Double {
        val from = (fromSeconds * RATE).roundToInt()
        val to = minOf(x.size, from + (seconds * RATE).roundToInt())
        var sum = 0.0
        for (i in from until to) sum += x[i].toDouble() * x[i]
        return 20 * log10(sqrt(sum / (to - from)) + 1e-12)
    }

    private companion object {
        const val RATE = 48_000
    }
}
