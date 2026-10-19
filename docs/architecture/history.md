# History

Part of [ARCHITECTURE.md](../../ARCHITECTURE.md), which lists every topic file.

Designs and measurements that have been superseded, kept for the record and taken out of the topic files so those describe only what is built now. Where a current design exists *because* of an old one going wrong, the topic file keeps that reason; this file has the dates and the old details. Finished roadmap items are in [ROADMAP-ARCHIVE.md](../../ROADMAP-ARCHIVE.md).

## Contents
- [Overview](#overview)
- [Threading and performance](#threading-and-performance)
- [Lifecycle, permissions and settings](#lifecycle-permissions-and-settings)
- [UI basics](#ui-basics)
- [Tuner screen](#tuner-screen)
- [Meter, wave and styles](#meter-wave-and-styles)
- [Metronome, widget and watch](#metronome-widget-and-watch)
- [Strum check and metronome](#strum-check-and-metronome)
- [Languages](#languages)
- [Testing](#testing)

## Overview
- **Music tools in 1.1:** on 2026-10-16 the owner first planned v1.0 as it was and the music tools (Phases 29–33) for a version 1.1 after it (Phase 34, "Version 1.1 on the Play Store", once v1.0 was live; the store screenshots under `ToolSwitchesOffRule`). Later that day, in no hurry to release, the owner moved the tools into v1.0: Phase 34 turns them on before the release.
- **CI trigger:** until 2026-10-16 GitHub Actions built `main` after every merge (and before that, every PR too). About 200 merges in a week used up the free plan's 2,000 minutes a month, so it now runs once a night.
- **Launcher icon:** until 2026-10-15 the icon was the October loop: a string that came in at an angle, made one loop and left straight.

## Threading and performance
- **Meter looks removed in Phase 25**, as measured on the S26 in Phase 13 (same method as the table in [Threading and performance](performance.md)):

| Chromatic, meter moving | Frames/s | Late frames (of ~2,500) | p50 / p90 / p95 / p99 | Main thread | Main per frame | RenderThread |
|---|---|---|---|---|---|---|
| Rolling scale | 119 | 12, repeat run 5 | 12 / 12 / 13 / 14 ms | 19.9 % (22.5 %) | 1.67 ms (1.89 ms) | 30.3 % |
| Comet | 119 | 6, repeat run 0 | 8 / 10 / 10 / 12 ms | 18.6 % (19.7 %) | 1.56 ms (1.65 ms) | 32.7 % |
| Studio meter | 120 | 10, repeat run 0 | 9 / 10 / 11 / 12 ms | 17.1 % (20.0 %) | 1.43 ms (1.67 ms) | 30.1 % |

- **Phase 14 meter finish, on the Xiaomi 12X:** frame times of every look in Contemporary just before and after the style needles, hubs, larger scale and ♭ ♯ in the box (`scripts/meter-frames.sh`). The finish changed nothing beyond run-to-run noise.

| Chromatic, meter moving | Before: frames/s, late (of ~2,400), p50 / p90 / p95 / p99, main per frame | After |
|---|---|---|
| Classic | 113, 387, 12 / 21 / 24 / 30 ms, 3.68 ms | 110, 567, 13 / 23 / 27 / 34 ms, 3.73 ms (114, 398, 11 / 21 / 24 / 34 ms, 3.39 ms) |
| Light arc | 117, 215, 9 / 17 / 21 / 30 ms, 3.30 ms | 115, 350, 12 / 19 / 22 / 30 ms, 3.34 ms (116, 271, 11 / 18 / 21 / 31 ms, 3.10 ms) |
| Spirit level | 104, 1,170, 16 / 26 / 30 / 38 ms, 3.39 ms | 103, 1,258, 16 / 26 / 31 / 40 ms, 3.18 ms |
| Rolling scale | 87, 1,478, 24 / 36 / 38 / 53 ms, 4.38 ms | 85, 1,449, 26 / 38 / 42 / 53 ms, 4.40 ms |
| Comet | 110, 672, 13 / 23 / 27 / 36 ms, 3.36 ms | 110, 768, 13 / 23 / 26 / 36 ms, 3.18 ms |
| Studio meter | 100, 1,373, 18 / 29 / 32 / 42 ms, 3.32 ms | 99, 1,405, 19 / 29 / 32 / 40 ms, 3.08 ms |
- **Music tools hidden in release builds (Phases 28–33, 2026-10-16 to 2026-10-17):** while the tools were built they landed on `main` behind their `ToolSwitches`, decided in one place, `toolSwitchesFor(debugBuild)` in `:app` (`BuildToolSwitches.kt`, fed `BuildConfig.DEBUG`): every tool on in debug builds, every one off in `release` and in the `nonMinifiedRelease` and `benchmarkRelease` builds the Baseline Profile plugin makes from it. `DebugToolSwitchesTest` (`app/src/testDebug`) and `ReleaseToolSwitchesTest` (`app/src/testRelease`, run by `testReleaseUnitTest` and the two profile builds' own unit tests, which `app/build.gradle.kts` enabled) checked each build's switches; the UI test `ToolSwitchesOffTest`, through `ToolSwitchesOffRule` (which set `AppContainer.toolSwitches` to `ALL_OFF` before the activity started), checked the debug app with every switch off was the app without the tools (two tuner modes, the bar's Metronome tab, no "more tools" button, no Practice). `MeterFramesBenchmark` started the `benchmarkRelease` build with an extra (`MainActivity.EXTRA_MEASURE_TOOLS`) to show the pitch trail for measuring. Phase 34 (2026-10-17) turned every tool on in every build and removed all of that.

## Lifecycle, permissions and settings
- **Microphone permission:** until Phase 26 a dismissed first dialog sent the player to Settings; Phase 26 taught the rules to tell a dismissed dialog from a permanent denial (the saved `micRefused`).
- **Metronome and rotation:** until Phase 26 every rotation stopped the clicks.
- **Settings groups:** until Phase 26 one Look group held every card grid, and "Pitch and notes" was called "Tuning", which clashed with the navigation bar's item of that name. "Below the note" was called "Display: Needle / Wave" until Phase 26. The tuner's palette button and its "A4 = … Hz" line went in Phase 14, leaving Settings the only place for style and A4.
- **Saved settings:** until Phase 26 a failed read ended the settings flow, so the defaults stayed for the rest of the session. The meter looks `ROLLING_SCALE`, `COMET` and `STUDIO_METER` went in Phase 25.
- **Custom tunings:** Phase 16. Until Phase 26 Save picked the tuning in a second DataStore write, and the editor had no "Discard changes?" question.

## UI basics
- **Navigation bar:** Phase 19 built it with three tabs. The Instrument item came on 2026-10-15, when the owner moved the instrument and tuning from a button above the strings into the bar; it was labelled "Tuning" until 2026-10-16, renamed because "Tuner" and "Tuning" looked alike. It went on 2026-10-19 (owner: "it is really just a function for the main tuner and not a separate page", and it never lit up): its headstock became the instrument button at the start of the row above the strings, beside the then quiet, tappable "Guitar · Drop D" label, and in the Practice tools. Rejected then (rough mock-up docs/design/instrument-button-preview.html, all "cluttery"): one button holding the name on two lines, two buttons side by side, two stacked; and the instrument in the mode switch ("Guitar | Chromatic"). Until Phase 26 it did nothing without the microphone, and turning the phone lost every screen's saved state (a `Row` for the rail and a `Column` for the bar).
- **Two-pane controls pane:** its 56 dp clearance was first made for the string-lock tip under the strings and stayed when the tip went in Phase 24.
- **Layout on its side:** until 2026-10-17 a phone on its side showed the compact layout (one big meter) by default, and the switch "Full layout on its side" (`keepTwoPaneLandscape`, key `keep_two_pane_landscape`) kept the two-pane layout instead. That surprised anyone who turned the phone by accident, and with auto-rotate off the big meter never showed, so in Phase 36 the owner turned it around into focus mode (`focusMode`, off by default; the app wasn't released, so the old key is not read).
- **Compact tuner without the rail:** until Phase 36 the compact layout hid the navigation rail (`TunerScreen` told `MainActivity` through `onCompactChange`, and `AppNavigationFrame` had a `hidden` flag) so the meter had the whole width, and a tap brought the rail back for 5 s with the controls; the app turned only with the phone. Since focus mode, the whole app stays on its side and the rail stayed.
- **Focus mode beside the rail:** from Phase 36 until Phase 40 (2026-10-19) focus mode's compact tuner kept the rail and the status bar, and showed the big note (112 dp) with the cents, the frequency and a small string row (or the wave with chromatic numbers) beside the meter, which left the meter about 490 dp wide on the Xiaomi 12X. The owner asked for the meter as big as possible with the rest much smaller or gone, so the plain tuner went full screen with only a small note in its corner (the rail hidden again, as before Phase 36, but the app stays on its side on every tab).
- **TalkBack pacing:** until 2026-10-16 each `NoteDisplay` held its own throttle, so every pluck and every "Play a string" was said at once.

## Tuner screen
- **Top corners:** the navigation bar replaced the settings gear (and a temporary metronome button beside it) in Phase 19; until then the mode switch kept clear of the gear. The palette button that opened a style sheet went in Phase 14, as did the "A4 = … Hz" line.
- **Mode switch:** the "Strings" segment was called "Guitar" until Phase 15, when other instruments came and the instrument-and-tuning button took the instrument's name.
- **Instrument and tuning:** Phase 15 had two menus ("Standard ▾ Guitar ▾", one-line shrinking in `PickerRow.kt`); Phase 16 replaced them with one button above the strings ("Guitar · Standard ⌄", `CombinedPicker`) that opened the panel. On 2026-10-15 the owner moved the panel's entry into the navigation bar's Instrument item. Phase 26 turned the row's start into a quiet label naming the instrument and tuning, and made the panel open scrolled to the ticked tuning (at first it asked from outside the sheet, often before the rows existed, so it opened at the top).
- **Strum check switch:** until 2026-10-15 it sat at the end of the instrument-and-tuning button's row. Until 2026-10-16 it sat at the string row's edge, up to 22 dp past a bass's last string; the first fix read the buttons' positions as alignment lines and left stale positions when the tuning changed, so the inset now comes from the row's fit rules.
- **Strum panel:** before Phase 26 a waiting panel showed its in-tune bands green.
- **String lock tip:** until Phase 24 a one-time balloon in the tuner's own layout ("Tap a string to lock it", `StringLockTip`, setting `stringLockTipSeen`, `StringLockTipStylesTest`) taught the string lock, squeezed into the corner beside the instrument-and-tuning button or above it where a large font left no room. The tour took over; its strings step was called "Lock a string" until Phase 26. The old key `string_lock_tip_seen` stays in existing installs and still counts as a saved setting for `decideTourOnce`.
- **Tour dim and placement:** until 2026-10-15 the dim was the background at 62 % (light themes mixed 10 % with ink) and the owner found the lit part hardly stood out; the balloon's pointer ended 2 dp from the spotlight rather than from the part's element. The reading order (`tourOrder`) came the same day, because the balloons jumped around.

## Meter, wave and styles
- **Meter looks:** Phase 13 brought the meter looks; Phase 25 (October 2026, `docs/design/meter-looks-round2-preview.html`) made every look show the note its own way. *Rolling scale*, *Comet* and *Studio meter* went (a saved one reads as Classic; the "CENTS" label under Classic's needle was the studio meter's), Light arc and Spirit level lost their numbers (the spirit level became straight), and *Eclipse*, *Arrows* and *Light bars* joined. Phase 26 made every resting centre and the eclipse's resting sun muted, so a meter without a note never looks in tune.
- **Open face:** Phase 14 removed the meter's plate, frame and drop shadow and the row of ♭ ♯ under the box. The dial grew from a 124u radius 24u inside the plate to 146u, 17u above the box's bottom, and the scale grew to the open face's larger one.
- **Wave:** the wave once breathed on gently in tune behind a temporary "When in tune" choice (saved key `wave_breathes_in_tune`); the owner picked stillness in October 2026. The strobe was a separate third display until October 2026, when it became the first wave look. Phase 26 added the in-tune check mark to the wave's pill.
- **Visual styles:** Phase 14 (October 2026) set today's six styles; versions before had seven (a saved Prehistoric or Middle Ages opens as Ancient). Ancient's display face was Caesar Dressing until October 2026, when Forum replaced it. The style cards once held per-style hand-drawn dials; the mood cards replaced them.
- **Press effect:** Phase 27 (2026-10-15) replaced Material's ripple everywhere, including the navigation bar's unbounded ripple, with the style's own press light.

## Metronome, widget and watch
- **Metronome screen:** Phase 19 built it; Phase 27 (2026-10-15) added Listen and the press effect. Until Phase 26 a tap on the tempo dial's middle jumped the tempo to 30 or 250. Until 2026-10-18 the dial was 288 dp upright (the tempo's figures 58 units) with 52 dp − and + in its bottom corners (centres 8 units in and 26 up, reaching outside it, so `dialFitting`, `dialFittingAbove` and `stepsBelow` sized the dial round them), and under it Tap tempo and Listen as labelled buttons (min(156 dp, half the row) × 56 dp, labels fitted by `FittedLabel`), a 196 dp Start / Stop with its word and a "Vibrate on the beat" row with a Material switch; the owner found it too crowded. The tool switch's selected segment showed Material's check mark (dropped from 1.5× text with three segments) until the same day.
- **Home-screen widget and watch app:** both Phase 20.
- **Watch microphone permission:** until Phase 26 a dismissed first dialog sent the player to the watch's settings.
- **Chords and Scales pickers:** from 2026-10-17 to 2026-10-18 the Chords tool showed every choice as a button: the twelve roots in two rows of six, the seven chord types four and three (or the five scales, two to a row upright and three on its side), about 22 framed buttons, then the chord's or scale's name in words as a heading (the scale's a polite live region) over the chart. On its side the Chords · Scales choice sat at the top of the end half, as the start half had no room for it, and the scales' start half scrolled by half a row at 2× text. The neck lay sideways on the Xiaomi held upright (about 1.28 dp a unit; upright it would have had about 1.1). The owner found it too cluttered and picked two pickers that open grids (C in docs/design/chords-calm-preview.html).

## Strum check and metronome
- **Strum check phone check (Phase 18):** what each string showed when the panel showed only the last reading (3 runs), against the readings weighed together:

| Strum (others in tune; ✓ = shown in tune) | Played, cents | Before: last reading shown (3 runs) | After: readings together |
|---|---|---|---|
| Guitar, all in tune | 0 | all ✓ · all ✓ · "Listening…" (the stop's click read as a strum) | all ✓ (B3, E4 less sure) |
| A2, G3 | −20, +15 | −20, +15 · −20, +15 · −21, +15 | −20, +15 |
| E2, D3, B3 | −10, +25, −30 | −11, +26, −31 · −17, +24, **+99** · −15, +25, **not heard** | −10, +25, −30 |
| G3, B3, E4 | −15, +10, +20 | −15, +10, +20 · −16, +11, +20 · −15, +11, +20 | −15, +10, +20 (twice) |
| B3 left out | – | not heard ×3 | not heard |
| A2 left out | – | **+61** · not heard (E2 **+6**) · not heard | not heard (read while it rang) |
| Bass (E1 A1 D2 G2), all in tune | 0 | all ✓ | – |
| A1, G2 | −20, +15 | −20, +15 | – |
| D2 left out | – | not heard | – |

- **Unison strings:** until Phase 26 each string of a unison pair knocked the other out in every other refine pass, so they were heard only because the pass count is even; `samplesSinceStrum` was an `Int` and overflowed 12.4 hours after a strum.
- **Metronome and audio focus:** Phase 19 built the metronome (finished 2026-10-14). Until 2026-10-15 it took `AUDIOFOCUS_GAIN`, which paused other apps' music, and stayed silent when focus was refused; the owner's decision 4 dropped audio focus for it. Tempo from music (Listen) came in Phase 27.

## Languages
- **Fonts:** until 2026-10-16 some Turkish and Polish words mixed two faces in Futuristic (Orbitron lacks ğ İ ş and ą ć ę ł ń ś ź ż); Ancient's former face, Caesar Dressing, lacked them too.

## Testing
- **Every screen in every style:** `SettingsStylesTest` came in Phase 14 and `MetronomeStylesTest` in Phase 19. The Phase 14 check ran them all in every style (`--all --thorough`) and on the Xiaomi 12X by hand (each style light and dark, upright and on its side, at 1.25× and 2× text).
- **Every language:** the first run (2026-10-16) found the mode switch, Listen's line and the rail's names cut off and fixed them.
- **Quick and thorough runs:** from 2026-10-16 every run was briefly thorough by default, as Phase 23's first item had planned; the owner preferred quick runs by day. The Phase 23 check (`--all --thorough`, overnight 2026-10-15/16) found tests written while their runs were deferred, and the tour stuck on step 2 (its Next button held an earlier `::next`, which Compose takes for the same function; Next now reads the step when tapped).
