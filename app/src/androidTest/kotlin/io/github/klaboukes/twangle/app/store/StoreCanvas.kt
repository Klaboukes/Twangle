package io.github.klaboukes.twangle.app.store

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import androidx.test.core.app.ActivityScenario
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * The size and proportions of one store picture: Play's upright screenshot ([PORTRAIT]: the phone's,
 * and the 10-inch tablet's upright set) or one on its side ([LANDSCAPE]: focus mode, on the phone and
 * the tablet), each with its own dp, the device's own text size left out.
 */
enum class CanvasSpec(
    val width: Int,
    val height: Int,
    /** How many dp the canvas's short side is, which sets every size below. */
    val shortSideDp: Float,
    val captionHeight: Dp,
    val captionHeightOverBubble: Dp,
    val captionGap: Dp,
    val sideMargin: Dp,
    val bottomMargin: Dp,
    val frame: Dp,
    val corner: Dp,
    val bubbleMaxWidth: Dp,
) {
    /** 1080 × 1920 px (9:16), 1080 px = 400 dp. */
    PORTRAIT(1080, 1920, 400f, 170.dp, 128.dp, 0.dp, 24.dp, 26.dp, 4.dp, 22.dp, 380.dp),

    /** 1920 × 1080 px (16:9), 1080 px = 450 dp: the caption in a band above, the screen on its side under it. */
    LANDSCAPE(1920, 1080, 450f, 92.dp, 92.dp, 8.dp, 40.dp, 22.dp, 6.dp, 18.dp, 680.dp),
    ;

    val density: Float get() = minOf(width, height) / shortSideDp
}

/**
 * One app screen as a store picture shows it: [screen] (the screen as it was, without the status bar)
 * and the part of its height that must show, [focus] (fractions from its top: the meter, the note and
 * its pill, say). Drawn as large as its place allows, never larger than the screen's own pixels; where
 * its whole height doesn't fit at the place's width, it is cut to [focus] and as much around it as
 * fits, so the app's words can be read at the size Play shows the picture (Phase 65: a whole phone
 * screen under the caption was 630 px wide, its words too small to read in the listing).
 */
class Window(val screen: ImageBitmap, val focus: ClosedFloatingPointRange<Float> = 0f..1f)

/**
 * One store screenshot as Play shows it: [spec]'s pixels in the app's (quiet) background, the
 * caption in large type above, and below it [rows] of app screens ([Window]), each in a slim dark
 * frame with round corners and a soft shadow. One screen takes the whole space under the caption;
 * rows share its height and the screens of a row its width (the six tiles' grid). Two screens alone
 * in a row, [staggered], stand side by side whole, the second lower, as two phones.
 *
 * Drawn with the app's own theme ([TwangleTheme]), so the caption is set in its display face, the
 * phone's own sans in every script (`StyleTypography`), right to left where the language is. [bubble] (the TalkBack screenshot) is what TalkBack says, in a speech bubble between
 * the caption and the phone, pointing at the phone: drawn here, since a picture can't show speech. [highContrast] draws the canvas in the
 * high-contrast look, as the screen on it.
 */
