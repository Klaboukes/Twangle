package io.github.klaboukes.twangle.wear

import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.audio.AndroidMetronomePlayer
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The watch metronome's timing on a real watch (Phase 80; docs/architecture/strum-and-metronome.md,
 * *On the watch*): the phone's [AndroidMetronomePlayer] at 120 BPM in 4/4 for ten minutes, silent
 * (taps only) or with the click at a low volume (put back after), a wrist tap on every beat through
 * [WatchBeatTaps] (its own thread, each tap started [TAP_LEAD_MILLIS] before the beat's heard
 * moment, from the player's upcoming beats), on a screen kept on as the metronome page will keep
 * it. It measures
 * - the clock, as the phone's `MetronomeDriftMeasurement` does: the beats' heard moments against a
 *   perfect grid (ppm, wander after 1, 5 and 10 minutes), the gaps and their scatter, missed beats,
 *   underruns;
 * - the beats as told when heard ([AndroidMetronomePlayer.beats], what the beat lights follow): how
 *   late each reached a collector on `Dispatchers.Default`;
 * - the taps: exactly one per beat, when each vibrate call went out against its moment (the
 *   beat's heard moment less the lead), and how long the call took;
 * - the watch's timers beside them: how late a 250 ms wait wakes on `Dispatchers.Default` and on a
 *   parked thread at urgent-audio priority, the reason the taps run on a thread of their own.
 * The script adds the vibrator service's own record of the last taps (when each motor started after
 * its call, how long it ran).
 *
 * On demand only (`scripts/watch-metronome-timing.sh`): without the instrumentation argument
 * `watchMetronomeTiming=true` it is skipped, so the watch's normal test runs pay nothing.
 * `watchMetronomeMinutes` sets the length, `watchMetronomeSilent=false` plays the click,
 * `watchMetronomeUsage=media` sends the taps as media vibration. Writes
 * `watch-metronome-timing-<silent|audible>.txt` (the summary) and `.tsv` (every beat) to the app's
 * files, which the script reads back with `run-as`.
 */
@RunWith(AndroidJUnit4::class)
class WatchMetronomeTimingMeasurement {

    @get:Rule
    val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    private val context = instrumentation.targetContext

    @Test
    fun keepsTimeOnTheWrist() {
        assumeTrue("on demand: scripts/watch-metronome-timing.sh", arguments.getString(ARGUMENT) == "true")
        val minutes = arguments.getString(MINUTES_ARGUMENT)?.toIntOrNull() ?: 10
        val silent = arguments.getString(SILENT_ARGUMENT) != "false"
        val name = if (silent) "silent" else "audible"

        // A screen kept on while it plays, as the metronome page will.
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = false }
            }
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                BasicText("Timing ($name)\n$minutes min", style = TextStyle(color = Color.White, fontSize = 16.sp))
            }
        }
        compose.waitForIdle()

        val player = AndroidMetronomePlayer(context)
        val beatCount = minutes * BPM
        val periodNanos = 60.0e9 / BPM
        val heard = LongArray(beatCount) { NONE }
        val told = LongArray(beatCount) { NONE }
        val callStart = LongArray(beatCount) { NONE }
        val callEnd = LongArray(beatCount) { NONE }
        val tapMillis = LongArray(beatCount)
        val taps = IntArray(beatCount)
        var screenOffBeats = 0

        val vibrator = context.vibrator()
        val power = context.getSystemService(PowerManager::class.java)
        val battery = context.getSystemService(BatteryManager::class.java)
        val audioManager = context.getSystemService(AudioManager::class.java)
        val volumeBefore = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val batteryBefore = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val startedAt = System.nanoTime()

        // The usage the taps go out with: none by default (Samsung files them as touch), or media.
        val attributes = if (arguments.getString(USAGE_ARGUMENT) == "media") {
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA)
        } else {
            null
        }
        // Written only on the tap thread, then read here after it has finished.
        val beatTaps = WatchBeatTaps { beat, millis ->
            val i = beat.index.toInt()
            val start = System.nanoTime()
            val effect = VibrationEffect.createOneShot(millis, TAP_AMPLITUDE)
            if (attributes != null) vibrator.vibrate(effect, attributes) else vibrator.vibrate(effect)
            val end = System.nanoTime()
            if (i in 0 until beatCount && taps[i]++ == 0) {
                callStart[i] = start
                callEnd[i] = end
                tapMillis[i] = millis
            }
        }

        val probeCount = minutes * 240 + 240
        val timerLate = ArrayList<Double>(probeCount)
        val urgentLate = ArrayList<Double>(probeCount)
        val probing = AtomicBoolean(true)
        val urgentThread = thread(name = "urgent-timer") {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (probing.get()) {
                val due = System.nanoTime() + PROBE_NANOS
                LockSupport.parkNanos(PROBE_NANOS)
                if (urgentLate.size < probeCount) urgentLate += (System.nanoTime() - due) / 1e6
            }
        }
        try {
            if (!silent) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, QUIET_VOLUME, 0)
            player.setSilent(silent)
            runBlocking {
                val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    player.beats.takeWhile { it.index < beatCount }.collect {
                        val i = it.index.toInt()
                        told[i] = System.nanoTime()
                        heard[i] = it.heardAtNanos
                        if (!power.isInteractive) screenOffBeats++
                    }
                }
                val tapping = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    beatTaps.follow(player.upcomingBeats) { player.isPlaying.value }
                }
                // The watch's own timers beside the beats: how late a plain 250 ms wait wakes.
                val timers = launch(Dispatchers.Default) {
                    while (true) {
                        val due = System.nanoTime() + PROBE_NANOS
                        delay(PROBE_NANOS / 1_000_000)
                        if (timerLate.size < probeCount) timerLate += (System.nanoTime() - due) / 1e6
                    }
                }
                player.start(BPM, Meter.FOUR_FOUR)
                withTimeout(minutes * 60_000L + SLACK_MILLIS) { collector.join() }
                timers.cancel()
                player.stop()
                tapping.cancel()
                withTimeout(SLACK_MILLIS) { player.isPlaying.first { !it } }
            }
        } finally {
            player.stop()
            player.setSilent(false)
            probing.set(false)
            urgentThread.join(SLACK_MILLIS)
            if (!silent) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeBefore, 0)
        }
        val wallMinutes = (System.nanoTime() - startedAt) / 60e9
        val batteryAfter = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val missed = heard.count { it == NONE }
        val underruns = underrunsLogged()
        val missedTaps = (0 until beatCount).count { heard[it] != NONE && taps[it] == 0 }
        val doubledTaps = taps.count { it > 1 }

        // From beat WARMUP on, the heard moments come from the output's timestamps.
        val n = (WARMUP until beatCount).filter { heard[it] != NONE }
        val gaps = n.zipWithNext().filter { (a, b) -> b == a + 1 }
            .map { (a, b) -> (heard[b] - heard[a] - periodNanos) / 1e6 }
        // Least squares: heard = intercept + slope · index.
        val meanX = n.average()
        val meanY = n.map { (heard[it] - heard[n[0]]).toDouble() }.average()
        var sxy = 0.0
        var sxx = 0.0
        for (i in n) {
            val dx = i - meanX
            sxy += dx * ((heard[i] - heard[n[0]]) - meanY)
            sxx += dx * dx
        }
        val slope = sxy / sxx
        val intercept = meanY - slope * meanX
        val ppm = (slope - periodNanos) / periodNanos * 1e6
        val scatter = n.map { ((heard[it] - heard[n[0]]) - (intercept + slope * it)) / 1e6 }
        fun wander(atMinute: Int): Double? {
            val i = minOf(atMinute * BPM, beatCount - 1)
            if (heard[i] == NONE) return null
            return (heard[i] - heard[n[0]] - (i - n[0]) * periodNanos) / 1e6
        }
        val lateTold = n.filter { told[it] != NONE }.map { (told[it] - heard[it]) / 1e6 }.sorted()
        val tapped = n.filter { callStart[it] != NONE }
        val lateTap = tapped.map { (callStart[it] - (heard[it] - TAP_LEAD_MILLIS * 1_000_000L)) / 1e6 }.sorted()
        val callTook = tapped.map { (callEnd[it] - callStart[it]) / 1e6 }.sorted()
        val tapLengths = tapped.groupingBy { tapMillis[it] }.eachCount().toSortedMap()

        val summary = buildString {
            appendLine(
                "Watch metronome timing ($name): $BPM BPM in 4/4 for $minutes min (%.1f min on the clock), $beatCount beats"
                    .format(wallMinutes),
            )
            appendLine(
                "Watch: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}; output ${player.sampleRate} Hz, float; " +
                    "motor amplitude control ${vibrator.hasAmplitudeControl()}; taps as ${if (attributes != null) "media" else "default"} vibration; " +
                    if (silent) {
                        "clicks silent"
                    } else {
                        "clicks at media volume $QUIET_VOLUME of ${audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}"
                    },
            )
            appendLine("Missed beats: $missed; underruns logged: $underruns; beats with the screen off: $screenOffBeats")
            appendLine("Audio clock vs system clock: %+.1f ppm (one beat every %.6f ms of system time)".format(ppm, slope / 1e6))
            appendLine(
                listOf(1, 5, minutes).filter { it <= minutes }.distinct().let { marks ->
                    "Off a perfect wall-clock grid after ${marks.joinToString(" / ")} min: " +
                        marks.joinToString(" / ") { m -> wander(m)?.let { "%+.2f ms".format(it) } ?: "-" }
                },
            )
            appendLine("Heard gaps minus 500 ms: %+.3f .. %+.3f ms, rms %.3f ms".format(gaps.min(), gaps.max(), rms(gaps)))
            appendLine("Scatter about the fitted line: max %.3f ms, rms %.3f ms".format(scatter.maxOf { abs(it) }, rms(scatter)))
            appendLine("Beat told as heard, reaching Dispatchers.Default after its moment: ${spread(lateTold)}")
            appendLine(
                "Taps: ${tapped.size} of ${n.size} beats after the first $WARMUP; missed $missedTaps, doubled $doubledTaps; " +
                    tapLengths.entries.joinToString { (ms, count) -> "$count of $ms ms" },
            )
            appendLine("Vibrate call after its moment ($TAP_LEAD_MILLIS ms before the beat's heard moment): ${spread(lateTap)}")
            appendLine("Vibrate call itself took: ${spread(callTook)}")
            appendLine("A 250 ms delay on Dispatchers.Default woke late by: ${spread(timerLate.sorted())}")
            appendLine("A 250 ms park on an urgent-audio thread woke late by: ${spread(urgentLate.sorted())}")
            appendLine("Battery: $batteryBefore % -> $batteryAfter %")
        }
        Log.i(TAG, summary)
        writeOutput(name, summary, beatCount, heard, told, callStart, callEnd, tapMillis, taps)

        assertEquals("missed beats", 0, missed)
        assertEquals("underruns", 0, underruns)
        assertEquals("missed taps", 0, missedTaps)
        assertEquals("doubled taps", 0, doubledTaps)
        // The watch's output timestamps are noisier than the phone's (a 40 ms period): 5 ms, not 2.
        assertTrue("a gap off by ${gaps.maxOf { abs(it) }} ms", gaps.all { abs(it) < 5.0 })
    }

    /** The player's "ran dry" warnings in this process's log. */
    private fun underrunsLogged(): Int {
        val pfd = instrumentation.uiAutomation.executeShellCommand("logcat -d --pid=${Process.myPid()} -s MetronomePlayer:W")
        val text = android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        return text.lines().count { "ran dry" in it }
    }

    private fun writeOutput(
        name: String,
        summary: String,
        beatCount: Int,
        heard: LongArray,
        told: LongArray,
        callStart: LongArray,
        callEnd: LongArray,
        tapMillis: LongArray,
        taps: IntArray,
    ) {
        val dir = context.filesDir
        File(dir, "watch-metronome-timing-$name.txt").writeText(summary)
        File(dir, "watch-metronome-timing-$name.tsv").bufferedWriter().use { out ->
            out.write("beat\theardAtNanos\ttoldAtNanos\tvibrateAtNanos\tvibrateReturnedAtNanos\ttapMillis\ttaps\n")
            for (i in 0 until beatCount) {
                out.write("$i\t${heard[i]}\t${told[i]}\t${callStart[i]}\t${callEnd[i]}\t${tapMillis[i]}\t${taps[i]}\n")
            }
        }
    }

    private fun spread(sorted: List<Double>): String =
        if (sorted.isEmpty()) {
            "-"
        } else {
            "min %.1f, median %.1f, 95%% %.1f, max %.1f ms".format(
                sorted.first(), sorted[sorted.size / 2], sorted[(sorted.size * 95) / 100], sorted.last(),
            )
        }

    private fun rms(values: List<Double>) = sqrt(values.sumOf { it * it } / values.size)

    private companion object {
        const val TAG = "WatchMetronomeTiming"
        const val ARGUMENT = "watchMetronomeTiming"
        const val MINUTES_ARGUMENT = "watchMetronomeMinutes"
        const val SILENT_ARGUMENT = "watchMetronomeSilent"
        const val USAGE_ARGUMENT = "watchMetronomeUsage"
        const val BPM = 120
        const val QUIET_VOLUME = 1
        const val WARMUP = 4
        const val NONE = Long.MIN_VALUE
        const val SLACK_MILLIS = 5_000L
        const val PROBE_NANOS = 250_000_000L
    }
}
