package io.github.klaboukes.twangle.feature.tuner.tour

import android.graphics.BlurMaskFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.NavMark
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.components.rememberAnimationsOff
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * The first-start tour's home: [content] (the whole window: the screen and its navigation bar or
 * rail) with the tour on its own layer above it while [open], never in its layout, so nothing in
 * [content] moves when the tour comes or goes. The parts in [steps] mark themselves with
 * [tourTarget] somewhere in [content]; the tour lights them up one at a time, each with a balloon,
 * in the layout's reading order ([tourOrder]: the navigation bar or rail last). Skip tour, Done (the last step's Next) and Back call [onEnd], which should close it (make [open]
 * false); it then fades out. While it is open, taps outside the balloon do nothing and TalkBack
 * hears only the balloon. [targets] is where the parts' places are kept (tests read it).
 */
@Composable
fun TourHost(
    open: Boolean,
    steps: List<TourPart>,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
    targets: TourTargets = remember { TourTargets() },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTourTargets provides targets) {
        Box(modifier, propagateMinConstraints = true) {
            // The screen below is hidden from TalkBack while the tour runs: only the balloon is heard.
            Box(if (open) Modifier.clearAndSetSemantics {} else Modifier, propagateMinConstraints = true) { content() }
            if (steps.isNotEmpty()) TourOverlay(open, steps, targets, onEnd, Modifier.matchParentSize())
        }
    }
}

/** Test tags for UI tests. */
object TourTestTags {
    /** The balloon (a pane titled "Show me around"), its text, and its two buttons. */
    const val BALLOON = "tour_balloon"
    const val TEXT = "tour_text"
    const val SKIP = "tour_skip"
    const val NEXT = "tour_next"

    /** The dim layer over the whole window. */
    const val OVERLAY = "tour_overlay"
}

/**
 * The tour's layer: the dim layer with the spotlight cut out round the current step's part, and the
 * balloon beside it (approved mock-up, docs/design/tour-preview.html). It fades in when [open] turns
 * true (at the step it was on, or the first after it ended) and out when it turns false. Next glides
 * the spotlight to the next part while the balloon fades out, then fades the next balloon in. The
 * steps go in the current layout's reading order ([tourOrder]); each balloon points at its own
 * element and keeps clear of the other controls where it can ([placeBalloon]).
 */
