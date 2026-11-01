package io.github.klaboukes.twangle.app.store

import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import android.text.TextUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.intl.LocaleList as TextLocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.StyleBackdropPreview
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.feature.tuner.components.MeterFrame
import java.util.Locale
import io.github.klaboukes.twangle.core.designsystem.R as DesignR

/**
 * The Play Store's feature graphic (docs/store-listing.md, *Icon and feature graphic*), one for each
 * store language: [FEATURE_WIDTH] × [FEATURE_HEIGHT] pixels on [BACKGROUND]'s quiet background. On
 * the start side the store [icon] beside the name "Twangle" in Retro's display face (the icon's
 * mustard and Retro's round letters belong together), and under them [line], what sets Twangle apart
 * in a few words ("Precise to a cent. Fully offline."), in [locale]'s language, set as the app sets
 * that language (its fonts, right to left for Arabic). On the end side three of the six styles'
 * meters fanned out ([FAN]), each its own style's card, look and colours, holding a note in tune with
 * its calm glow. The meters' small unit label ("CENTS") is left out, as are note names, so the line is
 * the one text to translate. Everything important keeps clear of the edges, which Play may crop.
 */
@Composable
fun FeatureGraphic(icon: ImageBitmap, line: String, locale: Locale) {
    val configuration = LocalConfiguration.current
    val localized = remember(configuration, locale) { Configuration(configuration).apply { setLocales(LocaleList(locale)) } }
    val rtl = TextUtils.getLayoutDirectionFromLocale(locale) == android.view.View.LAYOUT_DIRECTION_RTL
    CompositionLocalProvider(
        // At a screenshot's scale (2.7 px a dp), and the phone's own text size left out.
        LocalDensity provides Density(CanvasSpec.PORTRAIT.density, fontScale = 1f),
        LocalConfiguration provides localized,
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        TwangleTheme(style = BACKGROUND, darkTheme = true, animate = false) {
            Box(
                Modifier.layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints.fixed(FEATURE_WIDTH, FEATURE_HEIGHT))
                    layout(FEATURE_WIDTH, FEATURE_HEIGHT) { placeable.place(0, 0) }
                },
            ) {
                StyleBackdrop(Modifier.fillMaxSize(), quiet = true)
                Box(Modifier.align(Alignment.CenterStart).padding(start = EDGE)) { NameAndLine(icon, line, locale) }
                // The fan is decoration: it may come nearer the edge than the words.
                Box(Modifier.align(Alignment.CenterEnd).padding(end = FAN_EDGE)) { Fan() }
            }
        }
    }
}

/** The icon over the wordmark, the line under them, a sentence a line. */
@Composable
private fun NameAndLine(icon: ImageBitmap, line: String, locale: Locale) {
    Column(Modifier.widthIn(max = TEXT_WIDTH)) {
        Image(
            bitmap = icon,
            contentDescription = null,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .size(ICON_SIZE)
                .shadow(ICON_SHADOW, RoundedCornerShape(ICON_CORNER_PERCENT))
                .clip(RoundedCornerShape(ICON_CORNER_PERCENT)),
        )
        Spacer(Modifier.height(NAME_GAP))
        val ink = MaterialTheme.colorScheme.onBackground
        // The name in Retro's face, the same in every language (always Latin letters): the theme is
        // told English, or it would give a language Retro's face lacks (Vietnamese) the phone's font.
        val configuration = LocalConfiguration.current
        val english = remember(configuration) { Configuration(configuration).apply { setLocales(LocaleList(Locale.ENGLISH)) } }
        CompositionLocalProvider(LocalConfiguration provides english) {
            TwangleTheme(style = VisualStyle.RETRO, darkTheme = true, animate = false) {
                Text(
                    APP_NAME,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = NAME_SIZE.sp,
                        lineHeight = (NAME_SIZE * 1.15f).sp,
                        localeList = TextLocaleList("en"),
                    ),
                    color = ink,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(LINE_GAP))
        Text(
            remember(line) { sentencesOnTheirOwnLines(line) ?: line },
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = LINE_SIZE.sp,
                lineHeight = 1.3.em,
                textAlign = TextAlign.Start,
                // So Han characters take the language's own shapes (Traditional or Simplified).
                localeList = TextLocaleList(locale.toLanguageTag()),
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = LINE_ALPHA),
            maxLines = 2,
            // A long language's line a little smaller, never cut off.
            autoSize = TextAutoSize.StepBased(minFontSize = LINE_MIN_SIZE.sp, maxFontSize = LINE_SIZE.sp, stepSize = 0.5.sp),
        )
    }
}

