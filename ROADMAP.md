# Twangle — Roadmap

Status legend: ☐ todo · ◐ in progress · ☑ done

Each phase ends in a state that builds and can be demoed. See [ARCHITECTURE.md](ARCHITECTURE.md) for design details.

Phases 0–10 (scaffolding, DSP core, audio capture, tuner MVP, settings and tunings, polish → v0.9 beta, release preparation, clearer tuning, note names and visual polish, strobe display, compact landscape mode) are finished and moved to [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md).

---

## Owner checks (live tests)
Done by the owner by hand, with a real guitar or headset. They are not roadmap increments: `/roadmap-step` skips them.

- ☐ Phase 3: tested on at least 2 physical devices with a real guitar
- ☐ Phase 5: with a real Bluetooth headset connected, the tuner still listens through the phone's mic and reads a string correctly
- ☐ Phase 22: native speakers look over the new translations where possible
- ☐ Phase 12: with a real guitar, check the tips feel right: none while playing normally near the phone, "Play a bit louder" when playing very softly from across the room, "Too loud, move the phone back" when strumming hard right next to it
- ☐ Phase 11: on the S26, compare the wave breathing in tune with it holding still, using the temporary switch, and pick one; Claude then removes the other and the switch

---

## Phase 11 — A wave that helps you tune
Goal: the wave view gives the same "getting closer" cue as the strobe instead of being eye candy. Standing still means in tune; the drift speeds up the further off the note is; the shape still shows how far off from a distance.
- ☑ One motion for every wave look: the drift follows the strobe's speed rule (`StrobeMotion.driftSpeed`: still within ½ cent, faster per cent off) from the exact cents, not the in-tune flag, so the last few cents still show. The shape keeps showing distance up to 50 cents (deeper, more pulses further off) and smooths out close to the note. JVM tests for the drift at 0, ±1, ±3, ±5 and ±30 cents
- ☑ Temporary Settings switch "In tune: breathe / hold still", so the owner can compare both on the phone (English and Dutch, saved in settings); the losing option and the switch go after the owner check above (Settings → "When in tune", under the wave looks)
- ☑ Each look carries the cue in its own way, still in tune and drifting at strobe speed off pitch: stripes drift (not only lean), ripples spread out (sharp) or draw in (flat), fireflies drift and line up, dots scroll, the loop tumbles at the beat rate and settles into a ring. The Settings previews move the same way. Stripes only got the drift: the strobe look replaces them in the next step. Ripples swell unevenly off pitch, the loop spins like a coin with its near side sweeping the way the note is off, fireflies glide into an even line on the note; JVM tests for direction, stillness on the note and no jumps
- ☑ The strobe becomes one of the wave looks, so "Display" is just Needle or Wave; a saved Strobe display opens as Wave with the strobe look. **Owner decided:** 6 looks in the 3×2 grid, the strobe replacing the stripes look (final six: strobe, ribbon, dots, fireflies, loop, ripples). The strobe card comes first; a saved stripes look also opens as the strobe. Its marks now fade in and out at the ends, it rests at half height without a note, and on the note it holds still or swells gently with the temporary switch; the separate strobe view is gone (JVM tests for every old saved value)
- ☑ Every look checked in each style, light and dark, with TalkBack, on a phone and tablet on its side (also compact mode) and at 2× font; ARCHITECTURE.md updated; S26 check with short test tones. Screenshots of all six looks in all seven styles found two things to fix: the look names broke mid-word at 2× font in Dutch ("Strobo-scoop"; now two cards to a row from 1.3× and names shrink to fit one line), and the small looks were dim off pitch on dark backgrounds (brighter tint and fireflies). New UI tests: every look stays inside its box in every style, light and dark; TalkBack skips every look; the look cards are radio buttons in English and Dutch; names fit at 1.3× and 2× font

