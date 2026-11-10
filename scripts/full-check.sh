#!/usr/bin/env bash
# The full check: the watch app's tests on the watch when one is connected, a smoke test of the release
# build on the phone (scripts/release-smoke.sh: the UI tests run the debug build, which R8 never touches), every on-device UI test
# (in Ink, light and dark), the screens' tests again in the high-contrast look (its light and dark, Phase
# 86), then every language at 2× text, kept cool, with one summary at the end. Run it while nobody
# needs the phone. See
# .claude/skills/full-check/SKILL.md.
#
#   scripts/full-check.sh             the watch, the release build, the sweep, high contrast and the languages
#   scripts/full-check.sh --watch     only the watch app's tests, on the watch
#   scripts/full-check.sh --release-build  only the release build's smoke test (start, leave, turn; ~4 min)
#   scripts/full-check.sh --sweep     only the thorough sweep of every module
#   scripts/full-check.sh --contrast  only the high-contrast pass (the screens' modules)
#   scripts/full-check.sh --languages only the every-language checks
#   scripts/full-check.sh --store     only the Play Store screenshots (scripts/store-screenshots.sh)
#   scripts/full-check.sh --full ...  the languages of every module, none skipped
#   scripts/full-check.sh --dry-run   only say which devices and jobs it would run (nothing runs)
#   scripts/full-check.sh --fixed <PR>  once that PR fixed a run's failures and they were re-checked on
#                                     main: notes it for the release (no device; see The record below)
#   scripts/full-check.sh --self-check  checks the record's logic on made-up runs (no device, no build)
# (--watch, --release-build, --sweep, --contrast, --languages and --store may be combined.)
#
# Its exit code (Phase 90): 0 everything passed; 1 something failed (a test, the build, the release
# build, a job no device was left for); 5 nothing failed, but a device was busy with another run, so
# part of it didn't run; 3 the phones passed but the watch's tests didn't run (no watch, it dropped off
# or was busy). The summary's first line says the same: "everything passed", "failures below" or "not
# finished: a device was busy with another run", with ", watch not tested" whenever the watch's tests
# were asked for and didn't run; the busy message itself ("Another run (...) is using the device") is
# in the summary.
#
# The record a release reads (scripts/release.sh full-check; bundle refuses without it):
# build/full-check/result.txt, a line a part, "phones <code> <commit> <clean> <seconds since 1970>
# <date> [fixed <PR> <the run's commit> | failed <what>]" and "watch <code> ...", the code 0 passed,
# 1 something failed (all of it ran, or nothing did: the one build failed, cut off by BUILD_TIMEOUT
# or not, or no phone was there), 5 not all of it ran (a device busy, or none left for a job), and for the watch 2 not
# tested. A phones line with 1 names what failed, "failed build" or "failed sweep,languages" (build,
# no-device, release: its smoke test, sweep, contrast, languages; Phase 91: a failed build ran no job,
# so the line said 0 until then). A part's line is written only by a run that did all of it (the
# phones: the release build, the sweep, high contrast and the languages, no module list narrowed; the
# watch: its tests); a run of one part leaves the other's line as it was. --fixed turns a failed phones
# line into a pass at the commit checked out now, once every module passed after its last change
# (scripts/ui-tests.sh --since-pass has nothing left to run), and adds the PR to the summary's first line.
#
# The commit and clean state the run started from go to every device's scripts/ui-tests.sh --built
# (UI_TESTS_COMMIT, UI_TESTS_CLEAN), so a doc edited while it runs (which the full-check skill allows)
# no longer stops the later modules' passes from being noted.
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
# Logs go to build/full-check/ (build.txt, watch.txt, release-build.txt, sweep.txt, contrast.txt, languages.txt, store.txt, summary.txt;
# queue/ holds each job's own log while it runs); pictures of screens where a language didn't fit go to
# each module's build/outputs/connected_android_test_additional_output (with -<tag> when several devices ran),
# the store screenshots to fastlane/metadata/android/<locale>/images/phoneScreenshots/. Each module the
# sweep passes is noted as passed at this commit (scripts/ui-tests.sh, --since-pass), for the release.
#
# The devices (scripts/devices.sh), named once at the start, so a device that comes or goes during
# the run changes nothing for the others. The phones' stages run on every phone and tablet connected
# (owner, 2026-11-09): one build, then each device takes the next job from one queue (a module's sweep,
# its high-contrast run, its every-language check) whenever it is free: two phones take a little over half
# the time, a phone and the tablet (2½ times slower) about two thirds; the release build's smoke test runs first on the first phone. One device alone does every
# job, as before. FULL_CHECK_SERIALS="<serial> <serial>" names the devices, ANDROID_SERIAL one alone. A
# device that drops off mid-job puts that job back for the others and says so. The watch's tests run on
# the watch (the Galaxy Watch 5 over Wi-Fi, docs/devices.md) beside them. No watch connected: its stage
# is skipped, and the summary says so. A watch that drops off during its stage (its Wi-Fi went) says
# nothing about the app either: no failure, but "watch not tested" (exit 3).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# adb, the devices and cooling (wait_cool, COOL_BELOW): shared with the other scripts that use a device.
. "$ROOT/scripts/devices.sh"
OUT="$ROOT/build/full-check"
# A run with a module list narrowed for a short try (below) vouches for no release (The record).
[ -n "${LANGUAGE_MODULES:-}${CONTRAST_MODULES:-}${SWEEP_MODULES:-}" ] && narrowed=1 || narrowed=0
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

