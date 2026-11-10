#!/usr/bin/env bash
# Publishes PRIVACY.md and PRIVACY.nl.md to the public policy page
# (https://klaboukes.github.io/twangle-privacy/, GitHub Pages of the public
# repo Klaboukes/twangle-privacy). The app's own repo stays private until after
# release, and Play needs the policy at a public URL. Run it after every policy
# change has landed on main. It copies the policies and the page frame as
# origin/main has them (fetched first; the folder it runs in, its branch and its
# edits play no part, so a worktree can't publish an unmerged policy). Pushes
# only when the text or the frame changed.
#   --dry-run   compare with the public repo and show what would change; push nothing.
set -euo pipefail

DRY_RUN=0
case "${1:-}" in
    "") ;;
    --dry-run) DRY_RUN=1 ;;
    *) echo "Usage: $0 [--dry-run]" >&2; exit 2 ;;
esac

REPO="https://github.com/Klaboukes/twangle-privacy.git"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
source "$ROOT/scripts/main-export.sh"

MAIN="$WORK/main"
PUB="$WORK/repo"
export_main "$ROOT" "$MAIN" PRIVACY.md PRIVACY.nl.md website/privacy-layout.html
git clone --quiet --depth 1 "$REPO" "$PUB"
# English is the start page, Dutch is nl; the links between them follow.
sed -e 's/(PRIVACY\.nl\.md)/(nl.md)/' "$MAIN/PRIVACY.md" > "$PUB/index.md"
sed -e 's/(PRIVACY\.md)/(index.md)/' "$MAIN/PRIVACY.nl.md" > "$PUB/nl.md"
# Our own page frame in place of the theme's, which loads a script from another site.
mkdir -p "$PUB/_layouts"
cp "$MAIN/website/privacy-layout.html" "$PUB/_layouts/default.html"

cd "$PUB"
# The repo is public: commit with GitHub's no-reply address, never a personal one.
git config user.name "Klaboukes"
git config user.email "816876+Klaboukes@users.noreply.github.com"
git add index.md nl.md _layouts/default.html
if git diff --cached --quiet; then
    echo "The public policy is already up to date."
    exit 0
fi
if [ "$DRY_RUN" = 1 ]; then
    echo "Dry run, nothing pushed. These files would change on the public policy page:"
    git diff --cached --stat=200 | cat
    exit 0
fi
git commit --quiet -m "Update the privacy policy"
git push --quiet
echo "Published. GitHub Pages shows it within a few minutes:"
echo "  https://klaboukes.github.io/twangle-privacy/"
