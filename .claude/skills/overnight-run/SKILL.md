---
name: overnight-run
description: Run Twangle's overnight on-device check while the owner sleeps — first the night's translations (/translate), then every UI test in every style, then every language at 2× text where a screen changed since it last passed — fix what it finds, re-check the fixes, tick the roadmap and land it through a PR, without asking the owner anything. Use when the owner says "start the overnight run", "run the overnight tests", "/overnight-run", or plugs the phone in at bedtime for the tests the roadmap left for the night.
argument-hint: "[optional: --sweep or --languages to run only one part, --full to skip no language check, --store for the store screenshots]"
---

# The overnight run

The owner starts it at bedtime with a test phone on USB and goes to sleep, taking turns between the
Xiaomi 12X and the Galaxy S26 from one night to the next; say in the summary which phone it was. It runs for hours, so
the owner can't be asked anything: take the recommended answer to every question, note it, and put
it in the morning summary for them to double-check. That never covers a step that reaches Google Play
(`scripts/release.sh upload`, `promote`, `rollout`, `halt`, `listing`) or publishes anything (the website,
the privacy policy): those wait for the owner's yes in the morning. CLAUDE.md's hard rules, Git workflow and
environment notes apply; the [ui-test skill](../ui-test/SKILL.md) says how to read results and
what the phone's failures mean.

## 1. Pre-flight (a few minutes, no phone run yet)
Run it in the main folder, which belongs to the overnight run and releases (CLAUDE.md, *One worktree
per session that changes files*): the release reads its summary there. If another session has the
main folder on a branch, wait for it or ask in the morning summary; never switch it away under them.

```bash
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"   # adb is not on PATH (CLAUDE.md)
git status --short && git switch main && git pull
gh run list --branch main --limit 3          # a failed nightly run is fixed first (not one GitHub didn't run: roadmap-step, step 0)
"$ADB" devices                                # f813c98c or R3GL502L08P, "device"; more than one: export ANDROID_SERIAL
"$ADB" shell dumpsys battery | grep -E "temp|AC"   # charging, below ~35 °C
"$ADB" shell settings get system font_scale   # 1.0
"$ADB" shell settings get global stay_on_while_plugged_in   # non-zero: the screen stays on
```
- The script keeps Windows awake while it runs (a sleeping PC drops the phone: on 2026-10-19 it
  cost the night, and on 2026-10-27 again, as the keep-awake never loaded until it was fixed that
  day). It prints a warning in its first ten seconds when it can't; then put that in the summary.
  A closed laptop lid still sleeps it, so the laptop stays open.
