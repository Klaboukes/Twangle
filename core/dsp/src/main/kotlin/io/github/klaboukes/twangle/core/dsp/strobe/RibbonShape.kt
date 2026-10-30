package io.github.klaboukes.twangle.core.dsp.strobe

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * The shape of the sound ribbon that the phone's wave view (SoundRibbon.kt in :core:designsystem)
 * and the watch tuner draw: off pitch it swells into pulses, on the note one smooth, even band.
 * WaveMotion gives the beats, phase and depth.
 */

/**
 * Height of the ribbon at [u] (0 at the left, 1 at the right) in 0..1. It is widest in the middle
 * and narrows to nothing at both ends. [depth] (0..1) pinches it into [beats] pulses across the
 * width, moved along by [phase] pulses: a larger [phase] moves the pulses to the right.
 */
fun ribbonAt(u: Float, beats: Float, phase: Float, depth: Float): Float =
    max(0f, sin(PI.toFloat() * u.coerceIn(0f, 1f))) * pulseAt(u, beats, phase, depth)

/** [ribbonAt] without the narrowing at the ends: 1 on a pulse, down to 1 − [depth] between them. */
fun pulseAt(u: Float, beats: Float, phase: Float, depth: Float): Float {
    val pulse = 0.5f + 0.5f * cos(2f * PI.toFloat() * (beats * (u - 0.5f) - phase))
    return 1f - depth.coerceIn(0f, 1f) * (1f - pulse)
}
