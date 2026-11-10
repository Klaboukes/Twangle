#!/usr/bin/env bash
# Publishes the Twangle website (website/ in this repo, docs/architecture/website.md) to the
# public repo Klaboukes/klaboukes.github.io, which GitHub Pages serves at
# https://klaboukes.github.io/. Run it after a change to website/ has landed on main.
# It builds the pages as origin/main has them (fetched first; the folder it runs in, its branch
# and its edits play no part, so a worktree can't publish unmerged text), and website/build.py
# refuses the lot if any page or stylesheet loads something from another site or a link inside
# the site leads nowhere. Pushes only when something changed. The privacy policy stays in its
# own repo (scripts/publish-privacy.sh), at klaboukes.github.io/twangle-privacy/, so this repo
# never holds a twangle-privacy folder.
#   --dry-run   build, compare with the public repo and show what would change; push nothing.
set -euo pipefail

DRY_RUN=0
case "${1:-}" in
    "") ;;
    --dry-run) DRY_RUN=1 ;;
    *) echo "Usage: $0 [--dry-run]" >&2; exit 2 ;;
esac

REPO="https://github.com/Klaboukes/klaboukes.github.io.git"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
source "$ROOT/scripts/main-export.sh"

export_main "$ROOT" "$WORK/main" website
python -I "$WORK/main/website/build.py" "$WORK/site"
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
if [ "$DRY_RUN" = 1 ]; then
    echo "Dry run, nothing pushed. These files would change on https://klaboukes.github.io/:"
    git diff --cached --stat=200 | cat
    exit 0
fi
git commit --quiet -m "Update the website"
git push --quiet origin main
echo "Published. GitHub Pages shows it within a few minutes:"
echo "  https://klaboukes.github.io/"
