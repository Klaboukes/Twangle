package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchCentsTest {

    @Test
    fun `the words after the number keep their space`() {
        assertEquals("" to " cents flat", centsWords("12 cents flat", "12"))
    }

    @Test
    fun `words before the number stay before it`() {
        assertEquals("حوالي " to " سنت", centsWords("حوالي ١٢ سنت", "١٢"))
    }

    @Test
    fun `a text without the number keeps all its words after it`() {
        assertEquals("" to " cents", centsWords("cents", "12"))
    }

    @Test
    fun `flat and sharp say which way, in tune and right on the note only how far`() {
        val e2 = Note.of(PitchClass.E, 2)
        assertEquals(CentsKind.FLAT, CentsKind.of(WatchTunerState.Detected(e2, -12)))
        assertEquals(CentsKind.SHARP, CentsKind.of(WatchTunerState.Detected(e2, 3)))
        assertEquals(CentsKind.AMOUNT, CentsKind.of(WatchTunerState.Detected(e2, 0)))
        assertEquals(CentsKind.AMOUNT, CentsKind.of(WatchTunerState.Detected(e2, -2, inTune = true)))
    }
}
