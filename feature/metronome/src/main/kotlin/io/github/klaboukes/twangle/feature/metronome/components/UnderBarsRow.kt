package io.github.klaboukes.twangle.feature.metronome.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.StyleButton
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_FACE
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_ICON
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_OUTLINE_INSET
import io.github.klaboukes.twangle.feature.metronome.ListenPhase
import io.github.klaboukes.twangle.feature.metronome.ListenUi
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.metronome.R

/*
 * The row under the beat bars (2026-11-04, the owner: Listen was tucked away at the end of the options
 * panel): Vibrate at the start, Listen at the end, round buttons on the gear's face, and between them
 * the line: Listen's words ([ListenLine]) or, while the speed trainer is on and Listen has nothing to
 * say, the trainer's ([TrainerLine]). Until then Vibrate and Listen were rows in the options panel, the
 * line showed small icons of what was on there, and the trainer had a card of its own.
 */

/**
 * Vibrate ([vibrate], [onVibrateChange]) at the start, Listen ([listen], [onListen]) at the end and the
 * line between them: Listen's words ([onTryAgain], [onOpenSettings]) while it listens or has something to
 * say (a found tempo too, whose balloon says it), otherwise the speed trainer's while it is on ([trainer],
 * null while off; a tap opens its settings, [onTrainerOpen], from [onTrainerPlaced]'s place). They
 * crossfade as Listen's words do. The buttons' outlines line up with the top row's gear and ⋯ over them,
 * and the row is their 48 dp touch area tall at any font size, so nothing moves when the line changes.
 * [onListenPlaced] gives the Listen button's face, for the found tempo's balloon to point at.
 */
@Composable
internal fun UnderBarsRow(
    vibrate: Boolean,
    onVibrateChange: (Boolean) -> Unit,
    listen: ListenUi,
    onListen: () -> Unit,
    onTryAgain: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onListenPlaced: (Rect) -> Unit = {},
    trainer: TrainerStatus? = null,
    onTrainerOpen: () -> Unit = {},
    onTrainerPlaced: (Rect) -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(WORDS_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VibrateButton(vibrate, onVibrateChange, Modifier.offset(x = -TOP_BAR_OUTLINE_INSET))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ListenLine(listen, onTryAgain = onTryAgain, onOpenSettings = onOpenSettings)
            TrainerOnTheLine(trainer, shown = trainer != null && listen.phase == ListenPhase.IDLE, onTrainerOpen, onTrainerPlaced)
        }
        ListenButton(listen.listening, onListen, onListenPlaced, Modifier.offset(x = TOP_BAR_OUTLINE_INSET))
    }
}

/** The trainer's words ([trainer]) over Listen's empty line while [shown], crossfading as Listen's words do; the last stay drawn while they fade out. */
@Composable
private fun TrainerOnTheLine(trainer: TrainerStatus?, shown: Boolean, onOpen: () -> Unit, onPlaced: (Rect) -> Unit) {
    var last by remember { mutableStateOf(trainer) }
    if (trainer != null) last = trainer
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(LINE_FADE_MILLIS, delayMillis = LINE_FADE_MILLIS)),
        exit = fadeOut(tween(LINE_FADE_MILLIS)),
    ) {
        last?.let { TrainerLine(it, onOpen, onPlaced = onPlaced) }
    }
}

/**
 * Vibrate on the beat: the vibrating phone, filled in the accent while [on], outlined while off; a tap
 * toggles it ([onChange]). TalkBack hears a switch "Vibrate on the beat", on or off.
 */
@Composable
private fun VibrateButton(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    StyleButton(
        onClick = { onChange(!on) },
        label = stringResource(R.string.metronome_vibrate),
        modifier = modifier,
        face = TOP_BAR_FACE,
        main = on,
        role = Role.Switch,
        faceModifier = Modifier
            .testTag(MetronomeTestTags.VIBRATE)
            .semantics { toggleableState = ToggleableState(on) },
    ) {
        Icon(painterResource(R.drawable.ic_vibrate), contentDescription = null, modifier = Modifier.size(TOP_BAR_ICON))
    }
}

/**
 * Listen: the microphone; while [listening] the stop square in the accent, and a tap ([onClick]) stops
 * it. TalkBack hears "Listen" or "Stop listening". [onPlaced] gives its face's place.
 */
@Composable
private fun ListenButton(listening: Boolean, onClick: () -> Unit, onPlaced: (Rect) -> Unit, modifier: Modifier = Modifier) {
    StyleButton(
        onClick = onClick,
        label = stringResource(if (listening) R.string.metronome_listen_stop_spoken else R.string.metronome_listen),
        modifier = modifier,
        face = TOP_BAR_FACE,
        faceModifier = Modifier
            .testTag(MetronomeTestTags.LISTEN)
            .onGloballyPositioned { onPlaced(it.boundsInRoot()) },
    ) {
        Icon(
            painterResource(if (listening) R.drawable.ic_listen_stop else R.drawable.ic_listen),
            contentDescription = null,
            tint = if (listening) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            modifier = Modifier.size(TOP_BAR_ICON),
        )
    }
}

/** Between the line's words and each button's touch area. */
private val WORDS_GAP = 4.dp
