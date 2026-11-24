"""Synthesizes the sound guide's candidate sounds (Phase 94, round 2) into website/static/sounds/guide/*.wav.

    python -I scripts/guide-sounds.py            make the files, then check them
    python -I scripts/guide-sounds.py --check    only check the files already there

The sound guide plays a short sound between the tuner's readings while the string is sharp or flat, at the
vibration guide's pace (VibrationGuide.kt: one every 700 ms at 30 cents off or more, easing in a straight line to
one every 300 ms at the in-tune window's 5-cent edge), and an in-tune sound once the string holds within 5 cents.
Round 1 (docs/design/tuning-guide-sounds.html) glided at 2,000-3,600 Hz, which the owner found sharp; round 2 is
softer. The files are for listening: the owner's page (that design page, *Round 2*) and the public, unlisted
website/pages/<language>/sound-guide.html, which a blind reviewer is sent.

Every file: mono, 22,050 Hz, 16 bit, its peak at about -12 dBFS (E's glide -14), starting and ending on a
sample of exactly 0, with smooth fades, so nothing clicks. No steady note sits on an open string's pitch
(E2 A2 D3 G3 B3 E4 and their octaves); a glide passes through such pitches only on its way. Python standard
library only; the output is the same on every PC (the woodblock's noise has a fixed seed).
"""

import math
import random
import struct
import sys
import wave
from pathlib import Path

RATE = 22050
OUT = Path(__file__).resolve().parent.parent / "website" / "static" / "sounds" / "guide"
PEAK_DB = -12.0

# Notes used below, equal temperament from A4 = 440 Hz. None is an open string's pitch class (E A D G B).
C5, F5, FS5, GS5, AS5, C6 = 523.25, 698.46, 739.99, 830.61, 932.33, 1046.50

# The open strings of a standard guitar and their octaves up to 4 kHz, for the check below.
STRINGS = [f * 2 ** k for f in (82.41, 110.0, 146.83, 196.0, 246.94, 329.63) for k in range(6) if f * 2 ** k < 4000]


def silence(seconds):
    return [0.0] * int(round(seconds * RATE))


def cos_in(i, n):
    """Raised-cosine rise over n samples: 0 at i = 0, 1 at i = n."""
    return 0.5 - 0.5 * math.cos(math.pi * min(i, n) / n) if n > 0 else 1.0


def fade_ends(x, fade_in, fade_out):
    """Raised-cosine fades at both ends; the first and last samples become exactly 0."""
    n = len(x)
    a, b = int(fade_in * RATE), int(fade_out * RATE)
    for i in range(n):
        g = 1.0
        if i < a:
            g *= cos_in(i, a)
        if n - 1 - i < b:
            g *= cos_in(n - 1 - i, b)
        x[i] *= g
    x[0] = 0.0
    x[-1] = 0.0
    return x


def sweep(f_of_u, seconds, harmonics=((1, 1.0),)):
    """A tone whose frequency follows f_of_u(u), u from 0 to 1 over the sound; the phase is integrated, so the
    sweep has no jumps. harmonics: (multiple, amplitude)."""
    n = int(round(seconds * RATE))
    out, phase = [], 0.0
    for i in range(n):
        f = f_of_u(i / (n - 1))
        out.append(sum(a * math.sin(h * phase) for h, a in harmonics))
        phase += 2 * math.pi * f / RATE
    return out


def exp_glide(f0, f1):
    """Equal steps in pitch (a straight line in cents), as Web Audio's exponentialRampToValueAtTime."""
    return lambda u: f0 * (f1 / f0) ** u


def normalize(x, db=PEAK_DB):
    peak = max(abs(v) for v in x) or 1.0
    k = 10 ** (db / 20) / peak
    return [v * k for v in x]


def mix(parts, seconds):
    """parts: (start in seconds, samples); the sum, seconds long."""
    out = silence(seconds)
    for at, x in parts:
        s = int(round(at * RATE))
        for i, v in enumerate(x):
            if s + i < len(out):
                out[s + i] += v
    return out


def remove_dc(x):
    """Subtracts the mean under a Hann window, so the sound keeps starting and ending at 0."""
    n = len(x)
    w = [0.5 - 0.5 * math.cos(2 * math.pi * i / (n - 1)) for i in range(n)]
    k = sum(x) / sum(w)
    return [v - k * wi for v, wi in zip(x, w)]


