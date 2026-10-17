package io.github.klaboukes.twangle.app.store

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ActivityScenario
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.settings.VisualStyle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * One store screenshot as Play shows it: [CANVAS_WIDTH] × [CANVAS_HEIGHT] pixels in the style's
 * (quiet) background, the caption in large type above, and below it the app's screens in a slim
 * phone frame. One screen upright fills the space under the caption; two stand side by side, each at
 * about half width. A screen on its side (screenshot 8) comes already turned upright, as the phone lies.
 *
 * Drawn with the app's own theme ([TwangleTheme]), so the caption is set in the style's display face
 * with its fallbacks for other scripts (`StyleTypography`: Arabic, Devanagari, CJK and Cyrillic get the
 * phone's own font, Polish and Turkish in Futuristic get Space Age's), right to left where the
 * language is. [bubble] (the TalkBack screenshot) is what TalkBack says, in a speech bubble between
 * the caption and the phone, pointing at the phone: drawn here, since a picture can't show speech.
 */
@Composable
fun StoreCanvas(
    caption: String,
    screens: List<ImageBitmap>,
    style: VisualStyle,
    dark: Boolean,
    bubble: SpeechBubble? = null,
) {
    // 1080 px are 400 dp here on every phone, and the phone's own text size is left out.
    CompositionLocalProvider(LocalDensity provides Density(CANVAS_WIDTH / CANVAS_WIDTH_DP, fontScale = 1f)) {
        TwangleTheme(style = style, darkTheme = dark, animate = false) {
            Box(
                Modifier.layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints.fixed(CANVAS_WIDTH, CANVAS_HEIGHT))
                    layout(CANVAS_WIDTH, CANVAS_HEIGHT) { placeable.place(0, 0) }
                },
            ) {
                StyleBackdrop(Modifier.fillMaxSize(), quiet = true)
                val first = screens.first()
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Caption(
                        caption,
                        Modifier
                            .padding(horizontal = SIDE_MARGIN)
                            .fillMaxWidth()
                            .height(if (bubble == null) CAPTION_HEIGHT else CAPTION_HEIGHT_OVER_BUBBLE),
                    )
                    if (bubble != null) Bubble(bubble.text)
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .padding(bottom = BOTTOM_MARGIN, start = SIDE_MARGIN, end = SIDE_MARGIN)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val space = maxWidth
                        val tall = maxHeight
                        when {
                            screens.size > 1 -> Row(horizontalArrangement = Arrangement.spacedBy(PHONE_GAP)) {
                                val width = (space - PHONE_GAP * (screens.size - 1)) / screens.size
                                // Staggered, the first higher, so two phones fill the space under the caption.
                                val height = phoneHeight(first, width)
                                val stagger = ((tall - height) * STAGGER_SHARE).coerceAtLeast(0.dp)
                                screens.forEachIndexed { i, screen ->
                                    Phone(screen, width, Modifier.padding(top = if (i % 2 == 0) 0.dp else stagger, bottom = if (i % 2 == 0) stagger else 0.dp))
                                }
                            }
                            else -> Phone(first, minOf(space, tall * (first.width.toFloat() / first.height)))
                        }
                    }
                }
            }
        }
    }
}

/** What TalkBack says, drawn as a speech bubble above the phone. */
data class SpeechBubble(val text: String)

/** The caption, as large as fits in three lines, centred in its space. */
@Composable
private fun Caption(text: String, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val base = MaterialTheme.typography.headlineLarge
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        // The largest size that fits in two lines, else the largest in three: a short caption stays large.
        val style = remember(text, base, widthPx, heightPx) {
            fun fits(candidate: TextStyle, lines: Int): Boolean {
                val layout = measurer.measure(text, candidate, constraints = Constraints(maxWidth = widthPx))
                return layout.lineCount <= lines && layout.size.height <= heightPx
            }
            val sizes = CAPTION_SIZES.map { base.captionStyle(it) }
            sizes.filter { it.fontSize.value >= TWO_LINE_MIN_SIZE }.firstOrNull { fits(it, 2) }
                ?: sizes.firstOrNull { fits(it, CAPTION_MAX_LINES) }
                ?: sizes.last()
        }
        // Two short sentences ("Play a string. See it in tune.") get a line each, where they fit so.
        val shown = remember(text, style, widthPx) {
            sentencesOnTheirOwnLines(text)?.takeIf { split ->
                measurer.measure(split, style, constraints = Constraints(maxWidth = widthPx)).lineCount == 2
            } ?: text
        }
        Text(shown, style = style, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** [text] with a line break after its first sentence, if it is two sentences; else null. */
private fun sentencesOnTheirOwnLines(text: String): String? {
    val ends = listOf(". ", "。", "। ").mapNotNull { end -> text.indexOf(end).takeIf { it in 1 until text.length - end.length }?.let { it + end.length } }
    val cut = ends.minOrNull() ?: return null
    return text.substring(0, cut).trimEnd() + "\n" + text.substring(cut).trimStart()
}

/** Balanced lines (no single word left on the last one), centred. */
private fun TextStyle.captionStyle(size: Float) =
    copy(fontSize = size.sp, lineHeight = (size * 1.22f).sp, textAlign = TextAlign.Center, lineBreak = LineBreak.Heading)

/** How tall [Phone] draws [screen] at [width]. */
private fun phoneHeight(screen: ImageBitmap, width: Dp): Dp = (width - FRAME * 2) * (screen.height.toFloat() / screen.width) + FRAME * 2

/** A screen picture in a slim, dark phone frame with round corners and a soft shadow, [width] wide. */
@Composable
private fun Phone(screen: ImageBitmap, width: Dp, modifier: Modifier = Modifier) {
    val frame = if (MaterialTheme.colorScheme.background.luminance() < 0.2f) FRAME_ON_DARK else FRAME_ON_LIGHT
    Box(
        modifier
            .size(width, phoneHeight(screen, width))
            .shadow(PHONE_SHADOW, RoundedCornerShape(PHONE_CORNER))
            .background(frame, RoundedCornerShape(PHONE_CORNER))
            .padding(FRAME),
    ) {
        Image(
            bitmap = screen,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.High,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(PHONE_CORNER - FRAME)),
        )
    }
}

/**
 * A speech bubble with what TalkBack says, its tail pointing down at the phone under it: a quiet card
 * in the theme's inverse colours, no hard border (the owner's taste), a small speaker mark before the
 * words.
 */
@Composable
private fun Bubble(text: String) {
    val fill = MaterialTheme.colorScheme.inverseSurface
    val ink = MaterialTheme.colorScheme.inverseOnSurface
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shape = RoundedCornerShape(BUBBLE_CORNER)
    Column(Modifier.padding(horizontal = SIDE_MARGIN).widthIn(max = BUBBLE_MAX_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .shadow(BUBBLE_SHADOW, shape)
                .background(fill, shape)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpeakerMark(ink, mirrored = rtl)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 27.sp), color = ink)
        }
        Canvas(Modifier.offset(y = (-1).dp).size(TAIL * 1.4f, TAIL)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2, size.height)
                close()
            }
            drawPath(path, fill)
        }
        Spacer(Modifier.height(BUBBLE_GAP))
    }
}

