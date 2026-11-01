package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.runtime.Stable

/**
 * A number the meter, the wave and the signal bars read while drawing, on every frame: the exact
 * cents, how well the note is heard. [NaN][Float.NaN] stands for none (no note). A fun interface that
 * returns a plain `Float`, so a read boxes nothing; a `() -> Float?`, and even a `() -> Float` (Kotlin's
 * generic `Function0`), made a new `Float` object on every read. A lambda passed where one is expected
 * becomes one (`cents = { -12f }`).
 */
@Stable
fun interface LiveFloat {
    operator fun invoke(): Float
}

/** None, always: no note. */
internal val NO_LIVE_FLOAT = LiveFloat { Float.NaN }
