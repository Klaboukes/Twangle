# InTune

InTune is a guitar tuner for Android. Play a string, and InTune shows which note you played and how far it is from being in tune, on a needle gauge measured in cents.

## Free forever

InTune is and will always be **free**:

- **No ads.** Not now, not later.
- **No account.** No sign-up, no login, no email address.
- **No tracking.** The app has no internet permission, so it can't send anything anywhere.
- **Your audio stays on your phone.** Sound from the microphone is analysed live and never recorded, saved or uploaded.

## Features

- **Guitar mode:** picks the nearest string of the selected tuning automatically.
- **Chromatic mode:** tunes any note, for any instrument.
- **Tunings:** Standard, Drop D, half-step down, full-step down, DADGAD, Open G, Open D and Open E.
- **Accurate:** within ±1 cent on test signals, from low E up to the highest frets.
- The microphone is only on while the tuner is on screen.

Coming later: A4 calibration (415–466 Hz), a tuning picker, manual string selection, sharps/flats/solfège notation, light/dark themes and accessibility support. See the [roadmap](ROADMAP.md) for progress.

## Status

Early development. Pitch detection and microphone capture work; the tuner screen itself is being built now (Phase 3 of the [roadmap](ROADMAP.md)). The app is not on the Play Store yet.

## How it works

InTune listens to the microphone, finds the pitch of the note with the [YIN algorithm](http://audition.ens.fr/adc/pdf/2002_JASA_YIN.pdf) (de Cheveigné & Kawahara, 2002), and compares it with the target note. It's written in Kotlin with Jetpack Compose and needs Android 8.0 or newer.

| Module | What it does |
|---|---|
| `:app` | App entry point, theme, wiring |
| `:feature:tuner` | The tuner screen |
| `:core:audio` | Microphone capture |
| `:core:dsp` | Pitch detection, note math, tunings (pure Kotlin, no Android) |

The full design is in [ARCHITECTURE.md](ARCHITECTURE.md).

## Building

You need the Android SDK (API 37) and JDK 17 or newer, for example the one bundled with Android Studio.

```bash
./gradlew assembleDebug   # build the debug APK
./gradlew installDebug    # install it on a connected phone
./gradlew test            # run the unit tests
```

Every push to `main` is built by GitHub Actions, and the debug APK is attached to each run as `intune-debug-apk`.

## Contributing

Contributor guidelines and project rules are in [CLAUDE.md](CLAUDE.md).
