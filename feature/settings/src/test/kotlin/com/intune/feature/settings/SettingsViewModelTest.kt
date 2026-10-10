package com.intune.feature.settings

import com.intune.core.settings.AppSettings
import com.intune.core.settings.FakeSettingsRepository
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
}