@Composable
fun StoreCanvas(
    caption: String,
    rows: List<List<Window>>,
    dark: Boolean,
    spec: CanvasSpec = CanvasSpec.PORTRAIT,
    bubble: SpeechBubble? = null,
    staggered: Boolean = false,
    highContrast: Boolean = false,
) {
    CompositionLocalProvider(LocalDensity provides Density(spec.density, fontScale = 1f)) {
        TwangleTheme(darkTheme = dark, highContrast = highContrast, animate = false) {
            Box(
                Modifier.layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints.fixed(spec.width, spec.height))
                    layout(spec.width, spec.height) { placeable.place(0, 0) }
                },
            ) {
                StyleBackdrop(Modifier.fillMaxSize())
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Caption(
                        caption,
                        Modifier
                            .padding(start = spec.sideMargin, end = spec.sideMargin, top = spec.captionGap / 2, bottom = spec.captionGap)
                            .fillMaxWidth()
                            .height(if (bubble == null) spec.captionHeight else spec.captionHeightOverBubble),
                    )
                    if (bubble != null) Bubble(bubble.text, maxWidth = spec.bubbleMaxWidth)
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .padding(bottom = spec.bottomMargin, start = spec.sideMargin, end = spec.sideMargin)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val space = maxWidth
                        val tall = maxHeight
                        val pair = rows.singleOrNull()?.takeIf { staggered && it.size == 2 }
                        if (pair != null) {
                            StaggeredPair(pair, space, tall, spec)
                        } else {
                            val rowHeight = (tall - WINDOW_GAP * (rows.size - 1)) / rows.size
                            Column(verticalArrangement = Arrangement.spacedBy(WINDOW_GAP)) {
                                rows.forEach { row ->
                                    val cellWidth = (space - WINDOW_GAP * (row.size - 1)) / row.size
                                    Row(horizontalArrangement = Arrangement.spacedBy(WINDOW_GAP)) {
                                        row.forEach { window ->
                                            Box(Modifier.size(cellWidth, rowHeight), contentAlignment = Alignment.Center) {
                                                Device(window, cellWidth, rowHeight, spec.frame, spec.corner)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Two whole screens side by side, each about half the width, the second lower: Chords and the Capo helper. */
@Composable
private fun StaggeredPair(windows: List<Window>, space: Dp, tall: Dp, spec: CanvasSpec) {
    val width = (space - WINDOW_GAP) / 2
    val first = windows.first().screen
    val height = (width - spec.frame * 2) * (first.height.toFloat() / first.width) + spec.frame * 2
    val stagger = ((tall - height) * STAGGER_SHARE).coerceAtLeast(0.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(WINDOW_GAP)) {
        windows.forEachIndexed { i, window ->
            Box(Modifier.padding(top = if (i % 2 == 0) 0.dp else stagger, bottom = if (i % 2 == 0) stagger else 0.dp)) {
                Device(window, width, tall - stagger, spec.frame, spec.corner)
            }
        }
    }
}

/** What TalkBack says, drawn as a speech bubble above the phone. */
data class SpeechBubble(val text: String)

/** The caption, as large as fits in three lines, centred in its space. */
@Composable
private fun Caption(caption: String, modifier: Modifier) {
    val text = remember(caption) { koreanWordsKeptWhole(caption) }
    val measurer = rememberTextMeasurer()
    val base = MaterialTheme.typography.headlineLarge
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        // Two sentences ("Strum once. See every string.") get a line each, at the largest size where both fit so (also
        // in the height: the landscape band holds one line at the largest size, two only just), rather than a larger
        // one that breaks a sentence in two ("No / sign-up"). Otherwise the largest size that fits in two lines, else
        // the largest in three: a short caption stays large.
        val (shown, style) = remember(text, base, widthPx, heightPx) {
            fun fits(candidate: String, style: TextStyle, lines: Int, exactly: Boolean = false): Boolean {
                val layout = measurer.measure(candidate, style, constraints = Constraints(maxWidth = widthPx))
                return (if (exactly) layout.lineCount == lines else layout.lineCount <= lines) && layout.size.height <= heightPx
            }
            val sizes = CAPTION_SIZES.map { base.captionStyle(it) }
            val twoLineSizes = sizes.filter { it.fontSize.value >= TWO_LINE_MIN_SIZE }
            val split = sentencesOnTheirOwnLines(text)
            split?.let { lines -> twoLineSizes.firstOrNull { fits(lines, it, 2, exactly = true) }?.let { lines to it } }
                ?: (text to (twoLineSizes.firstOrNull { fits(text, it, 2) } ?: sizes.firstOrNull { fits(text, it, CAPTION_MAX_LINES) } ?: sizes.last()))
        }
        Text(shown, style = style, color = MaterialTheme.colorScheme.onBackground)
    }
}

/**
 * [text] with a word joiner between the letters of each Korean word, so its lines break only at spaces:
 * Korean is written with spaces between words, but Android breaks Hangul between any two syllables
 * ("맞았 / 는지", overnight run 2026-10-18). Other text is returned as it is.
 */
internal fun koreanWordsKeptWhole(text: String): String =
    if (text.none { it in HANGUL_SYLLABLES }) text else text.replace(BETWEEN_TWO_NON_SPACES, WORD_JOINER)

private val HANGUL_SYLLABLES = Char(0xAC00)..Char(0xD7A3)
private val WORD_JOINER = Char(0x2060).toString()

private val BETWEEN_TWO_NON_SPACES = Regex("""(?<=\S)(?=\S)""")

/** [text] with a line break after its first sentence, if it is two sentences; else null. */
internal fun sentencesOnTheirOwnLines(text: String): String? {
    val ends = SENTENCE_ENDS.mapNotNull { end -> text.indexOf(end).takeIf { it in 1 until text.length - end.length }?.let { it + end.length } }
    val cut = ends.minOrNull() ?: return null
    return text.substring(0, cut).trimEnd() + "\n" + text.substring(cut).trimStart()
}

/**
 * A sentence's end inside a caption: the full stop and a space; the ideographic full stop (Chinese, Japanese), which
 * takes no space; the danda (Hindi, Bengali, Marathi), Amharic's ። and Burmese's ။, each with a space; and Khmer's ។,
 * which Khmer may write without one.
 */
private val SENTENCE_ENDS = listOf(". ", "。", "। ", "። ", "။ ", "។")

/** Balanced lines (no single word left on the last one), centred. */
private fun TextStyle.captionStyle(size: Float) =
    copy(fontSize = size.sp, lineHeight = (size * 1.22f).sp, textAlign = TextAlign.Center, lineBreak = LineBreak.Heading)

/**
 * [window]'s screen in a slim, dark frame [frameWidth] wide with round [corner]s and a soft shadow, as
 * large as fits in [maxWidth] × [maxHeight] and never larger than its own pixels: its whole height
 * where that fits, else cut to its focus and as much around it as fits, centred on the focus.
 */
@Composable
private fun Device(window: Window, maxWidth: Dp, maxHeight: Dp, frameWidth: Dp, corner: Dp) {
    val screen = window.screen
    val density = LocalDensity.current
    val innerWidth = with(density) { (maxWidth - frameWidth * 2).toPx() }
    val innerHeight = with(density) { (maxHeight - frameWidth * 2).toPx() }
    val focusTop = window.focus.start * screen.height
    val focusHeight = (window.focus.endInclusive - window.focus.start) * screen.height
    val scale = minOf(innerWidth / screen.width, innerHeight / focusHeight, 1f)
    val shown = minOf(screen.height.toFloat(), innerHeight / scale).toInt()
    val top = (focusTop + focusHeight / 2 - shown / 2f).roundToInt().coerceIn(0, screen.height - shown)
    val frame = if (MaterialTheme.colorScheme.background.luminance() < 0.2f) FRAME_ON_DARK else FRAME_ON_LIGHT
    val width = with(density) { (screen.width * scale).toDp() } + frameWidth * 2
    val height = with(density) { (shown * scale).toDp() } + frameWidth * 2
    Box(
        Modifier
            .size(width, height)
            .shadow(PHONE_SHADOW, RoundedCornerShape(corner))
            .background(frame, RoundedCornerShape(corner))
            .padding(frameWidth),
    ) {
        Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(corner - frameWidth))) {
            drawImage(
                image = screen,
                srcOffset = IntOffset(0, top),
                srcSize = IntSize(screen.width, shown),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.High,
            )
        }
    }
}

/**
 * A speech bubble with what TalkBack says, its tail pointing down at the phone under it: a quiet card
 * in the theme's inverse colours, no hard border (the owner's taste), a small speaker mark before the
 * words.
 */
@Composable
private fun Bubble(text: String, maxWidth: Dp) {
    val fill = MaterialTheme.colorScheme.inverseSurface
    val ink = MaterialTheme.colorScheme.inverseOnSurface
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shape = RoundedCornerShape(BUBBLE_CORNER)
    Column(Modifier.padding(horizontal = SIDE_MARGIN).widthIn(max = maxWidth), horizontalAlignment = Alignment.CenterHorizontally) {
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
 * Draws [content] in a fresh activity of its own and returns the [width] × [height] pixels it drew.
 * Where they fit in the window clear of the navigation bar they are read back as the screen shows them
 * (PixelCopy); a picture larger than that (one on its side, drawn on a phone or tablet held upright; the
 * upright canvas on the tablet, whose three-button bar Android underlays with a light scrim in the
 * window's own pixels, a grey band across every tablet picture's foot, 2026-11-09) is recorded into a
 * graphics layer of its full size instead, as the window can't show it whole.
 */
fun renderCanvas(width: Int = CanvasSpec.PORTRAIT.width, height: Int = CanvasSpec.PORTRAIT.height, content: @Composable () -> Unit): Bitmap {
    ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
        val placed = CountDownLatch(1)
        var origin = Offset.Zero
        var layer: GraphicsLayer? = null
        var window = IntSize.Zero
        var navigation = Insets.NONE
        scenario.onActivity { activity ->
            window = IntSize(activity.window.decorView.width, activity.window.decorView.height)
            navigation = ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars()) ?: Insets.NONE
            activity.setContent {
                val recorded = rememberGraphicsLayer().also { layer = it }
                // Top left in every language: TopStart in a right-to-left one pinned a canvas wider
                // than the window to its right edge, and the picture on its side came out cut off.
                Box(
                    Modifier
                        .wrapContentSize(AbsoluteAlignment.TopLeft, unbounded = true)
                        .onGloballyPositioned { origin = it.positionInWindow(); placed.countDown() }
                        .drawWithContent {
                            recorded.record { this@drawWithContent.drawContent() }
                            drawLayer(recorded)
                        },
                ) { content() }
            }
        }
        check(placed.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "the store canvas was never laid out" }
        // Fonts, the backdrop's offscreen layer and the screen pictures settle in a few frames.
        Thread.sleep(SETTLE_MS)
        val x = origin.x.toInt()
        val y = origin.y.toInt()
        val fits = x >= navigation.left && x + width <= window.width - navigation.right &&
            y + height <= window.height - navigation.bottom
        val copied = CountDownLatch(1)
        var bitmap: Bitmap? = null
        var result = -1
        if (fits) {
            val target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            scenario.onActivity { activity ->
                PixelCopy.request(activity.window, Rect(x, y, x + width, y + height), target, { result = it; copied.countDown() }, Handler(Looper.getMainLooper()))
            }
            check(copied.await(WAIT_SECONDS, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS) { "could not read the store canvas back ($result)" }
            bitmap = target
        } else {
            val scope = MainScope()
            scope.launch {
                val drawn = (layer ?: error("no layer")).toImageBitmap().asAndroidBitmap()
                // A hardware bitmap can't be written as PNG or read pixel by pixel: a software copy.
                bitmap = Bitmap.createBitmap(drawn.copy(Bitmap.Config.ARGB_8888, false), 0, 0, width, height)
                copied.countDown()
            }
            val recorded = copied.await(WAIT_SECONDS, TimeUnit.SECONDS)
            scope.cancel()
            check(recorded) { "could not record the store canvas" }
        }
        return bitmap ?: error("no store canvas")
    }
}

private val SIDE_MARGIN = 24.dp

/** Between the screens of a row, and between rows. */
private val WINDOW_GAP = 14.dp

/** Two phones stand this share of their free height apart. */
private const val STAGGER_SHARE = 0.7f
private val PHONE_SHADOW = 14.dp
private val FRAME_ON_LIGHT = Color(0xFF1C1C1F)
private val FRAME_ON_DARK = Color(0xFF3A3A40)

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
