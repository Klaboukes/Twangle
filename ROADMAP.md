# Twangle — Roadmap

Status legend: ☐ todo · ◐ in progress · ☑ done

Each phase ends in a state that builds and can be demoed. See [ARCHITECTURE.md](ARCHITECTURE.md) for design details.

Phases 0–10 (scaffolding, DSP core, audio capture, tuner MVP, settings and tunings, polish → v0.9 beta, release preparation, clearer tuning, note names and visual polish, strobe display, compact landscape mode) are finished and moved to [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md).

---

## Owner checks (live tests)
Done by the owner by hand, with a real guitar or headset. They are not roadmap increments: `/roadmap-step` skips them.

- ☐ Phase 3: tested on at least 2 physical devices with a real guitar
- ☐ Phase 5: with a real Bluetooth headset connected, the tuner still listens through the phone's mic and reads a string correctly
- ☐ Phase 20: native speakers look over the new translations where possible
- ☐ Phase 11: on the S26, compare the wave breathing in tune with it holding still, using the temporary switch, and pick one; Claude then removes the other and the switch

---

## Phase 11 — A wave that helps you tune
Goal: the wave view gives the same "getting closer" cue as the strobe instead of being eye candy. Standing still means in tune; the drift speeds up the further off the note is; the shape still shows how far off from a distance.
- ☑ One motion for every wave look: the drift follows the strobe's speed rule (`StrobeMotion.driftSpeed`: still within ½ cent, faster per cent off) from the exact cents, not the in-tune flag, so the last few cents still show. The shape keeps showing distance up to 50 cents (deeper, more pulses further off) and smooths out close to the note. JVM tests for the drift at 0, ±1, ±3, ±5 and ±30 cents
- ☑ Temporary Settings switch "In tune: breathe / hold still", so the owner can compare both on the phone (English and Dutch, saved in settings); the losing option and the switch go after the owner check above (Settings → "When in tune", under the wave looks)
- ☑ Each look carries the cue in its own way, still in tune and drifting at strobe speed off pitch: stripes drift (not only lean), ripples spread out (sharp) or draw in (flat), fireflies drift and line up, dots scroll, the loop tumbles at the beat rate and settles into a ring. The Settings previews move the same way. Stripes only got the drift: the strobe look replaces them in the next step. Ripples swell unevenly off pitch, the loop spins like a coin with its near side sweeping the way the note is off, fireflies glide into an even line on the note; JVM tests for direction, stillness on the note and no jumps
- ☑ The strobe becomes one of the wave looks, so "Display" is just Needle or Wave; a saved Strobe display opens as Wave with the strobe look. **Owner decided:** 6 looks in the 3×2 grid, the strobe replacing the stripes look (final six: strobe, ribbon, dots, fireflies, loop, ripples). The strobe card comes first; a saved stripes look also opens as the strobe. Its marks now fade in and out at the ends, it rests at half height without a note, and on the note it holds still or swells gently with the temporary switch; the separate strobe view is gone (JVM tests for every old saved value)
- ☑ Every look checked in each style, light and dark, with TalkBack, on a phone and tablet on its side (also compact mode) and at 2× font; ARCHITECTURE.md updated; S26 check with short test tones. Screenshots of all six looks in all seven styles found two things to fix: the look names broke mid-word at 2× font in Dutch ("Strobo-scoop"; now two cards to a row from 1.3× and names shrink to fit one line), and the small looks were dim off pitch on dark backgrounds (brighter tint and fireflies). New UI tests: every look stays inside its box in every style, light and dark; TalkBack skips every look; the look cards are radio buttons in English and Dutch; names fit at 1.3× and 2× font

