# Twangle

Twangle is a free guitar tuner for Android. Play a string, and Twangle shows which note you played and how far it is from being in tune, on a meter measured in cents. It also has a metronome, tunes many other instruments, and comes with a home-screen widget and a Wear OS watch app.

## Free forever

Twangle is and will always be **free**:

- **No ads.** Not now, not later.
- **No account.** No sign-up, no login, no email address.
- **No in-app purchases.** Everything is in the app from the start.
- **No tracking.** No analytics, no crash reporting. The app has no internet permission, so it can't send anything anywhere.
- **Your audio stays on your device.** Sound from the microphone is analysed live and never recorded, saved or uploaded. The microphone is only on while the tuner is on screen.

Your settings stay on your device too (and in Android's own backup, if you have turned that on). The full [privacy policy](PRIVACY.md) ([Nederlands](PRIVACY.nl.md)) explains it in plain language.

## Features

### Tuner
- **Strings mode:** picks the nearest string of the selected tuning automatically, or tap a string to lock onto it.
- **Hear the note:** hold a string to hear it as a plucked string, so you can also tune by ear. The tone follows your A4 setting and fades out by itself.
- **Strum check:** strum all strings at once and see, for every string, whether it is flat, in tune, sharp or not heard.
- **Instruments:** guitar (6, 7 or 8 strings), bass (4 or 5 strings), ukulele (standard or low G), violin, viola, cello and mandolin.
- **Guitar tunings:** Standard, Drop D, half-step down, full-step down, DADGAD, Open G, Open D and Open E.
- **Your own tunings:** make, edit and delete tunings of your own for any instrument, unison strings included.
- **Chromatic mode:** tunes any note, for any instrument.
- **A4 calibration:** set the reference pitch anywhere from 415 to 466 Hz, e.g. 442 Hz to match an orchestra.
- **Helpful hints:** "Tune up" or "Tune down", a tip when it's too quiet, too noisy or too loud to hear you well, and a short tour of the screen the first time you open it.
- **Clear when in tune:** a calm glow (you choose how strong), and a short vibration if you like.
- **Accurate:** within ±1 cent on test signals, from the low B of a 5-string bass up to the highest frets.
- **Keeps the screen on** while you tune (can be turned off).

### Metronome
- 30 to 250 beats per minute on a round tempo dial, with − and + buttons and tap tempo.
- 2/4, 3/4, 4/4, 5/4, 6/8 and 7/8, with an accent on beat 1 and beat lights to follow along.
- Clicks over your own music or backing track, and can vibrate on the beat. It stops by itself for a phone call or when headphones are unplugged.

### Make it yours
- **Six visual styles:** Ancient, Classic, Retro, Contemporary, Futuristic and Space Age, each with its own colours, lettering and background.
- **Six meter looks** (Classic, Light arc, Spirit level, Eclipse, Arrows, Light bars) and **six wave looks** (a strobe among them).
- Light, dark or follow the phone, with colours from your wallpaper on Android 12+.
- **Note names:** C, C♯, D (or with flats), German style with H for B, or Do Re Mi. By default they follow what is usual in your language.
- **16 languages:** English, Dutch, German, French, Spanish, Italian, Portuguese (Brazil), Chinese (Simplified), Japanese, Korean, Hindi, Indonesian, Russian, Turkish, Polish and Arabic (right to left). Pick one in the app or follow the phone.
- A compact layout with a big meter when the phone is on its side.
- Works with TalkBack and large text.

### Widget and watch
- **Home-screen widget:** shows your instrument and tuning, and opens the tuner with one tap.
- **Wear OS watch app:** a chromatic tuner that runs on the watch by itself, with its own microphone, no phone needed.

## Status

Version 1.0.0, getting ready for the Google Play Store. The tuner, metronome, widget and watch app are done; the last checks before launch are listed in the [roadmap](ROADMAP.md), and finished work in the [roadmap archive](ROADMAP-ARCHIVE.md). The phone app needs Android 8.0 or newer, the watch app Wear OS 3 or newer.

## How it works

Twangle listens to the microphone, finds the pitch of the note with the [YIN algorithm](http://audition.ens.fr/adc/pdf/2002_JASA_YIN.pdf) (de Cheveigné & Kawahara, 2002), and compares it with the target note. It's written in Kotlin with Jetpack Compose (Compose for Wear OS on the watch).

| Module | What it does |
|---|---|
| `:app` | App entry point, navigation, wiring, the home-screen widget |
| `:wear` | The Wear OS watch app |
| `:feature:tuner` | The tuner screen |
| `:feature:settings` | The settings screen |
| `:feature:metronome` | The metronome screen |
| `:feature:practice` | The Practice tab: chord charts and scales (in progress, hidden in the Play build) |
| `:core:audio` | Microphone capture, reference tones and metronome clicks |
| `:core:settings` | Saved settings |
| `:core:designsystem` | The six visual styles: colours, fonts, backgrounds, style cards, meter and wave looks |
| `:core:dsp` | Pitch detection, strum check, note math, tunings, plucked-string tones, metronome timing (pure Kotlin, no Android) |

The full design is in [ARCHITECTURE.md](ARCHITECTURE.md) and the topic files it lists.

## Building

You need the Android SDK (API 37) and JDK 17 or newer, for example the one bundled with Android Studio.

```bash
./gradlew assembleDebug        # build the debug APK
./gradlew :app:installDebug    # install it on a connected phone
./gradlew test                 # run the unit tests
```

`./gradlew assembleRelease` builds the shrunk release APKs (phone and watch), `bundleRelease` the bundles for the Play Store. They are signed only on a machine that has the upload key (see [docs/architecture/performance.md](docs/architecture/performance.md), *Release build*); elsewhere it comes out unsigned.

GitHub Actions builds, tests and lints `main` every night. It doesn't keep the APKs; build them locally with `./gradlew assembleDebug`.

## Contributing

Contributor guidelines and project rules are in [CLAUDE.md](CLAUDE.md).

## Fonts

The styles use these typefaces, bundled with the app under the [SIL Open Font License 1.1](core/designsystem/src/main/assets/licenses): Forum (Denis Masharov), Alegreya Sans (Juan Pablo del Peral, Huerta Tipográfica), Playfair Display (Claus Eggers Sørensen), Bodoni Moda (Owen Earl, indestructible type*), Righteous (Astigmatic), Orbitron (Matt McInerney) and Michroma (Vernon Adams). The ♯ and ♭ signs come from a small cut of Noto Sans Symbols (Google).