# ---- Sharp and flat pairs (up = sharp, the string too high; down = flat, too low) ----------------------------------

def glide_a(up):
    """A, round 1's glide as it was: 0.1 s, 2,000 <-> 2,500 Hz (a major third) on a sine, 8 ms in, 25 ms out."""
    lo, hi, length = 2000.0, 2500.0, 0.10
    x = sweep(exp_glide(lo, hi) if up else exp_glide(hi, lo), length)
    n, a, r = len(x), int(0.008 * RATE), int(0.025 * RATE)
    for i in range(n):  # round 1's straight-line envelope
        x[i] *= min(1.0, i / a, (n - 1 - i) / r)
    return normalize(remove_dc(x))


def bloop_b(up):
    """B, a water drop: a 100 ms sine bending quickly between 720 and 1,240 Hz (most of the bend in the first third),
    a soft 8 ms rise and a fast decay (time constant 32 ms)."""
    lo, hi, length = 720.0, 1240.0, 0.10
    f0, f1 = (lo, hi) if up else (hi, lo)
    x = sweep(lambda u: f0 * (f1 / f0) ** (1 - (1 - u) ** 3), length)
    x = [v * math.exp(-i / RATE / 0.032) for i, v in enumerate(x)]
    return normalize(remove_dc(fade_ends(x, 0.008, 0.015)))


def blip(f, seconds=0.06):
    """A soft note: a sine with a little octave, 6 ms in, easing down (40 ms), 15 ms out."""
    x = sweep(lambda u: f, seconds, ((1, 1.0), (2, 0.1)))
    x = [v * math.exp(-i / RATE / 0.040) for i, v in enumerate(x)]
    return fade_ends(x, 0.006, 0.015)


def steps_c(up):
    """C, two stepping blips a major third apart: F#5 (740 Hz) and A#5 (932 Hz), 60 ms each, 25 ms between
    (145 ms in all); up for sharp, down for flat."""
    notes = (FS5, AS5) if up else (AS5, FS5)
    return normalize(remove_dc(mix([(0.0, blip(notes[0])), (0.085, blip(notes[1]))], 0.145)))


def knock(f, rng, seconds=0.07):
    """A soft woodblock knock: three inharmonic partials that die away fast (the higher, the faster) and a touch of
    filtered noise for the strike; a 3 ms rise, a 10 ms fade at the end."""
    n = int(round(seconds * RATE))
    partials = ((1.0, 1.0, 0.022), (2.32, 0.30, 0.010), (4.25, 0.10, 0.005))  # (ratio, amplitude, time constant)
    x, lp, prev = [], 0.0, 0.0
    for i in range(n):
        t = i / RATE
        v = sum(a * math.exp(-t / tau) * math.sin(2 * math.pi * f * r * t) for r, a, tau in partials)
        noise = rng.uniform(-1.0, 1.0)
        lp += 0.25 * (noise - lp)          # one-pole low-pass: a dull strike, not a hiss
        hp, prev = lp - prev, lp           # differencing: no DC from the noise
        v += 0.15 * hp * math.exp(-t / 0.005)
        x.append(v)
    return fade_ends(x, 0.003, 0.010)


def knocks_d(up):
    """D, two wooden knocks a major third apart, rising for sharp, falling for flat: G#5 (831 Hz) and C6 (1,047 Hz),
    70 ms each, 110 ms start to start (180 ms in all)."""
    rng = random.Random(94)
    notes = (GS5, C6) if up else (C6, GS5)
    return normalize(remove_dc(mix([(0.0, knock(notes[0], rng)), (0.11, knock(notes[1], rng))], 0.18)))


def glide_e(up):
    """E, the glide made rounder and quieter: 160 ms, 850 <-> 1,275 Hz (a fifth) on a pure sine, a 35 ms raised-cosine
    rise and a 60 ms fall, peak -14 dBFS."""
    lo, hi, length = 850.0, 1275.0, 0.16
    x = sweep(exp_glide(lo, hi) if up else exp_glide(hi, lo), length)
    return normalize(remove_dc(fade_ends(x, 0.035, 0.060)), -14.0)


PAIRS = {"a": glide_a, "b": bloop_b, "c": steps_c, "d": knocks_d, "e": glide_e}


# ---- In-tune sounds ------------------------------------------------------------------------------------------------

