package io.github.klaboukes.twangle.wear

import android.util.Log
import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.dsp.music.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

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
        fun of(failure: AudioRecorderException): WatchTunerError = when (failure) {
            is AudioRecorderException.PermissionMissing -> PERMISSION_MISSING
            is AudioRecorderException.MicrophoneBusy -> MICROPHONE_BUSY
            is AudioRecorderException.InitializationFailed, is AudioRecorderException.ReadFailed -> MICROPHONE_UNAVAILABLE
        }
    }
}

/** Where the watch tuner writes why listening stopped. */
fun interface WatchListenLog {
    fun stopped(cause: Throwable)

    companion object {
        /** Writes nothing: the JVM tests' default, where Android's log isn't there. */
        val NONE = WatchListenLog {}

        /** Android's log: a microphone failure as a warning, anything else (a bug in the app's code) as an error. */
        val ANDROID = WatchListenLog { cause ->
            if (cause is AudioRecorderException) {
                Log.w(TAG, "Listening stopped: the microphone failed", cause)
            } else {
                Log.e(TAG, "Listening stopped by an unexpected error", cause)
            }
        }

        private const val TAG = "WatchTuner"
    }
}

/**
 * These states, ending in [WatchTunerState.Failed] when the microphone fails ([AudioRecorderException]), as the phone's
 * tuner does (`showingMicFailures`, Phase 89); every failure is written to [log]. Anything else is a bug in the app's
 * own code: thrown on once written, so it reaches Play's crash reports instead of hiding behind "Microphone
 * unavailable" (Phase 91).
 */
internal fun Flow<WatchTunerState>.showingMicFailures(log: WatchListenLog): Flow<WatchTunerState> = catch { cause ->
    log.stopped(cause)
    if (cause !is AudioRecorderException) throw cause
    emit(WatchTunerState.Failed(WatchTunerError.of(cause)))
}
