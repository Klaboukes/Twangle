package io.github.klaboukes.twangle.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSignsTest {

    @Test
    fun textWithoutSignsIsUnchanged() {
        val text = musicText("E A D G B E")
        assertEquals("E A D G B E", text.text)
        assertTrue(text.spanStyles.isEmpty())
    }

    @Test
    fun sharpAfterALetterIsRaised() {
        val text = musicText("C♯")
        assertEquals("C♯", text.text)
        val span = text.spanStyles.single()
        assertEquals(1, span.start)
        assertEquals(2, span.end)
        assertEquals(RaisedSign, span.item)
    }

    @Test
    fun flatAfterALetterIsRaisedWithItsOwnSize() {
        assertEquals(RaisedFlat, musicText("D♭").spanStyles.single().item)
        assertEquals(RaisedFlat, musicText("Mi♭").spanStyles.single().item)
    }

    @Test
    fun everySignInARowIsSet() {
        val text = musicText("D♯ G♯ C♯ F♯ A♯ D♯")
        assertEquals("D♯ G♯ C♯ F♯ A♯ D♯", text.text)
        assertEquals(listOf(1, 4, 7, 10, 13, 16), text.spanStyles.map { it.start })
        assertTrue(text.spanStyles.all { it.item == RaisedSign })
    }

    @Test
    fun signsInASentenceAreRaisedToo() {
        val text = musicText("Play the C♯3 string")
        assertEquals(RaisedSign, text.spanStyles.single().item)
        assertEquals("♯", text.text.substring(text.spanStyles.single().start, text.spanStyles.single().end))
    }

    @Test
    fun aSignOnItsOwnKeepsItsSize() {
        assertEquals(LoneSign, musicText("♯").spanStyles.single().item)
        assertEquals(LoneSign, musicText("♪").spanStyles.single().item)
        assertEquals(LoneFlat, musicText("♭").spanStyles.single().item)
    }
}
