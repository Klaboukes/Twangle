package io.github.klaboukes.twangle.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.material3.Text
import java.util.Locale
import kotlin.math.abs

/**
 * "+12 cents" under the note, laid out so nothing moves while the number changes (owner, 2026-10-30:
 * centred as one text, the line shifted sideways with every reading). The number has a slot as wide
 * as the widest one ("−99"), in digits of one width, set against the unit; the line takes the room of
 * its widest reading and starts at the same place, so only the unit's ending can change ("1 cent",
 * "2 cents"). The words come from `wear_cents`, split at the number, so every language keeps its own
 * order. TalkBack and the tests read the whole line as one text.
 */
@Composable
fun WatchCents(cents: Int, style: TextStyle, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val numbers = style.merge(TextStyle(fontFeatureSettings = "tnum"))
    val room = rememberCentsRoom(numbers, locale)
    val number = WatchNoteName.signedCents(cents, locale)
    val (before, after) = centsWords(resources.getQuantityString(R.plurals.wear_cents, abs(cents), NUMBER_MARK))
    val whole = before + number + after
    Box(
        modifier.width(room.line).clearAndSetSemantics { text = AnnotatedString(whole) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row {
            if (before.isNotEmpty()) Text(before, style = style, maxLines = 1)
            Text(number, Modifier.width(room.number), style = numbers, textAlign = TextAlign.End, maxLines = 1)
            if (after.isNotEmpty()) Text(after, style = style, maxLines = 1)
        }
    }
}

/** How wide the number's slot and the whole line are at their widest, from −99 to +99 cents. */
private data class CentsRoom(val number: Dp, val line: Dp)

@Composable
private fun rememberCentsRoom(style: TextStyle, locale: Locale): CentsRoom {
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val resources = LocalResources.current
    val density = LocalDensity.current
    return remember(measurer, style, locale, resources, density) {
        fun width(text: String) = if (text.isEmpty()) 0 else measurer.measure(text, style, maxLines = 1).size.width
        var number = 0
        for (cents in -MAX_CENTS..MAX_CENTS) number = maxOf(number, width(WatchNoteName.signedCents(cents, locale)))
        var words = 0
        for (amount in 0..MAX_CENTS) {
            val (before, after) = centsWords(resources.getQuantityString(R.plurals.wear_cents, amount, NUMBER_MARK))
            words = maxOf(words, width(before) + width(after))
        }
        with(density) { CentsRoom(number = number.toDp(), line = (number + words).toDp()) }
    }
}

/** The words before and after the number in [text], which has [NUMBER_MARK] in the number's place. */
internal fun centsWords(text: String): Pair<String, String> {
    val at = text.indexOf(NUMBER_MARK)
    return if (at < 0) "" to " $text" else text.substring(0, at) to text.substring(at + NUMBER_MARK.length)
}

/** Stands in for the number while the words around it are found: a private-use sign no language writes. */
private const val NUMBER_MARK = ""

/** The widest reading the slot is measured for (the scale ends at 50; the detector rarely passes 99). */
private const val MAX_CENTS = 99
