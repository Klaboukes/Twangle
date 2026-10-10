# InTune — Roadmap

Status legend: ☐ todo · ◐ in progress · ☑ done

Each phase ends in a state that builds and can be demoed. See [ARCHITECTURE.md](ARCHITECTURE.md) for design details.

---

## Phase 0 — Scaffolding
Goal: an empty multi-module project that builds and launches a blank Compose screen.

- ☑ Docs: `CLAUDE.md`, `ARCHITECTURE.md`, `ROADMAP.md`
- ☑ Folder structure and Gradle module skeletons (`:app`, `:feature:tuner`, `:core:audio`, `:core:dsp`)
- ☑ Version catalog `gradle/libs.versions.toml`
- ☑ GitHub repo connected; branch → PR → squash-merge workflow (see CLAUDE.md); `.gitattributes` for line endings
- ☑ Install Android Studio (bundled JDK) and the Android SDK (API 37); generate the Gradle wrapper (9.8.1, checksum-pinned)
- ☑ Bump dependency versions to latest stable (AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00)
- ☑ `MainActivity` + `InTuneApplication` + `AppContainer` + Material 3 theme (dynamic color) + placeholder tuner screen + adaptive launcher icon
- ☑ `./gradlew assembleDebug test lint` passes
- ☑ CI: GitHub Actions workflow running `test`, `lint` and `assembleDebug` on every PR
- ☑ App verified on a device: Samsung Galaxy S26 (Android 16), launches cleanly in dark mode

## Phase 1 — DSP core (`:core:dsp`, pure JVM)
Goal: reliable pitch detection, proven by unit tests, with no device involved.

- ☑ `NoteMapper`: Hz ↔ MIDI ↔ note name/octave/cents, with configurable A4 (415–466 Hz); round-trips 60–1500 Hz within 0.01 cent
- ☑ `Tuning` model + presets: Standard, Drop D, Half-step down, Full-step down, DADGAD, Open G, Open D, Open E
- ☑ Nearest-string lookup for Auto mode (compared in cents; allocation-free; ties go to the lower string)
- ☑ `FrameAssembler` ring buffer (window 4096, hop 1024, no allocations per frame; accepts chunks of any size)
- ☑ `SignalGate`: RMS / dBFS level and DC removal (opens at −50 dBFS, 6 dB hysteresis)
- ☑ `YinPitchDetector`: difference function, CMNDF, absolute threshold, parabolic interpolation, confidence (plus 4 kHz low-pass and adaptive least-squares refinement, see ARCHITECTURE §3)
- ☑ `PitchSmoother`: median filter, octave-jump rejection, EMA on cents, note-change hysteresis (median 5, α 0.3, snap > 50 c, ±15 c hysteresis, octave confirm 3 frames)
- ☑ Test-signal generator (sine, saw, square, weak fundamental, noise, decaying pluck): band-limited, seeded; `testFixtures` so `:core:audio` can reuse it
- ☑ Tests: ±1 cent for 60–1500 Hz synthetic signals; no octave errors on the weak-fundamental case (measured worst: 0.1 c sine, 0.5 c harmonic-rich, < 1 c at 20 dB SNR)
- ☐ Microbenchmark: under 2 ms per 4096-sample frame on the JVM

## Phase 2 — Audio capture (`:core:audio`)
Goal: live microphone frames reach the DSP pipeline.

- ☐ `AudioRecorder` interface + `AndroidAudioRecorder` (48 kHz, mono, PCM float, `Flow<FloatArray>`)
- ☐ Audio source selection: `UNPROCESSED`, then `VOICE_RECOGNITION` as fallback
- ☐ Native sample-rate detection, plus handling for errors and a busy mic
- ☐ `RECORD_AUDIO` permission flow: rationale, denied, permanently denied → settings
- ☐ `FakeAudioRecorder` for tests
- ☐ Temporary debug screen showing raw Hz, confidence and dBFS

## Phase 3 — Tuner MVP (`:feature:tuner`) → **v0.1 (internal)**
Goal: a usable guitar tuner.

- ☐ `TunerViewModel` + `TunerUiState` (PermissionRequired / Listening / Detected / Error)
- ☐ Needle gauge (−50…+50 cents) drawn on a Compose `Canvas`, with animated needle
- ☐ Big note name + octave, frequency, cents readout
- ☐ String row for the selected tuning; detected string highlighted
- ☐ Auto (guitar) mode vs Chromatic mode toggle
- ☐ "In tune" state: |cents| ≤ 5 for ≥ 300 ms → color change
- ☐ Mic is released whenever the screen is not visible
- ☐ Tested on at least 2 physical devices with a real guitar

## Phase 4 — Settings & tunings → **v0.2**
- ☐ DataStore-backed settings repository
- ☐ A4 calibration (415–466 Hz, 1 Hz steps)
- ☐ Tuning picker (presets)
- ☐ Manual string selection (lock target string instead of auto-detect)
- ☐ Notation: sharps ♯ / flats ♭ / solfège (Do-Re-Mi)
- ☐ Theme: system / light / dark, and dynamic color on Android 12+
- ☐ Keep screen on while tuning (toggle)

## Phase 5 — Polish → **v0.9 (beta)**
- ☐ Haptic tick on reaching in-tune
- ☐ Accessibility: TalkBack live region ("E, 3 cents flat"), content descriptions, large-font layout, color-blind-safe in-tune indicator (shape as well as color)
- ☐ Landscape and tablet layouts
- ☐ Performance: profile CPU and battery; switch YIN to FFT-based difference if needed; Baseline Profile
- ☐ Edge cases: Bluetooth headset mic, very noisy room, harmonics on high strings, sustain decay
- ☐ Localization scaffolding (English, Dutch)

## Phase 6 — Release → **v1.0**
- ☐ App icon, adaptive icon, splash screen
- ☐ R8 minify + resource shrinking; release signing config (keystore kept out of the repo)
- ☐ Privacy policy (no audio stored or transmitted) + Play data-safety form
- ☐ Play Store listing: screenshots, description; internal → closed → production tracks

---

## Backlog / future ideas
- Strobe tuner display mode
- More instruments: bass (4/5-string, needs an 8192 window), 7/8-string guitar, ukulele, violin family, mandolin
- Custom user-defined tunings
- Reference tone generator (play the target note)
- Polyphonic "strum all strings" check
- Metronome
- Wear OS companion / home-screen widget
- Native (NDK/Oboe) detector if Kotlin performance is ever insufficient
