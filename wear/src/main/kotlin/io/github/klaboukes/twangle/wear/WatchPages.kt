package io.github.klaboukes.twangle.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.foundation.pager.rememberPagerState
import kotlin.math.abs

/** The watch app's pages, in their order: the tuner, then a swipe left, the metronome (Phase 80). */
enum class WatchPage { TUNER, METRONOME }

/**
 * The two pages side by side (Phase 80): the tuner, and a swipe left the metronome, with two page dots at
 * the bottom edge. Only the page shown is composed once a swipe has settled, so the tuner's microphone is
 * let go while the metronome shows (after the tuner's 5 s grace, Hard rule 4) and taken again when it comes
 * back. [opening] is the page to show first; a later [request] (the activity opened again for a page) moves
 * there and is handed back through [onRequestDone].
 */
@Composable
fun WatchPages(
    opening: WatchPage,
    request: WatchPage?,
    onRequestDone: () -> Unit,
    tuner: @Composable () -> Unit,
    metronome: @Composable () -> Unit,
) {
    val pager = rememberPagerState(initialPage = opening.ordinal) { WatchPage.entries.size }
    LaunchedEffect(request) {
        if (request != null) {
            pager.scrollToPage(request.ordinal)
            onRequestDone()
        }
    }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize().testTag(WatchPageDots.PAGER_TAG)) { page ->
            when (WatchPage.entries[page]) {
                WatchPage.TUNER -> tuner()
                WatchPage.METRONOME -> metronome()
            }
        }
        PageDots(pager)
    }
}

/** The two dots at the bottom edge, the shown page's in ink, easing across while a swipe runs. Drawn only, not for TalkBack. */
@Composable
private fun PageDots(pager: PagerState) {
    Canvas(Modifier.fillMaxSize().testTag(WatchPageDots.TAG).clearAndSetSemantics {}) {
        // Read while drawing, so a swipe redraws the dots without recomposing.
        val at = pager.currentPage + pager.currentPageOffsetFraction
        val d = size.minDimension
        val y = center.y + d * WatchPageDots.Y
        val gap = d * WatchPageDots.GAP
        for (i in WatchPage.entries.indices) {
            val on = (1f - abs(at - i)).coerceIn(0f, 1f)
            drawCircle(lerp(WatchColors.dotOff, WatchColors.ink, on), d * WatchPageDots.RADIUS, Offset(center.x + (i - 0.5f) * 2 * gap, y))
        }
    }
}

/** The page dots' place and size (fractions of the screen's smaller side, from its middle), the mock-up's. */
object WatchPageDots {
    const val TAG = "watchPageDots"
    const val PAGER_TAG = "watchPager"
    const val Y = 0.462f
    const val GAP = 0.022f
    const val RADIUS = 0.011f
}
