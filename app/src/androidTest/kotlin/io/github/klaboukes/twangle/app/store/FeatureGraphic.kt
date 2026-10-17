package io.github.klaboukes.twangle.app.store

import android.content.res.Resources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.feature.tuner.components.MeterFrame
import io.github.klaboukes.twangle.core.designsystem.R as DesignR

/**
 * The Play Store's feature graphic (docs/store-listing.md, *Icon and feature graphic*):
 * [FEATURE_WIDTH] × [FEATURE_HEIGHT] pixels in Contemporary, light (as screenshot 1), on the style's
 * quiet background. On the left the store [icon] with Play's round corners and a soft shadow, the
 * name "Twangle" under it in the style's display face; on the right the tuner's own meter
 * ([MeterFrame], the default look) holding a note in tune, its needle in the "in tune" colour and its
 * calm glow lit. No other words, so one picture serves every language: the meter's small unit label
 * ("CENTS", translated in the app) is left out here. Everything keeps well clear of the edges, which
 * Play may crop or cover.
 */
@Composable
fun FeatureGraphic(icon: ImageBitmap) {
    // At a screenshot's scale (2.7 px a dp), and the phone's own text size left out.
    CompositionLocalProvider(LocalDensity provides Density(CANVAS_WIDTH / CANVAS_WIDTH_DP, fontScale = 1f)) {
        TwangleTheme(style = FEATURE_STYLE, darkTheme = false, animate = false) {
            Box(
                Modifier.layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints.fixed(FEATURE_WIDTH, FEATURE_HEIGHT))
                    layout(FEATURE_WIDTH, FEATURE_HEIGHT) { placeable.place(0, 0) }
                },
            ) {
                StyleBackdrop(Modifier.fillMaxSize(), quiet = true)
                Row(
                    Modifier.fillMaxSize().padding(horizontal = EDGE),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        Text(
                            APP_NAME,
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = NAME_SIZE.sp, lineHeight = (NAME_SIZE * 1.2f).sp),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    WithoutMeterLabel {
                        MeterFrame(
                            look = MeterLook.DEFAULT,
                            cents = { 0f },
                            inTune = true,
                            glow = true,
                            effect = InTuneEffect.DEFAULT,
                            animationsOff = true,
                            reception = { 1f },
                            modifier = Modifier.width(METER_WIDTH),
                        )
                    }
                }
            }
        }
    }
}

/**
 * [content] with the meter's unit label ("CENTS", "CENT", "سنت" …) as an empty text, so the picture
 * has no words to translate.
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

/** As screenshot 1. */
private val FEATURE_STYLE = VisualStyle.CONTEMPORARY

/** The name is the app's own, the same in every language. */
private const val APP_NAME = "Twangle"

private val EDGE = 30.dp
private val ICON_SIZE = 64.dp

/** Play rounds its icons' corners by a fifth of their size. */
private const val ICON_CORNER_PERCENT = 20
private val ICON_SHADOW = 8.dp
private val NAME_GAP = 12.dp
private const val NAME_SIZE = 32f
private val METER_WIDTH = 176.dp
