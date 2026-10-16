# Phase mode: `/roadmap-step <N>`

Finish every remaining ☐ item of phase N in this session. This session is the orchestrator: it does pre-flight (SKILL.md step 0), the announcements, progress notes and the final report. Each increment's real work runs in a fresh sub-agent, so code dumps and build logs never fill this session.

1. **Check earlier phases first.** If a phase before N still has ☐ items (*Owner checks* don't count), name them in plain language. Ask whether to finish those first or start phase N anyway, and wait for the answer. Exceptions, chosen by the owner (2026-10-16): among the music tools (Phases 31–33 and 35) the owner picks the order, so only Phase 28 (the switches) and, for Phase 33, Phase 32 count as earlier; Phase 34 (the tools go live) waits for the other tool phases. Phase 23 (v1.0 on Play) is listed last, but its steps that don't show or ship the app (developer account, Play Console forms, store graphics, the rate row) may be done any time.
2. **Announce the whole phase once.** List the remaining items in plain language, grouped into the planned increments, plus anything the owner must do along the way (e.g. "keep the phone connected for the last two").
3. **For each increment, one after another:**
   - Post the SKILL.md step-2 announcement.
   - Launch one `general-purpose` sub-agent in the foreground (`run_in_background: false`) with the prompt below. Never run two at once: they share `main`, the phone and the Gradle daemon.
   - If it reports **BLOCKED**, ask the owner here, since sub-agents can't. Then send the answer back to the same agent with `SendMessage`.
   - Pass its carry-over facts into the next prompt, and post a one-line progress note (e.g. "3 of 6 done: tunings and string lookup are in, <PR link>").
4. **Stop early only when blocked** by something only the owner can provide, or by a failure you can't fix. Land what's finished, then say plainly what's left and what you need.
5. **Final report**, once no ☐ items remain:
   - One plain-language line per PR landed.
   - What the app can do now.
   - How it was checked.
   - Any items left open, and why.
   - Suggest `/roadmap-step <next phase>`.

**Sub-agent prompt** (fill in the angle brackets):
```
You are doing ONE increment of the Twangle roadmap, end to end, in C:\source\Twangle.
Increment: phase <N>, "<roadmap item text>" (<plain-language goal, as announced to the owner>).
Follow steps 3–7 of .claude/skills/roadmap-step/SKILL.md for this increment only: branch from freshly
pulled main, build, verify (including UI tests and the phone check when the owner can see or hear it),
tick ROADMAP.md, then push, open the PR, squash-merge and pull main. I talk to the owner, so skip
the announcement and the report.
Carry-over from earlier increments: <facts not in the repo, or "none">.
If you need a decision or physical action from the owner, stop without merging and report BLOCKED.
Final message (under 150 words): DONE or BLOCKED; PR URL; one plain-language line on what changed
for the app; how it was verified (tests, phone yes/no); carry-over facts the next increment needs.
```