/** Three styles' meters fanned out, the middle one in front. */
@Composable
private fun Fan() {
    Box(Modifier.size(FAN_WIDTH, FAN_HEIGHT), contentAlignment = Alignment.Center) {
        FAN.forEachIndexed { i, card ->
            val side = i - FAN.size / 2
            MeterCard(
                card,
                Modifier
                    .zIndex(if (side == 0) 1f else 0f)
                    // Not mirrored right to left: the cards' tilt isn't, and the fan should open upward.
                    .absoluteOffset(x = CARD_SPREAD * side, y = CARD_DROP * kotlin.math.abs(side))
                    .rotate(CARD_TILT * side),
            )
        }
    }
}

/** One style's meter on a card of that style's own background, holding a note in tune. */
@Composable
private fun MeterCard(card: FanCard, modifier: Modifier) {
    TwangleTheme(style = card.style, darkTheme = card.dark, animate = false) {
        val shape = RoundedCornerShape(CARD_CORNER)
        Box(
            modifier
                .size(CARD_WIDTH, CARD_HEIGHT)
                .shadow(CARD_SHADOW, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            StyleBackdropPreview(Modifier.matchParentSize())
            WithoutMeterLabel {
                MeterFrame(
                    look = card.look,
                    cents = { 0f },
                    inTune = true,
                    glow = true,
                    effect = InTuneEffect.DEFAULT,
                    animationsOff = true,
                    reception = { 1f },
                    modifier = Modifier.width(CARD_WIDTH - CARD_PADDING * 2),
                )
            }
        }
    }
}

/** One card of the fan: a style, light or dark, and a meter look. */
private data class FanCard(val style: VisualStyle, val dark: Boolean, val look: MeterLook)

/**
 * [content] with the meter's unit label ("CENTS", "CENT", "سنت" …) as an empty text, so the meters
 * have no words to translate.
 */
@Composable
private fun WithoutMeterLabel(content: @Composable () -> Unit) {
    val resources = LocalResources.current
    val unlabelled = remember(resources) {
        @Suppress("DEPRECATION")
        object : Resources(resources.assets, resources.displayMetrics, resources.configuration) {
            override fun getString(id: Int): String = if (id == DesignR.string.meter_cents_label) "" else resources.getString(id)
        }
    }
    CompositionLocalProvider(LocalResources provides unlabelled, content = content)
}

/** Play's feature graphic, exactly. */
const val FEATURE_WIDTH = 1024
const val FEATURE_HEIGHT = 500

/** The calm, dark ground the cards and the mustard icon stand out on. */
private val BACKGROUND = VisualStyle.CONTEMPORARY

/** Left to right: the middle one in front, light between two darks. */
private val FAN = listOf(
    FanCard(VisualStyle.FUTURISTIC, dark = true, look = MeterLook.LOUPE),
    FanCard(VisualStyle.CLASSIC, dark = false, look = MeterLook.CLASSIC),
    FanCard(VisualStyle.SPACE_AGE, dark = true, look = MeterLook.POINTERS),
)

/** The name is the app's own, the same in every language. */
private const val APP_NAME = "Twangle"

/** About 100 px of the 1024 from the start edge, as Play may crop or cover it (379 dp in all). */
private val EDGE = 36.dp
private val TEXT_WIDTH = 124.dp
private val ICON_SIZE = 40.dp

/** Play rounds its icons' corners by a fifth of their size. */
private const val ICON_CORNER_PERCENT = 20
private val ICON_SHADOW = 6.dp
private val NAME_GAP = 8.dp
private const val NAME_SIZE = 30f
private val LINE_GAP = 10.dp
private const val LINE_SIZE = 14f
private const val LINE_MIN_SIZE = 11f
private const val LINE_ALPHA = 0.88f

private val FAN_EDGE = 20.dp
private val FAN_WIDTH = 196.dp
private val FAN_HEIGHT = 140.dp
private val CARD_WIDTH = 94.dp
private val CARD_HEIGHT = 76.dp
private val CARD_PADDING = 8.dp
private val CARD_CORNER = 12.dp
private val CARD_SHADOW = 10.dp
private val CARD_SPREAD = 50.dp
private val CARD_DROP = 7.dp
private const val CARD_TILT = 9f
