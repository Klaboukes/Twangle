package io.github.klaboukes.twangle.core.audio

import android.media.AudioManager
import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The metronome's long-run steadiness on the phone (ARCHITECTURE.md §3, *Metronome*): plays the real
 * [AndroidMetronomePlayer] at 120 BPM for ten minutes, quietly, and measures
 * - the beats on the sample clock: every click of ten minutes rendered at the phone's rate in the
 *   player's chunks lands within half a sample of its ideal moment (no drift, by construction);
 * - the audio clock against the system clock: the beats' heard moments (from `AudioTrack.getTimestamp`)
 *   fitted to a line, whose slope against the ideal 500 ms gives the speaker clock's rate error in
 *   ppm, and how far the beats wander from a perfect wall-clock grid after 1, 5 and 10 minutes;
 * - the jitter of the beat reports: gaps between heard moments, scatter about the fitted line, how
 *   late each beat reaches the screen after its sound; and missed beats and underruns.
 *
 * On demand only (`scripts/metronome-drift.sh`): without the instrumentation argument
 * `metronomeDrift=true` it is skipped, so normal UI runs pay nothing. `metronomeDriftMinutes`
 * shortens it for a quick try. Writes `metronome-drift.txt` (the summary) and `metronome-drift.tsv`
 * (every beat) to the additional test output, which `scripts/ui-tests.sh` pulls.
 */
@RunWith(AndroidJUnit4::class)
class MetronomeDriftMeasurement {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    private val context = instrumentation.targetContext

    @Test
    fun staysOnTheBeatForTenMinutes() {
        assumeTrue("on demand: scripts/metronome-drift.sh", arguments.getString(ARGUMENT) == "true")
        val minutes = arguments.getString(MINUTES_ARGUMENT)?.toIntOrNull() ?: 10
        val player = AndroidMetronomePlayer(context)
        val rate = player.sampleRate
        val beatCount = minutes * BPM // one beat per 60 / BPM s: BPM beats a minute
        val periodNanos = 60.0e9 / BPM

        val gridErrorSamples = sampleGridError(rate, minutes)

        val heard = LongArray(beatCount) { NONE }
        val told = LongArray(beatCount) { NONE }
        val audioManager = context.getSystemService(AudioManager::class.java)
        val volumeBefore = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val startedAt = System.nanoTime()
        try {
            // Quiet but not silent: at 0 the player would show the volume panel.
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, QUIET_VOLUME, 0)
            runBlocking {
                val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    player.beats.takeWhile { it.index < beatCount }.collect {
                        val now = System.nanoTime()
                        heard[it.index.toInt()] = it.heardAtNanos
                        told[it.index.toInt()] = now
                    }
                }
                player.start(BPM, Meter.FOUR_FOUR)
                // The collector ends at the first beat past the last one measured.
                withTimeout(minutes * 60_000L + SLACK_MILLIS) { collector.join() }
                player.stop()
                withTimeout(SLACK_MILLIS) { player.isPlaying.first { !it } }
            }
        } finally {
            player.stop()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeBefore, 0)
        }
        val wallMinutes = (System.nanoTime() - startedAt) / 60e9

        val missed = heard.count { it == NONE }
        val underruns = underrunsLogged()

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
        // How far the beats are from a perfect wall-clock grid started at beat WARMUP.
        fun wander(atMinute: Int): Double? {
            val i = minOf(atMinute * BPM, beatCount - 1)
            if (heard[i] == NONE) return null
            return (heard[i] - heard[n[0]] - (i - n[0]) * periodNanos) / 1e6
        }
        val late = n.filter { told[it] != NONE }.map { (told[it] - heard[it]) / 1e6 }.sorted()

        val summary = buildString {
            appendLine("Metronome drift: $BPM BPM in 4/4 for $minutes min (%.1f min on the clock), $beatCount beats".format(wallMinutes))
            appendLine("Phone: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}; output $rate Hz, float")
            appendLine("Sample clock: largest beat error %.3f samples from the ideal grid over $minutes min (≤ 0.5 = no drift)".format(gridErrorSamples))
            appendLine("Missed beats: $missed; underruns logged: $underruns")
            appendLine("Audio clock vs system clock: %+.1f ppm (one beat every %.6f ms of system time)".format(ppm, slope / 1e6))
            appendLine(
                listOf(1, 5, minutes).filter { it <= minutes }.distinct().let { marks ->
                    "Off a perfect wall-clock grid after ${marks.joinToString(" / ")} min: " +
                        marks.joinToString(" / ") { m -> wander(m)?.let { "%+.2f ms".format(it) } ?: "-" }
                },
            )
            appendLine("Heard gaps minus 500 ms: %+.3f .. %+.3f ms, rms %.3f ms".format(gaps.min(), gaps.max(), rms(gaps)))
            appendLine("Scatter about the fitted line: max %.3f ms, rms %.3f ms".format(scatter.maxOf { abs(it) }, rms(scatter)))
            appendLine(
                "Beat told after its sound: min %.1f, median %.1f, 95%% %.1f, max %.1f ms".format(
                    late.first(), late[late.size / 2], late[(late.size * 95) / 100], late.last(),
                ),
            )
        }
        Log.i(TAG, summary)
        writeOutput(summary, beatCount, heard, told)

        assertTrue("beats off the sample grid by $gridErrorSamples samples", gridErrorSamples <= 0.5)
        assertEquals("missed beats", 0, missed)
        assertEquals("underruns", 0, underruns)
        assertTrue("a gap off by ${gaps.maxOf { abs(it) }} ms", gaps.all { abs(it) < 2.0 })
    }

    /** The largest distance, in samples, of any beat of [minutes] minutes from its ideal moment. */
    private fun sampleGridError(rate: Int, minutes: Int): Double {
        val track = ClickTrack(rate, BPM, Meter.FOUR_FOUR)
        val chunk = FloatArray(rate / 100) // the player's 10 ms chunks
        var worst = 0.0
        val total = minutes * 60L * rate
        while (track.framesRendered < total) {
            track.render(chunk) { index, _, _, frame ->
                worst = maxOf(worst, abs(frame - index * 60.0 * rate / BPM))
            }
        }
        return worst
    }

    /** The player's "ran dry" warnings in this process's log. */
    private fun underrunsLogged(): Int {
        val pfd = instrumentation.uiAutomation.executeShellCommand("logcat -d --pid=${Process.myPid()} -s MetronomePlayer:W")
        val text = android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        return text.lines().count { "ran dry" in it }
    }

    private fun writeOutput(summary: String, beatCount: Int, heard: LongArray, told: LongArray) {
        val dir = arguments.getString("additionalTestOutputDir")?.let(::File) ?: return
        dir.mkdirs()
        File(dir, "metronome-drift.txt").writeText(summary)
        File(dir, "metronome-drift.tsv").bufferedWriter().use { out ->
            out.write("beat\theardAtNanos\ttoldAtNanos\n")
            for (i in 0 until beatCount) out.write("$i\t${heard[i]}\t${told[i]}\n")
        }
    }

    private fun rms(values: List<Double>) = sqrt(values.sumOf { it * it } / values.size)

    private companion object {
        const val TAG = "MetronomeDrift"
        const val ARGUMENT = "metronomeDrift"
        const val MINUTES_ARGUMENT = "metronomeDriftMinutes"
        const val BPM = 120
        const val QUIET_VOLUME = 1
        const val WARMUP = 4
        const val NONE = Long.MIN_VALUE
        const val SLACK_MILLIS = 5_000L
    }
}
