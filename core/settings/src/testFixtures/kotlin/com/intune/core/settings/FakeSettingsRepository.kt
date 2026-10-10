package com.intune.core.settings

import com.intune.core.dsp.music.Notation
import com.intune.core.dsp.music.Tuning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [SettingsRepository] for tests. Starts from [initial]; [current] shows what was saved,
 * and setting [current] directly simulates a change made elsewhere (e.g. on the settings screen).
 */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    var current: AppSettings
        get() = state.value
        set(value) {
            state.value = value
        }

    override suspend fun setA4(hz: Float) = state.update { it.copy(a4Hz = AppSettings.normalizeA4(hz)) }

    override suspend fun setTuning(tuning: Tuning) = state.update { it.copy(tuning = tuning) }

    override suspend fun setNotation(notation: Notation) = state.update { it.copy(notation = notation) }

    override suspend fun setTheme(theme: ThemeMode) = state.update { it.copy(theme = theme) }

    override suspend fun setStyle(style: VisualStyle) = state.update { it.copy(style = style) }

    override suspend fun setDynamicColor(enabled: Boolean) = state.update { it.copy(dynamicColor = enabled) }

    override suspend fun setKeepScreenOn(enabled: Boolean) = state.update { it.copy(keepScreenOn = enabled) }

    override suspend fun setVibrateInTune(enabled: Boolean) = state.update { it.copy(vibrateInTune = enabled) }

    override suspend fun setShowWaves(enabled: Boolean) = state.update { it.copy(showWaves = enabled) }

    override suspend fun setWaveLook(look: WaveLook) = state.update { it.copy(waveLook = look) }
}