## Phase 12 — How well the tuner hears you
Goal: show how well the guitar comes through without adding clutter. The display itself looks crisp when the sound is good and faint when it isn't, and a tip appears only when the player can do something about it. **Owner decided (2026-10-12):** no separate meters; signal bars only as an optional extra in Settings.
- ☑ Noise-floor estimate in `:core:dsp`: tracks the room's background level between notes, plus a signal-to-noise figure while a note sounds (synthetic tests, no allocations per frame) — `NoiseFloorTracker`: minimum of note-free frames over 2 s; floor within 0.5 dB, SNR within 1 dB from 0 to 40 dB
- ☑ Clarity figure: how clean the pitch is (from YIN's confidence), so the meter shows how hard the tuner has to work — `Clarity.of`: 1 at 20 dB SNR or cleaner, ≈ 0.5 at 6 dB, ≈ 0 at 0 dB, the same for every pitch and timbre
- ☑ A steady hint line, first, because the tips build on it: a message must stay for a minimum time before it changes, and switching between "in tune", "Tune up ↑" and "Tune down ↓" uses a margin (hysteresis) so it no longer flickers on and off near the note. **Owner reported:** the tune-up / tune-down hint flickers on and off close to in tune. JVM tests that feed a reading wobbling around the in-tune edge and check the hint changes at most once — `SteadyHint` (each message settles 0.2 s for a direction, at once for in tune, then stays ≥ 0.5 s) after `TuningCue` (direction kept inside the in-tune window); the shown hint also decides "in tune", so colour, tick and hint agree
- ☑ The display shows how well it hears: the needle and every wave look are drawn crisp and solid with a strong, clean signal and turn faint and soft when it is weak or noisy, in every style, light and dark. This must never be confused with the tuning cue (motion and shape stay the tuning cue) and must not flicker (smoothed, JVM tests for the mapping from volume, noise and clarity to one reception figure) — `ReceptionTracker`: lowest of level (−50…−35 dBFS), SNR (0…20 dB) and clarity, each eased fast up (0.15 s) and slowly down (0.5 s); needle and wave fade to 0.62 opacity with up to 2 dp blur, the resting look unchanged
- ☑ Tips in the existing hint line, only when the player can fix something: too soft ("Play a bit louder"), too noisy ("Too noisy here"), too loud ("Too loud, move the phone back"). They use the steady hint line (no flicker when the level hovers at a limit: separate on and off limits plus a minimum time shown), never hide the tune-up / tune-down cue while a clean note sounds, English and Dutch, read out politely by TalkBack — `TipCue` + its own `SteadyHint`: too loud from a clipping peak (−0.5 / −3 dBFS), too soft from the note's loudest level (< −44 dBFS, so a fading tail never asks), too noisy from the room (−38 / −42 dBFS) or the note's best SNR (< 8 dB); tips settle 0.5–1 s, stay 3 s and hold through the gap between plucks. A tip takes an existing place (the "Play a string" line, the wave's pill, or the frequency under the needle, so the direction stays) on one line, so nothing moves
- ☑ Optional signal bars, for the curious: a Settings switch "Show signal bars" (off by default) adds a small reception icon with bars near the note, like a phone's signal bars; tapping it shows the volume, noise and clarity figures. Its bars change slowly enough not to flicker. English and Dutch, TalkBack — four bars in the free slot at the right of the note's pill (nothing added, nothing moves), lit from the reception figure with a 0.08 margin; between notes the last count stays, dimmed. The tap panel shows volume, background (dB) and clarity (%), updated 4× a second while open
- ☑ Checked in landscape (also compact mode), at 2× font, in every style light and dark (UI tests); S26 check in a quiet and a noisy room with short test tones — compact-landscape tests in all seven styles, light and dark: the bars sit beside the meter, stay reachable at 2× font and are clearly drawn; the longest tip fits one line at 2× font. On the S26 hiss brought "Too noisy here" in place of "Play a string" and cleared after it; a laptop note (−46 dB at the phone) brought "Play a bit louder", one bar, and the figures panel updating live. The laptop can't make the mic clip, so "Too loud" is checked by JVM tests only (owner check above)

## Phase 13 — A meter with a wow factor
Goal: the meter above the note (the half-circle dial and its needle, shown with both displays) is the first thing anyone sees and the app's main selling point, so it should look stunning. Players pick one of six meter looks, the way they pick a wave look. Every look must still read at a glance from arm's length (left = flat, right = sharp, centre = in tune), move smoothly at the screen's full frame rate, and give a calm, satisfying moment when the string reaches the note.

**Owner decided (2026-10-12):** after two rounds of moving mock-ups (Lock-on, Pendulum, Eclipse, Sunrise and Balance scale dropped), the six looks in this order, with Classic as the default for new installs:
1. **Classic**: today's dial, polished with depth, a glass shine, a needle shadow and a little mechanical overshoot when a new string is plucked
2. **Light arc**: a curve of glowing segments that light up from the centre out to the reading with a soft halo and a fading trail
3. **Spirit level**: a bubble in a curved glass tube that floats to the side the note is off and settles; in tune it rests between the two marks
4. **Rolling scale**: the scale is printed on a turning drum seen through a window and rolls past a fixed centre mark, like a ship's compass
5. **Comet**: a bright comet runs along the arc with a glittering tail that settles and softens on the note
6. **Studio meter**: a backlit meter like a recording desk's, with a real needle and a small lamp that softly lights on the note

Also decided: the in-tune moment must be smooth and not gimmicky (a soft glow that eases in, holds gently and fades, no bursts, sparks or flashes), and all six looks share **one art style, as if made by the same artist** (the same lines, numbers, glass, light and glow). The looks take only colours (and at most the font) from the current style; each style's own finishing touches for the meter wait for the theme redesign in Phase 14.

- ☑ Preview for the owner before anything is built: moving mock-ups in three styles (Prehistoric, Contemporary, Space Age), light and dark, as a page the owner opened on the phone; the owner chose the six above
- ☑ Meter look setting: `MeterLook` with the six looks, saved in settings (key `meter_look`; an unknown saved value gives Classic), JVM tests. One shared frame for every look so nothing else on the screen moves: the same 2:1 box and ±50 cents, readings beyond ±50 pinned at the edge, the "in tune" colour and check mark, the reception fade and blur from Phase 12, hidden from TalkBack. Motion lives in the layer and draw phases only, with no allocations per frame (paths and brushes made once), like the needle today. The shared art language (line weights, numbers, glass, light, colour roles) written down in ARCHITECTURE.md — `MeterFrame` hands every look one `MeterReading`; until a look's own art is built it shows the Classic dial, and the meter picker comes next
- ☑ Meter picker in Settings ("Meter", in the Look group next to the wave looks): a 3×2 grid of cards with moving previews, radio buttons for TalkBack, English and Dutch names that fit on one line at 2× font — `MeterLookPicker` above the display choice, shown with both displays; each card draws the look's real art (`MeterLookArt`, moved to `:core:designsystem` `meter/` so tuner and settings share it) through one gentle demo loop, so a look's own art appears in its card as soon as it is built; shares `LookCardGrid` with the wave picker (two to a row from 1.3× font); UI tests for six cards, each tap, TalkBack and names at 2× font; on the S26 the cards move and a pick survives a restart
- ☑ Looks 1 and 2 (Classic polished, Light arc) in the shared art style, coloured by all seven styles, light and dark — the shared art pieces (plate, recessed glass, marks and numbers, needle, marker shadow, glow, colour roles) in `meter/MeterArt.kt` for every look; Classic's needle and shadow only turn in their layers, on a slightly underdamped spring (overshoots about 9 % after a pluck); Light arc's 41 segments light from the centre to the reading with a halo and a fading trail; each look's own spring (`meterFeel`); the in-tune colour change as one eased amount (`MeterReading.tuned`), the glow comes later; JVM tests for the springs and the trail, UI tests in all seven styles light and dark; checked on the S26 with short test tones (Prehistoric light, Space Age dark)
- ☑ Looks 3 and 4 (Spirit level, Rolling scale) in the shared art style, coloured by all seven styles, light and dark — Spirit level: a curved glass tube tinted by the accent, the bubble drawn once and only moved, turned and stretched by its layer (it stretches a little while it moves fast), resting between two marks that fade to "in tune"; Rolling scale: a drum printed from −60 to +60 seen through a window, each mark placed, narrowed and faded by how far round the drum it is (numbers measured once), rolling past a fixed centre line with pointers, the zero bar on the side the note is off; JVM tests for the bubble's place and the drum's maths, UI tests in all seven styles light and dark; checked on the S26 with short test tones (Prehistoric light, Space Age dark)
- ☑ Looks 5 and 6 (Comet, Studio meter) in the shared art style, coloured by all seven styles, light and dark — Comet: the dial with a dotted path, a head drawn once and only moved by its layer, and a tail of the last 0.55 s (a preallocated ring stepped only while there is a tail) that thins, fades and shimmers gently, shortening to nothing as the head settles on the note; Studio meter: the same plate, glass, marks and needle on a face backlit in the accent, a flatter scale around a hidden pivot under a small cover, and a lamp that blends to "in tune" with the in-tune amount (its glow comes with the in-tune moment); JVM tests for the trail and the needle and lamp maths, UI tests for all six looks in all seven styles light and dark; checked on the S26 with short test tones
- ☑ The in-tune moment: each look's soft glow eases in when the note is reached, together with the existing vibration tick, holds gently while the note rings and fades when it leaves; it never restarts while the note holds and never flickers near the edge (it follows the steady hint's in-tune decision). JVM tests for when it starts and ends. It follows Android's "Remove animations" setting (no glow motion then, just the colour change) — `InTuneGlowRule` lights it with the tick and holds it 600 ms through a dropout of the same note; `MeterReading.glow` eases 0.4 s in / 0.6 s out; one shared `TuneGlow` (the mock-up's `tuneGlow`: "in tune" colour, 32 % dark / 16 % light, radial) drawn once per look under its marker and faded only by its layer's alpha, plus the studio lamp's halo; with "Remove animations" colour and glow snap; JVM tests for the rule and the easing, UI tests for every look in all seven styles light and dark; checked on the S26 with short test tones
- ☑ In-tune effect switch in Settings: "Calm" (default, the soft glow) or "Bold" (a stronger, larger glow, still smooth and without bursts), two choices next to the meter picker, saved in settings with an unknown value giving Calm, English and Dutch, TalkBack, JVM tests — `InTuneEffect` (key `in_tune_effect`), "In-tune effect: Calm / Bold" ("Effect bij gestemd: Rustig / Krachtig") as radio rows right under the meter picker, and the cards preview it; Bold is the same eased glow at 1.7 × the alpha (capped at 52 %: 52 % dark, 27 % light) and 1.35 × the size, the studio lamp's halo 1.7 × (capped at 85 %); both follow "Remove animations"; JVM tests for saving, the setters and the Bold numbers, UI tests for the choice (TalkBack, 2× font) and every look glowing inside its box in Bold, more than in Calm; checked on the S26 with short test tones
- ☑ Smooth on the phone: frame times measured on the S26 for every look in chromatic mode (meter moving all the time), no dropped frames at 120 Hz and main-thread work no higher than the needle's today (see the table in ARCHITECTURE.md) — `scripts/meter-frames.sh` (`MeterFramesBenchmark`; the benchmark build hears a synthetic ±30-cent sweep instead of the mic, and release builds never contain it); the meter now asks for 120 Hz while it moves (Android 16 gave it 60 Hz); every look at 119–120 frames/s with at most 0.5 % late frames (most repeat runs none), main thread 17–20 % and 1.4–1.7 ms per frame, against 22 % and 1.8 ms for the old needle on the same build and input; table "Meter looks (Phase 13)" in ARCHITECTURE.md
- ☐ Every look checked in each style, light and dark, with TalkBack, on a phone and tablet on its side (also compact mode, where the meter is biggest) and at 2× font (UI tests: inside its box, clearly drawn, skipped by TalkBack); screenshots of all six looks in all seven styles for the owner; ARCHITECTURE.md updated; S26 check with short test tones

