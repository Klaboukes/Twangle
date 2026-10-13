package io.github.klaboukes.twangle.feature.tuner.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.NoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags

/**
 * The selected tuning's name with a drop-down arrow. Tapping it lists [instrument]'s presets with
 * their open strings (in instrument order); the current one is ticked. Choosing one calls [onSelect].
 * [onExpandedChange] hears when the menu opens and closes (closed also when the picker goes away).
 * The name is set [textScale] times its size, so it can share a line with the instrument's.
 */
@Composable
fun TuningPicker(
    selected: Tuning,
    onSelect: (Tuning) -> Unit,
    modifier: Modifier = Modifier,
    instrument: Instrument = Instruments.ofTuning(selected.id) ?: Instruments.DEFAULT,
    notation: Notation = Notation.DEFAULT,
    onExpandedChange: (Boolean) -> Unit = {},
    textScale: Float = 1f,
) {
    PickerMenu(
        label = tuningLabel(selected),
        textScale = textScale,
        // TalkBack: "Standard, button, double-tap to change tuning".
        clickLabel = stringResource(R.string.tuner_change_tuning),
        items = instrument.tunings,
        selected = selected,
        itemLabel = { tuningLabel(it) },
        itemTuning = { it },
        onSelect = onSelect,
        notation = notation,
        onExpandedChange = onExpandedChange,
        modifier = modifier.testTag(TunerTestTags.TUNING_PICKER),
    )
}

/**
 * The picked instrument's name with a drop-down arrow, beside the [TuningPicker]. Tapping it lists
 * every instrument with its standard tuning's open strings; the current one is ticked. Choosing one
 * calls [onSelect] (the tuning then becomes that instrument's standard one). The name is set
 * [textScale] times its size.
 */
@Composable
fun InstrumentPicker(
    selected: Instrument,
    onSelect: (Instrument) -> Unit,
    modifier: Modifier = Modifier,
    notation: Notation = Notation.DEFAULT,
    onExpandedChange: (Boolean) -> Unit = {},
    textScale: Float = 1f,
) {
    PickerMenu(
        label = instrumentLabel(selected),
        textScale = textScale,
        // TalkBack: "Guitar, button, double-tap to change instrument".
        clickLabel = stringResource(R.string.tuner_change_instrument),
        items = Instruments.all,
        selected = selected,
        itemLabel = { instrumentLabel(it) },
        itemTuning = { it.defaultTuning },
        onSelect = onSelect,
        notation = notation,
        onExpandedChange = onExpandedChange,
        modifier = modifier.testTag(TunerTestTags.INSTRUMENT_PICKER),
    )
}

/**
 * A text button showing [label] and a drop-down arrow that opens a menu of [items], each with its
 * name and the open strings of [itemTuning] under it; [selected] is ticked.
 */
@Composable
private fun <T> PickerMenu(
    label: String,
    textScale: Float,
    clickLabel: String,
    items: List<T>,
    selected: T,
    itemLabel: @Composable (T) -> String,
    itemTuning: (T) -> Tuning,
    onSelect: (T) -> Unit,
    notation: Notation,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    LaunchedEffect(expanded) { currentOnExpandedChange(expanded) }
    DisposableEffect(Unit) { onDispose { currentOnExpandedChange(false) } }
    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = PICKER_PADDING, vertical = 8.dp),
            modifier = Modifier.semantics {
                onClick(label = clickLabel) {
                    expanded = true
                    true
                }
            },
        ) {
            val style = pickerTextStyle()
            // Only the name gets smaller; the arrow and the button's height keep their size.
            Text(
                label,
                style = if (textScale == 1f) {
                    style
                } else {
                    style.copy(fontSize = style.fontSize * textScale, letterSpacing = style.letterSpacing * textScale)
                },
                maxLines = 1,
            )
            Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val speech = rememberNoteNameSpeech()
            items.forEach { item ->
                val isSelected = item == selected
                val name = itemLabel(item)
                val tuning = itemTuning(item)
                // TalkBack: "Half step down, E flat, A flat …" (in Dutch "es, as …"), not the signs.
                val spoken = stringResource(R.string.tuner_spoken_tuning, name, spokenOpenStrings(tuning, notation, speech))
                DropdownMenuItem(
                    text = {
                        Column(modifier = Modifier.semantics { contentDescription = spoken }) {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = openStrings(tuning, notation),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                    } else {
                        null
                    },
                    modifier = Modifier.semantics { this.selected = isSelected },
                )
            }
        }
    }
}

/** Each picker button's room either side of its name and arrow. */
private val PICKER_PADDING = 12.dp

/** The drop-down arrow's width. */
private val PICKER_ARROW = 24.dp

/** What of a picker's width never shrinks with its name: padding either side and the arrow. */
internal val PICKER_FIXED_WIDTH = PICKER_PADDING * 2 + PICKER_ARROW

/** The style a picker's name is set in at full size: the title face over the button's own label style. */
@Composable
internal fun pickerTextStyle(): TextStyle = MaterialTheme.typography.labelLarge.merge(MaterialTheme.typography.titleMedium)

/** The tuning's name for users. Tunings without a known name (none today) show their id. */
@Composable
fun tuningLabel(tuning: Tuning): String = tuningNameRes(tuning.id)?.let { stringResource(it) } ?: tuning.id

/** The instrument's name for users. Instruments without a known name (none today) show their id. */
@Composable
fun instrumentLabel(instrument: Instrument): String =
    instrumentNameRes(instrument.id)?.let { stringResource(it) } ?: instrument.id

/** Open-string note names in instrument order, e.g. "D A D G B E", with raised signs ("E♭ A♭ …"). */
private fun openStrings(tuning: Tuning, notation: Notation): AnnotatedString =
    musicText(tuning.strings.joinToString(" ") { notation.nameIn(tuning, it.pitchClass) })

/** Open strings as TalkBack says them, in instrument order, e.g. "E flat, A flat, D flat …". */
private fun spokenOpenStrings(tuning: Tuning, notation: Notation, speech: NoteNameSpeech): String =
    tuning.strings.joinToString(", ") { speech.name(notation.nameIn(tuning, it.pitchClass)) }

/** Every instrument's first preset is its "Standard" tuning. */
private val standardTuningIds: Set<String> = Instruments.all.mapTo(HashSet()) { it.defaultTuning.id }

@StringRes
internal fun tuningNameRes(id: String): Int? = when (id) {
    in standardTuningIds -> R.string.tuning_standard
    Tunings.DROP_D.id -> R.string.tuning_drop_d
    Tunings.HALF_STEP_DOWN.id -> R.string.tuning_half_step_down
    Tunings.FULL_STEP_DOWN.id -> R.string.tuning_full_step_down
    Tunings.DADGAD.id -> R.string.tuning_dadgad
    Tunings.OPEN_G.id -> R.string.tuning_open_g
    Tunings.OPEN_D.id -> R.string.tuning_open_d
    Tunings.OPEN_E.id -> R.string.tuning_open_e
    "ukulele_low_g" -> R.string.tuning_low_g
    else -> null
}

@StringRes
internal fun instrumentNameRes(id: String): Int? = when (id) {
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

@Preview(showBackground = true)
@Composable
private fun TuningPickerPreview() {
    MaterialTheme { TuningPicker(Tunings.DROP_D, onSelect = {}) }
}

@Preview(showBackground = true)
@Composable
private fun InstrumentPickerPreview() {
    MaterialTheme { InstrumentPicker(Instruments.UKULELE, onSelect = {}) }
}
