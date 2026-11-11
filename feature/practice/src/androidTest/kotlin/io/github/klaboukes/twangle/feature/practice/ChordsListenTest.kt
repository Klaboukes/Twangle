package io.github.klaboukes.twangle.feature.practice

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import io.github.klaboukes.twangle.core.testing.onPhoneScreen
import java.io.File
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.designsystem.NAME_PICKER_HEIGHT
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.synth.StringSynth
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Listen in Chords (2.0, Phase 74): the round button at the end of the name's row on Chords (not on Scales, nor without
 * a listener), the name's height, wholly shown upright and on its side; a tap listens (TalkBack: "Listening"), and a
 * synthetic A minor strum through a fake microphone becomes the chord picked, and saved; a tap again stops it. A busy
 * microphone turns Listen off and says so (Phase 89), and a tap tries again; a missing permission says the microphone
 * is off instead (Phase 91).
 */
class ChordsListenTest {

    private val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val shownSize = mutableStateOf(XIAOMI)
    private val shownScales = mutableStateOf(false)
    private val savedRoots = mutableListOf<PitchClass>()
    private val savedQualities = mutableListOf<ChordQuality>()

    private val shownListener = mutableStateOf<ChordListener?>(ChordListener(FakeAudioRecorder(aMinor(), loop = true)))

    private fun show() {
        compose.setContent {
            val listener = shownListener.value
            PhoneScreen(shownSize.value, SystemBars.tab(onItsSide = shownSize.value.width > shownSize.value.height)) {
                TwangleTheme(animate = false) {
                    ChordsScreen(
                        tuning = Tunings.STANDARD,
                        tuningName = "Guitar · Standard",
                        spokenTuningName = "Guitar, Standard",
                        notation = io.github.klaboukes.twangle.core.dsp.music.Notation.SHARPS,
                        savedRoot = PitchClass.C,
                        savedQuality = ChordQuality.MAJOR,
                        onRootChange = { savedRoots += it },
                        onQualityChange = { savedQualities += it },
                        onBack = {},
                        modifier = Modifier.fillMaxSize(),
                        scales = shownScales.value,
                        chordListener = listener,
                    )
                }
            }
        }
    }

    private val listen get() = compose.onNodeWithTag(PracticeTestTags.CHORDS_LISTEN)
    private fun count() = compose.onAllNodesWithTag(PracticeTestTags.CHORDS_LISTEN).fetchSemanticsNodes().size

    @Test
    fun listenStandsAtTheNamesEndOnChordsOnly() {
        show()
        for (size in listOf(XIAOMI, XIAOMI_ON_ITS_SIDE)) {
            shownSize.value = size
            compose.waitForIdle()
            val button = listen.assertIsDisplayed().getBoundsInRoot()
            assertWhollyShown(listen, "$size: Listen")
            assertTrue("$size: Listen $button is the name's height", abs((button.bottom - button.top).value - NAME_PICKER_HEIGHT.value) < 1f)
        }
        shownScales.value = true
        compose.waitForIdle()
        assertEquals("on Scales", 0, count())
        shownListener.value = null
        shownScales.value = false
        compose.waitForIdle()
        assertEquals("without a listener", 0, count())
    }

    @Test
    fun aStrummedChordBecomesTheChordPicked() {
        show()
        compose.mainClock.autoAdvance = true
        listen.performClick()
        compose.waitUntil(STEP_WAIT_MS) { state() == context.getString(R.string.practice_chords_listening) }
        compose.waitUntil(STEP_WAIT_MS) { savedRoots.lastOrNull() == PitchClass.A && savedQualities.lastOrNull() == ChordQuality.MINOR }
        picture("chords-listen-a-minor")
        listen.performClick()
        compose.waitUntil(STEP_WAIT_MS) { state().isNullOrEmpty() }
    }

