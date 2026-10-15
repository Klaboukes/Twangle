package io.github.klaboukes.twangle.baselineprofile

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.regex.Pattern

/**
 * Frame times and main-thread work of every meter look in chromatic mode, with the meter moving
 * all the time. The `benchmarkRelease` app hears a synthetic tone that sweeps across about ±30
 * cents (`SyntheticAudioRecorder` in `:app`), so every look and every run gets the same input.
 *
 * For each look it picks the look in settings (below the note: numbers, so only the meter
 * moves), switches to chromatic and takes [RUNS] windows of [WINDOW_SECONDS]. Per window:
 * frames, janky frames and frame-time percentiles from `dumpsys gfxinfo`, and CPU time of the main
 * thread, the RenderThread and the whole app from `/proc/<pid>/…/stat`, as in ARCHITECTURE §4.
 * No tracing runs meanwhile, so the numbers compare with the Phase 5 table.
 *
 * Run with the phone connected, unlocked and upright: `scripts/meter-frames.sh`. Results go to
 * logcat (tag `MeterFrames`) and `meter-frames.tsv` in the test's additional output.
 * Instrumentation arguments: `meterLooks` (`MeterLook` names separated by colons, as Gradle cuts
 * arguments at commas; `CURRENT` keeps the look set in the app, e.g. an older build without
 * looks), `runs`, `seconds`.
 */
@RunWith(AndroidJUnit4::class)
class MeterFramesBenchmark {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val args = InstrumentationRegistry.getArguments()
    private val runs = args.getString("runs")?.toInt() ?: RUNS
    private val windowSeconds = args.getString("seconds")?.toInt() ?: WINDOW_SECONDS
    private val looks = (args.getString("meterLooks") ?: ALL_LOOKS).split(':').map { it.trim().uppercase(Locale.ROOT) }

    @Test
    fun meterLooksInChromaticMode() {
        // The meter never stops moving, so the UI never goes idle: don't wait for it.
        Configurator.getInstance().waitForIdleTimeout = IDLE_TIMEOUT_MS
        check(device.isNaturalOrientation) { "Hold the phone upright (portrait) for the measurement" }
        shell("input keyevent KEYCODE_WAKEUP")
        shell("pm grant $TARGET_PACKAGE android.permission.RECORD_AUDIO")
        shell("am start -W -n $TARGET_PACKAGE/$ACTIVITY")
        // A fresh install shows the first-start tour over the tuner first.
        waitFor(By.res(Pattern.compile("$MODE_CHROMATIC_TAG|$TOUR_SKIP_TAG")), "Tuner screen")
        device.findObject(By.res(TOUR_SKIP_TAG))?.let(::click)
        waitFor(By.res(MODE_CHROMATIC_TAG), "Tuner screen")

        // Only the meter moves: the cents display instead of the wave view.
        openSettings()
        click(findInSettings(By.text(NUMBERS_DISPLAY)))
        device.pressBack()
        waitFor(By.res(MODE_CHROMATIC_TAG), "Tuner screen")

        val pid = shell("pidof $TARGET_PACKAGE").trim().split(' ').first()
        check(pid.isNotEmpty()) { "Twangle isn't running" }
        val renderTid = renderThreadOf(pid)
        val rows = mutableListOf<Window>()
        log("refresh_rate_mode=${shell("settings get secure refresh_rate_mode").trim()} pid=$pid renderThread=$renderTid")
        for (look in looks) {
            if (look != CURRENT) {
                openSettings()
                val card = findInSettings(By.res(CARD_TAG_PREFIX + look.lowercase(Locale.ROOT)))
                click(card)
                device.pressBack()
            }
            click(waitFor(By.res(MODE_CHROMATIC_TAG), "Chromatic button"))
            SystemClock.sleep(SETTLE_MS)
            repeat(runs) { run -> rows += measure(look, run + 1, pid, renderTid).also { log(it.row()) } }
        }
        report(rows)
    }

