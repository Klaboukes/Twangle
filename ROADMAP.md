# Twangle — Roadmap

Status legend: ☐ todo · ◐ in progress · ☑ done

Each phase ends in a state that builds and can be demoed. Phases are worked in the order they are listed here, not by number. See [ARCHITECTURE.md](ARCHITECTURE.md) and its topic files for design details.

Phases 0–22, 24–30, 32 and 35 are finished and moved to [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md). Move a phase there once all its items are ☑, and finished owner items too, so only open ones stay below. Ideas not planned yet live in [IDEAS.md](IDEAS.md).

---

## Waiting on the owner
Things only the owner can start. Claude mentions these at the start of a session while they're open.
- ◐ **Find 12+ testers for the closed test (Phase 23):** Google requires new personal developer accounts to run a closed test with at least 12 testers who stay opted in for 14 days in a row before the app can go public. Gather their Google account emails (or a Google Group) early (owner, 2026-10-16: gathering them through their personal network). No hurry: the closed test starts once the music tools are in v1.0 (Phase 34)

## Owner checks (live tests)
Done by the owner by hand, with a real guitar or headset. They are not roadmap increments: `/roadmap-step` skips them.

- ☐ Phase 5: with a real Bluetooth headset connected, the tuner still listens through the phone's mic and reads a string correctly (postponed 2026-10-16: the owner has no Bluetooth headset yet)
- ☐ Phase 15: with a real bass (or a 7-string guitar or cello), check the lowest strings read correctly (B0, E1, B1, C2), and that the tuner shows no note while the amp only hums (postponed 2026-10-16: the owner has no other instrument yet)
- ☐ Phase 20: when you have a Wear OS watch, install Twangle on it and tune a real guitar with it: the note and cents follow the string, the ring glows softly when a string is in tune, and when the watch screen goes dark (or dims to always-on) the microphone icon disappears (postponed 2026-10-16: the owner has no Wear OS watch yet, and the watch app follows in an update, decision 3)
- ☐ Phase 29: with a real guitar (a debug build), turn on the 12th-fret check from the hammer-and-screwdriver button above the strings and check every string: tune it, play it open, then pressed at the 12th fret; check each verdict (fine, or move the saddle back / forward, about how many mm) matches what a guitar shop would say
- ☐ Phase 30: with a real guitar (a debug build), pick "Chord name" from the hammer-and-screwdriver button above the strings and strum a dozen chords you know, open and barre (for example E, A, D, G, C, Am, Em, Dm, E7, G7, Cmaj7, Am7, Dsus4, F and Bm as barre chords, and C with E in the bass): each shows its right name about a second after the strum and stays while it rings, the name doesn't flicker, and a muddled strum says "Not a clear chord" instead of a wrong name
- ☐ Phase 32: with a real guitar (a debug build), open Practice and pick Chords at the top. In standard tuning look up a dozen chords you know (for example C, G, D, A, E, Am, Em, Dm, F, B7, Cmaj7, Dsus4) and play the first shape of each, then step through a few others with ‹ ›: each is comfortable to hold and sounds the chord named, and the notes under the chart are the ones you play. Switch to Scales and play A minor pentatonic, G major and E blues along the neck: every dot is a note of the scale and the root dots are where you expect. Then pick a tuning of your own (or DADGAD) in the Instrument item and look up a few chords and a scale again: the shapes and the neck follow that tuning

---

## Music tools, before v1.0
**Owner decided (2026-10-16):** there is no hurry to release, so the music tools (Phases 29–33) are finished first and go into **v1.0**, not a later version 1.1 as planned earlier that day. Each tool stays hidden in the Play build behind its own switch (Phase 28) until it is finished and has passed the owner's check; then it goes live (Phase 34). The owner's debug builds and the UI tests show every tool. The owner picks the order among Phases 31–33 (`/roadmap-step <phase>`); Phase 33 needs Phase 32, and Phase 34 comes after the others. Phase 23 (the release) is listed last: its steps that show or ship the app (store texts, screenshots, the Baseline Profile, the Play tracks) wait for Phase 34, the others (developer account, Play Console forms, store graphics, the rate row) can be done any time.

