package io.github.klaboukes.twangle.wear

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import io.github.klaboukes.twangle.core.dsp.guide.GuideCue
import io.github.klaboukes.twangle.core.dsp.guide.GuidePulse
import io.github.klaboukes.twangle.core.dsp.guide.GuideTiming
import io.github.klaboukes.twangle.core.dsp.smoothing.InTuneDetector
import kotlin.math.abs

/**
 * Whether the watch's vibration guide is on (Phase 85): the player's [choice] on the tuner's switch, else on while a
 * screen reader is ([screenReaderOn]), as on the phone (`AppSettings.vibrationGuideOn`).
 */
fun watchVibrationGuideOn(choice: Boolean?, screenReaderOn: Boolean): Boolean = choice ?: screenReaderOn

/**
 * The vibration guide's cue for what the watch shows: nothing while no note is [heard]; in tune while it [inTune]; within
 * [edgeCents] of the note but not yet in tune, a wait (the watch's own rule: its taps are long and its microphone hears
 * them, so it falls quiet just when the in-tune check needs clean frames); else which way to turn by the whole [cents]
 * shown, as the word under the note ([WatchWord]), so the taps and the word always agree.
 */
fun watchGuideCue(heard: Boolean, inTune: Boolean, cents: Int, edgeCents: Float = InTuneDetector.DEFAULT_ENTER_CENTS): GuideCue = when {
    !heard -> GuideCue.NONE
    inTune -> GuideCue.IN_TUNE
    abs(cents) <= edgeCents -> GuideCue.WAIT
    cents < 0 -> GuideCue.TIGHTEN
    else -> GuideCue.LOOSEN
}

/**
 * Plays the watch's vibration guide (Phase 85, [GuideTiming.WATCH]). Called from the tuner's analysis thread, at most a
 * couple of times a second.
 */
interface WatchGuideVibrator {
    /** Plays [pulse] now ([GuidePulse.NONE] plays nothing). A pulse still playing gives way to it. */
    fun play(pulse: GuidePulse)

    /** Stops whatever the guide plays, e.g. the long buzz when the tuner stops listening. */
    fun stop()

    companion object {
        /** No motor: tests, previews. */
        val NONE: WatchGuideVibrator = object : WatchGuideVibrator {
            override fun play(pulse: GuidePulse) = Unit

            override fun stop() = Unit
        }
    }
}

/**
 * [WatchGuideVibrator] on the watch's motor, at full strength as the in-tune tap ([rememberWristTap]): one 150 ms tap to
 * tighten, two to loosen, one 600 ms buzz in tune ([GuideTiming.WATCH]). Played as media vibrations, not as touch
 * feedback: without attributes Samsung files the watch's taps as touch (usage TOUCH, docs/architecture/strum-and-metronome.md,
 * *On the watch*), which the watch's touch-vibration setting turns off; for a player tuning by feel that would leave the
 * guide silent without a word. The patterns are made once, so playing one allocates nothing.
 */
class AndroidWatchGuideVibrator(context: Context) : WatchGuideVibrator {
    private val vibrator: Vibrator = context.vibrator()
    private val timing = GuideTiming.WATCH

    private val tighten = VibrationEffect.createOneShot(millis(timing.tapSeconds), TAP_AMPLITUDE)

    // Off, on, off, on: the second tap starts pairSeconds after the first.
    private val loosen = VibrationEffect.createWaveform(
        longArrayOf(0L, millis(timing.tapSeconds), millis(timing.pairSeconds - timing.tapSeconds), millis(timing.tapSeconds)),
        intArrayOf(0, TAP_AMPLITUDE, 0, TAP_AMPLITUDE),
        NO_REPEAT,
    )
    private val inTune = VibrationEffect.createOneShot(millis(timing.buzzSeconds), TAP_AMPLITUDE)

    private val media: Any = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA)
    } else {
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
    }

    /** Whether the guide played since it last stopped, so [stop] never cancels anyone else's vibration. */
    @Volatile
    private var played = false

    override fun play(pulse: GuidePulse) {
        val effect = when (pulse) {
            GuidePulse.NONE -> return
            GuidePulse.TIGHTEN -> tighten
            GuidePulse.LOOSEN -> loosen
            GuidePulse.IN_TUNE -> inTune
        }
        if (!vibrator.hasVibrator()) return
        played = true
        vibrate(effect)
    }

    @Suppress("DEPRECATION")
    private fun vibrate(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, media as VibrationAttributes)
        } else {
            vibrator.vibrate(effect, media as AudioAttributes)
        }
    }

    override fun stop() {
        if (!played) return
        played = false
        vibrator.cancel()
    }

    private companion object {
        const val NO_REPEAT = -1

        fun millis(seconds: Float): Long = Math.round(seconds * 1_000f).toLong()
    }
}
