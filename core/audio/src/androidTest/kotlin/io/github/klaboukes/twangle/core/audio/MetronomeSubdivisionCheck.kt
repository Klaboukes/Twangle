package io.github.klaboukes.twangle.core.audio

import android.media.AudioManager
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.SpeedTrainer
import io.github.klaboukes.twangle.core.dsp.metronome.Subdivision
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Phase 62's phone check of the clicks between the beats and the speed trainer
 * (docs/architecture/strum-and-metronome.md, *Metronome*), with the real [AndroidMetronomePlayer] at a
 * low media volume (put back after):
 * - every subdivision each meter offers, at 60 and at 250 BPM, a few seconds each: no beat missed,
 *   the heard gaps, and the speaker's underruns (a lost or broken click would show as one);
 * - 250 BPM in sixteenths (a click every 60 ms) for a while: underruns and the heard gaps;
 * - the speed trainer from 200 to 250 BPM, +10 every bar, in sixteenths: each step on its downbeat,
 *   held at the goal, the gaps following each tempo.
 *
 * On demand only: `scripts/ui-tests.sh :core:audio -c MetronomeSubdivisionCheck -a metronomeSubdivisions=true`
 * (`-a metronomeSubdivisionsSeconds=…` sets the long run, 120 s by default; about 6 minutes in all).
 * Without the argument it is skipped, so normal UI runs pay nothing. Appends to
 * `metronome-subdivisions.txt` in the additional test output, which `scripts/ui-tests.sh` pulls.
 */
@RunWith(AndroidJUnit4::class)
class MetronomeSubdivisionCheck {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    private val context = instrumentation.targetContext

    private class Run(val beats: List<MetronomeBeat>, val underruns: Int, val gaps: List<Double>) {
        val missed get() = beats.zipWithNext().count { (a, b) -> b.index != a.index + 1 } +
            (if (beats.isEmpty() || beats[0].index != 0L) 1 else 0)
    }

    @Test
    fun everySubdivisionInEveryMeterAtSlowAndFastTempos() = measure { player, summary, problems ->
        val longSeconds = arguments.getString(SECONDS_ARGUMENT)?.toIntOrNull() ?: 120
        for (meter in Meter.entries) {
            for (subdivision in Subdivision.offeredIn(meter)) {
                for (bpm in listOf(SLOW, FAST)) {
                    val count = if (bpm == SLOW) 5 else 14
                    val run = play(player, bpm, meter, subdivision, null) { it.index < count }
                    val line = "%-11s %-5s %3d BPM: %2d beats, missed %d, underruns %d, gaps after the first two %s".format(
                        meter, subdivision, bpm, run.beats.size, run.missed, run.underruns, range(run.gaps.drop(1)),
                    )
                    summary.appendLine(line)
                    if (run.missed > 0 || run.underruns > 0 || run.beats.size != count) problems += line
                }
            }
        }

        val longCount = longSeconds * FAST / 60
        val long = play(player, FAST, Meter.FOUR_FOUR, Subdivision.FOUR, null) { it.index < longCount }
        val steady = long.gaps.drop(WARMUP)
        summary.appendLine(
            "Sixteenths at 250 BPM for $longSeconds s: ${long.beats.size} beats (${long.beats.size * 4} clicks), " +
                "missed ${long.missed}, underruns ${long.underruns}, gaps minus 240 ms %s, rms %.3f ms".format(range(steady), rms(steady)),
        )
        if (long.missed > 0 || long.underruns > 0 || steady.any { abs(it) >= 2.0 }) problems += "the long run at 250 BPM"
    }

    @Test
    fun theSpeedTrainerClimbsToItsGoalInSixteenths() = measure { player, summary, problems ->
        val trainer = SpeedTrainer.of(200, 250, 10, 1)
        var goalBar: Long? = null
        val climb = play(player, trainer.startBpm, Meter.FOUR_FOUR, Subdivision.FOUR, trainer) {
            if (goalBar == null && it.trainerBar >= 0 && it.bpm >= trainer.goalBpm) goalBar = it.trainerBar
            val goal = goalBar
            goal == null || it.trainerBar < goal + 2
        }
        val downbeats = climb.beats.filter { it.beatInBar == 0 }.map { it.trainerBar to it.bpm }
        val gaps = climb.gaps.drop(WARMUP)
        summary.appendLine(
            "Speed trainer 200 → 250, +10 every bar, sixteenths: downbeats (bar, BPM) $downbeats; " +
                "${climb.beats.size} beats, missed ${climb.missed}, underruns ${climb.underruns}, gaps off their tempo %s".format(range(gaps)),
        )
        val expected = (0L..6L).map { it to minOf(trainer.goalBpm, trainer.startBpm + trainer.stepBpm * it.toInt()) }
        if (downbeats.take(expected.size) != expected || climb.beats.any { it.bpm > trainer.goalBpm } ||
            climb.missed > 0 || climb.underruns > 0 || gaps.any { abs(it) >= 2.0 }
        ) {
            problems += "the trainer's climb"
        }
    }

