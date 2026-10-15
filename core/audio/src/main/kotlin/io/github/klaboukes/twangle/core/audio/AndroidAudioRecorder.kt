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

    private val inputs: List<AudioInput> =
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
        val (record, rate, input) = open()
        val mic = RecordInput(record)
        try {
            mic.watch()
            start(record)
            mic.checkSilenced()
            activeInput = input
            val opened = configFor(rate)
            config = opened
            val reader = ChunkReader(mic, rate)
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
            mic.unwatch()
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            record.release()
        }
    }
        // The collector holds 1 chunk, the channel up to CHANNEL_CAPACITY and the reader fills 1
        // more, so a pool of CHANNEL_CAPACITY + 2 arrays is never overwritten while still in use.
        .buffer(CHANNEL_CAPACITY)
        .flowOn(readDispatcher)

    private fun configFor(rate: Int): AudioConfig =
        if (chunkSize == null) AudioConfig.forSampleRate(rate) else AudioConfig(rate, chunkSize)

    /**
     * Opens the first input that initializes, at the current rate first and then at the device's
     * other rates, best first.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun open(): OpenedMic<AudioRecord> {
        val current = config.sampleRate
        val tried = (listOf(current) + rates).distinct()
        var lastFailure: Exception? = null
        val opened = openFirst(tried, inputs) { rate, input ->
            val record = try {
                build(rate, input)
            } catch (e: SecurityException) {
                throw AudioRecorderException.PermissionMissing(e)
            } catch (e: UnsupportedOperationException) {
                lastFailure = e
                null
            } catch (e: IllegalArgumentException) {
                lastFailure = e
                null
            }
            if (record != null && record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                null
            } else {
                record
            }
        } ?: throw AudioRecorderException.InitializationFailed(
            "No audio input could be opened at $tried Hz (tried $inputs)",
            lastFailure,
        )
        if (opened.sampleRate != current) Log.i(TAG, "No input opened at $current Hz: recording at ${opened.sampleRate} Hz")
        // Keep a Bluetooth headset's call-quality mic out of the way (null = Android decides).
        InputDeviceSelector.preferredInput(audioManager)?.let { opened.mic.setPreferredDevice(it) }
        return opened
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
        private const val TAG = "AudioRecorder"
        private const val CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
        private const val CHANNEL_CAPACITY = 2
        private const val POOL_SIZE = CHANNEL_CAPACITY + 2
        private const val BUFFERED_CHUNKS = 4
    }
}
