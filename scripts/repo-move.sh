#!/usr/bin/env bash
# Re-points a clone made before the repository moved (Phase 93, 2026-11-12): the history was rewritten
# (the owner's Gmail address replaced by GitHub's no-reply one, so every commit got a new SHA) into a new
# Klaboukes/Twangle, and the old one became the private Klaboukes/Twangle-archive. The URL stayed the same,
# so a clone made before the move fetches the new history, unrelated to its own. Run once in each PC's
# main folder (on main, clean, no run going), from Git Bash:
#
#   git fetch --no-tags origin main && bash <(git show FETCH_HEAD:scripts/repo-move.sh) [--dry-run]
#
# (that one line works before the clone has this script; not `git pull` first, which can't merge the new
# history and would move origin/main). It:
#   1. pairs every commit of the old main with its rewritten twin (the same tree and the same place in
#      the history, checked pair by pair; it stops on any difference);
#   2. rewrites the commits named in this PC's git-ignored records (build/full-check/result.txt and its
#      reached-<commit>.txt, build/ui-tests-passed/, build/*-screenshots.made-at), full or abbreviated,
#      so the full check's, the UI tests' and the store pictures' passes still count;
#   3. moves origin/main and local main to the new history and replaces the tags with the new ones;
#   4. lists the local branches and worktrees still on the old history: never push one (it would bring
#      the old commits, Gmail address and all, into the new repository); redo its work on a branch from
#      the new main, or delete it.
# --dry-run does 1, says what 2 to 4 would change, and changes nothing (only FETCH_HEAD).
# Exit codes: 0 done (or nothing to do), 1 a check failed (nothing changed), 2 unknown argument.
set -uo pipefail

dry=0
for arg in "$@"; do
    case "$arg" in
        --dry-run) dry=1 ;;
        *) echo "usage: bash <(git show FETCH_HEAD:scripts/repo-move.sh) [--dry-run]  (after: git fetch --no-tags origin main)" >&2; exit 2 ;;
    esac
done

fail() { echo "repo-move: $*" >&2; exit 1; }
say() { echo "repo-move: $*"; }

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || fail "not inside a git clone"
cd "$ROOT" || fail "can't enter $ROOT"
[[ "$(git rev-parse --path-format=absolute --git-dir)" == "$(git rev-parse --path-format=absolute --git-common-dir)" ]] ||
    fail "run it in the main folder, not in a worktree (the records live in the main folder)"
say "in $ROOT, origin $(git remote get-url origin)"

# --- Checks: on main, clean, no run going ----------------------------------------------------------
[[ "$(git symbolic-ref -q --short HEAD)" == main ]] || fail "this folder is not on main: a run or another session may have it; leave it"
[[ -z "$(git status --porcelain)" ]] || fail "this folder has changes (git status): leave it until they are gone"
[[ -e "$(git rev-parse --git-path index.lock)" ]] && fail "git is busy here (index.lock): try again when it is done"
# Any such run on this PC counts, whichever folder it is in (REPO_MOVE_IGNORE_RUNS=1: for a test of this
# script in a scratch clone while one runs elsewhere).
if [[ "${REPO_MOVE_IGNORE_RUNS:-0}" == 1 ]]; then
    :
elif command -v powershell.exe > /dev/null; then
    running="$(powershell.exe -NoProfile -Command '(Get-CimInstance Win32_Process).CommandLine' 2>/dev/null | tr -d '\r' |
        grep -oE 'scripts[/\\](full-check|ui-tests|release|store-screenshots|watch-screenshots|baseline-profile|record-plucks|release-smoke|share-store-screenshots)\.sh' | sort -u | paste -sd, -)"
    [[ -n "$running" ]] && fail "a run is going ($running): try again when it is done"
else
    say "can't see the running processes (no powershell.exe): make sure no full check, UI test run or release is going"
fi

# --- The new history (FETCH_HEAD), and the old main ----------------------------------------------
git fetch -q --no-tags origin main || fail "can't fetch origin's main"
NEW="$(git rev-parse FETCH_HEAD)"
related() { git merge-base "$1" "$2" > /dev/null 2>&1; }
OLD=""
for candidate in refs/remotes/origin/main 'refs/remotes/origin/main@{1}' refs/heads/main; do
    sha="$(git rev-parse -q --verify "$candidate^{commit}" 2>/dev/null)" || continue
    related "$sha" "$NEW" || { OLD="$sha"; break; }
