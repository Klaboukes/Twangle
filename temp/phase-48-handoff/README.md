# Phase 48 handoff (desktop → laptop, 2026-10-26)

A temporary folder: the state of `/roadmap-step 48` when the owner moved from the desktop to the laptop. Delete this folder once Phase 48 is done.

## Where Phase 48 stands
All work so far is merged on `main` (PRs #379–#385). In ROADMAP.md Phase 48:
1. ☑ **Hear them first:** owner picked **all four** round-2 sounds (tanpura, harmonium, flute organ, warm pad) with **all variations**: root alone, + fifth, + fifth and octave, and *Lower* (an octave down). Samples: `docs/design/drone-sounds-preview/` (PRs #380, #382).
2. ☑ **The drone in `:core:dsp`** (PR #383): `DroneSynth`.
3. ☑ **Played in `:core:audio`** (PR #384): `DronePlayer`.
4. ◐ **The button on Scales:** mock-up ready (PR #385), **waiting for the owner to pick A, B or C**. The mock-up is `docs/design/drone-button-preview.html` (a copy is in this folder). Open it in Firefox (CLAUDE.md rule).
   - **A (recommended):** a split button at the end of the scale-name row: ▶ / ■ plus the current sound's icon; a tap on the icon drops the panel under it. The neck keeps its full size.
   - **B:** a "Sound · Drone" row under the neck, like the metronome; the neck loses about a seventh of its height upright.
   - **C:** ▶ / ■ alone by the name, long press for the panel; calmest, but the long press can't be found.
   - All three: the panel has *Sound* (tanpura, harmonium, flute organ, warm pad, in that order, with icons), *Notes* (Root / + fifth / + fifth and octave) and *Lower* (on/off), all remembered; default tanpura with the fifth. While playing, ■ on the accent colour and the root's dots softly breathe. No hint texts.
5. ☐ **Owner check** added under *Owner checks*.

**To continue on the laptop:** `git switch main && git pull`, tell Claude the pick (A, B or C), and run `/roadmap-step 48`. Claude then builds the button (item 4) and adds the owner check (item 5).

## Facts for building the button (not all in the code's docs)
- **`DronePlayer`** (`:core:audio`): `play(rootHz, sound = TANPURA, voicing = FIFTH, lower = false)`, `stop()`, `isPlaying: StateFlow<Boolean>`. Roots outside 40–1000 Hz throw. `isPlaying` turns false at once on `stop`, and also on audio-focus loss (a call), headphones unplugged, or a failure, so ▶/■ can follow it directly. A new `play` while sounding crossfades (heard ≈ 0.12 s later). Transient audio focus pauses other apps' music; the track is released after the fade (`AndroidDronePlayer.holdsSpeaker`).
- **DI:** `AppContainer.dronePlayer`, with `testDronePlayer` as a test stand-in. `:feature:practice` now depends on `:core:audio` (written in the Practice tab doc). `MainActivity.PracticeTab` should pass `container.dronePlayer` to Scales; `ChordsScreen` has no player parameter yet. Scales must call `stop()` when it goes (another tool, another tab, the screen off), and must start again only on a tap.
- **Fake:** `FakeDronePlayer` (test fixtures, already in practice's test and androidTest dependencies): `plays` / `lastPlay` (`DronePlay`), `stopCount`, `interrupt()`.
- **Root:** `NoteMapper(a4).frequencyOf(DroneSynth.rootNote(pitchClass))` puts the root in G♯2–G3, following the A4 setting. Enums `DroneSound` (TANPURA, HARMONIUM, FLUTE_ORGAN, WARM_PAD) and `DroneVoicing` (ROOT, FIFTH, FIFTH_OCTAVE) are saved by name. The choices go in `SettingsRepository`, and PRIVACY.md and its Dutch copy name them among the saved settings.
- **Cost** on the Xiaomi per 10 ms chunk, worst case: pad 1.1 ms on a big core / 3.3 ms on a little one, harmonium 0.45, tanpura 0.32, flute 0.04; a crossfade doubles it for 0.4 s.
- **Caveat:** the flute organ and the pad (and *Lower*) are very quiet on a phone speaker. Flute and pad have more than 10 dB of headroom if the owner wants them louder.

## The owner's rules from this session
Both are already in the repo. Copies of the desktop's memory files are in `memory/`, so the laptop's Claude can save them too.
- `/roadmap-step <N>`: start phase N without asking about earlier open phases, unless N depends on one (in `.claude/skills/roadmap-step/phase-mode.md`, PR #379).
- Open every mock-up and preview page in **Firefox**, never the app's built-in browser, which doesn't play sounds (in CLAUDE.md, PR #381).
