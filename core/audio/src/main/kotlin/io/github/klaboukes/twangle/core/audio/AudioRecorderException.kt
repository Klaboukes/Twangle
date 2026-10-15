package io.github.klaboukes.twangle.core.audio

/** Why [AudioRecorder.chunks] stopped. Thrown into the flow, so collectors can `catch` it. */
sealed class AudioRecorderException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** `RECORD_AUDIO` hasn't been granted. */
    class PermissionMissing(cause: Throwable? = null) :
        AudioRecorderException("RECORD_AUDIO permission is not granted", cause)

    /** No microphone input could be opened with this format. */
    class InitializationFailed(message: String, cause: Throwable? = null) :
        AudioRecorderException(message, cause)

    /** Another app is holding the microphone. Retrying later may work. */
    class MicrophoneBusy(message: String = "The microphone is in use by another app", cause: Throwable? = null) :
        AudioRecorderException(message, cause)

    /**
     * Recording started but a read failed, e.g. the audio server restarted, or reads kept coming
     * back empty ([errorCode] 0). Retrying may work.
     */
    class ReadFailed(val errorCode: Int, message: String = "AudioRecord.read failed with code $errorCode") :
        AudioRecorderException(message)
}
