package io.github.klaboukes.twangle.core.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * [AudioRecorder] backed by `android.media.AudioRecord`: mono, `ENCODING_PCM_FLOAT`, at the
 * device's native rate ([SampleRateSelector]), inputs from [AudioSourceSelector] (unprocessed
 * first, voice recognition as the fallback). If no input opens at that rate, the next rate the
 * device accepts is tried ([openFirst]) and [config] changes to it, for this and later sessions.
 * An input that records only exact zeros from its first sample gives way to the next one within
 * the session ([FallbackReader]), and is skipped for the app's life once that one hears the room.
 * While a Bluetooth headset is connected it asks for the built-in mic ([InputDeviceSelector]).
 * Chunks last about 21 ms at any rate unless [chunkSize] is given.
 *
 * The blocking `read()` runs on a dedicated single thread so it never starves other coroutines.
 * Chunks come from a pool of [POOL_SIZE] arrays allocated once per collection, so recording
 * creates no garbage per chunk. [ChunkReader] fills them and notices a microphone that stops
 * really listening mid-session; on Android 10+ an `AudioRecordingCallback` tells it when Android
 * silences this recording for another app (a call, an assistant). Failures end the flow with an
 * [AudioRecorderException].
 */
class AndroidAudioRecorder(
    context: Context,
    private val chunkSize: Int? = null,
) : AudioRecorder {

    private val appContext: Context = context.applicationContext
    private val audioManager: AudioManager = appContext.getSystemService(AudioManager::class.java)

    // Every rate the device says it can record at, best first.
    private val rates: List<Int> = SampleRateSelector.candidates(SampleRateSelector.nativeRateProperty(audioManager))
        .filter { AudioRecord.getMinBufferSize(it, CHANNEL_MASK, ENCODING) > 0 }

    @Volatile
    override var config: AudioConfig = configFor(rates.firstOrNull() ?: AudioConfig.DEFAULT_SAMPLE_RATE)
        private set

    private val allInputs: List<AudioInput> =
        AudioSourceSelector.candidates(AudioSourceSelector.supportsUnprocessed(audioManager))

    private val readDispatcher = Dispatchers.IO.limitedParallelism(1)

    /** The input currently recording, or null when the microphone is closed. */
    @Volatile
    override var activeInput: AudioInput? = null
        private set

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun chunks(): Flow<FloatArray> = flow {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw AudioRecorderException.PermissionMissing()
        }
        val inputs = skippedInputs.usable(allInputs)
        val (record, rate, input) = open(inputs)
        val reader = FallbackReader(
            first = begin(record, input),
            firstInput = input,
            inputs = inputs,
            sampleRate = rate,
            skipped = skippedInputs,
            open = { next ->
                Log.w(TAG, "$activeInput recorded only zeros from its first sample: trying $next")
                tryBuild(rate, next)?.let { begin(it, next) }
            },
            release = { it.end() },
        )
        try {
            val opened = configFor(rate)
            config = opened
            val pool = Array(POOL_SIZE) { FloatArray(opened.chunkSize) }
            var next = 0
            while (true) {
                val chunk = pool[next]
                next = (next + 1) % POOL_SIZE
                reader.fill(chunk)
                emit(chunk)
            }
        } finally {
            activeInput = null
            reader.close()
        }
    }
        // The collector holds 1 chunk, the channel up to CHANNEL_CAPACITY and the reader fills 1
        // more, so a pool of CHANNEL_CAPACITY + 2 arrays is never overwritten while still in use.
        .buffer(CHANNEL_CAPACITY)
        .flowOn(readDispatcher)

    private fun configFor(rate: Int): AudioConfig =
        if (chunkSize == null) AudioConfig.forSampleRate(rate) else AudioConfig(rate, chunkSize)

    /**
     * Opens the first of [inputs] that initializes, at the current rate first and then at the
     * device's other rates, best first.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun open(inputs: List<AudioInput>): OpenedMic<AudioRecord> {
        val current = config.sampleRate
        val tried = (listOf(current) + rates).distinct()
        var lastFailure: Exception? = null
        val opened = openFirst(tried, inputs) { rate, input ->
            tryBuild(rate, input) { lastFailure = it }
        } ?: throw AudioRecorderException.InitializationFailed(
            "No audio input could be opened at $tried Hz (tried $inputs)",
            lastFailure,
        )
        if (opened.sampleRate != current) Log.i(TAG, "No input opened at $current Hz: recording at ${opened.sampleRate} Hz")
        return opened
    }

    /**
     * An initialized [AudioRecord] for [input] at [rate], or null when the phone refuses that
     * combination ([onFailure] gets why, when Android said). A missing permission throws.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun tryBuild(rate: Int, input: AudioInput, onFailure: (Exception) -> Unit = {}): AudioRecord? {
        val record = try {
            build(rate, input)
        } catch (e: SecurityException) {
            throw AudioRecorderException.PermissionMissing(e)
        } catch (e: UnsupportedOperationException) {
            onFailure(e)
            return null
        } catch (e: IllegalArgumentException) {
            onFailure(e)
            return null
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        // Keep a Bluetooth headset's call-quality mic out of the way (null = Android decides).
        InputDeviceSelector.preferredInput(audioManager)?.let { record.setPreferredDevice(it) }
        return record
    }

    /**
     * Starts recording [record] and follows Android's silencing of it; [input] becomes
     * [activeInput]. Releases it if it can't start (the microphone is busy).
     */
    private fun begin(record: AudioRecord, input: AudioInput): RecordInput {
        val mic = RecordInput(record)
        try {
            mic.watch()
            start(record)
            mic.checkSilenced()
        } catch (e: Throwable) {
            mic.end()
            throw e
        }
        activeInput = input
        return mic
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun build(rate: Int, input: AudioInput): AudioRecord {
        val minBytes = AudioRecord.getMinBufferSize(rate, CHANNEL_MASK, ENCODING)
        // Room for a few chunks, so a late collector doesn't make AudioRecord drop samples.
        val bufferBytes = maxOf(minBytes, configFor(rate).chunkSize * Float.SIZE_BYTES * BUFFERED_CHUNKS)
        return AudioRecord.Builder()
            .setAudioSource(input.androidSource)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setChannelMask(CHANNEL_MASK)
                    .setEncoding(ENCODING)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .build()
    }

    /** Before Android 10, a mic held by another app makes recording fail to start. */
    private fun start(record: AudioRecord) {
        try {
            record.startRecording()
        } catch (e: IllegalStateException) {
            throw AudioRecorderException.MicrophoneBusy(cause = e)
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            throw AudioRecorderException.MicrophoneBusy()
        }
    }

    /**
     * An open [AudioRecord] as [ChunkReader] sees it. From Android 10 the microphone is shared: an
     * app with priority (a call, an assistant, another recorder in front) silences us with zeros
     * instead of making reads fail, which is "busy" for a tuner. Android reports it to a
     * [SilencingCallback], which only sets a flag, so the read loop pays one volatile read.
     */
    private inner class RecordInput(private val record: AudioRecord) : MicInput {

        @Volatile
        var silenced = false

        private val callback: SilencingCallback? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) SilencingCallback(record.audioSessionId, this) else null

        override val isSilenced: Boolean get() = silenced

        override fun read(buffer: FloatArray, offset: Int, count: Int): Int =
            record.read(buffer, offset, count, AudioRecord.READ_BLOCKING)

        /** Starts following Android's silencing; callbacks come on the main thread, where they only set the flag. */
        fun watch() {
            callback?.let { audioManager.registerAudioRecordingCallback(it, null) }
        }

        /** Once recording: whether Android silences it from the start. */
        fun checkSilenced() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && record.activeRecordingConfiguration?.isClientSilenced == true) {
                silenced = true
            }
        }

        fun unwatch() {
            callback?.let { runCatching { audioManager.unregisterAudioRecordingCallback(it) } }
        }

        /** Stops following, stops recording and lets go of the microphone. */
        fun end() {
            unwatch()
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            record.release()
        }
    }

    /** Copies Android's "silenced" for the recording with [sessionId] to [input]. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private class SilencingCallback(private val sessionId: Int, private val input: RecordInput) :
        AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
            for (config in configs) {
                if (config.clientAudioSessionId == sessionId) input.silenced = config.isClientSilenced
            }
        }
    }

    companion object {
        /**
         * The inputs found to record nothing at all on this phone, for every recorder in the app
         * (the tuner's, the metronome's Listen, ear training's) until the app's process ends.
         */
        internal val skippedInputs = SkippedInputs()

        private const val TAG = "AudioRecorder"
        private const val CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
        private const val CHANNEL_CAPACITY = 2
        private const val POOL_SIZE = CHANNEL_CAPACITY + 2
        private const val BUFFERED_CHUNKS = 4
    }
}
