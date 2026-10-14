package io.github.klaboukes.twangle.feature.metronome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.github.klaboukes.twangle.core.audio.FakeMetronomePlayer
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MetronomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val player = FakeMetronomePlayer()
    private val settings = FakeSettingsRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(bpm: Int = 96, meter: Meter = Meter.THREE_FOUR, vibrate: Boolean = false) =
        MetronomeViewModel(player, settings, savedBpm = bpm, savedMeter = meter, savedVibrate = vibrate)

    @Test
    fun `it opens stopped at the saved tempo, meter and vibration`() {
        val vm = viewModel(bpm = 72, meter = Meter.SIX_EIGHT, vibrate = true)
        assertEquals(72, vm.bpm.value)
        assertEquals(Meter.SIX_EIGHT, vm.meter.value)
        assertTrue(vm.vibrate.value)
        assertFalse(vm.isPlaying.value)
        assertEquals(0, player.startCount)
    }

    @Test
    fun `start plays the tempo and meter shown, stop stops it`() {
        val vm = viewModel()
        vm.toggle()
        assertTrue(vm.isPlaying.value)
        assertEquals(96, player.bpm)
        assertEquals(Meter.THREE_FOUR, player.meter)
        vm.toggle()
        assertFalse(vm.isPlaying.value)
    }

    @Test
    fun `tempo and meter changes reach the clicks while playing and are saved`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggle()
        vm.stepBpm(+1)
        vm.setMeter(Meter.SEVEN_EIGHT)
        advanceUntilIdle()
        assertEquals(97, player.bpm)
        assertEquals(Meter.SEVEN_EIGHT, player.meter)
        assertEquals(97, settings.current.metronomeBpm)
        assertEquals(Meter.SEVEN_EIGHT, settings.current.metronomeMeter)
    }

    @Test
    fun `the tempo stays within 30 to 250 BPM`() = runTest(dispatcher) {
        val vm = viewModel(bpm = 250)
        vm.stepBpm(+1)
        assertEquals(250, vm.bpm.value)
        vm.setBpm(5)
        advanceUntilIdle()
        assertEquals(30, vm.bpm.value)
        assertEquals(30, settings.current.metronomeBpm)
    }

    @Test
    fun `a dial drag is saved only when the finger lifts`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setBpm(140, save = false)
        advanceUntilIdle()
        assertEquals(140, vm.bpm.value)
        assertEquals(AppSettings().metronomeBpm, settings.current.metronomeBpm)
        vm.saveBpm()
        advanceUntilIdle()
        assertEquals(140, settings.current.metronomeBpm)
    }

    @Test
    fun `tap tempo follows the taps from the second one`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.tap(10_000)
        assertEquals("one tap changes nothing", 96, vm.bpm.value)
        vm.tap(10_500)
        vm.tap(11_000)
        advanceUntilIdle()
        assertEquals(120, vm.bpm.value)
        assertEquals(120, settings.current.metronomeBpm)
    }

    @Test
    fun `the vibration switch is saved`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setVibrate(true)
        advanceUntilIdle()
        assertTrue(vm.vibrate.value)
        assertTrue(settings.current.metronomeVibrate)
    }

    @Test
    fun `stop only stops what plays, and the clicks stop when the ViewModel goes`() {
        val vm = viewModel()
        vm.stop()
        assertEquals("nothing to stop", 0, player.stopCount)
        vm.toggle()
        vm.stop()
        assertFalse(player.isPlaying.value)
        vm.toggle()
        vm.viewModelStore().clear()
        assertFalse("cleared ViewModel stops the clicks", player.isPlaying.value)
    }

    /** Puts the ViewModel in a store, as its owner would, so clearing the store clears it. */
    private fun MetronomeViewModel.viewModelStore(): ViewModelStore {
        val store = ViewModelStore()
        val vm = this
        ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
            },
        )[MetronomeViewModel::class.java]
        return store
    }
}
