#!/usr/bin/env bash
# Takes pictures of the whole tuner screen in all six styles, light and dark, on the phone
# (StyleScreenshots: the A string in tune on the style's background), and builds a contact sheet
# for the owner:
#   build/owner-screens/styles/index.html   (git-ignored; the PNGs sit next to it)
# For looking at a changed background or in-tune colour in every style at once.
# Needs the phone connected, like scripts/ui-tests.sh. Takes about a minute.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PULLED="feature/tuner/build/outputs/connected_android_test_additional_output"
SHEET="build/owner-screens/styles"

rm -rf "$PULLED"
scripts/ui-tests.sh :feature:tuner -c StyleScreenshots -a styleScreenshots=true || exit $?

rm -rf "$SHEET"
mkdir -p "$SHEET"
find "$PULLED" -name 'style-*.png' -exec cp {} "$SHEET/" \;

python - "$SHEET" <<'EOF'
import html, os, sys
sheet = sys.argv[1]
styles = [("ancient", "Ancient"), ("classic", "Classic"), ("retro", "Retro"),
          ("contemporary", "Contemporary"), ("futuristic", "Futuristic"), ("space-age", "Space Age")]
found = set(os.listdir(sheet))
parts, count, missing = [], 0, []
parts.append("<div class='scroll'><table><tr><th></th><th>Light</th><th>Dark</th></tr>")
for style, style_name in styles:
    parts.append(f"<tr><th class='row'>{html.escape(style_name)}</th>")
    for theme in ("light", "dark"):
        file = f"style-{style}-{theme}.png"
        if file in found:
            count += 1
            parts.append(f"<td><a href='{file}'><img src='{file}' loading='lazy' alt='{html.escape(f'{style_name}, {theme}')}'></a></td>")
        else:
            missing.append(file)
            parts.append(f"<td><div class='missing'>{theme} missing</div></td>")
    parts.append("</tr>")
parts.append("</table></div>")
page = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Twangle styles</title>
<style>
:root {{ --bg: #f6f5f2; --ink: #1d1d1f; --muted: #6b6b70; --line: #ddd9d0; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg: #17171a; --ink: #ececef; --muted: #a0a0a8; --line: #33333a; }} }}
body {{ margin: 0; padding: 16px; background: var(--bg); color: var(--ink); font: 15px/1.4 system-ui, sans-serif; }}
h1 {{ font-size: 22px; margin: 0 0 4px; }} p {{ margin: 0 0 16px; color: var(--muted); }}
.scroll {{ overflow-x: auto; }}
table {{ border-collapse: collapse; }}
th {{ font-weight: 600; padding: 6px 8px; text-align: center; white-space: nowrap; }}
th.row {{ text-align: right; vertical-align: middle; }}
td {{ padding: 4px; border-top: 1px solid var(--line); vertical-align: top; }}
td img {{ display: block; width: 260px; height: auto; border-radius: 8px; }}
.missing {{ width: 260px; height: 60px; color: var(--muted); font-size: 12px; }}
</style></head><body>
<h1>Twangle styles</h1>
<p>The tuner with the A string in tune, in six styles, light and dark. Tap a picture to see it full size. {count} pictures.</p>
{''.join(parts)}
</body></html>
"""
open(os.path.join(sheet, "index.html"), "w", encoding="utf-8").write(page)
print(f"Contact sheet: {os.path.abspath(os.path.join(sheet, 'index.html'))} ({count} pictures)")
for file in missing:
    print(f"  missing {file}")
sys.exit(1 if missing else 0)
EOF
