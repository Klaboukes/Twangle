#!/usr/bin/env bash
# The full check: the watch app's tests on the watch when one is connected, every on-device UI test
# (in Ink, light and dark), the screens' tests again in the high-contrast look (its light and dark, Phase
# 86), then every language at 2× text, kept cool, with one summary at the end. Run it while nobody
# needs the phone. See
# .claude/skills/full-check/SKILL.md.
#
#   scripts/full-check.sh             the watch, the sweep, high contrast and the languages
#   scripts/full-check.sh --watch     only the watch app's tests, on the watch
#   scripts/full-check.sh --sweep     only the thorough sweep of every module
#   scripts/full-check.sh --contrast  only the high-contrast pass (the screens' modules)
#   scripts/full-check.sh --languages only the every-language checks
#   scripts/full-check.sh --store     only the Play Store screenshots (scripts/store-screenshots.sh)
#   scripts/full-check.sh --full ...  the languages of every module, none skipped
#   scripts/full-check.sh --dry-run   only say which every-language checks would run (no phone)
# (--watch, --sweep, --contrast, --languages and --store may be combined.)
#
# The every-language checks of a module that passed whole are noted at that commit, like a
# module's pass (scripts/ui-tests.sh): build/ui-tests-passed/languages/feature-tuner holds
# "<commit> <seconds since 1970> <date>". A later run skips them while nothing the module shows has
# changed since (scripts/ui-test-scope.py, the rule --since-pass uses) and the pass is under
# LANGUAGES_MAX_AGE_DAYS old (7): so a check runs again within a week whatever happens, in case the
# scope missed a change. With 25 languages the checks of every module in 1.1's six styles took about 6 h (2 h since the same day's faster checks;
# 2.0 has one look, so one check a module, all six about 17 min on 2026-11-05);
# owner, 2026-10-30: skip what hasn't changed, and make the store screenshots only before a listing is
# sent (scripts/release.sh listing refuses screenshots older than the app's screens), not every run.
#
# Logs go to build/full-check/ (sweep.txt, contrast.txt, languages.txt, store.txt, summary.txt); pictures of screens
# where a language didn't fit go to each module's build/outputs/connected_android_test_additional_output,
# the store screenshots to fastlane/metadata/android/<locale>/images/phoneScreenshots/. Each module the
# sweep passes is noted as passed at this commit (scripts/ui-tests.sh, --since-pass), for the release.
#
# The devices (scripts/devices.sh): the phone's stages run on a phone (else the tablet), the watch's
# on the watch (the Galaxy Watch 5 over Wi-Fi, docs/devices.md), each named once at the start, so a
# device that comes or goes during the run changes nothing for the others; ANDROID_SERIAL names the
# phone instead. No watch connected: its stage is skipped, and the summary says so. A watch that drops
# off during its stage (its Wi-Fi went) says nothing about the app either: not counted as a failure.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ADB_EXE="${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"
COOL_BELOW="${COOL_BELOW:-37}"
OUT="$ROOT/build/full-check"
# The modules with @EveryLanguage tests (each checks light and dark itself; 1.1 ran them once per
# style). The list can be narrowed for a short try (LANGUAGE_MODULES=":app").
read -ra LANGUAGE_MODULES <<<"${LANGUAGE_MODULES:-:feature:tuner :feature:settings :feature:metronome :feature:practice :feature:eartraining :app}"
# The modules the high-contrast pass runs again in that look (scripts/ui-tests.sh --high-contrast): the
# ones that show the app's screens. :core:testing and :core:audio draw nothing of the look. Every test
# whose TwangleTheme names no look of its own takes it (LocalHighContrastByDefault), so the tests that
# loop over light and dark check both of its faces in one pass; the app's tests turn it on through its
# setting (HighContrastPassRule). Its passes are not noted (-a), the Ink sweep's are.
read -ra CONTRAST_MODULES <<<"${CONTRAST_MODULES:-:feature:tuner :feature:settings :feature:metronome :feature:practice :feature:eartraining :app}"