@Composable
private fun TourOverlay(
    open: Boolean,
    steps: List<TourPart>,
    targets: TourTargets,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animationsOff = rememberAnimationsOff()
    fun ms(duration: Int) = if (animationsOff) 0 else duration
    val density = LocalDensity.current
    val style = MaterialTheme.styleTokens.style
    // The step the spotlight is on (saved, so a turned phone carries on there), and the one the balloon shows.
    var step by rememberSaveable { mutableIntStateOf(0) }
    var ended by rememberSaveable { mutableStateOf(false) }
    var shownStep by remember { mutableIntStateOf(step) }
    val dim = remember { Animatable(0f) }
    val glide = remember { Animatable(1f) }
    val balloonAlpha = remember { Animatable(0f) }
    var glideFrom by remember { mutableStateOf<Spotlight?>(null) }
    val frame = remember { TourFrame() }
    val scope = rememberCoroutineScope()
    val moving = remember { arrayOfNulls<Job>(1) }
    val currentOnEnd by rememberUpdatedState(onEnd)
    // In the current layout's reading order (a turned phone reads its own), the bar or rail last.
    val order by remember(steps) { derivedStateOf { tourOrder(steps) { targets.boundsOf(it) } } }
    val last = steps.lastIndex
    val atStep = step.coerceIn(0, last)
    val balloonStep = shownStep.coerceIn(0, last)

    // A box in the window's pixels in the overlay's own.
    fun local(b: TourBox) = TourBox(b.left - frame.originX, b.top - frame.originY, b.right - frame.originX, b.bottom - frame.originY)

    fun spotlightOf(part: TourPart): Spotlight? = targets.boundsOf(part)?.let { b ->
        val pad = TourMeasures.HOLE_PAD * density.density
        spotlightFor(local(b), frame.width, frame.height, pad, tourCornerDp(style, part.corner) * density.density)
    }

    fun finish() {
        moving[0]?.cancel()
        ended = true
        currentOnEnd()
    }

    fun next() {
        if (moving[0]?.isActive == true || !open) return
        if (atStep >= last) {
            finish()
            return
        }
        moving[0] = scope.launch {
            glideFrom = spotlightOf(order[atStep])
            step = atStep + 1
            launch { balloonAlpha.animateTo(0f, tween(ms(TourTimings.FADE_OUT))) }
            launch { targets.reveal(order[step]) }
            glide.snapTo(0f)
            glide.animateTo(1f, tween(ms(TourTimings.GLIDE), easing = LinearEasing))
            glideFrom = null
            balloonAlpha.snapTo(0f)
            shownStep = step
            balloonAlpha.animateTo(1f, tween(ms(TourTimings.FADE_IN)))
        }
    }

    LaunchedEffect(open) {
        moving[0]?.cancel()
        if (open) {
            if (ended) {
                step = 0
                ended = false
            }
            shownStep = step
            glideFrom = null
            glide.snapTo(1f)
            balloonAlpha.snapTo(0f)
            launch { targets.reveal(order[step.coerceIn(0, last)]) }
            dim.animateTo(1f, tween(ms(TourTimings.DIM_IN)))
            balloonAlpha.animateTo(1f, tween(ms(TourTimings.FADE_IN)))
        } else {
            targets.shown = null
            launch { balloonAlpha.animateTo(0f, tween(ms(TourTimings.CLOSE))) }
            dim.animateTo(0f, tween(ms(TourTimings.CLOSE)))
        }
    }
    BackHandler(enabled = open) { finish() }

    val shown by remember { derivedStateOf { dim.value > 0f } }
    if (!open && !shown) return

    val scheme = MaterialTheme.colorScheme
    val dark = MaterialTheme.styleTokens.dark
    val scrim = if (dark) {
        lerp(scheme.background, Color.Black, TourMeasures.DARK_SCRIM_BLACK).copy(alpha = TourMeasures.DARK_SCRIM_ALPHA)
    } else {
        lerp(scheme.background, scheme.onBackground, TourMeasures.LIGHT_SCRIM_INK).copy(alpha = TourMeasures.LIGHT_SCRIM_ALPHA)
    }
    val halo = scheme.primary.copy(alpha = if (dark) TourMeasures.HALO_DARK_ALPHA else TourMeasures.HALO_LIGHT_ALPHA)
    val neon = style == VisualStyle.FUTURISTIC
    val holePaint = remember { HolePaint() }
    val placement = remember { mutableStateOf<BalloonPlacement?>(null) }
    val insets = WindowInsets.safeDrawing
    val fontScale = density.fontScale

    Layout(
        content = {
            TourBalloon(
                index = balloonStep,
                count = steps.size,
                part = order[balloonStep],
                placement = { placement.value },
                onSkip = ::finish,
                onNext = ::next,
                modifier = Modifier.graphicsLayer {
                    alpha = balloonAlpha.value
                    // No offscreen layer, which would clip the pointer and shadow outside the balloon's box.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
            )
        },
        modifier = modifier
            .testTag(TourTestTags.OVERLAY)
            .onGloballyPositioned {
                val origin = it.positionInRoot()
                frame.originX = origin.x
                frame.originY = origin.y
            }
            // Taps anywhere but on the balloon's buttons do nothing: the lit part isn't live.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
            .drawBehind {
                val to = spotlightOf(order[atStep])
                val from = glideFrom
                val hole = if (from != null && to != null) lerp(from, to, easeInOutCubic(glide.value)) else to
                drawScrim(scrim.copy(alpha = scrim.alpha * dim.value), halo.copy(alpha = halo.alpha * dim.value), hole, neon, holePaint)
            },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        frame.width = width.toFloat()
        frame.height = height.toFloat()
        val margins = TourMargins(
            left = max(TourMeasures.EDGE.dp.toPx(), insets.getLeft(this, layoutDirection).toFloat()),
            top = max(TourMeasures.TOP.dp.toPx(), insets.getTop(this).toFloat()),
            right = max(TourMeasures.EDGE.dp.toPx(), insets.getRight(this, layoutDirection).toFloat()),
            bottom = max(TourMeasures.BOTTOM.dp.toPx(), insets.getBottom(this).toFloat()),
        )
        val maxWidth = min(width - margins.left - margins.right, TourMeasures.MAX_WIDTH.dp.toPx() * fontScale).toInt().coerceAtLeast(0)
        val balloon = measurables.single().measure(Constraints(maxWidth = maxWidth, maxHeight = height))
        val part = order[balloonStep]
        val element = targets.boundsOf(part)?.let(::local)
        val reach = (TourMeasures.POINTER_GAP + TourMeasures.POINTER_HEIGHT).dp.toPx()
        val place = placeBalloon(
            element,
            balloon.width.toFloat(),
            balloon.height.toFloat(),
            width.toFloat(),
            height.toFloat(),
            margins,
            part.preferredSide,
            reach,
            TourMeasures.POINTER_END.dp.toPx(),
            avoid = targets.controlsBesides(part).map(::local),
        )
        placement.value = place
        if (open) {
            // Where the step on screen is, in the window's pixels, for tests.
            val tip = pointerTip(place, balloon.width.toFloat(), balloon.height.toFloat(), TourMeasures.POINTER_HEIGHT.dp.toPx())
            val shownNow = TourShown(
                part,
                balloonStep,
                steps.size,
                place.side,
                TourBox(place.x + frame.originX, place.y + frame.originY, place.x + balloon.width + frame.originX, place.y + balloon.height + frame.originY),
                tip?.let { TourPoint(it.x + frame.originX, it.y + frame.originY) },
            )
            if (targets.shown != shownNow) targets.shown = shownNow
        }
        layout(width, height) { balloon.place(place.x.toInt(), place.y.toInt()) }
    }
}

/** The overlay's place in the window and its size, in pixels, for turning the parts' bounds into its own. */
@Stable
private class TourFrame {
    var originX = 0f
    var originY = 0f
    var width = 0f
    var height = 0f
}

/**
 * What draws the spotlight's soft edge, cut out of the dim layer with a blur, and its halo, a blur
 * outside the edge only (Android 9+; before it, a sharp edge and no halo).
 */
private class HolePaint {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    val haloPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    var blur = -1f
    var haloBlur = -1f

    fun blurred(radius: Float): android.graphics.Paint {
        if (radius != blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            paint.maskFilter = if (radius > 0f) BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL) else null
            blur = radius
        }
        return paint
    }

    /** The halo in [color]: an outer blur of [radius], so it starts at the edge and fades outward. */
    fun halo(radius: Float, color: Color): android.graphics.Paint? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || radius <= 0f) return null
        if (radius != haloBlur) {
            haloPaint.maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.OUTER)
            haloBlur = radius
        }
        // An outer blur is half as strong as its paint at the edge.
        haloPaint.color = color.copy(alpha = (color.alpha * 2f).coerceAtMost(1f)).toArgb()
        return haloPaint
    }
}

