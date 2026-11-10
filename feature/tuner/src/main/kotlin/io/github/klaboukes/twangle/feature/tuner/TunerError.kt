package io.github.klaboukes.twangle.feature.tuner

import android.util.Log
import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/** Why the tuner can't listen right now. Every case can be retried. */
enum class TunerError {
    /** `RECORD_AUDIO` was revoked while the screen was open. */
    MIC_PERMISSION_MISSING,

    /** Another app (e.g. a phone call) is using the microphone. */
    MIC_BUSY,

    /** The microphone couldn't be opened at all. */
    MIC_UNAVAILABLE,

    /** Recording stopped unexpectedly, e.g. the audio system restarted. */
    MIC_STOPPED,
}

/** Maps a failure of the microphone to what the UI tells the user. */
fun AudioRecorderException.toTunerError(): TunerError = when (this) {
    is AudioRecorderException.PermissionMissing -> TunerError.MIC_PERMISSION_MISSING
    is AudioRecorderException.MicrophoneBusy -> TunerError.MIC_BUSY
    is AudioRecorderException.InitializationFailed -> TunerError.MIC_UNAVAILABLE
    is AudioRecorderException.ReadFailed -> TunerError.MIC_STOPPED
}

/** Where the tuner and the pitch trail write why they stopped listening ([showingMicFailures]). */
fun interface ListenLog {
    fun stopped(cause: Throwable)

    companion object {
        /** Writes nothing: the JVM tests' default, where Android's log isn't there. */
        val NONE = ListenLog {}

        /** Android's log: a microphone failure as a warning, anything else (a bug in the app's code) as an error. */
        val ANDROID = ListenLog { cause ->
            if (cause is AudioRecorderException) {
                Log.w(TAG, "Listening stopped: the microphone failed", cause)
            } else {
                Log.e(TAG, "Listening stopped by an unexpected error", cause)
            }
        }

        private const val TAG = "Tuner"
    }
}

/**
 * These readings, ending in a [TunerUiState.Error] when the microphone fails ([AudioRecorderException]); every failure
 * is written to [log]. Anything else is a bug in the app's own code: thrown on once written, so it closes the app and
 * reaches Play's crash reports, instead of hiding behind "Recording stopped" as it did until Phase 89.
 */
internal fun Flow<TunerUiState>.showingMicFailures(log: ListenLog): Flow<TunerUiState> = catch { cause ->
    log.stopped(cause)
    if (cause !is AudioRecorderException) throw cause
    emit(TunerUiState.Error(cause.toTunerError()))
}
