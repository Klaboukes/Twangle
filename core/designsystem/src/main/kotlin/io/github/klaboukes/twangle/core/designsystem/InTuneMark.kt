package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The high-contrast look's in-tune mark (Phase 86, the mock-up's): a disc in the in-tune green, ringed in the ink, with
 * a check in the green's own text colour, set before the word "In tune", so in tune reads by a mark and a word, not by
 * colour alone (colour inversion turns the green pink; many low-vision players see colour poorly). A drawing only:
 * the word says it to TalkBack. Made once per size and colours.
 */
@Composable
fun InTuneMark(modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.styleTokens
    val disc = tokens.inTune
    val check = tokens.onInTune
    val ring = MaterialTheme.colorScheme.onBackground
    Spacer(
        modifier.drawWithCache {
            val radius = size.minDimension / 2f
            val ringWidth = radius * RING_SHARE
            val c = size.center
            val s = radius * CHECK_SHARE
            val path = Path().apply {
                moveTo(c.x - s * 0.45f, c.y + s * 0.02f)
                lineTo(c.x - s * 0.12f, c.y + s * 0.34f)
                lineTo(c.x + s * 0.48f, c.y - s * 0.32f)
            }
            val ringStroke = Stroke(ringWidth)
            val checkStroke = Stroke(radius * CHECK_WIDTH_SHARE, cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind {
                drawCircle(disc, radius - ringWidth / 2f)
                drawCircle(ring, radius - ringWidth / 2f, style = ringStroke)
                drawPath(path, check, style = checkStroke)
            }
        },
    )
}

/** The mark's ring, check size and check line as shares of its radius. */
private const val RING_SHARE = 0.14f
private const val CHECK_SHARE = 1.2f
private const val CHECK_WIDTH_SHARE = 0.24f