## Phase 14 — New theme styles
Goal: a fresh set of visual styles for the whole app, designed together so every screen, the wave looks and the six meter looks feel like one product. The current Prehistoric → Space Age line may stay, change or be replaced; the owner decides. **Owner decided (2026-10-12):** this phase comes straight after the meter looks.
- ☐ Preview for the owner before anything is built: moving mock-ups of a few theme directions (the tuner screen with a meter look and a wave look, plus Settings), light and dark, as a page the owner can open on the phone. **Owner decides** the set of styles, their names and their order; this phase's list is updated to match
- ☐ Style setting moves to the new set: every old saved style opens as its closest new one (JVM tests for every old saved value); the style picker shows the new set with English and Dutch names
- ☐ Each new style's palette, fonts and background, light and dark, with readable contrast for every text and mark
- ☐ Each style's own finishing touches for the six meter looks and the wave looks, still in the shared art style from Phase 13
- ☐ Every screen checked in each new style, light and dark, with TalkBack, in landscape (also compact mode) and at 2× font (UI tests); screenshots of every style for the owner; ARCHITECTURE.md updated; S26 check

## Phase 15 — More instruments
Goal: tune more than a six-string guitar.
- ☐ `Instrument` model in `:core:dsp` with string counts and preset tunings: 7- and 8-string guitar, 4- and 5-string bass, ukulele (standard and low G), violin, viola, cello, mandolin
- ☐ Low notes: an 8192-sample window when the instrument goes below low E (down to B0 ≈ 30.9 Hz); ±1 cent tests from 30 Hz; CPU per frame measured on the S26
- ☐ Instrument picker (beside the tuning picker), saved in settings; the tuning menu shows only that instrument's tunings
- ☐ String row that fits 4 to 8 strings in portrait, landscape and at 2× font
- ☐ S26 check for each instrument with short test tones