done
[[ -n "$OLD" ]] || { say "nothing to move: main and origin's main share their history (already re-pointed, or origin not moved)"; exit 0; }
# Local main must be part of the old main (behind it or the same), so every commit it names is paired.
if related refs/heads/main "$NEW"; then
    :   # main already on the new history (an earlier run stopped after moving it); the records still get rewritten
else
    git merge-base --is-ancestor refs/heads/main "$OLD" || fail "local main has commits that origin's old main lacks: leave it as it is and ask Claude"
fi
say "old main $(git rev-parse --short "$OLD"), new main $(git rev-parse --short "$NEW")"

TMP="$(mktemp -d)" || fail "no temp folder"
trap 'rm -rf "$TMP"' EXIT

# --- 1. Pair the commits ---------------------------------------------------------------------------
# The rewrite changed only names and emails, so each new commit has its old twin's tree, its parents'
# twins as parents, and the same place on the first-parent line from the first commit.
depth="$(git rev-list --first-parent --count "$OLD")"
twin="$(git rev-list --first-parent --reverse "$NEW" | sed -n "${depth}p")"
[[ -n "$twin" ]] || fail "the new main is shorter than the old one: not its rewrite"
git log --format='%H %T %P' "$OLD" > "$TMP/old.txt" || fail "can't read the old history"
git log --format='%H %T %P' "$twin" > "$TMP/new.txt" || fail "can't read the new history"
awk -v o="$OLD" -v n="$twin" '
    FNR == 1 { f++ }
    f == 1 { ot[$1] = $2; on[$1] = NF - 2; for (i = 3; i <= NF; i++) op[$1, i - 2] = $i; next }
    f == 2 { nt[$1] = $2; nn[$1] = NF - 2; for (i = 3; i <= NF; i++) np[$1, i - 2] = $i; next }
    END {
        m[o] = n; q[1] = o; head = 1; tail = 1
        while (head <= tail) {
            a = q[head++]; b = m[a]
            if (!(a in ot) || !(b in nt)) { print "missing commit " a " / " b > "/dev/stderr"; exit 1 }
            if (ot[a] != nt[b] || on[a] != nn[b]) { print "differs: " a " / " b > "/dev/stderr"; exit 1 }
            for (i = 1; i <= on[a]; i++) {
                pa = op[a, i]; pb = np[b, i]
                if (pa in m) { if (m[pa] != pb) { print "paired twice: " pa > "/dev/stderr"; exit 1 } continue }
                if (pb in seen) { print "twin taken twice: " pb > "/dev/stderr"; exit 1 }
                m[pa] = pb; seen[pb] = 1; q[++tail] = pa
            }
        }
        for (k in m) print k, m[k]
    }' "$TMP/old.txt" "$TMP/new.txt" > "$TMP/map.txt" || fail "the new history is not the old one rewritten (nothing changed)"
count="$(wc -l < "$TMP/map.txt")"
[[ "$count" -eq "$(git rev-list --count "$OLD")" ]] || fail "paired $count commits of $(git rev-list --count "$OLD") (nothing changed)"
say "paired all $count commits of the old main with their rewritten twins (same trees)"

