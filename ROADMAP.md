# Twangle — Roadmap

Status legend: ☐ todo · ◐ in progress · ☑ done

Each phase ends in a state that builds and can be demoed. Phases are worked in the order they are listed here, not by number. See [ARCHITECTURE.md](ARCHITECTURE.md) and its topic files for design details.

Phases 0–22, 24–38 and 40 are finished and moved to [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md), with the overview of the music tools that went into v1.0. Left is the release: Phase 23 (the owner, in the Play Console), then Phase 39, opening the code. Move a phase there once all its items are ☑, and finished owner items too, so only open ones stay below. Ideas not planned yet live in [IDEAS.md](IDEAS.md).

---

## Waiting on the owner
Things only the owner can start. Claude mentions these at the start of a session while they're open.
- ◐ **Find 12+ testers for the closed test (Phase 23):** Google requires new personal developer accounts to run a closed test with at least 12 testers who stay opted in for 14 days in a row before the app can go public. Gather their Google account emails (or a Google Group) early (owner, 2026-10-16: gathering them through their personal network). No hurry: the closed test starts once the developer account and the internal track are ready

## Owner checks (live tests)
Done by the owner by hand, with a real guitar or headset. They are not roadmap increments: `/roadmap-step` skips them.

- ☐ Phase 5: with a real Bluetooth headset connected, the tuner still listens through the phone's mic and reads a string correctly (postponed 2026-10-16: the owner has no Bluetooth headset yet)
- ☐ Phase 15: with a real bass (or a 7-string guitar or cello), check the lowest strings read correctly (B0, E1, B1, C2), and that the tuner shows no note while the amp only hums (postponed 2026-10-16: the owner has no other instrument yet)
- ☐ Phase 20: when you have a Wear OS watch, install Twangle on it and tune a real guitar with it: the note and cents follow the string, the ring glows softly when a string is in tune, and when the watch screen goes dark (or dims to always-on) the microphone icon disappears (postponed 2026-10-16: the owner has no Wear OS watch yet, and the watch app follows in an update, decision 3)
- ☐ Phases 36 and 40: with auto-rotate off and the phone upright, tap the four-corners button at the top right of the tuner, beside Strings / Chromatic: the whole app turns on its side by itself and the tuner fills the whole screen (no tabs, no clock bar), with only the note small in the top corner. Play a string and read the meter from a few steps away. Tap the screen: the controls and the tabs come back for a few seconds. Switch to another tab and back: the screen doesn't turn, and the other tab shows its tabs and clock bar. Turn the phone the other way round: the app follows. Then tap the screen and the four-corners button at the top right: focus mode switches off and the app turns back upright and stays upright. Turn focus mode on again and swipe Back from the screen's edge (maybe twice): it switches off the same way, and the app stays open

---

## Phase 23 — Go live on the Play Store → **v1.0**
Goal: Twangle is public on Google Play. Done by the owner in the Play Console, with Claude's help. Plan at least 3 weeks of calendar time: Google's closed test alone takes 14 days. The account and the Play Console forms can start any time; the tracks need Phase 37's build, and the store listing Phase 38's texts and pictures. The finished preparation (final look, privacy policy, contact email, watch decision) is in [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md).
- ☐ Developer account ready (owner, with Claude's help): identity and Android-device verification, and a public developer contact email (**decision 5**)
- ☐ The policy URL and the contact email entered in the Play Console (once the developer account is ready)
- ☐ Play Console *App content*: privacy policy, ads (none), app access (no login), content rating questionnaire, target audience (13+), data safety (no data collected or shared), category Music & Audio
- ☐ Store listing in the Play Console: the texts, screenshots and graphics from Phase 38, category Music & Audio, up to five tags, contact email and website (**decision 5**); after the internal-track upload, read Play's pre-launch report, its accessibility findings included
- ☐ Internal track: install the Play-signed build on the Xiaomi from Play and check the mic, widget and language switch
- ☐ Closed track with 12+ testers for 14 days in a row (see *Waiting on the owner*), collecting their feedback
- ☐ Apply for production access, then a staged rollout to production

## Phase 39 — Open the code after v1.0
Goal: the Twangle repository goes public as open source, once v1.0 is live on Play. The licence is the **GPL version 3** (owner, 2026-10-17): anyone may copy, change and share the code, but what they build from it stays open under the same licence, so nobody can turn Twangle into a closed app with ads. The bundled fonts keep their own SIL Open Font License, and the AndroidX and Kotlin libraries' Apache 2.0 fits inside GPL 3.
- ☐ `LICENSE` with the full GPL 3 text, and a *Licence* section in README.md naming it
- ☐ README.md says the name "Twangle" and its icon are not covered by the licence, so copies must use their own name and icon
- ☐ Before opening: check the history for anything that shouldn't be public (the owner's personal email in old commits, `local.properties`, keys); the owner decides whether to keep the history or start the public repository fresh
- ☐ The owner makes the repository public; later commits use the GitHub no-reply email
- ☐ While the owner is the only author, the licence can still change; once others contribute, it can't without them, so settle any change before accepting outside code