/**
 * The dim layer, [color] over the whole window, with [hole] cut out of it: its corners rounded, or
 * cut in Futuristic ([neon]), with a soft edge and no outline, and a soft [halo] in the accent
 * hugging the edge outside, fading out over [TourMeasures.HALO_REACH].
 */
private fun DrawScope.drawScrim(color: Color, halo: Color, hole: Spotlight?, neon: Boolean, holePaint: HolePaint) {
    if (color.alpha <= 0f) return
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val layer = native.saveLayer(0f, 0f, size.width, size.height, null)
        native.drawColor(color.toArgb())
        if (hole != null && hole.box.width > 0f && hole.box.height > 0f) {
            val path = holePath(hole, if (neon) TourMeasures.NEON_CUT.dp.toPx() else 0f)
            // The mock-up's 14 dp canvas shadow blur is a Gaussian of half that; Android's blur radius is about 1.7 of those.
            val radius = TourMeasures.HOLE_SOFT.dp.toPx() * 0.5f * BLUR_RADIUS_PER_SIGMA
            native.drawPath(path.asAndroidPath(), holePaint.blurred(radius))
            // Fades to nothing over the reach: about two Gaussian sigmas.
            val haloRadius = TourMeasures.HALO_REACH.dp.toPx() * 0.5f * BLUR_RADIUS_PER_SIGMA
            if (halo.alpha > 0f) holePaint.halo(haloRadius, halo)?.let { native.drawPath(path.asAndroidPath(), it) }
        }
        native.restoreToCount(layer)
    }
}

