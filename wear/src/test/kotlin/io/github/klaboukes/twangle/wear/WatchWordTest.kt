package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchWordTest {

    private val e2 = Note.of(PitchClass.E, 2)
    private val a2 = Note.of(PitchClass.A, 2)

    private fun reading(cents: Int, inTune: Boolean = false, note: Note = e2) = WatchTunerState.Detected(note, cents, inTune)

    @Test
    fun `flat tightens, sharp loosens, in tune says so`() {
        assertEquals(WatchWord.TIGHTEN, WatchWord.of(reading(-12), null))
        assertEquals(WatchWord.LOOSEN, WatchWord.of(reading(9), null))
        assertEquals(WatchWord.IN_TUNE, WatchWord.of(reading(2, inTune = true), WatchWord.TIGHTEN))
    }

    @Test
    fun `near the note the word keeps its direction, so it doesn't swap while the reading wobbles`() {
        assertEquals(WatchWord.TIGHTEN, WatchWord.of(reading(2), WatchWord.TIGHTEN))
        assertEquals(WatchWord.TIGHTEN, WatchWord.of(reading(5), WatchWord.TIGHTEN))
        assertEquals(WatchWord.LOOSEN, WatchWord.of(reading(-5), WatchWord.LOOSEN))
        // Clearly on the other side it turns.
        assertEquals(WatchWord.LOOSEN, WatchWord.of(reading(6), WatchWord.TIGHTEN))
        assertEquals(WatchWord.TIGHTEN, WatchWord.of(reading(-6), WatchWord.LOOSEN))
    }

    @Test
    fun `near the note with nothing to keep there is no word yet`() {
        assertNull(WatchWord.of(reading(0), null))
        assertNull(WatchWord.of(reading(3), WatchWord.IN_TUNE))
    }

    @Test
    fun `the held word follows one note and starts afresh for another`() {
        val held = HeldWatchWord()
        assertEquals(WatchWord.TIGHTEN, held.next(reading(-12)))
        assertEquals(WatchWord.TIGHTEN, held.next(reading(3)))
        assertNull(held.next(reading(3, note = a2)))
        assertEquals(WatchWord.LOOSEN, held.next(reading(8, note = a2)))
        assertNull(held.next(null))
        assertNull(held.next(reading(2, note = a2)))
    }

    /** The screen may ask twice for one reading; the answer mustn't change. */
    @Test
    fun `asking again for the same reading gives the same word`() {
        for (previous in listOf(null, WatchWord.TIGHTEN, WatchWord.LOOSEN, WatchWord.IN_TUNE)) {
            for (cents in -10..10) {
                for (inTune in listOf(false, true)) {
                    val once = WatchWord.of(reading(cents, inTune), previous)
                    assertEquals(once, WatchWord.of(reading(cents, inTune), once))
                }
            }
        }
    }
}
