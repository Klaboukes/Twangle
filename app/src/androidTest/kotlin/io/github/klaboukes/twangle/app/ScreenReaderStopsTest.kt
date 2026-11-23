package io.github.klaboukes.twangle.app

import android.Manifest
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.klaboukes.twangle.app.navigation.Destination
import io.github.klaboukes.twangle.app.navigation.NavigationTestTags
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeDronePlayer
import io.github.klaboukes.twangle.core.audio.FakeMetronomePlayer
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.practice.PracticeTestTags
import io.github.klaboukes.twangle.feature.settings.SETTINGS_BACK_TAG
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout

/**
 * Every stop TalkBack can land on has a name (Phase 94). A blind guitarist's review found an "Unlabeled" stop a
 * swipe back from the Settings gear: the tuner's word line sat in a tagged box of its own size with nothing to say,
 * and TalkBack landed on that box, not on the words inside it. This walks the accessibility tree that TalkBack reads
 * (the real app, through the same service connection) on every tab, in every Practice tool and in Settings, and
 * fails on either kind of nameless stop:
 * - a stop with nothing to say: a node TalkBack may focus (screen-reader focusable, clickable, long-clickable or
 *   focusable) with no text or description of its own or in the parts inside it that aren't stops themselves;
 * - a nameless twin: a node with nothing to say round exactly one stop of its own size, which a screen reader may
 *   take for a stop of its own (the word line's box, a tooltip's anchor round its button).
 */
class ScreenReaderStopsTest {

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build())
        .around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO))
        // Focus mode's full-screen tuner hides the bar the walk goes through.
        .around(FocusModeOffRule())
        .around(HighContrastPassRule())
        .around(QuietMicrophoneRule())

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val container = ApplicationProvider.getApplicationContext<TwangleApplication>().container

    /** Silent stand-ins: Ear training asks its first question aloud at once, and the drone and clicks stay quiet too. */
    @Before
    fun setUp() {
        container.testTonePlayer = FakeTonePlayer()
        container.testDronePlayer = FakeDronePlayer()
        container.testMetronomePlayer = FakeMetronomePlayer()
        container.testEarTrainingRecorder = FakeAudioRecorder.silence()
    }

    @After
    fun tearDown() {
        container.testTonePlayer = null
        container.testDronePlayer = null
        container.testMetronomePlayer = null
        container.testEarTrainingRecorder = null
    }

    private fun waitFor(tag: String) = checkNotNull(device.wait(Until.findObject(By.res(tag)), WAIT_MS)) { "$tag not shown" }

    /**
     * Taps [tag] through the shell: UI Automator's own tap waits for the app to go idle first, and the tuner's and the
     * pitch trail's lens draw on every frame while they hear the stand-in microphone's hiss (10 s a tap).
     */
    private fun tap(tag: String) {
        val at = waitFor(tag).visibleCenter
        device.executeShellCommand("input tap ${at.x} ${at.y}")
    }

    @Test
    fun everyStopHasAName() {
        val found = mutableListOf<String>()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(TunerTestTags.PICKER_CHIP)
            found += nameless("the tuner")

            tap(NavigationTestTags.tab(Destination.METRONOME))
            waitFor(MetronomeTestTags.RULER)
            found += nameless("the metronome")

            tap(NavigationTestTags.tab(Destination.PRACTICE))
            waitFor(PracticeTestTags.CARDS)
            found += nameless("Practice's cards")
            for (tool in PracticeTool.entries) {
                // A tool whose switch is off in this build has no card.
                if (!device.wait(Until.hasObject(By.res(PracticeTestTags.card(tool))), SHORT_WAIT_MS)) continue
                tap(PracticeTestTags.card(tool))
                waitFor(PracticeTestTags.BACK)
                found += nameless(tool.name.lowercase())
                tap(PracticeTestTags.BACK)
                waitFor(PracticeTestTags.CARDS)
            }

            tap(NavigationTestTags.SETTINGS)
            waitFor(SETTINGS_BACK_TAG)
            // The bar fades out under Settings: its tabs, their names faded already, are no stops by then.
            device.wait(Until.gone(By.res(NavigationTestTags.BAR)), WAIT_MS)
            found += nameless("Settings")
        }
        if (found.isNotEmpty()) fail("TalkBack stops with no name:\n" + found.joinToString("\n"))
    }

    /** The nameless stops on the screen shown now ([screen] names it in the message), once it has settled. */
    private fun nameless(screen: String): List<String> {
        // Not waitForIdle: the tuner's and the pitch trail's lens draw on every frame while they hear the hiss.
        SystemClock.sleep(SETTLE_MS)
        val root = checkNotNull(instrumentation.uiAutomation.rootInActiveWindow) { "no window on $screen" }
        val found = mutableListOf<String>()
        check(root, screen, found)
        return found
    }

    private fun check(node: AccessibilityNodeInfo, screen: String, found: MutableList<String>) {
        if (!node.isVisibleToUser) return
        if (!hasName(node)) {
            val stops = stopsInside(node)
            when {
                isStop(node) && stops.isEmpty() -> found += "$screen: ${describe(node)} has nothing to say"
                stops.size == 1 && sameBounds(node, stops[0]) -> found += "$screen: ${describe(node)} is a nameless twin round ${describe(stops[0])}"
            }
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let { check(it, screen, found) }
    }

    /** Whether TalkBack may land on [node]. */
    private fun isStop(node: AccessibilityNodeInfo): Boolean =
        node.isVisibleToUser && (node.isScreenReaderFocusable || node.isClickable || node.isLongClickable || node.isFocusable)

    /** Whether [node] has something to say: a text or description of its own or in a part inside it that isn't a stop. */
    private fun hasName(node: AccessibilityNodeInfo): Boolean {
        if (!node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isVisibleToUser && !isStop(child) && hasName(child)) return true
        }
        return false
    }

    /** The stops inside [node], not counting those inside another stop. */
    private fun stopsInside(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val stops = mutableListOf<AccessibilityNodeInfo>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (!child.isVisibleToUser) continue
            if (isStop(child)) stops += child else stops += stopsInside(child)
        }
        return stops
    }

    private fun bounds(node: AccessibilityNodeInfo) = Rect().also { node.getBoundsInScreen(it) }

    /** Whether [a] and [b] cover the same place, give or take a pixel's rounding. */
    private fun sameBounds(a: AccessibilityNodeInfo, b: AccessibilityNodeInfo): Boolean {
        val one = bounds(a)
        val other = bounds(b)
        return abs(one.left - other.left) <= SAME_PX && abs(one.top - other.top) <= SAME_PX &&
            abs(one.right - other.right) <= SAME_PX && abs(one.bottom - other.bottom) <= SAME_PX
    }

    private fun describe(node: AccessibilityNodeInfo): String {
        val id = node.viewIdResourceName?.substringAfterLast('/')
        val name = node.contentDescription ?: node.text
        return "${node.className?.toString()?.substringAfterLast('.')}" +
            (id?.let { " #$it" } ?: "") + (name?.let { " \"$it\"" } ?: "") + " at ${bounds(node).toShortString()}"
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 60L
        const val WAIT_MS = 5_000L
        const val SHORT_WAIT_MS = 1_000L
        const val SAME_PX = 2

        /** Long enough for a screen just opened to have laid itself out and named its parts. */
        const val SETTLE_MS = 700L
    }
}
