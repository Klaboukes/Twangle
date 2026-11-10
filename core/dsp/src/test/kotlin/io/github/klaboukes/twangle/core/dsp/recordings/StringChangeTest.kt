package io.github.klaboukes.twangle.core.dsp.recordings

import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.music.nearestNote
import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.SignalGate
import io.github.klaboukes.twangle.core.dsp.pitch.TunerChain
import io.github.klaboukes.twangle.core.dsp.smoothing.PitchSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * A string change two octaves or a twelfth away, on the real recordings: a low string still ringing
 * and the next string plucked over it only a little louder, as a player tuning string after string
 * does without muting the last one. Those are the jumps the smoother holds as a likely detector slip
 * unless the pluck rises 10 dB over the quietest recent frame (docs/architecture/signal-pipeline.md,
 * *Smoothing*, item 1), and a pluck over a ringing string rises less. In guitar mode a jump that lands
 * on another string of the tuning needs only a smaller rise; in any-note mode the old rule stays.
 */
internal class StringChangeTest {

    private val mapper = NoteMapper()
    private val rate = 48_000
    private val guitar = { PitchSmoother().apply { strings = Tunings.STANDARD } }
    private val chromatic = { PitchSmoother() }

    private fun take(name: String): RealPluck = RealPlucks.all.first { it.name == name }

    private fun frames(mix: StringChangeMix, smoother: () -> PitchSmoother): List<TunerChain.Frame> =
        TunerChain(AnalysisSettings.forSampleRate(rate), smoother = smoother).run(mix.samples)

    /** Seconds from the second pluck to the first frame showing [note], or null when none does. */
    private fun shownAfter(mix: StringChangeMix, frames: List<TunerChain.Frame>, note: Note): Float? =
        frames.firstOrNull { it.endSample >= mix.secondOnset && it.isShown && mapper.nearestNote(it.shownHz) == note }
            ?.let { (it.endSample - mix.secondOnset) / rate.toFloat() }

    @Test
    fun `the high E plucked over a ringing low E, only 6 dB louder, shows within a quarter second in guitar mode`() {
        // A low E picked hard, then a second later the high E plucked softly, its first 85 ms 6 dB
        // over the low E: the detector reads E4 for 0.8 s, and the frame level rises 8 dB. Before,
        // the needle held the low E all that time and never showed the high E; now it shows it 0.17 s
        // after the pluck, as soon as a lone pluck would (0.10–0.20 s, RealPluckTest).
        val mix = StringChangeMix(take("guitar-E2-pick-1"), take("guitar-E4-finger-1"), 1f, 6f)
        val high = RealPlucks.strings.last()
        val before = shownAfter(mix, frames(mix, chromatic), high)
        assertNull("any-note mode showed the high E after $before s", before)
        val after = shownAfter(mix, frames(mix, guitar), high)
        assertNotNull("guitar mode never showed the high E", after)
        assertTrue("guitar mode showed the high E only $after s after its pluck", after!! <= 0.25f)
    }

    @Test
    fun `a string two octaves or a twelfth away, plucked over the ringing one, is followed as soon as the detector hears it`() {
        // Every pair of the standard tuning two octaves or a twelfth apart (low E → high E, low E →
        // B, A → high E), pick or finger on both, 0.6 or 1 s after the first pluck, 6 or 10 dB louder.
        // Where the detector reads the new string for a while (it often doesn't: the two strings
        // repeat together at the lower one's period, which YIN finds first), the needle shows it
        // within the smoother's three frames of confirmation, and it never shows a third note.
        val pairs = listOf("E2" to "E4", "E2" to "B3", "A2" to "E4")
        var followed = 0
        for ((low, high) in pairs) for (lowTouch in TOUCHES) for (highTouch in TOUCHES) for (delay in listOf(0.6f, 1f)) for (louder in listOf(6f, 10f)) {
            val mix = StringChangeMix(take("guitar-$low-$lowTouch"), take("guitar-$high-$highTouch"), delay, louder)
            val name = "guitar-$low-$lowTouch → guitar-$high-$highTouch, $delay s later, $louder dB louder"
            val lowNote = take("guitar-$low-$lowTouch").string
            val highNote = take("guitar-$high-$highTouch").string
            val after = frames(mix, guitar).filter { it.endSample >= mix.secondOnset }
            for (frame in after.filter { it.isShown }) {
                assertTrue("$name: showed ${mapper.nearestNote(frame.shownHz)}", mapper.nearestNote(frame.shownHz) in setOf(lowNote, highNote))
            }
            // The first run of frames in which the detector reads the new string, within 0.4 s.
            val start = after.indices.firstOrNull { i ->
                (after[i].endSample - mix.secondOnset) <= 0.4f * rate &&
                    i + HEARD_FRAMES <= after.size && (i until i + HEARD_FRAMES).all { readsAs(after[it], highNote) }
            } ?: continue
            followed++
            val shownFrom = start + PitchSmoother.DEFAULT_OCTAVE_CONFIRM_FRAMES - 1
            for (i in shownFrom until start + HEARD_FRAMES) {
                assertEquals("$name: shown ${(after[i].endSample - mix.secondOnset) / rate.toFloat()} s after the pluck", highNote, mapper.nearestNote(after[i].shownHz))
            }
        }
        // The case above and the others like it are among these.
        assertTrue("only $followed mixes in which the detector heard the new string", followed >= 10)
    }

    private fun readsAs(frame: TunerChain.Frame, note: Note) = frame.rawHz > 0f && mapper.nearestNote(frame.rawHz) == note

    private companion object {
        val TOUCHES = listOf("pick-1", "finger-1")

        /** Frames in a row the detector must read the new string for the needle to have to show it. */
        const val HEARD_FRAMES = 5
    }
}

/**
 * [first] plucked, then [second] plucked [delaySeconds] after it over it still ringing, [second]
 * scaled so its first 85 ms are [louderDb] louder (RMS) than [first] is at that moment. Ends when
 * either recording does.
 */
internal class StringChangeMix(first: RealPluck, second: RealPluck, delaySeconds: Float, louderDb: Float) {
    /** Where the second pluck starts in [samples]. */
    val secondOnset: Int = first.onsetSample + (delaySeconds * first.sampleRate).toInt()
    val samples: FloatArray

    init {
        require(first.sampleRate == second.sampleRate) { "${first.name} and ${second.name} differ in rate" }
        val window = first.sampleRate * 85 / 1000
        val firstLevel = SignalGate.rmsDbfs(first.samples.copyOfRange(secondOnset, secondOnset + window))
        val secondLevel = SignalGate.rmsDbfs(second.samples.copyOfRange(second.onsetSample, second.onsetSample + window))
        val gain = 10f.pow((firstLevel + louderDb - secondLevel) / 20f)
        val shift = second.onsetSample - secondOnset
        samples = FloatArray(minOf(first.samples.size, second.samples.size - shift)) { i ->
            first.samples[i] + if (i >= secondOnset) gain * second.samples[i + shift] else 0f
        }
    }
}
