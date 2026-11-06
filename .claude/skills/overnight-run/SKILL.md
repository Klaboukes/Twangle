---
name: overnight-run
description: Run Twangle's overnight on-device check while the owner sleeps — first the night's translations (/translate), then every UI test (light and dark), then every language at 2× text where a screen changed since it last passed — fix what it finds, re-check the fixes, tick the roadmap and land it through a PR, without asking the owner anything. Use when the owner says "start the overnight run", "run the overnight tests", "/overnight-run", or plugs the phone in at bedtime for the tests the roadmap left for the night.
argument-hint: "[optional: main or v2 to name the branch, --sweep or --languages to run only one part, --full to skip no language check, --store for the store screenshots]"
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

## 0. Which branch tonight
**One branch a night** (orchestrated with Phase 66, 2026-11-02): `main`, or `v2` while 2.0 is built
there (CLAUDE.md, *The v2 branch*). The owner may name it ("overnight run on v2"); otherwise
`git fetch -q origin && scripts/overnight-ui-tests.sh --which` picks it: `main` when its code changed
since its last clean overnight run (a release needs a clean run over the code it releases), otherwise
`v2` while `origin/v2` exists. It prints the branch and why; that is the first line of the morning
summary. Below, **`<branch>`** is that branch. On `v2`:
- the main folder goes to it with `git switch v2 && git pull` (`git switch` makes the local branch
  that follows `origin/v2` the first time), and back to `main` at the very end (`git switch main`):
  releases expect `main` there;
- there is no nightly CI to check (it runs on `main` only), the translations land on `v2`, and every
  PR goes `--base v2`;
- the scripts keep its records apart by themselves: the summary in `build/overnight/v2/summary.txt`
  and the passes in `build/ui-tests-passed/v2/`, so main's, which a release reads, stay main's.

## 1. Pre-flight (a few minutes, no phone run yet)
Run it in the main folder, which belongs to the overnight run and releases (CLAUDE.md, *One worktree
per session that changes files*): the release reads its summary there. If another session has the
main folder on a branch, wait for it or ask in the morning summary; never switch it away under them.

```bash
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"   # adb is not on PATH (CLAUDE.md)
git status --short && git switch <branch> && git pull
gh run list --branch main --limit 3          # main only: a failed nightly run is fixed first (not one GitHub didn't run: roadmap-step, step 0)
"$ADB" devices                                # the phone (f813c98c or R3GL502L08P) "device"; the watch too, when left on
export ANDROID_SERIAL=<the phone's serial>    # for these checks only: the run picks the phone and the watch itself
"$ADB" shell dumpsys battery | grep -E "temp|AC"   # charging, below ~35 °C
"$ADB" shell settings get system font_scale   # 1.0
"$ADB" shell settings get global stay_on_while_plugged_in   # non-zero: the screen stays on
```
- The script keeps Windows awake while it runs (a sleeping PC drops the phone: on 2026-10-19 it
  cost the night, and on 2026-10-27 again, as the keep-awake never loaded until it was fixed that
  day). It prints a warning in its first ten seconds when it can't; then put that in the summary.
  A closed laptop lid still sleeps it, so the laptop stays open.
- **The devices** (owner, 2026-11-06): the run picks its phone (else the tablet) and the Galaxy Watch 5
  once at the start (`scripts/devices.sh`), so a device that comes or goes during the night changes
  nothing (on 2026-11-05 the watch came on over Wi-Fi a minute in and every phone install stopped on
  "more than one device"). With the watch connected, the watch app's tests run on it first
  (`scripts/ui-tests.sh :wear --thorough`, a few minutes, its screen kept on and its time-out put back).
  No watch: the stage is skipped, which is no failure; a watch that drops off mid-stage isn't either.
  The summary names the phone and what the watch did.
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
  `<branch>`. By day texts are written only in English and Dutch; it translates what the other languages
  lack or have stale and lands that through its own PR, so tonight's `@EveryLanguage` checks see the new
  texts. When `scripts/translations.py status` says every language is up to date it does nothing. Then
  `git switch <branch> && git pull` again. Its report goes into the morning summary.
