# Twangle — Roadmap

Status legend: ☐ todo · ◐ in progress · ☑ done

Each phase ends in a state that builds and can be demoed. See [ARCHITECTURE.md](ARCHITECTURE.md) for design details.

Phases 0–7 (scaffolding, DSP core, audio capture, tuner MVP, settings and tunings, polish → v0.9 beta, release preparation, clearer tuning) are finished and moved to [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md).

---

## Owner checks (live tests)
Done by the owner by hand, with a real guitar or headset. They are not roadmap increments: `/roadmap-step` skips them.

- ☐ Phase 3: tested on at least 2 physical devices with a real guitar
- ☐ Phase 5: with a real Bluetooth headset connected, the tuner still listens through the phone's mic and reads a string correctly
- ☐ Phase 19: native speakers look over the new translations where possible

---

## Phase 8 — Note names and visual polish
Goal: sharps, flats and tunings look and read the way musicians write them, and no style has small layout clashes. From the UX review of 11 October 2026.
- ☑ ♯ and ♭ look the same everywhere: smaller and raised like in sheet music, in a face that has the signs, in the big note, string chips, tuning menu, settings and dial labels, in all seven styles (today Classic draws a ♯ as big as the letter, and the menu and settings show tiny, sunken ones)
- ☐ Each preset tuning uses the spelling players know, e.g. Half step down = E♭ A♭ D♭ G♭ B♭ E♭ instead of D♯ G♯ C♯ F♯ A♯ D♯, in the menu, string row and readout (Do Re Mi follows: Mi♭ …); the notation setting still decides chromatic mode; tests in `:core:dsp`
- ☑ Waiting screen: the note placeholder no longer looks like a grey loading bar (the dash renders as a block in some faces) — now a faint ♪
- ☐ Style clashes fixed: Classic's frame line no longer runs through the mode switch; Futuristic's corner brackets no longer cover settings text when scrolled; the mode switch uses each style's main accent (Futuristic shows purple beside its cyan); screenshot check of every style, light and dark
- ☐ Settings in three groups with headings: Tuning (A4, note names, in-tune precision), Look (style, light or dark, wallpaper colours, wave view and wave look), While tuning (keep screen on, vibrate, tune up/down hint)
- ☐ About section at the bottom of Settings: app version, the "free forever" promise, the privacy statement (Phase 6), font and open-source licences; the version number brought in line with the beta (it still says 0.0.1)
- ☐ Dutch TalkBack says note names the Dutch way: cis, des, es … instead of "C kruis", "D mol"
- ☐ English and Dutch texts; UI tests at 2× font and in landscape for the new settings rows and the About section; S26 check in every style

## Phase 9 — Strobe tuner display
Goal: a strobe view as a third display next to the needle and the wave, for very fine tuning.
- ☐ Strobe model in `:core:dsp`: the pattern's drift speed and direction from the cents deviation, standing still when in tune (tests on synthetic signals, no allocations per frame)
- ☐ Display picker in Settings: needle, wave or strobe (replaces the wave-view switch; old setting carried over)
- ☐ Strobe drawn in each of the seven styles, light and dark; the pattern moves in the draw phase only, so the screen doesn't recompose every frame
- ☐ TalkBack, landscape, tablet and 2× font for the strobe view (UI tests); S26 check in every style

## Phase 10 — Compact landscape mode
Goal: on its side, the phone shows only the meter and what is essential, big and easy to read from a distance.
- ☐ Compact layout in landscape: the meter fills the screen, with the note name, the cents and the in-tune signal; tuning picker, mode switch and buttons hidden
- ☐ The target string still shows (small) in guitar mode; tapping the screen briefly brings back the controls
- ☐ Settings switch to keep the full two-pane landscape layout instead
- ☐ In each style, light and dark, on phone and tablet, with TalkBack and 2× font (UI tests); S26 check

