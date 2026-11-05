package io.github.klaboukes.twangle.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klaboukes.twangle.core.audio.MetronomePlayer
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the watch's metronome page shows: the tempo, whether it plays, whether the speaker clicks (off: the
 * taps alone), and the beat of the bar whose light is lit ([NO_BEAT] while stopped, and before the first beat).
 */
data class WatchMetronomeState(
    val bpm: Int,
    val playing: Boolean,
    val click: Boolean,
    val litBeat: Int = NO_BEAT,
) {
    companion object {
        const val NO_BEAT = -1
    }
}

/**
 * The watch's metronome (Phase 80): the phone's [MetronomePlayer] in 4/4, the tempo turned by the bezel or
 * crown ([turn], 30–250 BPM, live while it plays), ▶ / ■ ([togglePlaying]) and the speaker ([toggleClick];
 * off, the player's clicks are silent, [MetronomePlayer.setSilent], so the clock never changes hands). Every
 * beat taps the wrist through [taps] ([WatchBeatTaps], following the player's upcoming beats for as long as
 * this lives, on [tapDispatcher]), the downbeat longer. It never touches the microphone.
 *
 * The tempo and the click choice are kept in [store]: read once when this is made (a turn or tap before
 * they arrive keeps what the player chose), the click saved at once, the tempo [SAVE_DELAY_MILLIS] after
 * the bezel stops (one write per turn, not per step) and at once when the page is left ([leave]).
 */
class WatchMetronomeViewModel(
    private val player: MetronomePlayer,
    private val store: WatchMetronomeStore,
    taps: WatchBeatTaps?,
    tapDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val tempo = MutableStateFlow(WatchMetronomeChoices.DEFAULT.bpm)
    private val click = MutableStateFlow(WatchMetronomeChoices.DEFAULT.click)
    private val lit = MutableStateFlow(WatchMetronomeState.NO_BEAT)

    // Whether the player changed something before the kept choices were read: then theirs is kept.
    private var tempoTouched = false
    private var clickTouched = false
    private var savedTempo: Int? = null
    private var tempoSave: Job? = null

    val state: StateFlow<WatchMetronomeState> =
        combine(tempo, click, player.isPlaying, lit) { bpm, click, playing, lit ->
            WatchMetronomeState(bpm, playing, click, if (playing) lit else WatchMetronomeState.NO_BEAT)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            WatchMetronomeState(tempo.value, player.isPlaying.value, click.value),
        )

    init {
        player.setSilent(!click.value)
        viewModelScope.launch {
            val kept = store.choices.first()
            savedTempo = kept.bpm
            if (!tempoTouched) tempo.value = kept.bpm
            if (!clickTouched) setClickNow(kept.click)
        }
        viewModelScope.launch { player.beats.collect { lit.value = it.beatInBar } }
        if (taps != null) {
            // Collected from the start, so not even the first beat, told at once, can be missed.
            viewModelScope.launch(tapDispatcher, start = CoroutineStart.UNDISPATCHED) {
                taps.follow(player.upcomingBeats) { player.isPlaying.value }
            }
        }
    }

    /** The bezel or crown turned [steps] (clockwise up), 1 BPM a step. */
    fun turn(steps: Int) {
        if (steps != 0) setTempo(tempo.value + steps)
    }

    /** The tempo to [bpm], clamped to 30–250; while playing from the next beat. */
    fun setTempo(bpm: Int) {
        val clamped = ClickTrack.clampBpm(bpm)
        tempoTouched = true
        if (clamped == tempo.value) return
        tempo.value = clamped
        if (player.isPlaying.value) player.setTempo(clamped)
        tempoSave?.cancel()
        tempoSave = viewModelScope.launch {
            delay(SAVE_DELAY_MILLIS)
            saveTempo()
        }
    }

    /** ▶ / ■: starts at the tempo, four beats to the bar, or stops. */
    fun togglePlaying() {
        if (player.isPlaying.value) {
            player.stop()
        } else {
            lit.value = WatchMetronomeState.NO_BEAT
            player.start(tempo.value, Meter.FOUR_FOUR)
        }
    }

    /** The speaker button: the click on or off, from about one buffer later, playing or not. */
    fun toggleClick() {
        clickTouched = true
        setClickNow(!click.value)
        viewModelScope.launch { store.setClick(click.value) }
    }

    /** The page was left (swiped away, the app in the background, the screen off): the clicks stop, the tempo is kept. */
    fun leave() {
        if (player.isPlaying.value) player.stop()
        if (tempoSave?.isActive == true) {
            tempoSave?.cancel()
            viewModelScope.launch { saveTempo() }
        }
    }

    private fun setClickNow(on: Boolean) {
        click.value = on
        player.setSilent(!on)
    }

    private suspend fun saveTempo() {
        val bpm = tempo.value
        if (bpm == savedTempo) return
        store.setTempo(bpm)
        savedTempo = bpm
    }

    override fun onCleared() {
        if (player.isPlaying.value) player.stop()
    }

    companion object {
        /** How long after the last step of a turn the tempo is written: one write per turn. */
        const val SAVE_DELAY_MILLIS = 600L
    }
}
