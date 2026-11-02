package io.github.klaboukes.twangle.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.app.R
import io.github.klaboukes.twangle.core.designsystem.NAV_MARK_HEIGHT
import io.github.klaboukes.twangle.core.designsystem.NAV_MARK_WIDTH
import io.github.klaboukes.twangle.core.designsystem.NavMark
import io.github.klaboukes.twangle.core.designsystem.PressIndication
import io.github.klaboukes.twangle.feature.tuner.tour.tourControl
import kotlin.math.min

/**
 * The app's frame: [content] with the navigation bar (Tuner · Metronome · Settings) at the
 * bottom, or, in a window wider than tall (a phone on its side, [useRail]), the rail along the start
 * edge. Both are variant B of the approved mock-up (docs/design/metronome-nav-preview.html): icons and
 * names straight on the app's background over a calm field, the open tab ([selected]) in the accent
 * with the accent's mark under its name. A tap on another tab calls [onSelect], and one on the open
 * tab [onReselect] (the Tuner tab's closes the tuner's tools). Every item is a tab: the instrument
 * and tuning are picked with the headstock button above the tuner's strings and in the Practice tools
 * (the bar's Instrument item until 2026-10-19). Without a [selected] tab (the tuning editor) the bar or
 * rail is away and [content] takes the whole window, as it does with [navigationHidden] (focus mode's full-screen tuner). The bar or rail keeps clear of the system bars; [content] is told those
 * insets are taken, so it doesn't leave room for them twice. With [practice] (the Practice tab's
 * switch on, Phase 32) the Metronome tab is called Practice.
 */
@Composable
fun AppNavigationFrame(
    selected: Destination?,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    onReselect: (Destination) -> Unit = {},
    practice: Boolean = false,
    navigationHidden: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shown = selected != null && !navigationHidden
    // The tab shown while the bar fades out after its screen has gone.
    val lastSelected = remember { arrayOf(Destination.TUNER) }
    if (selected != null) lastSelected[0] = selected
    val tab = selected ?: lastSelected[0]
    BoxWithConstraints(modifier.fillMaxSize()) {
        val rail = useRail(maxWidth.value, maxHeight.value)
        val navigation = @Composable {
            if (rail) {
                AnimatedVisibility(
                    visible = shown,
                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
                ) {
                    AppNavigationRail(tab, onSelect, practice = practice, onReselect = onReselect)
                }
            } else {
                AnimatedVisibility(
                    visible = shown,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    AppNavigationBar(tab, onSelect, practice = practice, onReselect = onReselect)
                }
            }
        }
        val taken = when {
            !shown -> Modifier
            rail -> Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
            else -> Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        }
        // [content] sits at the same place in the composition upright and on its side: rememberSaveable
        // keys follow that place, so a different place (a Row for the rail, a Column for the bar) lost
        // every screen's saved state on turning the phone, and brought back the state from the last time
        // in the other orientation on turning back.
        val body = @Composable { Box(taken) { content() } }
        Layout(contents = listOf(navigation, body), modifier = Modifier.fillMaxSize()) { (navs, bodies), constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val navPlaceables = navs.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
            val navWidth = navPlaceables.maxOfOrNull { it.width } ?: 0
            val navHeight = navPlaceables.maxOfOrNull { it.height } ?: 0
            val bodyConstraints = if (rail) {
                Constraints.fixed((width - navWidth).coerceAtLeast(0), height)
            } else {
                Constraints.fixed(width, (height - navHeight).coerceAtLeast(0))
            }
            val bodyPlaceables = bodies.map { it.measure(bodyConstraints) }
            layout(width, height) {
                // Relative placement mirrors in right-to-left languages, as a Row puts the rail at the start.
                if (rail) {
                    navPlaceables.forEach { it.placeRelative(0, 0) }
                    bodyPlaceables.forEach { it.placeRelative(navWidth, 0) }
                } else {
                    bodyPlaceables.forEach { it.placeRelative(0, 0) }
                    navPlaceables.forEach { it.placeRelative(0, height - it.height) }
                }
            }
        }
    }
}

