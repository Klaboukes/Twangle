package io.github.klaboukes.twangle.feature.tuner.components

import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.pitch.Clarity
import io.github.klaboukes.twangle.core.dsp.pitch.PitchChain
import io.github.klaboukes.twangle.core.dsp.recordings.WavFile
import io.github.klaboukes.twangle.core.dsp.speech.SpokenReadingKey
import io.github.klaboukes.twangle.core.dsp.speech.SpokenReadingThrottle
import io.github.klaboukes.twangle.feature.tuner.PitchReading
import io.github.klaboukes.twangle.feature.tuner.TuneWord
import io.github.klaboukes.twangle.feature.tuner.TunerInterpreter
import io.github.klaboukes.twangle.feature.tuner.TunerOptions
import io.github.klaboukes.twangle.feature.tuner.TunerUiState
import io.github.klaboukes.twangle.feature.tuner.tuneWordFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * What TalkBack would say after a real pluck, and when (Phase 94): the 30 real recordings of `:core:dsp`'s
 * fixtures (docs/architecture/testing.md, *Real recordings*: the owner's guitar, tuned, six strings, a pick and a
 * finger, a quiet room and a TV on), each heard as if the guitar were detuned −25 to +25 cents (A4 moved, which moves
 * every target), 1,530 plucks. Each goes through the tuner as on the phone, frame by frame: [PitchChain] →
 * [TunerInterpreter] (the smoothing, the string, the word held steady by `SteadyHint`) → the word under the lens
 * ([tuneWordFor]) → the reading's situation ([spokenReadingKey]) → [SpokenReadingThrottle], timed by the frames, as
 * `PacedSpeech` runs it on the clock.
 *
 * It decides how soon the phone may say a reading ([SpokenReadingThrottle.HELD_WORD_SETTLE_MILLIS]): sooner, but never
 * the pluck's first wobble (the attack's word, then the settled one, two readings in its first second), and never the
 * note and cents alone a moment before the word.
 */
class SpokenPluckTest {

    /** One recorded pluck's raw readings, one a hop. */
    private class Take(val name: String, val hopSeconds: Float, val readings: List<PitchReading>)

    /** What one pluck had said: when (ms after its first reading) and in which situation. */
    private class Said(val name: String, val offsetCents: Int, val readings: List<Pair<Long, SpokenReadingKey>>) {
        val wordless: Boolean get() = readings.any { it.second.side == null }
        val twoInFirstSecond: Boolean get() = readings.count { it.first < FIRST_SECOND_MS } >= 2
        val firstWordMs: Long? get() = readings.firstOrNull { it.second.side != null }?.first
        override fun toString() = "$name at $offsetCents cents: ${readings.map { "${it.second.side}@${it.first}" }}"
    }

    @Test
    fun `no reading is said before its word`() {
        val wordless = sayAll(::phone).filter { it.wordless }
        assertTrue("said without the word: ${wordless.take(5)}", wordless.isEmpty())
        // Before Phase 94 some were: the note and cents alone, then the word a moment later.
        assertTrue(sayAll(::before).count { it.wordless } > 0)
    }

    @Test
    fun `the phone says the word sooner, and no more plucks say two readings in their first second`() {
        val now = sayAll(::phone)
        val then = sayAll(::before)
        val twiceNow = now.count { it.twoInFirstSecond }
        val twiceThen = then.count { it.twoInFirstSecond }
        val soonerMs = meanFirstWordMs(then) - meanFirstWordMs(now)
        println(
            "Phase 94 pacing on ${now.size} plucks: the first word ${meanFirstWordMs(then).roundToInt()} → " +
                "${meanFirstWordMs(now).roundToInt()} ms after the first reading; two readings in the first second: " +
                "$twiceThen → $twiceNow",
        )
        assertTrue("two readings in the first second: $twiceNow now, $twiceThen before", twiceNow <= twiceThen)
        assertTrue("the first word ${soonerMs.roundToInt()} ms sooner", soonerMs >= 80.0)
    }

    /** Why not lower: a pluck's attack holds its word about that long, so the wobble would be said. */
    @Test
    fun `a little lower would say the pluck's first wobble`() {
        val lower = sayAll { SpokenReadingThrottle(readingSettleMillis = 250) }
        val twiceLower = lower.count { it.twoInFirstSecond }
        val twiceNow = sayAll(::phone).count { it.twoInFirstSecond }
        assertTrue("250 ms: $twiceLower plucks say two readings in their first second, 300 ms: $twiceNow", twiceLower > 3 * twiceNow)
    }

