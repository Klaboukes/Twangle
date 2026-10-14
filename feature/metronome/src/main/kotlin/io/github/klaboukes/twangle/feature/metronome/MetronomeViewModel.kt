package io.github.klaboukes.twangle.feature.metronome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klaboukes.twangle.core.audio.MetronomeBeat
import io.github.klaboukes.twangle.core.audio.MetronomePlayer
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.TapTempo
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The metronome screen's state: tempo, meter and the vibration switch, saved in settings, and
 * whether the clicks play ([MetronomePlayer]). It opens stopped, at the tempo, meter and vibration
 * last saved ([savedBpm], [savedMeter], [savedVibrate], from the settings already read). Every
 * change is saved at once, except a dial drag, which is saved when the finger lifts ([saveBpm]).
 * A change while playing is heard from the next beat. The clicks stop when the screen asks
 * ([stop], when it leaves the screen) and when the ViewModel goes.
 */
class MetronomeViewModel(
    private val player: MetronomePlayer,
    private val settingsRepository: SettingsRepository,
    savedBpm: Int = ClickTrack.DEFAULT_BPM,
    savedMeter: Meter = Meter.DEFAULT,
    savedVibrate: Boolean = false,
) : ViewModel() {

    private val _bpm = MutableStateFlow(ClickTrack.clampBpm(savedBpm))

    /** The tempo, whole BPM within 30–250. */
    val bpm: StateFlow<Int> = _bpm.asStateFlow()

    private val _meter = MutableStateFlow(savedMeter)
    val meter: StateFlow<Meter> = _meter.asStateFlow()

    private val _vibrate = MutableStateFlow(savedVibrate)

    /** Vibrate on every beat. */
    val vibrate: StateFlow<Boolean> = _vibrate.asStateFlow()

    /** True while the clicks play. */
    val isPlaying: StateFlow<Boolean> = player.isPlaying

    /** Every click as it is heard, for the beat lights and the vibration. */
    val beats: SharedFlow<MetronomeBeat> = player.beats

    private val tapTempo = TapTempo()

    /** Sets the tempo (clamped to 30–250 BPM); saved too unless [save] is false (a dial drag still moving). */
    fun setBpm(bpm: Int, save: Boolean = true) {
        val clamped = ClickTrack.clampBpm(bpm)
        _bpm.value = clamped
        player.setTempo(clamped)
        if (save) saveBpm()
    }

    /** Saves the tempo shown, at the end of a dial drag. */
    fun saveBpm() {
        val value = _bpm.value
        viewModelScope.launch { settingsRepository.setMetronomeBpm(value) }
    }

    /** One BPM slower (−1) or faster (+1). */
    fun stepBpm(delta: Int) = setBpm(_bpm.value + delta)

    /** A tap on Tap tempo at [atMillis] (a steady clock): from the second tap on, the tempo follows the taps. */
    fun tap(atMillis: Long) {
        tapTempo.tap(atMillis)?.let { setBpm(it) }
    }

    fun setMeter(meter: Meter) {
        _meter.value = meter
        player.setMeter(meter)
        viewModelScope.launch { settingsRepository.setMetronomeMeter(meter) }
    }

    fun setVibrate(enabled: Boolean) {
        _vibrate.value = enabled
        viewModelScope.launch { settingsRepository.setMetronomeVibrate(enabled) }
    }

    /** Start / Stop. */
    fun toggle() {
        if (player.isPlaying.value) player.stop() else player.start(_bpm.value, _meter.value)
    }

    /** Stops the clicks (nothing happens when none play). */
    fun stop() {
        if (player.isPlaying.value) player.stop()
    }

    override fun onCleared() {
        stop()
    }
}
