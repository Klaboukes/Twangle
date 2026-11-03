package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.SheetUnderNavigationBar
import io.github.klaboukes.twangle.core.designsystem.CHOICE_ROW_GAP
import io.github.klaboukes.twangle.core.designsystem.ChoiceRow
import io.github.klaboukes.twangle.core.designsystem.PressIndication
import io.github.klaboukes.twangle.core.designsystem.StepperButton
import io.github.klaboukes.twangle.core.designsystem.R as DesignR
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.pressSink
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import kotlinx.coroutines.launch

/**
 * The one picker, "What are you tuning?" (2.0, Phase 70; docs/design/v2-one-look-preview.html, *Where
 * the picker went*), opened from the picker chip (`PickerChip`) at the top of the tuner and of the
 * Practice tools (there without the Any note switch, as the charts need an instrument): a sheet from the bottom with the instruments as pills along the top
 * and [instrument]'s tunings below, [tuning] ticked. Picking an instrument calls [onInstrumentChange]
 * (the tuner switches to its standard tuning) and keeps the sheet open; picking a tuning calls
 * [onTuningChange] and closes it. [onDismiss] is called once it has closed (a pick, a tap outside, a
 * swipe down or Back). [customTunings] are the player's own tunings; the sheet lists the instrument's
 * under its presets ([MyTunings]), each with a ⋮ menu: Edit closes the sheet and calls [onEditTuning]
 * (the tuning editor), Delete asks first and then calls [onDeleteTuning] (no menu entry without its
 * lambda). With [onNewTuning], the list ends in "New tuning", which closes the sheet and calls it with
 * the instrument shown (the tuning editor again). With [onLoweredByChange], a quiet "Lower by" row
 * under the list lowers the picked tuning a semitone at a time (as the owner chose in
 * docs/design/lower-tuning.html), calling it with the new amount and keeping the sheet open; the
 * ticked row then shows the lowered tuning. [tuning] comes lowered ([Tuning.loweredBy]); a tap on its
 * row keeps it so, another row picks that tuning as written. With [onAnyNoteChange] (the tuner's, not
 * the Practice tools'), the sheet ends in the Any note switch ([AnyNoteRow]), on while [anyNote]: the
 * tuner's chromatic mode (`TunerMode.CHROMATIC`), set here since the mode switch went; a tap on it
 * calls [onAnyNoteChange] and closes the sheet, as a pick does. With [onIntonationCheck] (the tuner's in guitar mode,
 * for an instrument the 12th-fret check is offered for, in a build with the check; 2.0, Phase 71, the owner's pick B of
 * docs/design/v2-intonation-way-in-preview.html, 2026-11-03) the sheet's last row is the 12th-fret check
 * ([IntonationCheckRow]): a tap closes the sheet and calls it, which starts the check on the tuner.
 */
@Composable
fun InstrumentTuningPanel(
    instrument: Instrument,
    tuning: Tuning,
    onInstrumentChange: (Instrument) -> Unit,
    onTuningChange: (Tuning) -> Unit,
    onDismiss: () -> Unit,
    notation: Notation = Notation.DEFAULT,
    customTunings: List<CustomTuning> = emptyList(),
    onNewTuning: ((Instrument) -> Unit)? = null,
    onEditTuning: ((CustomTuning) -> Unit)? = null,
    onDeleteTuning: ((CustomTuning) -> Unit)? = null,
    onLoweredByChange: ((Int) -> Unit)? = null,
    anyNote: Boolean = false,
    onAnyNoteChange: ((Boolean) -> Unit)? = null,
    onIntonationCheck: (() -> Unit)? = null,
) {
    PickerPanel(
        instrument,
        tuning,
        notation,
        onInstrumentChange,
        onTuningChange,
        TuningActions(customTunings.filter { it.instrument == instrument }, onNewTuning, onEditTuning, onDeleteTuning),
        onDismiss = onDismiss,
        onLoweredByChange = onLoweredByChange,
        anyNote = anyNote,
        onAnyNoteChange = onAnyNoteChange,
        onIntonationCheck = onIntonationCheck,
    )
}

