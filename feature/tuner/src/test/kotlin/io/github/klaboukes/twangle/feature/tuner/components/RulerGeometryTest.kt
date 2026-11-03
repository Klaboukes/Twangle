package io.github.klaboukes.twangle.feature.tuner.components

import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.TunerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RulerGeometryTest {

    private val step = 58f
    private val pointer = 175f

    @Test
    fun `the reading sits at the target note plus its cents`() {
        assertEquals(40f, rulerPosition(40, 0f), 0f)
        assertEquals(40.25f, rulerPosition(40, 25f), 1e-6f)
        assertEquals(39.88f, rulerPosition(40, -12f), 1e-6f)
        // Guitar mode can be far off the string: E2 three semitones sharp sits on G2.
        assertEquals(43f, rulerPosition(40, 300f), 1e-6f)
    }

    @Test
    fun `the heard note is the one nearest the reading`() {
        assertEquals(40, rulerHeardMidi(40f))
        assertEquals(40, rulerHeardMidi(40.49f))
        assertEquals(41, rulerHeardMidi(40.5f))
        assertEquals(40, rulerHeardMidi(39.51f))
        assertEquals(43, rulerHeardMidi(rulerPosition(40, 300f)))
    }

    @Test
    fun `in tune the heard note's mark is under the pointer`() {
        assertEquals(pointer, rulerNoteX(40, rulerPosition(40, 0f), pointer, step), 0f)
        // Its neighbours to each side, pushed a little further out by the zoom round the pointer.
        assertEquals(pointer - step * rulerWarp(1f), rulerNoteX(39, 40f, pointer, step), 1e-3f)
        assertEquals(pointer + step * rulerWarp(1f), rulerNoteX(41, 40f, pointer, step), 1e-3f)
        assertTrue(rulerWarp(1f) in 1.4f..1.5f)
    }

    @Test
    fun `a sharp note slides the scale left, a flat one right`() {
        // 25 cents sharp: the note's mark left of the pointer, so the pointer stands right of it; zoomed, further than plain.
        val sharp = rulerNoteX(40, rulerPosition(40, 25f), pointer, step)
        assertEquals(pointer - step * rulerWarp(0.25f), sharp, 1e-3f)
        assertTrue(sharp < pointer - step / 4f)
        assertEquals(pointer + step * rulerWarp(0.25f), rulerNoteX(40, rulerPosition(40, -25f), pointer, step), 1e-3f)
        // Half way between E and F the pointer is half way between their marks.
        val between = rulerPosition(40, 50f)
        assertEquals(pointer, (rulerNoteX(40, between, pointer, step) + rulerNoteX(41, between, pointer, step)) / 2f, 1e-3f)
    }

    @Test
    fun `the needle zooms in round the pointer, three times as wide there, plain further out, in order`() {
        assertEquals(0f, rulerWarp(0f), 0f)
        // At the pointer a cent moves the scale three times as far as plain.
        assertEquals(3f, rulerWarp(0.001f) / 0.001f, 0.01f)
        // 10 cents off: 0.29 of a step, not 0.1; 2 cents: 0.06.
        assertEquals(0.287f, rulerWarp(0.1f), 0.002f)
        assertEquals(0.06f, rulerWarp(0.02f), 0.002f)
        // A few semitones out the spacing is the plain step again, the scale shifted by the zoom's 0.44.
        assertEquals(1f, rulerWarp(4f) - rulerWarp(3f), 1e-3f)
        // Never out of order, and the same either way.
        var last = rulerWarp(-6f)
        var d = -6f
        while (d <= 6f) {
            val x = rulerWarp(d)
            assertTrue("$d", x >= last)
            assertEquals(-x, rulerWarp(-d), 1e-5f)
            last = x
            d += 0.01f
        }
        // Any place on the scale is placed the same way as the notes' marks.
        assertEquals(rulerNoteX(41, 40.3f, pointer, step), rulerPlaceX(41f, 40.3f, pointer, step), 0f)
    }

    @Test
    fun `the cent marks come in as the note gets within 30 cents, whole within 5`() {
        assertEquals(0f, rulerMarksShown(Float.NaN), 0f)
        assertEquals(0f, rulerMarksShown(30f), 0f)
        assertEquals(0f, rulerMarksShown(-45f), 0f)
        assertEquals(0.5f, rulerMarksShown(-17.5f), 1e-6f)
        assertEquals(1f, rulerMarksShown(5f), 0f)
        assertEquals(1f, rulerMarksShown(0f), 0f)
    }

    @Test
    fun `every note whose name can show is drawn, and few more`() {
        val width = 350f
        for (position in listOf(40f, 40.3f, 40.5f, 40.99f, 63.2f)) {
            val first = rulerFirstMidi(position, width, step)
            val last = rulerLastMidi(position, width, step)
            // The first and last are off the ruler (their names half a step wide may still reach in)…
            assertTrue(rulerNoteX(first, position, width / 2f, step) <= -step / 2f)
            assertTrue(rulerNoteX(last, position, width / 2f, step) >= width + step / 2f)
            // …and only a few semitones are drawn.
            assertTrue(last - first <= width / step + 4)
        }
    }

    @Test
    fun `the heard note is big and its neighbours small, growing and shrinking as the scale slides`() {
        assertEquals(1f, rulerEmphasis(40, 40f), 0f)
        assertEquals(1f, rulerEmphasis(40, 40.2f), 0f)
        assertEquals(0f, rulerEmphasis(41, 40f), 0f)
        assertEquals(0f, rulerEmphasis(39, 40.1f), 0f)
        // Half way both are the same size, between small and big.
        val half = rulerEmphasis(40, 40.5f)
        assertEquals(half, rulerEmphasis(41, 40.5f), 1e-6f)
        assertTrue(half in 0.1f..0.9f)
        // Smaller the further it is.
        var last = 1f
        for (i in 0..20) {
            val e = rulerEmphasis(40, 40f + i * 0.05f)
            assertTrue("$i", e <= last)
            last = e
        }
    }

    @Test
    fun `names fade out at the ruler's ends`() {
        assertEquals(1f, rulerEdgeFade(175f, 350f, 32f), 0f)
        assertEquals(0.5f, rulerEdgeFade(16f, 350f, 32f), 1e-6f)
        assertEquals(0.5f, rulerEdgeFade(334f, 350f, 32f), 1e-6f)
        assertEquals(0f, rulerEdgeFade(-5f, 350f, 32f), 0f)
        assertEquals(0f, rulerEdgeFade(360f, 350f, 32f), 0f)
    }

    @Test
    fun `focus mode keeps the ends clear, then the names fade in`() {
        assertEquals("under the exit button", 0f, rulerEdgeFade(740f, 800f, 32f, clear = 64f), 0f)
        assertEquals("at the clear zone's edge", 0f, rulerEdgeFade(64f, 800f, 32f, clear = 64f), 0f)
        assertEquals(0.5f, rulerEdgeFade(80f, 800f, 32f, clear = 64f), 1e-6f)
        assertEquals(0.5f, rulerEdgeFade(720f, 800f, 32f, clear = 64f), 1e-6f)
        assertEquals(1f, rulerEdgeFade(400f, 800f, 32f, clear = 64f), 0f)
        assertEquals("no fade, a hard edge", 1f, rulerEdgeFade(70f, 800f, 0f, clear = 64f), 0f)
        assertEquals(0f, rulerEdgeFade(60f, 800f, 0f, clear = 64f), 0f)
    }

    @Test
    fun `the names follow the notation setting`() {
        val standard = Tunings.STANDARD
        assertEquals(listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"), rulerNames(Notation.SHARPS, standard, guitar = true))
        assertEquals("D♭", rulerNames(Notation.FLATS, standard, guitar = false)[1])
        assertEquals("Sol♯", rulerNames(Notation.SOLFEGE, standard, guitar = false)[8])
        assertEquals("H", rulerNames(Notation.GERMAN, standard, guitar = false)[11])
        assertEquals("B", rulerNames(Notation.GERMAN, Tunings.HALF_STEP_DOWN, guitar = true)[10])
    }

    @Test
    fun `a string keeps its tuning's spelling, Any note the notation's`() {
        // Half step down is spelled with flats: E♭ A♭ D♭ G♭ B♭ E♭.
        assertEquals("E♭", rulerNames(Notation.SHARPS, Tunings.HALF_STEP_DOWN, guitar = true)[3])
        assertEquals("D♯", rulerNames(Notation.SHARPS, Tunings.HALF_STEP_DOWN, guitar = false)[3])
        assertEquals("Mi♭", rulerNames(Notation.SOLFEGE, Tunings.HALF_STEP_DOWN, guitar = true)[3])
    }

    @Test
    fun `quiet, the ruler rests on a locked string, else the last note, else the first string or A4`() {
        val standard = Tunings.STANDARD
        assertEquals(45, rulerRestMidi(TunerMode.AUTO, standard, locked = 1, lastHeard = 52))
        assertEquals(52, rulerRestMidi(TunerMode.AUTO, standard, locked = null, lastHeard = 52))
        assertEquals(40, rulerRestMidi(TunerMode.AUTO, standard, locked = null, lastHeard = null))
        assertEquals(69, rulerRestMidi(TunerMode.CHROMATIC, standard, locked = null, lastHeard = null))
        assertEquals(61, rulerRestMidi(TunerMode.CHROMATIC, standard, locked = null, lastHeard = 61))
    }
}