    /** Runs [body] with the real player at a low media volume (put back after), then writes and checks its summary. */
    private fun measure(body: (AndroidMetronomePlayer, StringBuilder, MutableList<String>) -> Unit) {
        assumeTrue("on demand", arguments.getString(ARGUMENT) == "true")
        val player = AndroidMetronomePlayer(context)
        val audioManager = context.getSystemService(AudioManager::class.java)
        val volumeBefore = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val summary = StringBuilder()
        val problems = mutableListOf<String>()
        summary.appendLine(
            "Phone: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}; output ${player.sampleRate} Hz",
        )
        try {
            // Quiet but not silent: at 0 the player would show the volume panel.
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, QUIET_VOLUME, 0)
            body(player, summary, problems)
        } finally {
            player.stop()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeBefore, 0)
        }
        summary.appendLine("Media volume put back to $volumeBefore")
        Log.i(TAG, summary.toString())
        arguments.getString("additionalTestOutputDir")?.let(::File)?.let { dir ->
            dir.mkdirs()
            File(dir, "metronome-subdivisions.txt").appendText(summary.toString())
        }
        assertEquals("problems:\n${problems.joinToString("\n")}", 0, problems.size)
    }

    /**
     * Plays from beat 0 until [keep] turns false, then stops. The gaps are each heard gap minus the
     * tempo the beat before it played at, in ms.
     */
    private fun play(
        player: AndroidMetronomePlayer,
        bpm: Int,
        meter: Meter,
        subdivision: Subdivision,
        trainer: SpeedTrainer?,
        keep: (MetronomeBeat) -> Boolean,
    ): Run {
        val beats = mutableListOf<MetronomeBeat>()
        val underrunsBefore = underrunsLogged()
        runBlocking {
            val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                player.beats.takeWhile(keep).collect { beats += it }
            }
            player.start(bpm, meter, ClickSound.DEFAULT, subdivision, trainer)
            withTimeout(RUN_LIMIT_MILLIS) { collector.join() }
            player.stop()
            withTimeout(SLACK_MILLIS) { player.isPlaying.first { !it } }
        }
        Thread.sleep(LOG_SETTLE_MILLIS) // the session logs its underruns as it closes the track
        val gaps = beats.zipWithNext().filter { (a, b) -> b.index == a.index + 1 && a.bpm > 0 }
            .map { (a, b) -> (b.heardAtNanos - a.heardAtNanos - 60e9 / a.bpm) / 1e6 }
        return Run(beats.toList(), underrunsLogged() - underrunsBefore, gaps)
    }

    /** The underruns the player logged in this process so far ("The speaker ran dry N times"). */
    private fun underrunsLogged(): Int {
        val pfd = instrumentation.uiAutomation.executeShellCommand("logcat -d --pid=${Process.myPid()} -s MetronomePlayer:W")
        val text = ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        return Regex("ran dry (\\d+) times").findAll(text).sumOf { it.groupValues[1].toInt() }
    }

    private fun range(values: List<Double>) =
        if (values.isEmpty()) "-" else "%+.3f .. %+.3f ms".format(values.min(), values.max())

    private fun rms(values: List<Double>) = if (values.isEmpty()) 0.0 else sqrt(values.sumOf { it * it } / values.size)

    private companion object {
        const val TAG = "MetronomeSubdivisions"
        const val ARGUMENT = "metronomeSubdivisions"
        const val SECONDS_ARGUMENT = "metronomeSubdivisionsSeconds"
        const val SLOW = 60
        const val FAST = 250
        const val QUIET_VOLUME = 1
        const val WARMUP = 4
        const val RUN_LIMIT_MILLIS = 15 * 60_000L
        const val SLACK_MILLIS = 5_000L
        const val LOG_SETTLE_MILLIS = 300L
    }
}