/**
 * The sheet from the bottom (a Material 3 modal bottom sheet over the dimmed tuner): its title, the
 * instruments as pills in one row that scrolls sideways ([instrument] picked; twelve of them would take
 * three or four rows wrapped, so they scroll as before rather than wrap as the mock-up's five did), then
 * [instrument]'s tunings with their strings ([tuning] ticked): the presets, then the player's own
 * ([mine]). The list scrolls when it is taller than the room, and opens scrolled to the ticked tuning.
 * "Lower by", Any note and the 12th-fret check stay under it, always in view; in a short window (a phone on its
 * side) the first two side by side and the check at the list's end, so the list keeps its rows.
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
    onLoweredByChange: ((Int) -> Unit)?,
    anyNote: Boolean,
    onAnyNoteChange: ((Boolean) -> Unit)?,
    onIntonationCheck: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    // The whole panel, with its handle and the room for the navigation bar under it.
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize.height
    val navigationBar = WindowInsets.navigationBars.getBottom(density)
    // A short window (a phone on its side) gives the panel all its height up to the status bar (Phase 63's
    // review): at 74 % only one tuning showed under the title, the instruments and "Lower by".
    val short = with(density) { window.toDp() } < SHORT_WINDOW
    val statusBar = WindowInsets.statusBars.getTop(density)
    val maxHeight = with(density) {
        if (short) (window - statusBar - navigationBar).toDp() - HANDLE_HEIGHT - SHORT_TOP_GAP else (window * PANEL_MAX_HEIGHT - navigationBar).toDp() - HANDLE_HEIGHT
    }
    // Closed before anything else happens, so the panel isn't open again on coming back from the editor.
    val close: (then: () -> Unit) -> Unit = { then ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismiss()
                then()
            }
        }
    }
    // The ticked tuning, scrolled into view when the panel opens: a bass's or a long list of the
    // player's own tunings can put it below the fold.
    val ticked = remember { BringIntoViewRequester() }
    val pick: (Tuning) -> Unit = { picked ->
        onTuningChange(picked)
        close {}
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = PANEL_SHAPE,
        containerColor = scheme.surface,
        contentColor = scheme.onSurface,
        modifier = Modifier.testTag(TunerTestTags.PICKER_PANEL),
    ) {
        // The sheet's colour under the gesture bar too, no white strip there.
        SheetUnderNavigationBar()
        // In the panel's own content, which the sheet composes in its own window, later than the
        // code round it: asked from outside, the rows might not be there yet.
        LaunchedEffect(Unit) {
            // Once they have been laid out.
            withFrameNanos { }
            ticked.bringIntoView()
        }
        // Last, as the check is used now and then, by those who set up their own guitar (owner, 2026-11-03); in a short
        // window at the list's end, since a row of its own under the list left a tuning and a half in view there.
        val intonationRow = onIntonationCheck?.let { start ->
            @Composable { inList: Boolean -> IntonationCheckRow(onClick = { close(start) }, inList = inList) }
        }
        Column(Modifier.heightIn(max = maxHeight)) {
            val titleStyle = MaterialTheme.typography.titleLarge
            val title = @Composable { modifier: Modifier ->
                Text(
                    stringResource(R.string.tuner_picker_title),
                    style = titleStyle,
                    modifier = modifier.semantics { heading() },
                    // Beside the instruments at most two lines, set smaller where they don't hold it (large text).
                    maxLines = if (short) SHORT_TITLE_MAX_LINES else Int.MAX_VALUE,
                    autoSize = if (short) TextAutoSize.StepBased(minFontSize = SHORT_TITLE_MIN_SIZE, maxFontSize = titleStyle.fontSize, stepSize = 1.sp) else null,
                )
            }
            if (short) {
                // A short window: the title beside the instruments, so the list under them keeps more rows.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    title(Modifier.padding(start = PANEL_PADDING, bottom = 6.dp).widthIn(max = SHORT_TITLE_MAX_WIDTH))
                    Box(Modifier.weight(1f)) { InstrumentRow(instrument, onInstrumentChange) }
                }
            } else {
                title(Modifier.padding(start = PANEL_PADDING, end = PANEL_PADDING, top = 4.dp, bottom = 6.dp))
                InstrumentRow(instrument, onInstrumentChange)
            }
            HorizontalDivider(color = scheme.outlineVariant)
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = LIST_PADDING, end = LIST_PADDING, top = 2.dp, bottom = 22.dp),
            ) {
                Column(Modifier.selectableGroup()) {
                    PresetTunings(instrument, tuning, notation, onSelect = pick, ticked = ticked)
                    if (mine.tunings.isNotEmpty() || mine.onNew != null) {
                        MyTunings(
                            mine.tunings,
                            tuning,
                            notation,
                            onSelect = pick,
                            onEdit = mine.onEdit?.let { edit -> { picked: CustomTuning -> close { edit(picked) } } },
                            onDelete = mine.onDelete,
                            ticked = ticked,
                        )
                    }
                }
                mine.onNew?.let { onNew -> NewTuningRow(onClick = { close { onNew(instrument) } }) }
                // What the tuner can't do for this instrument, so nobody looks for it (the 12-string's strum check).
                instrumentNoteRes(instrument.id)?.let { InstrumentNote(stringResource(it)) }
                if (short && intonationRow != null) {
                    HorizontalDivider(Modifier.padding(top = 10.dp), color = scheme.outlineVariant)
                    intonationRow(true)
                }
            }
            // The tuning as written can go this far down; the row is left out where it can't go at all
            // (a five-string bass's B0) and while another instrument's tunings are shown.
            val lowest = tuning.loweredBy + tuning.lowerableBy
            val lowerBy: (@Composable () -> Unit)? = if (onLoweredByChange != null && lowest > 0 && instrument.ownsTuning(tuning, mine.tunings)) {
                { LowerByRow(tuning.loweredBy, lowest, onLoweredByChange) }
            } else {
                null
            }
            val anyNoteRow = onAnyNoteChange?.let { change ->
                @Composable {
                    AnyNoteRow(anyNote, onChange = { on ->
                        change(on)
                        close {}
                    })
                }
            }
            val lastRow = intonationRow?.takeIf { !short }?.let { row -> @Composable { row(false) } }
            if (short && lowerBy != null && anyNoteRow != null) {
                // A short window (a phone on its side): the two side by side, so the list keeps its rows
                // (one above the other they left a tuning and a half in view).
                HorizontalDivider(color = scheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { lowerBy() }
                    Box(Modifier.weight(1f)) { anyNoteRow() }
                }
            } else {
                for (row in listOfNotNull(lowerBy, anyNoteRow, lastRow)) {
                    HorizontalDivider(color = scheme.outlineVariant)
                    row()
                }
            }
        }
    }
}

/** Whether [tuning] is one of this instrument's presets or of its player's own tunings ([mine]). */
private fun Instrument.ownsTuning(tuning: Tuning, mine: List<CustomTuning>): Boolean =
    tuningById(tuning.id) != null || mine.any { it.id == tuning.id }

