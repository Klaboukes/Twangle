---
name: roadmap-step
description: Do the next Twangle roadmap increment (or, with a phase number, every remaining item of that phase) end to end. Announce it in plain language, branch, implement, verify locally (and on the phone when relevant), tick ROADMAP.md, then land it on main through a squash-merged PR. Use when the user says "next roadmap step", "continue the roadmap", "finish phase N", "/roadmap-step", "/roadmap-step <phase number>", or names a specific roadmap item to build.
argument-hint: "[optional: phase number to finish, e.g. 2, or a specific roadmap item]"
---

# Roadmap step

Take one [ROADMAP.md](../../../ROADMAP.md) increment from start to finish: pick → announce → branch → build → verify → land on `main`. CLAUDE.md's hard rules, environment notes and Git workflow all apply. This file doesn't repeat them.

| How it's called | What to do |
|---|---|
| `/roadmap-step` | **One increment**: the next ☐ item, then stop. |
| `/roadmap-step <text>` | **One increment**: the named item, then stop. |
| `/roadmap-step <number>` | **Phase mode**: the number is always a phase, never an item. Finish every remaining ☐ item of that phase. Read [phase-mode.md](phase-mode.md) and follow it instead of the steps below. |

The owner is an experienced software engineer, new to Android, who leaves the code to Claude. Every message to them says, in plain language, what changed for the app and what they need to do, if anything; technical detail only where it matters for them to know. Code details go in the PR.

## 0. Pre-flight

```bash
git status --short                    # must be clean; if not, stop and say what's uncommitted
git switch main && git pull
gh pr list --state open               # a leftover from an earlier session?
gh run list --branch main --limit 1   # did last night's CI run pass?
```
- **Your own worktree** (CLAUDE.md, *One worktree per session that changes files*): this skill edits, so it never works in the main folder. In a worktree the `git switch main && git pull` above is `git fetch -q origin && git switch --detach origin/main`.
- **An open PR from an unfinished step:** finish and land it instead of starting something new. Check out its branch and continue from step 4.
- **The last CI run on `main` failed:** first read `gh run view <id>`. If its annotation says GitHub didn't run it ("not started because… spending limit", "payments have failed", "storage quota has been hit"), the code isn't at fault: tell the owner in one line that the free GitHub minutes or storage are used up (unless they already know, until the month's minutes come back) and carry on. Otherwise fixing it is this iteration: read `gh run view <id> --log-failed`, branch `fix/<topic>`, then go to step 4.
- CI runs once a night (03:00 UTC), not after each merge, so it covers everything merged the day before. Don't start it by hand (`gh workflow run`) unless the owner asks: the free plan's minutes are scarce.
- **ROADMAP.md *Waiting on the owner* has ☐ or ◐ items:** remind the owner of them in one line each before carrying on.
- These are one-shot reads. Never poll CI.

## 1. Pick the increment

- Take the named item, or else the first ☐ item in the earliest phase that still has one, in the order ROADMAP.md lists them (not by phase number). Skip *Owner checks*, which are the owner's hand-run tests. Phases in ROADMAP-ARCHIVE.md are done.
- One increment is one item. You may bundle 2–3 small, tightly linked items from the same phase, but never items from two phases.
- Read ARCHITECTURE.md's Contents and the topic files in `docs/architecture/` that cover the item before designing anything.
- If it can't be done without the owner (a decision, a real guitar, another device, a Play account), say so, offer what you *can* do instead, and wait.

## 2. Announce it (before any code)

> **Next step: <plain-language title>** (Roadmap phase N)
> **What I'll do:** 1–3 sentences, described by what it does for the tuner.
> **Why it matters:** one sentence.
> **What you'll notice:** what changes in the app, or "Nothing visible yet, it's checked with automated tests."
> **Needed from you:** "Nothing", or the exact action (e.g. "keep the phone connected by USB").

Then carry on without waiting for approval, unless step 1 found something that needs the owner.

## 3–4. Branch and build

`git switch -c phase-<N>/<short-kebab-topic>`, then build it in small commits. Update the architecture docs (ARCHITECTURE.md or its topic file) in the same branch if the design changes.

**Texts:** a new or reworded text goes in English (`res/values/strings.xml`) and Dutch (`values-nl`) only; the other languages are translated at night by the [translate skill](../translate/SKILL.md) (docs/architecture/languages.md, *Day and night*). Every new English text gets a translator note right above it, a short XML comment saying what it means and where it shows: `<!-- Under the meter when the string is in tune. -->`. A text removed from English is removed from every language.

## 5. Verify (the local run is the merge gate)

1. `./gradlew test lint assembleDebug --console=plain` must pass with no new lint warnings.
2. **UI tests:** follow the [ui-test skill](../ui-test/SKILL.md). Run only the tests you touch while building, then `scripts/ui-tests.sh` once at the end, after step 1 passes. It decides which modules are affected. Put its summary lines in the PR.
3. **Phone check** for anything the owner can see or hear (UI, audio, permissions, startup):
   ```bash
   ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
   scripts/install-debug.sh --start   # builds, installs and starts the phone app under the phone's lock
   "$ADB" exec-out screencap -p > <scratchpad>/shot.png   # then look at it
   "$ADB" logcat -d -b crash                               # must be empty for our app
   ```
   Not `./gradlew :app:installDebug`: it installs outside the phone's lock, which stops another session's UI test run on the phone (and plain `installDebug` also puts the watch app on it). Exit 5 means another run has the phone: wait a minute or two and run it again. With more than one device connected, `export ANDROID_SERIAL=<serial>` first (docs/devices.md), so the `adb` calls after it go to the same one. If the phone isn't connected, ask the owner to connect it. Skip the check only if the owner says so, and note it in the PR. Pure `:core:dsp` work needs no phone check.

## 6. Update the roadmap

Tick ☐ → ☑, with a short note if useful (a measured accuracy or timing). If an item turned out bigger than expected, split it into new ☐ sub-items rather than ticking it half done. When this ticks a phase's last item, move the phase to ROADMAP-ARCHIVE.md in the same branch (its section and a line in the archive's Contents), and update *Where things stand* at the top of ROADMAP.md (a few short lines about what is open now, not a history: the archive keeps that).

## 7. Land it

Push, `gh pr create --base main --title "Phase N: <title>"`, `gh pr merge --squash --delete-branch`, then `git switch main && git pull`. The PR body has these sections:
- **What:** plain language first, then technical bullets.
- **Verification:** the commands run and their results, and whether the phone check ran.
- The attribution footer.

## 8. Report back, then stop

> **Done: <title>** ([PR link])
> - What the app can do now (or "groundwork, no visible change").
> - How it was checked ("42 automated tests pass", "runs on your Xiaomi").
> - Anything the owner should try or do.
>
> **Next up:** the next roadmap item in one sentence. Start a new session and run `/roadmap-step` to continue.
