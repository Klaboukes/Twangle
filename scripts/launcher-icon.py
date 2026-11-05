"""Draw Twangle's launcher icon (2.0, Phase 79) as Android vector drawables.

The mark is one moment of a plucked string's first three harmonics (the owner's pick from
docs/design/logo-v2-preview/, round 8's "A" in round 10's colours, "Richer overlaps"): mode n of a string fixed at both ends swings as sin(n*pi*u),
u along the string (Fletcher & Rossing, The Physics of Musical Instruments, ch. 2). The whole string is amber,
the halves blue, the thirds green, each half and a third as tall as the whole, so no harmonic ever rises above
the whole's arch (|sin(n*x)| <= n*sin(x) on 0..pi).

In the mock-up the three colours are see-through and add up like light ("screen" blending), each overlap then
made more colourful and a little deeper (round 10: added up alone, the overlaps above the string all came out
nearly white, the halves and the thirds hard to tell apart in a small icon). A vector drawable can't blend, so
this script works out each overlap's final colour on the icon's background and paints flat shapes. Each shape runs from the string out to its own edge and the shapes overlapping more are painted later,
so every edge inside the mark is a later shape's edge over an earlier one: no seams where two colours meet.

Writes, for the phone app and the watch app:
  res/drawable/ic_launcher_foreground.xml   the icon (on @color/ic_launcher_background)
  res/drawable/ic_launcher_monochrome.xml   the themed icon: the three harmonics in one colour, three strengths
wear/src/main/res/drawable/ic_twangle_mark.xml, the watch tile's and watch face shortcut's white mark,
wear/src/main/res/drawable/ic_twangle_mark_colour.xml, the mark in its colours in the middle of the watch tile, and
res/drawable/ic_opening_animated.xml in both, the opening screen's icon (Android 12 and up play it): the
string is plucked, each harmonic swinging at its own speed (the thirds three times as fast as the whole) and
settling into the icon's pose, every overlap in the icon's own colour all the while. Android 8 to 11 show it
still: its resting state is the icon.

    python -I scripts/launcher-icon.py [--preview out.svg]
"""