/**
 * The panel's bottom row: "Lower by", then − (back up a half step), how far the picked tuning is
 * lowered ("None", "½ step" … "2½ steps") and + (a half step lower, at most to [lowest]): the shared
 * stepper ([StepperButton]), as it changes a number, left to right in every language. The amount
 * takes the room of the widest one, so the buttons never move; TalkBack hears the label with the
 * amount, and again when it changes.
 */
@Composable
internal fun LowerByRow(loweredBy: Int, lowest: Int, onChange: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.tuner_lower_by)
    val amount = stringResource(loweredAmountRes(loweredBy))
    val spoken = stringResource(R.string.tuner_spoken_lower_by, label, amount)
    val amountStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TunerTestTags.LOWER_BY)
            .padding(start = PANEL_PADDING, end = LIST_PADDING, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .semantics { hideFromAccessibility() },
        )
        // − on the left, + on the right in every language, as every stepper.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperButton(
                    more = false,
                    label = stringResource(R.string.tuner_lower_less),
                    onClick = { onChange(loweredBy - 1) },
                    modifier = Modifier.testTag(TunerTestTags.LOWER_LESS),
                    enabled = loweredBy > 0,
                )
                Box(contentAlignment = Alignment.Center) {
                    for (semitones in 0..Tuning.MAX_LOWERED_BY) {
                        Text(
                            stringResource(loweredAmountRes(semitones)),
                            style = amountStyle,
                            maxLines = 1,
                            modifier = Modifier
                                .alpha(0f)
                                .clearAndSetSemantics {},
                        )
                    }
                    Text(
                        musicText(amount),
                        style = amountStyle,
                        color = scheme.onSurface,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .testTag(TunerTestTags.LOWERED_AMOUNT)
                            .semantics {
                                contentDescription = spoken
                                liveRegion = LiveRegionMode.Polite
                            },
                    )
                }
                StepperButton(
                    more = true,
                    label = stringResource(R.string.tuner_lower_more),
                    onClick = { onChange(loweredBy + 1) },
                    modifier = Modifier.testTag(TunerTestTags.LOWER_MORE),
                    enabled = loweredBy < lowest,
                )
            }
        }
    }
}

