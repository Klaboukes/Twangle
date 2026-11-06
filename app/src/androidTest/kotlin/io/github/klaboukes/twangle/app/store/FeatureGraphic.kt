package io.github.klaboukes.twangle.app.store

import android.content.res.Configuration
import android.os.LocaleList
import android.text.TextUtils
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.intl.LocaleList as TextLocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.TwangleName
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.settings.LensLook
import io.github.klaboukes.twangle.feature.tuner.components.Lens
import java.util.Locale

/**
 * The Play Store's feature graphic (docs/store-listing.md, *Icon and feature graphic*), one for each
 * store language: [FEATURE_WIDTH] × [FEATURE_HEIGHT] pixels on [BACKGROUND]'s quiet background. On
 * the start side Twangle's icon and name ([TwangleName], Phase 79: the launcher icon beside the word in
 * Comfortaa; 1.1 showed the icon over the name in Retro's face), and under it [line],
 * what sets Twangle apart in a few words ("It does what you need it to do. And nothing else."), in [locale]'s
 * language, set as the app sets that language (its fonts, right to left for Arabic). On the end side one meter
 * upright, Classic on a dark card, a string brought into tune and held there, in tune's green with its glow (2.0's
 * listing, 2026-11-05: until then three meters fanned out, each card too small for its ♭ and ♯). No note names, so
 * the line is the one text to translate. Everything important keeps clear of the edges, which Play may crop.
 */
@Composable
fun FeatureGraphic(line: String, locale: Locale) {
    val configuration = LocalConfiguration.current
    val localized = remember(configuration, locale) { Configuration(configuration).apply { setLocales(LocaleList(locale)) } }
    val rtl = TextUtils.getLayoutDirectionFromLocale(locale) == android.view.View.LAYOUT_DIRECTION_RTL
    CompositionLocalProvider(
        // At a screenshot's scale (2.7 px a dp), and the phone's own text size left out.
        LocalDensity provides Density(CanvasSpec.PORTRAIT.density, fontScale = 1f),
        LocalConfiguration provides localized,
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        TwangleTheme(darkTheme = true, animate = false) {
            Box(
                Modifier.layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints.fixed(FEATURE_WIDTH, FEATURE_HEIGHT))
                    layout(FEATURE_WIDTH, FEATURE_HEIGHT) { placeable.place(0, 0) }
                },
            ) {
                StyleBackdrop(Modifier.fillMaxSize())
                // The icon and the line start at TEXT_EDGE.
                Box(Modifier.align(Alignment.CenterStart).padding(start = TEXT_EDGE)) { NameAndLine(line, locale) }
                Box(Modifier.align(Alignment.CenterEnd).padding(end = METER_EDGE)) { Meter() }
            }
        }
    }
}

/** The icon and name, the line under it starting where the icon does, a sentence a line. */
@Composable
private fun NameAndLine(line: String, locale: Locale) {
    Column {
        TwangleName(NAME_CAP_HEIGHT)
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
            modifier = Modifier.widthIn(max = TEXT_WIDTH),
        )
    }
}

/**
 * The tuner's meter ([Lens], 2.0 Phases 71 and 78), Classic, the default look, straight on the background as the
 * app draws it (Phase 78's frameless meter; the owner, 2026-11-05, after a grey card behind it): a string in the
 * in-tune zone and held there, the needle and the glow green.
 */
@Composable
private fun Meter() {
    Box(Modifier.size(METER_WIDTH, METER_HEIGHT), contentAlignment = Alignment.Center) {
        // Laid out at its size on a phone and drawn smaller, so its numbers, ♭ and ♯ keep the app's proportions
        // (laid out at the graphic's size, its ♭ and ♯, sized for a phone, ran into the numbers).
        val scale = METER_WIDTH / LENS_WIDTH
        Lens(
            look = LensLook.CLASSIC,
            cents = { 0f },
            inTune = true,
            glow = true,
            animationsOff = true,
            modifier = Modifier
                .requiredSize(LENS_WIDTH, LENS_HEIGHT)
                .graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
}


/** Play's feature graphic, exactly. */
const val FEATURE_WIDTH = 1024
const val FEATURE_HEIGHT = 500

/** About 100 px of the 1024 from the start edge, as Play may crop or cover it (379 dp in all): where the word and the line start. */
private val TEXT_EDGE = 36.dp
/** The name's capitals: the icon about 33 dp and the word 75 dp across, clear of the fan. */
private val NAME_CAP_HEIGHT = 13.dp
private val TEXT_WIDTH = 182.dp
private val LINE_GAP = 10.dp
private const val LINE_SIZE = 14f
private const val LINE_MIN_SIZE = 10f
private const val LINE_ALPHA = 0.88f

/** The meter keeps about 50 px of the 1024 from the end edge; as large as the line's room allows. */
private val METER_EDGE = 22.dp
private val METER_WIDTH = 136.dp
private val METER_HEIGHT = 100.dp

/** The meter's size on a phone held upright, which the graphic shows smaller. */
private val LENS_WIDTH = 330.dp
private val LENS_HEIGHT = 230.dp
