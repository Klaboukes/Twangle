#!/usr/bin/env bash
# Publishes the Twangle website (website/ in this repo, docs/architecture/website.md) to the
# public repo Klaboukes/klaboukes.github.io, which GitHub Pages serves at
# https://klaboukes.github.io/. Run it after a change to website/ has landed on main.
# It builds the pages, and website/build.py refuses the lot if any page or stylesheet loads
# something from another site or a link inside the site leads nowhere. Pushes only when
# something changed. The privacy policy stays in its own repo (scripts/publish-privacy.sh),
# at klaboukes.github.io/twangle-privacy/, so this repo never holds a twangle-privacy folder.
set -euo pipefail

REPO="https://github.com/Klaboukes/klaboukes.github.io.git"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

python -I "$ROOT/website/build.py" "$WORK/site"
if [ -e "$WORK/site/twangle-privacy" ]; then
    echo "website/ must not have a twangle-privacy folder: it would hide the privacy policy." >&2
    exit 1
fi

git clone --quiet --depth 1 "$REPO" "$WORK/repo"
cd "$WORK/repo"
# GitHub Pages serves the main branch (also the very first time, when the repo is empty).
git symbolic-ref HEAD refs/heads/main
# Replace everything but the repo's own history with the new build, so removed pages go too.
find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
cp -R "$WORK/site/." .

# The repo is public: commit with GitHub's no-reply address, never a personal one.
git config user.name "Klaboukes"
git config user.email "816876+Klaboukes@users.noreply.github.com"
git add -A
if git diff --cached --quiet; then
    echo "The website is already up to date."
    exit 0
fi
git commit --quiet -m "Update the website"
git push --quiet origin main
echo "Published. GitHub Pages shows it within a few minutes:"
echo "  https://klaboukes.github.io/"
