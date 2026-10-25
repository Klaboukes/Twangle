package io.github.klaboukes.twangle.feature.metronome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.github.klaboukes.twangle.core.audio.FakeMetronomePlayer
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
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

    private fun viewModel(
        bpm: Int = 96,
        meter: Meter = Meter.THREE_FOUR,
        vibrate: Boolean = false,
        sound: ClickSound = ClickSound.DEFAULT,
    ) = MetronomeViewModel(player, settings, savedBpm = bpm, savedMeter = meter, savedVibrate = vibrate, savedSound = sound)

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
    fun `it opens with the saved sound, and the first beat clicks with it`() {
        val vm = viewModel(sound = ClickSound.CLAVES)
        assertEquals(ClickSound.CLAVES, vm.sound.value)
        vm.toggle()
        assertEquals(ClickSound.CLAVES, player.sound)
        assertEquals(0, player.previewCount)
    }

    @Test
    fun `a sound picked while stopped is saved and plays one bar at the tempo and meter shown`() = runTest(dispatcher) {
        val vm = viewModel(bpm = 84, meter = Meter.SIX_EIGHT)
        vm.previewSound(ClickSound.COWBELL)
        advanceUntilIdle()
        assertEquals(ClickSound.COWBELL, vm.sound.value)
        assertEquals(ClickSound.COWBELL, settings.current.metronomeClickSound)
        assertTrue(vm.isPreviewing.value)
        assertFalse("a preview isn't the metronome playing", vm.isPlaying.value)
        assertEquals(Triple(84, Meter.SIX_EIGHT, ClickSound.COWBELL), Triple(player.bpm, player.meter, player.sound))
        player.finishPreview()
        assertFalse(vm.isPreviewing.value)
        assertEquals(1, player.previewCount)
    }

    @Test
    fun `the next pick starts a new preview, and Start plays the sound picked`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.previewSound(ClickSound.COWBELL)
        vm.previewSound(ClickSound.RIM_CLICK)
        assertEquals(2, player.previewCount)
        assertEquals(ClickSound.RIM_CLICK, player.sound)
        vm.toggle()
        assertFalse("Start ends the preview", vm.isPreviewing.value)
        assertTrue(vm.isPlaying.value)
        assertEquals(ClickSound.RIM_CLICK, player.sound)
        advanceUntilIdle()
        assertEquals(ClickSound.RIM_CLICK, settings.current.metronomeClickSound)
    }

    @Test
    fun `a sound picked while playing is heard from the next beat, with no preview`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggle()
        vm.previewSound(ClickSound.CLAVES)
        advanceUntilIdle()
        assertEquals(ClickSound.CLAVES, player.sound)
        assertTrue(vm.isPlaying.value)
        assertFalse(vm.isPreviewing.value)
        assertEquals(0, player.previewCount)
        assertEquals("the clicks go on", 1, player.startCount)
        assertEquals(ClickSound.CLAVES, settings.current.metronomeClickSound)
    }

    @Test
    fun `leaving the screen ends a preview`() {
        val vm = viewModel()
        vm.previewSound(ClickSound.CLAVES)
        vm.stop()
        assertFalse(vm.isPreviewing.value)
        assertEquals(1, player.stopCount)
        vm.previewSound(ClickSound.COWBELL)
        vm.viewModelStore().clear()
        assertFalse("a cleared ViewModel ends the preview", player.isPreviewing.value)
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
