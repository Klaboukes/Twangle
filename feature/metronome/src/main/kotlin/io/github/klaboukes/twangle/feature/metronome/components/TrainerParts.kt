package io.github.klaboukes.twangle.feature.metronome.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.LINING_FIGURES
import io.github.klaboukes.twangle.core.designsystem.StepperButton
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.designsystem.NumberFont
import io.github.klaboukes.twangle.core.designsystem.PanelLabel
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.SpeedTrainer
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.metronome.R

/*
 * The speed trainer (Phase 62): a row in the options panel with its name, what it does in words and its
 * switch, which opens its settings (round 1's panel, docs/design/metronome-practice-preview.html) in a
 * floating panel of the same kind (the shared [FloatingPanel]); while it is on, its words on the line
 * between the Vibrate and Listen buttons too ([TrainerLine]), opening its settings from there. From
 * Phase 75 to 2026-11-04 the same row stood on a card over the meter (the first 2.0 mock-up's
 * `.trainer`), at last in a place kept for it while off; before Phase 75 its words stood on the status
 * line, as again now.
 */

/**
 * The speed trainer in words (Phase 62): [text] ("95 → 120 · +5 in 2 bars") under its name on its row;
 * TalkBack hears [spoken].
 */
@Immutable
data class TrainerStatus(val text: String, val spoken: String)

/**
 * The speed trainer's row in the options panel (Phase 75): its rising-line icon (in the accent while
 * [on]), its name over [status]'s words in the muted colour (its plan while stopped, where it stands
 * while it climbs), and its switch at the end ([onOnChange]). A tap on the row opens its settings
 * ([onOpen]). At least [TRAINER_ROW_HEIGHT] tall; the words shrink to one line each rather than wrap.
 * TalkBack hears a button with [TrainerStatus.spoken] and a switch "Speed trainer".
 */
@Composable
internal fun TrainerRow(
    on: Boolean,
    status: TrainerStatus,
    onOnChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val name = stringResource(R.string.metronome_trainer)
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = TRAINER_ROW_HEIGHT)
            .pressFeedback(source, shape)
            .padding(start = ROW_PADDING, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .testTag(MetronomeTestTags.TRAINER_OPTION)
                .heightIn(min = TRAINER_ROW_HEIGHT)
                .clickable(interactionSource = source, indication = null, role = Role.Button) {
                    source.pressFlash()
                    onOpen()
                }
                .semantics(mergeDescendants = true) { contentDescription = status.spoken },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_trainer),
                contentDescription = null,
                tint = if (on) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(ROW_ICON),
            )
            Spacer(Modifier.width(ROW_PADDING))
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                RowName(name)
                val style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontFeatureSettings = "$LINING_FIGURES, tnum")
                val smallest = with(LocalDensity.current) { MIN_NAME_SIZE.toSp() }
                Text(
                    status.text,
                    style = style,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = smallest, maxFontSize = style.fontSize, stepSize = 0.5.sp),
                    modifier = Modifier.testTag(MetronomeTestTags.TRAINER_OPTION_TEXT),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        TrainerSwitch(on, name, onOnChange, MetronomeTestTags.TRAINER_OPTION_SWITCH)
    }
}

/**
 * The trainer on the screen while it is on (2026-11-04, the owner: most players never use it, so it keeps
 * no room of its own): on the line between the Vibrate and Listen buttons, its rising-line icon in the
 * accent and [status]'s words (its plan while stopped, where it stands while it climbs) in the line's muted
 * 14 sp, one line, set smaller where a language or large text needs it (down to [MIN_NAME_SIZE]), the
 * whole line its touch area. A tap opens its settings ([onOpen]) from [onPlaced]'s place. TalkBack hears a
 * button with [TrainerStatus.spoken]; not a live region, so it never talks over the clicks.
 */
@Composable
internal fun TrainerLine(status: TrainerStatus, onOpen: () -> Unit, modifier: Modifier = Modifier, onPlaced: (Rect) -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 1.2.em, fontFeatureSettings = "$LINING_FIGURES, tnum")
    val smallest = with(LocalDensity.current) { MIN_NAME_SIZE.toSp() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .onGloballyPositioned { onPlaced(it.boundsInRoot()) }
            .testTag(MetronomeTestTags.TRAINER)
            .pressFeedback(source, PILL, lightInset = LINE_LIGHT_INSET)
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                source.pressFlash()
                onOpen()
            }
            .semantics(mergeDescendants = true) { contentDescription = status.spoken }
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_trainer), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(LINE_ICON))
        Spacer(Modifier.width(6.dp))
        Text(
            status.text,
            style = style,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = smallest, maxFontSize = style.fontSize, stepSize = 0.5.sp),
            modifier = Modifier.weight(1f, fill = false).testTag(MetronomeTestTags.TRAINER_TEXT),
        )
    }
}

