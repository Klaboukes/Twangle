package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.pickerButtonShape
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.stringChipShading
import io.github.klaboukes.twangle.core.designsystem.stringChipShape
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * One button for the instrument and its tuning ("Guitar · Standard ⌄"), above the strings, as the
 * approved mock-up docs/design/combined-picker-preview.html. Tapping it opens [PickerPanel] from the
 * bottom: the instruments along the top, [instrument]'s tunings below, [tuning] ticked. Picking an
 * instrument calls [onInstrumentChange] (the tuner switches to its standard tuning) and keeps the
 * panel open; picking a tuning calls [onTuningChange] and closes it. [onExpandedChange] hears when the
 * panel opens and closes (closed also when the button goes away). [customTunings] are the player's own
 * tunings; the button names a picked one by its name, and the panel lists the instrument's under its
 * presets ([MyTunings]), each with a ⋮ menu: Edit closes the panel and calls [onEditTuning], Delete
 * asks first and then calls [onDeleteTuning] (no menu entry without its lambda). With [onNewTuning], the
 * list ends in "New tuning", which closes the panel and calls it with the instrument shown.
 */
@Composable
fun CombinedPicker(
    instrument: Instrument,
    tuning: Tuning,
    onInstrumentChange: (Instrument) -> Unit,
    onTuningChange: (Tuning) -> Unit,
    modifier: Modifier = Modifier,
    notation: Notation = Notation.DEFAULT,
    onExpandedChange: (Boolean) -> Unit = {},
    customTunings: List<CustomTuning> = emptyList(),
    onNewTuning: ((Instrument) -> Unit)? = null,
    onEditTuning: ((CustomTuning) -> Unit)? = null,
    onDeleteTuning: ((CustomTuning) -> Unit)? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    LaunchedEffect(expanded) { currentOnExpandedChange(expanded) }
    DisposableEffect(Unit) { onDispose { currentOnExpandedChange(false) } }
    PickerButton(
        instrumentName = instrumentLabel(instrument),
        tuningName = customTunings.firstOrNull { it.id == tuning.id }?.name ?: tuningLabel(tuning),
        onClick = { expanded = true },
        modifier = modifier,
    )
    if (expanded) {
        PickerPanel(
            instrument,
            tuning,
            notation,
            onInstrumentChange,
            onTuningChange,
            TuningActions(customTunings.filter { it.instrument == instrument }, onNewTuning, onEditTuning, onDeleteTuning),
            onDismiss = { expanded = false },
        )
    }
}

/**
 * The button: the instrument's name, a dot, the tuning's name and an arrow, in a thin outline in the
 * style's button shape on a see-through backing (Futuristic: a neon outline, nothing behind). It
 * keeps to one line: where the names don't fit the room they get, they are set smaller, down to
 * [MIN_PICKER_TEXT_SCALE] ([pickerTextScale]); only past that (very large fonts) do they wrap.
 */
@Composable
private fun PickerButton(instrumentName: String, tuningName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.styleTokens.style
    val neon = style == VisualStyle.FUTURISTIC
    val shape = pickerButtonShape(style, MaterialTheme.shapes.small)
    val muted = scheme.onSurfaceVariant
    val label = buildAnnotatedString {
        append(instrumentName)
        withStyle(SpanStyle(color = muted)) { append(SEPARATOR) }
        append(tuningName)
    }
    // TalkBack: "Guitar, Standard, button, double-tap to change instrument or tuning".
    val spoken = stringResource(R.string.tuner_spoken_picker, instrumentName, tuningName)
    val clickLabel = stringResource(R.string.tuner_change_instrument_or_tuning)
    val textStyle = pickerTextStyle()
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val room = constraints.maxWidth - with(LocalDensity.current) { BUTTON_FIXED_WIDTH.roundToPx() }
        val full = textMeasurer.measure(label, textStyle, maxLines = 1, softWrap = false).size.width
        val scale = pickerTextScale(full, room)
        Row(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(TunerTestTags.PICKER)
                .heightIn(min = BUTTON_HEIGHT)
                .clip(shape)
                .background(if (neon) Color.Transparent else scheme.surface.copy(alpha = BUTTON_BACKING_ALPHA), shape)
                .border(BORDER, if (neon) scheme.primary.copy(alpha = NEON_OUTLINE_ALPHA) else muted.copy(alpha = OUTLINE_ALPHA), shape)
                .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = spoken }
                .padding(start = BUTTON_START_PADDING, end = BUTTON_END_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = textStyle.scaledBy(scale),
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(ARROW_GAP))
            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, tint = muted, modifier = Modifier.size(ARROW_SIZE))
        }
    }
}

/**
 * How much smaller the button's names are set so [fullWidth] (their width at full size, in pixels)
 * fits [room]: 1 when it fits, else just enough smaller (and 2 % more, as small type doesn't narrow
 * quite in step), but never below [MIN_PICKER_TEXT_SCALE].
 */
internal fun pickerTextScale(fullWidth: Int, room: Int): Float =
    if (fullWidth + 1 <= room) 1f else max(MIN_PICKER_TEXT_SCALE, room.toFloat() / (fullWidth + 1) * SHRUNK_NAME_MARGIN)

