# Twangle

Twangle is a free guitar tuner for Android. Play a string, and Twangle shows which note you played and how far it is from being in tune, on a meter measured in cents. It also has tools for guitarists (strum check, intonation check, chord name, pitch trail), a metronome, chord charts, scales, a capo helper and ear training, tunes many other instruments, and comes with a home-screen widget and a Wear OS watch app.

The website, **https://klaboukes.github.io/** ([Nederlands](https://klaboukes.github.io/nl/)), shows how each tool works and answers common questions ([help](https://klaboukes.github.io/help.html)).

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
- **Instruments:** guitar (6, 7 or 8 strings, or a 12-string tuned as six pairs), bass (4 or 5 strings), ukulele (standard, low G or baritone), violin, viola, cello, mandolin and 5-string banjo (open G, double C or double D).
- **Guitar tunings:** Standard, Drop D, half-step down, full-step down, DADGAD, Open G, Open D, Open E and Open C.
- **Lower any tuning:** by ½ to 2½ steps, your own tunings too, so Drop C, Drop B, C standard or Open G a half step down are there without a long list.
- **Your own tunings:** make, edit and delete tunings of your own for any instrument, unison strings included.
- **Chromatic mode:** tunes any note, for any instrument.
- **A4 calibration:** set the reference pitch anywhere from 415 to 466 Hz, e.g. 442 Hz to match an orchestra.
- **Helpful hints:** "Tune up" or "Tune down", a tip when it's too quiet, too noisy or too loud to hear you well, and a short tour of the screen the first time you open it.
- **Clear when in tune:** a calm glow (you choose how strong), and a short vibration if you like.
- **Accurate:** within ±1 cent on test signals, from the low B of a 5-string bass up to the highest frets.
- **Keeps the screen on** while you tune (can be turned off).

### Tuner tools
From the tools button above the strings, one at a time:
- **Strum check:** strum all strings at once and see, for every string, whether it is flat, in tune, sharp or not heard.
- **12th-fret check:** checks the intonation of a guitar, bass or other fretted instrument. Play a string open, then pressed at the 12th fret, and see how far the fretted octave is off, string by string.
- **Chord name:** strum a chord and see its name ("Am", "C/E") and its notes, with the last few chords below.
- **Pitch trail:** a line of your pitch over the last few seconds, to see bends, vibrato and how steady a note is.

### Practice
- **Metronome:** 30 to 250 beats per minute on a round tempo dial, with − and + buttons and tap tempo. 2/4, 3/4, 4/4, 5/4, 6/8 and 7/8, with an accent on beat 1 and beat lights to follow along.
- **Listen:** play for a few seconds and the metronome suggests your tempo.
- Clicks over your own music or backing track, and can vibrate on the beat. It stops by itself for a phone call or when headphones are unplugged.
- **Chord charts:** 20 chord types on any root, the power chord and 9ths included, drawn as in a songbook with fingers and barres, several shapes for each chord, for the tuning you picked, your own tunings included.
- **Scales:** the seven modes (major, Dorian, Phrygian, Lydian, Mixolydian, natural minor, Locrian), harmonic and melodic minor, Phrygian dominant, major and minor pentatonic, and blues and major blues, with every note on the neck up to the 12th fret and each mode's own colour note ringed. Left-handed players can have the chord charts and the neck drawn mirrored (a switch in Settings).
- **Capo helper:** pick the chord shapes you know and move the capo, and see which chords they sound as and in which key. A tap opens the shape in the chord charts.
- **Ear training:** play back a note Twangle plays while the tuner listens, or name intervals and chord types by ear with buttons. You choose what is asked; no scores are kept, and the microphone is used only by *Play it back*, only while it is on the screen.

### Make it yours
- **One calm look:** light or dark (or as the phone is), with your wallpaper's colours on Android 12 and later.
- **Six meter looks** (Classic, Light arc, Pointers, Loupe, Arrows, Light bars) and **six wave looks** (a strobe among them).
- Light, dark or follow the phone, with colours from your wallpaper on Android 12+.
- **Note names:** C, C♯, D (or with flats), German style with H for B, or Do Re Mi. By default they follow what is usual in your language.
- **25 languages:** English, Dutch, German, French, Spanish, Italian, Portuguese (Brazil), Chinese (Simplified), Chinese (Traditional), Japanese, Korean, Hindi, Bengali, Indonesian, Vietnamese, Thai, Russian, Ukrainian, Turkish, Polish, Swedish, Norwegian, Danish, Finnish and Arabic (right to left). Pick one in the app or follow the phone.
- **Focus mode** (off by default, the button above the tools button, or in Settings): turns the whole app on its side with one big meter, easy to read from a distance, also with auto-rotate off. The tuner goes full screen, with only the note small in a corner; a tap brings back the controls and the tabs.
- Works with TalkBack and large text.

### Widget and watch
- **Home-screen widget:** shows your instrument and tuning, and opens the tuner with one tap.
- **Wear OS watch app:** a chromatic tuner that runs on the watch by itself, with its own microphone, no phone needed.

## Status

Version 1.0.0, getting ready for the Google Play Store. The tuner and its tools, the Practice tab, the widget and the watch app are done; the last checks before launch are listed in the [roadmap](ROADMAP.md), and finished work in the [roadmap archive](ROADMAP-ARCHIVE.md). The phone app needs Android 8.0 or newer, the watch app Wear OS 3 or newer.

## How it works

Twangle listens to the microphone, finds the pitch of the note with the [YIN algorithm](http://audition.ens.fr/adc/pdf/2002_JASA_YIN.pdf) (de Cheveigné & Kawahara, 2002), and compares it with the target note. It's written in Kotlin with Jetpack Compose (Compose for Wear OS on the watch).

| Module | What it does |
|---|---|
| `:app` | App entry point, navigation, wiring, the home-screen widget |
| `:wear` | The Wear OS watch app |
| `:feature:tuner` | The tuner screen |
| `:feature:settings` | The settings screen |
| `:feature:metronome` | The metronome screen |
| `:feature:practice` | The Practice tab: chord charts, scales and the capo helper |
| `:core:audio` | Microphone capture, reference tones and metronome clicks |
| `:core:settings` | Saved settings |
| `:core:designsystem` | The look (Ink, light and dark): colours, shapes, buttons, meter and wave looks |
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

Text is set in the phone's own font. The ♯ and ♭ signs come from a small cut of Noto Sans Symbols (Google), bundled under the [SIL Open Font License 1.1](core/designsystem/src/main/assets/licenses) and renamed Twangle Music Signs as the licence asks of a changed font. The app icons are Google's Material Symbols (Apache License 2.0).
