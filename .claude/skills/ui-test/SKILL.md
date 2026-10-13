---
name: ui-test
description: Run Twangle's on-device UI tests on the test phone (Xiaomi 12X), scoped to what changed, with tight timeouts and a short summary. Use whenever UI or instrumented tests need to run, while iterating on a UI change, before landing a change the user can see or hear, or when the user says "run the UI tests", "/ui-test", or "/ui-test <filter>".
argument-hint: "[optional: test-name regex, a :module, --all, or --thorough]"
---

# UI tests on the phone

Always go through `scripts/ui-tests.sh`, never `./gradlew connectedDebugAndroidTest`. Gradle's task is slower, uninstalls the app (wiping the owner's settings) and floods the session with log lines. The script builds once, runs each module with `am instrument` under a time limit, and prints one line per module.

**As a command:** `/ui-test` runs `scripts/ui-tests.sh`. A `:module`, `--all` or `--thorough` argument is passed on unchanged. Any other text is a name filter: `scripts/ui-tests.sh <module> -f '<text>'` on the module that has those tests (usually `:feature:tuner`). Report the result in plain language.

## Pick the smallest run

Run each one as a single foreground Bash call with the timeout shown, never more "to be safe".

| When | Command | Time | Bash timeout |
|---|---|---|---|
| Iterating on one test or a few | `scripts/ui-tests.sh :feature:tuner -c TunerContentTest#someTest` | ~15 s | 120000 |
| Iterating on one area | `scripts/ui-tests.sh :feature:tuner -f 'Tip\|Hint'` | ~30 s | 180000 |
| **Once before landing** | `scripts/ui-tests.sh` (the modules this branch touches) | 1–2 min | 300000 |
| Build files or version catalog changed | `scripts/ui-tests.sh --all` | ~2 min | 300000 |
| Owner asks for every check, or ROADMAP "The look is final" | `scripts/ui-tests.sh --all --thorough` | ~4½ min | 480000 |

- `-f` is a regex matched against `Class#method`. `-c` takes a class, `Class#method`, or a comma-separated list. `-a key=value` passes an instrumentation argument.
- A normal run leaves out the double checks, so the style tests run in the default style only and `@Thorough` tests are skipped. Naming a `@Thorough` test with `-c` runs nothing unless you add `--thorough`.
- With no module given, the script picks the affected modules itself. If only docs, scripts or unit tests changed, it says so and exits 0.
- After the full run, a fix needs only the failed tests plus its area (`-f`). Run the full set again only if the fix touched shared layout code (`TunerContent`, `GuitarControls`, `ModeSlot`, the theme).

## Reading results

- Each module prints a line like `:feature:tuner 60s  147 tests, 0 failed, 56s on the phone`, then `FAIL Class#method: message` or `CRASH in …` lines. Exit code 0 means everything passed, 1 means something failed or timed out, and 3 means no phone is connected.
- Stack traces are in `build/ui-tests-<module>.log` and the Gradle output is in `build/ui-tests-build.log`. Grep them only when something failed.
- **Exit 3:** ask the owner to connect the Xiaomi 12X and accept the USB-debugging prompt. Don't land a visible or audible change without a run unless the owner says so, and if they do, note it in the PR.
- **`INSTALL_FAILED_USER_RESTRICTED` / `INSTALL_GRANT_RUNTIME_PERMISSIONS`:** MIUI's *USB debugging (Security settings)* or *Install via USB* is off (see CLAUDE.md), or the phone's own "Install via USB?" prompt timed out (it picks Deny after 10 s).
- **"test timed out" in `waitForNextChoreographerFrame`, then "Already interrogating!" or "No compose hierarchies found":** the phone's screen turned off. Re-run.
- **A hang with `MIUILOG- Permission Denied Activity` in logcat:** the script's MIUI app-op loop wasn't running. Never run tests around the script.
- **The laptop side died mid-run:** the tests carry on on the phone. Stop them with `adb shell am force-stop <package>.test` before the next run.
- After a deliberate restyle, a failing pixel or layout check usually means the check hasn't caught up with the new look. Name the check, explain why, and adjust the check rather than the design.
- `:core:audio` records from the real mic, so don't play test tones during it.

## Writing or changing UI tests

Read [writing-tests.md](writing-tests.md) first. It covers the shared-activity rule, quick and thorough checks, timeouts, fast pixel checks and known traps.
