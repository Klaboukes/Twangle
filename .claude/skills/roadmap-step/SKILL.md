---
name: roadmap-step
description: Do the next InTune roadmap increment end to end. Announce it in plain language, branch, implement, verify locally (and on the phone when relevant), tick ROADMAP.md, then land it on main through a squash-merged PR. Use when the user says "next roadmap step", "continue the roadmap", "/roadmap-step", or names a specific roadmap item to build.
argument-hint: "[optional: specific roadmap item or phase to work on]"
---

# Roadmap step

Run **one increment** of [ROADMAP.md](../../../ROADMAP.md) from start to finish: pick it → announce it → branch → build → verify → land on `main`.

The project owner does not write code. Every message to them must be plain language: what changed for the app and what they need to do (if anything). Keep code details in the PR, not in chat.

Read [CLAUDE.md](../../../CLAUDE.md) first. Its hard rules, environment notes (JAVA_HOME, adb path, Galaxy S26) and Git workflow all apply here.

## 0. Pre-flight (fix the ground before building on it)

```bash
git status --short          # must be clean; if not, stop and tell the user what's uncommitted
git switch main && git pull
gh pr list --state open     # leftovers from an earlier session?
gh run list --branch main --limit 1   # did CI pass on the last merge?
```
`gh` is at `C:\Program Files\GitHub CLI\gh.exe` if it isn't on PATH.

- **Open PR left over:** if it belongs to an unfinished roadmap step, finish and land that one instead of starting something new. Check out its branch and continue from step 4.
- **Last CI run on `main` failed:** fixing it *is* this iteration. Read the log (`gh run view <id> --log-failed`), branch as `fix/<topic>`, fix, then go to step 4.
- These are one-shot reads. Never loop or poll on CI.

## 1. Pick the increment

- If the user passed an argument, work on that item.
- Otherwise take the **first ☐ item in the earliest phase that still has ☐ items**.
- One increment = one roadmap item. You may bundle 2–3 *small, tightly linked* items from the same phase when one isn't meaningful alone (e.g. a model class plus its presets). Never span two phases.
- Read the ARCHITECTURE.md sections that cover the item before designing anything.
- If the item can't be done without the user (a decision, a real guitar, a second device, a Play Store account), say so plainly, propose what you *can* do instead, and wait for their answer.

## 2. Announce it in simple terms (before touching any code)

Post a short message to the user in this shape, with no jargon and no code:

> **Next step: <plain-language title>** (Roadmap phase N)
> **What I'll do:** 1–3 sentences on what gets built, described by what it does for the tuner.
> **Why it matters:** one sentence.
> **What you'll notice:** what changes in the app, or "Nothing visible yet. This is groundwork that's checked with automated tests."
> **Needed from you:** "Nothing", or the exact action (e.g. "keep the phone connected by USB").

Example: *"Next step: teaching the app note names. I'll write the part that turns a frequency like 82.4 Hz into 'E2, 3 cents sharp'. Why it matters: every reading the tuner shows depends on this. What you'll notice: nothing visible yet, it's checked with automated tests. Needed from you: nothing."*

Then continue straight away. Don't wait for approval unless step 1 found something that needs the user.

## 3. Branch

```bash
git switch -c phase-<N>/<short-kebab-topic>    # e.g. phase-1/note-mapper
```

## 4. Build it

- Follow CLAUDE.md hard rules, especially: `:core:dsp` stays Android-free, no allocations in the audio loop, versions only in `gradle/libs.versions.toml`.
- Every DSP change ships with tests (synthetic signals, ±1 cent target).
- Commit in small, focused steps (imperative subject ≤ 72 chars, attribution trailer as instructed by the system).
- If the design differs from ARCHITECTURE.md, update ARCHITECTURE.md in the same branch.

## 5. Verify locally (this is the merge gate)

`main` has no branch protection and no auto-merge (private repo on a free GitHub plan), so **the local run is the gate**. Run exactly what CI runs:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew test lint assembleDebug --console=plain
```
All three must pass with no new lint warnings. Fix and re-run until they do.

**On-device check** for any change the user can see or hear (UI, audio, permissions, app startup):
```bash
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
"$ADB" devices                                   # expect the SM-S942B as "device"
./gradlew installDebug
"$ADB" shell am start -W -n com.intune.app/.MainActivity
"$ADB" exec-out screencap -p > <scratchpad>/shot.png   # then look at it
"$ADB" logcat -d -b crash                         # must be empty for our app
```
- If the phone isn't listed, ask the user to connect it (and check Samsung **Auto Blocker** is off, see CLAUDE.md). Don't land device-facing changes without this check, unless the user explicitly says to skip it. Note any skipped check in the PR.
- Pure `:core:dsp` work needs no device check.

## 6. Update the roadmap

- Tick finished items ☐ → ☑ in ROADMAP.md. Add a short note if useful (e.g. measured accuracy or timing).
- If an item turned out bigger than expected, split it into new ☐ sub-items rather than ticking it half-done.

## 7. Land it

```bash
git push -u origin <branch>
gh pr create --base main --title "Phase N: <title>" --body "<body>"
gh pr merge --squash --delete-branch
git switch main && git pull && git fetch --prune
```
PR body sections:
- **What** (plain language first, then technical bullets)
- **Verification** (commands run + results, device check yes/no)
- the attribution footer

Merging right after the PR opens is intentional (the user chose "merge after local checks"). GitHub CI still runs on `main`, and the next iteration's pre-flight catches any failure.

## 8. Report back (plain language)

> **Done: <title>** — [PR link]
> - What the app can do now that it couldn't before (or "groundwork, no visible change").
> - How it was checked (e.g. "42 automated tests pass" / "runs on your S26").
> - Anything the user should try or do.
>
> **Next up:** one sentence naming the next roadmap item.
> To continue, start a new session and run `/roadmap-step`.

Then stop. One increment per run keeps each session's context small and each PR easy to review.
