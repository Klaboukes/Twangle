package io.github.klaboukes.twangle.core.dsp.trail

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory.Companion.DEFAULT_FRAME_SECONDS
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory.Companion.NO_NOTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class PitchHistoryTest {

    private val frame = DEFAULT_FRAME_SECONDS
    private val history = PitchHistory()

    /** Pushes [count] readings one frame apart from frame [from] on: note 45 (A2), cents = the frame number / 10. */
    private fun PitchHistory.fill(count: Int, from: Int = 0) {
        for (n in from until from + count) push(n * frame, targetMidi = 45, cents = n / 10f, inTune = n % 2 == 0)
    }

    private fun PitchHistory.entries(within: Float = seconds): List<Triple<Float, Int, Float>> {
        val list = mutableListOf<Triple<Float, Int, Float>>()
        forEachWithin(within) { time, target, cents, _ -> list += Triple(time, target, cents) }
        return list
    }

    @Test
    fun `starts empty`() {
        assertTrue(history.isEmpty)
        assertEquals(0L, history.end)
        assertEquals(emptyList<Triple<Float, Int, Float>>(), history.entries())
        assertEquals(0L, history.firstWithin())
    }

    @Test
    fun `holds five seconds by default, with room to spare`() {
        assertEquals(5f, history.seconds, 0f)
        assertTrue(history.readableEntries * frame >= 5f)
        assertEquals(history.readableEntries + PitchHistory.SLACK_ENTRIES, history.capacity)
        val short = PitchHistory(seconds = 2f)
        assertTrue(short.readableEntries * frame >= 2f)
        assertTrue(short.capacity < history.capacity)
    }

    @Test
    fun `gives back what was pushed, oldest first`() {
        history.fill(10)
        history.pushGap(10 * frame)
        assertFalse(history.isEmpty)
        val entries = history.entries()
        assertEquals(11, entries.size)
        for (n in 0 until 10) {
            assertEquals(n * frame, entries[n].first, 0f)
            assertEquals(45, entries[n].second)
            assertEquals(n / 10f, entries[n].third, 0f)
            assertEquals(n % 2 == 0, history.inTuneAt(n.toLong()))
        }
        assertEquals(NO_NOTE, entries[10].second)
        assertTrue(entries[10].third.isNaN())
        assertTrue(history.isGapAt(10))
        assertFalse(history.isGapAt(9))
        assertFalse(history.inTuneAt(10))
    }

    @Test
    fun `only the last seconds are walked`() {
        history.fill(1_000) // About 21 s.
        val entries = history.entries()
        val newest = 999 * frame
        assertEquals(newest, entries.last().first, 0f)
        assertTrue("reaches back ${newest - entries.first().first} s", newest - entries.first().first <= 5f)
        assertTrue("reaches back ${newest - entries.first().first} s", newest - entries.first().first > 5f - frame)
        // Older than the ring holds: never walked, even if asked for.
        val all = history.entries(within = 1_000f)
        assertEquals(history.readableEntries, all.size)
        // A shorter look back.
        val second = history.entries(within = 1f)
        assertTrue(newest - second.first().first <= 1f)
        assertEquals(1f / frame, second.size.toFloat(), 1.5f)
    }

    @Test
    fun `entries stay right after the ring wraps many times`() {
        history.fill(history.capacity * 7 + 13)
        val end = history.end
        for (index in history.firstReadable(end) until end) {
            assertEquals(index * frame, history.timeAt(index), 1e-3f)
            assertEquals(index / 10f, history.centsAt(index), 1e-3f)
        }
    }

    @Test
    fun `clear forgets everything so far, and new readings follow`() {
        history.fill(100)
        history.clear()
        assertTrue(history.isEmpty)
        assertEquals(emptyList<Triple<Float, Int, Float>>(), history.entries())
        history.fill(3, from = 200)
        assertEquals(listOf(200 / 10f, 201 / 10f, 202 / 10f), history.entries().map { it.third })
        assertEquals(103L, history.end) // Entries keep their numbers.
    }

    @Test
    fun `knows when a stalled reader's entries were written over`() {
        history.fill(50)
        val first = history.firstReadable()
        assertFalse(history.overwritten(first))
        history.fill(PitchHistory.SLACK_ENTRIES, from = 50) // A draw this slow is rare, but must not show garbage.
        assertFalse(history.overwritten(first))
        history.fill(history.capacity, from = 50 + PitchHistory.SLACK_ENTRIES)
        assertTrue(history.overwritten(first))
    }

    @Test
    fun `pushing and walking allocate nothing`() {
        history.fill(history.capacity * 2) // Warm up.
        var sum = 0f
        history.forEachWithin { _, _, cents, _ -> sum += cents }
        var n = 0
        val pushing = allocatedBytes {
            repeat(10_000) {
                history.push(n * frame, 45, n / 10f, inTune = true)
                history.pushGap(n * frame)
                n++
            }
        }
        assertEquals("bytes allocated by 20,000 pushes", 0L, pushing)
        val walking = allocatedBytes {
            repeat(100) { history.forEachWithin { time, _, cents, _ -> if (!cents.isNaN()) sum += cents + time } }
        }
        assertEquals("bytes allocated by 100 walks", 0L, walking)
        assertTrue(sum != 0f)
    }

    @Test
    fun `a reader on another thread sees whole entries only`() {
        // The writer runs many times faster than the tuner (one reading a millisecond instead of
        // one per 21 ms); every entry it writes has cents = time × 10, so a torn entry would show.
        val stop = AtomicBoolean(false)
        val walks = AtomicInteger(0)
        val writer = thread {
            var n = 0
            while (!stop.get()) {
                val time = n * 0.001f
                if (n % 50 == 49) history.pushGap(time) else history.push(time, 45, time * 10f)
                n++
                Thread.sleep(1)
            }
        }
        val failures = mutableListOf<String>()
        val until = System.nanoTime() + 300_000_000L
        while (System.nanoTime() < until) {
            val end = history.end
            val first = history.firstWithin(end = end)
            val seen = mutableListOf<Pair<Float, Float>>()
            for (index in first until end) {
                if (!history.isGapAt(index)) seen += history.timeAt(index) to history.centsAt(index)
            }
            if (history.overwritten(first)) continue // The reader stalled: it would skip this frame.
            walks.incrementAndGet()
            for ((time, cents) in seen) {
                if (kotlin.math.abs(cents - time * 10f) > 1e-3f) failures += "time $time, cents $cents"
            }
            for (i in 1 until seen.size) {
                if (seen[i].first < seen[i - 1].first) failures += "time went back: ${seen[i - 1].first} → ${seen[i].first}"
            }
        }
        stop.set(true)
        writer.join()
        assertTrue("walked only ${walks.get()} times", walks.get() > 10)
        assertEquals(emptyList<String>(), failures.take(5))
    }
}