private fun TextStyle.scaledBy(scale: Float): TextStyle =
    if (scale == 1f) this else copy(fontSize = fontSize * scale, letterSpacing = if (letterSpacing.isSp) letterSpacing * scale else letterSpacing)

/**
 * The panel from the bottom (a Material 3 modal bottom sheet over the dimmed tuner): its title, the
 * instruments in one row that scrolls sideways ([instrument] picked), then [instrument]'s tunings
 * with their strings ([tuning] ticked): the presets, then the player's own ([mine]). The list scrolls
 * when it is taller than the room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerPanel(
    instrument: Instrument,
    tuning: Tuning,
    notation: Notation,
    onInstrumentChange: (Instrument) -> Unit,
    onTuningChange: (Tuning) -> Unit,
    mine: TuningActions,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    // The whole panel, with its handle and the room for the navigation bar under it.
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize.height
    val navigationBar = WindowInsets.navigationBars.getBottom(density)
    val maxHeight = with(density) { (window * PANEL_MAX_HEIGHT - navigationBar).toDp() - HANDLE_HEIGHT }
    // Closed before anything else happens, so the panel isn't open again on coming back from the editor.
    val close: (then: () -> Unit) -> Unit = { then ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismiss()
                then()
            }
        }
    }
    val pick: (Tuning) -> Unit = { picked ->
        onTuningChange(picked)
        close {}
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = panelShape(MaterialTheme.styleTokens.style),
        containerColor = scheme.surface,
        contentColor = scheme.onSurface,
        modifier = Modifier.testTag(TunerTestTags.PICKER_PANEL),
    ) {
        Column(Modifier.heightIn(max = maxHeight)) {
            Text(
                stringResource(R.string.tuner_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(start = PANEL_PADDING, end = PANEL_PADDING, top = 4.dp, bottom = 6.dp)
                    .semantics { heading() },
            )
            InstrumentRow(instrument, onInstrumentChange)
            HorizontalDivider(color = scheme.outlineVariant)
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = LIST_PADDING, end = LIST_PADDING, top = 2.dp, bottom = 22.dp),
            ) {
                Column(Modifier.selectableGroup()) {
                    PresetTunings(instrument, tuning, notation, onSelect = pick)
                    if (mine.tunings.isNotEmpty() || mine.onNew != null) {
                        MyTunings(
                            mine.tunings,
                            tuning,
                            notation,
                            onSelect = pick,
                            onEdit = mine.onEdit?.let { edit -> { picked: CustomTuning -> close { edit(picked) } } },
                            onDelete = mine.onDelete,
                        )
                    }
                }
                mine.onNew?.let { onNew -> NewTuningRow(onClick = { close { onNew(instrument) } }) }
            }
        }
    }
}

/**
 * The player's own tunings for the instrument shown ([tunings]) and what "New tuning" ([onNew]), Edit
 * ([onEdit]) and Delete ([onDelete]) do; null leaves that row or menu entry out.
 */
private data class TuningActions(
    val tunings: List<CustomTuning>,
    val onNew: ((Instrument) -> Unit)?,
    val onEdit: ((CustomTuning) -> Unit)?,
    val onDelete: ((CustomTuning) -> Unit)?,
)

/** [instrument]'s preset tunings, each a [TuningRow]; [selected] is ticked. */
@Composable
private fun PresetTunings(instrument: Instrument, selected: Tuning, notation: Notation, onSelect: (Tuning) -> Unit) {
    for (preset in instrument.tunings) {
        TuningRow(tuningLabel(preset), preset, notation, selected = preset == selected, onClick = { onSelect(preset) })
    }
}

/** The list's last row: a + and "New tuning", opening the tuning editor for the instrument shown. */
@Composable
private fun NewTuningRow(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(TunerTestTags.NEW_TUNING)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(CHECK_SIZE))
        Text(
            stringResource(R.string.tuner_new_tuning),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = scheme.primary,
        )
    }
}

/** Every instrument in one row that scrolls sideways, [selected] picked and scrolled into view when the panel opens. */
@Composable
private fun InstrumentRow(selected: Instrument, onSelect: (Instrument) -> Unit) {
    val picked = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        // Once it has been laid out.
        withFrameNanos { }
        picked.bringIntoView()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = PANEL_PADDING, end = PANEL_PADDING, bottom = 6.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Instruments.all.forEachIndexed { index, instrument ->
            val isSelected = instrument == selected
            InstrumentChip(
                name = instrumentLabel(instrument),
                selected = isSelected,
                index = index,
                onClick = { onSelect(instrument) },
                modifier = if (isSelected) Modifier.bringIntoViewRequester(picked) else Modifier,
            )
        }
    }
}

/**
 * An instrument in the panel's row: its name in a thin outline in the style's button shape (Ancient's
 * pebbles alternate, Retro's push buttons are shaded, Futuristic's are neon outlines); the [selected]
 * one in the container colour with a 2 dp outline in the accent.
 */
