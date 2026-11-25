package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.dsp.guide.GuidePulse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays the sound guide's sounds (Phase 94, `GuideSounds` in `:core:dsp`): steps up for a string too high
 * ([GuidePulse.LOOSEN]), down for too low ([GuidePulse.TIGHTEN]), a soft bell in tune ([GuidePulse.IN_TUNE]). The
 * tuner calls it from its analysis thread a few times a second at most; [play] and [stop] return at once.
 */
interface GuideSoundPlayer {

    /**
     * True from [play] until the sound has left the speaker (or failed to play). While it is true the tuner sets the
     * microphone's frames aside, as for a reference tone: it would hear the guide's own sound.
     */
    val isSounding: StateFlow<Boolean>

    /** Plays [pulse]'s sound now ([GuidePulse.NONE] plays nothing), the in-tune bell [inTuneSeconds] long. */
    fun play(pulse: GuidePulse, inTuneSeconds: Float)

    /** Stops a sound that is playing (a fade of a few ms), e.g. when the tuner is left. */
    fun stop()

    companion object {
        /** No sound: previews, and tests that don't listen for it. */
        val NONE: GuideSoundPlayer = object : GuideSoundPlayer {
            override val isSounding: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

            override fun play(pulse: GuidePulse, inTuneSeconds: Float) = Unit

            override fun stop() = Unit
        }
    }
}
