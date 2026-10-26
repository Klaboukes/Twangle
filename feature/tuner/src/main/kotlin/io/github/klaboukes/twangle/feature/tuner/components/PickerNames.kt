package io.github.klaboukes.twangle.feature.tuner.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import io.github.klaboukes.twangle.core.designsystem.NoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.feature.tuner.R

/** The tuning's name for users. Tunings without a known name (none today) show their id. */
@Composable
fun tuningLabel(tuning: Tuning): String = tuningNameRes(tuning.id)?.let { stringResource(it) } ?: tuning.id

/** The instrument's name for users. Instruments without a known name (none today) show their id. */
@Composable
fun instrumentLabel(instrument: Instrument): String =
    instrumentNameRes(instrument.id)?.let { stringResource(it) } ?: instrument.id

/** Open-string note names in instrument order, e.g. "D A D G B E", with raised signs ("E♭ A♭ …"). */
internal fun openStrings(tuning: Tuning, notation: Notation): AnnotatedString =
    musicText(tuning.strings.joinToString(" ") { notation.nameIn(tuning, it.pitchClass) })

/**
 * Open strings as TalkBack says them, in instrument order, e.g. "E flat, A flat, D flat …", with the
 * language's own [separator] (`tuner_spoken_separator`: "، " in Arabic, "、" in Japanese).
 */
internal fun spokenOpenStrings(tuning: Tuning, notation: Notation, speech: NoteNameSpeech, separator: String): String =
    tuning.strings.joinToString(separator) { speech.name(notation.nameIn(tuning, it.pitchClass)) }

/** Every instrument's first preset is its "Standard" tuning. */
private val standardTuningIds: Set<String> = Instruments.all.mapTo(HashSet()) { it.defaultTuning.id }

/** The string of the preset tuning [id]'s name (also read by the home-screen widget), or null for an unknown one. */
@StringRes
fun tuningNameRes(id: String): Int? = when (id) {
    in standardTuningIds -> R.string.tuning_standard
    Tunings.DROP_D.id -> R.string.tuning_drop_d
    Tunings.HALF_STEP_DOWN.id -> R.string.tuning_half_step_down
    Tunings.FULL_STEP_DOWN.id -> R.string.tuning_full_step_down
    Tunings.DADGAD.id -> R.string.tuning_dadgad
    Tunings.OPEN_G.id -> R.string.tuning_open_g
    Tunings.OPEN_D.id -> R.string.tuning_open_d
    Tunings.OPEN_E.id -> R.string.tuning_open_e
    Tunings.OPEN_C.id -> R.string.tuning_open_c
    "ukulele_low_g" -> R.string.tuning_low_g
    "ukulele_baritone" -> R.string.tuning_baritone
    else -> null
}

/** The string of the instrument [id]'s name (also read by the home-screen widget), or null for an unknown one. */
@StringRes
fun instrumentNameRes(id: String): Int? = when (id) {
    Instruments.GUITAR.id -> R.string.instrument_guitar
    Instruments.GUITAR_7.id -> R.string.instrument_guitar_7
    Instruments.GUITAR_8.id -> R.string.instrument_guitar_8
    Instruments.BASS_4.id -> R.string.instrument_bass_4
    Instruments.BASS_5.id -> R.string.instrument_bass_5
    Instruments.UKULELE.id -> R.string.instrument_ukulele
    Instruments.VIOLIN.id -> R.string.instrument_violin
    Instruments.VIOLA.id -> R.string.instrument_viola
    Instruments.CELLO.id -> R.string.instrument_cello
    Instruments.MANDOLIN.id -> R.string.instrument_mandolin
    else -> null
}