    private fun measure(look: String, run: Int, pid: String, renderTid: String): Window {
        shell("dumpsys gfxinfo $TARGET_PACKAGE reset")
        val before = cpuTicks(pid, renderTid)
        val start = SystemClock.elapsedRealtime()
        SystemClock.sleep(windowSeconds * 1000L)
        val after = cpuTicks(pid, renderTid)
        val seconds = (SystemClock.elapsedRealtime() - start) / 1000.0
        val gfx = shell("dumpsys gfxinfo $TARGET_PACKAGE")
        // Where janky frames came from (slow UI thread, slow draw commands, GPU…), for profiling.
        gfx.lines().filter { JANK_CAUSE.matcher(it).find() }.take(JANK_CAUSE_LINES)
            .forEach { log("  $look $run ${it.trim()}") }
        return Window(
            look = look,
            run = run,
            seconds = seconds,
            frames = gfx.number("Total frames rendered: (\\d+)"),
            janky = gfx.number("Janky frames: (\\d+)"),
            deadlineMissed = gfx.number("Number Frame deadline missed: (\\d+)"),
            p50 = gfx.number("\\n50th percentile: (\\d+)ms"),
            p90 = gfx.number("\\n90th percentile: (\\d+)ms"),
            p95 = gfx.number("\\n95th percentile: (\\d+)ms"),
            p99 = gfx.number("\\n99th percentile: (\\d+)ms"),
            appMs = (after[0] - before[0]) * MS_PER_TICK,
            mainMs = (after[1] - before[1]) * MS_PER_TICK,
            renderMs = (after[2] - before[2]) * MS_PER_TICK,
        )
    }

    /** utime + stime in clock ticks of the whole app, its main thread and its RenderThread, read in one go. */
    private fun cpuTicks(pid: String, renderTid: String): LongArray {
        val lines = shell("cat /proc/$pid/stat /proc/$pid/task/$pid/stat /proc/$pid/task/$renderTid/stat")
            .lines().filter { it.isNotBlank() }
        check(lines.size == 3) { "Couldn't read the app's CPU times: $lines" }
        return LongArray(3) { i ->
            // Fields after "(comm)": state is field 3, utime field 14 and stime field 15 (proc(5)).
            val fields = lines[i].substringAfterLast(')').trim().split(' ')
            fields[11].toLong() + fields[12].toLong()
        }
    }

    private fun renderThreadOf(pid: String): String {
        val tids = shell("ls /proc/$pid/task").split(Regex("\\s+")).filter { it.isNotEmpty() }
        val names = shell("cat " + tids.joinToString(" ") { "/proc/$pid/task/$it/comm" }).lines()
        return tids.zip(names).firstOrNull { it.second.trim() == "RenderThread" }?.first
            ?: error("No RenderThread in $tids")
    }

    private fun report(rows: List<Window>) {
        val out = StringBuilder(Window.HEADER).append('\n')
        rows.forEach { out.append(it.row()).append('\n') }
        out.append('\n').append(SUMMARY_HEADER).append('\n')
        rows.groupBy { it.look }.forEach { (look, windows) ->
            val seconds = windows.sumOf { it.seconds }
            val frames = windows.sumOf { it.frames }
            val main = windows.sumOf { it.mainMs }
            out.append(
                String.format(
                    Locale.ROOT,
                    "%s\t%.0f\t%d\t%d\t%d/%d/%d/%d\t%.1f\t%.2f\t%.1f\t%.1f\n",
                    look, frames / seconds, windows.sumOf { it.janky }, windows.sumOf { it.deadlineMissed },
                    windows.maxOf { it.p50 }, windows.maxOf { it.p90 }, windows.maxOf { it.p95 }, windows.maxOf { it.p99 },
                    main / seconds / 10.0, if (frames > 0) main / frames else 0.0,
                    windows.sumOf { it.renderMs } / seconds / 10.0, windows.sumOf { it.appMs } / seconds / 10.0,
                ),
            )
        }
        out.lines().forEach { log(it) }
        val dir = args.getString("additionalTestOutputDir")?.let(::File)
            ?: instrumentation.targetContext.getExternalFilesDir(null)
        dir?.let { File(it.apply { mkdirs() }, "meter-frames.tsv").writeText(out.toString()) }
    }

