# Twangle — Roadmap

Status legend: ☐ todo · ◐ in progress · ☑ done

Each phase ends in a state that builds and can be demoed. See [ARCHITECTURE.md](ARCHITECTURE.md) for design details.

Phases 0–14 are finished and moved to [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md). Move a phase there once all its items are ☑ (owner checks stay below).

---

## Owner checks (live tests)
Done by the owner by hand, with a real guitar or headset. They are not roadmap increments: `/roadmap-step` skips them.

- ☐ Phase 3: tested on at least 2 physical devices with a real guitar
- ☐ Phase 5: with a real Bluetooth headset connected, the tuner still listens through the phone's mic and reads a string correctly
- ☐ Phase 11: on the test phone, compare the wave breathing in tune with it holding still, using the temporary switch, and pick one; Claude then removes the other and the switch
- ☐ Phase 12: with a real guitar, check the tips feel right: none while playing normally near the phone, "Play a bit louder" when playing very softly from across the room, "Too loud, move the phone back" when strumming hard right next to it
- ☐ Phase 14: try each of the six styles on the phone, light and dark, upright and on its side, and say if any colour, letter or background picture feels wrong
- ☐ Phase 22: native speakers look over the new translations where possible

---

## Phase 15 — More instruments
Goal: tune more than a six-string guitar.
- ☐ `Instrument` model in `:core:dsp` with string counts and preset tunings: 7- and 8-string guitar, 4- and 5-string bass, ukulele (standard and low G), violin, viola, cello, mandolin
- ☐ Low notes: an 8192-sample window when the instrument goes below low E (down to B0 ≈ 30.9 Hz); ±1 cent tests from 30 Hz; CPU per frame measured on the test phone
- ☐ Instrument picker (beside the tuning picker), saved in settings; the tuning menu shows only that instrument's tunings
- ☐ String row that fits 4 to 8 strings in portrait, landscape and at 2× font
- ☐ phone check for each instrument with short test tones

## Phase 16 — Custom tunings
Goal: players can make, name and keep their own tunings.
- ☐ Custom tunings stored in DataStore (name, instrument, one note per string); damaged data falls back to the presets
- ☐ Tuning editor: pick each string's note with ± semitone buttons, name it, save; checks for empty names and duplicates
- ☐ Custom tunings in the tuning menu below the presets, with edit and delete
- ☐ English and Dutch texts, TalkBack and 2× font for the editor; phone check

## Phase 17 — Reference tone
Goal: the app can play the target note so players can tune by ear.
- ☐ Tone player in `:core:audio` (`AudioTrack`): a short plucked tone at the target pitch that follows A4 calibration and stops by itself after a few seconds
- ☐ Long-press a string (or a play button) to hear it; tap again to stop
- ☐ The tuner ignores the microphone while a tone plays, so it doesn't measure its own sound
- ☐ Volume follows the phone's media volume; TalkBack labels; phone check by ear

## Phase 18 — Strum all strings
Goal: strum once and see which strings are out of tune.
- ☐ Research spike: polyphonic detection of 6 strings at once in `:core:dsp` (spectrum peaks per expected string, harmonics that overlap); write down the accuracy that is reachable
- ☐ Detector with synthetic chord tests (all six strings, one or two strings off by 10–30 cents, a missing string)
- ☐ "Strum check" view: every string shows flat, in tune or sharp at once, in each style
- ☐ phone check with recorded strums from short test tones; owner check with a real guitar

## Phase 19 — Metronome
Goal: a simple, steady metronome in the same app.
- ☐ Click generator with sample-exact timing (`AudioTrack`), 30–250 BPM, accent on the first beat, 2/4 to 7/8
- ☐ Metronome screen: tempo dial, ± buttons, tap tempo, beat lights in each style; optional vibration on the beat
- ☐ Stops when the app leaves the screen (no foreground service); tempo and meter saved in settings
- ☐ Timing drift measured over 10 minutes; English and Dutch texts; phone check

## Phase 20 — Wear OS companion and home-screen widget
Goal: Twangle on the watch and one tap from the home screen.
- ☐ Home-screen widget: shows the chosen tuning and opens the tuner with one tap (the widget never holds the microphone)
- ☐ Wear OS module: a watch tuner that listens with the watch's own mic and reuses `:core:dsp`
- ☐ Watch UI: note, cents and an in-tune ring sized for round screens; mic released when the watch screen turns off
- ☐ Check on a Wear OS emulator; real watch test by the owner

## Phase 21 — Native pitch detector (only if needed)
Goal: a C++ (NDK/Oboe) detector, built only if profiling shows the Kotlin one is too slow (for example for strum check or Wear OS).
- ☐ Decide with measurements from Phases 15, 18 and 20 whether it is needed; if not, close this phase
- ☐ Native YIN behind the `PitchDetector` interface, with the same ±1 cent tests as the Kotlin version
- ☐ CPU and battery compared with the Kotlin detector on the test phone

## Phase 22 — More languages
Goal: Twangle speaks the world's major languages before it goes public.
- ☐ `LocalizationTest` checks every supported language, not only Dutch; the in-app language list (Android 13+) shows them all
- ☐ Western European: German, French, Spanish, Italian, Portuguese (Brazil)
- ☐ Asian: Chinese (Simplified), Japanese, Korean, Hindi, Indonesian
- ☐ Russian, Turkish and Polish
- ☐ Arabic, with the screens mirrored right-to-left (string row, settings, wave and dial checked in RTL)
- ☐ Note names that match local habit: German uses H for B, and French, Italian, Spanish and Portuguese start on Do-Re-Mi (only the default; the notation setting still lets players choose)
- ☐ Long words and other scripts fit at 2× font in every style (UI tests); phone check in a few languages

## Phase 24 — Show me around
Goal: a short tour on first start that points out the main parts of the tuner with text balloons like the string-lock tip. **Owner to fill in** the rest of this phase and decide where it goes in the order (probably before going live).
- ☐ Balloons and tips that fit and move nothing at every text size and screen width: the string-lock tip reaches its corner beside the tuning picker in every style and language (Space Age in Dutch now just misses it on the Xiaomi 12X and goes above the picker; its check in `StringLockTipStylesTest` is skipped until then), and on wider phones; a reception tip that is set smaller to fit ("Too loud, move the phone back" at 1.25× text) no longer moves the note by 1–2 px (`TunerContentTest.aShrunkTipMovesNothing…`, now `@Ignore`d); both checks switched back on

## Phase 23 — Go live on the Play Store → **v1.0**
Goal: Twangle is public on Google Play. Needs the owner's Play developer account.
- ☐ The look is final: run `scripts/ui-tests.sh --all --thorough`, fix what it finds, and make those double checks part of every run again (every style, and the `@Thorough` tests; until then a normal run leaves them out so restyling stays quick)
- ☐ Play data-safety form (no data collected or shared)
- ☐ Privacy policy published at a public URL (the repo is private), and that URL plus a contact email entered in the Play Console
- ☐ Play Store listing: screenshots, description in every supported language
- ☐ Internal → closed → production tracks
