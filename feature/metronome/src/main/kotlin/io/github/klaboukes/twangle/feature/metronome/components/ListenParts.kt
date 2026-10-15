package io.github.klaboukes.twangle.feature.metronome.components

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.LINING_FIGURES
import io.github.klaboukes.twangle.core.designsystem.NavMark
import io.github.klaboukes.twangle.core.designsystem.PressTone
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.designsystem.meter.numberScaleFor
import io.github.klaboukes.twangle.core.designsystem.numberFontFor
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.metronome.TempoMarking
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.feature.metronome.ListenPhase
import io.github.klaboukes.twangle.feature.metronome.ListenUi
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.metronome.R
import io.github.klaboukes.twangle.feature.metronome.TempoPick
import kotlin.math.roundToInt

/*
 * Listen's status line and its suggestion balloon (Phase 27, docs/design/metronome-listen-preview.html,
 * "Line" and "Found").
 */

/** What the status line says. */
private sealed interface LineText {
    data object Nothing : LineText

    data class Listening(val estimate: Int) : LineText

    data object NoBeat : LineText

    data class MicOff(val needsSettings: Boolean) : LineText
}

private fun lineFor(listen: ListenUi): LineText = when (listen.phase) {
    ListenPhase.LISTENING -> LineText.Listening(listen.estimate)
    ListenPhase.NO_BEAT -> LineText.NoBeat
    ListenPhase.MIC_OFF -> LineText.MicOff(needsSettings = false)
    ListenPhase.MIC_OFF_SETTINGS -> LineText.MicOff(needsSettings = true)
    ListenPhase.IDLE, ListenPhase.FOUND -> LineText.Nothing
}

/**
 * The status line under the beat lights: 24 dp whose room is always kept (one line of its text at
 * larger fonts), so nothing moves when it shows or hides. 14 sp in the muted colour, centred; a
 * change crossfades (160 ms out, 160 ms in). "Listening…", then "Listening… 96 BPM" with the number
 * in ink; "No steady beat heard  Try again"; "Microphone is off" (with "Open settings" when only
 * Settings can allow it). TalkBack hears it as a polite live region.
 */
@Composable
fun ListenLine(listen: ListenUi, onTryAgain: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val text = TextStyle(fontSize = 14.sp, lineHeight = 1.2.em)
    val body = MaterialTheme.typography.bodyMedium.merge(text)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = LINE_HEIGHT)
            .testTag(MetronomeTestTags.LINE)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        // Keeps one line's room at any font size.
        Text(" ", style = body, modifier = Modifier.alpha(0f).clearAndSetSemantics {})
        AnimatedContent(
            targetState = lineFor(listen),
            transitionSpec = { fadeIn(tween(LINE_FADE_MILLIS, delayMillis = LINE_FADE_MILLIS)) togetherWith fadeOut(tween(LINE_FADE_MILLIS)) },
            contentAlignment = Alignment.Center,
            label = "listen line",
        ) { line -> LineContent(line, body, onTryAgain, onOpenSettings) }
    }
}

@Composable
private fun LineContent(line: LineText, body: TextStyle, onTryAgain: () -> Unit, onOpenSettings: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.styleTokens.style
    when (line) {
        LineText.Nothing -> Unit
        is LineText.Listening -> {
            val listening = stringResource(R.string.metronome_listening)
            val bpmLabel = stringResource(R.string.metronome_bpm)
            val digits = if (line.estimate > 0) localDigits(line.estimate, appLocale()) else ""
            val numbers = numberFontFor(style)
            Text(
                buildAnnotatedString {
                    append(listening)
                    if (line.estimate > 0) {
                        append(" ")
                        withStyle(SpanStyle(color = scheme.onBackground, fontFamily = numbers, fontWeight = FontWeight.Medium, fontFeatureSettings = "$LINING_FIGURES, tnum")) {
                            append(digits)
                        }
                        append(" ")
                        append(bpmLabel)
                    }
                },
                style = body,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LineText.NoBeat -> LineWithAction(stringResource(R.string.metronome_no_beat), stringResource(R.string.metronome_try_again), body, onTryAgain)
        is LineText.MicOff -> LineWithAction(
            stringResource(R.string.metronome_mic_off),
            if (line.needsSettings) stringResource(R.string.metronome_open_settings) else null,
            body,
            onOpenSettings,
        )
    }
}

/** The line's words and, after them, an action in the accent with a 48 dp touch area. */
@Composable
private fun LineWithAction(text: String, action: String?, body: TextStyle, onAction: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = body, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (action != null) {
            val source = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .testTag(MetronomeTestTags.LINE_ACTION)
                    // As tall as the line, so it never moves anything; Compose widens its touch area to 48 dp.
                    .height(LINE_HEIGHT)
                    // The light: a 32 dp pill, as in a 48 dp touch area.
                    .pressFeedback(source, PILL, lightInset = (LINE_HEIGHT - PILL_HEIGHT) / 2)
                    .clickable(interactionSource = source, indication = null, role = Role.Button) {
                        source.pressFlash()
                        onAction()
                    }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(action, style = body.copy(fontWeight = FontWeight.Medium), color = scheme.primary, maxLines = 1)
            }
        }
    }
}