    private fun openSettings() {
        click(waitFor(By.res(SETTINGS_TAB_TAG), "Settings tab"))
        check(device.wait(Until.gone(By.res(MODE_CHROMATIC_TAG)), TIMEOUT_MS)) { "Settings didn't open" }
    }

    /** Finds [selector] in the settings, scrolling up and then down to it (settings keep their scroll place). */
    private fun findInSettings(selector: BySelector): UiObject2 {
        device.wait(Until.findObject(selector), SHORT_WAIT_MS)?.let { return it }
        val list = waitFor(By.scrollable(true), "Settings list")
        list.scrollUntil(Direction.UP, Until.findObject(selector))?.let { return it }
        return list.scrollUntil(Direction.DOWN, Until.findObject(selector)) ?: error("No $selector in settings")
    }

    private fun waitFor(selector: BySelector, what: String): UiObject2 =
        device.wait(Until.findObject(selector), TIMEOUT_MS) ?: error("$what didn't appear")

    private fun click(node: UiObject2) {
        node.click()
        SystemClock.sleep(TAP_SETTLE_MS)
    }

    private fun shell(command: String): String = device.executeShellCommand(command)

    private fun String.number(regex: String): Int =
        Pattern.compile(regex).matcher(this).let { if (it.find()) it.group(1)!!.toInt() else -1 }

    private fun log(line: String) {
        Log.i(TAG, line)
    }

    private data class Window(
        val look: String,
        val run: Int,
        val seconds: Double,
        val frames: Int,
        val janky: Int,
        val deadlineMissed: Int,
        val p50: Int,
        val p90: Int,
        val p95: Int,
        val p99: Int,
        val appMs: Double,
        val mainMs: Double,
        val renderMs: Double,
    ) {
        fun row(): String = String.format(
            Locale.ROOT,
            "%s\t%d\t%.1f\t%d\t%.0f\t%d\t%d\t%d/%d/%d/%d\t%.1f\t%.2f\t%.1f\t%.1f",
            look, run, seconds, frames, frames / seconds, janky, deadlineMissed, p50, p90, p95, p99,
            mainMs / seconds / 10.0, if (frames > 0) mainMs / frames else 0.0, renderMs / seconds / 10.0, appMs / seconds / 10.0,
        )

        companion object {
            const val HEADER = "look\trun\ts\tframes\tfps\tjanky\tdeadlineMissed\tp50/p90/p95/p99 ms\tmain%\tmainMs/frame\trender%\tapp%"
        }
    }

    private companion object {
        const val TAG = "MeterFrames"
        const val ACTIVITY = "io.github.klaboukes.twangle.app.MainActivity"
        const val SETTINGS_TAB_TAG = "nav_settings"
        const val MODE_CHROMATIC_TAG = "tuner_mode_chromatic"
        const val CARD_TAG_PREFIX = "meter_look_card_"
        const val CURRENT = "CURRENT"
        const val ALL_LOOKS = "CLASSIC:LIGHT_ARC:SPIRIT_LEVEL:ECLIPSE:ARROWS:LIGHT_BARS"
        const val SUMMARY_HEADER = "look\tfps\tjanky\tdeadlineMissed\tworst p50/p90/p95/p99 ms\tmain%\tmainMs/frame\trender%\tapp%"

        /** The gfxinfo lines that say why frames were late ("Number Slow UI thread: 2", GPU percentiles). */
        val JANK_CAUSE: Pattern = Pattern.compile("^\\s*(Number [A-Z].*|.*gpu percentile.*): ")
        const val JANK_CAUSE_LINES = 16

        /** The display choice "Numbers: how many cents off", in English or Dutch (the meter on its own). */
        val NUMBERS_DISPLAY: Pattern = Pattern.compile("(Numbers|Getallen): .*")

        const val RUNS = 3
        const val WINDOW_SECONDS = 7
        const val SETTLE_MS = 3_000L
        const val TAP_SETTLE_MS = 700L
        const val TIMEOUT_MS = 10_000L
        const val SHORT_WAIT_MS = 1_000L
        const val IDLE_TIMEOUT_MS = 100L

        /** /proc reports CPU time in clock ticks of 10 ms (USER_HZ = 100 on Android). */
        const val MS_PER_TICK = 10.0
    }
}
