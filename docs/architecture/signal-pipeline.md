# Signal pipeline

Part of §3 of [ARCHITECTURE.md](../../ARCHITECTURE.md), which lists every topic file.

## Contents
- [Pipeline](#pipeline)
- [Key numbers](#key-numbers)
- [Other sample rates](#other-sample-rates)
- [Frame assembly](#frame-assembly)
- [Low notes](#low-notes)
- [Signal gate](#signal-gate)
- [Sustain check (ticks and clicks)](#sustain-check-ticks-and-clicks)
- [Noise floor, SNR and clarity](#noise-floor-snr-and-clarity)
- [Reception](#reception)
- [Tips](#tips)
- [Signal bars (optional)](#signal-bars-optional)
- [Pitch detection (YIN)](#pitch-detection-yin)
- [Smoothing](#smoothing)
- [Edge cases (measured, EdgeCaseTest)](#edge-cases-measured-edgecasetest)

## Pipeline
```
 Microphone
    │  AudioRecord.read()  (dedicated thread, 48 kHz mono float, blocking; built-in mic while a
    │                      Bluetooth headset is connected)
    ▼
 AudioRecorder ──Flow<FloatArray>(chunk = one hop ≈ 21 ms, 1024 samples at 48 kHz)──►
    ▼
 FrameAssembler        ring buffer → overlapping analysis window (≈ 85 ms, 4096 samples at 48 kHz;
                       ≈ 171 ms, 8192 samples, when the tuning goes below the guitar: see Low notes)
    ▼
 SignalGate            RMS level check (< ~-50 dBFS → "no signal"), DC offset removal
    ▼
 SustainCheck          newest hop vs the window's loudest (energy and peak): a tick that has
    │                  died away (> 12 dB down, or > 4 dB and back at the room) is not analysed
    ▼
 PitchDetector (YIN)   [upsample to ≥ 32 kHz if the rate is lower] → 4 kHz low-pass → difference fn
    │                  → CMNDF → absolute threshold → whole-period checks → above-range check
    │                  → least-squares refinement → PitchResult(frequencyHz, confidence), or NONE
    ▼
 NoiseFloorTracker     note-free frames' levels → median of 5 → minimum over 2 s = room's floor;
 + Clarity             with a pitch: SNR over that floor, clarity 0..1 from YIN's confidence
    ▼
 ReceptionTracker      level, SNR and clarity scored 0..1, each smoothed (fast up, slow down);
                       reception = the lowest of the three (needle and wave crisp ↔ faint)
    ▼
 PitchSmoother         Hz → fractional MIDI (NoteMapper.midiOf, honours A4) → onset: 3 agreeing
    │                  frames → octave-jump rejection → median of 5 → EMA (snaps on a new note,
    │                  smaller steps when unsure) → note with ±15-cent hysteresis; a passed
    │                  tick's frames neither start a note nor count as its dropout
    ▼
 NoteMapper / Tuning   note name + octave; in Auto mode → nearest string in tuning, cents from it
    ▼
 TunerViewModel        combine with settings → StateFlow<TunerUiState>
    ▼
 Compose UI            meter, note name, cents or wave, string row, in-tune indicator + haptic
```

## Key numbers
- **Sample rate:** 48 000 Hz, the native rate on most phones. `SampleRateSelector` uses `AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE` if it is 44 100–48 000 Hz (higher only costs CPU), then tries 48 000, 44 100, 32 000, 16 000 and 8 000; the first rate `AudioRecord.getMinBufferSize` accepts is expected. If no input opens at it, `AndroidAudioRecorder` tries the other accepted rates in that order (`openFirst`: every input at a rate before the next rate) and its `config` moves to the rate that opened; the pipelines read the rate again with the first chunk. Any rate from 8 to 96 kHz works end to end (see *Other sample rates*).
- **Analysis window:** 4096 samples (≈ 85 ms), about 7 periods of low E (82.41 Hz); YIN needs at least 2. Tunings that go lower (7- and 8-string guitar, bass, cello) use **8192** (≈ 171 ms, see *Low notes*).
- **Hop:** 1024 samples (≈ 21 ms), ~47 estimates per second.
- **Detection range:** 62–1500 Hz for guitar (τ_max = sampleRate / f_min). The floor sits above 60 Hz mains hum and below drop C (65.4 Hz). With the low-note window: **28–1500 Hz**, so B0 (30.9 Hz) reads even a semitone flat.
- **YIN threshold:** 0.15 (the paper suggests 0.10–0.15).
- **Accuracy target:** ±1 cent on synthetic signals, ±3 cents on recorded guitar (no recordings are in the tests yet: Phase 46).
- **"In tune":** |cents| ≤ 5 held for 300 ms (15 hops at 48 kHz), released past 7 cents, so a note hovering at 5 doesn't flicker. That is **Relaxed**, the default; **Precise** is ≤ 2, released past 3 ([Lifecycle](lifecycle.md), *Settings*). The shown "in tune" goes through the steady hint line (see [Notes, tunings and audio](notes-and-audio.md), *Audio capture*), so it also lasts at least 0.5 s.

## Other sample rates
`AnalysisSettings.forSampleRate(rate)` (8–96 kHz) scales window and hop so they always last ≈ 85 ms and ≈ 21 ms and the smoother's frame counts keep their meaning (44.1 kHz: 3763 / 941 samples). `PitchPipeline` builds its `FrameAssembler` and detector from it, and `AudioConfig.forSampleRate` makes the recorder's chunks one hop long. A rate outside 8–96 kHz ends the flow with `InitializationFailed`.

Below 32 kHz a period spans too few samples for ±1 cent, so `UpsamplingPitchDetector` first interpolates by an integer factor to ≥ 32 kHz (polyphase windowed-sinc FIR: Kaiser β = 8, 32 taps per phase, cut-off 0.45 × input rate), and YIN's low-pass drops to 0.4 × the input rate so no images get through. Measured: every rate from 8 to 96 kHz within 1 cent on sines, saws, plucks and weak fundamentals; 8 kHz costs 0.5 ms per frame on the JVM.

## Frame assembly
`FrameAssembler` keeps a ring buffer of `capacity` samples (4096 by default). `push()` takes chunks of any size and stops as soon as a frame is ready, so none is skipped; `readFrame(out, length)` copies the latest `length` samples (by default the 4096-sample window) oldest-first into a caller-owned array, and `skipFrame()` passes on a frame. First frame after one full window, then one per hop. `PitchPipeline` gives it a capacity of the low-note window (8192 at 48 kHz) so either window can be read at every hop; `available` says how much audio it holds since the mic opened. Allocated once.

## Low notes
`PitchRange` (`:core:dsp`) says how low the detector listens:
- **`STANDARD`:** 62–1500 Hz, 4096 samples. Every guitar tuning, ukulele, violin, viola, mandolin.
- **`LOW`:** 28–1500 Hz, **8192 samples** (`AnalysisSettings.forSampleRate(rate, PitchRange.LOW)`; ≈ 171 ms at any rate). 28 Hz is below B0 at −50 cents (30.0 Hz); τ_max = 1715 lags at 48 kHz, which with the filter's start-up and the fit's margin needs ≈ 8000 samples (W = 6276, ≈ 3.7 periods of the lowest note, like the standard window's 4). The hop stays 1024, so readings still come ~47 times a second and the smoother's frame counts keep their meaning.

`PitchRange.forLowestNote(hz)` picks `LOW` when the tuning's lowest string, **a semitone flat**, is below 62 Hz, at the current A4. So 7- and 8-string guitars, both basses and the cello (C2, 65.4 Hz: a semitone flat is 61.7 Hz) get `LOW`; no guitar preset does, even at A4 = 415 Hz (drop D's D2 at 69 Hz). `TunerOptions.pitchRange` works it out once per tuning or A4 change; `TunerViewModel` passes `{ options.pitchRange }` to `PitchPipeline.readings(range)`, which reads it every frame. The pipeline keeps a frame buffer and a detector for each range (made once per listening session, ≈ 200 KB for the low one) and one assembler holding the long window, so switching the tuning changes the window on the next hop without reopening the mic or losing audio. After the mic opens, low-range readings start once a whole long window has arrived (171 ms instead of 85). Chromatic mode follows the tuning too, so a bass player's chromatic mode also reads low.

The cost: twice the window, about 2.3× the CPU per frame (bigger FFT), and a reading that reflects audio ≈ 43 ms further back on average. Measured in [Threading and performance](performance.md) (*Low-note window on the Xiaomi 12X*).

**Measured (`LowPitchRangeTest`, 48 kHz):** sines 30–1500 Hz within 0.09 cents; sawtooth, square, pluck and weak fundamental within 0.52; every string of every low instrument within 1 cent from a semitone flat to 50 cents sharp, the right note even with a weak fundamental; 20 dB SNR within 0.33, 10 dB within 0.83; nothing below 28 Hz; 8, 16 and 44.1 kHz also within 1 cent; no allocation. *Known limit:* 50 and 60 Hz mains hum are inside the low range, so hum alone shows as a note (G1 / B♭1) and hum 10 dB below a B0 string pulls it up to 8 cents (≈ 2–3 cents from E1 up). A notch filter would fix it if real rooms need it.

## Signal gate
`SignalGate.process(frame)` first measures the **peak** as the mic delivered it (`peakDbfs`, to detect clipping), then removes the DC offset in place and measures the RMS level as `20 · log10(rms)` dBFS (full-scale square 0 dBFS, sine ≈ −3; silence floored at −120). It opens at **−50 dBFS** and closes below **−56 dBFS** (6 dB hysteresis), so a decaying note doesn't flicker. Only frames with the gate open reach the detector.

## Sustain check (ticks and clicks)
A tap on the desk, a clock's tick or a mouse click often rings at a pitch for a few milliseconds. YIN reads that ring, and it stays in the 85 ms window for four hops, so without a check each tick would show a note for ≈ 0.1 s (a 1–1.5 kHz ring sits nearest the high E string, far sharp: "the needle jumps to E4"). A string sustains; a tick dies away. `pitch/SustainCheck` cuts the window from its newest end into blocks of one hop, or one period of the lowest note where that is longer (the low range: 1715 samples, 36 ms, since a bass note's 32 ms cycle doesn't fit in a 21 ms hop and its energy per hop swings by up to 12 dB), and `dropDb` is how far the newest block is below the loudest. It measures both energy and peak and takes the larger drop: energy sees a 30 ms ping fade 12 dB within three hops; the peak sees a 2 ms tick louder than the string by its full height, where in energy it is only ≈ 6 dB. Every block holds a whole cycle, so a steady note reads ≈ 0 dB (up to 2.5 dB of energy on bass notes). The idea is energy-envelope onset detection (Bello et al. 2005) turned around: a pitch is only trusted while the envelope says the sound is still there.
A frame is **not sustained**, so it isn't analysed (`PitchReading.sustained` = false), when its sound had died away by its end:
- **more than 12 dB down** (`SustainCheck.DEFAULT_MAX_DROP_DB`): a loud tick has passed. A note that starts in the newest block is the loudest block, so new notes pass at once; on the phone a played E4 never dropped more than 1.8 dB across a window, a synthetic 24th-fret E fading fast about 7.
- **more than 4 dB down and the newest block within 6 dB of the room's background** (`ROOM_DROP_DB`, `ROOM_MARGIN_DB`, the floor from `NoiseFloorTracker`): a quiet tick has passed. A tick just above the room can't drop further than the room's level (6.6–8 dB on the phone, as little as that fast high note), but a note is still well above the room. Room noise alone varies by 1–2 dB, so it is sustained and ends a reading as usual.

While a note shows, a frame that isn't sustained doesn't count as a dropout (`PitchSmoother.noReading(sustained = false)`): a tick louder than the string hides it until the tick leaves the window. With the smoother's onset (*Smoothing*) a tick can't start a reading: it gives at most 2 analysed frames, and a new reading also needs frames loud enough to open the gate by themselves. On the phone the laptop speaker rang on at 1400 Hz for ≈ 70 ms after a tick, −48 to −55 dBFS: the gate stayed open on its hysteresis after the tick and three sustained frames read 1400 Hz, "E4 +25 semitones".

Measured (`EdgeCaseTest`, `SustainCheckTest`, 48 kHz): ticks (2 ms rings at 330 Hz–5 kHz, a knock, a clock tick, a digital click, a noise burst) and pings (the same rings lasting 8 or 30 ms), at peak −1 dBFS over a −70 or −55 dBFS room: **nothing shown**. Without the check, most rang at a pitch and showed for 5–36 frames per three of them. **On the Xiaomi 12X** (2026-10-15), 45 ticks of seven kinds from the laptop speaker: none shown; a played E4 shows on its third frame, on pitch. Ticks every 0.4 s over a ringing string, 12 dB quieter or 13 dB louder (peak): the reading is never lost and stays **within 0.26 cents**; ticks as loud as the string move it up to 3.5 cents for a frame or two. *Known limits:* a ping ringing 8–30 ms over a string is a pitched sound of its own (like a glass clink) and can pull the reading off, even onto its own note, for a moment; and in the low range a ping ringing ≈ 30 ms fades too little within a 36 ms block, so on its own it may show briefly. Cost: under 0.01 ms per frame.

## Noise floor, SNR and clarity
How well the tuner hears the guitar. Every `PitchReading` carries these besides the level; *Reception* turns them into what the display shows.
- **Noise floor** (`level/NoiseFloorTracker`): the room's background in dBFS. Only frames without a pitch count, gate open or not. Their levels pass a running median of 5 (ignores a glitch), and the floor is the lowest of those over the last **2 s of note-free frames** (scaled with the hop by `AnalysisSettings.newNoiseFloorTracker()`). This is Martin's (2001) minimum statistics on one band, with a median in place of his recursive smoothing (which took ~0.6 s to follow a 20 dB drop). The floor drops within a few frames, rises only once a louder room lasts 2 s, and notes never move it. NaN until the first note-free frame; reads a little low on fluctuating noise.
- **SNR** while a note sounds: `10 · log10(10^((level − floor) / 10) − 1)`, the frame's power minus the background's (as in Boll's spectral subtraction), at least −20 dB; NaN without a pitch or floor. Measured within 1 dB from 0 to 40 dB SNR.
- **Clarity** (`pitch/Clarity`): 0..1 from YIN's confidence `c = 1 − d′(τ)`. `d′(τ)` is the aperiodic share of the power, so `10 · log10(c / (1 − c))` is a periodic-to-aperiodic ratio in dB, like Boersma's (1993) harmonics-to-noise ratio. It depends only on the noise, not pitch or timbre, and sits ≈ 7.8 dB above broadband SNR (YIN's low-pass removes most hiss). Mapped linearly from 7.5 dB (`d′` = 0.15: 0) to 20 dB (`d′` = 0.01: 1): 20 dB SNR or cleaner gives 1, 10 dB ≈ 0.8, 6 dB ≈ 0.5, 0 dB ≈ 0.

All allocation-free (byte-counting tests). **No meters** for these (owner's decision, 2026-10-12): they become one *reception* figure the display shows, tips only when the player can fix something, and optional signal bars behind a setting.

## Reception
`level/ReceptionTracker` (pure, counted in frames) turns each pitched frame's level, SNR and clarity into one steady 0..1 figure: 1 for a strong, clean note, towards 0 when it is quiet, sinks into the room or loses its period.
- **Scores:** level 0 at −50 dBFS to 1 from −35 dBFS; SNR 0 at 0 dB to 1 from 20 dB (unknown room counts as 1); clarity as is. Linear in dB.
- **Smoothing:** a one-pole filter per factor, 0.15 s towards better and 0.5 s towards worse, so a rough frame or two barely dims it. In a note's first 0.1 s a factor rises at once (the window still holds the silence before it). `reset()` (note gone) returns to 1.
- **The figure** is the lowest of the three; `limit` (`ReceptionLimit`: `NONE` from 0.95, else `LEVEL`, `NOISE`, `CLARITY`) names the one holding it down. The smoothed scores and a smoothed `levelDbfs` are public for the signal bars.

`TunerInterpreter` feeds one tracker every pitched frame and resets it when the reading is lost; `Detected.reception` carries it. On screen (`components/ReceptionLook.kt`) it changes **only opacity and sharpness**, never colour, shape or motion, which stay the tuning cue: at worst reception the needle layer and wave fade to 0.62 of their opacity with up to 2 dp blur (`BlurEffect`, 8 steps made once; Android 12+, older phones only fade). The effect scales with how far the picture is from its resting look, so resting looks unchanged, and `HeldReception` keeps the last value while a note fades out. Text never fades.

## Tips
`TipCue` (`ReceptionTip.kt`, pure, counted in frames) picks the `ReceptionTip` the sound asks for, with separate on and off limits so a level hovering at a limit can't flicker:
- **Too loud** ("Too loud, move the phone back"): peak reaches −0.5 dBFS (clipping), until it stays below −3 dBFS. A pluck that clips only in its attack is shorter than the tip's settle time. Comes first, because clipping distorts everything else.
- **Too soft** ("Play a bit louder"): the note's *loudest* level so far is below −44 dBFS, so a well-played note's fading tail never asks.
- **Too noisy** ("Too noisy here"): room background ≥ −38 dBFS, until below −42 dBFS (also between notes); or the note's best SNR stays below 8 dB. Too soft comes first, since playing louder also beats noise.
- A note's verdict holds 2.5 s after it ends, so a tip stays through the gap to the next pluck.

The wanted tip passes through its own `SteadyHint<ReceptionTip>` (not restarted per note): settles in 0.5 s (loud), 0.6 s (soft) or 1 s (noisy), stays at least **3 s**, then the line stays tip-free for 1 s. `Detected.tip` and `Listening.tip` carry it.

On screen (`NoteDisplay`) a tip **replaces something less needed, so nothing moves**: "Play a string" without a note, the direction in the wave's pill, or the frequency under the needle (so "Tune up/down" stays). One line, set smaller (down to 11 sp) when it doesn't fit; its line keeps the height of a full-size line (measured with a `TextMeasurer`), the shrunk text centred in it. Without that it came out a pixel taller at 1.25× text and pushed the note up: under Android 14's non-linear font scaling Compose keeps an sp line height in proportion to the font size, and a smaller font is enlarged by a larger factor. TalkBack: "E 2, 12 cents flat, Play a bit louder".

## Signal bars (optional)
Behind "Show signal bars" (off by default). Four bars at the start of the mode switch's row (`ModeSwitchRow` in `TunerScreen.kt`), across from focus mode's button: the top start corner upright, the top of the controls pane on its side. From 2026-10-20 (owner's pick, from `docs/design/signal-bars-place-preview.html`) to 2026-10-25 they sat at the row's end, where a phone shows its own; focus mode's button took that place (Phase 43), and beside it the bars pushed the switch off centre, or squeezed it, so they went to the other end, where the switch stays centred whether they show or not. They never move with what the tuner hears. Focus mode, with its controls hidden, leaves them out.
- **Count** (`SignalBars`, pure): reception lights bars 1–4 from 0.05, 0.35, 0.65 and 0.9, each going out 0.08 below its threshold. Without a note the last count stays, dimmed to 45 %.
- **Drawing** (`components/SignalBarsButton.kt`): a `Canvas` in `onSurface`, unlit bars at 20 %; the count is a `derivedStateOf`, so only a changed count recomposes. Hosted in `movableContentOf`, so the count and an open panel survive layout changes.
- **Place and touch area:** the bars are drawn 24 dp, centred in a 48 dp slot at the row's end (`SIGNAL_BARS_SLOT`); the button around them is 48 × 48 dp (`touchArea`). The row takes the whole width and keeps the switch centred, unless a long label at a large font would bring it into the slot: then the switch moves towards the start, so it never runs under the bars. `SIGNAL_BARS` tags the button, `SIGNAL_BARS_ICON` the drawn bars (the layout tests' bounds).
- **Figures:** a tap (TalkBack "Signal bars: 3 of 4", action "show the figures") opens a small panel "How well Twangle hears you": volume, background and clarity. `TunerViewModel.signalFigures` updates them 4×/s, only while the setting is on.

## Pitch detection (YIN)
`YinPitchDetector` follows de Cheveigné & Kawahara (2002), steps 2–5, with `d(τ)` computed by FFT (`FftDifference`, [Threading and performance](performance.md)) and two additions that synthetic measurements showed were needed for ±1 cent:
1. **Low-pass pre-filter:** 4th-order Butterworth at 4 kHz (two RBJ biquads), skipping its ~92-sample start-up transient. Without it very bright tones near 1.4 kHz give a V-shaped dip that parabolic interpolation misjudges by ~2.5 cents. Also removes hiss.
2. **Adaptive least-squares refinement:** a cubic fitted to raw `d` over the lags `τ ± k` within 5 % of the way from the dip to the mean (`k` ≤ τ/16). Sharp dips use k = 1 (the classic parabola); broad dips (low pure notes) average dozens of lags. At 20 dB SNR this cut the worst low-E error from ~23 cents to under 1; the cubic term removes a ~0.3-cent bias.

**Range edges and whole-period checks.** The first dip is followed to its minimum; if it is still falling at τ_max, the pitch is below 62 Hz and the frame has none (this keeps 60 Hz hum out). Two checks then catch a period off by a whole factor, on raw `d`, and a third drops tones above the range:
- *Too long (noise):* heavy noise inflates `d′` at short lags, so the first accepted dip can be 2–3 periods. If `d` at a local minimum near τ/k is at most 15 % above `d(τ)` (and `d′` < 0.5), τ/k is taken.
- *Too short (harmonics):* a note with a strong 2nd and weak odd harmonics nearly repeats every half period. If the dip floor at 2τ or 3τ is below a quarter of the floor at τ, that lag is the period. *Dip floors* are the minimum of `d` between whole lags, from the quartic through `d` at τ ± 2 (a parabola misjudged a sharp dip by up to 0.3 % of the mean of `d` at a low analysis rate; the quartic is within 0.02 %). A floor under 0.2 % of the mean of `d` is a tone that repeats at τ and is left alone. Comparing raw `d` and skipping every tone with `d′(τ)` < 0.05 is not enough: a G string heard through the phone, its fundamental and odd harmonics faint (0.5–2 % of the mean left at its half period), reads G4 in every frame; a true G4 leaves under 0.2 % even at 20 dB SNR, and its floor at 2τ is as high (in noise) or barely lower.
- *Above the range:* a tone above 1500 Hz has no dip in range at its own period, but one at 2, 3 or 4 periods, so a 2 kHz whistle or a tick ringing at 3 or 5 kHz read as 1000, 1500 or 1250 Hz. After the two checks above, the frame has no pitch if the dip at τ_min is still falling towards shorter lags, or a dip of `d` below τ_min (from lag 4) divides τ a whole number of times and is about as deep. That is the *too long* test, on parabola-interpolated dip floors (a period of 10–30 lags falls well between whole lags, which alone makes `d` there several times `d(τ)` on a clean tone) plus 1 % of the mean of `d`. A note with a strong 2nd harmonic keeps its odd harmonics at the half period, ≥ 4 % of the mean of `d` in every test tone, so it still reads. Measured: sines and bright tones 1.6–5 kHz give nothing, also at 20 dB SNR; 1318–1500 Hz still within 1 cent.

Search range τ = 32…775 (1500…62 Hz) plus 50 lags for the fit; integration window `W = 4096 − 92 − 825 = 3179`. In the low-note range τ = 32…1715 (1500…28 Hz) plus 109 lags, `W = 8192 − 92 − 1824 = 6276`, FFT size 8192. Confidence = 1 − d′(τ). `PitchResult` is a value class packing two floats into a `Long`, with `PitchResult.NONE` instead of null, so it never boxes. Measured: ≈ 0.1 cent for sines, ≈ 0.5 for saw/square/pluck/weak fundamental, < 1 at 20 dB SNR, < 5 at 10 dB; no octave errors. `detect()` is allocation-free (byte-counting test). *Known limit (modelled 2026-10-24, Phase 46 in ROADMAP.md):* a real string is stiff, so its partials sit sharp of exact multiples (partial k at `k·f₀·√(1 + B·k²)`, Fletcher 1964), and the dip of `d` is pulled sharp by them. With `StrumChords.stiffStrum` at the strum tests' stiffness (B = 1.5·10⁻⁴, the low E) a pick attack (partials at 1/k) reads +2.9 cents 0.3 s after the pluck (+3.5 with every partial to 6 kHz, +6 for a very bright 1/√k pluck), under 1 cent from 1 s on and 0.3 at 1.5 s, as the stiff upper partials die away first; a finger pluck (1/k²) stays within 0.5 throughout. The bias grows with the number of partials in the dip: limiting E4 to its lowest three took it from +2.5 to +0.5 cents, so the planned fix refines the period on the lowest few partials only.

## Smoothing
`PitchSmoother` works in fractional MIDI, so cents are ×100 and A4 is already applied:
0. **Onset:** without a reading, a new one starts only once **3 readings in a row** agree within 50 cents, each from a frame loud enough to open the gate by itself (≥ −50 dBFS; the gate's hysteresis keeps a fading note, it doesn't start one); a frame without a pitch, one too quiet, or one far from the last starts the count over. A tick gives at most 2 such frames (see *Sustain check*). The reading starts at the median of the three, so a new note shows ≈ 43 ms later than without the onset (76 ms after the pluck instead of 33; 119 instead of 76 for the low strings, whose first frames have no pitch yet) and its first value is closer (worst 0.59 cents instead of 1.28 on a C6 pluck).
1. **Octave-jump rejection:** a reading a whole number of octaves (±30 cents) from the running median is dropped; if it holds 3 frames (~64 ms), the filters restart there.
2. **Median of 5** (~107 ms) removes other outliers.
3. **EMA, α = 0.3,** snapping when the median is > 50 cents away (a new string). Steps are weighted by confidence: 1 from 0.97 up, falling linearly to 0.2 at 0.88 and below. This roughly halves needle wobble in a noisy room and at the end of a decay without slowing clean notes.
4. **Note hysteresis:** the shown note changes only past 65 cents (50 + 15), so displayed cents can reach ±65.

`noReading()` keeps the last reading for up to 4 missed frames (~85 ms), then clears it; frames that aren't sustained (*Sustain check*) don't count. Allocation-free.

## Edge cases (measured, `EdgeCaseTest`)
What the user sees after the whole chain on synthetic signals:
- **White noise:** always the right note; worst error 0.8 cents at 10 dB SNR, 1.7 at 6, 2.5 at 3, ≈ 10 at 0 dB (low notes; high notes stay under 2).
- **Mains hum 10 dB below the string:** within 3 cents; hum alone shows nothing. *Known limit:* hum within ~6 dB of the string can lock YIN onto a shared period or hide low strings. A notch filter would fix it if real rooms need it.
- **Ticks, knocks and clicks:** alone, never shown; over a ringing string, within 0.26 cents (see *Sustain check*).
- **High strings with strong 2nd/3rd harmonics** (B3–C6): within 1 cent, no octave flips while decaying.
- **Sustain decay:** stays within 1 cent until the gate closes (once); over −60 dBFS room noise the last frames wobble up to 6 cents, never another note.
- **Bluetooth-like audio** (300–3400 Hz or 100–7000 Hz band, native 8 or 16 kHz): within 1 cent.