LANGUAGES_MAX_AGE_DAYS="${LANGUAGES_MAX_AGE_DAYS:-7}"

watch=0 sweep=0 contrast=0 languages=0 store=0 full=0 dry_run=0 picked=0
for arg in "$@"; do
    case "$arg" in
        --watch) watch=1 picked=1 ;;
        --sweep) sweep=1 picked=1 ;;
        --contrast) contrast=1 picked=1 ;;
        --languages) languages=1 picked=1 ;;
        --store) store=1 picked=1 ;;
        --full) full=1 ;;
        --dry-run) dry_run=1 ;;
        *) sed -n '2,38p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
    esac
done
[ $picked = 1 ] || { watch=1; sweep=1; contrast=1; languages=1; }
mkdir -p "$OUT"

# Where the passes of the every-language checks are noted: next to the modules' (scripts/ui-tests.sh),
# in the main folder, so a pass counts for every checkout of this PC.
LANGUAGES_PASSED="$(cd "$(git rev-parse --path-format=absolute --git-common-dir)/.." && pwd)/build/ui-tests-passed/languages"
languages_note() { local m="${1#:}"; echo "$LANGUAGES_PASSED/${m//:/-}"; }

# The modules a change since [commit] can reach (scripts/ui-test-scope.py), worked out once per commit.
reached_since() {
    local cache="$OUT/reached-$1.txt"
    [ -f "$cache" ] || { python -I scripts/ui-test-scope.py "$1" | tr -d '\r' > "$cache.tmp" && mv "$cache.tmp" "$cache"; } ||
        { rm -f "$cache.tmp"; return 1; }
    cat "$cache"
}