- Branch `chore/overnight-<date>` (or `phase-<N>/<topic>` when it closes a phase's item) from `<branch>`.
- `./gradlew test lint assembleDebug` must pass before the phone run.

## 2. The run

```bash
scripts/overnight-ui-tests.sh --dry-run                        # which language checks tonight runs (no phone)
scripts/overnight-ui-tests.sh > build/overnight-run.txt 2>&1   # in the background, ~1–3 h
```
It runs `scripts/ui-tests.sh --all --thorough --cool` (every module, light and dark, one class at a
time, waiting while the phone is 37 °C or warmer; 1.1's six styles took about 55 minutes, 2.0's one
look about a third of that), then the `@EveryLanguage`
checks one module at a time with cooling pauses, and writes `build/overnight/summary.txt`
(`build/overnight/v2/summary.txt` on `v2`; the dry run's first line says which). A screen where a
language didn't fit is saved as a picture under the module's
`build/outputs/connected_android_test_additional_output`; look at it with Read.

**Only the language checks that need it run** (owner, 2026-10-30: with 25 languages all 36 module
and style checks of 1.1 took 6 of the night's 8 hours; 2.0 has one look, so six module checks). A module's checks that passed whole are
noted (`build/ui-tests-passed/languages/`), and a later night skips them while nothing the module
shows changed since (the rule `--since-pass` uses) and the pass is under a week old; the log says
"passed …, nothing it shows changed since", and the summary's second line how many ran. A night after
a change every screen shows (the design system, a new language, the night's translations of texts in
every module) runs nearly all of them, about 20 minutes a module; a quiet one, very few. `--full` runs all
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
- Don't poll; you are told when it finishes. To start on failures early (the sweep prints each
  class as it finishes, e.g. `  PhoneScreenTest   3s  1 tests, 0 failed, 2s on the phone`), watch
  the log with the Monitor tool (30 min at most, re-arm on each expiry) and this filter, which
  stays quiet on passing lines: a case-insensitive "fail" matches every "0 failed", and a bare
  "warn" matches test names like `ScreenOffWarningTest`.
  ```bash
  tail -n 0 -F build/overnight-run.txt | grep -E --line-buffered "(^|[^0-9])[1-9][0-9]* failed|FAILED|[Aa]wake|[Ww]arning:|WARN|exit=|[Ee]rror:|ERROR|locked|[Cc]annot|can't|[Cc]rash|^== "
  ```
  It reports a failed class, a keep-awake or lock warning, each new stage (`== …`) and the end
  (`exit=` when the run was started with `; echo "exit=$?" >> build/overnight-run.txt`).
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
  Class#method`, `-f` for parameterized names like `screenshot[TUNE_UP]`, `--thorough` for the double
  checks, `DAY_LANGUAGES=<tag> … --languages` for the failing language's checks, `--all-languages`
  for all of them).
- **Re-check a language fix in every language** (`scripts/ui-tests.sh <module> --thorough
  --all-languages -f 'everyLanguage'`, a few minutes a module), not the failing language
  alone: some fail only after the languages before them. Bengali's About title in Futuristic
  (2026-10-30) never failed on its own, and its first fix ran French off the bar, which only the run
  in every language showed. Then the fixed language alone is no proof either way.
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
- Push, `gh pr create --base <branch>`, `gh pr merge --squash --delete-branch`, `git switch <branch> &&
  git pull` (the owner's policy: merge right away).
- On `<branch>`, when there is time left: `scripts/ui-tests.sh --since-pass`. Every module the sweep
  passed whole is already noted as passed; this runs the ones that failed (or changed with the
  fixes) once more and notes them too, so the next release (the [release skill](../release/SKILL.md))
  has little or nothing left to run.
- Put the phone back as it was: its system settings (font size, rotation, developer options), not
  Twangle's own settings (the owner resets those).
- On `v2`: `git switch main` last, so the main folder is on `main` again for a release.

## 5. The morning summary

Its first line says which branch the night checked and why (`--which`'s reason, or "you asked for
it"), then which phone, and what the watch did (the summary's *Phone* and *Watch* lines). Then plain language, short: what ran and how long, what it found (app bugs first), what was fixed and
how it was checked, what is left, and every choice made on the owner's behalf as a question to
double-check ("I picked X because Y — fine?"). Picture-worthy changes get a phone screenshot.
