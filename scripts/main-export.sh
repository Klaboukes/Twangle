# Sourced by publish-website.sh and publish-privacy.sh, so what they publish is what is merged
# on main and nothing else: not the branch the folder is on, not its uncommitted edits, not a
# worktree's unmerged work (Phase 90).

# export_main <repo root> <dest> <path>...
# Fetches origin, then unpacks the given paths as origin/main has them into <dest> (git archive:
# no checkout, so the folder this runs in stays as it is). Says which commit it took, and when this
# folder's own copy of those paths differs from it, so nobody thinks their edit went out.
export_main() {
    local root="$1" dest="$2"
    shift 2
    git -C "$root" fetch -q origin
    local commit
    commit="$(git -C "$root" rev-parse --verify -q 'origin/main^{commit}')" || {
        echo "No origin/main to publish from." >&2
        return 1
    }
    mkdir -p "$dest"
    git -C "$root" archive --format=tar "$commit" -- "$@" | tar -x -C "$dest"
    echo "Publishing from main: $(git -C "$root" log -1 --format='%h %s' "$commit")"
    if ! git -C "$root" diff --quiet "$commit" -- "$@" \
        || [ -n "$(git -C "$root" ls-files --others --exclude-standard -- "$@")" ]; then
        echo "Note: this folder's copy of $* differs from main; main's is published, not this one."
    fi
}
