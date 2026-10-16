package io.github.klaboukes.twangle.core.dsp.intonation

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln
import kotlin.math.log2

class SaddleMoveTest {

    @Test
    fun `about 2·7 cents per millimetre on a 648 mm scale`() {
        assertEquals(1f, SaddleMove.millimetres(2.67f, 648f), 0.01f)
        assertEquals(3f, SaddleMove.millimetres(8.01f, 648f), 0.05f)
        // A shorter scale needs less saddle for the same cents.
        assertTrue(SaddleMove.millimetres(8f, 628f) < SaddleMove.millimetres(8f, 648f))
    }

    @Test
    fun `sharp moves back, flat moves forward, the octave doesn't move`() {
        assertTrue(SaddleMove.millimetres(6f, 648f) > 0f)
        assertTrue(SaddleMove.millimetres(-6f, 648f) < 0f)
        assertEquals(0f, SaddleMove.millimetres(0f, 648f), 0f)
    }

    @Test
    fun `the move puts the 12th fret back on the octave`() {
        val l = 648.0
        for (c in listOf(-40f, -12f, -4f, 4f, 12f, 40f)) {
            val d = SaddleMove.millimetres(c, l.toFloat())
            // After the move the 12th fret is 1200·log2((L + d) / (L + 2d)) from the octave, which cancels c.
            val after = c + 1200.0 * log2((l + d) / (l + 2 * d))
            assertEquals("$c cents", 0.0, after, 0.01)
        }
    }

    @Test
    fun `small moves follow the linear rule of thumb`() {
        // d ≈ c · L · ln2 / 1200 for small c.
        val l = 648f
        assertEquals(4f * l * ln(2f) / 1200f, SaddleMove.millimetres(4f, l), 0.02f)
    }

    @Test
    fun `shown to the nearest half millimetre, and less than half a millimetre as 0`() {
        val l = 648f
        fun cents(mm: Float) = (1200.0 * log2((l + 2 * mm) / (l + mm).toDouble())).toFloat()
        assertEquals(3f, SaddleMove.shownMillimetres(cents(3.1f), l), 0f)
        assertEquals(1f, SaddleMove.shownMillimetres(cents(1.2f), l), 0f)
        assertEquals(1.5f, SaddleMove.shownMillimetres(cents(1.3f), l), 0f)
        assertEquals(0.5f, SaddleMove.shownMillimetres(cents(0.5f), l), 0f)
        assertEquals(0f, SaddleMove.shownMillimetres(cents(0.45f), l), 0f)
        // The size only: forward shows the same figure.
        assertEquals(3f, SaddleMove.shownMillimetres(-cents(3.1f), l), 0f)
    }

    @Test
    fun `the smallest move the check asks for is about a millimetre on a guitar`() {
        val justOff = Intonation.DEFAULT_TOLERANCE_CENTS + 0.01f
        assertEquals(1f, SaddleMove.shownMillimetres(justOff, SaddleMove.GUITAR_SCALE_MM), 0f)
    }

    @Test
    fun `the guitar family's scale lengths change a 3 mm answer by less than the rounding step`() {
        // 24″ (610 mm) short scales to 27″ (686 mm): the move for the same cents differs by under 0.5 mm.
        val cents = 8f
        val shown = SaddleMove.millimetres(cents, SaddleMove.GUITAR_SCALE_MM)
        for (scale in listOf(610f, 628f, 650f, 686f)) {
            val real = SaddleMove.millimetres(cents, scale)
            assertTrue("$scale mm: $real vs $shown", kotlin.math.abs(real - shown) < SaddleMove.STEP_MM)
        }
    }

    @Test
    fun `bad input is refused`() {
        assertThrows(IllegalArgumentException::class.java) { SaddleMove.millimetres(5f, 0f) }
        assertThrows(IllegalArgumentException::class.java) { SaddleMove.millimetres(5f, Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { SaddleMove.millimetres(1200f, 648f) }
        assertThrows(IllegalArgumentException::class.java) { SaddleMove.millimetres(Float.NaN, 648f) }
    }

    @Test
    fun `offered on fretted instruments only, millimetres on guitars only`() {
        val offered = Instruments.all.filter(IntonationInstruments::offers).map { it.id }
        assertEquals(listOf("guitar", "guitar_7", "guitar_8", "bass_4", "bass_5", "ukulele", "mandolin"), offered)
        for (fretless in listOf(Instruments.VIOLIN, Instruments.VIOLA, Instruments.CELLO)) {
            assertFalse(fretless.id, IntonationInstruments.offers(fretless))
        }
        for (guitar in listOf(Instruments.GUITAR, Instruments.GUITAR_7, Instruments.GUITAR_8)) {
            assertEquals(SaddleMove.GUITAR_SCALE_MM, IntonationInstruments.scaleLengthMm(guitar))
        }
        for (other in listOf(Instruments.BASS_4, Instruments.BASS_5, Instruments.UKULELE, Instruments.MANDOLIN, Instruments.VIOLIN)) {
            assertNull(other.id, IntonationInstruments.scaleLengthMm(other))
        }
    }
}
