---
name: ui-test
description: Run Twangle's on-device UI tests on a test phone (Xiaomi 12X or Galaxy S26), scoped to what changed, with tight timeouts and a short summary. Use whenever UI or instrumented tests need to run, while iterating on a UI change, before landing a change the user can see or hear, or when the user says "run the UI tests", "/ui-test", or "/ui-test <filter>".
argument-hint: "[optional: test-name regex, a :module, --all, --since-pass, --thorough, --cool, --languages, --all-languages or --dry-run]"
---

# UI tests on the phone

Always go through `scripts/ui-tests.sh`, never `./gradlew connectedDebugAndroidTest`. Gradle's task is slower, uninstalls the app (wiping the owner's settings) and floods the session with log lines. The script builds once, runs each module with `am instrument` under a time limit, and prints one line per module. The phones, their serials, setup and quirks are in [docs/devices.md](../../../docs/devices.md). With more than one device connected it picks its own (a phone, else the tablet; `ANDROID_SERIAL` names one instead), and `scripts/ui-tests.sh :wear`, named on its own, runs the watch app's tests on the Galaxy Watch 5.

**As a command:** `/ui-test` runs `scripts/ui-tests.sh`. A `:module`, `--all`, `--since-pass`, `--thorough`, `--cool`, `--languages`, `--all-languages` or `--dry-run` argument is passed on unchanged. Any other text is a name filter: `scripts/ui-tests.sh <module> -f '<text>'` on the module that has those tests (usually `:feature:tuner`). Report the result in plain language.

## Pick the smallest run

Run each one as a single foreground Bash call with the timeout shown, never more "to be safe".