/**
 * The navigation bar at the bottom of an upright phone: the three tabs, three equal items over the gesture bar, on a calm field (a soft wash of the background
 * colour that starts [FIELD_REACH] above the bar, no edge) that keeps them readable over the style's
 * motifs and the screen's last rows.
 */
@Composable
fun AppNavigationBar(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    practice: Boolean = false,
    onReselect: (Destination) -> Unit = {},
) {
    val field = MaterialTheme.colorScheme.background
    Row(
        modifier
            .fillMaxWidth()
            .testTag(NavigationTestTags.BAR)
            .drawBehind {
                val reach = FIELD_REACH.toPx()
                val total = size.height + reach
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        FIELD_FULL_AT to field.copy(alpha = FIELD_ALPHA),
                        1f to field.copy(alpha = FIELD_ALPHA),
                        startY = -reach,
                        endY = size.height,
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, -reach),
                    size = androidx.compose.ui.geometry.Size(size.width, total),
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(top = BAR_TOP_PADDING, bottom = BAR_BOTTOM_PADDING)
            .selectableGroup(),
        verticalAlignment = Alignment.Top,
    ) {
        for (destination in Destination.entries) {
            NavItem(destination, destination == selected, { onSelect(destination) }, { onReselect(destination) }, practice, Modifier.weight(1f))
        }
    }
}

/**
 * The rail along the start edge of a phone on its side: the same three tabs, one above the other in
 * the middle of the height, on a calm field that fades out [FIELD_REACH] past the rail's end edge.
 */