## Phase 11 — Volume and background-noise meters
Goal: two small meters show how well the guitar comes through and how much noise the tuner has to filter out.
- ☐ Noise-floor estimate in `:core:dsp`: tracks the room's background level between notes, plus a signal-to-noise figure while a note sounds (synthetic tests, no allocations per frame)
- ☐ Clarity figure: how clean the pitch is (from YIN's confidence), so the meter shows how hard the tuner has to work
- ☐ Two small meters on the tuner screen in each style: input volume (too soft / good / too loud) and background noise (quiet / some / loud), with a hint when it is too soft or too noisy
- ☐ English and Dutch texts, TalkBack, landscape (also compact mode) and 2× font; a Settings switch to hide them; S26 check in a quiet and a noisy room

## Phase 12 — More instruments
Goal: tune more than a six-string guitar.
- ☐ `Instrument` model in `:core:dsp` with string counts and preset tunings: 7- and 8-string guitar, 4- and 5-string bass, ukulele (standard and low G), violin, viola, cello, mandolin
- ☐ Low notes: an 8192-sample window when the instrument goes below low E (down to B0 ≈ 30.9 Hz); ±1 cent tests from 30 Hz; CPU per frame measured on the S26
- ☐ Instrument picker (beside the tuning picker), saved in settings; the tuning menu shows only that instrument's tunings
- ☐ String row that fits 4 to 8 strings in portrait, landscape and at 2× font
- ☐ S26 check for each instrument with short test tones

## Phase 13 — Custom tunings
Goal: players can make, name and keep their own tunings.
- ☐ Custom tunings stored in DataStore (name, instrument, one note per string); damaged data falls back to the presets
- ☐ Tuning editor: pick each string's note with ± semitone buttons, name it, save; checks for empty names and duplicates
- ☐ Custom tunings in the tuning menu below the presets, with edit and delete
- ☐ English and Dutch texts, TalkBack and 2× font for the editor; S26 check

## Phase 14 — Reference tone
Goal: the app can play the target note so players can tune by ear.
- ☐ Tone player in `:core:audio` (`AudioTrack`): a short plucked tone at the target pitch that follows A4 calibration and stops by itself after a few seconds
- ☐ Long-press a string (or a play button) to hear it; tap again to stop
- ☐ The tuner ignores the microphone while a tone plays, so it doesn't measure its own sound
- ☐ Volume follows the phone's media volume; TalkBack labels; S26 check by ear

## Phase 15 — Strum all strings
Goal: strum once and see which strings are out of tune.
- ☐ Research spike: polyphonic detection of 6 strings at once in `:core:dsp` (spectrum peaks per expected string, harmonics that overlap); write down the accuracy that is reachable
- ☐ Detector with synthetic chord tests (all six strings, one or two strings off by 10–30 cents, a missing string)
- ☐ "Strum check" view: every string shows flat, in tune or sharp at once, in each style
- ☐ S26 check with recorded strums from short test tones; owner check with a real guitar

## Phase 16 — Metronome
Goal: a simple, steady metronome in the same app.
- ☐ Click generator with sample-exact timing (`AudioTrack`), 30–250 BPM, accent on the first beat, 2/4 to 7/8
- ☐ Metronome screen: tempo dial, ± buttons, tap tempo, beat lights in each style; optional vibration on the beat
- ☐ Stops when the app leaves the screen (no foreground service); tempo and meter saved in settings
- ☐ Timing drift measured over 10 minutes; English and Dutch texts; S26 check

## Phase 17 — Wear OS companion and home-screen widget
Goal: Twangle on the watch and one tap from the home screen.
- ☐ Home-screen widget: shows the chosen tuning and opens the tuner with one tap (the widget never holds the microphone)
- ☐ Wear OS module: a watch tuner that listens with the watch's own mic and reuses `:core:dsp`
- ☐ Watch UI: note, cents and an in-tune ring sized for round screens; mic released when the watch screen turns off
- ☐ Check on a Wear OS emulator; real watch test by the owner

## Phase 18 — Native pitch detector (only if needed)
Goal: a C++ (NDK/Oboe) detector, built only if profiling shows the Kotlin one is too slow (for example for strum check or Wear OS).
- ☐ Decide with measurements from Phases 12, 15 and 17 whether it is needed; if not, close this phase
- ☐ Native YIN behind the `PitchDetector` interface, with the same ±1 cent tests as the Kotlin version
- ☐ CPU and battery compared with the Kotlin detector on the S26

## Phase 19 — More languages
Goal: Twangle speaks the world's major languages before it goes public.
- ☐ `LocalizationTest` checks every supported language, not only Dutch; the in-app language list (Android 13+) shows them all
- ☐ Western European: German, French, Spanish, Italian, Portuguese (Brazil)
- ☐ Asian: Chinese (Simplified), Japanese, Korean, Hindi, Indonesian
- ☐ Russian, Turkish and Polish
- ☐ Arabic, with the screens mirrored right-to-left (string row, settings, wave and dial checked in RTL)
- ☐ Note names that match local habit: German uses H for B, and French, Italian, Spanish and Portuguese start on Do-Re-Mi (only the default; the notation setting still lets players choose)
- ☐ Long words and other scripts fit at 2× font in every style (UI tests); S26 check in a few languages

## Phase 20 — Go live on the Play Store → **v1.0**
Goal: Twangle is public on Google Play. Needs the owner's Play developer account.
- ☐ Play data-safety form (no data collected or shared)
- ☐ Privacy policy published at a public URL (the repo is private), and that URL plus a contact email entered in the Play Console
- ☐ Play Store listing: screenshots, description in every supported language
- ☐ Internal → closed → production tracks
