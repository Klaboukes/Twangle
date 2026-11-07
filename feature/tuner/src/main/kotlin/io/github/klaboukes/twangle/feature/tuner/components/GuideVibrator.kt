package io.github.klaboukes.twangle.feature.tuner.components

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import io.github.klaboukes.twangle.core.dsp.guide.GuidePulse
import io.github.klaboukes.twangle.core.dsp.guide.VibrationGuide

/**
 * Plays the vibration guide's pulses (Phase 85, [VibrationGuide]). Called from the tuner's analysis thread, at most a
 * few times a second.
 */
interface GuideVibrator {
    /** Plays [pulse] now ([GuidePulse.NONE] plays nothing). A pulse still playing gives way to it. */
    fun play(pulse: GuidePulse)

    /** Stops whatever the guide is playing, e.g. the long buzz when the tuner stops listening. */
    fun stop()

    companion object {
        /** No motor: tests, previews. */
        val NONE: GuideVibrator = object : GuideVibrator {
            override fun play(pulse: GuidePulse) = Unit

            override fun stop() = Unit
        }
    }
}

/**
 * [GuideVibrator] on the phone's own vibration motor, as *Vibrate when in tune* uses it ([InTuneHaptic]): the motor
 * directly, not the touch-feedback haptics, which many people switch off for the whole phone. The three patterns are
 * made once, so playing one allocates nothing.
 */
class AndroidGuideVibrator(context: Context) : GuideVibrator {
    private val vibrator: Vibrator = context.vibrator()

    private val tighten = VibrationEffect.createOneShot(millis(VibrationGuide.TAP_SECONDS), VibrationEffect.DEFAULT_AMPLITUDE)

    // Off, on, off, on: the second tap starts PAIR_SECONDS after the first.
    private val loosen = VibrationEffect.createWaveform(
        longArrayOf(
            0L,
            millis(VibrationGuide.TAP_SECONDS),
            millis(VibrationGuide.PAIR_SECONDS - VibrationGuide.TAP_SECONDS),
            millis(VibrationGuide.TAP_SECONDS),
        ),
        NO_REPEAT,
    )
    private val inTune = VibrationEffect.createOneShot(millis(VibrationGuide.IN_TUNE_BUZZ_SECONDS), VibrationEffect.DEFAULT_AMPLITUDE)

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
        vibrator.vibrate(effect)
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
