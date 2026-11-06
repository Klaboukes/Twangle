package io.github.klaboukes.twangle.wear

import android.content.Context
import android.os.Process
import android.os.VibrationEffect
import io.github.klaboukes.twangle.core.audio.MetronomeBeat
import io.github.klaboukes.twangle.core.audio.MetronomePlayer
import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.locks.LockSupport

/**
 * How long the watch's motor runs for a metronome beat (Phase 80), always at full strength: a
 * plain beat as long as the tuner's tap ([TAP_MILLIS], the shortest felt while playing), the first
 * beat of the bar longer, [DOWNBEAT_TAP_MILLIS], since full strength is already the motor's most.
 * The downbeat's tap still leaves [TAP_GAP_MILLIS] of stillness before the next beat at [bpm] (from
 * 172 BPM it shortens, down to a plain tap's length at 240 BPM and over), so beats never run
 * together. A [bpm] of 0 (unknown) counts as the slowest. Pure.
 */
fun beatTapMillis(accent: BeatAccent, bpm: Int): Long {
    if (accent != BeatAccent.DOWNBEAT) return TAP_MILLIS
    val periodMillis = if (bpm > 0) 60_000L / bpm else Long.MAX_VALUE
    return (periodMillis - TAP_GAP_MILLIS).coerceIn(TAP_MILLIS, DOWNBEAT_TAP_MILLIS)
}

/**
 * Taps the wrist on every beat of the metronome, on time (Phase 80; measured on the Galaxy Watch 5
 * by `WatchMetronomeTimingMeasurement`, docs/architecture/strum-and-metronome.md, *On the watch*).
 *
 * It follows the player's [MetronomePlayer.upcomingBeats], each beat told 0.1–0.3 s before it is
 * heard, and a thread of its own at urgent-audio priority sleeps until [leadNanos] before each
 * beat's `heardAtNanos` and starts the motor then ([tap], with [beatTapMillis]), so the motor,
 * which takes about that long to start, moves on the beat. Silent or with the click, the clock is the
 * same. Tapping when a beat is told as heard ([MetronomePlayer.beats]) was too loose on the watch:
 * a thread of normal priority there woke up to 30 ms late, and the beat reached a tap 3–29 ms after
 * its moment; a parked urgent thread wakes within 2 ms.
 *
 * A beat whose moment comes after the metronome stopped ([playing] false then) isn't tapped.
 */
class WatchBeatTaps(
    private val leadNanos: Long = TAP_LEAD_MILLIS * 1_000_000L,
    private val tap: (beat: MetronomeBeat, millis: Long) -> Unit,
) {

    /** Taps each beat of [upcoming] at its moment while [playing], until the collection is cancelled. */
    suspend fun follow(upcoming: Flow<MetronomeBeat>, playing: () -> Boolean) {
        val timer = TapTimer(playing)
        timer.start()
        try {
            upcoming.collect { timer.add(it) }
        } finally {
            timer.close()
        }
    }

    /** The thread that waits for each beat's moment: a small ring of beats in the order told. */
    private inner class TapTimer(private val playing: () -> Boolean) : Thread("WatchBeatTaps") {
        private val lock = Any()
        private val due = LongArray(SLOTS)
        private val waiting = arrayOfNulls<MetronomeBeat>(SLOTS)
        private var first = 0
        private var count = 0

        @Volatile
        private var open = true

        init {
            isDaemon = true
        }

        fun add(beat: MetronomeBeat) {
            synchronized(lock) {
                if (count == SLOTS) dropFirst()
                val slot = (first + count) % SLOTS
                due[slot] = beat.heardAtNanos - leadNanos
                waiting[slot] = beat
                count++
            }
            LockSupport.unpark(this)
        }

        fun close() {
            open = false
            LockSupport.unpark(this)
        }

        override fun run() {
            // Not on the JVM of the unit tests, where Android's Process is a stub.
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) }
            while (open) {
                var next: MetronomeBeat? = null
                var wait = Long.MAX_VALUE
                synchronized(lock) {
                    if (count > 0) {
                        wait = due[first] - System.nanoTime()
                        if (wait <= 0) {
                            next = waiting[first]
                            dropFirst()
                        }
                    }
                }
                val beat = next
                when {
                    beat != null -> if (playing()) tap(beat, beatTapMillis(beat.accent, beat.bpm))
                    wait == Long.MAX_VALUE -> LockSupport.park(this)
                    else -> LockSupport.parkNanos(this, wait)
                }
            }
        }

        private fun dropFirst() {
            waiting[first] = null
            first = (first + 1) % SLOTS
            count--
        }
    }

    companion object {
        /** Beats waiting at once: a buffer's worth is two at 250 BPM. */
        private const val SLOTS = 8

        /** Taps through the watch's own motor, like the tuner's in-tune tap ([rememberWristTap]). */
        fun of(context: Context): WatchBeatTaps {
            val vibrator = context.vibrator()
            return WatchBeatTaps { _, millis ->
                if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createOneShot(millis, TAP_AMPLITUDE))
            }
        }
    }
}

/** The downbeat's tap, 100 ms longer than a plain beat's (to be felt on the owner's check, ROADMAP.md Phase 80). */
const val DOWNBEAT_TAP_MILLIS = 250L

/** The least stillness between a tap's end and the next beat, so two taps are felt as two. */
const val TAP_GAP_MILLIS = 100L

/**
 * How long before a beat the motor is asked to start: the mean of how long the Galaxy Watch 5's
 * vibrator service took from the call to the motor (1–27 ms, mean 12; strum-and-metronome.md,
 * *On the watch*).
 */
const val TAP_LEAD_MILLIS = 12L
