#!/usr/bin/env bash
# The overnight run: every on-device UI test in every style, then every language at 2× text, kept
# cool, with one summary at the end. Run it while nobody needs the phone. See
# .claude/skills/overnight-run/SKILL.md.
#
#   scripts/overnight-ui-tests.sh             the sweep and the languages
#   scripts/overnight-ui-tests.sh --sweep     only the thorough sweep of every module
#   scripts/overnight-ui-tests.sh --languages only the every-language checks
#   scripts/overnight-ui-tests.sh --store     only the Play Store screenshots (scripts/store-screenshots.sh)
#   scripts/overnight-ui-tests.sh --full ...  the languages of every module in every style, none skipped
#   scripts/overnight-ui-tests.sh --dry-run   only say which every-language checks would run (no phone)
#   scripts/overnight-ui-tests.sh --which     only say which branch tonight's run checks, and why
# (--sweep, --languages and --store may be combined.)
#
# The every-language checks of a module in a style that passed whole are noted at that commit, like a
# module's pass (scripts/ui-tests.sh): build/ui-tests-passed/languages/feature-tuner-ANCIENT holds
# "<commit> <seconds since 1970> <date>". A later night skips them while nothing the module shows has
# changed since (scripts/ui-test-scope.py, the rule --since-pass uses) and the pass is under
# LANGUAGES_MAX_AGE_DAYS old (7): so a check runs again within a week whatever happens, in case the
# scope missed a change. With 25 languages the checks of every module in every style took about 6 h (2 h since the same day's faster checks);
# owner, 2026-10-30: skip what hasn't changed, and make the store screenshots only before a listing is
# sent (scripts/release.sh listing refuses screenshots older than the app's screens), not every night.
#
# Logs go to build/overnight/ (sweep.txt, languages.txt, store.txt, summary.txt); pictures of screens
# where a language didn't fit go to each module's build/outputs/connected_android_test_additional_output,
# the store screenshots to fastlane/metadata/android/<locale>/images/phoneScreenshots/. Each module the
# sweep passes is noted as passed at this commit (scripts/ui-tests.sh, --since-pass), for the release.
#
# One branch a night (CLAUDE.md, *The v2 branch*): main, or v2 while 2.0 is built there. A run on v2
# keeps its records apart, the logs in build/overnight/v2/ and the passes in build/ui-tests-passed/v2/,
# so main's summary and notes, which a release reads, stay main's.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ADB_EXE="${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"
COOL_BELOW="${COOL_BELOW:-37}"
# The line this checkout is on, by the rule of scripts/ui-tests.sh's on_v2: v2 when it holds a commit
# of origin/v2 that main lacks, otherwise main.
line=main
v2_base="$(git merge-base HEAD origin/v2 2>/dev/null)" && ! git merge-base --is-ancestor "$v2_base" origin/main 2>/dev/null && line=v2
OUT="$ROOT/build/overnight"
[ $line = v2 ] && OUT="$OUT/v2"
# The modules with @EveryLanguage tests, and the styles (VisualStyle) they are parameterized by.
# Either list can be narrowed for a short try (LANGUAGE_MODULES=":app" LANGUAGE_STYLES="CLASSIC").
read -ra LANGUAGE_MODULES <<<"${LANGUAGE_MODULES:-:feature:tuner :feature:settings :feature:metronome :feature:practice :feature:eartraining :app}"
read -ra STYLES <<<"${LANGUAGE_STYLES:-ANCIENT CLASSIC RETRO CONTEMPORARY FUTURISTIC SPACE_AGE}"

LANGUAGES_MAX_AGE_DAYS="${LANGUAGES_MAX_AGE_DAYS:-7}"

# The branch tonight's run checks, as "<branch>: <why>" (after a git fetch): main when it has code no
# clean overnight run passed (a release needs one over the code it releases; docs, .claude/, scripts/
# and the Baseline Profile aside, the release skill's rule, and fastlane/'s store texts and release
# notes, which no test on the phone reads), otherwise v2 while origin/v2 exists. It
# reads main's summary in the main folder; a run whose failures were fixed reads as not clean, so main
# again, unless the owner names v2.
which_branch() {
    local summary commit
    summary="$(cd "$(git rev-parse --path-format=absolute --git-common-dir)/.." && pwd)/build/overnight/summary.txt"
    commit="$(sed -n '1s/^Overnight run[^:]*: [0-9]* min at \([0-9a-f]*\), everything passed$/\1/p' "$summary" 2>/dev/null)"
    if ! git rev-parse -q --verify origin/v2 > /dev/null; then
        echo "main: there is no v2 branch"
    elif [ -z "$commit" ] || ! git cat-file -e "$commit^{commit}" 2>/dev/null; then
        echo "main: its last overnight run on this PC didn't pass everything (or there is none)"
    elif ! git diff --quiet "$commit" origin/main -- . ':(exclude)*.md' ':(exclude)docs' ':(exclude).claude' \
        ':(exclude)scripts' ':(exclude)app/src/release/generated' ':(exclude)fastlane'; then
        echo "main: its code changed since its last clean overnight run (at ${commit:0:8})"
    else
        echo "v2: main's code is as its last clean overnight run passed it (at ${commit:0:8})"
    fi
}

