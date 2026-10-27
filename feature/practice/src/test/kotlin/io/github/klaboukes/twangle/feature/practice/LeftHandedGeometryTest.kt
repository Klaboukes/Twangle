package io.github.klaboukes.twangle.feature.practice

import org.junit.Assert.assertEquals
import org.junit.Test

/** Left-handed (Phase 57): the chord chart and the scale neck drawn as a mirror image, and nothing else changes. */
class LeftHandedGeometryTest {

    @Test
    fun `the chart's strings swap sides, the low string on the right`() {
        val right = ChartGeometry(6)
        val left = ChartGeometry(6, mirrored = true)
        for (string in 0 until 6) {
            assertEquals("string $string", right.width - right.stringX(string), left.stringX(string), 1e-4f)
            assertEquals("string $string lies where string ${5 - string} lay", right.stringX(5 - string), left.stringX(string), 1e-4f)
        }
        assertEquals("the low string on the right", right.gridRight, left.stringX(0), 1e-4f)
        assertEquals("the grid itself doesn't move", right.gridLeft to right.gridRight, left.gridLeft to left.gridRight)
        assertEquals("nor does the chart's size", right.width to right.height, left.width to left.height)
    }

    @Test
    fun `the neck is flipped left to right whichever way it lies`() {
        val right = NeckGeometry(6)
        val left = NeckGeometry(6, mirrored = true)
        for (lie in NeckLie.entries) {
            assertEquals("same size $lie", right.width(lie) to right.height(lie), left.width(lie) to left.height(lie))
            for (fret in 0..right.frets) {
                for (string in 0 until 6) {
                    val r = right.point(lie, right.fretMiddle(fret), right.stringLine(string))
                    val l = left.point(lie, left.fretMiddle(fret), left.stringLine(string))
                    assertEquals("x of fret $fret string $string $lie", right.width(lie) - r.x, l.x, 1e-4f)
                    assertEquals("y of fret $fret string $string $lie", r.y, l.y, 1e-4f)
                }
            }
        }
    }

    @Test
    fun `sideways the nut is on the right and the thickest string stays at the bottom`() {
        val left = NeckGeometry(6, mirrored = true)
        val nut = left.point(NeckLie.SIDEWAYS, left.fretLine(0), left.stringLine(0)).x
        val twelfth = left.point(NeckLie.SIDEWAYS, left.fretLine(12), left.stringLine(0)).x
        assertEquals("the nut right of the 12th fret", true, nut > twelfth)
        val thickest = left.point(NeckLie.SIDEWAYS, left.fretMiddle(3), left.stringLine(0)).y
        val thinnest = left.point(NeckLie.SIDEWAYS, left.fretMiddle(3), left.stringLine(5)).y
        assertEquals("the thickest string below the thinnest", true, thickest > thinnest)
    }

    @Test
    fun `upright the thickest string and the fret numbers are on the right, the nut still at the top`() {
        val left = NeckGeometry(6, mirrored = true)
        val thickest = left.point(NeckLie.UPRIGHT, left.fretMiddle(3), left.stringLine(0)).x
        val thinnest = left.point(NeckLie.UPRIGHT, left.fretMiddle(3), left.stringLine(5)).x
        val numbers = left.point(NeckLie.UPRIGHT, left.fretMiddle(3), NeckGeometry.LABEL_MIDDLE).x
        assertEquals("thickest right of thinnest", true, thickest > thinnest)
        assertEquals("the fret numbers right of the thickest string", true, numbers > thickest)
        val nut = left.point(NeckLie.UPRIGHT, left.fretLine(0), left.stringLine(0)).y
        val twelfth = left.point(NeckLie.UPRIGHT, left.fretLine(12), left.stringLine(0)).y
        assertEquals("the nut above the 12th fret", true, nut < twelfth)
    }
}