/** A small speaker with two sound waves, pointing toward the text. */
@Composable
private fun SpeakerMark(color: Color, mirrored: Boolean) {
    Canvas(Modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        fun x(v: Float) = if (mirrored) w - v * w else v * w
        val body = Path().apply {
            moveTo(x(0.08f), h * 0.38f)
            lineTo(x(0.26f), h * 0.38f)
            lineTo(x(0.48f), h * 0.18f)
            lineTo(x(0.48f), h * 0.82f)
            lineTo(x(0.26f), h * 0.62f)
            lineTo(x(0.08f), h * 0.62f)
            close()
        }
        drawPath(body, color)
        val stroke = Stroke(width = w * 0.075f)
        for ((i, r) in listOf(0.16f, 0.30f).withIndex()) {
            val sweep = if (i == 0) 70f else 80f
            val centre = Offset(x(0.50f), h / 2)
            drawArc(
                color = color,
                startAngle = (if (mirrored) 180f else 0f) - sweep / 2,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(centre.x - r * w, centre.y - r * w),
                size = androidx.compose.ui.geometry.Size(2 * r * w, 2 * r * w),
                style = stroke,
            )
        }
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/**
 * Draws [content] in a fresh activity of its own and returns the [width] × [height] pixels it drew
 * (by default a screenshot's canvas), read back from the window as the screen shows them (PixelCopy).
 */
fun renderCanvas(width: Int = CANVAS_WIDTH, height: Int = CANVAS_HEIGHT, content: @Composable () -> Unit): Bitmap {
    ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
        val placed = CountDownLatch(1)
        var origin = Offset.Zero
        scenario.onActivity { activity ->
            activity.setContent {
                Box(Modifier.onGloballyPositioned { origin = it.positionInWindow(); placed.countDown() }) { content() }
            }
        }
        check(placed.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "the store canvas was never laid out" }
        // Fonts, the backdrop's offscreen layer and the screen pictures settle in a few frames.
        Thread.sleep(SETTLE_MS)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var result = -1
        val copied = CountDownLatch(1)
        scenario.onActivity { activity ->
            val x = origin.x.toInt()
            val y = origin.y.toInt()
            PixelCopy.request(activity.window, Rect(x, y, x + width, y + height), bitmap, { result = it; copied.countDown() }, Handler(Looper.getMainLooper()))
        }
        check(copied.await(WAIT_SECONDS, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS) { "could not read the store canvas back ($result)" }
        return bitmap
    }
}

/** Play's portrait screenshot, 9:16 (docs/store-listing.md, *Phone screenshots*). */
const val CANVAS_WIDTH = 1080
const val CANVAS_HEIGHT = 1920
const val CANVAS_WIDTH_DP = 400f

private val CAPTION_HEIGHT = 170.dp
private val CAPTION_HEIGHT_OVER_BUBBLE = 128.dp
private val SIDE_MARGIN = 24.dp
private val BOTTOM_MARGIN = 26.dp

private val PHONE_GAP = 14.dp

/** Two phones stand this share of their free height apart. */
private const val STAGGER_SHARE = 0.7f
private val PHONE_CORNER = 22.dp
private val PHONE_SHADOW = 14.dp
private val FRAME = 4.dp
private val FRAME_ON_LIGHT = Color(0xFF1C1C1F)
private val FRAME_ON_DARK = Color(0xFF3A3A40)

private val BUBBLE_MAX_WIDTH = 380.dp
private val BUBBLE_GAP = 6.dp
private val BUBBLE_CORNER = 20.dp
private val BUBBLE_SHADOW = 10.dp
private val TAIL = 18.dp

/** Caption sizes tried, largest first, in sp at 1080 px = 400 dp. */
private val CAPTION_SIZES = listOf(34f, 32f, 30f, 28f, 26f, 24f, 22f)
private const val CAPTION_MAX_LINES = 3
private const val TWO_LINE_MIN_SIZE = 26f

private const val WAIT_SECONDS = 10L
private const val SETTLE_MS = 800L
