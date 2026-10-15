package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * Material's buttons with Twangle's press effect ([pressFeedback]) in place of the ripple that
 * TwangleTheme turns off: the same look, layout, touch area and TalkBack as Material's own, and the
 * light and sink of every other button of the app. Material sets its 48 dp touch area outside the
 * modifier it is given, so the light would cover the touch area rather than the button; these
 * buttons switch Material's off and set the same touch area round the light themselves.
 */

/** Material's [TextButton] with the press effect, the light under its text. */
@Composable
fun TwangleTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    tone: PressTone = PressTone.PLAIN,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    OwnTouchArea { touch ->
        TextButton(
            onClick = {
                source.pressFlash()
                onClick()
            },
            modifier = modifier.touchArea(touch).pressFeedback(source, shape, tone = tone),
            enabled = enabled,
            shape = shape,
            colors = colors,
            contentPadding = contentPadding,
            interactionSource = source,
        ) { WithTouchArea(touch) { content() } }
    }
}

/**
 * Material's filled [Button] with the press effect. Material draws its filled face as content, so the
 * light lies over it ([pressFeedback]'s `above`) in [tone] (the on-accent colour for an accent fill).
 */
@Composable
fun TwangleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    tone: PressTone = PressTone.ACCENT_FILL,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    OwnTouchArea { touch ->
        Button(
            onClick = {
                source.pressFlash()
                onClick()
            },
            modifier = modifier.touchArea(touch).pressFeedback(source, shape, tone = tone, above = true),
            enabled = enabled,
            shape = shape,
            colors = colors,
            border = border,
            contentPadding = contentPadding,
            interactionSource = source,
        ) { WithTouchArea(touch) { content() } }
    }
}

/** Material's [IconButton] (a 40 dp round button in a 48 dp touch area) with the press effect. */
@Composable
fun TwangleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    OwnTouchArea { touch ->
        IconButton(
            onClick = {
                source.pressFlash()
                onClick()
            },
            modifier = modifier.touchArea(touch).pressFeedback(source, CircleShape),
            enabled = enabled,
            colors = colors,
            interactionSource = source,
        ) { WithTouchArea(touch, content) }
    }
}

/** Material's [FilledTonalIconButton] with the press effect, the light over its filled face. */
@Composable
fun TwangleFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    OwnTouchArea { touch ->
        FilledTonalIconButton(
            onClick = {
                source.pressFlash()
                onClick()
            },
            modifier = modifier.touchArea(touch).pressFeedback(source, shape, above = true),
            enabled = enabled,
            shape = shape,
            interactionSource = source,
        ) { WithTouchArea(touch, content) }
    }
}

/** Material's [DropdownMenuItem] with the row's press light ([PressIndication.Row]). */
@Composable
fun TwangleMenuItem(text: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    DropdownMenuItem(
        text = text,
        onClick = {
            source.pressFlash()
            onClick()
        },
        modifier = modifier.indication(source, PressIndication.Row),
        interactionSource = source,
    )
}

/**
 * Runs [content] with Material's own touch area off, handing it the size that was asked for
 * (48 dp unless a test or a screen turned it off), for [touchArea] to set round the light instead.
 */
@Composable
fun OwnTouchArea(content: @Composable (touch: Dp) -> Unit) {
    val touch = LocalMinimumInteractiveComponentSize.current
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) { content(touch) }
}

/** Gives a button's own content Material's touch area back (anything interactive inside it). */
@Composable
private fun WithTouchArea(touch: Dp, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides touch, content = content)
}

/**
 * At least [side] each way, the button centred in it, as Material's `minimumInteractiveComponentSize`:
 * the space a small button takes, and Compose widens its touches to it.
 */
fun Modifier.touchArea(side: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val min = if (side.value.isNaN()) 0 else side.roundToPx()
    val (width, height) = touchAreaSize(placeable.width, placeable.height, min)
    layout(width, height) {
        placeable.place(((width - placeable.width) / 2f).roundToInt(), ((height - placeable.height) / 2f).roundToInt())
    }
}

/** A button of [width] × [height] px in a touch area at least [min] px each way. Pure, for tests. */
internal fun touchAreaSize(width: Int, height: Int, min: Int): Pair<Int, Int> = maxOf(width, min) to maxOf(height, min)