watch=0 release_build=0 sweep=0 contrast=0 languages=0 store=0 full=0 dry_run=0 self_check=0 picked=0 fixed_by=""
while [ $# -gt 0 ]; do
    arg="$1"
    shift
    case "$arg" in
        --fixed)
            fixed_by="${1:-}"
            [ $# -gt 0 ] && shift
            [ -n "$fixed_by" ] || { echo "--fixed needs the PR that fixed the failures: --fixed '#761'" >&2; exit 2; } ;;
        --watch) watch=1 picked=1 ;;
        --release-build) release_build=1 picked=1 ;;
        --sweep) sweep=1 picked=1 ;;
        --contrast) contrast=1 picked=1 ;;
        --languages) languages=1 picked=1 ;;
        --store) store=1 picked=1 ;;
        --full) full=1 ;;
        --dry-run) dry_run=1 ;;
        --self-check) self_check=1 ;;
        *) sed -n '2,73p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
    esac
done
[ $picked = 1 ] || { watch=1; release_build=1; sweep=1; contrast=1; languages=1; }
mkdir -p "$OUT"

# The record for the release (The record, above): [part]'s line, or nothing.
RESULT="$OUT/result.txt"
record_of() { grep "^$1 " "$RESULT" 2>/dev/null | tail -1; }
# Writes [part]'s line ([the rest of it]), keeping the other part's.
write_record() {
    local part="$1"
    shift
    { grep -v "^$part " "$RESULT" 2>/dev/null; echo "$part $*"; } > "$RESULT.tmp" && mv "$RESULT.tmp" "$RESULT"
}
# Writes this run's line for [part]: [code] [anything after the date]. A pass or a failure always; a
# line saying a part didn't (all) run only over no earlier pass, which still holds for its own commit
# (scripts/release.sh compares that commit with the one it releases).
record_line() {
    local part="$1" code="$2" old
    shift 2
    read -r _ old _ <<<"$(record_of "$part")"
    if [ "$code" = 0 ] || [ "$code" = 1 ] || [ "${old:-}" != 0 ]; then
        write_record "$part" "$code" "$tested_commit" "$clean" "$(date +%s)" "$(date '+%Y-%m-%d_%H:%M')" "$@"
    fi
}