/**
 * The sheet's last row: "Any note" over "The nearest note, no strings", and a switch, on while [on]
 * (the tuner's chromatic mode, which shows any note and no strings). The whole row toggles it, as a
 * Settings switch does; TalkBack hears one switch with both lines.
 */
@Composable
internal fun AnyNoteRow(on: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .toggleable(value = on, onValueChange = onChange, role = Role.Switch)
            .testTag(TunerTestTags.ANY_NOTE)
            .padding(start = PANEL_PADDING, end = PANEL_PADDING, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.tuner_any_note),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = scheme.onSurface,
            )
            Text(
                stringResource(R.string.tuner_any_note_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        // The row handles the tap, so the switch itself doesn't.
        Switch(checked = on, onCheckedChange = null)
    }
}

/**
 * The sheet's last row in guitar mode: "12th-fret check" over "Is the saddle in the right place?", and a chevron at its
 * end (pointing the way the text reads), as the row opens something rather than switching. The whole row is one button
 * ([onClick]); TalkBack hears both lines. [inList]: at the end of the tunings' list (a short window), its text in line
 * with theirs.
 */
@Composable
internal fun IntonationCheckRow(onClick: () -> Unit, inList: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(TunerTestTags.INTONATION_ROW)
            .padding(horizontal = if (inList) TUNING_TEXT_INSET else PANEL_PADDING)
            .padding(top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.tuner_intonation_check),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = scheme.onSurface,
            )
            Text(
                stringResource(R.string.tuner_intonation_check_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        // The ▾ icon turned to point along the line: › in left-to-right languages, ‹ in right-to-left ones.
        Icon(
            painterResource(DesignR.drawable.ic_expand_more),
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(CHECK_SIZE).rotate(if (rtl) 90f else -90f),
        )
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

/** [instrument]'s preset tunings, each a [TuningRow]; [selected] is ticked, and brought into view by [ticked]. */
@Composable
private fun PresetTunings(
    instrument: Instrument,
    selected: Tuning,
    notation: Notation,
    onSelect: (Tuning) -> Unit,
    ticked: BringIntoViewRequester? = null,
) {
    for (preset in instrument.tunings) {
        val isSelected = preset.id == selected.id
        // The picked one as it is, lowered or not.
        val shown = if (isSelected) selected else preset
        TuningRow(
            tuningLabel(shown),
            shown,
            notation,
            selected = isSelected,
            onClick = { onSelect(shown) },
            modifier = if (isSelected && ticked != null) Modifier.bringIntoViewRequester(ticked) else Modifier,
        )
    }
}

/** A quiet line under the tunings: what the tuner leaves out for the instrument shown ([instrumentNoteRes]). */
@Composable
private fun InstrumentNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = TUNING_TEXT_INSET, end = TUNING_TEXT_INSET, top = 10.dp)
            .testTag(TunerTestTags.PICKER_NOTE),
    )
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
        Icon(painterResource(DesignR.drawable.ic_add), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(CHECK_SIZE))
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
        Instruments.all.forEach { instrument ->
            val isSelected = instrument == selected
            InstrumentChip(
                name = instrumentLabel(instrument),
                selected = isSelected,
                onClick = { onSelect(instrument) },
                modifier = if (isSelected) Modifier.bringIntoViewRequester(picked) else Modifier,
            )
        }
    }
}