# Says when [module]'s every-language checks last passed, if they need no run: passed whole
# at a commit this clone has, under LANGUAGES_MAX_AGE_DAYS ago, and nothing the module shows changed since.
languages_still_good() {
    local file commit epoch when reached
    file="$(languages_note "$1")"
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
    echo "Records in ${OUT#"$ROOT"/}/."
    [ $sweep = 1 ] && echo "sweep: every module, Ink light and dark (scripts/ui-tests.sh --all --thorough --cool)"
    [ $contrast = 1 ] && echo "high contrast, light and dark: ${CONTRAST_MODULES[*]} (scripts/ui-tests.sh --high-contrast --thorough --cool)"
    would=0
    for module in "${LANGUAGE_MODULES[@]}"; do
        if since="$(languages_still_good "$module")"; then
            echo "skip $module: passed $since, nothing it shows changed since"
        else
            echo "run  $module"
            would=$((would + 1))
        fi
    done
    echo "$would of ${#LANGUAGE_MODULES[@]} every-language checks would run."
    exit 0
fi

adb() { MSYS_NO_PATHCONV=1 "$ADB_EXE" "$@"; }
. "$ROOT/scripts/devices.sh"

# The phone and the watch for this run, picked once (the phone's stages, and wait_cool, use ANDROID_SERIAL).
PHONE="${ANDROID_SERIAL:-$(first_device phone tablet)}"
WATCH="$(first_device watch)"
[ -n "$PHONE" ] && export ANDROID_SERIAL="$PHONE"
phone_name="$([ -n "$PHONE" ] && device_name "$PHONE" || echo "no phone")"

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
    kill -0 $awake_pid 2> /dev/null || echo "Warning: could not keep Windows awake; set the PC to never sleep while it runs." >&2
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
watch_result=""
if [ $watch = 1 ]; then
    # First, while the watch's Wi-Fi link is fresh.
    echo "== The watch ($(date +%H:%M))" | tee "$OUT/watch.txt"
    if [ -z "$WATCH" ]; then
        watch_result="not connected, so its tests didn't run (wake it on its charger before the run)"
        echo "No watch connected: $watch_result" | tee -a "$OUT/watch.txt"
    else
        watch_name="$(device_name "$WATCH")"
        echo "On the $watch_name" | tee -a "$OUT/watch.txt"
        ANDROID_SERIAL="$WATCH" MODULE_TIMEOUT=1200 scripts/ui-tests.sh :wear --thorough 2>&1 | tee -a "$OUT/watch.txt"
        if [ "${PIPESTATUS[0]}" = 0 ]; then
            watch_result="passed on the $watch_name"
            logs+=("$OUT/watch.txt")
        elif ready_devices | grep -qxF -- "$WATCH"; then
            watch_result="failures below, on the $watch_name"
            logs+=("$OUT/watch.txt")
            status=1
        else
            watch_result="the $watch_name dropped off during its run, so its results say nothing (log: ${OUT#"$ROOT"/}/watch.txt)"
            echo "The watch dropped off during its run: its results say nothing" | tee -a "$OUT/watch.txt"
        fi
    fi
fi
if [ $sweep = 1 ]; then
    logs+=("$OUT/sweep.txt")
    echo "== Thorough sweep of every module ($(date +%H:%M))" | tee "$OUT/sweep.txt"
    MODULE_TIMEOUT=1200 scripts/ui-tests.sh --all --thorough --cool 2>&1 | tee -a "$OUT/sweep.txt"
    [ "${PIPESTATUS[0]}" = 0 ] || status=1
fi
if [ $contrast = 1 ]; then
    logs+=("$OUT/contrast.txt")
    echo "== The screens in high contrast, light and dark ($(date +%H:%M))" | tee "$OUT/contrast.txt"
    wait_cool | tee -a "$OUT/contrast.txt"
    MODULE_TIMEOUT=1200 scripts/ui-tests.sh "${CONTRAST_MODULES[@]}" --high-contrast --thorough --cool 2>&1 | tee -a "$OUT/contrast.txt"
    [ "${PIPESTATUS[0]}" = 0 ] || status=1
fi
skipped=0 checked=0
if [ $languages = 1 ]; then
    logs+=("$OUT/languages.txt")
    rm -f "$OUT"/reached-*.txt   # worked out against this run's tree
    echo "== Every language at 2× text ($(date +%H:%M))" | tee "$OUT/languages.txt"
    for module in "${LANGUAGE_MODULES[@]}"; do
        if since="$(languages_still_good "$module")"; then
            echo "-- $module: passed $since, nothing it shows changed since" | tee -a "$OUT/languages.txt"
            skipped=$((skipped + 1))
            continue
        fi
        checked=$((checked + 1))
        wait_cool | tee -a "$OUT/languages.txt"
        echo "-- $module" | tee -a "$OUT/languages.txt"
        run="$OUT/language-run.txt"
        : > "$run"
        MODULE_TIMEOUT=3000 scripts/ui-tests.sh "$module" --thorough --all-languages -f "everyLanguage" 2>&1 | tee -a "$OUT/languages.txt" "$run"
        if [ "${PIPESTATUS[0]}" != 0 ]; then
            status=1
        elif [ $clean = 1 ] && grep -qE " [1-9][0-9]* tests, 0 failed" "$run"; then
            mkdir -p "$LANGUAGES_PASSED"
            echo "$tested_commit $(date +%s) $(date '+%Y-%m-%d %H:%M')" > "$(languages_note "$module")"
        fi
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
    echo "Full check: $(( (SECONDS - start) / 60 )) min $tested, $([ $status = 0 ] && echo "everything passed" || echo "failures below")"
    [ $languages = 1 ] && echo "Every language: $checked of $((checked + skipped)) module checks ran; $skipped passed earlier with nothing they show changed since"
    [ $sweep = 1 ] || [ $contrast = 1 ] || [ $languages = 1 ] || [ $store = 1 ] && echo "Phone: the $phone_name"
    [ $watch = 1 ] && echo "Watch: $watch_result"
    cat "${logs[@]}" 2>/dev/null | grep -E "FAIL|CRASH|TIMED OUT|no results|Build failed|screen went off" || true
} > "$OUT/summary.txt"
cat "$OUT/summary.txt"
exit $status
