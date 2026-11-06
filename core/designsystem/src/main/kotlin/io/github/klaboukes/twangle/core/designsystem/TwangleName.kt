package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay

/**
 * Twangle's name with its icon beside it (Phase 79; the owner, 2026-11-05: the mark and the name apart, in place
 * of the name on a ringing string with the harmonics behind its letters). The icon is the launcher icon as the
 * home screen shows it, the harmonics on their dark tile ([R.drawable.twangle_icon_plucked], from
 * scripts/launcher-icon.py), so players see one picture everywhere; the word is [R.drawable.twangle_wordmark]
 * (Comfortaa Bold, spaced, from scripts/wordmark.py) in [ink]. Sized by the word's [capHeight], the T's height:
 * the icon [ICON_PER_CAP] times that, its middle level with the middle of the capitals. The icon stays before the
 * word and unmirrored in right-to-left languages too, as a logo does. Says nothing to TalkBack, so the caller names it.
 *
 * [plucked]: the icon's string is plucked as it appears, and again at each tap, as on the opening screen; the
 * word stays still. Still with Android's "Remove animations" on. Unplucked (the store banner), it is at rest.
 */
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun TwangleName(
    capHeight: Dp,
    modifier: Modifier = Modifier,
    ink: Color = MaterialTheme.colorScheme.onBackground,
    plucked: Boolean = false,
) {
    val iconSize = capHeight * ICON_PER_CAP
    val wordHeight = capHeight * (WORD_HEIGHT / WORD_BASELINE)
    val icon = AnimatedImageVector.animatedVectorResource(R.drawable.twangle_icon_plucked)
    var plucks by remember { mutableIntStateOf(0) }
    val still = rememberAnimationsOff()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier.then(if (plucked) Modifier.pointerInput(Unit) { detectTapGestures { plucks++ } } else Modifier),
            verticalAlignment = Alignment.Top,
        ) {
            // A fresh painter for each pluck, so the swing always starts from rest (one turned back would play it backwards).
            key(plucks) {
                var atRest by remember { mutableStateOf(!plucked || still) }
                if (plucked && !still) {
                    LaunchedEffect(Unit) {
                        // The first pluck waits for the page to finish coming in.
                        if (plucks == 0) delay(FIRST_PLUCK_DELAY_MS)
                        atRest = true
                    }
                }
                Image(rememberAnimatedVectorPainter(icon, atEnd = atRest), contentDescription = null, modifier = Modifier.size(iconSize))
            }
            Spacer(Modifier.width(capHeight * GAP_PER_CAP))
            Image(
                painterResource(R.drawable.twangle_wordmark),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ink),
                modifier = Modifier
                    .padding(top = (iconSize - capHeight) / 2)
                    .size(wordHeight * (WORD_WIDTH / WORD_HEIGHT), wordHeight),
            )
        }
    }
}

/**
 * The icon as tall as two and a half capitals, the gap after it half of one: the tile's own margin round the
 * harmonics (a ninth of it each side) adds to it where the tile can't be seen, on Ink dark.
 */
private const val ICON_PER_CAP = 2.5f
private const val GAP_PER_CAP = 0.5f

/** The word's drawing (twangle_wordmark.xml): its width, its height to the g's tail and its baseline from the top. */
private const val WORD_WIDTH = 449.70f
private const val WORD_HEIGHT = 100.80f
private const val WORD_BASELINE = 78.10f

private const val FIRST_PLUCK_DELAY_MS = 250L
