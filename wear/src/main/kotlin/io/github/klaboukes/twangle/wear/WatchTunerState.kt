package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.dsp.music.Note

/** What the watch tuner shows. Only what is drawn is in here, so equal states look the same. */
sealed interface WatchTunerState {

    /** The microphone is open but no note is heard. */
    data object Listening : WatchTunerState

    /**
     * A note is heard: [note] and how far off it is, in whole [cents] (positive = sharp), and
     * whether it has been [inTune] long enough to count (the phone's `InTuneDetector`).
     */
    data class Detected(val note: Note, val cents: Int, val inTune: Boolean = false) : WatchTunerState

    /** The microphone could not be used. */
    data class Failed(val error: WatchTunerError) : WatchTunerState
}

/** Why the watch tuner can't listen. */
enum class WatchTunerError {
    /** `RECORD_AUDIO` was taken away while the tuner was open. */
    PERMISSION_MISSING,

    /** Another app (a call, a voice assistant) holds the microphone. */
    MICROPHONE_BUSY,

    /** The microphone could not be opened or stopped delivering sound. */
    MICROPHONE_UNAVAILABLE,

    ;

    companion object {
        fun of(failure: Throwable): WatchTunerError = when (failure) {
            is AudioRecorderException.PermissionMissing -> PERMISSION_MISSING
            is AudioRecorderException.MicrophoneBusy -> MICROPHONE_BUSY
            else -> MICROPHONE_UNAVAILABLE
        }
    }
}
