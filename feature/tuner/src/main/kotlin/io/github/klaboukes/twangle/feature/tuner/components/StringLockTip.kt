package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.feature.tuner.R
import kotlin.math.min

/** Which way the [StringLockTip]'s pointer points: at the string row below it, or above it. */
enum class TipPointer {
    /** The balloon sits above the strings, beside the tuning picker; the pointer, near its start, reaches down at the strings. */
    DOWN,

    /** The balloon sits under the string row; the pointer is in the middle, under the strings. */
    UP,
}

/**
 * The one-time tip "Tap a string to lock it", as a speech balloon with a small
 * [pointer] at the string row. It floats over the screen (the tuner places it without giving it
 * room of its own, see `GuitarControls`), so it never moves anything or hides the tuning picker.
 * The balloon has the style's own corner shape (`shapes.medium`) and accent container colours, in
 * light and dark. The whole balloon is one button that dismisses it, so TalkBack reads the tip and
 * offers "Dismiss tip". Pointing up it shows a close icon; pointing down it sits in a small corner
 * beside the tuning picker, where the text needs all the width (tapping it, or a string, still
 * closes it). Its text grows with the system font up to [MAX_TIP_FONT_SCALE], so the balloon stays
 * small enough to float beside the controls without covering the readout.
 */
@Composable
fun StringLockTip(onDismiss: () -> Unit, pointer: TipPointer, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = BalloonShape(MaterialTheme.shapes.medium, pointer, POINTER_WIDTH, POINTER_HEIGHT)
    val density = LocalDensity.current
    val closeIcon = pointer == TipPointer.UP
    Row(
        modifier = modifier
            .clip(shape)
            .clickable(onClickLabel = stringResource(R.string.tuner_string_lock_tip_dismiss), role = Role.Button, onClick = onDismiss)
            .background(colors.primaryContainer, shape)
            .border(1.dp, colors.primary, shape)
            .padding(
                start = if (closeIcon) 14.dp else 10.dp,
                end = 10.dp,
                top = BODY_PADDING + if (pointer == TipPointer.UP) POINTER_HEIGHT else 0.dp,
                bottom = BODY_PADDING + if (pointer == TipPointer.DOWN) POINTER_HEIGHT else 0.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompositionLocalProvider(LocalDensity provides Density(density.density, min(density.fontScale, MAX_TIP_FONT_SCALE))) {
            Text(
                text = stringResource(R.string.tuner_string_lock_tip),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        // The click label already says what tapping does, so TalkBack doesn't need the icon.
        if (closeIcon) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * A speech balloon: [body] (the style's corner shape) with a small triangular pointer of
 * [pointerWidth] × [pointerHeight] on the side [pointer] says. A pointer down sits near the start
 * ([DOWN_POINTER_FROM_START]), so it passes beside the centred tuning picker; a pointer up sits in
 * the middle.
 */
private data class BalloonShape(
    private val body: Shape,
    private val pointer: TipPointer,
    private val pointerWidth: Dp,
    private val pointerHeight: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val pointerH = with(density) { pointerHeight.toPx() }
        val halfPointer = with(density) { pointerWidth.toPx() } / 2f
        val bodyTop = if (pointer == TipPointer.UP) pointerH else 0f
        val bodyBottom = bodyTop + size.height - pointerH
        val bodyPath = Path().apply {
            addOutline(body.createOutline(Size(size.width, size.height - pointerH), layoutDirection, density))
            translate(Offset(0f, bodyTop))
        }
        val corner = halfPointer * 2f
        val wanted = if (pointer == TipPointer.DOWN) with(density) { DOWN_POINTER_FROM_START.toPx() } else size.width / 2f
        val fromStart = wanted.coerceIn(corner, (size.width - corner).coerceAtLeast(corner))
        val x = if (layoutDirection == LayoutDirection.Ltr) fromStart else size.width - fromStart
        val pointerPath = Path().apply {
            if (pointer == TipPointer.DOWN) {
                moveTo(x - halfPointer, bodyBottom - 1f)
                lineTo(x + halfPointer, bodyBottom - 1f)
                lineTo(x, size.height)
            } else {
                moveTo(x - halfPointer, bodyTop + 1f)
                lineTo(x + halfPointer, bodyTop + 1f)
                lineTo(x, 0f)
            }
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, bodyPath, pointerPath))
    }
}

/** How far the tip's text grows with the system font: enough to read, small enough to float in the gap. */
private const val MAX_TIP_FONT_SCALE = 1.3f

/** Where along the balloon a downward pointer sits, from the start: beside the centred picker, so the tuner can aim it at a string. */
internal val DOWN_POINTER_FROM_START = 32.dp

private val POINTER_WIDTH = 14.dp

/** The pointer's height; part of the balloon's measured height. */
internal val POINTER_HEIGHT = 8.dp
private val BODY_PADDING = 6.dp

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun StringLockTipPreview() {
    TwangleTheme(style = VisualStyle.FUTURISTIC, darkTheme = true) { StringLockTip(onDismiss = {}, pointer = TipPointer.DOWN) }
}
