"""Draw Twangle's launcher icon (2.0, Phase 79) as Android vector drawables.

The mark is one moment of a plucked string's first three harmonics (the owner's pick from
docs/design/logo-v2-preview/, round 8, "A"): mode n of a string fixed at both ends swings as sin(n*pi*u),
u along the string (Fletcher & Rossing, The Physics of Musical Instruments, ch. 2). The whole string is amber,
the halves blue, the thirds green, each half and a third as tall as the whole, so no harmonic ever rises above
the whole's arch (|sin(n*x)| <= n*sin(x) on 0..pi).

In the mock-up the three colours are see-through and add up like light ("screen" blending). A vector drawable
can't blend, so this script works out each overlap's final colour on the icon's background and paints flat
shapes. Each shape runs from the string out to its own edge and the shapes overlapping more are painted later,
so every edge inside the mark is a later shape's edge over an earlier one: no seams where two colours meet.

Writes, for the phone app and the watch app:
  res/drawable/ic_launcher_foreground.xml   the icon (on @color/ic_launcher_background) and the opening screen's
  res/drawable/ic_launcher_monochrome.xml   the themed icon: the three harmonics in one colour, three strengths
and wear/src/main/res/drawable/ic_twangle_mark.xml, the watch tile's and watch face shortcut's white mark.

    python -I scripts/launcher-icon.py [--preview out.svg]
"""

import math
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

BACKGROUND = "#0E1116"                       # Ink dark (StylePalettes.kt, InkDark.background)
STRING_Y = 57.0                              # the (unseen) string, a little under the canvas's centre
X0, X1 = 17.0, 91.0                          # the string's two ends on the 108-unit canvas
# (harmonic, height above the string at its peak, which way it swings first, colour, strength)
HARMONICS = [
    (1, 28.0, 1, "#FFB648", 0.85),           # the whole: Ink's amber (far off)
    (2, 14.0, -1, "#5FC4FF", 0.78),          # the halves: Ink's blue (the accent)
    (3, 28.0 / 3, 1, "#3DDC84", 0.95),       # the thirds: Ink's green (in tune)
]
MONO_ALPHA = [0.5, 0.75, 1.0]                # the themed icon's strengths, whole to thirds
SAMPLES = 1500
TOLERANCE = 0.01                             # how far a simplified edge may stray, in canvas units


def height(k, u):
    """Harmonic k's height above the string at u (0..1), negative below it."""
    n, amp, sign, _, _ = HARMONICS[k]
    return sign * amp * math.sin(n * math.pi * u)


def x_at(u):
    return X0 + (X1 - X0) * u


def crossings():
    """Every u where two harmonics cross or one crosses the string, so the edges' corners fall on samples."""
    curves = [lambda u, k=k: height(k, u) for k in range(3)] + [lambda u: 0.0]
    found = set()
    for i in range(len(curves)):
        for j in range(i + 1, len(curves)):
            f = lambda u: curves[i](u) - curves[j](u)
            for s in range(SAMPLES):
                a, b = s / SAMPLES, (s + 1) / SAMPLES
                fa, fb = f(a), f(b)
                if fa == 0:
                    found.add(a)
                elif fa * fb < 0:
                    for _ in range(60):
                        m = (a + b) / 2
                        if f(a) * f(m) <= 0:
                            b = m
                        else:
                            a = m
                    found.add((a + b) / 2)
    return found


GRID = sorted({s / SAMPLES for s in range(SAMPLES + 1)} | crossings())


def screen(dst, src):
    return tuple(1 - (1 - d) * (1 - s) for d, s in zip(dst, src))


def rgb(hex_colour):
    return tuple(int(hex_colour[i:i + 2], 16) / 255 for i in (1, 3, 5))


def hex_of(c):
    return "#" + "".join(f"{round(v * 255):02X}" for v in c)


def colour_of(members):
    """The colour the mock-up shows where the harmonics in `members` overlap: each one screened over the
    background in turn at its strength (CSS mix-blend-mode: screen with opacity, in sRGB)."""
    dst = rgb(BACKGROUND)
    for k in sorted(members):
        blended = screen(dst, rgb(HARMONICS[k][3]))
        a = HARMONICS[k][4]
        dst = tuple(d + (b - d) * a for d, b in zip(dst, blended))
    return hex_of(dst)


def simplify(points, tolerance):
    """Ramer-Douglas-Peucker: drop the points a straight line between their neighbours passes within tolerance of."""
    if len(points) < 3:
        return points
    (ax, ay), (bx, by) = points[0], points[-1]
    dx, dy = bx - ax, by - ay
    length = math.hypot(dx, dy) or 1e-12
    worst, index = 0.0, 0
    for i in range(1, len(points) - 1):
        px, py = points[i]
        d = abs(dy * (px - ax) - dx * (py - ay)) / length
        if d > worst:
            worst, index = d, i
    if worst <= tolerance:
        return [points[0], points[-1]]
    return simplify(points[:index + 1], tolerance)[:-1] + simplify(points[index:], tolerance)


def path_data(points):
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in points) + " Z"


