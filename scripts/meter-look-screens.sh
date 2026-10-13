#!/usr/bin/env bash
# Takes pictures of all six meter looks in all six styles, light and dark, on the phone
# (MeterLookScreenshots), and builds a contact sheet for the owner:
#   build/owner-screens/meter-looks/index.html   (git-ignored; the PNGs sit next to it)
# Set A: the marker 20 cents flat. Set B: in tune with the Bold glow fully lit.
# Needs the phone connected, like scripts/ui-tests.sh. Takes about a minute and a half.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PULLED="feature/tuner/build/outputs/connected_android_test_additional_output"
SHEET="build/owner-screens/meter-looks"

rm -rf "$PULLED"
scripts/ui-tests.sh :feature:tuner -c MeterLookScreenshots -a meterScreenshots=true || exit $?

rm -rf "$SHEET"
mkdir -p "$SHEET"
find "$PULLED" -name '*.png' -exec cp {} "$SHEET/" \;

python - "$SHEET" <<'EOF'
import html, os, sys
sheet = sys.argv[1]
styles = [("ancient", "Ancient"), ("classic", "Classic"), ("retro", "Retro"),
          ("contemporary", "Contemporary"), ("futuristic", "Futuristic"), ("space-age", "Space Age")]
looks = [("classic", "Classic"), ("light-arc", "Light arc"), ("spirit-level", "Spirit level"),
         ("rolling-scale", "Rolling scale"), ("comet", "Comet"), ("studio-meter", "Studio meter")]
sets = [("a", "A. 20 cents flat"), ("b", "B. In tune, Bold glow")]
found = set(os.listdir(sheet))
parts, count, missing = [], 0, []
for key, title in sets:
    parts.append(f"<h2>{html.escape(title)}</h2><div class='scroll'><table><tr><th></th>")
    parts += [f"<th>{html.escape(name)}</th>" for _, name in looks]
    parts.append("</tr>")
    for style, style_name in styles:
        parts.append(f"<tr><th class='row'>{html.escape(style_name)}</th>")
        for look, look_name in looks:
            cell = []
            for theme in ("light", "dark"):
                file = f"{key}-{style}-{look}-{theme}.png"
                if file in found:
                    count += 1
                    cell.append(f"<a href='{file}'><img src='{file}' loading='lazy' alt='{html.escape(f'{look_name}, {style_name}, {theme}')}'></a>")
                else:
                    missing.append(file)
                    cell.append(f"<div class='missing'>{theme} missing</div>")
            parts.append("<td>" + "".join(cell) + "</td>")
        parts.append("</tr>")
    parts.append("</table></div>")
page = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Twangle meter looks</title>
<style>
:root {{ --bg: #f6f5f2; --ink: #1d1d1f; --muted: #6b6b70; --line: #ddd9d0; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg: #17171a; --ink: #ececef; --muted: #a0a0a8; --line: #33333a; }} }}
body {{ margin: 0; padding: 16px; background: var(--bg); color: var(--ink); font: 15px/1.4 system-ui, sans-serif; }}
h1 {{ font-size: 22px; margin: 0 0 4px; }} p {{ margin: 0 0 16px; color: var(--muted); }}
h2 {{ font-size: 18px; margin: 28px 0 8px; }}
.scroll {{ overflow-x: auto; }}
table {{ border-collapse: collapse; }}
th {{ font-weight: 600; padding: 6px 8px; text-align: center; white-space: nowrap; }}
th.row {{ text-align: right; vertical-align: middle; }}
td {{ padding: 4px; border-top: 1px solid var(--line); vertical-align: top; }}
td img {{ display: block; width: 170px; height: auto; margin-bottom: 4px; border-radius: 6px; }}
.missing {{ width: 170px; height: 60px; color: var(--muted); font-size: 12px; }}
</style></head><body>
<h1>Twangle meter looks</h1>
<p>Six looks in six styles, light above dark. Tap a picture to see it full size. {count} pictures.</p>
{''.join(parts)}
</body></html>
"""
open(os.path.join(sheet, "index.html"), "w", encoding="utf-8").write(page)
print(f"Contact sheet: {os.path.abspath(os.path.join(sheet, 'index.html'))} ({count} pictures)")
for file in missing:
    print(f"  missing {file}")
sys.exit(1 if missing else 0)
EOF
