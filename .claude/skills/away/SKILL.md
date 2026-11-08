---
name: away
description: Do a task while the owner is away (asleep, out, afk) without asking them anything — build all of it that can be built, take the recommended answer to every choice, keep each choice easy to undo, write each one down, and when they're back give a short report with every choice as a question to check. Use when the owner gives a task and says they're off to bed, going out, afk, "back in an hour", "don't wait for me", "ik ga slapen", "ik ben even weg", or "/away <task>". The task can itself be a skill ("/away /roadmap-step 88"): follow that skill, with these rules on top.
argument-hint: "<the task, or a skill call such as /roadmap-step 88>"
---

# While the owner is away

The owner hands over a task and leaves: nobody answers a question until they're back. So never stop
to ask. Build what can be built, decide what has to be decided, and save the questions for the report
at the end, where the owner checks each choice. CLAUDE.md's hard rules, Git workflow and worktree
rules apply as always. The [full check](../full-check/SKILL.md) already works this way; when the task
is the full check, just run that.

## 1. Before the owner goes (the first message)

- Repeat the task in one sentence and say what "done" will look like, so a misunderstanding shows
  while they might still read it. Then start; don't wait for a reply.
- Keep the PC awake: `request_keep_awake` with `until: "session_idle"`. It stops idle sleep only, so
  a closed laptop lid still sleeps it; say so in the same message when this is the laptop.
- Work in a worktree of your own (CLAUDE.md, *One worktree per session that changes files*).
- Start a decisions log at `<scratchpad>/away-decisions.md`, so the choices survive a long session
  and its summaries: one line per choice, added the moment it is made.

## 2. While they're away

**Never ask.** No AskUserQuestion, no "shall I…?", no `wrap_up` with `blocked`. When a choice comes
up (a design, a name, a wording, which of two fixes, how far to take something):
1. Pick what you would recommend, the way CLAUDE.md, the architecture docs and the owner's memories
   point (calm, plain, consistent; useful over advanced).
2. Prefer the choice that is easiest to undo: behind the tool's switch, in its own commit, a small
   change over a wide one, an option over a new default.
3. Add it to the log: what you picked, the main alternative, and why, in plain language.

**Mock-ups:** where a design needs one (the owner's rule: mock up first for big visual choices),
make the mock-up page with the options side by side, build the recommended one, and put the page in
the report. Don't open Firefox for it while they're away; that waits for the report.

**Stuck on one part** (it needs the owner's guitar, ears or hands, a device that isn't connected, an
account, a Play step): leave that part, log what is missing, and carry on with the rest of the task.
Stop only when nothing of the task is left that can be done without them.

**Stay in the task.** Finish what was asked. Work beyond it (the next roadmap item, a fix spotted in
passing) is not started: it goes in the report as a proposal, unless the task said to keep going
("finish phase 88 and then carry on").

**Merging:** the owner's policy is unchanged: merge each PR once the local checks pass. Nothing
reaches players until the owner makes a release, so a choice they later reject is a revert, not a
problem. Give each choice the owner might reject its own commit, and list the choices in the PR body
under *Choices to check*.

**Long tasks:** don't stop for context. Run each increment in a sub-agent (as `/roadmap-step <N>`
does) and tell every sub-agent the owner is away: it decides itself, logs its choices in its report,
and never ends on a question. Copy their choices into the log.

**The phone and the room:** UI tests and phone checks are fine (`/ui-test`; keep the phone cool).
Make no sound from the PC or the phone: no test tones, no strum checks through the speaker, no
`scripts/strum-check.sh` (the owner may be asleep nearby). A check that needs sound goes in the
report.

## 3. Still waits for the owner, away or not

These keep their yes, whatever the task says: anything that reaches Google Play
(`scripts/release.sh upload|promote|rollout|halt|listing`), publishing the website
(`scripts/publish-website.sh`), anything sent to someone (an email, a message, a GitHub comment to
another person), anything that costs money, deleting what can't be brought back, and changes to the
owner's accounts or the PC's settings. Get everything ready for them and put the one command in the
report. (Updating and publishing the privacy policy needs no yes; just report it.)

## 4. When they're back: the report

When the task is done (or nothing more can be done without them):
- `PushNotification` with one line, when they said they were out or afk (not at bedtime: it would
  wake them): what was done and how many choices wait, e.g. "Phase 88 done, 3 PRs merged; 4 choices
  to check".
- The closing message, plain and short:
  1. **Done:** what the app can do now, with each PR's link, and how it was checked.
  2. **Not done:** what was left and what it needs from them.
  3. **Choices to check:** every line of the log as a question, numbered, the most important first:
     "I picked X over Y because Z — fine?". Point to the commit or the mock-up page.
  4. **Proposals:** work found beyond the task, in a line each.
- `wrap_up`'s question is the first choice to check, with buttons such as "Fine" and "Change it".
  The owner answers the rest in chat, by number ("2: use Y"); undo or change each rejected choice
  then, in a new branch and PR.