def regions():
    """(colour, pathData) to paint in order. For each side of the string and each set of overlapping harmonics,
    the part of that set's band reaching from the string out to the band's outer edge, smaller sets first."""
    out = []
    for side in (1, -1):                                   # above the string, then below
        for size in (1, 2, 3):
            for members in ([0], [1], [2]) if size == 1 else ([0, 1], [0, 2], [1, 2]) if size == 2 else ([0, 1, 2],):
                def band(u):
                    hs = [side * height(k, u) for k in range(3)]
                    if any(hs[k] <= 0 for k in members):
                        return None
                    upper = min(hs[k] for k in members)
                    lower = max([0.0] + [hs[k] for k in range(3) if k not in members and hs[k] > 0])
                    return upper if upper > lower else None
                run = []
                for u in GRID + [None]:
                    top = band(u) if u is not None else None
                    if top is not None:
                        run.append((x_at(u), STRING_Y - side * top))
                        continue
                    if len(run) >= 2:
                        edge = simplify(run, TOLERANCE)
                        outline = edge + [(edge[-1][0], STRING_Y), (edge[0][0], STRING_Y)]
                        out.append((colour_of(members), path_data(outline)))
                    run = []
    return out


def harmonic_outline(k):
    """Harmonic k's whole shape, between the string and its curve (its lobes above and below)."""
    edge = simplify([(x_at(u), STRING_Y - height(k, u)) for u in GRID], TOLERANCE)
    return path_data(edge + [(X1, STRING_Y), (X0, STRING_Y)])


HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!-- Drawn by scripts/launcher-icon.py: change that script and run it again rather than editing this file.
     {what} -->
"""


def vector(size_dp, viewport, body, what, translate=None):
    group_open, group_close = "", ""
    if translate:
        group_open = f'\n    <group\n        android:translateX="{translate[0]}"\n        android:translateY="{translate[1]}">\n'
        group_close = "    </group>\n"
        body = "".join("    " + line + "\n" if line else "\n" for line in body.splitlines())
    return (HEADER.format(what=what)
            + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            + f'    android:width="{size_dp}dp"\n    android:height="{size_dp}dp"\n'
            + f'    android:viewportWidth="{viewport}"\n    android:viewportHeight="{viewport}">\n'
            + group_open + body + group_close + "</vector>\n")


def path_xml(colour, data, alpha=None):
    a = f'\n        android:fillAlpha="{alpha}"' if alpha is not None else ""
    return f'\n    <path\n        android:fillColor="{colour}"{a}\n        android:pathData="{data}" />\n'


def main():
    painted = regions()
    foreground = vector(108, 108, "".join(path_xml(c, d) for c, d in painted),
                        "Twangle's mark: one moment of a plucked string's first three harmonics, the whole in amber,\n"
                        "     the halves in blue, the thirds in green, where they overlap the colours they add up to on\n"
                        "     @color/ic_launcher_background. 108-unit adaptive icon canvas.")
    mono_body = "".join(path_xml("#FFFFFFFF", harmonic_outline(k), MONO_ALPHA[k]) for k in range(3))
    monochrome = vector(108, 108, mono_body,
                        "The themed icon: the same three harmonics in one colour at three strengths.")
    # The watch's mark: the harmonics cropped to themselves (x 17..91, y 29..71), white so the tile can tint it.
    mark = vector(24, 78, mono_body,
                  "Twangle's mark on its own for the watch tile and the watch face shortcut: the launcher icon's\n"
                  "     harmonics (ic_launcher_monochrome.xml) cropped to themselves, white so they can be tinted.",
                  translate=(-15, -11))
    for module in ("app", "wear"):
        res = ROOT / module / "src/main/res/drawable"
        (res / "ic_launcher_foreground.xml").write_text(foreground, encoding="utf-8", newline="\n")
        (res / "ic_launcher_monochrome.xml").write_text(monochrome, encoding="utf-8", newline="\n")
    (ROOT / "wear/src/main/res/drawable/ic_twangle_mark.xml").write_text(mark, encoding="utf-8", newline="\n")
    print(f"{len(painted)} shapes, {sum(d.count('L') + 1 for _, d in painted)} points")

    if "--preview" in sys.argv:
        out = pathlib.Path(sys.argv[sys.argv.index("--preview") + 1])
        to_svg = lambda body: body.replace("android:fillColor", "fill").replace("android:fillAlpha", "fill-opacity") \
            .replace("android:pathData", "d").replace("<path", "<path")
        clip = '<clipPath id="m"><circle cx="54" cy="54" r="36"/></clipPath>'
        out.write_text(
            '<svg xmlns="http://www.w3.org/2000/svg" width="1040" height="360" viewBox="0 0 312 108">'
            f'<defs>{clip}</defs>'
            f'<g clip-path="url(#m)"><rect width="108" height="108" fill="{BACKGROUND}"/>'
            + to_svg("".join(path_xml(c, d) for c, d in painted)) + '</g>'
            f'<g transform="translate(104 0)" clip-path="url(#m)"><rect width="108" height="108" fill="#2A3A55"/>'
            + to_svg(mono_body).replace("#FFFFFFFF", "#C9DAFF") + '</g>'
            f'<g transform="translate(208 0)"><rect x="18" y="18" width="72" height="72" fill="#000"/>'
            + to_svg(mono_body).replace("#FFFFFFFF", "#FFFFFF") + '</g></svg>', encoding="utf-8")


if __name__ == "__main__":
    main()