/** The spotlight's outline: a rounded box, or (with [cut] > 0) one with its corners cut by at most [cut]. */
private fun holePath(hole: Spotlight, cut: Float): Path = Path().apply {
    val b = hole.box
    if (cut > 0f) {
        val c = min(hole.corner, cut)
        moveTo(b.left + c, b.top)
        lineTo(b.right - c, b.top)
        lineTo(b.right, b.top + c)
        lineTo(b.right, b.bottom - c)
        lineTo(b.right - c, b.bottom)
        lineTo(b.left + c, b.bottom)
        lineTo(b.left, b.bottom - c)
        lineTo(b.left, b.top + c)
        close()
    } else {
        addRoundRect(RoundRect(b.left, b.top, b.right, b.bottom, CornerRadius(hole.corner)))
    }
}

/**
 * The balloon for step [index] of [count] ([part]): filled with the style's accent, no outline,
 * lifted by a soft shadow (Futuristic: an accent glow) that also falls under the pointer, which
 * points at the part from the side [placement] says. Above the text the style's mark and the step
 * count; under it Skip tour and Next (Done on the last step), side by side or on two rows. TalkBack
 * hears a pane titled "Show me around" reading "Step 2 of 5. Lock a string.", then the buttons.
 */
@Composable
private fun TourBalloon(
    index: Int,
    count: Int,
    part: TourPart,
    placement: () -> BalloonPlacement?,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = MaterialTheme.styleTokens
    val style = tokens.style
    val neon = style == VisualStyle.FUTURISTIC
    val fill = scheme.primary
    val onFill = scheme.onPrimary
    val shape = MaterialTheme.shapes.medium
    val text = stringResource(part.text)
    val spoken = stringResource(R.string.tour_step_spoken, index + 1, count, text)
    val title = stringResource(R.string.tour_title)
    val body = MaterialTheme.typography.bodyLarge
    val shadow = remember { BalloonShadow() }
    val shadowAlpha = if (tokens.dark) DARK_SHADOW else LIGHT_SHADOW
    Column(
        modifier
            .testTag(TourTestTags.BALLOON)
            .semantics { paneTitle = title }
            .drawBehind { drawBalloon(shape, placement(), fill, neon, shadowAlpha, shadow) }
            .width(IntrinsicSize.Max)
            .padding(start = BALLOON_SIDE_PADDING, end = BALLOON_SIDE_PADDING, top = BALLOON_TOP_PADDING, bottom = BALLOON_BOTTOM_PADDING),
    ) {
        Row(
            Modifier.padding(bottom = 4.dp).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavMark(style, onFill, Modifier)
            Text(
                stringResource(R.string.tour_step_count, index + 1, count),
                style = body.copy(fontSize = 12.sp, lineHeight = 1.3.em, fontWeight = FontWeight.Medium, letterSpacing = 0.03.em),
                color = onFill.copy(alpha = COUNT_ALPHA),
            )
        }
        Text(
            text,
            style = body.copy(fontSize = 16.sp, lineHeight = 1.35.em),
            color = onFill,
            modifier = Modifier
                .testTag(TourTestTags.TEXT)
                .semantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                },
        )
        TourButtons(
            skip = {
                TextButton(
                    onClick = onSkip,
                    shape = tourButtonShape(style),
                    colors = ButtonDefaults.textButtonColors(contentColor = onFill),
                    contentPadding = PaddingValues(horizontal = SKIP_SHIFT, vertical = 6.dp),
                    modifier = Modifier.testTag(TourTestTags.SKIP),
                ) { Text(stringResource(R.string.tour_skip), style = buttonText(body)) }
            },
            next = {
                Button(
                    onClick = onNext,
                    shape = tourButtonShape(style),
                    colors = if (neon) {
                        ButtonDefaults.buttonColors(containerColor = onFill.copy(alpha = NEON_NEXT_FILL), contentColor = onFill)
                    } else {
                        ButtonDefaults.buttonColors(containerColor = onFill, contentColor = fill)
                    },
                    border = if (neon) BorderStroke(1.5.dp, onFill) else null,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.testTag(TourTestTags.NEXT),
                ) {
                    Text(stringResource(if (index == count - 1) R.string.tour_done else R.string.tour_next), style = buttonText(body))
                }
            },
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun buttonText(body: TextStyle) = body.copy(fontSize = 14.sp, lineHeight = 1.2.em, fontWeight = FontWeight.Medium)

/** Next's shape: the style's button shape with at most 20 dp corners (Ancient's pebble, Futuristic's cut corners). */
private fun tourButtonShape(style: VisualStyle): Shape = when (style) {
    VisualStyle.ANCIENT -> RoundedCornerShape(topStart = 22.dp, topEnd = 16.dp, bottomEnd = 24.dp, bottomStart = 14.dp)
    VisualStyle.CLASSIC -> RoundedCornerShape(4.dp)
    VisualStyle.FUTURISTIC -> CutCornerShape(10.dp)
    VisualStyle.RETRO, VisualStyle.CONTEMPORARY, VisualStyle.SPACE_AGE -> RoundedCornerShape(20.dp)
}

/**
 * Skip tour at the start and Next at the end of one row; where the two don't fit side by side, Skip
 * on the first row and Next at the end of a second. Skip's text lines up with the balloon's text
 * ([SKIP_SHIFT] into the padding). Its widest is both side by side, so the balloon hugs them.
 */
@Composable
private fun TourButtons(skip: @Composable () -> Unit, next: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Layout(contents = listOf(skip, next), modifier = modifier, measurePolicy = TourButtonsPolicy)
}

private object TourButtonsPolicy : MultiContentMeasurePolicy {
    override fun MeasureScope.measure(measurables: List<List<Measurable>>, constraints: Constraints): MeasureResult {
        val shift = SKIP_SHIFT.roundToPx()
        val gap = BUTTON_GAP.roundToPx()
        val rowGap = BUTTON_ROW_GAP.roundToPx()
        val available = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val skip = measurables[0].single().measure(Constraints(maxWidth = if (available == Constraints.Infinity) available else available + shift))
        val next = measurables[1].single().measure(Constraints(maxWidth = available))
        val oneRow = skip.width - shift + gap + next.width
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else max(oneRow, max(skip.width - shift, next.width))
        val sideBySide = oneRow <= width
        val height = if (sideBySide) max(skip.height, next.height) else skip.height + rowGap + next.height
        return layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            if (sideBySide) {
                skip.placeRelative(-shift, (height - skip.height) / 2)
                next.placeRelative(width - next.width, (height - next.height) / 2)
            } else {
                skip.placeRelative(-shift, 0)
                next.placeRelative(width - next.width, skip.height + rowGap)
            }
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int): Int =
        measurables[0].single().maxIntrinsicWidth(height) - SKIP_SHIFT.roundToPx() + BUTTON_GAP.roundToPx() +
            measurables[1].single().maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int): Int =
        max(measurables[0].single().minIntrinsicWidth(height) - SKIP_SHIFT.roundToPx(), measurables[1].single().minIntrinsicWidth(height))

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int): Int =
        measurables[0].single().minIntrinsicHeight(width) + BUTTON_ROW_GAP.roundToPx() + measurables[1].single().minIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int): Int =
        measurables[0].single().maxIntrinsicHeight(width) + BUTTON_ROW_GAP.roundToPx() + measurables[1].single().maxIntrinsicHeight(width)
}