Rules for every phase here: no internet, no account, audio never saved, every text translated, TalkBack and 2× text from the start, nothing added to the tuner screen's clutter. A rough mock-up for the owner comes before each new screen or mode. Each phase's live test goes under *Owner checks* when it lands.

**Where they go in the app** (owner agreed, 2026-10-16, and again after asking whether every tool, the metronome included, could share one Tools item in the bar): the bar keeps four items. Five would leave each name about 78 dp on the Xiaomi instead of about 98, too little for long words in 16 languages at 2× text (Dutch "Instellingen" already shrinks with four), and would crowd a calm screen.
- The tools that listen (strum check, 12th-fret check, chord name, pitch trail) stay modes inside the tuner, under the "more tools" button above the strings: they share the tuner's microphone, instrument, tuning and string row, and the 12th-fret check starts by tuning the string. The bar doesn't change for them. The button shows a hammer and screwdriver so it is easy to find (Phase 35, done).
- The Metronome tab becomes **Practice** (Phase 32), holding the Metronome, Chords and the Capo helper, with a small switch at the top like the tuner's mode switch. It opens on the tool used last (saved), so someone who mostly uses the metronome still reaches it in one tap. The bar becomes Tuner · Instrument · Practice · Settings. Chords and the Capo helper follow the instrument picked in the Instrument item. "Practice" rather than "Tools": it is short in every language ("Oefenen", "Üben"; "Gereedschap" and "Werkzeuge" would shrink like "Instellingen").
- Later tools that don't listen (ear training, a drone, a practice timer) go into Practice too, so the bar doesn't change again.

## Phase 31 — Pitch trail
Goal: a line that draws the note over the last few seconds, so a held note, a bend or vibrato shows how steady it is; also for singers and violinists.
- ☐ Preallocated history of readings (no allocation per frame), drawn in the draw phase only, like the strobe
- ☐ Rough mock-up of the trail in two or three styles for the owner to pick from
- ☐ The mode inside the tuner behind its switch (Phase 28), in every style, light and dark; TalkBack describes it in words; JVM and UI tests; frame times measured on the phone, like the meter looks (`scripts/meter-frames.sh`)
- ☐ Owner check added: hold a note, bend a string and play vibrato, and check the line shows it clearly

