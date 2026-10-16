package io.github.klaboukes.twangle.feature.metronome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.audio.MetronomeBeat
import io.github.klaboukes.twangle.core.audio.MetronomePlayer
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.OnsetTempoDetector
import io.github.klaboukes.twangle.core.dsp.metronome.TapTempo
import io.github.klaboukes.twangle.core.dsp.metronome.TempoDetector
import io.github.klaboukes.twangle.core.dsp.metronome.TempoStatus
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The metronome screen's state: tempo, meter and the vibration switch, saved in settings, and
 * whether the clicks play ([MetronomePlayer]). It opens stopped, at the tempo, meter and vibration
 * last saved ([savedBpm], [savedMeter], [savedVibrate], from the settings already read). Every
 * change is saved at once, except a dial drag, which is saved when the finger lifts ([saveBpm]).
 * A change while playing is heard from the next beat. The clicks stop when the screen asks
 * ([stop], when it leaves the screen) and when the ViewModel goes.
 *
 * **Listen** (Phase 27, docs/design/metronome-listen-preview.html): [listen] opens [recorder] (the
 * microphone; the screen has asked for the permission first) and feeds a [TempoDetector] on
 * [dspDispatcher], with the metronome's own clicks paused. It ends by itself once the tempo is
 * steady, after [LISTEN_MILLIS], when the player taps Stop or touches another control (the player
 * took over), or when the screen leaves; the microphone is released then. A found tempo is only
 * suggested ([ListenPhase.FOUND]): nothing changes until [use]. The clicks come back afterwards only
 * if they were playing before.
 *
 * [tools] says which music tools show (Phase 28): Practice (Phase 32) and the capo helper
 * (Phase 33) come only with their switches. Tests get every tool unless they say otherwise; the app
 * passes its build's.
 */
