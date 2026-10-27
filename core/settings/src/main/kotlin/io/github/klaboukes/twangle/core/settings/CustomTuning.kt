package io.github.klaboukes.twangle.core.settings

import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A tuning the player made: a [name] they typed, the [instrument] it is for and one note per
 * string ([notes], in instrument order like [Tuning.strings]).
 *
 * [id] is made once when the tuning is first saved (see [SettingsRepository.addCustomTuning]) and
 * never changes, so editing the name or notes keeps the tuning picked. It always starts with
 * [ID_PREFIX], which no preset id does. [tuning] is what the tuner compares notes with; its id is
 * [id] and it has no spelling of its own, so the sharps/flats setting decides how it is written.
 */
data class CustomTuning(
    val id: String,
    val name: String,
    val instrument: Instrument,
    val notes: List<Note>,
) {
    init {
        require(id.startsWith(ID_PREFIX) && id.length > ID_PREFIX.length && id.none { it in RESERVED }) {
            "Custom tuning id '$id' must start with '$ID_PREFIX'"
        }
        require(name.isNotEmpty() && name == normalizeName(name)) { "Custom tuning name '$name' isn't normalized" }
        require(notes.size == instrument.stringCount) {
            "Custom tuning has ${notes.size} notes, '${instrument.id}' has ${instrument.stringCount} strings"
        }
        require(notes.all { it.midi in MIDI_RANGE }) { "Custom tuning notes must be MIDI $MIDI_RANGE" }
    }

    /** The tuning the tuner uses, with this tuning's [id] and the instrument's octave strings ([Instrument.octaveCourses]). */
    val tuning: Tuning = Tuning(id, notes, octaveCourses = instrument.octaveCourses)

    companion object {
        /** Every custom tuning id starts with this; preset ids never do. */
        const val ID_PREFIX = "custom_"

        /** The longest name kept; a longer one is cut off. */
        const val MAX_NAME_LENGTH = 40

        /** Notes a string may be set to: the MIDI range, far wider than any instrument needs. */
        val MIDI_RANGE = 0..127

        // Characters the saved form uses as separators; an id may not contain them.
        private const val RESERVED = "|,\n"

        /**
         * [raw] as it is saved and compared: trimmed, every run of spaces, tabs or line breaks
         * made one space, and cut to [MAX_NAME_LENGTH]. Empty means there is no usable name. The
         * editor's checks for empty and duplicate names should compare these.
         */
        fun normalizeName(raw: String): String =
            raw.trim().replace(WHITESPACE, " ").take(MAX_NAME_LENGTH).trimEnd()

        private val WHITESPACE = Regex("\\s+")
    }
}

/**
 * How [CustomTuning]s are saved in one DataStore string: one line per tuning, in the player's
 * order, as `id|instrument id|midi,midi,…|name` with the name URL-encoded (so it holds no `|` or
 * line break). Fields added later go after the name; readers ignore fields they don't know.
 *
 * Reading is forgiving: a line that can't be read (unknown instrument, wrong number of notes, a
 * note out of range, no name, a repeated id) is skipped and the others are kept, so damaged data
 * costs at most those tunings and the presets always remain.
 */
internal object CustomTuningCodec {
    private const val FIELD = '|'
    private const val NOTE = ','
    private const val LINE = '\n'
    private const val CHARSET = "UTF-8"

    fun encode(tunings: List<CustomTuning>): String = tunings.joinToString(LINE.toString()) { tuning ->
        listOf(
            tuning.id,
            tuning.instrument.id,
            tuning.notes.joinToString(NOTE.toString()) { it.midi.toString() },
            URLEncoder.encode(tuning.name, CHARSET),
        ).joinToString(FIELD.toString())
    }

    fun decode(saved: String?): List<CustomTuning> {
        if (saved.isNullOrEmpty()) return emptyList()
        val tunings = mutableListOf<CustomTuning>()
        for (line in saved.split(LINE)) {
            val tuning = decodeLine(line) ?: continue
            if (tunings.none { it.id == tuning.id }) tunings += tuning
        }
        return tunings
    }

    private fun decodeLine(line: String): CustomTuning? {
        val fields = line.split(FIELD)
        if (fields.size < 4) return null
        val instrument = Instruments.byId(fields[1]) ?: return null
        val notes = fields[2].split(NOTE).map { Note(it.toIntOrNull() ?: return null) }
        val name = try {
            CustomTuning.normalizeName(URLDecoder.decode(fields[3], CHARSET))
        } catch (_: IllegalArgumentException) {
            return null
        }
        return try {
            CustomTuning(fields[0], name, instrument, notes)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