sweep=0 languages=0 store=0 full=0 dry_run=0 picked=0
for arg in "$@"; do
    case "$arg" in
        --sweep) sweep=1 picked=1 ;;
        --languages) languages=1 picked=1 ;;
        --store) store=1 picked=1 ;;
        --full) full=1 ;;
        --dry-run) dry_run=1 ;;
        --which) which_branch; exit 0 ;;
        *) sed -n '2,31p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
    esac
done
[ $picked = 1 ] || { sweep=1; languages=1; }
mkdir -p "$OUT"

# Where the passes of the every-language checks are noted: next to the modules' (scripts/ui-tests.sh),
# in the main folder, so a pass counts for every checkout of this PC; v2's apart, as there.
LANGUAGES_PASSED="$(cd "$(git rev-parse --path-format=absolute --git-common-dir)/.." && pwd)/build/ui-tests-passed"
[ $line = v2 ] && LANGUAGES_PASSED="$LANGUAGES_PASSED/v2"
LANGUAGES_PASSED="$LANGUAGES_PASSED/languages"
languages_note() { local m="${1#:}"; echo "$LANGUAGES_PASSED/${m//:/-}-$2"; }

# The modules a change since [commit] can reach (scripts/ui-test-scope.py), worked out once per commit.
reached_since() {
    local cache="$OUT/reached-$1.txt"
    [ -f "$cache" ] || { python -I scripts/ui-test-scope.py "$1" | tr -d '\r' > "$cache.tmp" && mv "$cache.tmp" "$cache"; } ||
        { rm -f "$cache.tmp"; return 1; }
    cat "$cache"
}

# Says when [module]'s every-language checks in [style] last passed, if they need no run: passed whole
# at a commit this clone has, under LANGUAGES_MAX_AGE_DAYS ago, and nothing the module shows changed since.
languages_still_good() {
    local file commit epoch when reached
    file="$(languages_note "$1" "$2")"
    [ $full = 0 ] && [ -f "$file" ] || return 1
    read -r commit epoch when < "$file" || return 1
    git cat-file -e "$commit^{commit}" 2>/dev/null || return 1
    [ $(( $(date +%s) - epoch )) -lt $(( LANGUAGES_MAX_AGE_DAYS * 86400 )) ] || return 1
    reached="$(reached_since "$commit")" || return 1
    grep -qx -- "$1" <<<"$reached" && return 1
    echo "$when, at ${commit:0:8}"
}

if [ $dry_run = 1 ]; then
    rm -f "$OUT"/reached-*.txt
    echo "This checkout is on $line (records in ${OUT#"$ROOT"/}/). Tonight's default, $(which_branch)"
    would=0
    for module in "${LANGUAGE_MODULES[@]}"; do
        for style in "${STYLES[@]}"; do
            if since="$(languages_still_good "$module" "$style")"; then
                echo "skip $module $style: passed $since, nothing it shows changed since"
            else
                echo "run  $module $style"
                would=$((would + 1))
            fi
        done
    done
    echo "$would of $(( ${#LANGUAGE_MODULES[@]} * ${#STYLES[@]} )) every-language checks would run."
    exit 0
fi

adb() { MSYS_NO_PATHCONV=1 "$ADB_EXE" "$@"; }

# Keeps Windows from sleeping while the run lasts: on 2026-10-19 the PC went to sleep after an idle
# hour, the phone's connection went with it, and one test seemed to hang for 8 hours. A PowerShell
# that sets SetThreadExecutionState(ES_CONTINUOUS | ES_SYSTEM_REQUIRED) again every 30 s until it is
# stopped; the display may still turn off. Its C# goes in single quotes: PowerShell doesn't take \"
# inside a string, so the first version never loaded it, failed quietly and the PC slept from 02:03
# to 09:17 on 2026-10-27. It now stops on any error, and the run says so when it isn't holding.
keep_awake() {
    command -v powershell.exe > /dev/null || return 0
    powershell.exe -NoProfile -NonInteractive -Command '
        $ErrorActionPreference = "Stop"
        Add-Type -Name Power -Namespace Twangle -MemberDefinition '\''[DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint flags);'\''
        while ($true) {
            if ([Twangle.Power]::SetThreadExecutionState([uint32]"0x80000001") -eq 0) { exit 1 }
            Start-Sleep -Seconds 30
        }' > /dev/null 2>&1 &
    awake_pid=$!
    trap 'kill $awake_pid 2> /dev/null' EXIT
    sleep 10
    kill -0 $awake_pid 2> /dev/null || echo "Warning: could not keep Windows awake; set the PC to never sleep for tonight." >&2
}
keep_awake