/** The paints of the balloon's two-layer shadow (or Futuristic's glow); Android 9+ draws them in hardware. */
private class BalloonShadow {
    val near = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val far = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
}

/**
 * The balloon's body in [shape] with its pointer on the side [placement] says, filled with [fill]
 * over a soft shadow at [shadowAlpha] (Futuristic, [neon]: an accent glow and a small shadow).
 */
private fun DrawScope.drawBalloon(shape: Shape, placement: BalloonPlacement?, fill: Color, neon: Boolean, shadowAlpha: Float, shadow: BalloonShadow) {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBalloon)) }
    if (placement != null && placement.side != BalloonSide.OVER) {
        path.addPath(pointerPath(placement, size, this))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val argb = fill.toArgb()
        val native = path.asAndroidPath()
        drawIntoCanvas { canvas ->
            if (neon) {
                shadow.far.apply { color = argb; setShadowLayer(10.dp.toPx() * SHADOW_SPREAD, 0f, 0f, fill.copy(alpha = NEON_GLOW_ALPHA).toArgb()) }
                shadow.near.apply { color = argb; setShadowLayer(4.dp.toPx() * SHADOW_SPREAD, 0f, 2.dp.toPx(), Color.Black.copy(alpha = shadowAlpha).toArgb()) }
            } else {
                shadow.far.apply { color = argb; setShadowLayer(10.dp.toPx() * SHADOW_SPREAD, 0f, 6.dp.toPx(), Color.Black.copy(alpha = shadowAlpha).toArgb()) }
                shadow.near.apply { color = argb; setShadowLayer(2.dp.toPx() * SHADOW_SPREAD, 0f, 1.dp.toPx(), Color.Black.copy(alpha = shadowAlpha * 0.8f).toArgb()) }
            }
            canvas.nativeCanvas.drawPath(native, shadow.far)
            canvas.nativeCanvas.drawPath(native, shadow.near)
        }
    }
    drawPath(path, fill)
}

