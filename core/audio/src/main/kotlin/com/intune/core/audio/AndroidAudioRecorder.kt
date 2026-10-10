package com.intune.core.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Build
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
 * first, voice recognition as the fallback). While a Bluetooth headset is connected it asks for
 * the built-in mic ([InputDeviceSelector]). Chunks last about 21 ms at any rate unless [chunkSize]
 * is given.
 *
 * The blocking `read()` runs on a dedicated single thread so it never starves other coroutines.
 * Chunks come from a pool of [POOL_SIZE] arrays allocated once per collection, so recording
 * creates no garbage per chunk. Failures end the flow with an [AudioRecorderException].
 */
class AndroidAudioRecorder(
    context: Context,
    chunkSize: Int? = null,
) : AudioRecorder {

    private val appContext: Context = context.applicationContext
    private val audioManager: AudioManager = appContext.getSystemService(AudioManager::class.java)

    override val config: AudioConfig = SampleRateSelector.select(SampleRateSelector.nativeRateProperty(audioManager)) { rate ->
        AudioRecord.getMinBufferSize(rate, CHANNEL_MASK, ENCODING) > 0
    }.let { rate ->
        if (chunkSize == null) AudioConfig.forSampleRate(rate) else AudioConfig(rate, chunkSize)
    }

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
        val (record, input) = open()
        try {
            start(record)
            activeInput = input
            val pool = Array(POOL_SIZE) { FloatArray(config.chunkSize) }
            var next = 0
            var first = true
            while (true) {
                val chunk = pool[next]
                next = (next + 1) % POOL_SIZE
                readFully(record, chunk)
                if (first) {
                    first = false
                    // Android 10+ shares the mic: a higher-priority app (e.g. a call) silences us
                    // with zeros instead of failing. That is "busy" for a tuner.
                    if (isSilenced(record)) throw AudioRecorderException.MicrophoneBusy()
                }
                emit(chunk)
            }
        } finally {
            activeInput = null
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            record.release()
        }
    }
        // The collector holds 1 chunk, the channel up to CHANNEL_CAPACITY and the reader fills 1
        // more, so a pool of CHANNEL_CAPACITY + 2 arrays is never overwritten while still in use.
        .buffer(CHANNEL_CAPACITY)
        .flowOn(readDispatcher)

    /** Opens the first input in [inputs] that initializes. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun open(): Pair<AudioRecord, AudioInput> {
        var lastFailure: Exception? = null
        for (input in inputs) {
            val record = try {
                build(input)
            } catch (e: SecurityException) {
                throw AudioRecorderException.PermissionMissing(e)
            } catch (e: UnsupportedOperationException) {
                lastFailure = e
                continue
            } catch (e: IllegalArgumentException) {
                lastFailure = e
                continue
            }
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                // Keep a Bluetooth headset's call-quality mic out of the way (null = Android decides).
                InputDeviceSelector.preferredInput(audioManager)?.let { record.setPreferredDevice(it) }
                return record to input
            }
            record.release()
        }
        throw AudioRecorderException.InitializationFailed(
            "No audio input could be opened at ${config.sampleRate} Hz (tried $inputs)",
            lastFailure,
        )
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun build(input: AudioInput): AudioRecord {
        val minBytes = AudioRecord.getMinBufferSize(config.sampleRate, CHANNEL_MASK, ENCODING)
        // Room for a few chunks, so a late collector doesn't make AudioRecord drop samples.
        val bufferBytes = maxOf(minBytes, config.chunkSize * Float.SIZE_BYTES * BUFFERED_CHUNKS)
        return AudioRecord.Builder()
            .setAudioSource(input.androidSource)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(config.sampleRate)
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

    private fun isSilenced(record: AudioRecord): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            record.activeRecordingConfiguration?.isClientSilenced == true

    /** Blocks until [chunk] is full. */
    private fun readFully(record: AudioRecord, chunk: FloatArray) {
        var filled = 0
        while (filled < chunk.size) {
            val read = record.read(chunk, filled, chunk.size - filled, AudioRecord.READ_BLOCKING)
            if (read < 0) throw AudioRecorderException.ReadFailed(read)
            filled += read
        }
    }

    companion object {
        private const val CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
        private const val CHANNEL_CAPACITY = 2
        private const val POOL_SIZE = CHANNEL_CAPACITY + 2
        private const val BUFFERED_CHUNKS = 4
    }
}
