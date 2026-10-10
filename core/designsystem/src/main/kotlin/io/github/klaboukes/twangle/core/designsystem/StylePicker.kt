package io.github.klaboukes.twangle.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.dial.dialAngle
import io.github.klaboukes.twangle.core.designsystem.dial.dialPivot
import io.github.klaboukes.twangle.core.designsystem.dial.drawDialNeedle
import io.github.klaboukes.twangle.core.designsystem.dial.drawDialScale
import io.github.klaboukes.twangle.core.settings.VisualStyle

/**
 * A row of style cards to choose the app's look from. Each card is a small live preview drawn in
 * its own style (background, dial and lettering) in light or [darkTheme], with the style's name and
 * a one-line description under it. The [selected] card has a ring and a check mark; tapping a card
 * calls [onSelect]. The row scrolls sideways and starts with the selected card in view.
 */
@Composable
fun StylePicker(
    selected: VisualStyle,
    onSelect: (VisualStyle) -> Unit,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        // Bring the current style into view once, without fighting the user's own scrolling later.
        listState.scrollToItem((VisualStyle.entries.indexOf(selected) - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        modifier = modifier.selectableGroup(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(VisualStyle.entries, key = { it.name }) { style ->
            StyleCard(style, style == selected, darkTheme, onClick = { onSelect(style) })
        }
    }
}

/** The style's name for users, e.g. "Middle Ages". */
@Composable
fun styleName(style: VisualStyle): String = stringResource(styleNameRes(style))

/** Test tag of [style]'s card. */
fun styleCardTag(style: VisualStyle): String = "style_card_" + style.name.lowercase()

@Composable
private fun StyleCard(style: VisualStyle, selected: Boolean, darkTheme: Boolean, onClick: () -> Unit) {
    val name = styleName(style)
    val tagline = stringResource(styleTaglineRes(style))
    val description = stringResource(R.string.style_card_description, name, tagline)
    val ring by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "styleRing",
    )
    val ringWidth by animateDpAsState(if (selected) 3.dp else 1.dp, label = "styleRingWidth")
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .width(CARD_WIDTH)
            .clip(shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag(styleCardTag(style))
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CARD_PREVIEW_HEIGHT)
                .clip(shape)
                .border(ringWidth, ring, shape),
        ) {
            // The preview is drawn in the card's own style, not the app's current one.
            TwangleTheme(style = style, darkTheme = darkTheme, animate = false) {
                StylePreview()
            }
            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.padding(3.dp))
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tagline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A miniature tuner in the current style: background, dial with the needle a little flat, and a note. */
@Composable
private fun StylePreview() {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        StyleBackdropPreview(Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            StaticDial(cents = PREVIEW_CENTS, modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.style_preview_note),
                style = MaterialTheme.typography.displayMedium,
                color = colors.onSurface,
                maxLines = 1,
            )
        }
    }
}

/** The style's dial with the needle resting at [cents], for previews. */
@Composable
fun StaticDial(cents: Float, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.styleTokens
    val background = MaterialTheme.colorScheme.background
    Canvas(modifier.aspectRatio(2f)) {
        drawDialScale(tokens.style, tokens.dial, tokens.dial.centre, background)
        rotate(dialAngle(cents) * (180f / Math.PI.toFloat()), dialPivot()) {
            drawDialNeedle(tokens.style, tokens.dial, tokens.dial.needle, background)
        }
    }
}

private val CARD_WIDTH = 132.dp
private val CARD_PREVIEW_HEIGHT = 168.dp

/** Where the preview needle rests: a little flat, so the needle's shape shows. */
private const val PREVIEW_CENTS = -14f

@StringRes
private fun styleNameRes(style: VisualStyle): Int = when (style) {
    VisualStyle.PREHISTORIC -> R.string.style_prehistoric
    VisualStyle.MIDDLE_AGES -> R.string.style_middle_ages
    VisualStyle.CLASSIC -> R.string.style_classic
    VisualStyle.RETRO -> R.string.style_retro
    VisualStyle.CONTEMPORARY -> R.string.style_contemporary
    VisualStyle.FUTURISTIC -> R.string.style_futuristic
    VisualStyle.SPACE_AGE -> R.string.style_space_age
}

@StringRes
private fun styleTaglineRes(style: VisualStyle): Int = when (style) {
    VisualStyle.PREHISTORIC -> R.string.style_prehistoric_tagline
    VisualStyle.MIDDLE_AGES -> R.string.style_middle_ages_tagline
    VisualStyle.CLASSIC -> R.string.style_classic_tagline
    VisualStyle.RETRO -> R.string.style_retro_tagline
    VisualStyle.CONTEMPORARY -> R.string.style_contemporary_tagline
    VisualStyle.FUTURISTIC -> R.string.style_futuristic_tagline
    VisualStyle.SPACE_AGE -> R.string.style_space_age_tagline
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun StylePickerPreview() {
    TwangleTheme(style = VisualStyle.CLASSIC, darkTheme = false) {
        StylePicker(VisualStyle.CLASSIC, onSelect = {}, darkTheme = false)
    }
}
