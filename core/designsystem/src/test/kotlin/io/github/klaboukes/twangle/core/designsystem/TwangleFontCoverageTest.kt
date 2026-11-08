package io.github.klaboukes.twangle.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/**
 * Nunito's character map leaves out every Greek letter. The face drew only Δ Σ Ω μ π (as maths signs),
 * so in a Greek word those few came from Nunito and the rest from the phone's font, a lighter letter in
 * the middle of a word (Phase 87). Without them the phone's font draws whole Greek words.
 */
class TwangleFontCoverageTest {

    // Gradle runs unit tests in the module folder.
    private val font = File("src/main/res/font/nunito.ttf")

    @Test
    fun nunitoMapsNoGreekLetter() {
        val greek = mappedCodePoints().filter { it in 0x370..0x3FF }
        assertEquals("Greek code points mapped: ${greek.map { "U+%04X".format(it) }}", emptyList<Int>(), greek)
    }

    @Test
    fun nunitoStillMapsLatinAndCyrillic() {
        val mapped = mappedCodePoints()
        assertTrue("A", 'A'.code in mapped)
        assertTrue("Ж", 'Ж'.code in mapped)
        assertTrue("ő", 'ő'.code in mapped)
    }

    /** Every code point a format 4 subtable of the font's cmap maps to a glyph (the font has only those). */
    private fun mappedCodePoints(): Set<Int> {
        val bytes = ByteBuffer.wrap(font.readBytes())
        val tables = bytes.getShort(4).toInt() and 0xFFFF
        val cmap = (0 until tables).map { 12 + 16 * it }
            .first { String(ByteArray(4) { i -> bytes.get(it + i) }, Charsets.US_ASCII) == "cmap" }
            .let { bytes.getInt(it + 8) }
        val subtables = bytes.getShort(cmap + 2).toInt() and 0xFFFF
        val mapped = mutableSetOf<Int>()
        for (k in 0 until subtables) {
            val at = cmap + bytes.getInt(cmap + 4 + 8 * k + 4)
            if ((bytes.getShort(at).toInt() and 0xFFFF) != 4) continue
            val segments = (bytes.getShort(at + 6).toInt() and 0xFFFF) / 2
            val ends = at + 14
            val starts = ends + 2 * segments + 2
            val deltas = starts + 2 * segments
            val offsets = deltas + 2 * segments
            for (s in 0 until segments) {
                val start = bytes.getShort(starts + 2 * s).toInt() and 0xFFFF
                val end = bytes.getShort(ends + 2 * s).toInt() and 0xFFFF
                val delta = bytes.getShort(deltas + 2 * s).toInt()
                val offset = bytes.getShort(offsets + 2 * s).toInt() and 0xFFFF
                for (c in start..end) {
                    if (c == 0xFFFF) continue
                    val glyph = if (offset == 0) {
                        (c + delta) and 0xFFFF
                    } else {
                        val g = bytes.getShort(offsets + 2 * s + offset + 2 * (c - start)).toInt() and 0xFFFF
                        if (g == 0) 0 else (g + delta) and 0xFFFF
                    }
                    if (glyph != 0) mapped += c
                }
            }
        }
        return mapped
    }
}
