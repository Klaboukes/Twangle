package io.github.klaboukes.twangle.feature.tuner.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import io.github.klaboukes.twangle.core.designsystem.NoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.dsp.music.CommonTuning
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.LoweredName
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.feature.tuner.R

/**
 * The tuning's name for users ([tuningName]): [ownName] for one of the player's own, else the preset's.
 * Tunings without a known name (none today) show their id.
 */
@Composable
fun tuningLabel(tuning: Tuning, ownName: String? = null): String {
    val resources = LocalResources.current
    return tuningName(tuning, ownName) { id, args -> if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args) }
}

/**
 * The name [tuning] shows (the tuner, the panel, the Practice tools and the home-screen widget):
 * [ownName] for the player's own tuning, else the preset's ("Standard", "Drop D"). Lowered
 * ([Tuning.loweredName]), it is the name of what it turns into, a preset ("Half step down") or a
 * common tuning ("Drop C"), else its name with how far down ("Open G, ½ step down"). [string] looks
 * up a string resource with its format arguments; an unknown id shows as it is.
 */
fun tuningName(tuning: Tuning, ownName: String?, string: (Int, Array<out Any>) -> String): String {
    val asWritten = ownName ?: tuningNameRes(tuning.id)?.let { string(it, emptyArray()) } ?: tuning.id
    return when (val lowered = tuning.loweredName) {
        null -> asWritten
        is LoweredName.Preset -> tuningNameRes(lowered.tuning.id)?.let { string(it, emptyArray()) } ?: lowered.tuning.id
        is LoweredName.Common -> string(commonTuningNameRes(lowered.tuning), emptyArray())
        is LoweredName.Shifted -> string(shiftedTuningNameRes(lowered.semitones), arrayOf(asWritten))
    }
}

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

/** Every instrument's first preset is its "Standard" tuning, but the banjo's, which players call open G. */
private val standardTuningIds: Set<String> = (Instruments.all - Instruments.BANJO_5).mapTo(HashSet()) { it.defaultTuning.id }

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
    "banjo_open_g" -> R.string.tuning_open_g
    "banjo_double_c" -> R.string.tuning_double_c
    "banjo_double_d" -> R.string.tuning_double_d
    else -> null
}

/** The string of a [CommonTuning]'s name, such as "Drop C". */
@StringRes
internal fun commonTuningNameRes(tuning: CommonTuning): Int = when (tuning) {
    CommonTuning.C_SHARP_STANDARD -> R.string.tuning_c_sharp_standard
    CommonTuning.C_STANDARD -> R.string.tuning_c_standard
    CommonTuning.B_STANDARD -> R.string.tuning_b_standard
    CommonTuning.B_FLAT_STANDARD -> R.string.tuning_b_flat_standard
    CommonTuning.A_STANDARD -> R.string.tuning_a_standard
    CommonTuning.DROP_C_SHARP -> R.string.tuning_drop_c_sharp
    CommonTuning.DROP_C -> R.string.tuning_drop_c
    CommonTuning.DROP_B -> R.string.tuning_drop_b
    CommonTuning.DROP_B_FLAT -> R.string.tuning_drop_b_flat
    CommonTuning.DROP_A -> R.string.tuning_drop_a
}

/** The string of a name lowered [semitones] (1 to 5) without a name of its own: "%1$s, ½ step down". */
@StringRes
internal fun shiftedTuningNameRes(semitones: Int): Int = SHIFTED_NAMES[semitones - 1]

/** The "Lower by" row's amount for [semitones] (0 to 5): "None", "½ step" … "2½ steps". */
@StringRes
internal fun loweredAmountRes(semitones: Int): Int = LOWERED_AMOUNTS[semitones]

private val SHIFTED_NAMES = listOf(
    R.string.tuning_lowered_1, R.string.tuning_lowered_2, R.string.tuning_lowered_3, R.string.tuning_lowered_4, R.string.tuning_lowered_5,
)

private val LOWERED_AMOUNTS = listOf(
    R.string.tuner_lowered_none,
    R.string.tuner_lowered_1, R.string.tuner_lowered_2, R.string.tuner_lowered_3, R.string.tuner_lowered_4, R.string.tuner_lowered_5,
)

/** The string of the instrument [id]'s name (also read by the home-screen widget), or null for an unknown one. */
@StringRes
fun instrumentNameRes(id: String): Int? = when (id) {
    Instruments.GUITAR.id -> R.string.instrument_guitar
    Instruments.GUITAR_12.id -> R.string.instrument_guitar_12
    Instruments.GUITAR_7.id -> R.string.instrument_guitar_7
    Instruments.GUITAR_8.id -> R.string.instrument_guitar_8
    Instruments.BASS_4.id -> R.string.instrument_bass_4
    Instruments.BASS_5.id -> R.string.instrument_bass_5
    Instruments.UKULELE.id -> R.string.instrument_ukulele
    Instruments.VIOLIN.id -> R.string.instrument_violin
    Instruments.VIOLA.id -> R.string.instrument_viola
    Instruments.CELLO.id -> R.string.instrument_cello
    Instruments.MANDOLIN.id -> R.string.instrument_mandolin
    Instruments.BANJO_5.id -> R.string.instrument_banjo_5
    else -> null
}

/**
 * The string of what the tuner leaves out for the instrument [id], shown quietly under its tunings in
 * the panel, or null where it leaves out nothing: the 12-string's strum check and chord name.
 */
@StringRes
fun instrumentNoteRes(id: String): Int? = when (id) {
    Instruments.GUITAR_12.id -> R.string.instrument_note_guitar_12
    else -> null
}
