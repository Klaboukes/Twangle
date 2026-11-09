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
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntRect
import io.github.klaboukes.twangle.core.designsystem.styleTokens

/**
 * The size of one store picture: Play's upright screenshot, 1080 × 1920 px (9:16) with 1080 px = 400 dp, the phone's
 * and the 10-inch tablet's alike, the device's own text size left out. Until the from-scratch set (2026-11-09) focus
 * mode had one on its side too.
 */
enum class CanvasSpec(val width: Int, val height: Int, val shortSideDp: Float) {
    PORTRAIT(1080, 1920, 400f),
    ;

    val density: Float get() = minOf(width, height) / shortSideDp
}

/**
 * A picture's words as `screenshot_captions.txt` gives them, one line a picture: the [headline], then the [lines]
 * under it, `|` between ("It *keeps time*, too.|A metronome in the same app."). Words between asterisks are drawn in
 * the picture's colour ([Accent]). The privacy picture's lines are its three facts ([Moment.Facts]).
 */
class StoreCaption(val headline: String, val lines: List<String>) {
    companion object {
        fun parse(line: String): StoreCaption {
            val parts = line.split('|').map { it.trim() }.filter { it.isNotEmpty() }
            return StoreCaption(parts.first(), parts.drop(1))
        }
    }
}

/** The one colour a picture has beside the app's own: its marked words and its glow. */
enum class Accent { IN_TUNE, FAR_OFF, PRIMARY }

@Composable
private fun Accent.colour(): Color = when (this) {
    Accent.IN_TUNE -> MaterialTheme.styleTokens.inTune
    Accent.FAR_OFF -> MaterialTheme.styleTokens.farOff
    Accent.PRIMARY -> MaterialTheme.colorScheme.primary
}

/** What a picture shows under its words: one moment, drawn large (the from-scratch set, owner 2026-11-09). */
sealed interface Moment {
    /**
     * The part of [screen] that sells it, [area] in its pixels, drawn without a frame on [background] (the screen's own,
     * so the cut doesn't show) as large as fits and never larger than its pixels. [fade] softens its sides, where the
     * tuner's ruler runs on to the screen's edges.
     */
    class Cut(val screen: ImageBitmap, val area: IntRect, val background: Color, val fade: Boolean = false) : Moment

    /** The whole [screen] in a phone's frame on a glow of the picture's colour: the one picture of the whole app. */
    class Phone(val screen: ImageBitmap) : Moment

    /** No screen: the microphone in a disc of the picture's colour and the [facts] under it, each with its mark. */
    class Facts(val facts: List<String>) : Moment
}

/** What TalkBack says, drawn as a speech bubble above the screen. */
data class SpeechBubble(val text: String)

/** An app screen and the part of its height that must show ([focus], fractions from its top); [Device] draws it. */
class Window(val screen: ImageBitmap, val focus: ClosedFloatingPointRange<Float> = 0f..1f)

/**
 * One store screenshot as Play shows it: [spec]'s pixels in Ink dark, the [caption]'s headline in large type at the top
 * with its line under it, and below them the [moment], one thing drawn large (the from-scratch set, owner 2026-11-09;
 * until then each picture was the app's screen in a phone's frame, its words too small to read at Play's size).
 *
 * Drawn with the app's own theme ([TwangleTheme]), so the words are set in its face, the phone's own sans in every
 * script (`StyleTypography`), right to left where the language is. [bubble] (the TalkBack picture) is what TalkBack
 * says, in a speech bubble over the screen: drawn here, since a picture can't show speech. [highContrast] draws the
 * canvas in the high-contrast look, as the screen on it.
 */
@Composable
fun StoreCanvas(
    caption: StoreCaption,
    moment: Moment,
    accent: Accent,
    spec: CanvasSpec = CanvasSpec.PORTRAIT,
    bubble: SpeechBubble? = null,
    highContrast: Boolean = false,
) {
    CompositionLocalProvider(LocalDensity provides Density(spec.density, fontScale = 1f)) {
        TwangleTheme(darkTheme = true, highContrast = highContrast, animate = false) {
            val colour = accent.colour()
            val background = (moment as? Moment.Cut)?.background ?: MaterialTheme.colorScheme.background
            Box(
                Modifier
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints.fixed(spec.width, spec.height))
                        layout(spec.width, spec.height) { placeable.place(0, 0) }
                    }
                    .background(background),
            ) {
                if (moment is Moment.Phone) Glow(colour, Modifier.fillMaxSize())
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(TOP_MARGIN))
                    Headline(caption.headline, colour, Modifier.padding(horizontal = SIDE_MARGIN))
                    if (moment !is Moment.Facts) {
                        caption.lines.forEach { Subline(it, colour, Modifier.padding(start = SIDE_MARGIN, end = SIDE_MARGIN, top = SUBLINE_GAP)) }
                    }
                    if (bubble != null) {
                        Spacer(Modifier.height(CONTENT_GAP))
                        Bubble(bubble.text, maxWidth = BUBBLE_MAX_WIDTH)
                    }
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = if (bubble == null) CONTENT_GAP else 0.dp, bottom = BOTTOM_MARGIN),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (moment) {
                            is Moment.Cut -> Cut(moment, maxWidth, maxHeight)
                            is Moment.Phone -> Device(Window(moment.screen), maxWidth, maxHeight, PHONE_FRAME, PHONE_CORNER)
                            is Moment.Facts -> Facts(moment.facts, colour)
                        }
                    }
                }
            }
        }
    }
}

