package io.github.klaboukes.twangle.core.testing

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.audio.AndroidAudioRecorder
import io.github.klaboukes.twangle.core.audio.AudioInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Records plucked strings on the test phone as 16-bit mono WAV files, for the real-recording tests
 * in `:core:dsp` (ROADMAP Phase 46, *Real recordings in the tests*). On demand only, through
 * `scripts/record-plucks.sh`: without the instrumentation argument `pluckPlan` it is skipped, so UI
 * test runs pay nothing.
 *
 * This is a test helper, never Twangle: the app itself writes no audio anywhere (hard rule 7). It
 * records through Twangle's own [AndroidAudioRecorder], so the files hold what the tuner hears: the
 * same input (unprocessed, else voice recognition; hard rule 5) at the same rate.
 *
 * `pluckPlan` lists groups as `name|label|takes|note`, separated by `;`. For each group the phone
 * shows the label (and the note, if any) and waits for a tap on its screen (`pluckAutoStart=true`
 * skips the wait), then records each take for `pluckSeconds` (default 4) seconds, a fixed length:
 * "Pluck now" shows once the microphone is running. Each take is written as `<name>-<take>.wav` to
 * the additional test output, which `scripts/ui-tests.sh` pulls to the PC. Every prompt also goes out
 * as an instrumentation status (`pluck=`), which the script prints in the terminal as it comes.
 */
@RunWith(AndroidJUnit4::class)
class PluckRecorder {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    private val context = instrumentation.targetContext
    private val screen = RecorderScreenState()