# The battery's temperature in whole degrees; waits (screen on, so the phone stays unlocked) while
# it is at COOL_BELOW or warmer, at most 30 minutes.
wait_cool() {
    local tenths t waited=0
    while :; do
        tenths="$(adb shell dumpsys battery 2>/dev/null | tr -d '\r' | sed -n 's/^ *temperature: \([0-9]*\)/\1/p' | head -1)"
        t=$(( ${tenths:-0} / 10 ))
        if [ "$t" -lt "$COOL_BELOW" ] || [ $waited -ge 1800 ]; then break; fi
        sleep 30
        waited=$((waited + 30))
    done
    [ $waited -gt 0 ] && echo "  (cooled ${waited}s to ${t} °C)"
    return 0
}

start=$SECONDS
status=0
# Only a clean working tree is a commit a pass can be noted at (as in scripts/ui-tests.sh).
tested_commit="$(git rev-parse HEAD)"
[ -z "$(git status --porcelain)" ] && clean=1 || clean=0
logs=()
if [ $sweep = 1 ]; then
    logs+=("$OUT/sweep.txt")
    echo "== Thorough sweep of every module ($(date +%H:%M))" | tee "$OUT/sweep.txt"
    MODULE_TIMEOUT=1200 scripts/ui-tests.sh --all --thorough --cool 2>&1 | tee -a "$OUT/sweep.txt"
    [ "${PIPESTATUS[0]}" = 0 ] || status=1
fi
skipped=0 checked=0
if [ $languages = 1 ]; then
    logs+=("$OUT/languages.txt")
    rm -f "$OUT"/reached-*.txt   # worked out against tonight's tree
    echo "== Every language at 2× text ($(date +%H:%M))" | tee "$OUT/languages.txt"
    for module in "${LANGUAGE_MODULES[@]}"; do
        for style in "${STYLES[@]}"; do
            if since="$(languages_still_good "$module" "$style")"; then
                echo "-- $module $style: passed $since, nothing it shows changed since" | tee -a "$OUT/languages.txt"
                skipped=$((skipped + 1))
                continue
            fi
            checked=$((checked + 1))
            wait_cool | tee -a "$OUT/languages.txt"
            echo "-- $module $style" | tee -a "$OUT/languages.txt"
            run="$OUT/language-run.txt"
            : > "$run"
            MODULE_TIMEOUT=3000 scripts/ui-tests.sh "$module" --thorough --all-languages -f "everyLanguage.*\[$style\]" 2>&1 | tee -a "$OUT/languages.txt" "$run"
            if [ "${PIPESTATUS[0]}" != 0 ]; then
                status=1
            elif [ $clean = 1 ] && grep -qE " [1-9][0-9]* tests, 0 failed" "$run"; then
                mkdir -p "$LANGUAGES_PASSED"
                echo "$tested_commit $(date +%s) $(date '+%Y-%m-%d %H:%M')" > "$(languages_note "$module" "$style")"
            fi
        done
    done
fi
if [ $store = 1 ]; then
    logs+=("$OUT/store.txt")
    echo "== Store screenshots in every language ($(date +%H:%M))" | tee "$OUT/store.txt"
    wait_cool | tee -a "$OUT/store.txt"
    scripts/store-screenshots.sh 2>&1 | tee -a "$OUT/store.txt"
    [ "${PIPESTATUS[0]}" = 0 ] || status=1
fi

tested="at $(git rev-parse --short HEAD)"
[ $clean = 1 ] || tested="$tested with uncommitted changes"
{
    echo "Overnight run on $line: $(( (SECONDS - start) / 60 )) min $tested, $([ $status = 0 ] && echo "everything passed" || echo "failures below")"
    [ $languages = 1 ] && echo "Every language: $checked of $((checked + skipped)) module-and-style checks ran; $skipped passed earlier with nothing they show changed since"
    cat "${logs[@]}" 2>/dev/null | grep -E "FAIL|CRASH|TIMED OUT|no results|Build failed|screen went off" || true
} > "$OUT/summary.txt"
cat "$OUT/summary.txt"
exit $status