# --- 2. The records --------------------------------------------------------------------------------
# Every word of 7 to 40 hex digits that names a commit of the old main (a whole SHA, or the start of
# exactly one) becomes its twin's, as long; all digits counts only at 40 (a time is not a commit).
rewrite() {
    awk -v mapfile="$TMP/map.txt" -v file="$1" '
        BEGIN { while ((getline line < mapfile) > 0) { split(line, a, " "); twin[a[1]] = a[2]; keys[++n] = a[1]; twins[n] = a[2] } }
        function rewritten(tok,   i) { for (i = 1; i <= n; i++) if (substr(twins[i], 1, length(tok)) == tok) return 1; return 0 }
        {
            s = $0; out = ""
            while (match(s, /[0-9a-f]+/)) {
                tok = substr(s, RSTART, RLENGTH); pre = substr(s, 1, RSTART - 1); s = substr(s, RSTART + RLENGTH)
                before = (out pre) ~ /[0-9A-Za-z_]$/; after = s ~ /^[0-9A-Za-z_]/
                rep = tok; L = length(tok)
                if (!before && !after && L >= 7 && L <= 40 && (L == 40 || tok ~ /[a-f]/)) {
                    hits = 0
                    if (L == 40) { if (tok in twin) { hits = 1; hit = tok } }
                    else for (i = 1; i <= n; i++) if (substr(keys[i], 1, L) == tok) { hits++; hit = keys[i] }
                    if (hits == 1) { rep = substr(twin[hit], 1, L); changed++ }
                    else if (hits == 0 && !rewritten(tok)) print "  " file ": " tok " is no commit of the old main, left as it is" > "/dev/stderr"
                }
                out = out pre rep
            }
            print out s
        }
        END { print changed + 0 > (mapfile ".count") }' "$1"
}
records=()
for f in build/full-check/result.txt build/*-screenshots.made-at build/shared-store-screenshots/*.made-at; do
    [[ -f "$f" ]] && records+=("$f")
done
[[ -d build/ui-tests-passed ]] && while IFS= read -r f; do records+=("$f"); done < <(find build/ui-tests-passed -type f | sort)
changes=()
for f in "${records[@]}"; do
    rewrite "$f" > "$TMP/record" || fail "can't read $f"
    n="$(cat "$TMP/map.txt.count")"
    if [[ "$n" -gt 0 ]]; then
        changes+=("$f")
        mkdir -p "$TMP/records/$(dirname "$f")" && cp "$TMP/record" "$TMP/records/$f"
        say "record $f: $n commit(s) $([[ $dry == 1 ]] && echo "would be rewritten" || echo rewritten)"
    fi
done
renames=()
for f in build/full-check/reached-*.txt; do
    [[ -f "$f" ]] || continue
    old="${f#build/full-check/reached-}"; old="${old%.txt}"
    new="$(awk -v k="$old" '$1 == k { print $2 }' "$TMP/map.txt")"
    [[ -n "$new" ]] && renames+=("$f build/full-check/reached-$new.txt") && say "record $f $([[ $dry == 1 ]] && echo would be) renamed for $new"
done
[[ ${#changes[@]} -eq 0 && ${#renames[@]} -eq 0 ]] && say "no record names a commit of the old main"

# --- 3. Main and the tags --------------------------------------------------------------------------
if [[ $dry == 1 ]]; then
    say "would move origin/main and main to $(git rev-parse --short "$NEW") and take origin's tags"
else
    git fetch -q --prune --no-tags origin || fail "can't fetch origin (nothing changed yet)"
    [[ "$(git rev-parse refs/remotes/origin/main)" == "$NEW" ]] || fail "origin's main moved meanwhile: run again (nothing changed yet)"
    git fetch -q --force --no-tags origin '+refs/tags/*:refs/tags/*' || fail "can't fetch origin's tags (nothing else changed yet)"
    git reset -q --keep "$NEW" || fail "can't move main (records not yet rewritten; run again)"
    for f in "${changes[@]}"; do cp "$TMP/records/$f" "$f"; done
    for r in "${renames[@]}"; do mv $r; done
    gmail="$(git log --format='%ae%n%ce' main | grep -ci 'barthoukes@gmail' || true)"
    [[ "$gmail" == 0 ]] || fail "main still has $gmail commits under the Gmail address: ask Claude"
    say "main is now $(git log -1 --format='%h %s' main)"
fi

# --- 4. What is still on the old history ------------------------------------------------------------
old_left=0
while IFS= read -r ref; do
    [[ "$ref" == refs/heads/main ]] && continue
    related "$ref" "$NEW" && continue
    say "branch ${ref#refs/heads/} is on the OLD history: never push it; redo its work from the new main, or delete it (git branch -D ${ref#refs/heads/})"
    old_left=1
done < <(git for-each-ref --format='%(refname)' refs/heads)
remote_tags=" $(git ls-remote -q --tags --refs origin | awk '{ print $2 }' | paste -sd' ' -) "
replaced=0
while IFS= read -r tag; do
    sha="$(git rev-parse -q --verify "$tag^{commit}")" || continue
    related "$sha" "$NEW" && continue
    if [[ $dry == 1 && "$remote_tags" == *" $tag "* ]]; then
        replaced=$((replaced + 1))
    else
        say "tag ${tag#refs/tags/} is on the OLD history (not on origin): never push it (git tag -d ${tag#refs/tags/})"
        old_left=1
    fi
done < <(git for-each-ref --format='%(refname)' refs/tags)
[[ $replaced -gt 0 ]] && say "$replaced tags would be replaced by origin's"
git worktree list --porcelain | awk '/^worktree /{ sub(/^worktree /, ""); w = $0 } /^HEAD /{ print $2 "\t" w }' |
    while IFS=$'\t' read -r head path; do
        [[ "$path" == "$ROOT" || "$(cd "$path" 2>/dev/null && pwd)" == "$ROOT" ]] && continue
        related "$head" "$NEW" || say "worktree $path is on the OLD history: never push from it; remove it when its work is safe (git worktree remove)"
    done
[[ $old_left == 0 ]] && say "no other branch or tag is on the old history"
[[ $dry == 1 ]] && say "dry run: nothing changed"
exit 0