private val LINE_HEIGHT = 24.dp
private val PILL_HEIGHT = 32.dp
private const val LINE_FADE_MILLIS = 160

/**
 * The suggestion: a balloon on its own layer over the screen, pointing at Listen ([anchor], in this
 * layer's coordinates), the tour's balloon (the style's accent with on-accent text, no outline, a
 * soft lift; Futuristic an accent glow and cut corners). Inside: the style's mark and "Heard", the
 * tempo big in the number face with "BPM" and its marking, ½× 1× 2× (only those within 30–250), then
 * Not now and Use. It fades in and out; nothing changes until Use.
 */
@Composable
fun ListenBalloon(
    listen: ListenUi,
    anchor: Rect?,
    onUse: (TempoPick) -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showing = listen.phase == ListenPhase.FOUND && anchor != null
    // The last suggestion stays drawn while the balloon fades out.
    var shown by remember { mutableStateOf(listen) }
    if (listen.phase == ListenPhase.FOUND) shown = listen
    val density = LocalDensity.current
    AnimatedVisibility(
        visible = showing,
        enter = fadeIn(tween(BALLOON_IN_MILLIS)),
        exit = fadeOut(tween(BALLOON_OUT_MILLIS)),
        modifier = modifier,
    ) {
        val place = anchor ?: Rect.Zero
        val pointer = remember { BalloonPointer() }
        Layout(
            content = { Balloon(shown, pointer, onUse, onNotNow) },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val side = with(density) { BALLOON_SIDE_MARGIN.toPx() }
            val gap = with(density) { BALLOON_GAP.toPx() }
            val maxWidth = with(density) { BALLOON_MAX_WIDTH.roundToPx() }
            val balloon = measurables.single().measure(
                Constraints(maxWidth = minOf(maxWidth, (constraints.maxWidth - 2 * side.roundToInt()).coerceAtLeast(0))),
            )
            val end = with(density) { POINTER_END_MARGIN.toPx() }
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            val w = balloon.width.toFloat()
            val h = balloon.height.toFloat()
            val left: Float
            val top: Float
            when {
                // Above Listen, as the mock-up; below where there is no room above (a short window).
                h <= place.top - gap || h <= height - place.bottom - gap -> {
                    val above = h <= place.top - gap
                    left = (place.center.x - w / 2f).coerceIn(side, (width - side - w).coerceAtLeast(side))
                    top = if (above) place.top - gap - h else place.bottom + gap
                    pointer.side = if (above) BalloonSide.ABOVE else BalloonSide.BELOW
                    pointer.at = (place.center.x - left).coerceIn(end, (w - end).coerceAtLeast(end))
                }
                // Beside it on the side with more room (a phone on its side), over the dial.
                else -> {
                    val toLeft = place.left >= width - place.right
                    left = if (toLeft) (place.left - gap - w).coerceAtLeast(side) else (place.right + gap).coerceAtMost(width - side - w)
                    top = (place.center.y - h / 2f).coerceIn(0f, (height - h).coerceAtLeast(0f))
                    pointer.side = if (toLeft) BalloonSide.LEFT_OF else BalloonSide.RIGHT_OF
                    pointer.at = (place.center.y - top).coerceIn(end, (h - end).coerceAtLeast(end))
                }
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                balloon.place(left.roundToInt(), top.roundToInt())
            }
        }
    }
}

