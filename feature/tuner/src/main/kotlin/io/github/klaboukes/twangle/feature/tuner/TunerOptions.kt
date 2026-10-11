package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.TunerMode

/**
 * The user's choices that decide what a reading is compared with. Built by [TunerViewModel] from
 * the mode switch and the saved settings, and only rebuilt when one of them changes, so handing it
 * to [TunerInterpreter] every frame doesn't allocate.
 */
data class TunerOptions(
    val mode: TunerMode = TunerMode.AUTO,
    val tuning: Tuning = Tunings.DEFAULT,
    /** Concert pitch in Hz (A4 calibration). */
    val a4Hz: Float = NoteMapper.DEFAULT_A4_HZ,
    /** String index the user locked in guitar mode, or null to pick the nearest string automatically. */
    val lockedString: Int? = null,
    /** How note names are written. */
    val notation: Notation = Notation.DEFAULT,
)
