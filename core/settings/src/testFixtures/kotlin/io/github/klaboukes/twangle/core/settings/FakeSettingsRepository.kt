package io.github.klaboukes.twangle.core.settings

import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [SettingsRepository] for tests. Starts from [initial]; [current] shows what was saved,
 * and setting [current] directly simulates a change made elsewhere (e.g. on the settings screen).
 * New custom tunings get the ids `custom_1`, `custom_2`, … in the order they are added.
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

    override suspend fun setMode(mode: TunerMode) = state.update { it.copy(mode = mode) }

    override suspend fun setInstrument(instrument: Instrument) = state.update {
        if (it.instrument == instrument) it else it.copy(instrument = instrument, tuning = instrument.defaultTuning)
    }

    override suspend fun setTuning(tuning: Tuning) = state.update { settings ->
        val instrument = Instruments.ofTuning(tuning.id)
            ?: settings.customTunings.firstOrNull { it.id == tuning.id }?.instrument
        settings.copy(instrument = instrument ?: settings.instrument, tuning = tuning)
    }

    private var lastCustomId = 0

    override suspend fun addCustomTuning(name: String, instrument: Instrument, notes: List<Note>, pick: Boolean): CustomTuning {
        val added = CustomTuning(
            "${CustomTuning.ID_PREFIX}${++lastCustomId}", CustomTuning.normalizeName(name), instrument, notes,
        )
        state.update {
            val withIt = it.copy(customTunings = it.customTunings + added)
            if (pick) withIt.copy(instrument = instrument, tuning = added.tuning) else withIt
        }
        return added
    }

    override suspend fun updateCustomTuning(id: String, name: String, notes: List<Note>, pick: Boolean): CustomTuning? {
        val old = state.value.customTunings.firstOrNull { it.id == id } ?: return null
        val changed = CustomTuning(id, CustomTuning.normalizeName(name), old.instrument, notes)
        state.update { settings ->
            val picked = pick || settings.tuning.id == id
            settings.copy(
                customTunings = settings.customTunings.map { if (it.id == id) changed else it },
                instrument = if (picked) changed.instrument else settings.instrument,
                tuning = if (picked) changed.tuning else settings.tuning,
            )
        }
        return changed
    }

    override suspend fun deleteCustomTuning(id: String) = state.update { settings ->
        val deleted = settings.customTunings.firstOrNull { it.id == id } ?: return@update settings
        val picked = settings.tuning.id == id
        settings.copy(
            customTunings = settings.customTunings - deleted,
            instrument = if (picked) deleted.instrument else settings.instrument,
            tuning = if (picked) deleted.instrument.defaultTuning else settings.tuning,
        )
    }

    override suspend fun setNotation(notation: Notation?) = state.update { it.copy(notationChoice = notation) }

    override suspend fun setInTunePrecision(precision: InTunePrecision) =
        state.update { it.copy(inTunePrecision = precision) }

    override suspend fun setTheme(theme: ThemeMode) = state.update { it.copy(theme = theme) }

    override suspend fun setStyle(style: VisualStyle) = state.update { it.copy(style = style) }

    override suspend fun setDynamicColor(enabled: Boolean) = state.update { it.copy(dynamicColor = enabled) }

    override suspend fun setKeepScreenOn(enabled: Boolean) = state.update { it.copy(keepScreenOn = enabled) }

    override suspend fun setVibrateInTune(enabled: Boolean) = state.update { it.copy(vibrateInTune = enabled) }

    override suspend fun setShowTuneHint(enabled: Boolean) = state.update { it.copy(showTuneHint = enabled) }

    override suspend fun setShowSignalBars(enabled: Boolean) = state.update { it.copy(showSignalBars = enabled) }

    override suspend fun setDisplay(display: TunerDisplay) = state.update { it.copy(display = display) }

    override suspend fun setWaveLook(look: WaveLook) = state.update { it.copy(waveLook = look) }

    override suspend fun setMeterLook(look: MeterLook) = state.update { it.copy(meterLook = look) }

    override suspend fun setInTuneEffect(effect: InTuneEffect) = state.update { it.copy(inTuneEffect = effect) }

    override suspend fun setKeepTwoPaneLandscape(enabled: Boolean) =
        state.update { it.copy(keepTwoPaneLandscape = enabled) }

    override suspend fun setTourSeen(seen: Boolean) = state.update { it.copy(tourSeen = seen) }

    /** Nothing to decide: the fake starts from the settings it is given. */
    override suspend fun decideTourOnce() = Unit

    override suspend fun setMicRefused(refused: Boolean) = state.update { it.copy(micRefused = refused) }

    override suspend fun setMetronomeBpm(bpm: Int) = state.update { it.copy(metronomeBpm = ClickTrack.clampBpm(bpm)) }

    override suspend fun setMetronomeMeter(meter: Meter) = state.update { it.copy(metronomeMeter = meter) }

    override suspend fun setMetronomeVibrate(enabled: Boolean) = state.update { it.copy(metronomeVibrate = enabled) }
}
