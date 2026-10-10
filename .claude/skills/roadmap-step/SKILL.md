---
name: roadmap-step
description: Do the next Twangle roadmap increment (or, with a phase number, every remaining item of that phase) end to end. Announce it in plain language, branch, implement, verify locally (and on the phone when relevant), tick ROADMAP.md, then land it on main through a squash-merged PR. Use when the user says "next roadmap step", "continue the roadmap", "finish phase N", "/roadmap-step", "/roadmap-step <phase number>", or names a specific roadmap item to build.
argument-hint: "[optional: phase number to finish, e.g. 2, or a specific roadmap item]"
---

# Roadmap step

Run [ROADMAP.md](../../../ROADMAP.md) work from start to finish: pick an increment → announce it → branch → build → verify → land on `main`.

**What the argument means:**

| How it's called | What to do |
|---|---|
| `/roadmap-step` (no argument) | **One increment**: the next ☐ item, then stop. |
| `/roadmap-step <number>`, e.g. `/roadmap-step 2` | **Phase mode**: the number is always a **phase**, never an item. Finish *every* remaining ☐ item of that phase in this session, each increment in its own sub-agent so this session stays small (see "Phase mode" below). |
| `/roadmap-step <text>`, e.g. `/roadmap-step tuning presets` | **One increment**: the named item, then stop. |

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

- Phase mode: take the first ☐ item of the requested phase.
- If the user named an item, work on that item.
- Otherwise take the **first ☐ item in the earliest phase that still has ☐ items**.
- One increment = one roadmap item. You may bundle 2–3 *small, tightly linked* items from the same phase when one isn't meaningful alone (e.g. a model class plus its presets). Never span two phases.
- Read the ARCHITECTURE.md sections that cover the item before designing anything.
- If the item can't be done without the user (a decision, a real guitar, a second device, a Play Store account), say so plainly, propose what you *can* do instead, and wait for their answer.

### Phase mode

When called with a phase number N:

1. **Check earlier phases first.** If any phase before N still has ☐ items, tell the user which ones in plain language and ask whether to (a) finish those first or (b) start phase N anyway. Wait for the answer. CLAUDE.md says phases go in order.
2. **Announce the whole phase once** before starting: a plain-language list of the remaining items, grouped into the increments you plan, plus anything the user will need to do along the way (e.g. "keep the phone connected for the last two").
3. **Run each increment in its own sub-agent, one after another.** This session (the "orchestrator") only does pre-flight, the phase announcement, the step-2 announcement of each increment, progress notes and the final report. The heavy work (reading code, building, device checks, PR) happens in a fresh sub-agent per increment, so its file dumps and build logs never enter this session and nothing has to be compacted. For each increment:
   - Post the step-2 announcement to the user.
   - Launch one `general-purpose` sub-agent in the **foreground** (`run_in_background: false`; the next increment depends on it). Never run two increments in parallel: they share `main`, the phone and the Gradle daemon.
   - Use the prompt template below. The sub-agent does steps 3–7 for that one increment: its own branch, local verification (and S26 check when user-facing), ROADMAP tick, squash-merged PR, `main` pulled.
   - Read its report. If it says **BLOCKED**, ask the user here (sub-agents can't ask the user), then send the answer back to the same agent with `SendMessage`, or handle it in the next launch.
   - Pass the report's carry-over into the next sub-agent's prompt.
4. **Between increments**, post a one-line progress note (e.g. "3 of 6 done: tunings and string lookup are in — PR link"). No full report and no question to the user until the end, unless something is blocked.

   **Sub-agent prompt template** (fill in the angle brackets):
   ```
   You are doing ONE increment of the Twangle roadmap, end to end, in C:\source\InTune.
   Increment: phase <N>, "<roadmap item text>" (<plain-language goal, as announced to the owner>).
   First read CLAUDE.md and .claude/skills/roadmap-step/SKILL.md, then follow the skill's steps 3–7
   for this increment only: branch phase-<N>/<topic> from freshly pulled main, build, verify locally
   (./gradlew test lint assembleDebug; on-device check on the S26 if the owner can see or hear it),
   update ARCHITECTURE.md if the design changes, tick the item in ROADMAP.md, push, open the PR, squash-merge,
   switch to main and pull. Skip the skill's announcement and report steps; I talk to the owner.
   Carry-over from earlier increments: <facts not in the repo, or "none">.
   If you need a decision or physical action from the owner, stop without merging and report BLOCKED.
   Final message (under 150 words): DONE or BLOCKED; PR URL; one plain-language line on what changed
   for the app; how it was verified (tests, device yes/no); carry-over facts the next increment needs.
   ```
5. **Stop early only when blocked**: something needs the user (a decision, a real guitar, a device that isn't connected), or a failure you can't fix. Land what's finished, then explain plainly what's left and what you need.
6. When no ☐ items remain in phase N, do a single step-8 report for the whole phase, built from the sub-agents' reports.

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
"$ADB" shell am start -W -n io.github.klaboukes.twangle/com.intune.app.MainActivity
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

Then stop. Without a phase number, one increment per run keeps each session's context small and each PR easy to review.

**Phase mode report:** list every PR landed (one line each, plain language), what the app can do now that the phase is done, how it was checked, any items left open and why, and the next phase. Suggest `/roadmap-step <next phase number>` to continue.
