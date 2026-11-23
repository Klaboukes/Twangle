package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * An icon button's name in a tooltip on a long press (the tuner's focus mode and strum check buttons, the 12th-fret
 * check's ✕): [content] (the button, which TalkBack already hears by that name) with Material's plain tooltip
 * saying [label], [above] the button or under it. Material's own long press gave the box round the button a node of
 * its own for TalkBack, the button's size with nothing to say (a stop a screen reader may call "Unlabeled", Phase 94);
 * here the long press is watched without one, as Material watches it: held past the long-press time it shows the
 * tooltip, and the press is the tooltip's, not a tap on the button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NameTooltip(label: String, above: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(if (above) TooltipAnchorPosition.Above else TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = state,
        modifier = modifier,
        // Its own long press and its node for TalkBack off: the long press is watched below.
        enableUserInput = false,
    ) {
        Box(Modifier.showOnLongPress(state, scope)) { content() }
    }
}

/** Shows [state]'s tooltip when a touch is held past the long-press time, as Material's tooltip does, with no semantics. */
@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.showOnLongPress(state: TooltipState, scope: CoroutineScope): Modifier = pointerInput(state) {
    awaitEachGesture {
        val pass = PointerEventPass.Initial
        val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
        if (down.type != PointerType.Touch && down.type != PointerType.Stylus) return@awaitEachGesture
        try {
            withTimeout(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation(pass = pass) }
        } catch (_: PointerEventTimeoutCancellationException) {
            scope.launch { state.show(MutatePriority.UserInput) }
            // The press was the tooltip's: the button under it doesn't take it as a tap.
            awaitPointerEvent(pass = pass).changes.forEach { it.consume() }
        }
    }
}