/** The trainer's switch, its own touch target (48 dp), heard as "Speed trainer, switch". */
@Composable
private fun TrainerSwitch(on: Boolean, name: String, onOnChange: (Boolean) -> Unit, tag: String) {
    Box(
        Modifier
            .testTag(tag)
            .heightIn(min = ROW_HEIGHT)
            .toggleable(value = on, role = Role.Switch, onValueChange = onOnChange)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Switch(checked = on, onCheckedChange = null)
    }
}

/** A row's name: one line, set smaller where a language or very large text needs it, never cut; [bold] over the trainer's settings. */
@Composable
private fun RowName(text: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (bold) 16.sp else 15.sp, fontWeight = if (bold) FontWeight.Bold else null)
    val smallest = with(LocalDensity.current) { MIN_NAME_SIZE.toSp() }
    Text(
        text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = smallest, maxFontSize = style.fontSize, stepSize = 0.5.sp),
        modifier = modifier,
    )
}

/**
 * The speed trainer's settings, in a floating panel opened from its row: at the top its name and its
 * switch ([on], [onOnChange]); then the start and goal tempos, each with −
 * and + a [SpeedTrainer.TEMPO_STEP] apart (the goal always at least that far above the start: a − or +
 * that would pass it is dimmed); then the step and the bars, each four choices as the sounds are.
 * Every change calls [onPlan] with the new [plan]. TalkBack hears each tempo with its name ("Start, 80
 * BPM"), − and + by what they do, and the choices as radio groups.
 */