## Phase 12 — Volume and background-noise meters
Goal: two small meters show how well the guitar comes through and how much noise the tuner has to filter out.
- ☑ Noise-floor estimate in `:core:dsp`: tracks the room's background level between notes, plus a signal-to-noise figure while a note sounds (synthetic tests, no allocations per frame) — `NoiseFloorTracker`: minimum of note-free frames over 2 s; floor within 0.5 dB, SNR within 1 dB from 0 to 40 dB
- ☑ Clarity figure: how clean the pitch is (from YIN's confidence), so the meter shows how hard the tuner has to work — `Clarity.of`: 1 at 20 dB SNR or cleaner, ≈ 0.5 at 6 dB, ≈ 0 at 0 dB, the same for every pitch and timbre
- ☐ Two small meters on the tuner screen in each style: input volume (too soft / good / too loud) and background noise (quiet / some / loud), with a hint when it is too soft or too noisy
- ☐ English and Dutch texts, TalkBack, landscape (also compact mode) and 2× font; a Settings switch to hide them; S26 check in a quiet and a noisy room

## Phase 13 — More instruments
Goal: tune more than a six-string guitar.
- ☐ `Instrument` model in `:core:dsp` with string counts and preset tunings: 7- and 8-string guitar, 4- and 5-string bass, ukulele (standard and low G), violin, viola, cello, mandolin
- ☐ Low notes: an 8192-sample window when the instrument goes below low E (down to B0 ≈ 30.9 Hz); ±1 cent tests from 30 Hz; CPU per frame measured on the S26
- ☐ Instrument picker (beside the tuning picker), saved in settings; the tuning menu shows only that instrument's tunings
- ☐ String row that fits 4 to 8 strings in portrait, landscape and at 2× font
- ☐ S26 check for each instrument with short test tones

## Phase 14 — Custom tunings
Goal: players can make, name and keep their own tunings.
- ☐ Custom tunings stored in DataStore (name, instrument, one note per string); damaged data falls back to the presets
- ☐ Tuning editor: pick each string's note with ± semitone buttons, name it, save; checks for empty names and duplicates
- ☐ Custom tunings in the tuning menu below the presets, with edit and delete
- ☐ English and Dutch texts, TalkBack and 2× font for the editor; S26 check

## Phase 15 — Reference tone
Goal: the app can play the target note so players can tune by ear.
- ☐ Tone player in `:core:audio` (`AudioTrack`): a short plucked tone at the target pitch that follows A4 calibration and stops by itself after a few seconds
- ☐ Long-press a string (or a play button) to hear it; tap again to stop
- ☐ The tuner ignores the microphone while a tone plays, so it doesn't measure its own sound
- ☐ Volume follows the phone's media volume; TalkBack labels; S26 check by ear

## Phase 16 — Strum all strings
Goal: strum once and see which strings are out of tune.
- ☐ Research spike: polyphonic detection of 6 strings at once in `:core:dsp` (spectrum peaks per expected string, harmonics that overlap); write down the accuracy that is reachable
- ☐ Detector with synthetic chord tests (all six strings, one or two strings off by 10–30 cents, a missing string)
- ☐ "Strum check" view: every string shows flat, in tune or sharp at once, in each style
- ☐ S26 check with recorded strums from short test tones; owner check with a real guitar

## Phase 17 — Metronome
Goal: a simple, steady metronome in the same app.
- ☐ Click generator with sample-exact timing (`AudioTrack`), 30–250 BPM, accent on the first beat, 2/4 to 7/8
- ☐ Metronome screen: tempo dial, ± buttons, tap tempo, beat lights in each style; optional vibration on the beat
- ☐ Stops when the app leaves the screen (no foreground service); tempo and meter saved in settings
- ☐ Timing drift measured over 10 minutes; English and Dutch texts; S26 check

## Phase 18 — Wear OS companion and home-screen widget
Goal: Twangle on the watch and one tap from the home screen.
- ☐ Home-screen widget: shows the chosen tuning and opens the tuner with one tap (the widget never holds the microphone)
- ☐ Wear OS module: a watch tuner that listens with the watch's own mic and reuses `:core:dsp`
- ☐ Watch UI: note, cents and an in-tune ring sized for round screens; mic released when the watch screen turns off
- ☐ Check on a Wear OS emulator; real watch test by the owner

## Phase 19 — Native pitch detector (only if needed)
Goal: a C++ (NDK/Oboe) detector, built only if profiling shows the Kotlin one is too slow (for example for strum check or Wear OS).
- ☐ Decide with measurements from Phases 13, 16 and 18 whether it is needed; if not, close this phase
- ☐ Native YIN behind the `PitchDetector` interface, with the same ±1 cent tests as the Kotlin version
- ☐ CPU and battery compared with the Kotlin detector on the S26

## Phase 20 — More languages
Goal: Twangle speaks the world's major languages before it goes public.
- ☐ `LocalizationTest` checks every supported language, not only Dutch; the in-app language list (Android 13+) shows them all
- ☐ Western European: German, French, Spanish, Italian, Portuguese (Brazil)
- ☐ Asian: Chinese (Simplified), Japanese, Korean, Hindi, Indonesian
- ☐ Russian, Turkish and Polish
- ☐ Arabic, with the screens mirrored right-to-left (string row, settings, wave and dial checked in RTL)
- ☐ Note names that match local habit: German uses H for B, and French, Italian, Spanish and Portuguese start on Do-Re-Mi (only the default; the notation setting still lets players choose)
- ☐ Long words and other scripts fit at 2× font in every style (UI tests); S26 check in a few languages

## Phase 21 — Go live on the Play Store → **v1.0**
Goal: Twangle is public on Google Play. Needs the owner's Play developer account.
- ☐ Play data-safety form (no data collected or shared)
- ☐ Privacy policy published at a public URL (the repo is private), and that URL plus a contact email entered in the Play Console
- ☐ Play Store listing: screenshots, description in every supported language
- ☐ Internal → closed → production tracks
