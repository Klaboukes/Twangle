# Twangle — Ideas for after v1.0

Not planned yet: ideas the owner wants written down, to pick up after launch only if their condition is met. Each becomes a numbered phase in [ROADMAP.md](ROADMAP.md) once it's chosen, and leaves this file. The music tools became Phases 28–34 on 2026-10-16.

## Custom themes (idea, 2026-10-16)
Let people make their own look on top of the six styles. **Condition:** start only if several closed-test testers or early users ask to change the look. Rules that stay: no internet, no account, readable by the same rule as the six palettes (`StylePaletteTest`), calm by default. The six built-in backgrounds stay drawn in code (they fit every screen, have light, dark and quiet versions, and weigh almost nothing).
1. ☐ **Your own background photo with matching colours:** a "Make your own" card at the end of the style choices; the photo comes from Android's photo picker (no permission), is copied into the app's own storage, shrunk to the screen once, dimmed or softly blurred under a veil of the theme's colour (stronger behind Settings) so the needle and note stay readable; the theme's colours are taken from the photo, like Material You does from the wallpaper. The widget and watch keep their built-in look.
2. ☐ **A small theme builder** around that card's live preview: background (a built-in picture or your photo), main colour, lettering, meter look, light / dark / follow the phone. Twangle works out the other colours and keeps them readable; no editing of every colour one by one.
3. ☐ **On the shelf, only if 1 and 2 prove popular:** a theme code (a short text to save and share themes; a theme with a photo needs a small file instead) and a round trip with the user's own AI app: Twangle shares instructions through Android's share menu, the user chats with any AI, copies the code it gives, and Twangle offers to preview it from the clipboard. Never a live connection (that would need internet and an account).

## After the drone and ear training (idea, 2026-10-26)
What is left of these two ideas now that they are Phases 48 and 54. **Condition:** only once both have landed, and if players use them.
1. ☐ **The drone and the metronome together:** both sounding while you practise, which needs them on one screen (leaving the metronome stops its clicks today)
2. ☐ **A scale or mode by ear:** Twangle plays a scale over the drone, the player names it (major, Dorian, Mixolydian, …)

## Twangle on the iPhone (idea, 2026-10-26)
One codebase for both phones: Kotlin Multiplatform, with Compose Multiplatform drawing the screens on the iPhone. **Condition:** only after v1.0 is on Play, and if the first reviews or testers ask for an iPhone version. A project of its own, not a phase to slip in before the launch (the feasibility look of 2026-10-26).

**What carries over as it is:** the pitch detector and all the music maths (`:core:dsp` has no Android in it), and all sixteen languages (the translations keep their file format). **Shared after a mechanical move:** every screen, the six styles, the meter and wave looks, the metronome and the practice tools; their texts and fonts move to Compose's multiplatform resources (`composeResources`, the same `values-<language>/strings.xml` layout). **Needs an iPhone twin:** the microphone and the sound output (`:core:audio`, behind the same interfaces; `AVAudioSession`'s measurement mode is the iPhone's counterpart of `UNPROCESSED`), haptics, keep screen on, the in-app language switch, the app shell and its navigation. **Written in Swift, not shared:** the home-screen widget (WidgetKit) and an Apple Watch app (watchOS has no Compose; it could still use the Kotlin detector as a framework). The UI-test scripts, the overnight run, the store screenshots and the release scripts speak to Android phones and to Play; each needs an Apple counterpart, and every visible change is then checked on two platforms.

**What it needs that isn't here today:**
- **A Mac.** Apple only lets iPhone apps be built on one (Xcode). A Mac mini is the cheapest; a rented cloud Mac does for building, not for the daily phone check. Claude Code runs on it as it does here; the Windows machines keep the Android work.
- **An iPhone** for the device checks. The simulator can use the Mac's microphone, but the tuner is tested against a real phone's microphone and audio path.
- **Apple's developer programme,** about €99 a year, also for a free app. Every release passes Apple's review (usually a day). The store listing needs a privacy label and a sentence on why the app uses the microphone; both are easy, Twangle records nothing and sends nothing.
- **The licence** (Phase 56): as the sole author the owner can put a GPL 3 app on the App Store; others could not republish the code there. Decide whether that matters when the licence is chosen.

**Rejected routes:** redrawing every screen natively in Swift over the shared Kotlin maths (months of work, two user interfaces to keep in step forever), or a full Swift rewrite (two of everything). Compose draws its own pixels, so Twangle looks the same on both phones, which suits a tuner with six custom styles.
1. ☐ **The modules become multiplatform,** one at a time, with the Android app working unchanged at every step (the UI tests prove it): `:core:dsp` first (its tests keep running on the JVM), then `:core:settings` (DataStore is multiplatform), then the design system and the feature modules with their texts and fonts in `composeResources`.
2. ☐ **The iPhone twins in `:core:audio`:** the recorder (AVAudioEngine in measurement mode, float frames into the same `Flow<FloatArray>`) and the three players (reference tones, clicks, drone), checked on the iPhone against the laptop's test tones.
3. ☐ **The iPhone shell:** the app, navigation bar or rail, microphone permission, keep screen on, haptics, the language switch, opening screen and icon; then the Mac side of the scripts (UI tests on the simulator, screenshots, a release script for App Store Connect).
4. ☐ **On the shelf, only if the phone app finds users:** the widget (WidgetKit) and the Apple Watch app (Swift, with the Kotlin detector as a framework).