@Composable
internal fun TrainerSettings(
    plan: SpeedTrainer,
    on: Boolean,
    onOnChange: (Boolean) -> Unit,
    onPlan: (SpeedTrainer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(R.string.metronome_trainer)
    Column(modifier.testTag(MetronomeTestTags.TRAINER_SETTINGS)) {
        Row(Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            RowName(name, Modifier.weight(1f).padding(start = ROW_PADDING), bold = true)
            Spacer(Modifier.width(8.dp))
            TrainerSwitch(on, name, onOnChange, MetronomeTestTags.TRAINER_SETTINGS_SWITCH)
            Spacer(Modifier.width(6.dp))
        }
        val step = SpeedTrainer.TEMPO_STEP
        TempoStepper(
            label = stringResource(R.string.metronome_trainer_start),
            bpm = plan.startBpm,
            tag = MetronomeTestTags.TRAINER_START,
            slower = stringResource(R.string.metronome_trainer_start_slower),
            faster = stringResource(R.string.metronome_trainer_start_faster),
            canSlower = SpeedTrainer.lower(plan.startBpm) >= ClickTrack.MIN_BPM,
            canFaster = SpeedTrainer.higher(plan.startBpm) <= plan.goalBpm - step,
            onSlower = { onPlan(plan.copy(startBpm = SpeedTrainer.lower(plan.startBpm).coerceAtLeast(ClickTrack.MIN_BPM))) },
            onFaster = { onPlan(plan.copy(startBpm = SpeedTrainer.higher(plan.startBpm))) },
        )
        TempoStepper(
            label = stringResource(R.string.metronome_trainer_goal),
            bpm = plan.goalBpm,
            tag = MetronomeTestTags.TRAINER_GOAL,
            slower = stringResource(R.string.metronome_trainer_goal_slower),
            faster = stringResource(R.string.metronome_trainer_goal_faster),
            canSlower = SpeedTrainer.lower(plan.goalBpm) >= plan.startBpm + step,
            canFaster = SpeedTrainer.higher(plan.goalBpm) <= ClickTrack.MAX_BPM,
            onSlower = { onPlan(plan.copy(goalBpm = SpeedTrainer.lower(plan.goalBpm))) },
            onFaster = { onPlan(plan.copy(goalBpm = SpeedTrainer.higher(plan.goalBpm))) },
        )
        val locale = appLocale()
        FaceRow(stringResource(R.string.metronome_trainer_step), Modifier.padding(top = 2.dp)) {
            val bpm = stringResource(R.string.metronome_bpm)
            for (option in SpeedTrainer.STEPS) {
                val text = stringResource(R.string.metronome_trainer_plus, localDigits(option, locale))
                TextSegment(
                    text = text,
                    name = "$text $bpm",
                    tag = MetronomeTestTags.trainerStep(option),
                    picked = option == plan.stepBpm,
                    onPick = { onPlan(plan.copy(stepBpm = option)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        FaceRow(stringResource(R.string.metronome_trainer_bars), Modifier.padding(top = 4.dp)) {
            for (option in SpeedTrainer.BAR_CHOICES) {
                val digits = localDigits(option, locale)
                TextSegment(
                    text = digits,
                    name = pluralStringResource(R.plurals.metronome_trainer_bars_value, option, digits),
                    tag = MetronomeTestTags.trainerBars(option),
                    picked = option == plan.bars,
                    onPick = { onPlan(plan.copy(bars = option)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A heading over four choices side by side, a radio group TalkBack hears by the heading. */
@Composable
private fun FaceRow(title: String, modifier: Modifier = Modifier, choices: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(modifier) {
        PanelLabel(title)
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup()
                .semantics { contentDescription = title },
            horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
            content = choices,
        )
    }
}

/**
 * A tempo of the trainer's: its [label] at the start, then − [bpm] + at the end, the number in the
 * style's number face. TalkBack hears the label and number as one ("Start, 80 BPM") and − and + by
 * [slower] and [faster]; one that can't move further is dimmed and does nothing.
 */
@Composable
private fun TempoStepper(
    label: String,
    bpm: Int,
    tag: String,
    slower: String,
    faster: String,
    canSlower: Boolean,
    canFaster: Boolean,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
) {
    val digits = localDigits(bpm, appLocale())
    val bpmLabel = stringResource(R.string.metronome_bpm)
    val spoken = "$label, $digits $bpmLabel"
    Row(Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowName(label, Modifier.weight(1f).padding(start = ROW_PADDING))
        }
        TrainerStepper(faster = false, name = slower, enabled = canSlower, tag = "${tag}_slower", onClick = onSlower)
        val numbers = NumberFont
        val style = TextStyle(fontFamily = numbers, fontSize = 18.sp, fontFeatureSettings = "$LINING_FIGURES, tnum")
        val smallest = with(LocalDensity.current) { MIN_NAME_SIZE.toSp() }
        Text(
            digits,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = smallest, maxFontSize = style.fontSize, stepSize = 0.5.sp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .testTag(tag)
                .widthIn(min = NUMBER_WIDTH, max = NUMBER_WIDTH)
                .semantics { hideFromAccessibility() },
        )
        TrainerStepper(faster = true, name = faster, enabled = canFaster, tag = "${tag}_faster", onClick = onFaster)
    }
}

/** − or + beside a trainer tempo: the shared stepper ([StepperButton], a 36 dp face in a 48 dp touch area). */
@Composable
private fun TrainerStepper(faster: Boolean, name: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    StepperButton(more = faster, label = name, onClick = onClick, modifier = Modifier.testTag(tag), enabled = enabled)
}

/**
 * What the trainer's row says of its [plan]: stopped (or off), from where to where and how it climbs
 * ("80 → 120 · +5 every 4 bars"); playing, the tempo now ([bpm]), the goal and the next step ("95 → 120 ·
 * +5 in 2 bars", [bar] being the bar it plays in, −1 before the first downbeat); at the goal "At the goal ·
 * 120 BPM".
 */
@Composable
internal fun trainerStatus(plan: SpeedTrainer, bpm: Int, bar: Long, playing: Boolean): TrainerStatus {
    val locale = appLocale()
    val now = localDigits(bpm, locale)
    val start = localDigits(plan.startBpm, locale)
    val goal = localDigits(plan.goalBpm, locale)
    return when {
        !playing -> {
            val bars = plan.bars
            val every = pluralStringResource(
                R.plurals.metronome_trainer_every,
                bars,
                stringResource(R.string.metronome_trainer_plus, localDigits(plan.stepBpm, locale)),
                localDigits(bars, locale),
            )
            TrainerStatus(
                stringResource(R.string.metronome_trainer_line, start, goal, every),
                stringResource(R.string.metronome_trainer_spoken_set, start, goal, every),
            )
        }
        bar >= 0L && plan.atGoal(bar) -> TrainerStatus(
            stringResource(R.string.metronome_trainer_at_goal, goal),
            stringResource(R.string.metronome_trainer_spoken_goal, goal),
        )
        else -> {
            val left = plan.barsToNextStep(bar)
            val next = pluralStringResource(
                R.plurals.metronome_trainer_next,
                left,
                stringResource(R.string.metronome_trainer_plus, localDigits(plan.nextStepBpm(bar), locale)),
                localDigits(left, locale),
            )
            TrainerStatus(
                stringResource(R.string.metronome_trainer_line, now, goal, next),
                stringResource(R.string.metronome_trainer_spoken_climbing, now, goal, next),
            )
        }
    }
}

/** The trainer tempo's number between − and +: room for three figures. */
private val NUMBER_WIDTH = 52.dp

/** The trainer's row in the options panel: at least this tall (the mock-up's card's height). */
private val TRAINER_ROW_HEIGHT = 56.dp

/** The trainer line's icon, a little smaller than a row's, and its press light: a pill 8 dp in from the top and bottom of its 48 dp. */
private val LINE_ICON = 20.dp
private val LINE_LIGHT_INSET = 8.dp
