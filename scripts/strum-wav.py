"""Writes a synthetic strum as a WAV file, for checking strum check on the phone (scripts/strum-check.sh).

    python scripts/strum-wav.py out.wav "E2 A2 D3 G3 B3 E4" "0 -20 0 15 0 0" [peak dBFS]

The second argument lists the open strings (A4 = 440 Hz), the third how far each one is off, in
cents; "x" leaves that string out. The optional fourth sets the peak level (default -1 dBFS). The strings start 15 ms apart from 0.5 s in, lowest first, and
ring for 4 s in all.

Each string is the same ideal plucked string as PluckSynth and the JVM tests' SyntheticStrum
(Fletcher & Rossing 1998, ch. 2): harmonic k starts at 1/k and decays with time constant
1.5 s / (1 + (k - 1) / 2), harmonics below 6 kHz, at most 32. Every string is equally loud. Each
harmonic is a damped resonator, x[n] = 2r·cos(w)·x[n-1] - r²·x[n-2] (a decaying sine), so the pitch is
exact. Standard library only; one strum takes a few seconds.
"""
import math, struct, sys, wave

RATE = 48000
LENGTH_S, LEAD_S, SPREAD_S = 4.0, 0.5, 0.015
DECAY_S, MAX_PARTIAL_HZ, MAX_HARMONICS = 1.5, 6000.0, 32
STEPS = {'C': -9, 'D': -7, 'E': -5, 'F': -4, 'G': -2, 'A': 0, 'B': 2}


def note_hz(name):
    """Equal temperament from A4 = 440 Hz: "E2", "F#3", "Bb1"."""
    letter, rest = name[0].upper(), name[1:]
    shift = 0
    while rest and rest[0] in '#b':
        shift += 1 if rest[0] == '#' else -1
        rest = rest[1:]
    semitones = STEPS[letter] + shift + 12 * (int(rest) - 4)
    return 440.0 * 2 ** (semitones / 12)


def main():
    out, notes, cents = sys.argv[1], sys.argv[2].split(), sys.argv[3].split()
    if len(notes) != len(cents):
        sys.exit("give one cents value (or x) per string")
    total, lead = int(RATE * LENGTH_S), int(RATE * LEAD_S)
    buf = [0.0] * total
    for i, (note, off) in enumerate(zip(notes, cents)):
        if off.lower() == 'x':
            continue
        f = note_hz(note) * 2 ** (float(off) / 1200)
        start = lead + int(i * SPREAD_S * RATE)
        for k in range(1, MAX_HARMONICS + 1):
            if k * f > MAX_PARTIAL_HZ:
                break
            w = 2 * math.pi * k * f / RATE
            r = math.exp(-1 / (DECAY_S / (1 + (k - 1) / 2) * RATE))
            c1, c2 = 2 * r * math.cos(w), -r * r
            # The two samples before the start, so that y[start + m] = r^m·sin(m·w) / k.
            y1, y2 = -math.sin(w) / (k * r), -math.sin(2 * w) / (k * r * r)
            for n in range(start, total):
                y = c1 * y1 + c2 * y2
                buf[n] += y
                y2, y1 = y1, y
    peak = max(abs(x) for x in buf) or 1.0
    peak_dbfs = float(sys.argv[4]) if len(sys.argv) > 4 else -1.0
    scale = 10 ** (peak_dbfs / 20) * 32767 / peak
    with wave.open(out, 'wb') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(struct.pack('<%dh' % total, *(int(x * scale) for x in buf)))


if __name__ == '__main__':
    main()
