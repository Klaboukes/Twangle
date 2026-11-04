package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.LensLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The meter's look the tuner draws (Phase 78): the saved one, Classic by default. */
@OptIn(ExperimentalCoroutinesApi::class)
class MeterLookTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the meter look is the saved one, Classic by default`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = TunerViewModel(FakeAudioRecorder.silence(), settings, FakeTonePlayer(), StandardTestDispatcher(testScheduler))
        backgroundScope.launch { vm.lensLook.collect {} }
        advanceTimeBy(100)
        assertEquals(LensLook.CLASSIC, vm.lensLook.value)
        for (look in LensLook.entries.reversed()) {
            settings.setLensLook(look)
            advanceTimeBy(100)
            assertEquals(look, vm.lensLook.value)
        }
    }
}