## Phase 16 — Custom tunings
Goal: players can make, name and keep their own tunings.
- ☐ Custom tunings stored in DataStore (name, instrument, one note per string); damaged data falls back to the presets
- ☐ Tuning editor: pick each string's note with ± semitone buttons, name it, save; checks for empty names and duplicates
- ☐ Custom tunings in the tuning menu below the presets, with edit and delete
- ☐ English and Dutch texts, TalkBack and 2× font for the editor; S26 check

## Phase 17 — Reference tone
Goal: the app can play the target note so players can tune by ear.
- ☐ Tone player in `:core:audio` (`AudioTrack`): a short plucked tone at the target pitch that follows A4 calibration and stops by itself after a few seconds
- ☐ Long-press a string (or a play button) to hear it; tap again to stop
- ☐ The tuner ignores the microphone while a tone plays, so it doesn't measure its own sound
- ☐ Volume follows the phone's media volume; TalkBack labels; S26 check by ear

## Phase 18 — Strum all strings
Goal: strum once and see which strings are out of tune.
- ☐ Research spike: polyphonic detection of 6 strings at once in `:core:dsp` (spectrum peaks per expected string, harmonics that overlap); write down the accuracy that is reachable
- ☐ Detector with synthetic chord tests (all six strings, one or two strings off by 10–30 cents, a missing string)
- ☐ "Strum check" view: every string shows flat, in tune or sharp at once, in each style
- ☐ S26 check with recorded strums from short test tones; owner check with a real guitar

