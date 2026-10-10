package com.intune.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intune.core.dsp.music.Notation
import com.intune.core.settings.AppSettings
import com.intune.core.settings.SettingsRepository
import com.intune.core.settings.ThemeMode
import com.intune.core.settings.VisualStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shows the saved [settings] and saves every change the user makes on the settings screen. */
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    /** The saved settings; null for a moment until they have loaded. */
    val settings: StateFlow<AppSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setA4(hz: Float) {
        viewModelScope.launch { repository.setA4(hz) }
    }

    fun setNotation(notation: Notation) {
        viewModelScope.launch { repository.setNotation(notation) }
    }

    fun setStyle(style: VisualStyle) {
        viewModelScope.launch { repository.setStyle(style) }
    }

    fun setTheme(theme: ThemeMode) {
        viewModelScope.launch { repository.setTheme(theme) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { repository.setKeepScreenOn(enabled) }
    }

    fun setVibrateInTune(enabled: Boolean) {
        viewModelScope.launch { repository.setVibrateInTune(enabled) }
    }

    fun setShowWaves(enabled: Boolean) {
        viewModelScope.launch { repository.setShowWaves(enabled) }
    }
}
