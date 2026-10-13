package io.github.klaboukes.twangle.core.designsystem.dial

import kotlin.math.PI

/*
 * The shared dial geometry: a half circle from −[DIAL_RANGE_CENTS] (far left, flat) to
 * +[DIAL_RANGE_CENTS] (far right, sharp). The meter looks (`meter/`) draw on it.
 */

/** Range on each side of the dial, in cents. */
const val DIAL_RANGE_CENTS = 50f

/** Angle of [cents] in radians, clockwise from straight up: ±π/2 at ±[DIAL_RANGE_CENTS]. */
fun dialAngle(cents: Float): Float = (cents / DIAL_RANGE_CENTS) * (PI.toFloat() / 2f)