/** A soft round glow of [colour] behind the phone, a little below the middle. */
@Composable
private fun Glow(colour: Color, modifier: Modifier) {
    Spacer(
        modifier.drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(colour.copy(alpha = GLOW_ALPHA), Color.Transparent),
                    center = Offset(size.width / 2, size.height * GLOW_CENTRE),
                    radius = size.width * GLOW_RADIUS,
                ),
            )
        },
    )
}

/**
 * The headline, as large as fits: two sentences ("Strum once. See every string.") start a line each, so the second,
 * often the coloured one, stands on its own; in at most three lines (two at a smaller size left the line, picture 1,
 * small beside the others). Balanced lines, centred.
 */
@Composable
private fun Headline(headline: String, accent: Color, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val base = MaterialTheme.typography.headlineLarge
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val width = constraints.maxWidth
        val (text, style) = remember(headline, accent, base, width) {
            val text = koreanWordsKeptWhole(sentencesOnTheirOwnLines(headline) ?: headline)
            val styles = HEADLINE_SIZES.map { base.headlineStyle(it) }
            fun fits(style: TextStyle, lines: Int) =
                measurer.measure(marked(text, accent), style, constraints = Constraints(maxWidth = width)).lineCount <= lines
            text to (styles.firstOrNull { fits(it, HEADLINE_MAX_LINES) } ?: styles.last())
        }
        Text(marked(text, accent), style = style, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** A line under the headline, quieter and smaller, as large as fits in two lines. */
@Composable
private fun Subline(line: String, accent: Color, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val base = MaterialTheme.typography.titleLarge
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val width = constraints.maxWidth
        val (text, style) = remember(line, accent, base, width) {
            val text = koreanWordsKeptWhole(line)
            val styles = SUBLINE_SIZES.map { base.sublineStyle(it) }
            text to (
                styles.firstOrNull { measurer.measure(marked(text, accent), it, constraints = Constraints(maxWidth = width)).lineCount <= 2 }
                    ?: styles.last()
                )
        }
        Text(marked(text, accent), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** [text] with the words between asterisks in [accent], the asterisks left out. */
internal fun marked(text: String, accent: Color): AnnotatedString = buildAnnotatedString {
    text.split('*').forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(color = accent)) { append(part) } else append(part)
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
 * which Khmer may write without one. The coloured words' closing asterisk may come between the stop and the space.
 */
private val SENTENCE_ENDS = listOf(". ", ".* ", "。", "。*", "। ", "।* ", "። ", "።* ", "။ ", "။* ", "។", "។*")

/** The headline: heavy, balanced lines (no single word left on the last one), centred. */
private fun TextStyle.headlineStyle(size: Float) = copy(
    fontSize = size.sp,
    lineHeight = (size * 1.14f).sp,
    fontWeight = FontWeight.ExtraBold,
    textAlign = TextAlign.Center,
    lineBreak = LineBreak.Heading,
)

private fun TextStyle.sublineStyle(size: Float) = copy(
    fontSize = size.sp,
    lineHeight = (size * 1.22f).sp,
    fontWeight = FontWeight.SemiBold,
    textAlign = TextAlign.Center,
    lineBreak = LineBreak.Heading,
)

/** [cut]'s area of its screen, as large as fits in [maxWidth] × [maxHeight] and never larger than its pixels, unframed. */
@Composable
private fun Cut(cut: Moment.Cut, maxWidth: Dp, maxHeight: Dp) {
    val density = LocalDensity.current
    val area = cut.area
    val scale = with(density) { minOf(maxWidth.toPx() / area.width, maxHeight.toPx() / area.height, 1f) }
    val width = with(density) { (area.width * scale).toDp() }
    val height = with(density) { (area.height * scale).toDp() }
    val faded = if (!cut.fade) {
        Modifier
    } else {
        Modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, FADE to Color.Black, 1f - FADE to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            }
    }
    Canvas(Modifier.size(width, height).then(faded)) {
        drawImage(
            image = cut.screen,
            srcOffset = IntOffset(area.left, area.top),
            srcSize = IntSize(area.width, area.height),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }
}

/** The microphone in a glowing disc of [colour], and under it the [facts], each after its mark (no account, no internet, the microphone). */
@Composable
private fun Facts(facts: List<String>, colour: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(MIC_DISC * 2)
                .drawBehind { drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = GLOW_ALPHA), Color.Transparent))) },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(MIC_DISC).background(colour.copy(alpha = DISC_ALPHA), CircleShape), contentAlignment = Alignment.Center) {
                Mark(MIC_MARK, colour, MIC_MARK_SIZE)
            }
        }
        Spacer(Modifier.height(FACTS_GAP))
        Column(Modifier.widthIn(max = FACTS_WIDTH), verticalArrangement = Arrangement.spacedBy(FACT_GAP)) {
            facts.zip(FACT_MARKS).forEach { (fact, mark) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mark(mark, colour, FACT_MARK_SIZE)
                    Spacer(Modifier.width(FACT_MARK_GAP))
                    Text(
                        koreanWordsKeptWhole(fact),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

/** A line drawing in a 24-unit square ([pathData], SVG's path syntax), stroked in [colour] at [size]. */
@Composable
private fun Mark(pathData: String, colour: Color, size: Dp) {
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    Canvas(Modifier.size(size)) {
        scale(this.size.width / MARK_UNITS, pivot = Offset.Zero) {
            drawPath(path, colour, style = Stroke(width = MARK_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A microphone, a person struck through (no account) and Wi-Fi struck through (no internet), in 24 units. */
private const val MIC_MARK = "M9 6a3 3 0 0 1 6 0v5a3 3 0 0 1 -6 0zM5 11a7 7 0 0 0 14 0M12 18v3"
private const val NO_ACCOUNT_MARK = "M8 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0M4 21c1-4 4.5-6 8-6s7 2 8 6M3 3l18 18"
private const val NO_INTERNET_MARK = "M2 8.5a15 15 0 0 1 20 0M5 12a10 10 0 0 1 14 0M8.5 15.5a5 5 0 0 1 7 0M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M3 3l18 18"

/** The facts' marks in the captions' order: no sign-up, no internet, nothing recorded or sent. */
private val FACT_MARKS = listOf(NO_ACCOUNT_MARK, NO_INTERNET_MARK, MIC_MARK)
private const val MARK_UNITS = 24f
private const val MARK_STROKE = 1.8f

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

/** The words' margins at the sides and the top, in dp at 1080 px = 400 dp (the mock-up's 50 and about 120 px). */
private val SIDE_MARGIN = 20.dp
private val TOP_MARGIN = 44.dp
private val SUBLINE_GAP = 8.dp

/** Between the words (or the bubble) and the moment under them, and under the moment. */
private val CONTENT_GAP = 22.dp
private val BOTTOM_MARGIN = 24.dp

/** Headline sizes tried, largest first, in sp at 1080 px = 400 dp, the first that fits in three lines. */
private val HEADLINE_SIZES = listOf(36f, 34f, 32f, 30f, 28f, 26f, 24f, 22f)
private const val HEADLINE_MAX_LINES = 3
private val SUBLINE_SIZES = listOf(21f, 20f, 19f, 18f, 17f, 16f)

/** The whole app's phone: a slim frame with round corners and a soft shadow. */
private val PHONE_FRAME = 4.dp
private val PHONE_CORNER = 24.dp
private val PHONE_SHADOW = 14.dp
private val FRAME_ON_LIGHT = Color(0xFF1C1C1F)
private val FRAME_ON_DARK = Color(0xFF3A3A40)

/** The picture's colour behind the phone and the microphone: how strong at its heart, where, and how far it reaches. */
private const val GLOW_ALPHA = 0.36f
private const val GLOW_CENTRE = 0.62f
private const val GLOW_RADIUS = 0.8f

/** The tuner's cut fades out over this share of its width at each side. */
private const val FADE = 0.06f

/** The privacy picture: the microphone's disc, its facts' marks and spacing. */
private val MIC_DISC = 112.dp
private val MIC_MARK_SIZE = 62.dp
private const val DISC_ALPHA = 0.16f
private val FACTS_GAP = 24.dp
private val FACTS_WIDTH = 340.dp
private val FACT_GAP = 30.dp
private val FACT_MARK_SIZE = 38.dp
private val FACT_MARK_GAP = 18.dp

private val BUBBLE_MAX_WIDTH = 360.dp
private val BUBBLE_GAP = 6.dp
private val BUBBLE_CORNER = 20.dp
private val BUBBLE_SHADOW = 10.dp
private val TAIL = 18.dp

private const val WAIT_SECONDS = 10L
private const val SETTLE_MS = 800L