@Composable
fun AppNavigationRail(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    practice: Boolean = false,
    onReselect: (Destination) -> Unit = {},
) {
    val field = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxHeight()
            .testTag(NavigationTestTags.RAIL)
            .drawBehind {
                val reach = FIELD_REACH.toPx()
                val total = size.width + reach
                val rtl = layoutDirection == LayoutDirection.Rtl
                // Full from the start edge to halfway, then fading out past the end edge.
                val stops = arrayOf(0f to field.copy(alpha = FIELD_ALPHA), 0.5f to field.copy(alpha = FIELD_ALPHA), 1f to Color.Transparent)
                drawRect(
                    if (rtl) {
                        Brush.horizontalGradient(*stops, startX = size.width, endX = -reach)
                    } else {
                        Brush.horizontalGradient(*stops, startX = 0f, endX = total)
                    },
                    topLeft = androidx.compose.ui.geometry.Offset(if (rtl) -reach else 0f, 0f),
                    size = androidx.compose.ui.geometry.Size(total, size.height),
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
            .width(RAIL_WIDTH),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // Only with a very large font on a short window would the tabs not fit; then they scroll.
            Modifier.verticalScroll(rememberScrollState()).padding(vertical = RAIL_GAP).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(RAIL_GAP),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (destination in Destination.entries) {
                NavItem(destination, destination == selected, { onSelect(destination) }, { onReselect(destination) }, practice, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * One tab: its icon, its name under it and, while it is the open one, the accent's mark under
 * that (the room for the mark is kept either way, so nothing moves). The open tab is in the accent,
 * the others in the muted colour. TalkBack hears its name, "tab" and whether it is selected, like a
 * Material 3 navigation bar item; the icon says nothing more. With [practice] the Metronome tab is
 * called Practice (it keeps the metronome's icon: the metronome is still what it holds first).
 */
@Composable
private fun NavItem(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
    onReselect: () -> Unit,
    practice: Boolean,
    modifier: Modifier = Modifier,
) {
    NavEntry(
        icon = navIcon(destination, selected),
        label = stringResource(destination.label(practice)),
        selected = selected,
        modifier = modifier
            .testTag(NavigationTestTags.tab(destination))
            // No tour step of their own: the tour's balloons keep clear of the tabs.
            .tourControl()
            .selectable(
                selected = selected,
                onClick = { if (selected) onReselect() else onClick() },
                role = Role.Tab,
                interactionSource = null,
                indication = PressIndication(MaterialTheme.shapes.medium),
            ),
    )
}

/**
 * What every tab of the bar or rail looks like: its [icon], its [label] under it and, while
 * [selected], the accent's mark under that (the room for the mark is kept either way, so nothing
 * moves); in the accent while selected, else in the muted colour.
 */
@Composable
private fun NavEntry(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val color by animateColorAsState(if (selected) colors.primary else colors.outline, label = "navItem")
    Column(
        modifier
            .heightIn(min = MIN_TOUCH)
            .padding(horizontal = ITEM_SIDE_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(ICON_BOX), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(ICON_SIZE))
        }
        Spacer(Modifier.height(ICON_LABEL_GAP))
        NavLabel(label, color)
        Spacer(Modifier.height(LABEL_MARK_GAP))
        Box(Modifier.size(NAV_MARK_WIDTH, NAV_MARK_HEIGHT)) {
            if (selected) NavMark(colors.primary)
        }
    }
}

/**
 * A tab's name on one line: it grows with the font up to [MAX_LABEL_FONT_SCALE] and, where it would not
 * fit its tab (Dutch "Instellingen" in a wide face, German "Einstellungen"), is set smaller just enough.
 */
@Composable
private fun NavLabel(text: String, color: Color) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, min(density.fontScale, MAX_LABEL_FONT_SCALE))) {
        // The letter spacing relative to the size, so a name set smaller is set tighter too.
        val style = MaterialTheme.typography.labelMedium.let { base ->
            if (base.letterSpacing.isSp && base.fontSize.isSp) base.copy(letterSpacing = (base.letterSpacing.value / base.fontSize.value).em) else base
        }
        Text(
            text,
            color = color,
            style = style,
            maxLines = 1,
            textAlign = TextAlign.Center,
            // Never wrapped, so the auto-size sees a name too wide (Ear training's AnswerButton in IntervalsPart.kt
            // says why).
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = style.fontSize, stepSize = 0.5.sp),
        )
    }
}

/** The tab's name: the Metronome tab is Practice with [practice] (Phase 32). */
internal fun Destination.label(practice: Boolean): Int = when (this) {
    Destination.TUNER -> R.string.nav_tuner
    Destination.METRONOME -> if (practice) R.string.nav_practice else R.string.nav_metronome
    Destination.SETTINGS -> R.string.nav_settings
}

/** Test tags for UI tests and the Baseline Profile generator (as resource ids, see `MainActivity`). */
object NavigationTestTags {
    const val BAR = "nav_bar"
    const val RAIL = "nav_rail"

    /** A tab, e.g. "nav_settings". */
    fun tab(destination: Destination): String = "nav_" + destination.name.lowercase()
}

/** The calm field starts this far above the bar (or reaches this far past the rail). */
private val FIELD_REACH = 36.dp

/** The calm field's strength (the background colour at 70 %), reached this far down the field. */
private const val FIELD_ALPHA = 0.7f
private const val FIELD_FULL_AT = 0.55f

private val BAR_TOP_PADDING = 10.dp
private val BAR_BOTTOM_PADDING = 4.dp
private val RAIL_WIDTH = 80.dp
private val RAIL_GAP = 14.dp
private val ICON_BOX = 28.dp
private val ICON_SIZE = 24.dp
private val ICON_LABEL_GAP = 2.dp
private val LABEL_MARK_GAP = 3.dp
private val ITEM_SIDE_PADDING = 4.dp
private val MIN_TOUCH = 48.dp
private const val MAX_LABEL_FONT_SCALE = 1.3f
/** German "Einstellungen" in the 80 dp rail at 2× text needs this. */
private val MIN_LABEL_SIZE = 7.sp
