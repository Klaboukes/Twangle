# Twangle — Roadmap

Status legend: ☐ todo · ◐ in progress · ☑ done

Each phase ends in a state that builds and can be demoed. See [ARCHITECTURE.md](ARCHITECTURE.md) for design details.

Phases 0–17 are finished and moved to [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md). Move a phase there once all its items are ☑ (owner checks stay below).

---

## Owner checks (live tests)
Done by the owner by hand, with a real guitar or headset. They are not roadmap increments: `/roadmap-step` skips them.

- ☐ Phase 3: tested on at least 2 physical devices with a real guitar
- ☐ Phase 5: with a real Bluetooth headset connected, the tuner still listens through the phone's mic and reads a string correctly
- ☐ Phase 11: on the test phone, compare the wave breathing in tune with it holding still, using the temporary switch, and pick one; Claude then removes the other and the switch
- ☐ Phase 12: with a real guitar, check the tips feel right: none while playing normally near the phone, "Play a bit louder" when playing very softly from across the room, "Too loud, move the phone back" when strumming hard right next to it
- ☐ Phase 14: try each of the six styles on the phone, light and dark, upright and on its side, and say if any colour, letter or background picture feels wrong
- ☐ Phase 15: with a real bass (or a 7-string guitar or cello), check the lowest strings read correctly (B0, E1, B1, C2), and that the tuner shows no note while the amp only hums
- ☐ Phase 16: make a tuning of your own ("New tuning" in the instrument and tuning panel), tune a real guitar to it, then change it with Edit and delete it
- ☑ Phase 17: hold each string of a real guitar's tuning and tune by ear to the tone; check it sounds like a plucked string, at a comfortable loudness, and fades out by itself (owner, 2026-10-14: good enough for now)
- ☐ Phase 18: with a real guitar in a quiet room, switch on strum check (the strings button beside "Guitar · Standard"), strum all six open strings and check each one reads like the tuner does when you play it alone; then detune one or two strings a little and leave one out (mute it). Try it in drop D too
- ☐ Phase 22: native speakers look over the new translations where possible

---

## Phase 18 — Strum all strings
Goal: strum once and see which strings are out of tune.
- ☑ Research spike: polyphonic detection of 6 strings at once in `:core:dsp` (spectrum peaks per expected string, harmonics that overlap); write down the accuracy that is reachable — `StrumDetector`: with a 341 ms frame ≤ 0.12 cent on strings with a clear partial, ≤ 2.3 on B3/E4 (under E2's harmonics); 1.9 ms per reading on the Xiaomi (ARCHITECTURE.md, *Strum check*)
- ☑ Detector with synthetic chord tests (all six strings, one or two strings off by 10–30 cents, a missing string) — joint fit of overlapping partials: an octave pair detuned against each other 7.3 → 0.44 cent; `StrumListener` gives the first reading 0.39–0.44 s after a strum, then one every 0.1 s; `StrumVerdict` flat / in tune / sharp / not heard; 2.6 ms per reading on the Xiaomi. Known limits: a soft upper octave string within 35 cents of the lower one reads up to 19 cents off, a missing D4 in DADGAD/open D reads as heard (ARCHITECTURE.md, *Strum check*)
- ☑ "Strum check" view: every string shows flat, in tune or sharp at once, in each style — a switch beside the instrument-and-tuning button; the panel takes the meter's place, less sure strings hollow; a synthetic strum from a laptop speaker read A2 −21 / G3 +15 on the Xiaomi (ARCHITECTURE.md, *Strum check view*)
- ☑ phone check with recorded strums from short test tones; owner check with a real guitar (now under *Owner checks*) — `scripts/strum-check.sh` plays synthetic strums from the computer's speaker to the Xiaomi: every string within 1 cent of what was played, missing strings caught. It found that the last reading, as the strings die away, was up to 7 cents off and that clicks and room sounds were read as strums; each string now shows the strum's readings together, weighted by loudness (ARCHITECTURE.md, *Strum check*, *Phone check*)

## Phase 19 — Metronome
Goal: a simple, steady metronome in the same app.
- ☑ Click generator with sample-exact timing (`AudioTrack`), 30–250 BPM, accent on the first beat, 2/4 to 7/8 (`ClickTrack` + `AndroidMetronomePlayer`; no drift over ten simulated minutes, beats 250 ms apart within ±0.02 ms on the Xiaomi 12X)
- ☑ Metronome screen: tempo dial, ± buttons, tap tempo, beat lights in each style; optional vibration on the beat. Mock-up: [docs/design/metronome-nav-preview.html](docs/design/metronome-nav-preview.html). **Owner decided (2026-10-14):** bar variant **B · Frameless** (for the bar, below); keep the round tempo dial; the rest of the metronome as shown: meter picker 2/4 3/4 4/4 5/4 6/8 7/8 at the top, beat lights in the style's own wave marks (beat 1 largest, group starts middle sized), a frameless dial 30–250 BPM over 300° with the meter's scale rules and the style's arc and needle, the tempo with its Italian name, − / + by 1 BPM, Tap tempo (average of the last taps, reset after 2 s), Start / Stop, "Vibrate on the beat"; lit marks in the accent, never in-tune green; one soft accent glow behind the tempo on each beat; the quiet background; opens stopped. Until the bar lands it opens from a small metronome button beside the tuner's gear (temporary); Back returns to the tuner
- ☑ Stops when the app leaves the screen (no foreground service); tempo and meter saved in settings (and the vibration switch; the screen stays on while it plays)
- ☐ Timing drift measured over 10 minutes; English and Dutch texts; phone check
- ☐ Bottom navigation bar with Tuner · Metronome · Settings (Material 3 `NavigationBar`), replacing the gear button; a navigation rail along the edge on a phone on its side, hidden in the compact layout; in each style, without a frame that fights the frameless meter. Mock-up first; English and Dutch, TalkBack and 2× font; phone check

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
- ☐ Balloons and tips that fit and move nothing at every text size and screen width: the string-lock tip reaches its corner beside the instrument-and-tuning button in every style and language (in Dutch, Futuristic and Space Age now miss it on the Xiaomi 12X and go above the button, and on a 360 dp phone so does Dutch in every style; `StringLockTipStylesTest` checks that fallback until then), and on wider phones; a reception tip that is set smaller to fit ("Too loud, move the phone back" at 1.25× text) no longer moves the note by 1–2 px (`TunerContentTest.aShrunkTipMovesNothing…`, now `@Ignore`d); both checks switched back on

## Phase 23 — Go live on the Play Store → **v1.0**
Goal: Twangle is public on Google Play. Needs the owner's Play developer account.
- ☐ The look is final: run `scripts/ui-tests.sh --all --thorough`, fix what it finds, and make those double checks part of every run again (every style, and the `@Thorough` tests; until then a normal run leaves them out so restyling stays quick)
- ☐ Play data-safety form (no data collected or shared)
- ☐ Privacy policy published at a public URL (the repo is private), and that URL plus a contact email entered in the Play Console
- ☐ Play Store listing: screenshots, description in every supported language
- ☐ Internal → closed → production tracks
