#!/usr/bin/env bash
# Publishes PRIVACY.md and PRIVACY.nl.md to the public policy page
# (https://klaboukes.github.io/twangle-privacy/, GitHub Pages of the public
# repo Klaboukes/twangle-privacy). The app's own repo stays private until after
# release, and Play needs the policy at a public URL. Run it after every policy
# change has landed on main. Pushes only when the text or the frame changed.
set -euo pipefail

REPO="https://github.com/Klaboukes/twangle-privacy.git"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

git clone --quiet --depth 1 "$REPO" "$WORK"
# English is the start page, Dutch is nl; the links between them follow.
sed -e 's/(PRIVACY\.nl\.md)/(nl.md)/' "$ROOT/PRIVACY.md" > "$WORK/index.md"
sed -e 's/(PRIVACY\.md)/(index.md)/' "$ROOT/PRIVACY.nl.md" > "$WORK/nl.md"
# Our own page frame in place of the theme's, which loads a script from another site.
mkdir -p "$WORK/_layouts"
cp "$ROOT/website/privacy-layout.html" "$WORK/_layouts/default.html"

cd "$WORK"
# The repo is public: commit with GitHub's no-reply address, never a personal one.
git config user.name "Klaboukes"
git config user.email "816876+Klaboukes@users.noreply.github.com"
git add index.md nl.md _layouts/default.html
if git diff --cached --quiet; then
    echo "The public policy is already up to date."
    exit 0
fi
git commit --quiet -m "Update the privacy policy"
git push --quiet
echo "Published. GitHub Pages shows it within a few minutes:"
echo "  https://klaboukes.github.io/twangle-privacy/"
