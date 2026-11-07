package io.github.klaboukes.twangle.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.LensLook
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.core.settings.UnderMeter
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

    /** The player's pick, or null for Automatic (the app's language decides). */
    fun setNotation(notation: Notation?) {
        viewModelScope.launch { repository.setNotation(notation) }
    }

    fun setInTunePrecision(precision: InTunePrecision) {
        viewModelScope.launch { repository.setInTunePrecision(precision) }
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

    fun setStringTickOff(enabled: Boolean) {
        viewModelScope.launch { repository.setStringTickOff(enabled) }
    }

    fun setUnderMeter(choice: UnderMeter) {
        viewModelScope.launch { repository.setUnderMeter(choice) }
    }

    fun setLensLook(look: LensLook) {
        viewModelScope.launch { repository.setLensLook(look) }
    }

    fun setLeftHanded(enabled: Boolean) {
        viewModelScope.launch { repository.setLeftHanded(enabled) }
    }
}
