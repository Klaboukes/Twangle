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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.LINING_FIGURES
import io.github.klaboukes.twangle.core.designsystem.StyleButton
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.designsystem.numberFontFor
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.SpeedTrainer
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.metronome.R

/*
 * The speed trainer in the options panel (Phase 62, the owner's pick II of
 * docs/design/metronome-layout-preview.html; its settings as round 1's panel,
 * docs/design/metronome-practice-preview.html): a row under Listen with its switch, and its settings,
 * which open in the panel in place of the rows, with a back arrow.
 */

/**
 * The speed trainer's row: its rising-line icon (in the accent while [on]), its name, its switch and a
 * › to its settings. A tap on the row opens the settings ([onOpen]); the switch turns it on or off
 * ([onOnChange]). TalkBack hears a button "Speed trainer" and a switch of the same name.
 */
@Composable
internal fun TrainerRow(on: Boolean, onOnChange: (Boolean) -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val name = stringResource(R.string.metronome_trainer)
    Row(
        modifier
            .testTag(MetronomeTestTags.TRAINER)
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .pressFeedback(source, MaterialTheme.shapes.medium)
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                source.pressFlash()
                onOpen()
            }
            .padding(start = ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_trainer),
            contentDescription = null,
            tint = if (on) scheme.primary else scheme.onSurface,
            modifier = Modifier.size(ROW_ICON),
        )
        Spacer(Modifier.width(ROW_PADDING))
        RowName(name, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        TrainerSwitch(on, name, onOnChange)
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp).size(20.dp),
        )
    }
}

/** The trainer's switch, its own touch target (48 dp), heard as "Speed trainer, switch". */
@Composable
private fun TrainerSwitch(on: Boolean, name: String, onOnChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .testTag(MetronomeTestTags.TRAINER_SWITCH)
            .heightIn(min = ROW_HEIGHT)
            .toggleable(value = on, role = Role.Switch, onValueChange = onOnChange)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Switch(checked = on, onCheckedChange = null)
    }
}

/** A row's name: one line, set smaller where a language or very large text needs it, never cut. */
@Composable
private fun RowName(text: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
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
 * The speed trainer's settings, in the options panel in place of its rows: at the top a back arrow
 * ([onBack]), its name and its switch ([on], [onOnChange]); then the start and goal tempos, each with −
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(R.string.metronome_trainer)
    Column(modifier.testTag(MetronomeTestTags.TRAINER_SETTINGS)) {
        Row(Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            BackArrow(onBack)
            RowName(name, Modifier.weight(1f).padding(start = 4.dp))
            Spacer(Modifier.width(8.dp))
            TrainerSwitch(on, name, onOnChange)
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
        ChoiceRow(stringResource(R.string.metronome_trainer_step), Modifier.padding(top = 2.dp)) {
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
        ChoiceRow(stringResource(R.string.metronome_trainer_bars), Modifier.padding(top = 4.dp)) {
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

/** ‹ back to the panel's rows: a 48 dp touch area, heard as "Back to the options". */
@Composable
private fun BackArrow(onBack: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val name = stringResource(R.string.metronome_trainer_back)
    Box(
        Modifier
            .testTag(MetronomeTestTags.TRAINER_BACK)
            .size(ROW_HEIGHT)
            .pressFeedback(source, MaterialTheme.shapes.medium)
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                source.pressFlash()
                onBack()
            }
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(ROW_ICON))
    }
}

/** A heading over four choices side by side, a radio group TalkBack hears by the heading. */
@Composable
private fun ChoiceRow(title: String, modifier: Modifier = Modifier, choices: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(modifier) {
        PanelHeading(title)
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
        StepperButton(faster = false, name = slower, enabled = canSlower, tag = "${tag}_slower", onClick = onSlower)
        val numbers = numberFontFor(MaterialTheme.styleTokens.style)
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
        StepperButton(faster = true, name = faster, enabled = canFaster, tag = "${tag}_faster", onClick = onFaster)
    }
}

/** − or + beside a trainer tempo: a [STEPPER_FACE] face in the style's button shape, a 48 dp touch area. */
@Composable
private fun StepperButton(faster: Boolean, name: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    StyleButton(onClick = onClick, label = name, modifier = Modifier.testTag(tag), face = STEPPER_FACE, enabled = enabled) {
        Icon(painterResource(if (faster) R.drawable.ic_add else R.drawable.ic_remove), contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

/**
 * What the status line says of the speed trainer [plan] while it is on: stopped, from where to where
 * and how it climbs ("80 → 120 · +5 every 4 bars"); playing, the tempo now ([bpm], the dial's), the goal
 * and the next step ("95 → 120 · +5 in 2 bars", [bar] being the bar it plays in, −1 before the first
 * downbeat); at the goal "At the goal · 120 BPM".
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

/** − and +'s face beside a trainer tempo, as the mock-up's 36 dp. */
private val STEPPER_FACE = 36.dp

/** The trainer tempo's number between − and +: room for three figures. */
private val NUMBER_WIDTH = 52.dp
