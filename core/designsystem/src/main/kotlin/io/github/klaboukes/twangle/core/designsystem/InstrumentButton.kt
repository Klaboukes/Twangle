package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.settings.VisualStyle

/**
 * The instrument and tuning button: a headstock ([INSTRUMENT_ICON], no word) in the style's button
 * outline and shape, see-through, like the tuner's "more tools" button and strum check switch, so the
 * row above the strings has a button of the same kind at each end. It sits at the start of that row
 * and at the Practice tools' tuning line; a tap ([onClick]) opens the instrument and tuning panel.
 * A long-press shows "Instrument" in a tooltip. TalkBack: "Instrument, Guitar, Standard ([current]),
 * button, double-tap to change instrument or tuning". [faceModifier] goes on the outline, not the
 * 48 dp touch target round it (the tour lights up the outline), and on the node TalkBack hears (a test tag).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentButton(
    current: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    faceModifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.styleTokens.style
    val neon = style == VisualStyle.FUTURISTIC
    val shape = pickerButtonShape(style, MaterialTheme.shapes.small)
    val label = stringResource(R.string.instrument_button)
    val action = stringResource(R.string.instrument_button_action)
    val outline = if (neon) scheme.primary.copy(alpha = NEON_OUTLINE_ALPHA) else scheme.onSurfaceVariant.copy(alpha = OUTLINE_ALPHA)
    val fill = if (neon) neonFill(scheme) else scheme.surface.copy(alpha = BACKING_ALPHA)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        val press = remember { MutableInteractionSource() }
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .size(INSTRUMENT_BUTTON_SIZE)
                .then(faceModifier)
                .pressSink(press)
                .clip(shape)
                .background(fill, shape)
                .border(1.dp, outline, shape)
                .clickable(press, PressIndication(shape, face = true), onClickLabel = action, role = Role.Button) {
                    press.pressFlash()
                    onClick()
                }
                .clearAndSetSemantics {
                    contentDescription = label
                    stateDescription = current
                    role = Role.Button
                    onClick(label = action) {
                        onClick()
                        true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(INSTRUMENT_ICON, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(INSTRUMENT_ICON_SIZE))
        }
    }
}

/** The button's outline: 44 dp like the style's other buttons above the strings, in a 48 dp touch target. */
val INSTRUMENT_BUTTON_SIZE = 44.dp

/**
 * A guitar headstock with three tuning pegs at each side, in 1.8 dp round-ended lines on a 24 dp
 * grid, drawn like the navigation bar's tab icons (it was the bar's Instrument item until 2026-10-19).
 */
val INSTRUMENT_ICON: ImageVector = ImageVector.Builder(
    name = "instrument",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    listOf(
        // The headstock, narrowing into the neck at the bottom.
        "M10.5,21.5V15.5L8.5,13.5V4a1.5,1.5 0,0 1,1.5,-1.5h4a1.5,1.5 0,0 1,1.5,1.5v9.5L13.5,15.5v6",
        // The pegs.
        "M5,5.5h3.5", "M5,9h3.5", "M5,12.5h3.5",
        "M15.5,5.5H19", "M15.5,9H19", "M15.5,12.5H19",
    ).forEach { path ->
        addPath(
            pathData = addPathNodes(path),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()

private val INSTRUMENT_ICON_SIZE = 24.dp
private const val OUTLINE_ALPHA = 0.6f
private const val NEON_OUTLINE_ALPHA = 0.55f
private const val BACKING_ALPHA = 0.45f