    @Test
    fun `every recording is heard`() {
        assertEquals(30, takes.size)
        assertTrue(sayAll(::phone).all { it.readings.isNotEmpty() })
    }

    /** As the phone paces its readings (`PacedSpeech`). */
    private fun phone() = SpokenReadingThrottle(readingSettleMillis = SpokenReadingThrottle.HELD_WORD_SETTLE_MILLIS)

    /** As it paced them before Phase 94: 400 ms, a reading without its word too. */
    private fun before() = SpokenReadingThrottle(readingSettleMillis = 400, unsettledMillis = 400)

    private fun meanFirstWordMs(said: List<Said>): Double = said.mapNotNull { it.firstWordMs }.average()

    /** Every take at every detuning, each with a fresh throttle from [throttle]. */
    private fun sayAll(throttle: () -> SpokenReadingThrottle): List<Said> =
        takes.flatMap { take -> OFFSETS.map { say(take, it, throttle()) } }

    private fun say(take: Take, offsetCents: Int, throttle: SpokenReadingThrottle): Said {
        // A4 that many cents higher: every string's target too, so the guitar sounds that much flat.
        val options = TunerOptions(a4Hz = NoteMapper.DEFAULT_A4_HZ * 2f.pow(offsetCents / 1200f))
        val interpreter = TunerInterpreter(take.hopSeconds)
        var held = TuneWord.PLAY
        val said = mutableListOf<Pair<Long, SpokenReadingKey>>()
        var firstReadingMs = -1L
        val frames = take.readings.mapIndexed { i, reading ->
            val atMs = (i * take.hopSeconds * 1_000).roundToInt().toLong()
            when (val state = interpreter.interpret(reading, options)) {
                is TunerUiState.Detected -> {
                    // As the word line holds it: a hint not settled yet keeps the word before.
                    val word = tuneWordFor(heard = true, hint = state.hint, chromatic = false, previous = held)
                    held = word
                    val readout = NoteReadout(state.noteName, state.octave, state.cents, state.inTune, state.hint)
                    val key = spokenReadingKey(readout, word, tip = state.tip?.name)
                    Frame(atMs, "$key ${state.cents.roundToInt()}", key)
                }
                is TunerUiState.Listening -> {
                    held = TuneWord.PLAY
                    Frame(atMs, state.tip?.name ?: "Play a string", null)
                }
                else -> error("unexpected $state")
            }
        }
        // Each frame's text stands until the next frame; the throttle's wait runs between them, as on the clock.
        for ((i, frame) in frames.withIndex()) {
            if (frame.key != null && firstReadingMs < 0) firstReadingMs = frame.atMs
            val nextMs = frames.getOrNull(i + 1)?.atMs ?: Long.MAX_VALUE
            val wait = throttle.delayFor(frame.text, frame.key, frame.atMs) ?: continue
            if (frame.atMs + wait < nextMs) {
                throttle.publish(frame.text, frame.key, frame.atMs + wait)
                if (frame.key != null) said += (frame.atMs + wait - firstReadingMs) to frame.key
            }
        }
        return Said(take.name, offsetCents, said)
    }

    private class Frame(val atMs: Long, val text: String, val key: SpokenReadingKey?)

    private companion object {
        val OFFSETS = -25..25
        const val FIRST_SECOND_MS = 1_000L

        /** The fixtures of `:core:dsp` (Gradle runs unit tests in the module's folder; an input of this task). */
        val takes: List<Take> by lazy {
            val files = File("../../core/dsp/src/test/resources/fixtures").listFiles { file -> file.name.endsWith(".wav") }
            checkNotNull(files) { "no recordings in core/dsp/src/test/resources/fixtures" }
            files.sorted().map { file -> take(file.name.removeSuffix(".wav"), WavFile.read(file.inputStream())) }
        }

        /** The readings [PitchChain] makes of [wav] at its own rate, as `PitchPipeline` hands them on. */
        fun take(name: String, wav: WavFile): Take {
            val chain = PitchChain(wav.sampleRate)
            val readings = mutableListOf<PitchReading>()
            var at = 0
            while (at < wav.samples.size) {
                at += chain.push(wav.samples, at, wav.samples.size - at)
                if (chain.analyse()) {
                    val pitch = chain.pitch
                    readings += PitchReading(
                        pitch.frequencyHz, pitch.confidence, chain.levelDbfs, chain.gateOpen, chain.noiseFloorDbfs,
                        chain.snrDb, Clarity.of(pitch.confidence), chain.peakDbfs, chain.sustained,
                    )
                }
            }
            return Take(name, chain.hopSeconds, readings)
        }
    }
}
