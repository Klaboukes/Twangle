package io.github.klaboukes.twangle.wear

import android.content.res.Resources
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
 * The cents, small under the word, as the phone writes them there (2.0, Phase 75): "12 cents flat", "3 cents
 * sharp", and in tune only how many, "2 cents", since the word says in tune ("0 cents" right on the note before it
 * counts). Laid out so nothing moves while the number changes (owner, 2026-10-30: a line centred as one text
 * shifted sideways with every reading): the number has a slot as wide as the widest one ("99"), in digits of one
 * width, set against the words; the line takes the room of the widest reading of its kind (flat, sharp, or the
 * amount alone) and starts at the same place, so within a kind only the unit's ending can change ("1 cent",
 * "2 cents"). The words are the texts TalkBack says (`wear_spoken_cents_flat`, `wear_spoken_cents_sharp`,
 * `wear_cents`) split at the number, so every language keeps its own order. TalkBack and the tests read the whole
 * line as one text (TalkBack itself hears the dial's spoken layer, which hides this line).
 */
@Composable
fun WatchCents(reading: WatchTunerState.Detected, style: TextStyle, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val numbers = style.merge(TextStyle(fontFeatureSettings = "tnum"))
    val kind = CentsKind.of(reading)
    val room = rememberCentsRoom(numbers, kind, locale)
    val amount = abs(reading.cents)
    val number = digits(amount, locale)
    val whole = centsText(resources, kind, amount, locale)
    val (before, after) = centsWords(whole, number)
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

/** Which way the cents line says the note is off: [FLAT], [SHARP], or only how far ([AMOUNT]: in tune, or 0). */
internal enum class CentsKind {
    FLAT,
    SHARP,
    AMOUNT,
    ;

    companion object {
        fun of(reading: WatchTunerState.Detected): CentsKind = when {
            reading.inTune || reading.cents == 0 -> AMOUNT
            reading.cents < 0 -> FLAT
            else -> SHARP
        }
    }
}

/** The cents line of [kind] for [amount] cents, in [locale]'s digits: "12 cents flat", "2 cents". */
internal fun centsText(resources: Resources, kind: CentsKind, amount: Int, locale: Locale): String = when (kind) {
    CentsKind.FLAT -> resources.getQuantityString(R.plurals.wear_spoken_cents_flat, amount, amount)
    CentsKind.SHARP -> resources.getQuantityString(R.plurals.wear_spoken_cents_sharp, amount, amount)
    CentsKind.AMOUNT -> resources.getQuantityString(R.plurals.wear_cents, amount, digits(amount, locale))
}

/** [amount] in the digits of [locale] as the texts around it write them (Arabic-Indic in Arabic: "١٢"). */
internal fun digits(amount: Int, locale: Locale): String = String.format(locale, "%d", amount)

/** How wide the number's slot and the whole line are at their widest, from 0 to 99 cents. */
private data class CentsRoom(val number: Dp, val line: Dp)

@Composable
private fun rememberCentsRoom(style: TextStyle, kind: CentsKind, locale: Locale): CentsRoom {
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val resources = LocalResources.current
    val density = LocalDensity.current
    return remember(measurer, style, kind, locale, resources, density) {
        fun width(text: String) = if (text.isEmpty()) 0 else measurer.measure(text, style, maxLines = 1).size.width
        var number = 0
        var words = 0
        for (amount in 0..MAX_CENTS) {
            val digits = digits(amount, locale)
            number = maxOf(number, width(digits))
            val (before, after) = centsWords(centsText(resources, kind, amount, locale), digits)
            words = maxOf(words, width(before) + width(after))
        }
        with(density) { CentsRoom(number = number.toDp(), line = (number + words).toDp()) }
    }
}

/** The words before and after the [number] in [text]; a text without it keeps all its words after it. */
internal fun centsWords(text: String, number: String): Pair<String, String> {
    val at = text.indexOf(number)
    return if (at < 0) "" to " $text" else text.substring(0, at) to text.substring(at + number.length)
}

/** The widest reading the slot is measured for (the arc ends at 50; the detector rarely passes 99). */
private const val MAX_CENTS = 99