/** The 14 × 8 dp pointer on the balloon's side facing the part, its middle at [BalloonPlacement.pointer]; it overlaps the body by 1 px. */
private fun pointerPath(placement: BalloonPlacement, size: Size, density: Density): Path = Path().apply {
    val half = with(density) { TourMeasures.POINTER_WIDTH.dp.toPx() } / 2f
    val tall = with(density) { TourMeasures.POINTER_HEIGHT.dp.toPx() }
    val at = placement.pointer
    when (placement.side) {
        BalloonSide.ABOVE -> {
            moveTo(at - half, size.height - 1f)
            lineTo(at + half, size.height - 1f)
            lineTo(at, size.height + tall)
        }
        BalloonSide.BELOW -> {
            moveTo(at - half, 1f)
            lineTo(at + half, 1f)
            lineTo(at, -tall)
        }
        BalloonSide.RIGHT -> {
            moveTo(1f, at - half)
            lineTo(1f, at + half)
            lineTo(-tall, at)
        }
        BalloonSide.LEFT -> {
            moveTo(size.width - 1f, at - half)
            lineTo(size.width - 1f, at + half)
            lineTo(size.width + tall, at)
        }
        BalloonSide.OVER -> Unit
    }
    close()
}

private val BALLOON_SIDE_PADDING = 14.dp
private val BALLOON_TOP_PADDING = 12.dp

/** The mock-up's 8 dp, less the 4 dp the buttons' 48 dp touch targets add under their 40 dp. */
private val BALLOON_BOTTOM_PADDING = 4.dp

/** Skip's text lines up with the balloon's text: its button reaches this far into the padding. */
private val SKIP_SHIFT = 12.dp
private val BUTTON_GAP = 8.dp
private val BUTTON_ROW_GAP = 4.dp

/** The step count is the on-accent colour at 78 %. */
private const val COUNT_ALPHA = 0.78f

/** The mock-up's shadow strength, light and dark. */
private const val LIGHT_SHADOW = 0.22f
private const val DARK_SHADOW = 0.55f

/** Futuristic: the accent glow at 40 %, and Next filled with on-accent at 16 % inside its neon outline. */
private const val NEON_GLOW_ALPHA = 0.4f
private const val NEON_NEXT_FILL = 0.16f

/** A CSS blur radius is two Gaussian sigmas; Android's shadow and mask radius is about 1.7 sigmas. */
private const val BLUR_RADIUS_PER_SIGMA = 1.73f
private const val SHADOW_SPREAD = 0.87f