import colorsys
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
# Round 10's "Richer overlaps": an overlap's saturation raised by this much (up to the cap) and its lightness
# lowered, two waves' overlap more than all three's, which stays the lightest (HSL, as the mock-up's toHsl).
OVERLAP_SATURATION, OVERLAP_SATURATION_CAP = 0.25, 0.85
OVERLAP_DARKER = {2: 0.10, 3: 0.03}
SAMPLES = 1500
TOLERANCE = 0.01                             # how far a simplified edge may stray, in canvas units
# The launcher icon (and the themed icon) draw the mark at this size about the canvas's centre, so it sits inside
# the 66-unit safe zone with room around it like other apps' symbols (owner, 2026-11-05: at full width it ran to
# the edge of the watch's round icon, and round launchers cut the string's ends off). The opening screen and the
# tile's cropped marks keep the full size (owner, 2026-11-05).
MARK_SCALE = 0.75


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
    background in turn at its strength (CSS mix-blend-mode: screen with opacity, in sRGB), an overlap then
    made more colourful and deeper (OVERLAP_SATURATION, OVERLAP_DARKER)."""
    dst = rgb(BACKGROUND)
    for k in sorted(members):
        blended = screen(dst, rgb(HARMONICS[k][3]))
        a = HARMONICS[k][4]
        dst = tuple(d + (b - d) * a for d, b in zip(dst, blended))
    if len(members) > 1:
        h, l, sat = colorsys.rgb_to_hls(*dst)
        sat = min(OVERLAP_SATURATION_CAP, sat + OVERLAP_SATURATION)
        dst = colorsys.hls_to_rgb(h, l - OVERLAP_DARKER[len(members)], sat)
    return hex_of(tuple(min(1.0, max(0.0, v)) for v in dst))


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


def vector(size_dp, viewport, body, what, translate=None, scale=None):
    group_open, group_close = "", ""
    if translate or scale:
        attrs = (f'android:translateX="{translate[0]}"\n        android:translateY="{translate[1]}"' if translate else
                 f'android:scaleX="{scale}"\n        android:scaleY="{scale}"\n'
                 f'        android:pivotX="{viewport / 2:g}"\n        android:pivotY="{viewport / 2:g}"')
        group_open = f'\n    <group\n        {attrs}>\n'
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


OPENING_MS = 1000                            # the opening animation, all of it: the string swings throughout
SWINGS = 0.5                                 # how often the whole string swings in it (the halves twice that, the thirds three times)
SWING_FRAMES = 40
# The watch's opening screen slides in from the side (about half a second on the Galaxy Watch 5): the watch
# holds the string at rest that long first, so the whole swing is seen (owner, 2026-11-05: "much faster").
WATCH_HOLD_MS = 500


def swing(n, q):
    """How far harmonic n stands from the string, as a share of its resting height, q (0..1) into the swing:
    plucked from rest, swinging n times as fast as the whole string (sin; SWINGS times in all for the whole),
    easing into the resting pose (smoothstep, so it arrives with no jolt) and never past it."""
    s = q * q * (3 - 2 * q)
    return s + (1 - s) * math.sin(2 * math.pi * SWINGS * n * q)


def relative_swing(k, outer, q):
    """Harmonic k's swing inside a group already scaled by harmonic `outer`'s, so the two multiply to k's own.
    At the start both are 0; their ratio then is the limit, n_k / n_outer (sin x ~ x). The outer ones (the
    whole, the halves) stay above 0 after the start with SWINGS at 0.5, so the ratio stays finite."""
    a = swing(HARMONICS[outer][0], q)
    if a < 1e-6:
        return HARMONICS[k][0] / HARMONICS[outer][0]
    return swing(HARMONICS[k][0], q) / a


def keyframes(prop, values, duration):
    frames = "".join(f'\n                        <keyframe android:fraction="{f:.4f}" android:value="{v:.4f}" />'
                     for f, v in values)
    return (f'\n            <objectAnimator\n                android:duration="{duration}"\n'
            f'                android:interpolator="@android:anim/linear_interpolator">\n'
            f'                <propertyValuesHolder\n                    android:propertyName="{prop}"\n'
            f'                    android:valueType="floatType">{frames}\n                </propertyValuesHolder>\n'
            f'            </objectAnimator>\n')


def target(name, animator):
    return (f'\n    <target android:name="{name}">\n        <aapt:attr name="android:animation">'
            + animator + '        </aapt:attr>\n    </target>\n')


def opening(hold_ms=0):
    """The opening screen's animated icon, every overlap in the icon's own colour while the harmonics swing (owner,
    2026-11-05). Each set of overlapping harmonics is painted as nested groups, each hinged on the string, the
    outer ones clipped to their harmonic's shape and the innermost holding the last one's shape in the set's
    colour (colour_of): its region is where they all cover. Each group's height is animated so the nested
    scales multiply to each harmonic's own swing (relative_swing). Painted singles, then pairs, then all three,
    as the icon's regions are, so each point ends in the colour of everything that covers it. With hold_ms the
    string first rests that long (the harmonics flat on it), then swings for OPENING_MS."""
    sets = [[0], [1], [2], [0, 1], [0, 2], [1, 2], [0, 1, 2]]
    body, animated = "", []
    for i, members in enumerate(sets):
        indent, close = "    ", ""
        for depth, k in enumerate(members):
            name = f"s{i}g{depth}"
            animated.append((name, k, members[depth - 1] if depth else None))
            body += f'\n{indent}<group\n{indent}    android:name="{name}"\n{indent}    android:pivotY="{STRING_Y}">\n'
            if depth < len(members) - 1:
                body += f'{indent}    <clip-path android:pathData="{harmonic_outline(k)}" />\n'
            else:
                body += (f'{indent}    <path\n{indent}        android:fillColor="{colour_of(members)}"\n'
                         f'{indent}        android:pathData="{harmonic_outline(k)}" />\n')
            close = f"{indent}</group>\n" + close
            indent += "    "
        body += close
    drawable = vector(108, 108, body, "").split("-->\n", 1)[1]           # the <vector> without the file header
    drawable = "".join("        " + line + "\n" if line else "\n" for line in drawable.rstrip("\n").split("\n"))
    targets = ""
    for name, k, outer in animated:
        total = hold_ms + OPENING_MS
        frames = [i / SWING_FRAMES for i in range(SWING_FRAMES + 1)]
        values = [((hold_ms + q * OPENING_MS) / total,
                   swing(HARMONICS[k][0], q) if outer is None else relative_swing(k, outer, q)) for q in frames]
        if hold_ms:
            values.insert(0, (0.0, values[0][1]))
        targets += target(name, keyframes("scaleY", values, total))
    return (HEADER.format(what="The opening screen's icon: the string plucked, each harmonic swinging at its own speed into\n"
                               "     the icon's pose, its overlaps in the icon's colours throughout (ic_launcher_foreground.xml).\n"
                               "     Still, it is that icon.")
            + '<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            + '    xmlns:aapt="http://schemas.android.com/aapt">\n\n'
            + '    <aapt:attr name="android:drawable">\n' + drawable + '    </aapt:attr>\n'
            + targets + '</animated-vector>\n')


def main():
    painted = regions()
    foreground = vector(108, 108, "".join(path_xml(c, d) for c, d in painted),
                        "Twangle's mark: one moment of a plucked string's first three harmonics, the whole in amber,\n"
                        "     the halves in blue, the thirds in green, where they overlap the colours they add up to on\n"
                        "     @color/ic_launcher_background, made richer. 108-unit adaptive icon canvas, the mark at MARK_SCALE\n"
                        "     about its centre.",
                        scale=MARK_SCALE)
    mono_body = "".join(path_xml("#FFFFFFFF", harmonic_outline(k), MONO_ALPHA[k]) for k in range(3))
    monochrome = vector(108, 108, mono_body,
                        "The themed icon: the same three harmonics in one colour at three strengths.", scale=MARK_SCALE)
    # The watch's mark: the harmonics cropped to themselves (x 17..91, y 29..71), white so the tile can tint it.
    mark = vector(24, 78, mono_body,
                  "Twangle's mark on its own for the watch tile and the watch face shortcut: the launcher icon's\n"
                  "     harmonics (ic_launcher_monochrome.xml) cropped to themselves, white so they can be tinted.",
                  translate=(-15, -11))
    # The tile's mark: the launcher icon's harmonics in their own colours, cropped the same way (owner, 2026-11-05:
    # tinted in the watch's accent, the mark showed all blue).
    coloured_mark = vector(24, 78, "".join(path_xml(c, d) for c, d in painted),
                           "Twangle's mark on its own for the watch tile: the launcher icon's harmonics in their own colours\n"
                           "     (ic_launcher_foreground.xml) cropped to themselves, for the tile's black.",
                           translate=(-15, -11))
    (ROOT / "wear/src/main/res/drawable/ic_twangle_mark_colour.xml").write_text(coloured_mark, encoding="utf-8", newline="\n")
    for module in ("app", "wear"):
        res = ROOT / module / "src/main/res/drawable"
        (res / "ic_launcher_foreground.xml").write_text(foreground, encoding="utf-8", newline="\n")
        (res / "ic_launcher_monochrome.xml").write_text(monochrome, encoding="utf-8", newline="\n")
    (ROOT / "wear/src/main/res/drawable/ic_twangle_mark.xml").write_text(mark, encoding="utf-8", newline="\n")
    for module, theme, hold in (("app", "Theme.Twangle.Starting", 0), ("wear", "Theme.Twangle.Watch.Starting", WATCH_HOLD_MS)):
        (ROOT / module / "src/main/res/drawable/ic_opening_animated.xml").write_text(opening(hold), encoding="utf-8", newline="\n")
        (ROOT / module / "src/main/res/values/opening_animation.xml").write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n<!-- Written by scripts/launcher-icon.py with ic_opening_animated.xml. -->\n'
            f'<resources>\n    <!-- How long the opening screen\'s icon animation lasts ({theme}). -->\n'
            f'    <integer name="opening_animation_ms">{hold + OPENING_MS}</integer>\n</resources>\n', encoding="utf-8", newline="\n")
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