    @Test
    fun aBusyMicrophoneTurnsListenOffAndSaysSo() {
        // A call has the microphone: the recorder fails as it opens (Phase 89; until then this closed the app).
        val recorder = FakeAudioRecorder(failure = AudioRecorderException.MicrophoneBusy())
        shownListener.value = ChordListener(recorder)
        show()
        compose.mainClock.autoAdvance = true
        val busy = context.getString(R.string.practice_chords_mic_busy)
        listen.performClick()
        compose.waitUntil(STEP_WAIT_MS) { state() == busy }
        compose.onNodeWithText(busy).assertIsDisplayed()
        picture("chords-listen-busy")
        // The call is over: a tap on Listen tries again, and listens.
        recorder.failure = null
        recorder.signal = aMinor()
        recorder.loop = true
        listen.performClick()
        compose.waitUntil(STEP_WAIT_MS) { state() == context.getString(R.string.practice_chords_listening) }
        compose.waitUntil(STEP_WAIT_MS) { compose.onAllNodesWithText(busy).fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(STEP_WAIT_MS) { savedRoots.lastOrNull() == PitchClass.A && savedQualities.lastOrNull() == ChordQuality.MINOR }
        listen.performClick()
        compose.waitUntil(STEP_WAIT_MS) { state().isNullOrEmpty() }
    }

    @Test
    fun aMissingPermissionTurnsListenOffAndSaysTheMicrophoneIsOff() {
        // The permission taken back in the phone's settings while Listen was on (Phase 91; until then "Microphone is
        // busy", which a tap couldn't mend): the recorder refuses for want of it.
        val recorder = FakeAudioRecorder(failure = AudioRecorderException.PermissionMissing())
        shownListener.value = ChordListener(recorder)
        show()
        compose.mainClock.autoAdvance = true
        val off = context.getString(R.string.practice_chords_mic_off)
        listen.performClick()
        compose.waitUntil(STEP_WAIT_MS) { state() == off }
        compose.onNodeWithText(off).assertIsDisplayed()
        assertEquals("not busy", 0, compose.onAllNodesWithText(context.getString(R.string.practice_chords_mic_busy)).fetchSemanticsNodes().size)
        // Allowed again (the test's grant): a tap asks, finds it allowed, and listens.
        recorder.failure = null
        recorder.signal = aMinor()
        recorder.loop = true
        listen.performClick()
        compose.waitUntil(STEP_WAIT_MS) { state() == context.getString(R.string.practice_chords_listening) }
        compose.waitUntil(STEP_WAIT_MS) { compose.onAllNodesWithText(off).fetchSemanticsNodes().isEmpty() }
        listen.performClick()
        compose.waitUntil(STEP_WAIT_MS) { state().isNullOrEmpty() }
    }

    /** With `-a listenScreens=true`, the phone screen as a picture for the owner. */
    private fun picture(name: String) {
        val arguments = InstrumentationRegistry.getArguments()
        if (arguments.getString("listenScreens") != "true") return
        val dir = arguments.getString("additionalTestOutputDir") ?: return
        compose.waitForIdle()
        val bitmap = compose.onPhoneScreen().captureToImage().asAndroidBitmap()
        File(File(dir).apply { mkdirs() }, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun state(): String? = compose.onAllNodesWithTag(PracticeTestTags.CHORDS_LISTEN).fetchSemanticsNodes()
        .firstOrNull()?.config?.getOrNull(SemanticsProperties.StateDescription)

    /** A minor strummed on a guitar's voice (x02210: A2 E3 A3 C4 E4), 0.3 s of quiet before it, 3 s long. */
    private fun aMinor(): FloatArray {
        val rate = 48_000
        val start = (0.3 * rate).roundToInt()
        val length = 3 * rate
        val out = FloatArray(start + length)
        val synth = StringSynth(rate, Instruments.GUITAR.voice)
        val tone = FloatArray(length)
        listOf(45, 52, 57, 60, 64).forEachIndexed { i, midi ->
            val delay = (i * 0.015 * rate).roundToInt()
            tone.fill(0f)
            synth.start((440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat())
            synth.render(tone, delay, length - delay)
            for (n in 0 until length) out[start + n] += tone[n]
        }
        val loudest = out.maxOf { abs(it) }
        for (n in out.indices) out[n] *= 0.4f / loudest
        return out
    }

    private companion object {
        val XIAOMI = DpSize(393.dp, 793.dp)
        val XIAOMI_ON_ITS_SIDE = DpSize(793.dp, 393.dp)
        const val TEST_TIMEOUT_SECONDS = 30L
        const val STEP_WAIT_MS = 10_000L
    }
}
