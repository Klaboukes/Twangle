package io.github.klaboukes.twangle.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

/** [transferFully], the loop behind the recorder's reads and the players' `AudioTrack` writes. */
class BlockingTransferTest {

    private class Stalled : Exception()
    private class Failed(val code: Int) : Exception()

    /** Runs a transfer of [count] whose steps move [moves] in turn (then everything left). */
    private fun transfer(count: Int, vararg moves: Int): Pair<List<Int>, Int> {
        val asked = mutableListOf<Int>()
        var pauses = 0
        transferFully(
            count = count,
            pause = { pauses++ },
            onError = { throw Failed(it) },
            onStalled = { throw Stalled() },
        ) { done ->
            asked += done
            moves.getOrElse(asked.size - 1) { count - done }
        }
        return asked to pauses
    }

    @Test
    fun `partial steps continue where the last one stopped`() {
        val (asked, pauses) = transfer(1000, 300, 0, 200)
        assertEquals(listOf(0, 300, 300, 500), asked)
        assertEquals(1, pauses)
    }

    @Test(expected = Failed::class)
    fun `an error code ends the transfer`() {
        transfer(1000, 300, -32)
    }

    @Test
    fun `the error code is passed on`() {
        val code = runCatching { transfer(1000, -6) }.exceptionOrNull() as Failed
        assertEquals(-6, code.code)
    }

    @Test(expected = Stalled::class)
    fun `steps that keep moving nothing stall the transfer instead of spinning`() {
        // An AudioTrack the system stopped: every write takes nothing.
        transfer(480, *IntArray(MAX_EMPTY_STEPS))
    }

    @Test
    fun `empty steps only count in a row`() {
        val moves = IntArray(MAX_EMPTY_STEPS - 1) + intArrayOf(1) + IntArray(MAX_EMPTY_STEPS - 1)
        val (_, pauses) = transfer(480, *moves)
        assertEquals(2 * (MAX_EMPTY_STEPS - 1), pauses)
    }

    @Test
    fun `nothing to move takes no step`() {
        assertEquals(emptyList<Int>(), transfer(0).first)
    }
}
