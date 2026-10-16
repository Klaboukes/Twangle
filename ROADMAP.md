# Twangle — Roadmap

Status legend: ☐ todo · ◐ in progress · ☑ done

Each phase ends in a state that builds and can be demoed. Phases are worked in the order they are listed here, not by number. See [ARCHITECTURE.md](ARCHITECTURE.md) and its topic files for design details.

Phases 0–22 and 24–27 are finished and moved to [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md). Move a phase there once all its items are ☑, and finished owner items too, so only open ones stay below. Ideas not planned yet live in [IDEAS.md](IDEAS.md).

---

## Waiting on the owner
Things only the owner can start. Claude mentions these at the start of a session while they're open.
- ◐ **Find 12+ testers for the closed test (Phase 23):** Google requires new personal developer accounts to run a closed test with at least 12 testers who stay opted in for 14 days in a row before the app can go public. Gather their Google account emails (or a Google Group) early (owner, 2026-10-16: gathering them through their personal network)

## Owner checks (live tests)
Done by the owner by hand, with a real guitar or headset. They are not roadmap increments: `/roadmap-step` skips them.

- ☐ Phase 5: with a real Bluetooth headset connected, the tuner still listens through the phone's mic and reads a string correctly (postponed 2026-10-16: the owner has no Bluetooth headset yet)
- ☐ Phase 15: with a real bass (or a 7-string guitar or cello), check the lowest strings read correctly (B0, E1, B1, C2), and that the tuner shows no note while the amp only hums (postponed 2026-10-16: the owner has no other instrument yet)
- ☐ Phase 20: when you have a Wear OS watch, install Twangle on it and tune a real guitar with it: the note and cents follow the string, the ring glows softly when a string is in tune, and when the watch screen goes dark (or dims to always-on) the microphone icon disappears (postponed 2026-10-16: the owner has no Wear OS watch yet, and the watch app follows in an update, decision 3)

---

