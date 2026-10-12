package io.github.klaboukes.twangle.feature.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteadyHintTest {

    /** One hop at 48 kHz, as on the phone. */
    private val frame = 1024f / 48_000f

    private enum class Message(override val settleSeconds: Float, override val minShowSeconds: Float) : SteadyMessage {
        QUICK(0f, 0.5f),
        SLOW(0.2f, 0.5f),
        LONG(0f, 1.5f),
    }

    private val hint = SteadyHint<Message>()

    /** Feeds [wanted] for [frames] frames and returns what is shown after the last one. */
    private fun feed(wanted: Message?, frames: Int): Message? {
        var shown: Message? = null
        repeat(frames) { shown = hint.update(wanted, frame) }
        return shown
    }

    @Test
    fun `starts empty and shows a message without settle time at once`() {
        assertNull(hint.shown)
        assertEquals(Message.QUICK, hint.update(Message.QUICK, frame))
    }

    @Test
    fun `a message shows once it has been wanted for its settle time`() {
        // 0.2 s is 9.4 frames: shown on the 10th.
        assertNull(feed(Message.SLOW, 9))
        assertEquals(Message.SLOW, feed(Message.SLOW, 1))
    }

    @Test
    fun `a blip shorter than the settle time never shows`() {
        feed(Message.QUICK, 30)
        repeat(20) {
            hint.update(Message.SLOW, frame)
            assertEquals(Message.QUICK, hint.update(Message.QUICK, frame))
        }
        // Interrupted, the settle time starts over.
        feed(Message.SLOW, 5)
        hint.update(Message.QUICK, frame)
        assertEquals(Message.QUICK, feed(Message.SLOW, 9))
        assertEquals(Message.SLOW, feed(Message.SLOW, 1))
    }

    @Test
    fun `a shown message stays for its minimum time`() {
        hint.update(Message.QUICK, frame)
        // 0.5 s is 23.4 frames: the 24th frame after it showed may change it.
        repeat(23) { assertEquals(Message.QUICK, hint.update(Message.LONG, frame)) }
        assertEquals(Message.LONG, hint.update(Message.LONG, frame))
        // LONG stays 1.5 s (70.3 frames), even when nothing is wanted.
        assertEquals(Message.LONG, feed(null, 70))
        assertNull(feed(null, 1))
    }

    @Test
    fun `a change waiting for the minimum time is dropped once it is no longer wanted`() {
        hint.update(Message.QUICK, frame)
        feed(Message.LONG, 10)
        assertEquals(Message.QUICK, feed(Message.QUICK, 30))
    }

    @Test
    fun `a message that changes every frame changes the line at most once per minimum time`() {
        var changes = 0
        var last: Message? = null
        val frames = (3f / frame).toInt()
        for (i in 0 until frames) {
            val shown = hint.update(if (i % 2 == 0) Message.QUICK else null, frame)
            if (shown != last) changes++
            last = shown
        }
        // 3 s at no more than one change per 0.5 s.
        assertTrue("changed $changes times", changes <= 7)
    }

    @Test
    fun `an empty line after a message stays for the minimum time too`() {
        hint.update(Message.QUICK, frame)
        assertNull(feed(null, 24))
        repeat(23) { assertNull(hint.update(Message.QUICK, frame)) }
        assertEquals(Message.QUICK, hint.update(Message.QUICK, frame))
    }

    @Test
    fun `reset clears the line at once and the next message shows straight away`() {
        hint.update(Message.LONG, frame)
        hint.reset()
        assertNull(hint.shown)
        assertEquals(Message.QUICK, hint.update(Message.QUICK, frame))
    }
}
