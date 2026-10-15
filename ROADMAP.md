# Twangle — Roadmap

Status legend: ☐ todo · ◐ in progress · ☑ done

Each phase ends in a state that builds and can be demoed. See [ARCHITECTURE.md](ARCHITECTURE.md) for design details.

Phases 0–20 and 24 are finished and moved to [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md). Move a phase there once all its items are ☑ (owner checks stay below).

---

## Waiting on the owner
Things only the owner can start. Claude mentions these at the start of a session while they're open.
- ☐ **Overnight run for Phase 22's fit check and Phase 25's meter looks:** at bedtime, plug the phone in by USB (on the charger, somewhere cool) and tell Claude "start the overnight run". It checks that every language fits at 2× text in every style, runs the on-device UI tests of the new meter looks (`MeterLooksTest`, `SettingsContentTest`, `SettingsStylesTest`, `CompactLandscapeStylesTest`, `TunerContentTest`), fixes what it finds, and runs the full UI tests, which takes a few hours
- ☑ **Note names:** owner chose "what is most common in those countries" (2026-10-15): Polish and Russian call B "H" as German does, Turkish and Arabic use Do-Re-Mi

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
- ☐ Phase 25: tune a real guitar with each of the six meter looks, light and dark, and say if any feels hard to read or too busy
- ☐ Ticks and clicks: with no string ringing, tap the desk, click a pen and let a clock tick next to the phone: the needle stays still. Then tune a real guitar and check a plucked string still shows promptly and holds steady while you tap; the G string stays on G3 (it used to jump to E4)

---

## Phase 21 — Native pitch detector (only if needed)
Goal: a C++ (NDK/Oboe) detector, built only if profiling shows the Kotlin one is too slow (for example for strum check or Wear OS).
- ☐ Decide with measurements from Phases 15, 18 and 20 whether it is needed; if not, close this phase
- ☐ Native YIN behind the `PitchDetector` interface, with the same ±1 cent tests as the Kotlin version
- ☐ CPU and battery compared with the Kotlin detector on the test phone

## Phase 22 — More languages
Goal: Twangle speaks the world's major languages before it goes public.
- ☑ `LocalizationTest` checks every supported language, not only Dutch; the in-app language list (Android 13+) shows them all (one list, `twangle.languages` in gradle.properties, feeds both apps' `localeFilters` and the test)
- ☑ Western European: German, French, Spanish, Italian, Portuguese (Brazil) (phone and watch apps, all texts; UI tests in these languages still to run, in the fit-check item below)
- ☑ Asian: Chinese (Simplified), Japanese, Korean, Hindi, Indonesian (phone and watch apps, all texts; CJK and Devanagari from the phone's own fonts; UI tests in these languages still to run, in the fit-check item below)
- ☑ Russian, Turkish and Polish (phone and watch apps, all texts; Cyrillic falls back to the phone font in four styles, and Ancient and Futuristic mix faces in Turkish and Polish titles, see the fit-check item below)
- ☑ Arabic, with the screens mirrored right-to-left (string row, settings, wave and dial checked in RTL): texts, settings and panels mirror; meter, wave, strum check, string row, tempo dial, beat lights and the A4 slider stay low-left/high-right; Arabic-Indic digits everywhere except octaves in note names (phone checked in Arabic; UI tests in Arabic still to run, in the fit-check item below)
- ☑ Note names that match local habit: German, Polish and Russian use H for B, and French, Italian, Spanish, Portuguese, Turkish and Arabic start on Do-Re-Mi (only the default; the notation setting still lets players choose). Settings has *Automatic* (follows the language) plus a German option; German TalkBack says Cis/Des/H; the watch follows its own language (phone checked in German and French; watch not checked on a device)
- ☑ In-app language picker in Settings: Phone's language first, then every language in twangle.languages in its own name; works on Android 8–12 too (AppCompat per-app language, the same setting as phone Settings on Android 13+; phone checked: Polish and back, texts and H for B follow, kept after a restart; its UI tests in `SettingsContentTest` run in the overnight run; not checked on an Android 8–12 phone)
- ☐ Long words and other scripts fit at 2× font in every style (UI tests); phone check in a few languages. **Runs overnight, started by the owner** (see *Waiting on the owner*); also fix the Turkish/Polish letters missing from Ancient's and Futuristic's title fonts (words mix two fonts) and check Futuristic's and Space Age's letter spacing on Chinese and Japanese

## Phase 25 — Meter looks, round 2
Goal: every meter look shows the note its own way, so they no longer look alike (approved mock-up: [docs/design/meter-looks-round2-preview.html](docs/design/meter-looks-round2-preview.html), owner 2026-10-15). Six looks: Classic, Light arc, Spirit level, Eclipse, Arrows, Light bars. UI tests run in the overnight run (see *Waiting on the owner*).
- ☑ Classic gets the "CENTS" label from Studio meter; Rolling scale, Comet and Studio meter go (a saved one opens as Classic)
- ☑ Light arc becomes a fan of little lights that opens toward the note, and Spirit level a big straight vial with the bubble at its middle height; neither has numbers any more
- ☑ New looks Eclipse, Arrows and Light bars, with their names in every language (the arrows' shimmer from the mock-up left out, so a steady note draws nothing new)
- ☐ The on-device UI tests of the six looks pass (`MeterLooksTest` and the settings and tuner layout tests). **Runs overnight, started by the owner** (see *Waiting on the owner*)

## Phase 23 — Go live on the Play Store → **v1.0**
Goal: Twangle is public on Google Play. Needs the owner's Play developer account.
- ☐ The look is final: run `scripts/ui-tests.sh --all --thorough`, fix what it finds, and make those double checks part of every run again (every style, and the `@Thorough` tests; until then a normal run leaves them out so restyling stays quick)
- ☐ Play data-safety form (no data collected or shared)
- ☐ Privacy policy published at a public URL (the repo is private), and that URL plus a contact email entered in the Play Console
- ☐ Play Store listing: screenshots, description in every supported language
- ☐ Internal → closed → production tracks