- A Twangle on the phone signed by the owner's other machine is uninstalled and installed again by
  `scripts/ui-tests.sh` (the owner's rule); it says so in the log.
- The run works on either test phone, the Xiaomi 12X or the Galaxy S26 (`R3GL502L08P`, API 37):
  the tests give the same answer on any phone (docs/architecture/testing.md, *Any phone*). A
  failure on one phone only is a test that still assumes a phone, or a real bug on that kind of
  phone: find out which, never skip it.
- Check no other session or sub-agent holds the phone (ListAgents; `scripts/ui-tests.sh` also
  takes a lock on the phone and exits 5 if another run has it).
- Read ROADMAP.md: phases may have items marked "Runs overnight", and *Waiting on the owner* may
  have an open overnight item. The run below covers all of them; note which items it closes.
- **Translations first** (owner, 2026-10-28): run the [translate skill](../translate/SKILL.md) on
  `main`. By day texts are written only in English and Dutch; it translates what the other languages
  lack or have stale and lands that through its own PR, so tonight's `@EveryLanguage` checks see the new
  texts. When `scripts/translations.py status` says every language is up to date it does nothing. Then
  `git switch main && git pull` again. Its report goes into the morning summary.
- Branch `chore/overnight-<date>` (or `phase-<N>/<topic>` when it closes a phase's item).
- `./gradlew test lint assembleDebug` must pass before the phone run.

## 2. The run

```bash
scripts/overnight-ui-tests.sh --dry-run                        # which language checks tonight runs (no phone)
scripts/overnight-ui-tests.sh > build/overnight-run.txt 2>&1   # in the background, ~1–7 h
```
It runs `scripts/ui-tests.sh --all --thorough --cool` (every module, every style, one class at a
time, waiting while the phone is 37 °C or warmer; about 55 minutes), then the `@EveryLanguage`
checks one module and style at a time with cooling pauses, and writes `build/overnight/summary.txt`. A screen where a
language didn't fit is saved as a picture under the module's
`build/outputs/connected_android_test_additional_output`; look at it with Read.

**Only the language checks that need it run** (owner, 2026-10-30: with 25 languages all 36 module
and style checks took 6 of the night's 8 hours). A module's checks in a style that passed whole are
noted (`build/ui-tests-passed/languages/`), and a later night skips them while nothing the module
shows changed since (the rule `--since-pass` uses) and the pass is under a week old; the log says
"passed …, nothing it shows changed since", and the summary's second line how many ran. A night after
a change every screen shows (the design system, a new language, the night's translations of texts in
every module) runs nearly all of them, about 7 hours; a quiet one, very few. `--full` runs all
(after a change to the test helpers' scoping, or when in doubt). Skipped checks count as passed for the
release, as `--since-pass` modules do.

**The store screenshots are no longer made at night** (owner, 2026-10-30, they took 50 minutes):
they are made before the store listing is sent, and `scripts/release.sh listing` refuses pictures of
an older app ([release skill](../release/SKILL.md), step 8). Make them only when the owner asks, or
when the morning summary should show a change to them: `scripts/overnight-ui-tests.sh --store` (or
`scripts/store-screenshots.sh`).

- **While it runs, don't edit code, tests or scripts**: each language run rebuilds from the working
  tree, and bash reads a running script as it goes. Docs (ARCHITECTURE.md and docs/architecture/, ROADMAP.md, skills) are
  safe to edit.
- Don't poll; you are told when it finishes. Read the log in between only to start on failures
  early (the sweep prints each class as it finishes).
- Never play test tones, never loop a tone, and never re-run the whole sweep for a fix.

**When store screenshots were made:** look at every language's eight pictures in
`fastlane/metadata/android/<locale>/images/phoneScreenshots/` (a contact sheet per language: this PC
has no ImageMagick, so draw them with PowerShell's System.Drawing, 4 × 2 at 35 %, into
`build/overnight/sheets/`): a caption cut off or set in
the wrong script, a screen without its note or chord, a bubble in the wrong language. Fix and make
only those languages again (`scripts/store-screenshots.sh <locale> …`). The pictures are git-ignored
(docs/store-listing.md, *Phone screenshots*); put the en-US and one other language in the morning
summary.

## 3. Fix what it finds

For each failure decide first: **app bug or outdated test?**
- Read the test and the code. Reproduce with the smallest run (`scripts/ui-tests.sh <module> -c
  Class#method`, `-f` for parameterized names like `test[ANCIENT]`, `--thorough` for style
  variants, `DAY_LANGUAGES=<tag> … --languages` for the failing language's checks, `--all-languages`
  for all of them).
- When it isn't clear, add a temporary probe (a `Log.i("PROBE", …)` read back with
  `adb logcat -d -s PROBE`, or a screenshot to the extra output) and remove it before committing.
- A real bug: fix the app, keep the test. A test that is wrong about the app (a tag on the wrong
  node, a string read in the wrong language, a dialog's own window, rounding): fix the test and say
  why in the commit.
- Things learnt so far: the phone gives Twangle its own language (Dutch), so tests read expected
  strings in the window's language, never from `targetContext` as is; dialogs, menus and sheets are
  windows of their own with the phone's language and text size; Android silences the microphone of
  an app with nothing in the foreground; TalkBack's spoken hint is held back up to 2 s; Compose
  takes function references to a local fun for equal, so a button may hold an old one.
- After a fix run only the failed tests and their area. Land nothing with a red test.

## 4. Land it

- Small focused commits, `./gradlew test lint assembleDebug` again, tick ROADMAP.md (☐ → ☑) for
  the items the run closed, with what was found in brackets, and update the architecture docs where a
  design changed.
- Push, `gh pr create --base main`, `gh pr merge --squash --delete-branch`, `git switch main &&
  git pull` (the owner's policy: merge right away).
- On `main`, when there is time left: `scripts/ui-tests.sh --since-pass`. Every module the sweep
  passed whole is already noted as passed; this runs the ones that failed (or changed with the
  fixes) once more and notes them too, so the next release (the [release skill](../release/SKILL.md))
  has little or nothing left to run.
- Put the phone back as it was: its system settings (font size, rotation, developer options), not
  Twangle's own settings (the owner resets those).

## 5. The morning summary

Plain language, short: what ran and how long, what it found (app bugs first), what was fixed and
how it was checked, what is left, and every choice made on the owner's behalf as a question to
double-check ("I picked X because Y — fine?"). Picture-worthy changes get a phone screenshot.
