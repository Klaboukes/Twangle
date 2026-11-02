package io.github.klaboukes.twangle.feature.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The word under the lens (Phase 71): which word a reading asks for, with strings and in Any note. */
class TuneWordTest {

    @Test
    fun `nothing heard asks to play`() {
        for (chromatic in listOf(false, true)) {
            for (hint in listOf(null, TunerHint.IN_TUNE, TunerHint.TUNE_UP, TunerHint.TUNE_DOWN)) {
                assertEquals(TuneWord.PLAY, tuneWordFor(heard = false, hint = hint, chromatic = chromatic, previous = TuneWord.TIGHTEN))
            }
        }
    }

    @Test
    fun `with strings a flat string is tightened and a sharp one loosened`() {
        assertEquals(TuneWord.TIGHTEN, tuneWordFor(heard = true, hint = TunerHint.TUNE_UP, chromatic = false))
        assertEquals(TuneWord.LOOSEN, tuneWordFor(heard = true, hint = TunerHint.TUNE_DOWN, chromatic = false))
        assertEquals(TuneWord.IN_TUNE, tuneWordFor(heard = true, hint = TunerHint.IN_TUNE, chromatic = false))
    }

    @Test
    fun `in Any note there is no string to turn, so it says too low or too high`() {
        assertEquals(TuneWord.TOO_LOW, tuneWordFor(heard = true, hint = TunerHint.TUNE_UP, chromatic = true))
        assertEquals(TuneWord.TOO_HIGH, tuneWordFor(heard = true, hint = TunerHint.TUNE_DOWN, chromatic = true))
        assertEquals(TuneWord.IN_TUNE, tuneWordFor(heard = true, hint = TunerHint.IN_TUNE, chromatic = true))
    }

    @Test
    fun `a heard note whose hint has not settled keeps the word it had`() {
        assertEquals(TuneWord.PLAY, tuneWordFor(heard = true, hint = null, chromatic = false))
        assertEquals(TuneWord.PLAY, tuneWordFor(heard = true, hint = null, chromatic = false, previous = TuneWord.PLAY))
        assertEquals(TuneWord.TIGHTEN, tuneWordFor(heard = true, hint = null, chromatic = false, previous = TuneWord.TIGHTEN))
        assertEquals(TuneWord.IN_TUNE, tuneWordFor(heard = true, hint = null, chromatic = true, previous = TuneWord.IN_TUNE))
        // In the mode's own words, should the mode have changed.
        assertEquals(TuneWord.TOO_LOW, tuneWordFor(heard = true, hint = null, chromatic = true, previous = TuneWord.TIGHTEN))
        assertEquals(TuneWord.LOOSEN, tuneWordFor(heard = true, hint = null, chromatic = false, previous = TuneWord.TOO_HIGH))
    }

    @Test
    fun `only the four directions say which way`() {
        assertEquals(setOf(TuneWord.TIGHTEN, TuneWord.LOOSEN, TuneWord.TOO_LOW, TuneWord.TOO_HIGH), TuneWord.entries.filter { it.isDirection }.toSet())
    }

    @Test
    fun `a pluck coming in flat goes from play straight to tighten`() {
        val words = wordsFor(List(40) { -14f })
        assertEquals(TuneWord.PLAY, words.first())
        assertEquals(TuneWord.TIGHTEN, words.last())
        assertEquals(listOf(TuneWord.PLAY, TuneWord.TIGHTEN), words.distinctInRow())
    }

    @Test
    fun `a string tuned up from flat to in tune never says loosen`() {
        // From 20 cents flat to the note over a second, then held: Tighten, then In tune, nothing between.
        val cents = List(47) { -20f + 20f * it / 46f } + List(30) { 0.4f }
        val words = wordsFor(cents)
        assertEquals(listOf(TuneWord.PLAY, TuneWord.TIGHTEN, TuneWord.IN_TUNE), words.distinctInRow())
    }

    @Test
    fun `a reading wobbling round the target does not flicker`() {
        // Starting sharp, then wobbling across the note, ±2 cents, frame to frame.
        val cents = List(20) { 9f } + List(60) { if (it % 2 == 0) 2f else -2f }
        val words = wordsFor(cents)
        val changes = words.distinctInRow()
        assertFalse(changes.toString(), TuneWord.TIGHTEN in changes)
        assertTrue(changes.toString(), changes.size <= 3)
    }

    @Test
    fun `in Any note the same reading says too low`() {
        val words = wordsFor(List(40) { -14f }, chromatic = true)
        assertEquals(listOf(TuneWord.PLAY, TuneWord.TOO_LOW), words.distinctInRow())
    }

    /** The word shown frame by frame for [cents] (47 frames a second), through the tuner's own cue and its steady line. */
    private fun wordsFor(cents: List<Float>, chromatic: Boolean = false): List<TuneWord> {
        val cue = TuningCue()
        val line = SteadyHint<TunerHint>()
        var previous: TuneWord? = null
        val words = mutableListOf(tuneWordFor(heard = false, hint = null, chromatic = chromatic))
        for (c in cents) {
            val hint = line.update(cue.update(c, FRAME_SECONDS), FRAME_SECONDS)
            val word = tuneWordFor(heard = true, hint = hint, chromatic = chromatic, previous = previous)
            previous = word
            words += word
        }
        return words
    }

    private fun List<TuneWord>.distinctInRow(): List<TuneWord> = fold(mutableListOf()) { acc, w -> if (acc.lastOrNull() != w) acc += w; acc }

    private companion object {
        const val FRAME_SECONDS = 1f / 47f
    }
}
