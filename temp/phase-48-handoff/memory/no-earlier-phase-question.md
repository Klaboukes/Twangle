---
name: no-earlier-phase-question
description: "In /roadmap-step <N>, don't ask about earlier open phases unless phase N depends on them"
metadata:
  node_type: memory
  type: feedback
  originSessionId: 361a1724-37c7-41c5-b452-54129e00493f
  modified: 2026-10-26T15:32:54.290Z
---

When the owner runs `/roadmap-step <N>`, start phase N directly. Don't ask whether to finish an earlier (lower-numbered or earlier-listed) phase with open items first, unless phase N truly depends on that phase being built first; only then raise it.

**Why:** owner, 2026-10-26: "never ask me again when there is a phase with a lower number still open in the roadmap, unless there is a dependency on the other phase being build first."

**How to apply:** skip phase-mode.md step 1's question (the repo skill was changed to match, PR on 2026-10-26); still mention a real dependency if one exists.
