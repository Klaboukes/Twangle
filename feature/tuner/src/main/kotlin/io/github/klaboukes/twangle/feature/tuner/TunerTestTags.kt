package io.github.klaboukes.twangle.feature.tuner

/**
 * Test tags for UI tests and the Baseline Profile generator (`:baselineprofile`), which finds them as
 * resource ids because `MainActivity` turns on `testTagsAsResourceId`. Tags don't change with the
 * app's language, unlike the visible text.
 */
object TunerTestTags {
    /** A microphone error's message in the tuner's place (a polite live region, so TalkBack says it as it comes). */
    const val ERROR = "tuner_error"

    /**
     * The lens (2.0, Phase 71): its box, inside the room for its glow. TalkBack skips it; the tag lets UI tests
     * find where it sits, and whether it is quiet (`LensQuiet`).
     */
    const val GAUGE = "tuner_gauge"

    /**
     * The ruler at the top (2.0, Phase 71): the chromatic scale under its pointer. TalkBack skips it; UI tests read
     * the note it shows and whether it is quiet from its semantics (`RulerNote`, `RulerQuiet`).
     */
    const val RULER = "tuner_ruler"

    /**
     * The word under the lens (2.0, Phase 71: Tighten, Loosen, In tune, Too low, Too high, Play a string) with the
     * cents under it: [WORD] is the block TalkBack speaks (its description is the spoken reading), [WORD_TEXT] the
     * word as drawn and [WORD_CENTS] the small line under it (the cents, or a tip about the sound).
     */
    const val WORD = "tuner_word"
    const val WORD_TEXT = "tuner_word_text"
    const val WORD_CENTS = "tuner_word_cents"

    /** The optional signal bars by the ruler's heard note (in the chip row while a tool's panel takes the lens's place), and the figures panel a tap on them opens. */
    const val SIGNAL_BARS = "tuner_signal_bars"
    const val SIGNAL_FIGURES = "tuner_signal_figures"

    /** The signal bars as drawn, in their small slot; [SIGNAL_BARS] is the 48 dp button around them. */
    const val SIGNAL_BARS_ICON = "tuner_signal_bars_icon"

    /** The picker sheet, "What are you tuning?", opened from the picker chip. */
    const val PICKER_PANEL = "tuner_picker_panel"

    /**
     * The picker chip at the top of the tuner ("Guitar · Standard ▾", "Any note ▾"), which opens the sheet: on its own
     * line under the top row (Phase 77), in the row's middle on a phone on its side.
     */
    const val PICKER_CHIP = "tuner_picker_chip"

    /** The tab's name in the middle of the tuner's top row ("Tune", Phase 77), over the picker chip's line. */
    const val TITLE = "tuner_title"

    /** The sheet's Any note switch at its bottom (the whole row toggles it). */
    const val ANY_NOTE = "tuner_any_note"

    /** A 12-string's small "8" on a string button with an octave string (TalkBack has it in the button's name). */
    const val OCTAVE_MARK = "tuner_octave_mark"

    /** The quiet line under an instrument's tunings in the panel saying what the tuner can't do for it (the 12-string's). */
    const val PICKER_NOTE = "tuner_picker_note"

    /** The panel's "New tuning" row, which opens the tuning editor. */
    const val NEW_TUNING = "tuner_new_tuning"

    /** The panel's "Lower by" row at its bottom, its amount, and its down and up buttons. */
    const val LOWER_BY = "tuner_lower_by"
    const val LOWERED_AMOUNT = "tuner_lowered_amount"
    const val LOWER_MORE = "tuner_lower_more"
    const val LOWER_LESS = "tuner_lower_less"

    /** The dialog that asks before one of the player's own tunings is deleted. */
    const val DELETE_TUNING_DIALOG = "tuner_delete_tuning_dialog"

    /** One of the player's own tunings in the panel, by its id, and its ⋯ button (Edit, Delete). */
    fun customTuning(id: String): String = "tuner_custom_tuning_$id"
    fun customTuningMenu(id: String): String = "tuner_custom_tuning_menu_$id"

    /** Focus mode's full-screen tuner (the wide layout without its controls, or the 12th-fret check's panel), which shows the controls when tapped. */
    const val COMPACT = "tuner_compact"

    /** The wide layout (2.0, Phase 73): a window wider than tall, the ruler, the lens and the word, with the pegs or in focus mode without. */
    const val WIDE = "tuner_wide"

    /** In focus mode's wide layout, the string heard (or locked), small at the end of the word's line (TalkBack hears it in the word). */
    const val FOCUS_STRING = "tuner_focus_string"

    /** Focus mode's exit button, in the top end corner of the full-screen tuner. */
    const val EXIT_FOCUS = "tuner_exit_focus"

    /** The button that turns focus mode on, at the end of the top row (top right, upright and on its side). */
    const val ENTER_FOCUS = "tuner_enter_focus"

    /** In its place among focus mode's controls a tap brings up: back to the full-screen tuner. */
    const val HIDE_CONTROLS = "tuner_hide_controls"

    /** After a strum of the open strings (2.0, Phase 74): string [index]'s mark above its peg (↑, ↓, ✓ or –). */
    fun strumMark(index: Int): String = "tuner_strum_mark_$index"

    /** The strum check's button above the pegs, at their end (2.0, Phase 74, after the owner's guitar check). */
    const val STRUM_BUTTON = "tuner_strum_button"

    /** The strum check's panel in the lens's place, its × and string [index]'s column (1.1's panel, the owner's pick of 2026-11-03). */
    const val STRUM_PANEL = "tuner_strum_panel"
    const val STRUM_CLOSE = "tuner_strum_close"
    fun strumString(index: Int): String = "tuner_strum_string_$index"

    /** The 12th-fret check's way in (2.0, Phase 71): the picker sheet's last row in guitar mode. */
    const val INTONATION_ROW = "tuner_intonation_row"

    /** The 12th-fret check's way out: the small × in its panel's corner. */
    const val INTONATION_CLOSE = "tuner_intonation_close"

    /** The 12th-fret check: its panel in the meter's place, the line under it, and the verdict's two buttons. */
    const val INTONATION_PANEL = "tuner_intonation_panel"
    const val INTONATION_STATUS = "tuner_intonation_status"
    const val INTONATION_AGAIN = "tuner_intonation_again"
    const val INTONATION_NEXT = "tuner_intonation_next"

    /** String [index]'s column in the 12th-fret check's summary. */
    fun intonationString(index: Int): String = "tuner_intonation_string_$index"

    /** Keep screen on's warning before the screen is let go ("Screen turns off soon"). */
    const val SCREEN_OFF_WARNING = "tuner_screen_off_warning"
}
