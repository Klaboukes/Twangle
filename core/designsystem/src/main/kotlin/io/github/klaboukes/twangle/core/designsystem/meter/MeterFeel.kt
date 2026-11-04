package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import io.github.klaboukes.twangle.core.settings.LensLook
import kotlin.math.PI

/**
 * How a look's marker follows the reading: a spring that swings [frequencyHz] times a second with a
 * damping ratio of [dampingRatio] (0.5 or more, so it settles naturally and never jitters; below 1
 * it swings a little past a new reading before it settles, like a real needle).
 */
@Immutable
data class MeterFeel(val frequencyHz: Float, val dampingRatio: Float) {
    /** Stiffness for a unit mass: ω² with ω = 2π · [frequencyHz] (a damped harmonic oscillator). */
    val stiffness: Float get() = (2f * PI.toFloat() * frequencyHz).let { it * it }

    /** The spring the meter's marker follows each new reading with. */
    fun spring(): SpringSpec<Float> = spring(dampingRatio = dampingRatio, stiffness = stiffness)
}

/**
 * The looks' springs, as on the approved mock-up page (`docs/design/meter-looks-preview.html`, `FEEL`); the meter's
 * looks take theirs from [meterFeel].
 */
object MeterFeels {
    /** Classic swings a little past a new string, like a mechanical needle (1.1's, back in Phase 78). */
    val CLASSIC = MeterFeel(frequencyHz = 1.6f, dampingRatio = 0.6f)

    /** Light arc follows almost without swinging. */
    val LIGHT_ARC = MeterFeel(frequencyHz = 2.2f, dampingRatio = 0.9f)
}

/**
 * The spring the meter's marker ([MeterReading.cents]) follows each reading with in [look]: Classic's needle its own,
 * the Light arc's, almost without swinging, for the others (the strobe drifts from the marker's cents).
 */
fun meterFeel(look: LensLook): MeterFeel = when (look) {
    LensLook.CLASSIC -> MeterFeels.CLASSIC
    LensLook.LIGHT_ARC, LensLook.STROBE -> MeterFeels.LIGHT_ARC
}

/** Smoothstep: starts and ends gently. */
val MeterSmoothstep = Easing { t -> t * t * (3f - 2f * t) }

/** How long the meter takes to fade to the "in tune" colours, and back. */
const val METER_IN_TUNE_IN_MS = 400
const val METER_IN_TUNE_OUT_MS = 600

/** How [MeterReading.tuned] moves towards 1 when the note is [inTune], and back to 0 when it leaves. */
fun meterTunedSpec(inTune: Boolean): AnimationSpec<Float> =
    tween(durationMillis = if (inTune) METER_IN_TUNE_IN_MS else METER_IN_TUNE_OUT_MS, easing = MeterSmoothstep)

/** How long the in-tune glow takes to ease in when the note is reached, and to fade when it leaves. */
const val METER_GLOW_IN_MS = 400
const val METER_GLOW_OUT_MS = 600

/** How [MeterReading.glow] moves towards 1 while the glow is [lit], and back to 0 when it goes out. */
fun meterGlowSpec(lit: Boolean): AnimationSpec<Float> =
    tween(durationMillis = if (lit) METER_GLOW_IN_MS else METER_GLOW_OUT_MS, easing = MeterSmoothstep)