| When | Command | Time | Bash timeout |
|---|---|---|---|
| Iterating on one test or a few | `scripts/ui-tests.sh :feature:tuner -c TunerContentTest#someTest` | ~15 s | 120000 |
| Iterating on one area | `scripts/ui-tests.sh :feature:tuner -f 'Tip\|Hint'` | ~30 s | 180000 |
| **Once before landing** (when the owner hasn't left UI tests for the full check) | `scripts/ui-tests.sh` (the modules this branch touches) | 1–3 min | 300000 |
| Build files or version catalog changed | `scripts/ui-tests.sh`, as above: it widens to the modules a build file touches by itself. Never `--all` by day; every module runs in the full check (owner, 2026-10-28) | 1–5 min | 420000 |
| Before a release (the [release skill](../release/SKILL.md)) | `scripts/ui-tests.sh --since-pass`: the modules changed since they last passed whole on this PC | 0–5 min | 420000 |
| Every double check | only in the full check: `scripts/full-check.sh` (the [full-check skill](../full-check/SKILL.md)), never while iterating | ~45 min | in the background |
| Texts changed or added (any language) | `scripts/ui-tests.sh <module> --languages -f 'everyLanguage'` (English and Dutch only) | ~1 min per module | 300000 |

- `-f` is a regex matched against `Class#method` (a parameterized test is `method[<parameter>]`, e.g. `StoreScreenshots#screenshot[TUNE_UP]`). `-c` takes a class, `Class#method`, or a comma-separated list. `-a key=value` passes an instrumentation argument. `--high-contrast` shows the screens in the high-contrast look (Phase 86, its light and dark where a test loops over both; the full check's pass after the Ink sweep, and for checking a change to that look by day), as `-a highContrast=true`, so it notes no pass.
- `--cool` runs one test class at a time and waits while the phone is 37 °C or warmer (`COOL_BELOW`); use it for anything longer than a few minutes. `--languages` adds the `@EveryLanguage` checks at 2× text in the testers' languages only, English and Dutch (`DAY_LANGUAGES`); `--all-languages` runs them in all 25 (about half an hour a module) and belongs to the full check, never to daily work: the owner has testers only for English and Dutch, so a text too long in another language may wait for the full check (2026-10-26). A screen that didn't fit is saved as a picture under the module's `build/outputs/connected_android_test_additional_output`.
- **The phone heats up.** About 15 minutes of thorough tests, or long screenshot and tone sessions, took the Xiaomi to 45 °C (`adb shell dumpsys battery | grep temp`, in tenths of a degree); then the pixel tests run past their 10 s limits and the owner notices the heat. Use `--cool` for long runs (it keeps the screen on while it waits: turned off, the phone may lock and stop the run), and never re-run a whole sweep that a smaller run already covers. With `--cool` the whole thorough sweep (~1 h) stayed below 37 °C on 2026-10-16.
- A normal run is quick: `@Thorough` tests are skipped. Naming a `@Thorough` test with `-c` runs nothing unless you add `--thorough`. The full suite (`--thorough`) is for the full check only: the owner wants limited tests during normal work, so they never wait for long runs (2026-10-16).
- With no module given, the script picks the affected modules itself (`scripts/ui-test-scope.py`): a module's own sources reach it, a feature's main sources also `:app`, and a change in `:core:dsp`, `:core:settings`, `:core:audio` or `:core:designsystem` Kotlin only the modules whose code uses what changed (the changed member, e.g. `SettingsRepository.setEarExercise`, or the whole declaration), following core code that uses it in turn. The Play it back increment (Phase 55), which touched `:core:dsp` and `:core:settings`, ran six modules (~12 min on the phone) and now runs `:feature:eartraining` and `:app`. The broad set stays for core resources and build files, `:core:testing` and `TwangleTheme`. If only docs, scripts or unit tests changed, or nothing a UI test uses, it says so and exits 0.
- **`--dry-run`** says which modules would run and why, with no build and no phone (seconds). Use it when the pick looks wrong; `python -I scripts/ui-test-scope.py --why <base> [<head>]` does the same for any two commits, and `--self-check` checks the helper. It reads names, not types, so a name used elsewhere too only widens the pick; anything it misses still runs in the full check.
- After the full run, a fix needs only the failed tests plus its area (`-f`). Run the full set again only if the fix touched shared layout code (`TunerContent`, `GuitarControls`, `ModeSlot`, the theme).

## Reading results

- Each module prints a line like `:feature:tuner 60s  147 tests, 0 failed, 56s on the phone`, then `FAIL Class#method: message` or `CRASH in …` lines. Exit code 0 means everything passed, 1 means something failed or timed out, 3 means no phone is connected, 4 means the phone is locked, and 5 means another run is using the phone.
- Stack traces are in `build/ui-tests-<module>.log` and the Gradle output is in `build/ui-tests-build.log`. Grep them only when something failed.
- **Exit 3:** ask the owner to connect a test phone (the Xiaomi 12X or the Galaxy S26; the Lenovo Tab M11 works too) and accept the USB-debugging prompt. Don't land a visible or audible change without a run unless the owner says so, and if they do, note it in the PR.
- **`INSTALL_FAILED_USER_RESTRICTED` / `INSTALL_GRANT_RUNTIME_PERMISSIONS`:** MIUI's *USB debugging (Security settings)* or *Install via USB* is off (docs/devices.md, *Xiaomi quirks*), or the phone's own "Install via USB?" prompt timed out (it picks Deny after 10 s).
- **"test timed out" in `waitForNextChoreographerFrame` or `waitForComposeRoots`, then "Already interrogating!" or "No compose hierarchies found":** the phone's screen was off (its power button) or locked. The first test of the run takes the time-out, so that test looks slow; it isn't. The script wakes the screen before each module, stops with exit 4 if the phone is locked, and prints "The phone's screen went off during this run" when it did. Re-run with the screen on.
- **Tests fail with "No compose hierarchies found" or the app "not displayed" right after an install, or a store screenshot shows a dialog:** look at the screen (`adb exec-out screencap -p`). Play Protect's "Send app for a security check?" may be covering it: tap **Don't send** (docs/devices.md, *Every device*; never *Send*), then run the module again.
- **Exit 4:** ask the owner to unlock the phone and leave its screen on.
- **Layout checks fail that passed before (things "not displayed", rows or tips in the wrong place):** look at the phone's own settings before the code: text size (`settings get system font_scale`, 1.0), navigation (swipe gestures, `settings get secure navigation_mode` 2) and orientation. On 2026-10-14 the Xiaomi had switched to 125 % text and three buttons by itself. The script holds the screen upright during a run and puts auto-rotate back afterwards.
- **Exit 5:** another run, often another Claude session's, holds the phone (a lock on it). A live run touches the lock every 10 s; a lock with no sign of life for a minute belongs to a run that was killed (a stalled sub-agent, a closed terminal), and the next run takes it over by itself, putting back the rotation settings that run saved in it and stopping its tests and its MIUI loop. Two runs at once break each other: an install stops the other run ("Process crashed") and their windows push each other into the background. Wait a minute or two and run again; never work around the lock.
- **Everything slower than usual, first test timing out:** look for leftover `while true; do for p in $(pm list packages …` loops (`adb shell ps -A -o PID,STIME,ARGS | grep "while true"`). Scripts from before the fix left one running after every run, each costing the phone about a third of a core; kill the ones no running script needs (`adb shell kill <pid>`). The script's own loop now ends with its run.
- **A hang with `MIUILOG- Permission Denied Activity` in logcat:** the script's MIUI app-op loop wasn't running. Never run tests around the script.
- **The laptop side died mid-run:** the tests carry on on the phone. Stop them with `adb shell am force-stop <package>.test` before the next run.
- After a deliberate restyle, a failing pixel or layout check usually means the check hasn't caught up with the new look. Name the check, explain why, and adjust the check rather than the design.
- `:core:audio` records from the real mic, so don't play test tones during it. Its `AndroidTonePlayerTest` plays a few short tones through the speaker itself (about 5 s, at media volume).

## Writing or changing UI tests

Read [writing-tests.md](writing-tests.md) first. It covers the shared-activity rule, quick and thorough checks, timeouts, fast pixel checks and known traps.
