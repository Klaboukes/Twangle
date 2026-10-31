package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Twangle's separate faces for picking one of a small set of short values (Phase 63,
 * docs/architecture/ui.md *Shared controls*): the metronome's sound and "Between the beats", the speed
 * trainer's step and bars, Ear training's notes in a row. Side by side in a row, each on the button's
 * see-through face ([buttonLook]), the picked one in the container colour with the 2 dp accent outline.
 * TalkBack hears each as a radio button; put them in a `selectableGroup()`.
 */

/**
 * One value of a row of faces, holding [face] (an icon or a short text) on a [CHOICE_FACE_HEIGHT]
 * face in a touch area at least 48 dp tall, a radio button TalkBack hears by [name]. Not [enabled]:
 * dimmed in place, untouchable. Give it its width ([Modifier.weight] in a row).
 */
@Composable
fun ChoiceFace(
    name: String,
    picked: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    face: @Composable () -> Unit,
) {
    val look = buttonLook()
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = CHOICE_ROW_HEIGHT)
            .selectable(
                selected = picked,
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
            ) {
                source.pressFlash()
                onPick()
            }
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(CHOICE_FACE_HEIGHT)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .pressFeedback(source, look.shape, fill = look.fill(picked), outline = look.outline(picked)),
            contentAlignment = Alignment.Center,
        ) {
            face()
        }
    }
}

/**
 * A [ChoiceFace] showing a short [text] ("+5", "4"), set smaller where it doesn't fit (very large
 * text), never cut. TalkBack hears [name].
 */
@Composable
fun TextChoiceFace(text: String, name: String, picked: Boolean, onPick: () -> Unit, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)
    // In dp, so it holds at any font size.
    val smallest = with(LocalDensity.current) { MIN_TEXT_SIZE.toSp() }
    ChoiceFace(name, picked, onPick, modifier) {
        Text(
            text,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            // Never wrapped, so the auto-size sees a text too wide.
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = smallest, maxFontSize = style.fontSize, stepSize = 0.5.sp),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** A [ChoiceFace]'s face height, in its 48 dp touch area. */
val CHOICE_FACE_HEIGHT = 40.dp

/** [ChoiceFace]s in a row stand this far apart. */
val CHOICE_FACE_GAP = 4.dp

private val MIN_TEXT_SIZE = 8.dp
private const val DISABLED_ALPHA = 0.38f
