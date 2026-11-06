package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.settings.TunerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A tap on the navigation bar's Tuner tab ([TunerViewModel.closeToolsOnRequest]) leads to the plain tuner. */
@OptIn(ExperimentalCoroutinesApi::class)
class CloseToolsTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // The 12th-fret check's way in is on (its Settings switch, off on a fresh install).
    private val settings = FakeSettingsRepository()
    private val tones = FakeTonePlayer()

    private fun TestScope.viewModel() =
        TunerViewModel(FakeAudioRecorder.silence(), settings, tones, StandardTestDispatcher(testScheduler), saved = settings.settings.value, tools = ToolSwitches.ALL_ON)

    @Test
    fun `each new request turns the 12th-fret check off, and the tuner listens again`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.setIntonationCheck(true)
        advanceTimeBy(100)
        assertTrue(vm.state.value is TunerUiState.IntonationCheck)

        vm.closeToolsOnRequest(1)
        advanceTimeBy(100)
        assertFalse(vm.intonationCheck.value)
        val state = vm.state.value
        // Back on the plain tuner, still in guitar mode.
        assertTrue("was $state", state is TunerUiState.Listening)
        assertEquals(TunerMode.AUTO, vm.mode.value)

        vm.setIntonationCheck(true)
        vm.closeToolsOnRequest(2)
        assertFalse(vm.intonationCheck.value)
    }

    @Test
    fun `a request already acted on keeps the tool`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.closeToolsOnRequest(1)
        vm.setIntonationCheck(true)
        // The tuner shown again without a new tap (back from the tuning editor, the phone turned).
        vm.closeToolsOnRequest(1)
        assertTrue(vm.intonationCheck.value)
        // Before the first tap there is nothing to act on.
        val fresh = viewModel()
        fresh.setIntonationCheck(true)
        fresh.closeToolsOnRequest(0)
        assertTrue(fresh.intonationCheck.value)
    }
}