/**
 * An instrument in the sheet's row, a pill (Ink's, docs/design/v2-one-look-preview.html): its name in
 * a thin outline; the [selected] one filled in the text colour, its name in the sheet's.
 */
@Composable
private fun InstrumentChip(name: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape: Shape = CircleShape
    val fill = if (selected) scheme.onSurface else Color.Transparent
    val outline = if (selected) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = OUTLINE_ALPHA)
    // The press effect: the chip sinks, and the light lies over its fill, under its name.
    val press = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = CHIP_HEIGHT)
            .pressSink(press)
            .clip(shape)
            .background(fill, shape)
            .border(BORDER, outline, shape)
            .selectable(selected = selected, interactionSource = press, indication = PressIndication(shape), role = Role.RadioButton) {
                press.pressFlash()
                onClick()
            }
            .padding(horizontal = CHIP_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.surface else scheme.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * A tuning in the panel: its [name] over its open strings on the shared [ChoiceRow], the [selected]
 * one in the container colour with the accent outline and ticked. TalkBack says the name and the strings ("Half step down, E flat, A flat …"). The
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
    val spoken = stringResource(R.string.tuner_spoken_tuning, speech.text(name), spokenOpenStrings(tuning, notation, speech, stringResource(R.string.tuner_spoken_separator)))
    ChoiceRow(
        selected = selected,
        onClick = onClick,
        modifier = modifier.padding(top = CHOICE_ROW_GAP),
        minHeight = ROW_HEIGHT,
        spoken = spoken,
        trailing = trailing,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                musicText(name),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = LocalContentColor.current,
            )
            // Lowest string first, left to right, in right-to-left languages too (else "E♭ … E♭"
            // would put its last ♭ in front).
            Text(
                openStrings(tuning, notation),
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** The panel's top corners: 22 dp, Ink's large shape. */
private val PANEL_SHAPE: Shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

private val BORDER = 1.dp

/** The thin outline: the muted text colour at 60 %. */
private const val OUTLINE_ALPHA = 0.6f

/** The panel is at most this share of the window's height, so the tuner shows above it. */
private const val PANEL_MAX_HEIGHT = 0.74f

/** Below this window height (every phone on its side, none upright) the panel may take the whole height. */
private val SHORT_WINDOW = 500.dp

/** In a short window, the panel's top stays this far under the status bar. */
private val SHORT_TOP_GAP = 8.dp

/** In a short window the title, beside the instruments, takes at most this width (two lines where it needs more). */
private val SHORT_TITLE_MAX_WIDTH = 220.dp
private const val SHORT_TITLE_MAX_LINES = 2
private val SHORT_TITLE_MIN_SIZE = 12.sp

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
private fun InstrumentTuningPanelPreview() {
    MaterialTheme { InstrumentTuningPanel(Instruments.UKULELE, Instruments.UKULELE.defaultTuning, onInstrumentChange = {}, onTuningChange = {}, onDismiss = {}) }
}