## Phase 19 — Metronome
Goal: a simple, steady metronome in the same app.
- ☐ Click generator with sample-exact timing (`AudioTrack`), 30–250 BPM, accent on the first beat, 2/4 to 7/8
- ☐ Metronome screen: tempo dial, ± buttons, tap tempo, beat lights in each style; optional vibration on the beat
- ☐ Stops when the app leaves the screen (no foreground service); tempo and meter saved in settings
- ☐ Timing drift measured over 10 minutes; English and Dutch texts; S26 check

## Phase 20 — Wear OS companion and home-screen widget
Goal: Twangle on the watch and one tap from the home screen.
- ☐ Home-screen widget: shows the chosen tuning and opens the tuner with one tap (the widget never holds the microphone)
- ☐ Wear OS module: a watch tuner that listens with the watch's own mic and reuses `:core:dsp`
- ☐ Watch UI: note, cents and an in-tune ring sized for round screens; mic released when the watch screen turns off
- ☐ Check on a Wear OS emulator; real watch test by the owner

## Phase 21 — Native pitch detector (only if needed)
Goal: a C++ (NDK/Oboe) detector, built only if profiling shows the Kotlin one is too slow (for example for strum check or Wear OS).
- ☐ Decide with measurements from Phases 15, 18 and 20 whether it is needed; if not, close this phase
- ☐ Native YIN behind the `PitchDetector` interface, with the same ±1 cent tests as the Kotlin version
- ☐ CPU and battery compared with the Kotlin detector on the S26

## Phase 22 — More languages
Goal: Twangle speaks the world's major languages before it goes public.
- ☐ `LocalizationTest` checks every supported language, not only Dutch; the in-app language list (Android 13+) shows them all
- ☐ Western European: German, French, Spanish, Italian, Portuguese (Brazil)
- ☐ Asian: Chinese (Simplified), Japanese, Korean, Hindi, Indonesian
- ☐ Russian, Turkish and Polish
- ☐ Arabic, with the screens mirrored right-to-left (string row, settings, wave and dial checked in RTL)
- ☐ Note names that match local habit: German uses H for B, and French, Italian, Spanish and Portuguese start on Do-Re-Mi (only the default; the notation setting still lets players choose)
- ☐ Long words and other scripts fit at 2× font in every style (UI tests); S26 check in a few languages

## Phase 23 — Go live on the Play Store → **v1.0**
Goal: Twangle is public on Google Play. Needs the owner's Play developer account.
- ☐ Play data-safety form (no data collected or shared)
- ☐ Privacy policy published at a public URL (the repo is private), and that URL plus a contact email entered in the Play Console
- ☐ Play Store listing: screenshots, description in every supported language
- ☐ Internal → closed → production tracks