@Composable
private fun Balloon(listen: ListenUi, pointer: BalloonPointer, onUse: (TempoPick) -> Unit, onNotNow: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tokens = MaterialTheme.styleTokens
    val style = tokens.style
    val neon = style == VisualStyle.FUTURISTIC
    val fill = scheme.primary
    val onFill = scheme.onPrimary
    val shape = MaterialTheme.shapes.medium
    val shadowAlpha = if (tokens.dark) DARK_SHADOW else LIGHT_SHADOW
    val title = stringResource(R.string.metronome_heard)
    val shadow = remember { BalloonShadow() }
    val density = LocalDensity.current
    var pick by rememberSaveable(listen.found, listen.half, listen.double) { mutableStateOf(TempoPick.AS_HEARD) }
    val tempo = listen.tempoFor(pick).takeIf { it > 0 } ?: listen.found
    val muted = onFill.copy(alpha = MUTED_ON_ACCENT)
    val numbers = numberFontFor(style)
    val body = MaterialTheme.typography.bodyLarge
    Column(
        Modifier
            .testTag(MetronomeTestTags.BALLOON)
            .semantics { paneTitle = title }
            .drawBehind { drawBalloon(shape, pointer, fill, neon, shadowAlpha, shadow, with(density) { POINTER_WIDTH.toPx() }, with(density) { POINTER_HEIGHT.toPx() }) }
            .widthIn(max = BALLOON_MAX_WIDTH)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NavMark(style, onFill, Modifier)
            Text(
                title,
                style = body.copy(fontSize = 12.sp, lineHeight = 1.3.em, fontWeight = FontWeight.Medium, letterSpacing = 0.03.em),
                color = muted,
            )
        }
        val digits = localDigits(tempo, appLocale())
        val marking = markingName(TempoMarking.of(tempo))
        val numberSize = with(density) { (BALLOON_NUMBER_UNITS * numberScaleFor(style)).dp.toSp() }
        Row(
            Modifier
                .padding(top = 2.dp)
                .testTag(MetronomeTestTags.BALLOON_BPM)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                digits,
                style = TextStyle(fontFamily = numbers, fontSize = numberSize, lineHeight = numberSize * 1.05f, fontFeatureSettings = "$LINING_FIGURES, tnum"),
                color = onFill,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                stringResource(R.string.metronome_bpm),
                style = TextStyle(fontFamily = numbers, fontSize = 13.sp, letterSpacing = (13 * 0.12).sp, fontFeatureSettings = LINING_FIGURES),
                color = muted,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                marking,
                style = body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic),
                color = muted,
                modifier = Modifier.alignByBaseline(),
            )
        }
        val picks = TempoPick.entries.filter { listen.tempoFor(it) > 0 }
        if (picks.size > 1) {
            val speed = stringResource(R.string.metronome_listen_speed)
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .selectableGroup()
                    .semantics { contentDescription = speed },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (option in picks) PickChip(option, picked = option == pick, onPick = { pick = option })
            }
        }
        BalloonButtons(onNotNow = onNotNow, onUse = { onUse(pick) }, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PickChip(option: TempoPick, picked: Boolean, onPick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.styleTokens.style
    val source = remember { MutableInteractionSource() }
    val shape = chipShape(style)
    val spoken = stringResource(
        when (option) {
            TempoPick.HALF -> R.string.metronome_pick_half
            TempoPick.AS_HEARD -> R.string.metronome_pick_as_heard
            TempoPick.DOUBLE -> R.string.metronome_pick_double
        },
    )
    val label = when (option) {
        TempoPick.HALF -> "½×"
        TempoPick.AS_HEARD -> localDigits(1, appLocale()) + "×"
        TempoPick.DOUBLE -> localDigits(2, appLocale()) + "×"
    }
    Box(
        Modifier
            .testTag(MetronomeTestTags.pick(option))
            .defaultMinSize(minWidth = 48.dp, minHeight = 32.dp)
            .pressFeedback(
                source,
                shape,
                fill = if (picked) scheme.onPrimary else scheme.onPrimary.copy(alpha = CHIP_FILL),
                tone = if (picked) PressTone.IN_ACCENT_FILLED else PressTone.IN_ACCENT,
            )
            .selectable(selected = picked, interactionSource = source, indication = null, role = Role.RadioButton) {
                source.pressFlash()
                onPick()
            }
            .semantics { contentDescription = spoken }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(fontFamily = numberFontFor(style), fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = LINING_FIGURES),
            color = if (picked) scheme.primary else scheme.onPrimary,
        )
    }
}

/** Not now (a text button at the start, its words lined up with the balloon's) and Use (filled on-accent, at the end), on one row. */
@Composable
private fun BalloonButtons(onNotNow: () -> Unit, onUse: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.styleTokens.style
    val neon = style == VisualStyle.FUTURISTIC
    val shape = useShape(style)
    val text = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 1.2.em, fontWeight = FontWeight.Medium)
    val skipSource = remember { MutableInteractionSource() }
    val useSource = remember { MutableInteractionSource() }
    Row(modifier.widthIn(min = 200.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .offset(x = -SKIP_SHIFT)
                .testTag(MetronomeTestTags.NOT_NOW)
                .heightIn(min = 40.dp)
                .pressFeedback(skipSource, shape, tone = PressTone.IN_ACCENT)
                .clickable(interactionSource = skipSource, indication = null, role = Role.Button) {
                    skipSource.pressFlash()
                    onNotNow()
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.metronome_not_now), style = text, color = scheme.onPrimary) }
        Spacer(Modifier.weight(1f).width(8.dp))
        Box(
            Modifier
                .testTag(MetronomeTestTags.USE)
                .heightIn(min = 40.dp)
                .pressFeedback(
                    useSource,
                    shape,
                    fill = if (neon) scheme.onPrimary.copy(alpha = CHIP_FILL) else scheme.onPrimary,
                    outline = if (neon) BorderStroke(1.5.dp, scheme.onPrimary) else null,
                    tone = if (neon) PressTone.IN_ACCENT else PressTone.IN_ACCENT_FILLED,
                )
                .clickable(interactionSource = useSource, indication = null, role = Role.Button) {
                    useSource.pressFlash()
                    onUse()
                }
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.metronome_use), style = text, color = if (neon) scheme.onPrimary else scheme.primary) }
    }
}

