# Twangle

Twangle is a guitar tuner for Android. Play a string, and Twangle shows which note you played and how far it is from being in tune, on a needle gauge measured in cents.

## Free forever

Twangle is and will always be **free**:

- **No ads.** Not now, not later.
- **No account.** No sign-up, no login, no email address.
- **No tracking.** The app has no internet permission, so it can't send anything anywhere.
- **Your audio stays on your phone.** Sound from the microphone is analysed live and never recorded, saved or uploaded.

## Features

- **Guitar mode:** picks the nearest string of the selected tuning automatically, or tap a string to lock onto it.
- **Chromatic mode:** tunes any note, for any instrument.
- **Tunings:** Standard, Drop D, half-step down, full-step down, DADGAD, Open G, Open D and Open E. Tap the tuning name to switch.
- **Note names:** sharps (C♯), flats (D♭) or Do Re Mi.
- **Your look:** light, dark or follow the phone, with colours from your wallpaper on Android 12+.
- **Keeps the screen on** while you tune (can be turned off).
- **A4 calibration:** set the reference pitch anywhere from 415 to 466 Hz, e.g. 442 Hz to match an orchestra.
- **Accurate:** within ±1 cent on test signals, from low E up to the highest frets.
- The microphone is only on while the tuner is on screen.

Coming later: see the [roadmap](ROADMAP.md).

## Status

Beta. The tuner, its settings and the polish phase are done (Phases 0–5, see the [roadmap archive](ROADMAP-ARCHIVE.md)); release preparation comes next. The app goes on the Play Store at the end of the roadmap.

## How it works

Twangle listens to the microphone, finds the pitch of the note with the [YIN algorithm](http://audition.ens.fr/adc/pdf/2002_JASA_YIN.pdf) (de Cheveigné & Kawahara, 2002), and compares it with the target note. It's written in Kotlin with Jetpack Compose and needs Android 8.0 or newer.

| Module | What it does |
|---|---|
| `:app` | App entry point, theme, wiring |
| `:feature:tuner` | The tuner screen |
| `:feature:settings` | The settings screen |
| `:core:audio` | Microphone capture |
| `:core:settings` | Saved settings |
| `:core:designsystem` | The seven visual styles: colours, fonts, backgrounds and dials |
| `:core:dsp` | Pitch detection, note math, tunings (pure Kotlin, no Android) |

The full design is in [ARCHITECTURE.md](ARCHITECTURE.md).

## Building

You need the Android SDK (API 37) and JDK 17 or newer, for example the one bundled with Android Studio.

```bash
./gradlew assembleDebug   # build the debug APK
./gradlew installDebug    # install it on a connected phone
./gradlew test            # run the unit tests
```

Every push to `main` is built by GitHub Actions, and the debug APK is attached to each run as `twangle-debug-apk`.

## Contributing

Contributor guidelines and project rules are in [CLAUDE.md](CLAUDE.md).

## Fonts

The styles use these typefaces, bundled with the app under the [SIL Open Font License 1.1](core/designsystem/src/main/assets/licenses): Caesar Dressing (Open Window), MedievalSharp (Wojciech Kalinowski), Playfair Display (Claus Eggers Sørensen), Righteous (Astigmatic), Orbitron (Matt McInerney) and Michroma (Vernon Adams).
