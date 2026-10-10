package com.intune.feature.tuner

import com.intune.core.audio.AudioRecorderException

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

/** Maps a failure from the audio pipeline to what the UI tells the user. */
fun Throwable.toTunerError(): TunerError = when (this) {
    is AudioRecorderException.PermissionMissing -> TunerError.MIC_PERMISSION_MISSING
    is AudioRecorderException.MicrophoneBusy -> TunerError.MIC_BUSY
    is AudioRecorderException.InitializationFailed -> TunerError.MIC_UNAVAILABLE
    is AudioRecorderException.ReadFailed -> TunerError.MIC_STOPPED
    else -> TunerError.MIC_STOPPED
}