# What of the phones' part failed, each named once (The record's list, above).
phones_failures=()
note_failure() { [[ " ${phones_failures[*]} " == *" $1 "* ]] || phones_failures+=("$1"); }
# The one build's exit code. Anything but 0 is a failed build, also when BUILD_TIMEOUT cut it off
# (scripts/ui-tests.sh exits 1 then, timeout itself 124). No job runs after it, so nothing else counts
# it: until Phase 91 the record said the phones passed, though the run exited 1.
build_done() { [ "$1" = 0 ] || { build_failed=1; note_failure build; }; }
# The phones' code for the record: 1 the build failed, 5 not all the jobs ran, 1 something else
# failed, 0 everything passed.
phones_code() {
    if [ $build_failed = 1 ]; then echo 1
    elif [ $unrun = 1 ]; then echo 5
    elif [ ${#phones_failures[@]} -gt 0 ]; then echo 1
    else echo 0
    fi
}
# The phones' line, only from a run that did all of their part, with what failed on a failure.
record_phones() {
    local code
    [ $release_build = 1 ] && [ $sweep = 1 ] && [ $contrast = 1 ] && [ $languages = 1 ] && [ $narrowed = 0 ] || return 0
    code="$(phones_code)"
    if [ "$code" = 1 ]; then
        record_line phones 1 failed "$(IFS=,; echo "${phones_failures[*]}")"
    else
        record_line phones "$code"
    fi
}
# The run's outcome, exit_code and its first line's words (the header says which), from failed (the
# store screenshots), the phones' failures, unrun and busy, and the watch's code.
run_outcome() {
    [ ${#phones_failures[@]} -gt 0 ] && failed=1
    [ $watch = 1 ] && [ "${watch_code:-}" = 1 ] && failed=1
    [ $unrun = 1 ] && [ $busy = 0 ] && failed=1
    watch_untested=0
    [ $watch = 1 ] && [ "${watch_code:-}" = 2 ] && watch_untested=1
    if [ $failed = 1 ]; then
        exit_code=1 outcome="failures below"
    elif [ $unrun = 1 ]; then
        # (a job left unrun with no device busy counts as a failure above)
        exit_code=5 outcome="not finished: a device was busy with another run (below)"
    elif [ $watch_untested = 1 ]; then
        exit_code=3 outcome="everything else passed"
    else
        exit_code=0 outcome="everything passed"
    fi
    [ $watch_untested = 1 ] && outcome="$outcome, watch not tested"
    return 0
}

# --self-check: the record's logic above on made-up runs, in a record of its own; says each case and
# exits 1 when one went wrong. Each case: its name, the phones line before it (its code; - none), the
# line expected after it ("<code> <commit>[ <the rest>]"; - none), the run's exit code, then its steps.
self_check() {
    local bad=0 name before expected want_exit got
    RESULT="$(mktemp)"
    tested_commit=this clean=1 watch=0 store=0
    check() {
        name="$1" before="$2" expected="$3" want_exit="$4"
        shift 4
        phones_failures=() build_failed=0 unrun=0 busy=0 failed=0 watch_code="" narrowed=0
        release_build=1 sweep=1 contrast=1 languages=1
        : > "$RESULT"
        [ "$before" = - ] || echo "phones $before earlier 1 1 2026-11-01_12:00" > "$RESULT"
        local step
        for step in "$@"; do eval "$step"; done
        record_phones
        run_outcome
        got="$(record_of phones | awk '{ printf "%s %s", $2, $3; for (i = 7; i <= NF; i++) printf " %s", $i }')"
        [ -n "$got" ] || got=-
        if [ "$got" = "$expected" ] && [ "$exit_code" = "$want_exit" ]; then
            echo "ok    $name: $got, exit $exit_code"
        else
            echo "WRONG $name: $got, exit $exit_code; expected $expected, exit $want_exit"
            bad=1
        fi
    }
    check "everything passed" - "0 this" 0 "build_done 0"
    check "the build failed" - "1 this failed build" 1 "build_done 1"
    check "the build cut off by BUILD_TIMEOUT (timeout's own code)" - "1 this failed build" 1 "build_done 124"
    check "the build failed after a pass" 0 "1 this failed build" 1 "build_done 1"
    check "the build failed, a narrowed run" 0 "0 earlier" 1 "narrowed=1" "build_done 1"
    check "a module's sweep and a language check failed" - "1 this failed sweep,languages" 1 \
        "build_done 0" "note_failure sweep" "note_failure languages" "note_failure sweep"
    check "no phone connected" - "1 this failed no-device" 1 "build_done 0" "note_failure no-device"
    check "a job left while a device was busy, after a pass" 0 "0 earlier" 5 "build_done 0" "unrun=1 busy=1"
    check "a job left while a device was busy, after a failure" 1 "5 this" 5 "build_done 0" "unrun=1 busy=1"
    check "a job no device was left for" - "5 this" 1 "build_done 0" "unrun=1"
    check "the store screenshots failed" 0 "0 this" 1 "build_done 0" "failed=1"
    rm -f "$RESULT"
    [ $bad = 0 ] && echo "The record's logic: every case as expected." || echo "The record's logic: a case went wrong (WRONG above)."
    return $bad
}
[ $self_check = 1 ] && { self_check; exit $?; }

# --fixed: the last whole run's phones failed, its failures were fixed by [PR] and re-checked on main.
if [ -n "$fixed_by" ]; then
    [ $picked = 0 ] && [ $dry_run = 0 ] || { echo "--fixed goes on its own." >&2; exit 2; }
    read -r part code commit was_clean _ <<<"$(record_of phones)"
    [ "${part:-}" = phones ] || { echo "No full check of the phones on record here ($RESULT): nothing to mark fixed." >&2; exit 1; }
    [ "$code" = 1 ] || { echo "The last full check's phones had $([ "$code" = 0 ] && echo "no failures to fix" || echo "jobs that didn't run (a device busy, or none left): run it again, a fix can't vouch for them")." >&2; exit 1; }
    [ "$was_clean" = 1 ] || { echo "The last full check ran with uncommitted changes, so it vouches for no commit: run it again." >&2; exit 1; }
    [ -z "$(git status --porcelain)" ] || { echo "Uncommitted changes here: --fixed notes a commit, so commit or drop them first." >&2; exit 1; }
    git merge-base --is-ancestor "$commit" HEAD 2>/dev/null ||
        { echo "This checkout ($(git rev-parse HEAD | cut -c1-8)) doesn't follow the run's commit ${commit:0:8}: run --fixed on main after the fix landed." >&2; exit 1; }
    left="$(scripts/ui-tests.sh --since-pass --dry-run 2>&1 | tr -d '\r')"
    if ! grep -q "nothing to run" <<<"$left"; then
        echo "Not every module passed after its last change, so the fix isn't re-checked yet: run scripts/ui-tests.sh --since-pass first." >&2
        grep -E "^(To run|Would run):" <<<"$left" >&2
        exit 1
    fi
    now="$(git rev-parse HEAD)"
    fixed_by="${fixed_by//[ |&\\\/]/_}"
    write_record phones 0 "$now" 1 "$(date +%s)" "$(date '+%Y-%m-%d_%H:%M')" fixed "$fixed_by" "$commit"
    [ -f "$OUT/summary.txt" ] && sed -i "1s|\$|; fixed by $fixed_by and re-checked, at ${now:0:8}|" "$OUT/summary.txt"
    echo "Noted: the full check at ${commit:0:8}, its failures fixed by $fixed_by and re-checked, counts at ${now:0:8}."
    exit 0
fi

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

# Every module with UI tests (scripts/ui-tests.sh's own list), the longest first, so the jobs share out
# evenly between the devices (2026-11-08 on the Xiaomi, the sweep: the tuner 5 min, Practice 3½, the
# app 2, Settings and the metronome about 1 each). A module this list lacks goes last. The sweep can be
# narrowed for a short try, like the other two lists (SWEEP_MODULES=":core:testing").
LONGEST_FIRST=(:feature:tuner :feature:practice :app :feature:settings :feature:metronome :feature:eartraining :core:audio :core:testing)
sweep_modules() {
    local all module
    all=" ${SWEEP_MODULES:-$(scripts/ui-tests.sh --all --dry-run | tr -d '\r' | sed -n 's/^Would run: //p')} "
    for module in "${LONGEST_FIRST[@]}"; do [[ "$all" == *" $module "* ]] && echo "$module"; done
    for module in $all; do [[ " ${LONGEST_FIRST[*]} " == *" $module "* ]] || echo "$module"; done
    return 0
}

# The phones' jobs, one a line ("<stage> <module>"), in the order the devices take them: the
# every-language checks first (the longest, when they run, and best on a cool phone), then each
# module's sweep and high-contrast run, longest first. The language checks that need no run go to
# [file] as lines for the log.
make_jobs() {
    local module since
    : > "$1"
    if [ $languages = 1 ]; then
        for module in "${LANGUAGE_MODULES[@]}"; do
            if since="$(languages_still_good "$module")"; then
                echo "-- $module: passed $since, nothing it shows changed since" >> "$1"
            else
                echo "languages $module"
            fi
        done
    fi
    for module in $(sweep_modules); do
        [ $sweep = 1 ] && echo "sweep $module"
        [ $contrast = 1 ] && [[ " ${CONTRAST_MODULES[*]} " == *" $module "* ]] && echo "contrast $module"
    done
    return 0
}

# The devices, picked once at the start, so a device that comes or goes during the run changes
# nothing for the others: every phone and tablet connected, phones first (FULL_CHECK_SERIALS="<serial>
# <serial>" names them instead, ANDROID_SERIAL one alone), and the watch.
if [ -n "${FULL_CHECK_SERIALS:-}" ]; then
    read -ra LANES <<<"$FULL_CHECK_SERIALS"
elif [ -n "${ANDROID_SERIAL:-}" ]; then
    LANES=("$ANDROID_SERIAL")
else
    mapfile -t LANES < <(devices_of phone tablet)
fi
WATCH="$([ $watch = 1 ] && first_device watch)"
# Each device's name, and with more than one its tag (phone, tablet, phone2...): its lines in the
# output start with it, and its logs and pulled pictures carry it (UI_TESTS_TAG), so two devices running
# the same module keep apart. One device alone has no tag, so its files are named as they always were.
NAMES=() TAGS=()
declare -A kinds_seen=()
for i in "${!LANES[@]}"; do
    NAMES[i]="$(device_name "${LANES[i]}")"
    TAGS[i]=""
    if [ ${#LANES[@]} -gt 1 ]; then
        kind="$(device_kind "${LANES[i]}")"
        kinds_seen[$kind]=$(( ${kinds_seen[$kind]:-0} + 1 ))
        TAGS[i]="$kind"
        [ "${kinds_seen[$kind]}" -gt 1 ] && TAGS[i]="$kind${kinds_seen[$kind]}"
    fi
done
devices_line() {
    local i out=""
    for i in "${!LANES[@]}"; do out="${out:+$out, }the ${NAMES[i]}${TAGS[i]:+ [${TAGS[i]}]}"; done
    echo "${out:-no phone or tablet connected}"
}

QUEUE="$OUT/queue"
if [ $dry_run = 1 ]; then
    rm -f "$OUT"/reached-*.txt
    echo "Records in ${OUT#"$ROOT"/}/."
    echo "Devices: $(devices_line)$([ ${#LANES[@]} -gt 1 ] && echo ", each taking the next job when it is free")"
    [ $watch = 1 ] && echo "Watch: ${WATCH:-none connected, so its stage is skipped and the run says \"watch not tested\" (exit 3 when all else passes)}"
    [ $release_build = 1 ] && echo "release build: start, leave, turn, on the first device (scripts/release-smoke.sh)"
    skipped_file="$(mktemp)"
    mapfile -t jobs < <(make_jobs "$skipped_file")
    cat "$skipped_file" | sed 's/^-- /skip /'
    echo "The jobs, in the order the devices take them (sweep: Ink light and dark; contrast: high contrast; languages: every language at 2×):"
    printf '  %s\n' "${jobs[@]}"
    would="$(printf '%s\n' "${jobs[@]}" | grep -c '^languages ')"
    echo "$would of ${#LANGUAGE_MODULES[@]} every-language checks would run."
    rm -f "$skipped_file"
    exit 0
fi

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

# Each line of a device's output, starting with its tag ([tablet] ...) when there are several.
prefix() { if [ -n "$1" ]; then sed -u "s/^/[$1] /"; else cat; fi; }

# The queue: list holds the jobs, next the number of the last one taken. A device takes the next job
# under a lock (a folder, made in one step), so no job is taken twice.
queue_lock() { until mkdir "$QUEUE/lock" 2>/dev/null; do sleep 0.2; done; }
queue_unlock() { rmdir "$QUEUE/lock"; }
claim_job() {
    local n line
    queue_lock
    n=$(( $(cat "$QUEUE/next") + 1 ))
    line="$(sed -n "${n}p" "$QUEUE/list")"
    [ -n "$line" ] && echo "$n" > "$QUEUE/next"
    queue_unlock
    [ -n "$line" ] && echo "$n $line"
}
requeue() { queue_lock; echo "$1" >> "$QUEUE/list"; queue_unlock; }
unclaimed() { echo $(( $(wc -l < "$QUEUE/list") - $(cat "$QUEUE/next") )); }

# One job on this lane's device: [stage] [module] [job number]. Before a high-contrast or language job it
# lets the device cool (wait_cool, scripts/devices.sh, on ANDROID_SERIAL); a sweep cools class by class.
run_job() {
    local code
    case "$1" in
        sweep)
            MODULE_TIMEOUT=1200 scripts/ui-tests.sh "$2" --built --thorough --cool 2>&1 ;;
        contrast)
            wait_cool
            MODULE_TIMEOUT=1200 scripts/ui-tests.sh "$2" --built --high-contrast --thorough --cool 2>&1 ;;
        languages)
            wait_cool
            MODULE_TIMEOUT=3000 scripts/ui-tests.sh "$2" --built --thorough --all-languages -f "everyLanguage" 2>&1 | tee "$QUEUE/$3.run"
            code=${PIPESTATUS[0]}
            if [ "$code" = 0 ] && [ $clean = 1 ] && grep -qE " [1-9][0-9]* tests, 0 failed" "$QUEUE/$3.run"; then
                mkdir -p "$LANGUAGES_PASSED"
                echo "$tested_commit $(date +%s) $(date '+%Y-%m-%d %H:%M')" > "$(languages_note "$2")"
            fi
            return "$code" ;;
    esac
}

# A device's lane: [index] [1 on the first round]. On the first round the first device starts with the
# release build's smoke test, which builds itself. Then it takes job after job until none is left. A
# job that fails because the device dropped off (its cable, its Wi-Fi) goes back on the queue for the
# others, and the lane ends: its results say nothing about the app.
lane() {
    local i="$1" first="$2" job n stage module code
    local serial="${LANES[$1]}" tag="${TAGS[$1]}" name="${NAMES[$1]}"
    export ANDROID_SERIAL="$serial"
    if [ -n "$tag" ]; then export UI_TESTS_TAG="$tag"; else unset UI_TESTS_TAG; fi
    if [ "$first" = 1 ] && [ "$i" = 0 ] && [ $release_build = 1 ]; then
        echo "== The release build: start, leave, turn, on the $name ($(date +%H:%M))" | tee "$OUT/release-build.txt" | prefix "$tag"
        scripts/release-smoke.sh 2>&1 | tee -a "$OUT/release-build.txt" | prefix "$tag"
        echo "${PIPESTATUS[0]}" > "$QUEUE/release.code"
    fi
    while job="$(claim_job)" && [ -n "$job" ]; do
        read -r n stage module <<<"$job"
        echo "-- $stage $module, on the $name ($(date +%H:%M))" | tee "$QUEUE/$n.txt" | prefix "$tag"
        run_job "$stage" "$module" "$n" | tee -a "$QUEUE/$n.txt" | prefix "$tag"
        code=${PIPESTATUS[0]}
        if [ "$code" != 0 ] && ! ready_devices | grep -qxF -- "$serial"; then
            echo "The $name dropped off: $stage $module goes back on the queue" | prefix "$tag"
            mv "$QUEUE/$n.txt" "$QUEUE/$n.dropped"
            echo "$(date +%H:%M)" >> "$QUEUE/dropped-$i"
            requeue "$stage $module"
            return 0
        fi
        # Another run (another session's) holds the device: the job goes back on the queue, for another
        # device or for this one in the next round (after BUSY_WAIT), and the lane ends for this round.
        if [ "$code" = 5 ]; then
            echo "The $name is busy with another run: $stage $module goes back on the queue" | prefix "$tag"
            echo "$(date +%H:%M) $(grep -m1 "is using the device" "$QUEUE/$n.txt" | sed "s/^\[[a-z0-9]*\] //")" >> "$QUEUE/busy-$i"
            mv "$QUEUE/$n.txt" "$QUEUE/$n.busy"
            requeue "$stage $module"
            return 0
        fi
        echo "$code" > "$QUEUE/$n.code"
        echo "$n" >> "$QUEUE/done-$i"
    done
}

start=$SECONDS
# Only a clean working tree is a commit a pass can be noted at (as in scripts/ui-tests.sh).
tested_commit="$(git rev-parse HEAD)"
[ -z "$(git status --porcelain)" ] && clean=1 || clean=0
# Handed to every device's scripts/ui-tests.sh --built, which would otherwise look at the tree afresh
# for each module: one doc edited during the run then kept every later module from being noted as
# passed, and the release's --since-pass ran them again. The APKs are built once, from this tree.
export UI_TESTS_COMMIT="$tested_commit" UI_TESTS_CLEAN="$clean"
logs=()
rm -rf "$QUEUE"
mkdir -p "$QUEUE"
rm -f "$OUT"/reached-*.txt   # worked out against this run's tree
make_jobs "$QUEUE/skipped" > "$QUEUE/list"
echo 0 > "$QUEUE/next"
checked="$(grep -c '^languages ' "$QUEUE/list")"
skipped="$(wc -l < "$QUEUE/skipped")"
echo "== On $(devices_line)$([ ${#LANES[@]} -gt 1 ] && echo ", each taking the next job when it is free") ($(date +%H:%M))"

# One build for every device: the test APKs of every module, the app, and the watch app when the watch
# is there. The devices then install what it built (scripts/ui-tests.sh --built); the release build's
# smoke test builds its own.
build_failed=0
build_args=()
[ -s "$QUEUE/list" ] && build_args+=(--all)
[ -n "$WATCH" ] && build_args+=(:wear)
if [ ${#build_args[@]} -gt 0 ]; then
    echo "== One build for every device ($(date +%H:%M))" | tee "$OUT/build.txt"
    scripts/ui-tests.sh "${build_args[@]}" --build-only 2>&1 | tee -a "$OUT/build.txt"
    build_done "${PIPESTATUS[0]}"
    [ $build_failed = 1 ] && logs+=("$OUT/build.txt")
fi

# The watch app's tests, on the watch, beside the phones'.
watch_result="" watch_pid="" watch_code=""
if [ $watch = 1 ]; then
    echo "== The watch ($(date +%H:%M))" | tee "$OUT/watch.txt"
    if [ -z "$WATCH" ]; then
        watch_code=2 watch_result="not tested: not connected, so its tests didn't run (wake it on its charger before the run)"
        echo "No watch connected: its tests don't run (wake it on its charger before the run)" | tee -a "$OUT/watch.txt"
    elif [ $build_failed = 1 ]; then
        watch_code=1 watch_result="not run: the build failed"
    else
        watch_name="$(device_name "$WATCH")"
        echo "On the $watch_name" | tee -a "$OUT/watch.txt"
        (
            ANDROID_SERIAL="$WATCH" MODULE_TIMEOUT=1200 scripts/ui-tests.sh :wear --built --thorough 2>&1 | tee -a "$OUT/watch.txt" | prefix watch
            # Its result: a code (0 passed, 1 failed, 2 not tested: it dropped off or was busy, which
            # says nothing about the app) and the summary's words.
            code=${PIPESTATUS[0]}
            if [ "$code" = 0 ]; then
                echo "0 passed on the $watch_name" > "$QUEUE/watch.result"
            elif [ "$code" = 5 ]; then
                echo "2 not tested: another run was using the $watch_name ($(grep -m1 -o "Another run ([^)]*)" "$OUT/watch.txt" | sed 's/Another run (\(.*\))/\1/')), so its tests didn't run" > "$QUEUE/watch.result"
            elif ready_devices | grep -qxF -- "$WATCH"; then
                echo "1 failures below, on the $watch_name" > "$QUEUE/watch.result"
            else
                echo "The watch dropped off during its run: its results say nothing" | tee -a "$OUT/watch.txt" | prefix watch
                echo "2 not tested: the $watch_name dropped off during its run, so its results say nothing (log: ${OUT#"$ROOT"/}/watch.txt)" > "$QUEUE/watch.result"
            fi
        ) &
        watch_pid=$!
    fi
fi

# The phones' jobs: every device a lane of its own, taking the next job from the one queue. Jobs a
# device that dropped off or was busy put back are taken by the devices still there, in another round
# if those had finished; a device that came back joins in. At most three rounds; a round after one in
# which a device was busy with another run first waits BUSY_WAIT seconds (default 300) for that run.
BUSY_WAIT="${BUSY_WAIT:-300}"
if [ $build_failed = 0 ] && { [ -s "$QUEUE/list" ] || [ $release_build = 1 ]; }; then
    if [ ${#LANES[@]} = 0 ]; then
        echo "No phone or tablet connected: the phones' stages didn't run" | tee "$OUT/no-device.txt"
        logs+=("$OUT/no-device.txt")
        note_failure no-device
    else
        round=1 busy_seen=0
        while :; do
            pids=()
            for i in "${!LANES[@]}"; do
                [ $round = 1 ] || ready_devices | grep -qxF -- "${LANES[i]}" || continue
                lane "$i" "$([ $round = 1 ] && echo 1 || echo 0)" &
                pids+=($!)
            done
            [ ${#pids[@]} -gt 0 ] && wait "${pids[@]}"
            [ ${#pids[@]} -gt 0 ] && [ "$(unclaimed)" -gt 0 ] && [ $round -lt 3 ] || break
            busy_now="$(cat "$QUEUE"/busy-* 2>/dev/null | wc -l)"
            if [ "$busy_now" -gt "$busy_seen" ]; then
                echo "== Another run has a device: waiting ${BUSY_WAIT}s before the jobs left ($(date +%H:%M))"
                sleep "$BUSY_WAIT"
            fi
            busy_seen=$busy_now
            round=$((round + 1))
        done
    fi
fi
[ -n "$watch_pid" ] && wait "$watch_pid"

# The results. failed: something failed; unrun: a job or the release build didn't run; busy: a device
# was busy with another run at some point (a job it put back that another device or round ran is no
# harm). The watch's own result is watch_code: 0 passed, 1 failed, 2 not tested.
failed=0 unrun=0 busy=0
[ -n "$(ls "$QUEUE"/busy-* 2>/dev/null)" ] && busy=1
if [ -n "$watch_pid" ]; then
    read -r watch_code watch_result < "$QUEUE/watch.result" || { watch_code=1; watch_result="no result (log: ${OUT#"$ROOT"/}/watch.txt)"; }
    [ "$watch_code" = 2 ] || logs+=("$OUT/watch.txt")
fi
if [ -f "$QUEUE/release.code" ]; then
    logs+=("$OUT/release-build.txt")
    case "$(cat "$QUEUE/release.code")" in
        0) ;;
        5) unrun=1 busy=1 ;;
        *) note_failure release ;;
    esac
fi
# Each stage's log, its jobs in queue order, whichever device ran them; a job no device was left for
# says so.
total="$(wc -l < "$QUEUE/list")" taken="$(cat "$QUEUE/next")"
stage_log() {
    local stage="$1" file="$OUT/$2" n=0 s m
    echo "== $3" > "$file"
    [ "$stage" = languages ] && cat "$QUEUE/skipped" >> "$file"
    while read -r s m; do
        n=$((n + 1))
        [ "$s" = "$stage" ] || continue
        if [ -f "$QUEUE/$n.txt" ]; then
            cat "$QUEUE/$n.txt" >> "$file"
            [ "$(cat "$QUEUE/$n.code" 2>/dev/null)" = 0 ] || note_failure "$stage"
        elif [ $n -gt "$taken" ]; then
            echo "-- $s $m: not run, no device was left for it$([ $busy = 1 ] && echo " (a device was busy with another run)")" >> "$file"
            unrun=1
        fi
    done < "$QUEUE/list"
    logs+=("$file")
}
if [ $build_failed = 0 ] && [ ${#LANES[@]} -gt 0 ]; then
    [ $sweep = 1 ] && stage_log sweep sweep.txt "Thorough sweep of every module, Ink light and dark"
    [ $contrast = 1 ] && stage_log contrast contrast.txt "The screens in high contrast, light and dark"
    [ $languages = 1 ] && stage_log languages languages.txt "Every language at 2× text"
fi

if [ $store = 1 ]; then
    # Its script takes a phone and the tablet itself, both at once when both are there.
    logs+=("$OUT/store.txt")
    echo "== Store screenshots in every language ($(date +%H:%M))" | tee "$OUT/store.txt"
    scripts/store-screenshots.sh 2>&1 | tee -a "$OUT/store.txt"
    [ "${PIPESTATUS[0]}" = 0 ] || failed=1
fi

# Which device did what: "the Xiaomi 2112123AG (f813c98c), 12 jobs and the release build".
device_report() {
    local i out="" part jobs
    for i in "${!LANES[@]}"; do
        jobs="$(cat "$QUEUE/done-$i" 2>/dev/null | wc -l)"
        part="the ${NAMES[i]}"
        [ -f "$QUEUE/busy-$i" ] && part="$part (busy with another run at $(cut -d' ' -f1 "$QUEUE/busy-$i" | paste -sd, -), its jobs put back: $(head -1 "$QUEUE/busy-$i" | cut -d' ' -f2- | sed 's/, maybe from another session.*//'))"
        [ ${#LANES[@]} -gt 1 ] && part="$part, $jobs job$([ "$jobs" = 1 ] || echo s)"
        if [ "$i" = 0 ] && [ -f "$QUEUE/release.code" ]; then
            part="$part$([ ${#LANES[@]} -gt 1 ] && echo " and" || echo ",") the release build"
            [ "$(cat "$QUEUE/release.code")" = 5 ] && part="$part (not run: busy with another run)"
        fi
        [ -f "$QUEUE/dropped-$i" ] && part="$part (dropped off at $(paste -sd, "$QUEUE/dropped-$i"); its job went to the others)"
        out="${out:+$out; }$part"
    done
    echo "${out:-no phone or tablet connected}"
}

# The run's outcome (run_outcome, above) and the record for the release (the header's The record).
run_outcome
record_phones
[ $watch = 1 ] && record_line watch "${watch_code:-1}"

tested="at ${tested_commit:0:8}"
[ $clean = 1 ] || tested="$tested with uncommitted changes"
{
    echo "Full check: $(( (SECONDS - start) / 60 )) min $tested, $outcome"
    [ $languages = 1 ] && [ $build_failed = 0 ] && echo "Every language: $checked of $((checked + skipped)) module checks ran; $skipped passed earlier with nothing they show changed since"
    if [ $release_build = 1 ] || [ $sweep = 1 ] || [ $contrast = 1 ] || [ $languages = 1 ]; then
        echo "$([ ${#LANES[@]} -gt 1 ] && echo Devices || echo Phone): $(device_report)"
    fi
    [ $watch = 1 ] && echo "Watch: $watch_result"
    cat "${logs[@]}" 2>/dev/null | grep -E "FAIL|CRASH|TIMED OUT|no results|Build failed|screen went off|crashed or stopped|didn't install|is locked|not run|dropped off|No phone|is using the device" || true
} > "$OUT/summary.txt"
cat "$OUT/summary.txt"
exit $exit_code