def bell(length, f=C5):
    """A soft bell on C5 (523 Hz): the fundamental and three inharmonic partials (2, 2.76 and 4.07 times, as a small
    bell's), each dying away exponentially, the higher ones faster; the time constant is a 2.5th of the length, so a
    longer setting rings longer, then a gentle fade to 0 at its end."""
    n = int(round(length * RATE))
    tau = length / 2.5
    partials = ((1.0, 1.0), (2.0, 0.30), (2.76, 0.20), (4.07, 0.07))
    x = []
    for i in range(n):
        t = i / RATE
        x.append(sum(a * math.exp(-t / (tau / r ** 0.8)) * math.sin(2 * math.pi * f * r * t) for r, a in partials))
    return normalize(remove_dc(fade_ends(x, 0.004, min(0.04, 0.15 * length))))


def mallet(f, seconds, tau):
    x = sweep(lambda u: f, seconds, ((1, 1.0), (2, 0.15), (3, 0.04)))
    x = [v * math.exp(-i / RATE / tau) for i, v in enumerate(x)]
    return fade_ends(x, 0.004, min(0.04, 0.15 * seconds))


def chime(length):
    """A two-note chime rising a fifth to the C: F5 (698 Hz), then C6 (1,047 Hz) 0.12 s later at the short length and
    0.3 s later at 1 and 2 s; mallet-like notes (a sine with a little octave and twelfth), the second ringing to the
    end."""
    gap = min(0.3, 0.4 * length)
    first = mallet(F5, gap + 0.08, 0.10)
    second = mallet(C6, length - gap, (length - gap) / 2.5)
    return normalize(remove_dc(mix([(0.0, [0.8 * v for v in first]), (gap, second)], length)))


def high_c(length=0.3, f=2093.0):
    """Round 1's high C (C7, 2,093 Hz) for comparison: almost a pure sine, 15 ms in, easing to 0.7, 0.12 s out."""
    n = int(round(length * RATE))
    rel = min(0.15, length * 0.4)
    x = []
    for i in range(n):
        t = i / RATE
        if t < 0.015:
            g = t / 0.015
        elif t < length - rel:
            g = 1 - 0.3 * (t - 0.015) / (length - rel - 0.015)
        else:
            g = 0.7 * (length - t) / rel
        x.append(g * (math.sin(2 * math.pi * f * t) + 0.06 * math.sin(4 * math.pi * f * t)))
    x[0] = x[-1] = 0.0
    return normalize(remove_dc(x))


LENGTHS = {"short": 0.3, "1s": 1.0, "2s": 2.0}


# ---- A string coming up to pitch ------------------------------------------------------------------------------------

# What the tuner reads while a string is tightened from 40 cents flat to in tune (straight lines between the points).
RISING = ((0.0, -40), (1.5, -38), (3.2, -24), (4.6, -11), (5.8, -4), (6.4, -1), (8.0, 0))
IN_TUNE, FAR, SLOW, QUICK, HOLD = 5.0, 30.0, 0.70, 0.30, 0.15   # VibrationGuide's window, pace and hold


def cents_at(t):
    for (t0, c0), (t1, c1) in zip(RISING, RISING[1:]):
        if t <= t1:
            return c0 + (c1 - c0) * (t - t0) / (t1 - t0)
    return RISING[-1][1]


def interval(c):
    """VibrationGuide's pace: 700 ms at 30 cents off or more, a straight line to 300 ms at 5 cents, start to start."""
    a = min(FAR, max(IN_TUNE, abs(c)))
    return QUICK + (SLOW - QUICK) * (a - IN_TUNE) / (FAR - IN_TUNE)


def plan():
    """The times of the flat sounds and of the in-tune sound, walked in 10 ms steps as round 1's page does."""
    flats, nxt, since, t = [], 0.0, None, 0.0
    while t < 8.0:
        c = cents_at(t)
        if abs(c) <= IN_TUNE:
            since = t if since is None else since
            if t - since >= HOLD:
                return flats, t
        else:
            since = None
            if t >= nxt:
                flats.append(t)
                nxt = t + interval(c)
        t = round(t + 0.01, 2)
    raise SystemExit("the demo's string never came into tune")


def demo(pair):
    flats, tune = plan()
    flat = PAIRS[pair](False)
    ding = bell(0.3)
    return mix([(t, flat) for t in flats] + [(tune, ding)], tune + 0.3 + 0.5)


# ---- Files ---------------------------------------------------------------------------------------------------------