class MetronomeViewModel(
    private val player: MetronomePlayer,
    private val settingsRepository: SettingsRepository,
    savedBpm: Int = ClickTrack.DEFAULT_BPM,
    savedMeter: Meter = Meter.DEFAULT,
    savedVibrate: Boolean = false,
    private val recorder: AudioRecorder? = null,
    savedMicRefused: Boolean = false,
    private val dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nanoTime: () -> Long = System::nanoTime,
    private val detectorFor: (sampleRate: Int) -> TempoDetector = ::OnsetTempoDetector,
    val tools: ToolSwitches = ToolSwitches.ALL_ON,
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

    private val _listen = MutableStateFlow(ListenUi())

    /** Where Listen stands: what the button, the status line and the balloon show. */
    val listen: StateFlow<ListenUi> = _listen.asStateFlow()

    private val _heard = MutableStateFlow<HeardBeats?>(null)

    /**
     * The beats the detector hears while listening, once it has a tempo, for the beat lights; null
     * otherwise. Moves a few times a second, so the screen reads it outside composition.
     */
    val heard: StateFlow<HeardBeats?> = _heard.asStateFlow()

    /** Whether the player refused the microphone before on this install (saved), as the tuner's prompt. */
    val micRefused: StateFlow<Boolean> = settingsRepository.settings
        .map { it.micRefused }
        .stateIn(viewModelScope, SharingStarted.Eagerly, savedMicRefused)

    private var listenJob: Job? = null
    private var lineJob: Job? = null
    private var resumeJob: Job? = null
    private var detector: TempoDetector? = null

    /** Sets the tempo (clamped to 30–250 BPM); saved too unless [save] is false (a dial drag still moving). */
    fun setBpm(bpm: Int, save: Boolean = true) {
        takeOver()
        applyBpm(bpm, save)
    }

    private fun applyBpm(bpm: Int, save: Boolean) {
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
        takeOver()
        tapTempo.tap(atMillis)?.let { applyBpm(it, save = true) }
    }

    fun setMeter(meter: Meter) {
        takeOver()
        _meter.value = meter
        player.setMeter(meter)
        viewModelScope.launch { settingsRepository.setMetronomeMeter(meter) }
    }

    fun setVibrate(enabled: Boolean) {
        takeOver()
        _vibrate.value = enabled
        viewModelScope.launch { settingsRepository.setMetronomeVibrate(enabled) }
    }

    /** Start / Stop. It takes no taps while listening (the screen shows it resting). */
    fun toggle() {
        when (_listen.value.phase) {
            ListenPhase.LISTENING -> return
            // The balloon goes as Not now, but Start / Stop then does its own job from stopped.
            ListenPhase.FOUND -> closeOffer(resume = false)
            else -> Unit
        }
        resumeJob?.cancel()
        if (player.isPlaying.value) player.stop() else player.start(_bpm.value, _meter.value)
    }

    /** Stops the clicks and any listening (the screen left); a suggestion still open goes. */
    fun stop() {
        endListening()
        resumeJob?.cancel()
        lineJob?.cancel()
        _listen.value = _listen.value.copy(phase = ListenPhase.IDLE)
        if (player.isPlaying.value) player.stop()
    }

    /**
     * Listen was tapped with the microphone allowed: listening starts, the clicks pause. While
     * listening a tap stops it ([stopListening]).
     */
    fun listen() {
        if (recorder == null) return
        val now = _listen.value
        if (now.phase == ListenPhase.LISTENING) {
            stopListening()
            return
        }
        resumeJob?.cancel()
        lineJob?.cancel()
        // Listening again from an open suggestion keeps what was playing before the first time.
        val wasPlaying = player.isPlaying.value || (now.phase == ListenPhase.FOUND && now.wasPlaying)
        if (player.isPlaying.value) player.stop()
        val started = nanoTime()
        _listen.value = ListenUi(phase = ListenPhase.LISTENING, wasPlaying = wasPlaying, startedAtNanos = started)
        _heard.value = null
        listenJob = viewModelScope.launch { listenFor(recorder) }
    }

    /** Stop while listening: the tempo stays, the clicks come back if they were playing. */
    fun stopListening() {
        if (_listen.value.phase != ListenPhase.LISTENING) return
        endListening()
        val wasPlaying = _listen.value.wasPlaying
        _listen.value = _listen.value.copy(phase = ListenPhase.IDLE, endedAtNanos = nanoTime())
        if (wasPlaying) resume()
    }

    /**
     * Use the suggested tempo times [pick]: the tempo changes (and is saved). The clicks come back at
     * it [resumeAfterMillis] later (once the dial has glided there) if they were playing before.
     */
    fun use(pick: TempoPick, resumeAfterMillis: Long = USE_GLIDE_MILLIS) {
        val offer = _listen.value
        if (offer.phase != ListenPhase.FOUND) return
        val tempo = offer.tempoFor(pick).takeIf { it > 0 } ?: offer.found
        _listen.value = offer.copy(phase = ListenPhase.IDLE)
        applyBpm(tempo, save = true)
        if (offer.wasPlaying) {
            resumeJob = viewModelScope.launch {
                delay(resumeAfterMillis)
                resume()
            }
        }
    }

    /** Not now (or Back): the suggestion goes, nothing changes; the clicks come back if they were playing. */
    fun notNow() = closeOffer(resume = true)

    /** Try again after "No steady beat heard": as Listen. */
    fun tryAgain() = listen()

    /**
     * The microphone isn't allowed: the line says so for a while, with "Open settings" when only
     * Settings can allow it ([needsSettings]).
     */
    fun showMicOff(needsSettings: Boolean) {
        showLine(if (needsSettings) ListenPhase.MIC_OFF_SETTINGS else ListenPhase.MIC_OFF)
    }

    /** The player refused the microphone in Android's dialog: saved, as the tuner does. */
    fun onMicRefused() {
        if (!micRefused.value) viewModelScope.launch { settingsRepository.setMicRefused(true) }
    }

    override fun onCleared() {
        stop()
    }

    /** Touching another control: listening ends (the player took over) and a suggestion goes, as Not now. */
    private fun takeOver() {
        when (_listen.value.phase) {
            ListenPhase.LISTENING -> stopListening()
            ListenPhase.FOUND -> closeOffer(resume = true)
            else -> Unit
        }
    }

    private fun closeOffer(resume: Boolean) {
        val offer = _listen.value
        if (offer.phase != ListenPhase.FOUND) return
        _listen.value = offer.copy(phase = ListenPhase.IDLE)
        if (resume && offer.wasPlaying) resume()
    }

    private fun resume() {
        if (!player.isPlaying.value) player.start(_bpm.value, _meter.value)
    }

    private fun endListening() {
        listenJob?.cancel()
        listenJob = null
        _heard.value = null
    }

    /** Shows [phase]'s line (no beat, microphone off) for [LINE_MILLIS], then nothing. */
    private fun showLine(phase: ListenPhase) {
        lineJob?.cancel()
        _listen.value = _listen.value.copy(phase = phase)
        lineJob = viewModelScope.launch {
            delay(LINE_MILLIS)
            if (_listen.value.phase == phase) _listen.value = _listen.value.copy(phase = ListenPhase.IDLE)
        }
    }

    /** Listens until the tempo is steady or [LISTEN_MILLIS] are up, then suggests it, or says no beat was heard. */
    private suspend fun listenFor(recorder: AudioRecorder) {
        val heard = try {
            withContext(dspDispatcher) { hear(recorder) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: AudioRecorderException) {
            null
        }
        listenJob = null
        _heard.value = null
        val ended = _listen.value.copy(phase = ListenPhase.IDLE, endedAtNanos = nanoTime(), estimate = 0)
        if (heard == null) {
            _listen.value = ended
            showLine(ListenPhase.NO_BEAT)
            if (ended.wasPlaying) resume()
        } else {
            _listen.value = ended.copy(phase = ListenPhase.FOUND, found = heard.bpm, half = heard.half, double = heard.double)
        }
    }

    /**
     * Feeds the microphone to the detector until it is steady or time is up; the tempo found (steady,
     * or still settling when time ran out), or null for no beat. Runs on [dspDispatcher].
     */
    private suspend fun hear(recorder: AudioRecorder): Found? {
        var detector: TempoDetector? = this.detector?.also { it.reset() }
        val session = _listen.value.startedAtNanos
        var firstSampleNanos = Long.MAX_VALUE
        var shownBpm = -1
        var shownBeat = Long.MIN_VALUE
        withTimeoutOrNull(LISTEN_MILLIS) {
            recorder.chunks().first { chunk ->
                val rate = recorder.config.sampleRate
                val d = detector?.takeIf { it.sampleRate == rate } ?: detectorFor(rate)
                if (d !== detector) {
                    detector = d
                    this@MetronomeViewModel.detector = d
                }
                d.process(chunk, 0, chunk.size)
                // When the first sample was heard: the earliest the chunks could have come, since a
                // chunk never arrives before it is recorded.
                val start = nanoTime() - samplesToNanos(d.samplesProcessed, rate)
                if (start < firstSampleNanos) firstSampleNanos = start
                val settled = d.status == TempoStatus.SETTLING || d.status == TempoStatus.STEADY
                val estimate = if (settled) d.bpm else 0
                if (estimate != shownBpm) {
                    shownBpm = estimate
                    // Only while this listening lasts: Stop on the main thread may have ended it meanwhile.
                    _listen.update { if (it.listening && it.startedAtNanos == session) it.copy(estimate = estimate) else it }
                }
                if (settled && d.lastBeatSample >= 0L && d.beatIntervalSamples > 0f && d.lastBeatSample != shownBeat) {
                    shownBeat = d.lastBeatSample
                    _heard.value = HeardBeats(
                        lastBeatNanos = firstSampleNanos + samplesToNanos(d.lastBeatSample, rate),
                        intervalNanos = (d.beatIntervalSamples.toDouble() * NANOS_PER_SECOND / rate).toLong(),
                    )
                }
                d.status == TempoStatus.STEADY
            }
        }
        val d = detector ?: return null
        return when (d.status) {
            TempoStatus.STEADY, TempoStatus.SETTLING -> if (d.bpm > 0) Found(d.bpm, d.halfBpm, d.doubleBpm) else null
            TempoStatus.LISTENING, TempoStatus.NO_BEAT -> null
        }
    }

    private class Found(val bpm: Int, val half: Int, val double: Int)

    companion object {
        /** Listening ends after this long without a steady tempo. */
        const val LISTEN_MILLIS = 15_000L

        /** "No steady beat heard" and "Microphone is off" stay this long. */
        const val LINE_MILLIS = 8_000L

        /** The dial glides to a used tempo over this long; the clicks come back after it. */
        const val USE_GLIDE_MILLIS = 700L

        private const val NANOS_PER_SECOND = 1_000_000_000.0

        private fun samplesToNanos(samples: Long, rate: Int): Long = (samples * NANOS_PER_SECOND / rate).toLong()
    }
}

/** Where Listen stands. */
enum class ListenPhase {
    /** Not listening; nothing to show. */
    IDLE,

    /** The microphone is open and the detector listens. */
    LISTENING,

    /** A tempo was found and is suggested in the balloon; nothing changed yet. */
    FOUND,

    /** Listening ended without a steady beat: "No steady beat heard  Try again", for a while. */
    NO_BEAT,

    /** The microphone was refused: "Microphone is off", for a while. */
    MIC_OFF,

    /** The microphone can only be allowed in Settings: "Microphone is off  Open settings", for a while. */
    MIC_OFF_SETTINGS,
}

/** The suggestion's ½× 1× 2× choice. */
enum class TempoPick { HALF, AS_HEARD, DOUBLE }

/**
 * What Listen shows. [startedAtNanos] and [endedAtNanos] (0 while listening) time the trace round the
 * Listen button, on the `System.nanoTime()` clock; [estimate] is the tempo it is settling on (0 for
 * none yet); [found], [half] and [double] the suggestion (0 where out of range); [wasPlaying] whether
 * the clicks played before listening.
 */
data class ListenUi(
    val phase: ListenPhase = ListenPhase.IDLE,
    val wasPlaying: Boolean = false,
    val startedAtNanos: Long = 0L,
    val endedAtNanos: Long = 0L,
    val estimate: Int = 0,
    val found: Int = 0,
    val half: Int = 0,
    val double: Int = 0,
) {
    val listening: Boolean get() = phase == ListenPhase.LISTENING

    /** The tempo of [pick], or 0 where it is out of range. */
    fun tempoFor(pick: TempoPick): Int = when (pick) {
        TempoPick.HALF -> half
        TempoPick.AS_HEARD -> found
        TempoPick.DOUBLE -> double
    }
}

/** The beat grid the detector hears: its latest beat and the time from one beat to the next, on the `System.nanoTime()` clock. */
data class HeardBeats(val lastBeatNanos: Long, val intervalNanos: Long)
