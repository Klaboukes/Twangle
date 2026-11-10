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
# (--watch, --release-build, --sweep, --contrast, --languages and --store may be combined.)
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
# nothing about the app either: not counted as a failure.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# adb, the devices and cooling (wait_cool, COOL_BELOW): shared with the other scripts that use a device.
. "$ROOT/scripts/devices.sh"
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

watch=0 release_build=0 sweep=0 contrast=0 languages=0 store=0 full=0 dry_run=0 picked=0
for arg in "$@"; do
    case "$arg" in
        --watch) watch=1 picked=1 ;;
        --release-build) release_build=1 picked=1 ;;
        --sweep) sweep=1 picked=1 ;;
        --contrast) contrast=1 picked=1 ;;
        --languages) languages=1 picked=1 ;;
        --store) store=1 picked=1 ;;
        --full) full=1 ;;
        --dry-run) dry_run=1 ;;
        *) sed -n '2,45p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
    esac
done
[ $picked = 1 ] || { watch=1; release_build=1; sweep=1; contrast=1; languages=1; }
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
    [ $watch = 1 ] && echo "Watch: ${WATCH:-none connected, so its stage is skipped}"
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
        echo "$code" > "$QUEUE/$n.code"
        echo "$n" >> "$QUEUE/done-$i"
    done
}

start=$SECONDS
status=0
# Only a clean working tree is a commit a pass can be noted at (as in scripts/ui-tests.sh).
tested_commit="$(git rev-parse HEAD)"
[ -z "$(git status --porcelain)" ] && clean=1 || clean=0
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
    if [ "${PIPESTATUS[0]}" != 0 ]; then
        build_failed=1
        status=1
        logs+=("$OUT/build.txt")
    fi
fi

# The watch app's tests, on the watch, beside the phones'.
watch_result="" watch_pid=""
if [ $watch = 1 ]; then
    echo "== The watch ($(date +%H:%M))" | tee "$OUT/watch.txt"
    if [ -z "$WATCH" ]; then
        watch_result="not connected, so its tests didn't run (wake it on its charger before the run)"
        echo "No watch connected: $watch_result" | tee -a "$OUT/watch.txt"
    elif [ $build_failed = 1 ]; then
        watch_result="not run: the build failed"
    else
        watch_name="$(device_name "$WATCH")"
        echo "On the $watch_name" | tee -a "$OUT/watch.txt"
        (
            ANDROID_SERIAL="$WATCH" MODULE_TIMEOUT=1200 scripts/ui-tests.sh :wear --built --thorough 2>&1 | tee -a "$OUT/watch.txt" | prefix watch
            # Its result: a code (0 passed, 1 failed, 2 dropped off, which says nothing) and the summary's words.
            if [ "${PIPESTATUS[0]}" = 0 ]; then
                echo "0 passed on the $watch_name" > "$QUEUE/watch.result"
            elif ready_devices | grep -qxF -- "$WATCH"; then
                echo "1 failures below, on the $watch_name" > "$QUEUE/watch.result"
            else
                echo "The watch dropped off during its run: its results say nothing" | tee -a "$OUT/watch.txt" | prefix watch
                echo "2 the $watch_name dropped off during its run, so its results say nothing (log: ${OUT#"$ROOT"/}/watch.txt)" > "$QUEUE/watch.result"
            fi
        ) &
        watch_pid=$!
    fi
fi

# The phones' jobs: every device a lane of its own, taking the next job from the one queue. Jobs a
# device that dropped off put back are taken by the devices still there, in another round if those
# had finished; a device that came back joins in. At most three rounds.
if [ $build_failed = 0 ] && { [ -s "$QUEUE/list" ] || [ $release_build = 1 ]; }; then
    if [ ${#LANES[@]} = 0 ]; then
        echo "No phone or tablet connected: the phones' stages didn't run" | tee "$OUT/no-device.txt"
        logs+=("$OUT/no-device.txt")
        status=1
    else
        round=1
        while :; do
            pids=()
            for i in "${!LANES[@]}"; do
                [ $round = 1 ] || ready_devices | grep -qxF -- "${LANES[i]}" || continue
                lane "$i" "$([ $round = 1 ] && echo 1 || echo 0)" &
                pids+=($!)
            done
            [ ${#pids[@]} -gt 0 ] && wait "${pids[@]}"
            [ ${#pids[@]} -gt 0 ] && [ "$(unclaimed)" -gt 0 ] && [ $round -lt 3 ] || break
            round=$((round + 1))
        done
    fi
fi
[ -n "$watch_pid" ] && wait "$watch_pid"

# The results. The watch's first.
if [ -n "$watch_pid" ]; then
    read -r watch_code watch_result < "$QUEUE/watch.result" || { watch_code=1; watch_result="no result (log: ${OUT#"$ROOT"/}/watch.txt)"; }
    [ "$watch_code" = 2 ] || logs+=("$OUT/watch.txt")
    [ "$watch_code" = 1 ] && status=1
fi
if [ -f "$QUEUE/release.code" ]; then
    logs+=("$OUT/release-build.txt")
    [ "$(cat "$QUEUE/release.code")" = 0 ] || status=1
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
            [ "$(cat "$QUEUE/$n.code" 2>/dev/null)" = 0 ] || status=1
        elif [ $n -gt "$taken" ]; then
            echo "-- $s $m: not run, no device was left for it" >> "$file"
            status=1
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
    [ "${PIPESTATUS[0]}" = 0 ] || status=1
fi

# Which device did what: "the Xiaomi 2112123AG (f813c98c), 12 jobs and the release build".
device_report() {
    local i out="" part jobs
    for i in "${!LANES[@]}"; do
        jobs="$(cat "$QUEUE/done-$i" 2>/dev/null | wc -l)"
        part="the ${NAMES[i]}"
        [ ${#LANES[@]} -gt 1 ] && part="$part, $jobs job$([ "$jobs" = 1 ] || echo s)"
        [ "$i" = 0 ] && [ -f "$QUEUE/release.code" ] && part="$part$([ ${#LANES[@]} -gt 1 ] && echo " and" || echo ",") the release build"
        [ -f "$QUEUE/dropped-$i" ] && part="$part (dropped off at $(paste -sd, "$QUEUE/dropped-$i"); its job went to the others)"
        out="${out:+$out; }$part"
    done
    echo "${out:-no phone or tablet connected}"
}

tested="at $(git rev-parse --short HEAD)"
[ $clean = 1 ] || tested="$tested with uncommitted changes"
{
    echo "Full check: $(( (SECONDS - start) / 60 )) min $tested, $([ $status = 0 ] && echo "everything passed" || echo "failures below")"
    [ $languages = 1 ] && echo "Every language: $checked of $((checked + skipped)) module checks ran; $skipped passed earlier with nothing they show changed since"
    if [ $release_build = 1 ] || [ $sweep = 1 ] || [ $contrast = 1 ] || [ $languages = 1 ]; then
        echo "$([ ${#LANES[@]} -gt 1 ] && echo Devices || echo Phone): $(device_report)"
    fi
    [ $watch = 1 ] && echo "Watch: $watch_result"
    cat "${logs[@]}" 2>/dev/null | grep -E "FAIL|CRASH|TIMED OUT|no results|Build failed|screen went off|crashed or stopped|didn't install|is locked|not run|dropped off|No phone" || true
} > "$OUT/summary.txt"
cat "$OUT/summary.txt"
