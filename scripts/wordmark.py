"""Draw Twangle's wordmark (2.0, Phase 79) as an Android vector drawable.

The name "Twangle" in Comfortaa Bold, its letters spaced 0.06 em apart (the owner's pick, round 2's 4,
"Bold, spaced", in docs/design/logo-v2-preview/wordmark.html), traced from the font into flat shapes so the
app carries no font file. It is laid out in the ringing name's own frame (RingingName.kt): a 340 x 110 box whose
string runs at y 56, the word 42 units tall (the font size), centred, its baseline 16 units under the string.

Needs fontTools (python -I -m pip install fonttools) and the font, kept beside this script with its licence
(scripts/fonts/comfortaa/, SIL Open Font License 1.1, from github.com/google/fonts ofl/comfortaa). The OFL
covers the font; a drawing made with it is the app's own and needs no credit.

    python -I scripts/wordmark.py [--preview out.svg]
"""

import pathlib
import sys

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
FONT = ROOT / "scripts/fonts/comfortaa/Comfortaa[wght].ttf"
OUT = ROOT / "core/designsystem/src/main/res/drawable/twangle_wordmark.xml"

NAME = "Twangle"
WEIGHT = 700
SPACING = 0.06                               # em between letters, as CSS letter-spacing
FRAME_W, FRAME_H = 340, 110                  # the ringing name's box (RingingName.kt)
SIZE = 42.0                                  # the font size in frame units
BASELINE = 56 + 16                           # the string at 56, the baseline 16 under it


def kerning(font, left, right):
    """The font's own kerning between two glyphs (GPOS pair positioning, the "kern" feature), in font units."""
    gpos = font["GPOS"].table
    lookups = {i for fr in gpos.FeatureList.FeatureRecord if fr.FeatureTag == "kern" for i in fr.Feature.LookupListIndex}
    total = 0
    for i in sorted(lookups):
        lookup = gpos.LookupList.Lookup[i]
        for sub in lookup.SubTable:
            if lookup.LookupType == 9:                     # extension: the real subtable inside
                sub = sub.ExtSubTable
            if getattr(sub, "LookupType", 2) != 2 or left not in sub.Coverage.glyphs:
                continue
            value = None
            if sub.Format == 1:
                pairs = sub.PairSet[sub.Coverage.glyphs.index(left)].PairValueRecord
                value = next((p.Value1 for p in pairs if p.SecondGlyph == right), None)
            else:
                c1 = sub.ClassDef1.classDefs.get(left, 0)
                c2 = sub.ClassDef2.classDefs.get(right, 0)
                value = sub.Class1Record[c1].Class2Record[c2].Value1
            if value is not None and getattr(value, "XAdvance", 0):
                total += value.XAdvance
                break                                      # the first subtable that has the pair decides
    return total


def main():
    font = instantiateVariableFont(TTFont(FONT), {"wght": WEIGHT})
    upm = font["head"].unitsPerEm
    cmap = font.getBestCmap()
    glyphs = [cmap[ord(c)] for c in NAME]
    glyph_set = font.getGlyphSet()
    advances = [font["hmtx"][g][0] for g in glyphs]
    # Pen positions in font units: each advance, the pair's kerning and the spacing between letters.
    xs, x = [], 0.0
    for i, g in enumerate(glyphs):
        xs.append(x)
        if i + 1 < len(glyphs):
            x += advances[i] + kerning(font, g, glyphs[i + 1]) + SPACING * upm
    width = (x + advances[-1]) * SIZE / upm
    left = (FRAME_W - width) / 2
    paths = []
    for g, gx in zip(glyphs, xs):
        pen = SVGPathPen(glyph_set, ntos=lambda v: f"{v:.2f}".rstrip("0").rstrip("."))
        scale = SIZE / upm
        # Font units (y up, from the pen position) to the frame (y down, from its corner).
        glyph_set[g].draw(TransformPen(pen, (scale, 0, 0, -scale, left + gx * scale, BASELINE)))
        paths.append(pen.getCommands())
    data = " ".join(paths)
    OUT.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Drawn by scripts/wordmark.py: change that script and run it again rather than editing this file.\n"
        f"     The name in Comfortaa Bold, letters {SPACING} em apart, in the ringing name's {FRAME_W} x {FRAME_H} frame\n"
        "     (RingingName.kt); white, so it can be tinted. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{FRAME_W}dp"\n    android:height="{FRAME_H}dp"\n'
        f'    android:viewportWidth="{FRAME_W}"\n    android:viewportHeight="{FRAME_H}">\n'
        f'    <path\n        android:fillColor="#FFFFFFFF"\n        android:pathData="{data}" />\n'
        "</vector>\n", encoding="utf-8", newline="\n")
    print(f"{NAME}: {width:.1f} wide from {left:.1f}, {len(data)} characters of path")
    if "--preview" in sys.argv:
        out = pathlib.Path(sys.argv[sys.argv.index("--preview") + 1])
        out.write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="{FRAME_W * 3}" height="{FRAME_H * 3}" '
                       f'viewBox="0 0 {FRAME_W} {FRAME_H}"><rect width="{FRAME_W}" height="{FRAME_H}" fill="#0E1116"/>'
                       f'<path d="{data}" fill="#EEF1F5"/></svg>', encoding="utf-8")


if __name__ == "__main__":
    main()