@Composable
private fun InstrumentChip(name: String, selected: Boolean, index: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.styleTokens.style
    val neon = style == VisualStyle.FUTURISTIC
    val shape: Shape = if (style == VisualStyle.ANCIENT) stringChipShape(style, index % 2) else MaterialTheme.shapes.small
    val fill = when {
        neon -> if (selected) scheme.primary.copy(alpha = NEON_PICKED_ALPHA) else Color.Transparent
        selected -> scheme.primaryContainer
        style == VisualStyle.RETRO -> scheme.surface.copy(alpha = RETRO_BACKING_ALPHA)
        else -> Color.Transparent
    }
    val outline = when {
        selected -> scheme.primary
        neon -> scheme.primary.copy(alpha = NEON_OUTLINE_ALPHA)
        else -> scheme.onSurfaceVariant.copy(alpha = OUTLINE_ALPHA)
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = CHIP_HEIGHT)
            .clip(shape)
            .background(fill, shape)
            .then(if (style == VisualStyle.RETRO) Modifier.stringChipShading(style, shape, scheme.onSurface) else Modifier)
            .border(if (selected) PICKED_BORDER else BORDER, outline, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = CHIP_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected && !neon) scheme.onPrimaryContainer else scheme.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * A tuning in the panel: its [name] over its open strings, the [selected] one in the container colour
 * and ticked. TalkBack says the name and the strings ("Half step down, E flat, A flat …"). The
 * player's own tunings put their ⋮ button in [trailing], at the end, outside the part that picks it.
 */
@Composable
internal fun TuningRow(
    name: String,
    tuning: Tuning,
    notation: Notation,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val speech = rememberNoteNameSpeech()
    val spoken = stringResource(R.string.tuner_spoken_tuning, name, spokenOpenStrings(tuning, notation, speech))
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else Color.Transparent, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ROW_HEIGHT)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = spoken }
                .padding(start = 12.dp, end = if (trailing != null) 4.dp else 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                )
                Text(openStrings(tuning, notation), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(CHECK_SIZE))
            }
        }
        trailing?.invoke()
    }
}

/** The panel's top corners: the style's card corners, at least 14 dp (Futuristic's cut, Space Age's 20 dp). */
private fun panelShape(style: VisualStyle): Shape = when (style) {
    VisualStyle.FUTURISTIC -> CutCornerShape(topStart = 14.dp, topEnd = 14.dp)
    VisualStyle.RETRO -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    VisualStyle.SPACE_AGE -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    else -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
}

/** The style a picker's name is set in at full size: the title face over the button's own label style. */
@Composable
internal fun pickerTextStyle(): TextStyle = MaterialTheme.typography.labelLarge.merge(MaterialTheme.typography.titleMedium)

/** Between the instrument's and the tuning's names on the button. */
private const val SEPARATOR = " · "

/** The button's names never get smaller than this; past it they wrap. */
internal const val MIN_PICKER_TEXT_SCALE = 0.7f

/** Names set smaller are set this much smaller again, so rounding never cuts them off. */
private const val SHRUNK_NAME_MARGIN = 0.98f

private val BUTTON_HEIGHT = 44.dp
private val BUTTON_START_PADDING = 18.dp
private val BUTTON_END_PADDING = 14.dp
private val ARROW_GAP = 6.dp
private val ARROW_SIZE = 20.dp
private val BORDER = 1.dp
private val PICKED_BORDER = 2.dp

/** What of the button's width never shrinks with its names: padding, arrow and outline. */
private val BUTTON_FIXED_WIDTH = BUTTON_START_PADDING + BUTTON_END_PADDING + ARROW_GAP + ARROW_SIZE + BORDER * 2

/** The button's see-through backing, so it reads on any style's background (the mock-up's 45 %). */
private const val BUTTON_BACKING_ALPHA = 0.45f

/** The thin outline: the muted text colour at 60 %; Futuristic's neon: the accent at 55 %. */
private const val OUTLINE_ALPHA = 0.6f
private const val NEON_OUTLINE_ALPHA = 0.55f

/** Futuristic's picked instrument: the accent at 12 % behind it. */
private const val NEON_PICKED_ALPHA = 0.12f

/** Retro's push buttons stand on the surface colour at 85 %. */
private const val RETRO_BACKING_ALPHA = 0.85f

/** The panel is at most this share of the window's height, so the tuner shows above it. */
private const val PANEL_MAX_HEIGHT = 0.74f

/** Material's drag handle at the panel's top: 4 dp with 22 dp above and below. */
private val HANDLE_HEIGHT = 48.dp

private val PANEL_PADDING = 20.dp
private val LIST_PADDING = 12.dp
private val CHIP_HEIGHT = 40.dp
private val CHIP_PADDING = 16.dp

private val CHIP_GAP = 8.dp
private val ROW_HEIGHT = 56.dp
private val CHECK_SIZE = 22.dp

/** The list's side padding, which [MyTunings]' heading lines up with. */
internal val TUNING_TEXT_INSET = 12.dp

@Preview(showBackground = true)
@Composable
private fun CombinedPickerPreview() {
    MaterialTheme { CombinedPicker(Instruments.UKULELE, Instruments.UKULELE.defaultTuning, onInstrumentChange = {}, onTuningChange = {}) }
}
