package io.github.klaboukes.twangle.feature.metronome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.github.klaboukes.twangle.core.audio.FakeMetronomePlayer
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.SpeedTrainer
import io.github.klaboukes.twangle.core.dsp.metronome.Subdivision
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
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
        subdivision: Subdivision = Subdivision.DEFAULT,
        trainer: SpeedTrainer? = null,
        trainerOn: Boolean = false,
        tools: ToolSwitches = ToolSwitches.ALL_ON,
    ) = MetronomeViewModel(
        player,
        settings,
        savedBpm = bpm,
        savedMeter = meter,
        savedVibrate = vibrate,
        savedSound = sound,
        savedSubdivision = subdivision,
        savedTrainer = trainer,
        savedTrainerOn = trainerOn,
        tools = tools,
    )

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
    fun `it opens with the saved clicks between the beats, and Start plays them`() {
        val vm = viewModel(subdivision = Subdivision.THREE)
        assertEquals(Subdivision.THREE, vm.subdivision.value)
        vm.toggle()
        assertEquals(Subdivision.THREE, player.subdivision)
        assertEquals(0, player.previewCount)
    }

    @Test
    fun `clicks between the beats picked while stopped are saved and play one bar`() = runTest(dispatcher) {
        val vm = viewModel(bpm = 84, sound = ClickSound.COWBELL)
        vm.setSubdivision(Subdivision.TWO)
        advanceUntilIdle()
        assertEquals(Subdivision.TWO, vm.subdivision.value)
        assertEquals(Subdivision.TWO, settings.current.metronomeSubdivision)
        assertTrue(vm.isPreviewing.value)
        assertFalse(vm.isPlaying.value)
        assertEquals(1, player.previewCount)
        assertEquals(Triple(84, ClickSound.COWBELL, Subdivision.TWO), Triple(player.bpm, player.sound, player.subdivision))
        vm.previewSound(ClickSound.CLAVES)
        assertEquals("a sound's bar plays with them too", Subdivision.TWO, player.subdivision)
    }

    @Test
    fun `clicks between the beats picked while playing are heard from the next beat, with no preview`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggle()
        vm.setSubdivision(Subdivision.FOUR)
        advanceUntilIdle()
        assertEquals(Subdivision.FOUR, player.subdivision)
        assertTrue(vm.isPlaying.value)
        assertFalse(vm.isPreviewing.value)
        assertEquals(0, player.previewCount)
        assertEquals("the clicks go on", 1, player.startCount)
        assertEquals(Subdivision.FOUR, settings.current.metronomeSubdivision)
        vm.setSubdivision(Subdivision.NONE)
        advanceUntilIdle()
        assertEquals(Subdivision.NONE, player.subdivision)
        assertEquals(Subdivision.NONE, settings.current.metronomeSubdivision)
    }

    @Test
    fun `triplets are kept through 6-8, which plays them as none, and come back in 4-4`() = runTest(dispatcher) {
        val vm = viewModel(meter = Meter.FOUR_FOUR)
        vm.setSubdivision(Subdivision.THREE)
        vm.toggle()
        vm.setMeter(Meter.SIX_EIGHT)
        advanceUntilIdle()
        assertEquals("the choice stays", Subdivision.THREE, vm.subdivision.value)
        assertEquals(Subdivision.THREE, settings.current.metronomeSubdivision)
        assertEquals("6/8 plays it as none", Subdivision.NONE, player.subdivision.within(player.meter))
        vm.setMeter(Meter.FOUR_FOUR)
        assertEquals(Subdivision.THREE, player.subdivision.within(player.meter))
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

    @Test
    fun `the speed trainer turned on the first time is filled in from the tempo shown and saved`() = runTest(dispatcher) {
        val vm = viewModel(bpm = 96)
        assertFalse(vm.trainerSaved.value)
        assertEquals("filled in, not saved yet", SpeedTrainer(96, 116, 5, 4), vm.trainer.value)
        vm.stepBpm(-1)
        assertEquals("it follows the tempo until saved", SpeedTrainer(95, 115, 5, 4), vm.trainer.value)
        vm.setTrainerOn(true)
        advanceUntilIdle()
        assertTrue(vm.trainerOn.value)
        assertTrue(vm.trainerSaved.value)
        assertEquals(SpeedTrainer(95, 115, 5, 4), settings.current.metronomeTrainer)
        assertTrue(settings.current.metronomeTrainerOn)
        assertEquals("the dial shows the start", 95, vm.bpm.value)
    }

    @Test
    fun `Start climbs from the start tempo, the dial following each downbeat, and holds at the goal`() = runTest(dispatcher) {
        val vm = viewModel(bpm = 120, meter = Meter.TWO_FOUR, trainer = SpeedTrainer(80, 90, 5, 1), trainerOn = true)
        advanceUntilIdle()
        assertEquals("stopped it shows the start", 80, vm.bpm.value)
        vm.toggle()
        assertEquals(80, player.bpm)
        assertEquals(SpeedTrainer(80, 90, 5, 1), player.trainer)
        val shown = mutableListOf<Int>()
        for (beat in 0L until 8L) {
            player.beat(beat)
            advanceUntilIdle()
            shown += vm.bpm.value
        }
        assertEquals("each step on a downbeat, never past the goal", listOf(80, 80, 85, 85, 90, 90, 90, 90), shown)
        assertEquals(3L, vm.trainerBar.value)
        advanceUntilIdle()
        assertEquals("climbing tempos aren't saved as the metronome's", AppSettings().metronomeBpm, settings.current.metronomeBpm)
    }

    @Test
    fun `Stop and Start begin again from the start tempo`() = runTest(dispatcher) {
        val vm = viewModel(meter = Meter.TWO_FOUR, trainer = SpeedTrainer(80, 100, 10, 1), trainerOn = true)
        advanceUntilIdle()
        vm.toggle()
        for (beat in 0L until 4L) player.beat(beat)
        advanceUntilIdle()
        assertEquals(90, vm.bpm.value)
        vm.toggle()
        assertEquals("stopped, back at the start", 80, vm.bpm.value)
        assertEquals(-1L, vm.trainerBar.value)
        assertTrue("still on", vm.trainerOn.value)
        vm.toggle()
        assertEquals(80, player.bpm)
        assertEquals(2, player.startCount)
    }

    @Test
    fun `a tempo by hand, Tap tempo or the switch end the trainer and keep the tempo it reached`() = runTest(dispatcher) {
        val plan = SpeedTrainer(80, 100, 10, 1)
        for (end in listOf<(MetronomeViewModel) -> Unit>({ it.stepBpm(+1) }, { it.tap(1_000L) }, { it.setTrainerOn(false) })) {
            val vm = viewModel(meter = Meter.TWO_FOUR, trainer = plan, trainerOn = true)
            advanceUntilIdle()
            vm.toggle()
            for (beat in 0L until 4L) player.beat(beat)
            advanceUntilIdle()
            assertEquals(90, vm.bpm.value)
            end(vm)
            advanceUntilIdle()
            assertFalse(vm.trainerOn.value)
            assertFalse(settings.current.metronomeTrainerOn)
            assertEquals("the trainer ends in the clicks", null, player.trainer)
            assertTrue(vm.bpm.value in 90..91)
            assertEquals("the clicks keep the tempo shown", vm.bpm.value, player.bpm)
            assertEquals("and it is saved", vm.bpm.value, settings.current.metronomeBpm)
            player.beat(4L)
            advanceUntilIdle()
            assertEquals("no more climbing", vm.bpm.value, player.bpm)
            vm.toggle()
            settings.setMetronomeTrainerOn(true)
        }
    }

    @Test
    fun `a new plan while climbing begins again from its start, and is saved`() = runTest(dispatcher) {
        val vm = viewModel(meter = Meter.TWO_FOUR, trainer = SpeedTrainer(80, 100, 10, 1), trainerOn = true)
        advanceUntilIdle()
        vm.toggle()
        for (beat in 0L until 4L) player.beat(beat)
        advanceUntilIdle()
        vm.setTrainer(SpeedTrainer(70, 100, 10, 2))
        advanceUntilIdle()
        assertEquals(70, vm.bpm.value)
        assertEquals(SpeedTrainer(70, 100, 10, 2), player.trainer)
        assertEquals(SpeedTrainer(70, 100, 10, 2), settings.current.metronomeTrainer)
        assertEquals(1, player.trainerChanges)
        assertTrue(vm.trainerOn.value)
    }

    @Test
    fun `without its switch there is no trainer, whatever was saved`() = runTest(dispatcher) {
        val vm = viewModel(bpm = 100, trainer = SpeedTrainer(80, 90, 5, 1), trainerOn = true, tools = ToolSwitches.ALL_ON.copy(speedTrainer = false))
        assertFalse(vm.trainerOn.value)
        assertEquals("the saved tempo, not the trainer's start", 100, vm.bpm.value)
        vm.setTrainerOn(true)
        vm.toggle()
        assertEquals(null, player.trainer)
        assertFalse(vm.trainerOn.value)
        assertEquals(100, player.bpm)
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
