package io.github.klaboukes.twangle.feature.settings

import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSettingsRepository(AppSettings(a4Hz = 441f))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows the saved settings`() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        backgroundScope.launch { vm.settings.collect {} }
        runCurrent()

        assertEquals(441f, vm.settings.value?.a4Hz)
    }

    @Test
    fun `saves a new A4, rounded and clamped`() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        backgroundScope.launch { vm.settings.collect {} }

        vm.setA4(442.4f)
        runCurrent()
        assertEquals(442f, repository.current.a4Hz)
        assertEquals(442f, vm.settings.value?.a4Hz)

        vm.setA4(400f)
        runCurrent()
        assertEquals(415f, repository.current.a4Hz)
    }

    @Test
    fun `saves the notation`() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        vm.setNotation(Notation.SOLFEGE)
        runCurrent()
        assertEquals(Notation.SOLFEGE, repository.current.notation)
    }

    @Test
    fun `saves the in-tune precision`() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        vm.setInTunePrecision(InTunePrecision.PRECISE)
        runCurrent()
        assertEquals(InTunePrecision.PRECISE, repository.current.inTunePrecision)
    }

    @Test
    fun `saves the style`() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        vm.setStyle(VisualStyle.RETRO)
        runCurrent()
        assertEquals(VisualStyle.RETRO, repository.current.style)
    }

    @Test
    fun `saves the wave look`() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        vm.setWaveLook(WaveLook.RIPPLES)
        runCurrent()
        assertEquals(WaveLook.RIPPLES, repository.current.waveLook)
    }

    @Test
    fun `saves whether the wave breathes in tune`() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        vm.setWaveBreathesInTune(false)
        runCurrent()
        assertEquals(false, repository.current.waveBreathesInTune)
    }

    @Test
    fun `saves theme, wallpaper colours, display, layout, keep screen on, vibration and tune hint`() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        vm.setTheme(ThemeMode.LIGHT)
        vm.setDynamicColor(false)
        vm.setDisplay(TunerDisplay.STROBE)
        vm.setKeepScreenOn(false)
        vm.setVibrateInTune(false)
        vm.setShowTuneHint(false)
        vm.setKeepTwoPaneLandscape(true)
        runCurrent()

        assertEquals(TunerDisplay.STROBE, repository.current.display)

        assertEquals(ThemeMode.LIGHT, repository.current.theme)
        assertEquals(false, repository.current.dynamicColor)
        assertEquals(false, repository.current.keepScreenOn)
        assertEquals(false, repository.current.vibrateInTune)
        assertEquals(false, repository.current.showTuneHint)
        assertEquals(true, repository.current.keepTwoPaneLandscape)
    }
}
