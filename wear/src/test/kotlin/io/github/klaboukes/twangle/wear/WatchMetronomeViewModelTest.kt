package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.audio.FakeMetronomePlayer
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class WatchMetronomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** The kept choices in memory; [released] holds them back until completed, as a slow file would. */
    private class FakeStore(
        initial: WatchMetronomeChoices = WatchMetronomeChoices.DEFAULT,
        val released: CompletableDeferred<Unit> = CompletableDeferred(Unit),
    ) : WatchMetronomeStore {
        val kept = MutableStateFlow(initial)
        var tempoWrites = 0

        override val choices: Flow<WatchMetronomeChoices> = flow {
            released.await()
            emitAll(kept)
        }

        override suspend fun setTempo(bpm: Int) {
            tempoWrites++
            kept.value = kept.value.copy(bpm = bpm)
        }

        override suspend fun setClick(click: Boolean) {
            kept.value = kept.value.copy(click = click)
        }
    }

    private val player = FakeMetronomePlayer()

    private fun viewModel(store: WatchMetronomeStore = FakeStore(), taps: WatchBeatTaps? = null) =
        WatchMetronomeViewModel(player, store, taps)

    @Test
    fun `opens at the phone's tempo with taps only before anything is kept`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(WatchMetronomeState(bpm = 120, playing = false, click = false), vm.state.value)
        assertTrue("the clicks are silent", player.silent)
    }

    @Test
    fun `opens at the kept tempo and click`() = runTest(dispatcher) {
        val vm = viewModel(FakeStore(WatchMetronomeChoices(bpm = 96, click = true)))
        advanceUntilIdle()

        assertEquals(WatchMetronomeState(bpm = 96, playing = false, click = true), vm.state.value)
        assertFalse("the speaker clicks", player.silent)
    }

    @Test
    fun `the bezel turns the tempo 1 BPM a step, within 30 to 250`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.turn(1)
        vm.turn(3)
        runCurrent()
        assertEquals(124, vm.state.value.bpm)
        vm.turn(-2)
        runCurrent()
        assertEquals(122, vm.state.value.bpm)
        vm.turn(500)
        runCurrent()
        assertEquals(250, vm.state.value.bpm)
        vm.turn(-1_000)
        runCurrent()
        assertEquals(30, vm.state.value.bpm)
    }

    @Test
    fun `play starts at the tempo in four four and stop stops`() = runTest(dispatcher) {
        val vm = viewModel(FakeStore(WatchMetronomeChoices(bpm = 96, click = false)))
        advanceUntilIdle()

        vm.togglePlaying()
        advanceUntilIdle()
        assertTrue(vm.state.value.playing)
        assertEquals(96, player.bpm)
        assertEquals(Meter.FOUR_FOUR, player.meter)

        vm.togglePlaying()
        advanceUntilIdle()
        assertFalse(vm.state.value.playing)
        assertEquals(1, player.stopCount)
    }

    @Test
    fun `the tempo changes live while playing`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.togglePlaying()
        vm.turn(5)
        assertEquals(125, player.bpm)
        assertEquals(1, player.startCount)
    }

    @Test
    fun `the lit beat follows the beats heard and goes out when stopped`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.togglePlaying()
        advanceUntilIdle()
        assertEquals(WatchMetronomeState.NO_BEAT, vm.state.value.litBeat)

        val lit = mutableListOf<Int>()
        for (i in 0L until 6L) {
            player.beat(i)
            runCurrent()
            lit += vm.state.value.litBeat
        }
        assertEquals(listOf(0, 1, 2, 3, 0, 1), lit)

        vm.togglePlaying()
        runCurrent()
        assertEquals(WatchMetronomeState.NO_BEAT, vm.state.value.litBeat)

        // Starting again begins with no light, not the last one.
        vm.togglePlaying()
        runCurrent()
        assertEquals(WatchMetronomeState.NO_BEAT, vm.state.value.litBeat)
    }

    @Test
    fun `the player stopping by itself shows stopped`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.togglePlaying()
        player.beat(0)
        runCurrent()
        player.stop() // a call, a failure
        runCurrent()
        assertEquals(WatchMetronomeState(bpm = 120, playing = false, click = false), vm.state.value)
    }

    @Test
    fun `the speaker button turns the click on and off and keeps it`() = runTest(dispatcher) {
        val store = FakeStore()
        val vm = viewModel(store)
        advanceUntilIdle()

        vm.toggleClick()
        advanceUntilIdle()
        assertTrue(vm.state.value.click)
        assertFalse(player.silent)
        assertTrue(store.kept.value.click)

        vm.toggleClick()
        advanceUntilIdle()
        assertFalse(vm.state.value.click)
        assertTrue(player.silent)
        assertFalse(store.kept.value.click)
    }

    @Test
    fun `a turn is kept once, after the bezel stops`() = runTest(dispatcher) {
        val store = FakeStore()
        val vm = viewModel(store)
        advanceUntilIdle()

        repeat(10) {
            vm.turn(1)
            advanceTimeBy(50)
        }
        assertEquals(0, store.tempoWrites)
        advanceTimeBy(WatchMetronomeViewModel.SAVE_DELAY_MILLIS + 1)
        assertEquals(1, store.tempoWrites)
        assertEquals(130, store.kept.value.bpm)
    }

    @Test
    fun `leaving stops the clicks and keeps the tempo at once`() = runTest(dispatcher) {
        val store = FakeStore()
        val vm = viewModel(store)
        advanceUntilIdle()
        vm.togglePlaying()
        vm.turn(-20)

        vm.leave()
        runCurrent()
        assertFalse(vm.state.value.playing)
        assertEquals(1, player.stopCount)
        assertEquals(1, store.tempoWrites)
        assertEquals(100, store.kept.value.bpm)

        // Nothing more to write afterwards, and leaving again changes nothing.
        advanceUntilIdle()
        vm.leave()
        advanceUntilIdle()
        assertEquals(1, store.tempoWrites)
        assertEquals(1, player.stopCount)
    }

    @Test
    fun `a turn or tap before the kept choices arrive is kept`() = runTest(dispatcher) {
        val store = FakeStore(WatchMetronomeChoices(bpm = 96, click = false), released = CompletableDeferred())
        val vm = viewModel(store)
        runCurrent()
        vm.turn(2)
        vm.toggleClick()
        store.released.complete(Unit)
        advanceUntilIdle()

        assertEquals(WatchMetronomeState(bpm = 122, playing = false, click = true), vm.state.value)
        assertFalse(player.silent)
    }

    @Test
    fun `every beat taps the wrist while playing, the downbeat longer`() = runTest(dispatcher) {
        val taps = Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
        val vm = viewModel(taps = WatchBeatTaps(leadNanos = 0L) { beat, millis -> taps += beat.index to millis })
        advanceUntilIdle()
        vm.togglePlaying()
        for (i in 0L until 5L) player.beat(i, heardAtNanos = System.nanoTime())
        withContext(Dispatchers.Default) {
            withTimeout(2_000) { while (taps.size < 5) delay(10) }
        }

        assertEquals(listOf(0L to 250L, 1L to 150L, 2L to 150L, 3L to 150L, 4L to 250L), taps.toList())
    }
}
