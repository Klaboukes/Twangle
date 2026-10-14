# Twangle — Roadmap

Status legend: ☐ todo · ◐ in progress · ☑ done

Each phase ends in a state that builds and can be demoed. See [ARCHITECTURE.md](ARCHITECTURE.md) for design details.

Phases 0–20 are finished and moved to [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md). Move a phase there once all its items are ☑ (owner checks stay below).

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
- ☐ Phase 19: play along with the metronome for a few minutes with a real guitar, at a few tempos and meters: check it feels steady, the accents help you find beat 1, and the clicks are pleasant at a comfortable loudness
- ☐ Phase 20: when you have a Wear OS watch, install Twangle on it and tune a real guitar with it: the note and cents follow the string, the ring glows softly when a string is in tune, and when the watch screen goes dark (or dims to always-on) the microphone icon disappears
- ☐ Phase 22: native speakers look over the new translations where possible

---

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
Goal: a short tour on first start that points out the main parts of the tuner, one at a time, with text balloons that float on their own layer above the screen.
**Owner decided (2026-10-14):** the tour balloons live in a separate overlay layer above the whole UI instead of being squeezed into the layout, since the tour runs once at first start; under the dim layer they may cover anything.
- ☑ Preview for the owner before anything is built: mock-up of the tour in `docs/design/tour-preview.html` (each step on a tuner screen: the screen dimmed, one part lit up, a short balloon with Next / Skip; a few styles, light and dark, Dutch at 2× text, a 360 dp phone). **Owner decided (2026-10-14), round 2:** balloons without an outline, lifted by a soft shadow (Futuristic: a glow), carrying the style's mark, filled with the style's **accent**; texts as short as possible: "How far off your note is", "Lock a string", "Pick instrument and tuning", "Tune strings or any note", "Metronome and settings" (Dutch in the mock-up). The mock-up's top comment is the design the build follows
- ☑ The tour: on first start the screen dims and one part at a time is lit up (spotlight cut-out) — the note with needle/wave, the strings (tap one to lock it), the instrument-and-tuning button, the guitar/chromatic mode switch, the bottom bar (metronome, settings) — each with a short balloon and Next / Skip tour (last step: Done); shown once (a saved setting), "Show me around" in Settings starts it again; never a nuisance for experienced players (owner, 2026-10-14): one tap on "Skip tour" or Back ends the whole tour for good, and updates of an existing install never show it; balloon wholly shown at 2× font, in Dutch, on a 360 dp phone, on a phone on its side and in the two-pane layout, every style (the in-layout string-lock tip misses its corner in Dutch, Futuristic and Space Age on the Xiaomi 12X, and in Dutch in every style on a 360 dp phone; the overlay balloon must not); TalkBack; English and Dutch; phone check (done 2026-10-14: an overlay over the whole window, `TourHost` in `:feature:tuner` `tour/`; on the rail of a phone on its side the balloon goes beside it; `TourStylesTest`, `TourFlowTest`, `TourGeometryTest`; the in-layout tip stays, hidden while the tour is due or has run)
- ☑ The one-time string-lock tip becomes the tour's strings step: the in-layout balloon (`StringLockTip`, its corner/above-the-button fallback and `stringLockTipSeen`) goes; `StringLockTipStylesTest`, which checks that fallback, is replaced by the tour's tests (the landscape controls pane keeps its 56 dp clearance, which also keeps a margin when it scrolls; the old `string_lock_tip_seen` key stays in existing installs and still marks them as updates)
- ☐ A reception tip that is set smaller to fit ("Too loud, move the phone back" at 1.25× text) no longer moves the note by 1–2 px; `TunerContentTest.aShrunkTipMovesNothing…` (now `@Ignore`d) switched back on

## Phase 23 — Go live on the Play Store → **v1.0**
Goal: Twangle is public on Google Play. Needs the owner's Play developer account.
- ☐ The look is final: run `scripts/ui-tests.sh --all --thorough`, fix what it finds, and make those double checks part of every run again (every style, and the `@Thorough` tests; until then a normal run leaves them out so restyling stays quick)
- ☐ Play data-safety form (no data collected or shared)
- ☐ Privacy policy published at a public URL (the repo is private), and that URL plus a contact email entered in the Play Console
- ☐ Play Store listing: screenshots, description in every supported language
- ☐ Internal → closed → production tracks