def sounds():
    out = {}
    for name, make in PAIRS.items():
        out[f"{name}-sharp"] = make(True)
        out[f"{name}-flat"] = make(False)
    for label, length in LENGTHS.items():
        out[f"bell-{label}"] = bell(length)
        out[f"chime-{label}"] = chime(length)
    out["high-c-short"] = high_c()
    for name in "bcde":
        out[f"demo-{name}"] = demo(name)
    return out


def write(path, x):
    frames = b"".join(struct.pack("<h", max(-32767, min(32767, int(round(v * 32767))))) for v in x)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(frames)


def read(path):
    with wave.open(str(path), "rb") as w:
        if (w.getnchannels(), w.getsampwidth(), w.getframerate()) != (1, 2, RATE):
            raise SystemExit(f"{path.name}: not mono 16-bit {RATE} Hz")
        raw = w.readframes(w.getnframes())
    return [v / 32767 for (v,) in struct.iter_unpack("<h", raw)]


def zc_hz(x):
    """The pitch of a stretch by its rising zero crossings (good enough for a sound whose fundamental leads)."""
    ups = [i for i in range(1, len(x)) if x[i - 1] < 0 <= x[i]]
    return (len(ups) - 1) * RATE / (ups[-1] - ups[0]) if len(ups) > 2 else 0.0


def check():
    """For each file: length, peak, DC, the first and last samples, the step from the first sample to the second and
    from the last but one to the last (a sound that starts or stops with a jump clicks; a faded one moves off 0 by a
    hair), and, for the pairs, the pitch near the start and near the end, so up and down go the right way."""
    problems, total = [], 0
    print(f"{'file':<16}{'s':>6}{'peak dBFS':>10}{'DC':>9}{'ends':>7}{'end step':>10}  pitch start -> end")
    for path in sorted(OUT.glob("*.wav")):
        x = read(path)
        total += path.stat().st_size
        peak = max(abs(v) for v in x)
        dc = sum(x) / len(x)
        edge = max(abs(x[1] - x[0]), abs(x[-1] - x[-2]))
        line = (f"{path.stem:<16}{len(x) / RATE:>6.2f}{20 * math.log10(peak):>10.1f}{dc:>9.5f}"
                f"{'0 0' if x[0] == 0 and x[-1] == 0 else 'NO':>7}{edge:>10.5f}")
        stem = path.stem
        if stem[0] in PAIRS and stem.endswith(("sharp", "flat")):
            nz = [i for i, v in enumerate(x) if abs(v) > 1e-4]
            body = x[nz[0]:nz[-1]]
            k = max(len(body) // 4, int(0.012 * RATE))
            a, b = zc_hz(body[:k]), zc_hz(body[-k:])
            line += f"  {a:6.0f} -> {b:6.0f} Hz"
            if (b > a) != stem.endswith("sharp"):
                problems.append(f"{stem}: goes the wrong way")
        print(line)
        if not -13.5 <= 20 * math.log10(peak) <= -11.5 and not stem.startswith("e-"):
            problems.append(f"{stem}: peak {20 * math.log10(peak):.1f} dBFS")
        if abs(dc) > 0.001:
            problems.append(f"{stem}: DC {dc:.4f}")
        if x[0] != 0 or x[-1] != 0:
            problems.append(f"{stem}: doesn't start and end on 0")
        if edge > 0.002:
            problems.append(f"{stem}: a jump at an end ({edge:.3f})")
    for f in (C5, F5, FS5, GS5, AS5, C6):  # the steady notes
        near = min(abs(1200 * math.log2(f / s)) for s in STRINGS)
        if near < 50:
            problems.append(f"{f} Hz is {near:.0f} cents from an open string")
    print(f"{len(list(OUT.glob('*.wav')))} files, {total / 1024:.0f} KB in all")
    if problems:
        print("Problems:\n  " + "\n  ".join(problems))
        sys.exit(1)
    print("All start and end on 0, no clicks at the ends, no DC, the pairs go the right way.")


def main():
    if "--check" not in sys.argv[1:]:
        OUT.mkdir(parents=True, exist_ok=True)
        for old in OUT.glob("*.wav"):
            old.unlink()
        for name, x in sounds().items():
            write(OUT / f"{name}.wav", x)
        flats, tune = plan()
        print("Demo: flat sounds at " + ", ".join(f"{t:.2f}" for t in flats) + f" s; in tune at {tune:.2f} s")
    check()


if __name__ == "__main__":
    main()
