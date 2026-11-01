package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.smoothing.InTuneDetector
import io.github.klaboukes.twangle.core.settings.InTunePrecision

/** An [InTuneDetector] with the window of [precision]: Relaxed is the default (5 / 7 cents). */
fun InTuneDetector.Companion.forPrecision(precision: InTunePrecision): InTuneDetector = when (precision) {
    InTunePrecision.RELAXED -> InTuneDetector()
    InTunePrecision.PRECISE -> InTuneDetector(InTuneDetector.PRECISE_ENTER_CENTS, InTuneDetector.PRECISE_EXIT_CENTS)
}

/** The exit margin of [precision]'s in-tune window: further off than this, a note is out of tune (7 or 3 cents). */
fun InTuneDetector.Companion.exitCentsFor(precision: InTunePrecision): Float = when (precision) {
    InTunePrecision.RELAXED -> InTuneDetector.DEFAULT_EXIT_CENTS
    InTunePrecision.PRECISE -> InTuneDetector.PRECISE_EXIT_CENTS
}