/** The ½× 1× 2× chips: the style's corners up to 16 dp (Futuristic cut). */
private fun chipShape(style: VisualStyle): Shape = when (style) {
    VisualStyle.CLASSIC -> RoundedCornerShape(4.dp)
    VisualStyle.FUTURISTIC -> CutCornerShape(8.dp)
    VisualStyle.ANCIENT, VisualStyle.RETRO, VisualStyle.CONTEMPORARY, VisualStyle.SPACE_AGE -> RoundedCornerShape(16.dp)
}

/** Use's shape, as the tour's Next: the style's button shape with at most 20 dp corners. */
private fun useShape(style: VisualStyle): Shape = when (style) {
    VisualStyle.ANCIENT -> RoundedCornerShape(topStart = 22.dp, topEnd = 16.dp, bottomEnd = 24.dp, bottomStart = 14.dp)
    VisualStyle.CLASSIC -> RoundedCornerShape(4.dp)
    VisualStyle.FUTURISTIC -> CutCornerShape(10.dp)
    VisualStyle.RETRO, VisualStyle.CONTEMPORARY, VisualStyle.SPACE_AGE -> RoundedCornerShape(20.dp)
}

/** Where the balloon stands from Listen. */
private enum class BalloonSide { ABOVE, BELOW, LEFT_OF, RIGHT_OF }

/** The balloon's pointer: on which side, and where along it (px from its top or left). Set in layout, read in draw. */
private class BalloonPointer {
    var side by mutableStateOf(BalloonSide.ABOVE)
    var at by mutableFloatStateOf(0f)
}

/** The paints of the balloon's two-layer shadow (or Futuristic's glow); Android 9+ draws them in hardware. */
private class BalloonShadow {
    val near = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val far = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
}

/**
 * The balloon's body in [shape] with its pointer on the side [pointer] says, filled with
 * [fill] over a soft shadow at [shadowAlpha] (Futuristic, [neon]: an accent glow and a small shadow),
 * as the tour's balloon.
 */
private fun DrawScope.drawBalloon(
    shape: Shape,
    pointer: BalloonPointer,
    fill: Color,
    neon: Boolean,
    shadowAlpha: Float,
    shadow: BalloonShadow,
    pointerWidth: Float,
    pointerHeight: Float,
) {
    val path = Path().apply {
        addOutline(shape.createOutline(size, layoutDirection, this@drawBalloon))
        val half = pointerWidth / 2f
        val at = pointer.at
        when (pointer.side) {
            BalloonSide.ABOVE -> {
                moveTo(at - half, size.height - 1f)
                lineTo(at + half, size.height - 1f)
                lineTo(at, size.height + pointerHeight)
            }
            BalloonSide.BELOW -> {
                moveTo(at - half, 1f)
                lineTo(at + half, 1f)
                lineTo(at, -pointerHeight)
            }
            BalloonSide.LEFT_OF -> {
                moveTo(size.width - 1f, at - half)
                lineTo(size.width - 1f, at + half)
                lineTo(size.width + pointerHeight, at)
            }
            BalloonSide.RIGHT_OF -> {
                moveTo(1f, at - half)
                lineTo(1f, at + half)
                lineTo(-pointerHeight, at)
            }
        }
        close()
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

private const val BALLOON_IN_MILLIS = 200
private const val BALLOON_OUT_MILLIS = 150
private val BALLOON_MAX_WIDTH = 260.dp
private val BALLOON_SIDE_MARGIN = 16.dp

/** The balloon's pointer tip stands this far above Listen. */
private val BALLOON_GAP = 11.dp
private val POINTER_WIDTH = 14.dp
private val POINTER_HEIGHT = 9.dp

/** The pointer is kept this far (to its middle) from the balloon's ends. */
private val POINTER_END_MARGIN: Dp = 27.dp

/** Not now's words line up with the balloon's: its button reaches this far into the padding. */
private val SKIP_SHIFT = 12.dp
private const val BALLOON_NUMBER_UNITS = 40f
private const val MUTED_ON_ACCENT = 0.78f
private const val CHIP_FILL = 0.16f
private const val LIGHT_SHADOW = 0.22f
private const val DARK_SHADOW = 0.55f
private const val NEON_GLOW_ALPHA = 0.4f
private const val SHADOW_SPREAD = 0.87f