## Phase 23 — Go live on the Play Store → **v1.0**
Goal: Twangle is public on Google Play. Needs the owner's Play developer account. Plan at least 3 weeks of calendar time: Google's closed test alone takes 14 days.
- ☑ The look is final: run `scripts/ui-tests.sh --all --thorough`, fix what it finds, and make those double checks part of every run again (every style, and the `@Thorough` tests; until then a normal run leaves them out so restyling stays quick) (2026-10-16: `--all --thorough` passed after the fixes above; the double checks run in every overnight run; owner, 2026-10-16: normal work keeps the quick runs, so they are not part of every run)
- ☑ Style backgrounds reworked to abstract patterns, picked by the owner from [docs/design/style-backgrounds-preview.html](docs/design/style-backgrounds-preview.html): Ancient rock layers, Retro supergraphic, Contemporary silk lines, Futuristic glowing grid fading away (no horizon line), Space Age halftone; Classic unchanged
- ☑ Ancient's lettering calmer: Forum (antique Roman letters, with Polish, Turkish and Russian) instead of Caesar Dressing, picked by the owner from [docs/design/ancient-font-preview.html](docs/design/ancient-font-preview.html)
- ☐ Baseline Profile regenerated once the look is final (`./gradlew :app:generateBaselineProfile`; the current one predates the metronome and the navigation bar), with the metronome and settings screens added to the generator
- ☐ Developer account ready (owner, with Claude's help): identity and Android-device verification, and a public developer contact email (**decision 5**)
- ☑ Privacy policy brought up to date (every stored setting: instrument, own tunings, metronome, app language, landscape layout; the tour instead of the string-lock tip; the remembered microphone refusal, on the phone and in the watch's own small file, so "stores no settings" no longer holds for the watch; the wave's "when in tune" choice is gone; also the metronome's Listen and the widget) in English and Dutch, and published at a public URL (**decision 5**, owner 2026-10-16: a small separate public repo just for the policy, the code stays private until after release): https://klaboukes.github.io/twangle-privacy/, kept up to date with `scripts/publish-privacy.sh`
- ☑ The contact email (owner, **decision 5**: twangle.tuner@proton.me) in the policy's *Contact* section (English and Dutch, then `scripts/publish-privacy.sh`)
- ☐ The policy URL and the contact email entered in the Play Console (once the developer account is ready)
- ☐ Play Console *App content*: privacy policy, ads (none), app access (no login), content rating questionnaire, target audience (13+), data safety (no data collected or shared), category Music & Audio
- ☐ Play Store listing texts as planned in [docs/store-listing.md](docs/store-listing.md): title "Twangle: Guitar Tuner", short and full description (strong points first: accurate, many instruments, metronome, made for everyone with TalkBack and large text, no ads / account / tracking / internet), translated into every supported language; the owner picks one of the two short descriptions
- ☐ Store screenshots made by an on-device test, not by hand: a fixed note through the fake recorder, the eight screens and styles of the plan, placed on a 1080×1920 canvas with a translated caption (the Xiaomi's 20:9 screen is too long for Play as it is), in every language; under `ToolSwitchesOffRule`, so they show the v1.0 app (Phase 28); run in the overnight run
- ☐ Store graphics: the 512×512 icon and the 1024×500 feature graphic (icon, name and an in-tune meter, no other words); no "free" or "no ads" in any picture or the title (Google's rules)
- ☐ A calm "Rate Twangle" row in About that opens the Play page (no rating pop-up), in every language
- ☐ Store settings: category Music & Audio, up to five tags, contact email and website (**decision 5**); after the internal-track upload, read Play's pre-launch report, its accessibility findings included
- ☑ Watch app as decided (**decision 3**): if it launches now, a real-watch check, the Wear OS form factor opted in, Wear screenshots and its own track; otherwise it follows in an update (owner, 2026-10-16: it follows in an update after v1.0; v1.0 ships the phone app only)
- ☐ Internal track: install the Play-signed build on the Xiaomi from Play and check the mic, widget and language switch
- ☐ Closed track with 12+ testers for 14 days in a row (see *Waiting on the owner*), collecting their feedback
- ☐ Apply for production access, then a staged rollout to production

---

## After v1.0 → version 1.1
**Owner decided (2026-10-16):** work on the music tools starts now, before v1.0 is out, and v1.0 stays as it is. Every new tool hides in the Play build behind its own switch (Phase 28) until the owner releases it in 1.1 (Phase 34); the owner's debug builds and the UI tests show it. Phase 23's steps and fixes for v1.0 keep landing on `main` as usual. Phase 28 comes first; after it the owner picks the order (`/roadmap-step <phase>`). Phase 33 needs Phase 32.

Rules for every phase here: no internet, no account, audio never saved, every text translated, TalkBack and 2× text from the start, nothing added to the tuner screen's clutter. A rough mock-up for the owner comes before each new screen or mode. Each phase's live test goes under *Owner checks* when it lands.

**Where they go in the app** (owner agreed, 2026-10-16): the bar keeps four items. Five would leave each name about 78 dp on the Xiaomi instead of about 98, too little for long words in 16 languages at 2× text (Dutch "Instellingen" already shrinks with four), and would crowd a calm screen.
- The three tools that listen (Phases 29–31) become modes inside the tuner, next to strings, chromatic and strum check; the bar doesn't change for them.
- The Metronome tab becomes **Practice** (Phase 32), holding the Metronome, Chords and the Capo helper, with a small switch at the top like the tuner's mode switch. It opens on the tool used last (saved), so someone who mostly uses the metronome still reaches it in one tap. The bar becomes Tuner · Instrument · Practice · Settings. Chords and the Capo helper follow the instrument picked in the Instrument item.
- Later tools that don't listen (ear training, a drone, a practice timer) go into Practice too, so the bar doesn't change again.

---

## Phase 28 — Hidden until 1.1
Goal: new tools can land on `main` without changing what v1.0 users see.
- ☑ One switch per after-v1.0 tool (intonation check, chord naming, pitch trail, Practice tab with chords, capo helper), on in debug builds and off in release builds, decided in one place in `:app` and passed down (through `AppContainer`), so a hidden tool leaves no trace in a release build: no mode, tab, setting, tour step or text on screen
- ☑ Tests: a release-variant unit test checks every switch is off there; a debug test checks they are on; a UI test can turn one off to see the v1.0 screen
- ☑ Phase 23's store screenshots and the Baseline Profile see the v1.0 app (switches off), whatever the debug build shows: `ToolSwitchesOffRule` for the screenshot test; the profile's builds are non-debug, which `ReleaseToolSwitchesTest` now checks in them too
- ☑ docs/architecture/performance.md (*Release build*) describes the switches and how a tool goes live: its switch turns on for release (or goes), in Phase 34

## Phase 29 — Intonation check
Goal: tells whether a guitar plays in tune up the neck. Play the open string, then the same string at the 12th fret; Twangle compares the two and says to move that string's saddle back (12th fret sharp) or forward (flat), or that it's fine. One string at a time, with the existing pitch detector.
- ☑ `:core:dsp`: the comparison (the 12th fret should sound exactly one octave, 1200 cents, above the open string) with a tolerance of a few cents worked out in the phase, and a verdict per string; synthetic tests for sharp, flat, fine and a wrong note at the fret (`intonation/Intonation`: 3-cent tolerance, about 1 mm of saddle; over 50 cents is not the octave; every verdict right on every instrument's strings, offset within 0.38 cents)
- ☑ Rough mock-up of the mode (choosing a string, the two steps, the verdict) for the owner to pick from (`docs/design/intonation-preview.html`; owner picked option A on 2026-10-16: a switch like the strum check, with the small icon switches gathered behind one "more options" (^) button so the tuner doesn't fill up with icons as tools are added; millimetres shown only where their accuracy can be guaranteed)
- ☐ The mode inside the tuner, next to strings, chromatic and strum check, behind its switch (Phase 28); every text translated, TalkBack and 2× text; JVM and UI tests (UI tests overnight); phone check with synthetic tones
- ☐ Owner check added: with a real guitar, check each string's verdict matches what a guitar shop would say

## Phase 30 — What chord is this?
Goal: strum a chord and Twangle names it (e.g. "A minor"), building on the strum check's six-strings-at-once detection, pointed at chords instead of tuning.
- ☐ `:core:dsp`: chord naming from a strum, the common chords first (major, minor, 7th, major 7th, minor 7th, sus2, sus4), with synthetic chord tests like the strum check's, open and barre shapes, and no name when the strum isn't a clear chord
- ☐ Rough mock-up of the mode for the owner to pick from
- ☐ The mode inside the tuner behind its switch (Phase 28): the chord's name in the player's note names, translated, TalkBack and 2× text; JVM and UI tests; phone check with synthetic chords
- ☐ Owner check added: strum a dozen real chords and check the names

## Phase 31 — Pitch trail
Goal: a line that draws the note over the last few seconds, so a held note, a bend or vibrato shows how steady it is; also for singers and violinists.
- ☐ Preallocated history of readings (no allocation per frame), drawn in the draw phase only, like the strobe
- ☐ Rough mock-up of the trail in two or three styles for the owner to pick from
- ☐ The mode inside the tuner behind its switch (Phase 28), in every style, light and dark; TalkBack describes it in words; JVM and UI tests; frame times measured on the phone, like the meter looks (`scripts/meter-frames.sh`)
- ☐ Owner check added: hold a note, bend a string and play vibrato, and check the line shows it clearly

## Phase 32 — Practice tab with chord and scale charts
Goal: finger diagrams for the chosen instrument and tuning, own tunings included (a DADGAD player sees DADGAD shapes), and scales on a fretboard; no microphone needed. The Metronome tab becomes Practice.
- ☐ Rough mock-up of the Practice tab, a chord chart and a scale on the fretboard, for the owner to pick from
- ☐ The Metronome tab becomes Practice behind its switch (Phase 28): the bar reads Tuner · Instrument · Practice · Settings, a small switch at the top picks the tool, and it opens on the tool used last (saved); with the switch off it stays the v1.0 Metronome tab
- ☐ `:core:dsp`: playable chord shapes for any instrument and tuning (within a comfortable fret span, open strings where they fit), and scales (major, minor, pentatonic, blues) on any tuning; pure and tested
- ☐ Chords: pick a root and a chord type, see one or more finger diagrams; TalkBack reads a shape in words (string by string, fret and finger)
- ☐ Scales: pick a root and a scale, see it on a fretboard; TalkBack reads it in words
- ☐ Every text translated, 2× text, every style light and dark; JVM and UI tests; phone check
- ☐ Owner check added: look up chords and scales in standard tuning and in a tuning of your own, and play them

## Phase 33 — Capo helper
Goal: "the song is in E♭, where does the capo go to play G shapes?", and which shapes sound which chord with the capo on. Needs Phase 32.
- ☐ `:core:dsp`: capo maths (the fret for a key and a set of shapes, and what a shape sounds like with the capo on, in the player's note names); pure and tested
- ☐ Rough mock-up for the owner to pick from
- ☐ The Capo helper in Practice, behind its switch (Phase 28), linked to the chord charts; translated, TalkBack and 2× text; JVM and UI tests; phone check
- ☐ Owner check added: work out the capo for two songs you know and play them

## Phase 34 — Version 1.1 on the Play Store
Goal: the finished tools reach users. **Condition:** v1.0 is live on Play (Phase 23).
- ☐ The owner picks which finished tools go into 1.1; their switches turn on for release (or go), and unfinished ones stay hidden
- ☐ Version 1.1.0 (`twangle.version`)
- ☐ Privacy policy checked for the new tools (they listen only like the tuner; new saved choices such as the Practice tool used last) and published with `scripts/publish-privacy.sh`
- ☐ Baseline Profile regenerated with the new screens
- ☐ Store listing, screenshots and a short "What's new", in every language
- ☐ Internal track check on the Xiaomi, then a staged rollout