    // The permission comes with the install (`adb install -g`), checked below.
    @SuppressLint("MissingPermission")
    @Test
    fun recordPlucks() {
        val plan = arguments.getString(PLAN_ARGUMENT)
        assumeTrue("on demand: scripts/record-plucks.sh", plan != null)
        val groups = parsePlan(checkNotNull(plan))
        val seconds = arguments.getString(SECONDS_ARGUMENT)?.toFloatOrNull() ?: DEFAULT_SECONDS
        val autoStart = arguments.getString(AUTO_START_ARGUMENT) == "true"
        val outDir = File(arguments.getString("additionalTestOutputDir") ?: context.getExternalFilesDir(null)!!.path)
        outDir.mkdirs()
        assertEquals(
            "The microphone permission is missing: install the test with adb install -g (scripts/ui-tests.sh does)",
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO),
        )

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.setContent { RecorderScreen(screen) }
            }
            val recorder = AndroidAudioRecorder(context)
            for ((index, group) in groups.withIndex()) {
                val step = "Step ${index + 1} of ${groups.size}"
                show(Tone.WAIT, group.label, "Tap the screen when you are ready", listOfNotNull(group.note, step).joinToString("\n"))
                tell("")
                tell("$step: ${group.label}, ${group.takes} ${if (group.takes == 1) "pluck" else "plucks"}")
                group.note?.let { tell("  $it") }
                if (!autoStart) {
                    tell("  Tap the phone's screen when you are ready.")
                    waitForTap()
                }
                for (take in 1..group.takes) {
                    val takeLine = "Pluck ${take} of ${group.takes}"
                    show(Tone.WAIT, group.label, "Quiet the strings", takeLine)
                    Thread.sleep(QUIET_MILLIS)
                    val recording = runBlocking {
                        record(recorder, seconds) {
                            show(Tone.GO, group.label, "Pluck now!", "$takeLine. Let it ring.")
                            tell("  $takeLine: PLUCK NOW, then let it ring")
                        }
                    }
                    val file = File(outDir, "${group.name}-$take.wav")
                    writeWav(file, recording.samples, recording.sampleRate, comment(recording, group))
                    tell("  $takeLine: saved ${file.name} (${recording.sampleRate} Hz, ${recording.input}), ${level(recording.samples)}")
                }
            }
            show(Tone.DONE, "All done", "Thank you!", "The files go to the PC now.")
            tell("")
            tell("All recorded.")
            Thread.sleep(DONE_MILLIS)
        }
    }

    /**
     * Records [seconds] of audio through [recorder], calling [onStart] once the microphone runs (its
     * first chunk), so "Pluck now" never shows before it listens.
     */
    @SuppressLint("MissingPermission")
    private suspend fun record(recorder: AndroidAudioRecorder, seconds: Float, onStart: () -> Unit): Recording {
        var samples: FloatArray? = null
        var input: AudioInput? = null
        var filled = 0
        recorder.chunks().first { chunk ->
            val out = samples ?: FloatArray((recorder.config.sampleRate * seconds).roundToInt()).also {
                samples = it
                input = recorder.activeInput
                onStart()
            }
            val count = minOf(chunk.size, out.size - filled)
            chunk.copyInto(out, filled, 0, count)
            filled += count
            filled == out.size
        }
        return Recording(checkNotNull(samples), recorder.config.sampleRate, input)
    }

    private fun waitForTap() {
        val tapped = CountDownLatch(1)
        screen.onTap = { tapped.countDown() }
        try {
            check(tapped.await(TAP_WAIT_MINUTES, TimeUnit.MINUTES)) { "No tap on the phone for $TAP_WAIT_MINUTES minutes: stopped" }
        } finally {
            screen.onTap = null
        }
    }

    private fun show(tone: Tone, title: String, prompt: String, detail: String) = instrumentation.runOnMainSync {
        screen.tone = tone
        screen.title = title
        screen.prompt = prompt
        screen.detail = detail
    }

    /** A line for the terminal: an instrumentation status the script prints as it comes. */
    private fun tell(line: String) {
        Log.i(TAG, line)
        instrumentation.sendStatus(PROMPT_STATUS_CODE, Bundle().apply { putString(PROMPT_KEY, line) })
    }

    /** Where the file came from, in the file itself (ASCII, as INFO text should be). */
    private fun comment(recording: Recording, group: Group): String {
        val text = "Twangle pluck recorder (scripts/record-plucks.sh): ${group.label}; " +
            "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}; " +
            "input ${recording.input}; ${recording.sampleRate} Hz"
        return text.map { if (it.code in 32..126) it else '?' }.joinToString("")
    }

    private class Recording(val samples: FloatArray, val sampleRate: Int, val input: AudioInput?)

    private class Group(val name: String, val label: String, val takes: Int, val note: String?)

    private fun parsePlan(plan: String): List<Group> = plan.split(';').filter { it.isNotBlank() }.map { entry ->
        val fields = entry.split('|')
        require(fields.size >= 3 && fields[0].matches(Regex("[A-Za-z0-9-]+"))) { "Not a plan entry: $entry" }
        Group(fields[0], fields[1], fields[2].toInt(), fields.getOrNull(3)?.takeIf { it.isNotBlank() })
    }

    companion object {
        private const val TAG = "PluckRecorder"
        private const val PLAN_ARGUMENT = "pluckPlan"
        private const val SECONDS_ARGUMENT = "pluckSeconds"
        private const val AUTO_START_ARGUMENT = "pluckAutoStart"
        private const val DEFAULT_SECONDS = 4f
        private const val PROMPT_KEY = "pluck"

        // Not one of AndroidJUnitRunner's own codes (1 a test starts, 0 and below its result).
        private const val PROMPT_STATUS_CODE = 2
        private const val QUIET_MILLIS = 1500L
        private const val DONE_MILLIS = 1500L
        private const val TAP_WAIT_MINUTES = 15L

        /** The peak in dB below full scale, with a word when it is far too quiet or clipped. */
        fun level(samples: FloatArray): String {
            val peak = samples.maxOfOrNull { abs(it) } ?: 0f
            val db = if (peak > 0f) 20 * log10(peak) else -120f
            val text = "peak ${"%.0f".format(db)} dB"
            return when {
                peak >= CLIPPED -> "$text: CLIPPED, play softer or move the phone away"
                peak < QUIET -> "$text: very quiet, was the string plucked? Move the phone closer"
                else -> text
            }
        }

        private const val CLIPPED = 0.99f
        private const val QUIET = 0.01f
    }
}

private enum class Tone(val background: Color) {
    WAIT(Color(0xFF263238)),
    GO(Color(0xFF1B5E20)),
    DONE(Color(0xFF0D47A1)),
}

private class RecorderScreenState {
    var tone by mutableStateOf(Tone.WAIT)
    var title by mutableStateOf("")
    var prompt by mutableStateOf("")
    var detail by mutableStateOf("")

    @Volatile
    var onTap: (() -> Unit)? = null
}

/** What the player reads while recording: big enough to read from where they sit with the guitar. */
@Composable
private fun RecorderScreen(state: RecorderScreenState) {
    Column(
        Modifier
            .fillMaxSize()
            .background(state.tone.background)
            .clickable { state.onTap?.invoke() }
            .padding(horizontal = 24.dp, vertical = 64.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(state.title, color = Color.White, fontSize = 26.sp, textAlign = TextAlign.Center, lineHeight = 32.sp)
        Text(
            state.prompt,
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 56.sp,
        )
        Text(state.detail, color = Color(0xFFCFD8DC), fontSize = 20.sp, textAlign = TextAlign.Center, lineHeight = 26.sp)
    }
}