## Phase 33 — Capo helper
Goal: "the song is in E♭, where does the capo go to play G shapes?", and which shapes sound which chord with the capo on. Needs Phase 32.
- ☐ `:core:dsp`: capo maths (the fret for a key and a set of shapes, and what a shape sounds like with the capo on, in the player's note names); pure and tested
- ☐ Rough mock-up for the owner to pick from
- ☐ The Capo helper in Practice, behind its switch (Phase 28), linked to the chord charts; translated, TalkBack and 2× text; JVM and UI tests; phone check
- ☐ Owner check added: work out the capo for two songs you know and play them

## Phase 34 — The tools go live in v1.0
Goal: the finished tools reach users in v1.0. Comes after the other tool phases and before Phase 23's store steps.
- ☐ Each tool's owner check passed (*Owner checks*); the owner picks which go live, and a tool that isn't ready stays hidden (it can follow in an update)
- ☐ Their switches turn on for release, or the switch and its checks go altogether; `ReleaseToolSwitchesTest` and `ToolSwitchesOffTest` updated to match, and docs/architecture/performance.md (*Release build*)
- ☐ Privacy policy checked for the new tools (they listen only like the tuner; new saved choices such as the Practice tool used last) in English and Dutch, and published with `scripts/publish-privacy.sh`
- ☐ docs/store-listing.md updated: the tools that go live among the strong points, and a tool or two in the screenshot plan (the owner picks)

---

## Phase 23 — Go live on the Play Store → **v1.0**
Goal: Twangle is public on Google Play. Needs the owner's Play developer account. Plan at least 3 weeks of calendar time: Google's closed test alone takes 14 days. Comes last: the music tools go live first (Phase 34), and the steps that show or ship the app wait for that.
- ☑ The look is final: run `scripts/ui-tests.sh --all --thorough`, fix what it finds, and make those double checks part of every run again (every style, and the `@Thorough` tests; until then a normal run leaves them out so restyling stays quick) (2026-10-16: `--all --thorough` passed after the fixes above; the double checks run in every overnight run; owner, 2026-10-16: normal work keeps the quick runs, so they are not part of every run)
- ☑ Style backgrounds reworked to abstract patterns, picked by the owner from [docs/design/style-backgrounds-preview.html](docs/design/style-backgrounds-preview.html): Ancient rock layers, Retro supergraphic, Contemporary silk lines, Futuristic glowing grid fading away (no horizon line), Space Age halftone; Classic unchanged
- ☑ Ancient's lettering calmer: Forum (antique Roman letters, with Polish, Turkish and Russian) instead of Caesar Dressing, picked by the owner from [docs/design/ancient-font-preview.html](docs/design/ancient-font-preview.html)
- ☐ Baseline Profile regenerated once the look is final and the tools are live (Phase 34) (`./gradlew :app:generateBaselineProfile`; the current one predates the metronome and the navigation bar), with the metronome (or Practice), settings and the live tools' screens added to the generator
- ☐ Developer account ready (owner, with Claude's help): identity and Android-device verification, and a public developer contact email (**decision 5**)
- ☑ Privacy policy brought up to date (every stored setting: instrument, own tunings, metronome, app language, landscape layout; the tour instead of the string-lock tip; the remembered microphone refusal, on the phone and in the watch's own small file, so "stores no settings" no longer holds for the watch; the wave's "when in tune" choice is gone; also the metronome's Listen and the widget) in English and Dutch, and published at a public URL (**decision 5**, owner 2026-10-16: a small separate public repo just for the policy, the code stays private until after release): https://klaboukes.github.io/twangle-privacy/, kept up to date with `scripts/publish-privacy.sh`
- ☑ The contact email (owner, **decision 5**: twangle.tuner@proton.me) in the policy's *Contact* section (English and Dutch, then `scripts/publish-privacy.sh`)
- ☐ The policy URL and the contact email entered in the Play Console (once the developer account is ready)
- ☐ Play Console *App content*: privacy policy, ads (none), app access (no login), content rating questionnaire, target audience (13+), data safety (no data collected or shared), category Music & Audio
- ☐ Play Store listing texts as planned in [docs/store-listing.md](docs/store-listing.md): title "Twangle: Guitar Tuner", short and full description (strong points first: accurate, many instruments, metronome, the music tools that go live in Phase 34, made for everyone with TalkBack and large text, no ads / account / tracking / internet), translated into every supported language; the owner picks one of the two short descriptions
- ☐ Store screenshots made by an on-device test, not by hand: a fixed note through the fake recorder, the eight screens and styles of the plan, placed on a 1080×1920 canvas with a translated caption (the Xiaomi's 20:9 screen is too long for Play as it is), in every language; with the release build's switches (`toolSwitchesFor(debugBuild = false)`, after Phase 34), so they show exactly the tools v1.0 has; run in the overnight run
- ☐ Store graphics: the 512×512 icon and the 1024×500 feature graphic (icon, name and an in-tune meter, no other words); no "free" or "no ads" in any picture or the title (Google's rules)
- ☐ A calm "Rate Twangle" row in About that opens the Play page (no rating pop-up), in every language
- ☐ Store settings: category Music & Audio, up to five tags, contact email and website (**decision 5**); after the internal-track upload, read Play's pre-launch report, its accessibility findings included
- ☑ Watch app as decided (**decision 3**): if it launches now, a real-watch check, the Wear OS form factor opted in, Wear screenshots and its own track; otherwise it follows in an update (owner, 2026-10-16: it follows in an update after v1.0; v1.0 ships the phone app only)
- ☐ Internal track (after Phase 34): install the Play-signed build on the Xiaomi from Play and check the mic, widget and language switch
- ☐ Closed track with 12+ testers for 14 days in a row (see *Waiting on the owner*), collecting their feedback
- ☐ Apply for production access, then a staged rollout to production
