package com.intune.core.audio

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * [AudioRecorder] backed by `android.media.AudioRecord`: mono, `ENCODING_PCM_FLOAT`, inputs from
 * [AudioSourceSelector] (unprocessed first, voice recognition as the fallback).
 *
 * The blocking `read()` runs on a dedicated single thread so it never starves other coroutines.
 * Chunks come from a pool of [POOL_SIZE] arrays allocated once per collection, so recording
 * creates no garbage per chunk.
 */
class AndroidAudioRecorder(
    context: Context,
    override val config: AudioConfig = AudioConfig(),
) : AudioRecorder {

    private val inputs: List<AudioInput> = AudioSourceSelector.candidates(
        AudioSourceSelector.supportsUnprocessed(context.getSystemService(AudioManager::class.java)),
    )

    private val readDispatcher = Dispatchers.IO.limitedParallelism(1)

    /** The input currently recording, or null when the microphone is closed. For diagnostics. */
    @Volatile
    var activeInput: AudioInput? = null
        private set

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun chunks(): Flow<FloatArray> = flow {
        val (record, input) = open()
        try {
            record.startRecording()
            activeInput = input
            val pool = Array(POOL_SIZE) { FloatArray(config.chunkSize) }
            var next = 0
            while (true) {
                val chunk = pool[next]
                next = (next + 1) % POOL_SIZE
                readFully(record, chunk)
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
        for (input in inputs) {
            val record = try {
                build(input)
            } catch (_: UnsupportedOperationException) {
                continue
            }
            if (record.state == AudioRecord.STATE_INITIALIZED) return record to input
            record.release()
        }
        error("No audio input could be opened (tried $inputs)")
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

    /** Blocks until [chunk] is full. */
    private fun readFully(record: AudioRecord, chunk: FloatArray) {
        var filled = 0
        while (filled < chunk.size) {
            val read = record.read(chunk, filled, chunk.size - filled, AudioRecord.READ_BLOCKING)
            check(read >= 0) { "AudioRecord.read failed with code $read" }
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
