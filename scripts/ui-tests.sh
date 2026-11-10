#!/usr/bin/env bash
# Runs the on-device UI tests, only for the modules a change touches, and prints a short summary per
# module. See .claude/skills/ui-test/SKILL.md.
#
#   scripts/ui-tests.sh                          modules affected by this branch (vs main) + uncommitted work
#   scripts/ui-tests.sh --all                    every module with UI tests
#   scripts/ui-tests.sh --since-pass             every module changed since it last passed whole on
#                                                this PC (or never did): before a release
#   scripts/ui-tests.sh :feature:tuner           whole module(s)
#   scripts/ui-tests.sh :feature:tuner -f Tip    only tests whose Class#method name matches a regex
#   scripts/ui-tests.sh :feature:tuner -c TunerContentTest#switchingModesMovesNothingWithTheLargestFont
#   scripts/ui-tests.sh :wear                    the watch app's tests, on the watch (named on its own)
#   scripts/ui-tests.sh :feature:tuner -c StyleScreenshots -a styleScreenshots=true
#                                                another instrumentation argument (-a key=value, repeatable)
#   scripts/ui-tests.sh --thorough ...           also the double checks: the @Thorough tests (other
#                                                font sizes, languages, orientations)
#   scripts/ui-tests.sh --high-contrast ...      every screen in the high-contrast look (Phase 86), in its
#                                                light and dark where a test checks both: the full
#                                                check's third pass (-a highContrast=true, so no pass is noted)
#   scripts/ui-tests.sh --cool ...               one test class at a time, waiting while the phone is
#                                                warmer than COOL_BELOW (default 37 °C): long runs
#   scripts/ui-tests.sh --languages ...          also the @EveryLanguage tests, in the testers'
#                                                languages only (DAY_LANGUAGES, default en,nl from gradle.properties): daytime
#   scripts/ui-tests.sh --all-languages ...      the @EveryLanguage tests in every language Twangle
#                                                ships (long; the full check)
#   scripts/ui-tests.sh --dry-run ...            only say which modules would run, and why (no build,
#                                                no phone)
#   scripts/ui-tests.sh --no-build ...           use the APKs the last run built: for a script that runs
#                                                the same tests again and again (store-screenshots.sh)
#   scripts/ui-tests.sh --built ...              the same, for a caller that built every APK from this tree
#                                                itself just before (full-check.sh, one build for its devices):
#                                                a pass is noted, at UI_TESTS_COMMIT when the caller
#                                                hands it down with UI_TESTS_CLEAN (its tree's state)
#   scripts/ui-tests.sh --all :wear --build-only only build those modules' APKs (no device needed): the
#                                                full check's one build, before its devices run --built
#
# With more than one device connected the run picks its own (scripts/devices.sh): a phone (else the
# tablet) for the phone's modules, the watch for :wear; ANDROID_SERIAL names one instead.
#
# A normal run is quick and leaves out the double checks: tests marked @Thorough (:core:testing) are
# skipped. The full check
# (scripts/full-check.sh) passes --thorough and checks everything. --quick is accepted and
# changes nothing.
#
# A module that passes whole (no -f, -c or -a) from a clean working tree is noted as passed at that
# commit in build/ui-tests-passed/ (this PC only; the full check notes every
# module it passes).
# --since-pass reads those notes, so a release runs only what changed since the last good run.
#
# One Gradle call builds every test APK (and the app, for :app). Each module's tests are then
# installed and run straight on the phone with `am instrument`: Gradle's own test task costs 15-25 s
# more per module and uninstalls the app afterwards. A module's run is stopped after MODULE_TIMEOUT
# seconds (default 600); a single test is limited by its own timeout rule.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
# adb, the devices, the lock, waking and cooling: shared with the other scripts that use a device.
. "$ROOT/scripts/devices.sh"
MODULE_TIMEOUT="${MODULE_TIMEOUT:-600}"
# UI_TESTS_TAG=<name>: this run's logs and pulled files get "-<name>" in their names, so runs on two devices at once
# (scripts/store-screenshots.sh, a phone and a tablet) keep apart.
TAG="${UI_TESTS_TAG:+-$UI_TESTS_TAG}"
# The languages a daytime --languages run checks: twangle.dayLanguages in gradle.properties (en,nl),
# the ones written by day, as language tags (pt-rBR -> pt-BR, b+zh+Hant -> zh-Hant). The full check checks all.
DAY_LANGUAGES="${DAY_LANGUAGES:-$(sed -n 's/^twangle\.dayLanguages=//p' gradle.properties | sed 's/-r/-/g; s/b+//g; s/+/-/g')}"
BUILD_TIMEOUT="${BUILD_TIMEOUT:-600}"
UI_MODULES=(:core:testing :feature:tuner :feature:settings :feature:metronome :feature:practice :feature:eartraining :core:audio :app)

usage() { sed -n '2,38p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }

# Where this branch left main.
branch_base() {
    git merge-base HEAD origin/main 2>/dev/null || git merge-base HEAD main
}

# The UI test modules a change since commit [base] (by default where this branch left main) can
# reach, uncommitted work included: scripts/ui-test-scope.py follows what changed in the core modules
# to the modules that use it. Docs, CI files, the Play Store texts and JVM unit tests need no device
# run. With --why, each module comes with its reason. If the helper fails, every module.
affected_modules() {
    local base="${1:-}"
    [ $# -gt 0 ] && shift
    [ -n "$base" ] || base="$(branch_base)"
    { python -I scripts/ui-test-scope.py "$@" "$base" || {
        echo "Could not work out which modules the change reaches: taking every one." >&2
        printf '%s\n' "${UI_MODULES[@]}"
    }; } | tr -d '\r'
}

# A module's last whole pass on this PC: build/ui-tests-passed/feature-tuner holds "<commit> <date>".
# Kept in the main folder, so a pass in a session's own worktree counts for every checkout of this PC.
PASSED="$(cd "$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir)/.." && pwd)/build/ui-tests-passed"
pass_file() { local m="${1#:}"; echo "$PASSED/${m//:/-}"; }

# The UI test modules no pass vouches for: never passed whole on this PC, passed at a commit this
# clone no longer has, or changed since in a way the module can see.
unproven_modules() {
    local module commit
    local -A reached=()
    for module in "${UI_MODULES[@]}"; do
        commit="$(cut -d' ' -f1 "$(pass_file "$module")" 2>/dev/null)"
        if [ -n "$commit" ] && git cat-file -e "$commit^{commit}" 2>/dev/null; then
            # Modules passed at the same commit (the full check's) share one look at the change.
            [ -n "${reached[$commit]+set}" ] || reached[$commit]=" $(affected_modules "$commit" | tr '\n' ' ')"
        fi
        if [ -z "$commit" ] || ! git cat-file -e "$commit^{commit}" 2>/dev/null ||
            [[ "${reached[$commit]}" == *" $module "* ]]; then
            echo "$module"
        fi
    done
}

modules=() filter="" class="" extra=() thorough=0 cool=0 languages=0 since_pass=0 dry_run=0 no_build=0 built=0 build_only=0
while [ $# -gt 0 ]; do
    case "$1" in
        --all) modules=("${UI_MODULES[@]}") ;;
        --since-pass) since_pass=1 ;;
        --thorough) thorough=1 ;;
        --quick) thorough=0 ;;
        --high-contrast) extra+=("highContrast=true") ;;
        --cool) cool=1 ;;
        --languages) languages=1 ;;
        --all-languages) languages=2 ;;
        --dry-run) dry_run=1 ;;
        --no-build) no_build=1 ;;
        --built) no_build=1 built=1 ;;
        --build-only) build_only=1 ;;
        -f | --filter) filter="$2"; shift ;;
        -c | --class) class="$2"; shift ;;
        -a | --arg) extra+=("$2"); shift ;;
        -h | --help) usage ;;
        :*) modules+=("$1") ;;
        *) echo "Unknown argument: $1" >&2; usage ;;
    esac
    shift
done
[ -n "$filter" ] && [ -n "$class" ] && { echo "Use -f or -c, not both." >&2; exit 2; }
if [ $since_pass = 1 ]; then
    [ ${#modules[@]} -eq 0 ] && [ -z "$filter$class" ] || { echo "--since-pass picks the modules itself: no modules, -f or -c with it." >&2; exit 2; }
    mapfile -t modules < <(unproven_modules)
    for module in "${UI_MODULES[@]}"; do
        [[ " ${modules[*]} " == *" $module "* ]] ||
            echo "Passed, nothing changed since: $module ($(cut -d' ' -f2- "$(pass_file "$module")"), at $(cut -c1-8 "$(pass_file "$module")"))"
    done
    if [ ${#modules[@]} -eq 0 ]; then
        echo "Every module passed after its last change: nothing to run."
        exit 0
    fi
    echo "To run: ${modules[*]}"
elif [ ${#modules[@]} -eq 0 ] && [ $dry_run = 1 ]; then
    echo "Would run, for the change since $(git rev-parse --short "$(branch_base)"), where this left main (uncommitted work included):"
    affected_modules "" --why | grep . || echo "nothing (only docs, CI or unit tests changed, or nothing a UI test uses)"
    exit 0
elif [ ${#modules[@]} -eq 0 ]; then
    mapfile -t modules < <(affected_modules)
    if [ ${#modules[@]} -eq 0 ]; then
        echo "No UI tests affected (only docs, CI or unit tests changed, or nothing a UI test uses)."
        exit 0
    fi
    echo "Affected: ${modules[*]} (why: --dry-run)"
fi
if [ $dry_run = 1 ]; then
    echo "Would run: ${modules[*]}"
    exit 0
fi

# The device: ANDROID_SERIAL's, else a phone (or the tablet) for the phone's modules and the watch for
# :wear, which runs on its own. Say plainly that it is missing instead of failing after the build.
on_watch=0
if [[ " ${modules[*]} " == *" :wear "* ]]; then
    [ ${#modules[@]} = 1 ] || [ $build_only = 1 ] || { echo ":wear runs on the watch: name it on its own (scripts/ui-tests.sh :wear)." >&2; exit 2; }
    on_watch=1
fi
if [ $build_only = 0 ]; then
    if [ $on_watch = 1 ]; then use_device watch; else use_device phone tablet; fi
fi

# :feature:tuner -> feature/tuner
dir_of() { local d="${1#:}"; echo "${d//://}"; }

# The test APK's package: the app's id or the library's namespace, plus ".test".
test_package() {
    local file id
    file="$(dir_of "$1")/build.gradle.kts"
    id="$(sed -n 's/^ *applicationId = "\(.*\)"/\1/p' "$file" | head -1)"
    [ -z "$id" ] && id="$(sed -n 's/^ *namespace = "\(.*\)"/\1/p' "$file" | head -1)"
    echo "$id.test"
}

# A short class name gets its package from the module, e.g. :feature:tuner + TunerContentTest.
qualify() {
    local module="$1" name="$2" out="" part
    IFS=',' read -ra parts <<< "$name"
    for part in "${parts[@]}"; do
        if [[ "$part" != *.*#* && "$part" != *.*.* ]]; then
            local file
            file="$(find "$(dir_of "$module")" -path "*/src/androidTest/*" -name "${part%%#*}.kt" -print -quit 2>/dev/null)"
            if [ -n "$file" ]; then
                part="$(sed -n 's/^package \(.*\)$/\1/p' "$file" | head -1).$part"
            fi
        fi
        out="${out:+$out,}$part"
    done
    echo "$out"
}

# One argument for the phone's shell, in single quotes.
quote() { printf "'%s'" "${1//\'/\'\\\'\'}"; }

# Prints tests, failures, phone seconds and the failing tests from `am instrument -r` output.
summarize() {
    python -I - "$1" <<'EOF'
import re, sys
# Test names can hold note names (F♯); Windows would print them in its own code page and fail.
sys.stdout.reconfigure(encoding='utf-8')
text = open(sys.argv[1], encoding='utf-8', errors='replace').read().replace('\r', '')
tests = fails = skipped = 0
failed, status, key, running = [], {}, None, None
for line in text.split('\n'):
    if line.startswith('INSTRUMENTATION_STATUS: '):
        key, _, value = line[len('INSTRUMENTATION_STATUS: '):].partition('=')
        status[key] = value
    elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
        code = int(line.split(': ')[1])
        name = f"{status.get('class', '?').rsplit('.', 1)[-1]}#{status.get('test', '?')}"
        if code == 1:
            running = name
        elif code <= 0:  # a result; other codes are a test's own lines (PluckRecorder's prompts)
            running = None
            tests += 1
            if code in (-3, -4):  # @Ignore'd, or an assumption that did not hold
                skipped += 1
            elif code in (-1, -2):
                fails += 1
                message = status.get('stack', '').strip().split('\n')[0]
                failed.append(f"  FAIL {name}: {message[:200]}")
        status, key = {}, None
    elif line.startswith('INSTRUMENTATION_'):
        key = None
    elif key is not None:
        status[key] += '\n' + line
secs = ''
time = re.findall(r'^Time: ([\d.,]+)', text, re.M)
if time:
    # The last separator is the decimal one, whatever the phone's language.
    t = time[-1].rstrip('.,')
    dec = max(t.rfind('.'), t.rfind(','))
    t = re.sub(r'[.,]', '', t[:dec]) + '.' + t[dec + 1:] if dec >= 0 else t
    secs = f", {float(t):.0f}s on the phone"
if tests == 0 and running is None:
    # A class whose tests all wait for an argument runs none.
    if re.search(r'^OK \(0 tests\)', text, re.M):
        print('0 tests')
        sys.exit(0)
    sys.exit(1)
print(f"{tests} tests, {fails} failed{f', {skipped} skipped' if skipped else ''}{secs}")
if running is not None:
    crash = re.search(r'shortMsg=(.*)', text)
    print(f"  CRASH in {running}: {crash.group(1) if crash else 'the run stopped'}")
if failed:
    print('\n'.join(failed))
sys.exit(1 if fails or running is not None else 0)
EOF
}

# The tests are built from the working tree as it is now: only a clean one is a commit a pass can
# be noted at. With --built the caller built the APKs and may hand down the commit and clean state
# of the tree it built them from (UI_TESTS_COMMIT, UI_TESTS_CLEAN: the full check, Phase 90): looked
# at afresh here, one doc edited during its run stopped every later module from being noted.
if [ $built = 1 ] && [[ "${UI_TESTS_COMMIT:-}" =~ ^[0-9a-f]{40}$ && "${UI_TESTS_CLEAN:-}" =~ ^[01]$ ]]; then
    tested="$UI_TESTS_COMMIT" clean="$UI_TESTS_CLEAN"
else
    tested="$(git rev-parse HEAD)"
    [ -z "$(git status --porcelain)" ] && clean=1 || clean=0
fi

# Notes that [module] passed whole at this commit (pass_file above): never after --no-build, whose
# APKs may be older than the tree; --built vouches they are this tree's.
note_pass() {
    [ $clean = 1 ] && { [ $no_build = 0 ] || [ $built = 1 ]; } && [ -z "$filter$class" ] && [ ${#extra[@]} -eq 0 ] || return 0
    mkdir -p "$PASSED"
    echo "$tested $(date '+%Y-%m-%d %H:%M')" > "$(pass_file "$1")"
}

# Build everything first, in one Gradle call.
mkdir -p "$ROOT/build"
tasks=()
for module in "${modules[@]}"; do tasks+=("$module:assembleDebugAndroidTest"); done
[[ " ${modules[*]} " == *" :app "* ]] && tasks+=(":app:assembleDebug")
[ $on_watch = 1 ] && tasks+=(":wear:assembleDebug")
start=$SECONDS
# --no-build: a script running the same tests over and over (one language at a time) built them on
# its first call; Gradle's up-to-date check alone took 22 s a call (2026-11-06). Noted as a pass only
# with --built (note_pass).
if [ $no_build = 1 ]; then
    :
elif ! timeout "$BUILD_TIMEOUT" ./gradlew "${tasks[@]}" --console=plain > "$ROOT/build/ui-tests-build.log" 2>&1; then
    echo "Build failed (log: build/ui-tests-build.log)"
    grep -E "What went wrong|^e: |error:" -A3 "$ROOT/build/ui-tests-build.log" | head -15
    exit 1
fi
[ $no_build = 1 ] || printf '%-18s %3ds\n' "build" $((SECONDS - start))
[ $build_only = 1 ] && exit 0

# One run at a time on the phone (take_phone_lock, scripts/devices.sh: the lock, its beat, the takeover
# of a run that was cut off, and at the end this script's put_back and the lock's removal).
take_phone_lock

# The tests expect an upright phone. With auto-rotate on and the phone lying on its side, the screen
# turns to landscape mid-run: the tuner hides its navigation bar there and the settings list is
# shorter, so checks that pass upright fail. Held upright for the run, the owner's rotation settings
# put back after (hold_upright).
hold_upright
# A watch's screen sleeps after 15 s (the owner's setting), and then its tests find "No compose
# hierarchies" (docs/devices.md, *Watch*): kept on for the run, the owner's time-out put back after.
SCREEN_OFF=""
if [ $on_watch = 1 ]; then
    SCREEN_OFF="$(adb shell settings get system screen_off_timeout 2>/dev/null | tr -d '\r')"
    adb shell "echo $SCREEN_OFF > $LOCK/screen_off && settings put system screen_off_timeout 1800000" > /dev/null 2>&1
fi
restore_screen_off() {
    [[ "$SCREEN_OFF" =~ ^[0-9]+$ ]] && adb shell settings put system screen_off_timeout "$SCREEN_OFF" > /dev/null 2>&1
}
# Run at the end by the lock's EXIT trap, before the lock goes.
put_back() { restore_rotation; restore_screen_off; stop_test_processes; }

# MIUI (the Xiaomi test phone) refuses to let a test open the app's screen ("MIUILOG- Permission
# Denied Activity", "Abort background activity starts") unless the package has Xiaomi's own
# "start in background" app-op, MIUIOP 10021. Every install resets it, so a loop on the phone keeps
# setting it during the whole run. Needs "USB debugging (Security settings)" on (see CLAUDE.md).
# The loop runs only while this run holds the lock, and at most LOCK_MAX_AGE seconds: one left running
# after its run costs the phone about a third of a core, and they used to pile up, one a run, until
# the tests ran out of time (the first test of a --thorough run first).
if [ -n "$(adb shell getprop ro.miui.ui.version.name 2>/dev/null | tr -d '\r')" ]; then
    guard='echo $$ > LOCK/guard.pid; end=$(($(date +%s) + MAX)); while [ "$(cat LOCK/owner 2>/dev/null)" = TOKEN ] && [ $(date +%s) -lt $end ]; do for p in $(pm list packages io.github.klaboukes.twangle | cut -d: -f2); do appops set $p 10021 allow; done; sleep 0.2; done'
    guard="${guard//LOCK/$LOCK}"
    guard="${guard//TOKEN/$LOCK_TOKEN}"
    adb shell "${guard//MAX/$LOCK_MAX_AGE}" > /dev/null 2>&1 &
fi

# Milliseconds since the phone's screen last went off, by its power button or a time-out.
ms_since_screen_off() {
    adb shell dumpsys power 2>/dev/null | tr -d '\r' | sed -n 's/.*mLastSleepTime=[0-9-]* (\([0-9]*\) ms ago).*/\1/p' | head -1
}

# Whether [package] on the device is this very APK (relative path), byte for byte: then installing it
# again changes nothing. A run per language (scripts/store-screenshots.sh) installed the same two APKs
# 36 times, and on the tablet each install brought Play Protect's prompt up (2026-11-09).
installed_already() {
    local path there here
    path="$(adb shell pm path "$2" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p')"
    # Not installed, or in parts (ours are single APKs).
    [ -n "$path" ] && [ "$(wc -l <<< "$path")" -eq 1 ] || return 1
    there="$(adb shell sha256sum "$path" 2>/dev/null | tr -d '\r' | cut -d' ' -f1)"
    here="$(sha256sum "$1" | cut -d' ' -f1)"
    [ -n "$there" ] && [ "$there" = "$here" ]
}

# Installs an APK (relative path) as [package], unless the device has that very APK already; prints
# why not on failure. A package signed by the owner's other machine (INSTALL_FAILED_UPDATE_INCOMPATIBLE)
# is uninstalled and installed again, the owner's rule: on 2026-10-26 it failed every install of the
# overnight run.
install_apk() {
    local out package
    installed_already "$1" "$2" && return 0
    if out="$(adb install -r -t -g "$1" 2>&1)"; then
        dismiss_play_protect "$2"
        return 0
    fi
    package="$(echo "$out" | tr -d '\r' | sed -n 's/.*INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package \([^ ]*\) signatures.*/\1/p' | head -1)"
    if [ -n "$package" ]; then
        echo "$package was signed on the other machine: uninstalling it" >&2
        adb uninstall "$package" > /dev/null 2>&1
        out="$(adb install -r -t -g "$1" 2>&1)" && { dismiss_play_protect "$2"; return 0; }
    fi
    echo "could not install $1: $(echo "$out" | tr -d '\r' | grep -E 'Failure|INSTALL_|error' | head -1)"
    return 1
}

# Runs one `am instrument` call (arguments after the first four) and prints its time and summary.
run_tests() {
    local module="$1" package="$2" runner="$3" log="$4" start=$SECONDS code summary
    shift 4
    timeout "$MODULE_TIMEOUT" env MSYS_NO_PATHCONV=1 "$ADB_EXE" shell am instrument -w -r "$@" "$runner" > "$log" 2>&1
    code=$?
    printf '%3ds  ' $((SECONDS - start))
    if [ $code = 124 ]; then
        adb shell am force-stop "$package" > /dev/null 2>&1
        echo "TIMED OUT after ${MODULE_TIMEOUT}s (log: $log)"
        return 1
    fi
    summary="$(summarize "$log")"
    code=$?
    if [ -n "$summary" ]; then
        echo "$summary"
    else
        echo "no results (log: $log)"
        if [ $thorough = 0 ] && [ -n "$filter$class" ]; then
            echo "  (a test marked @Thorough only runs with --thorough)"
        fi
        tail -5 "$log"
    fi
    return $code
}

# The module's test classes (files with a @Test), by short name.
test_classes() {
    grep -rl --include='*.kt' '@Test' "$(dir_of "$1")/src/androidTest" 2>/dev/null | sed 's|.*/||; s|\.kt$||' | sort
}

status=0
for module in "${modules[@]}"; do
    start=$SECONDS
    failed=0
    dir="$(dir_of "$module")"
    log="build/ui-tests${module//:/-}$TAG.log"
    package="$(test_package "$module")"
    printf '%-18s ' "$module"
    apk="$(find "$dir/build/outputs/apk/androidTest/debug" -name '*.apk' -print -quit 2>/dev/null)"
    if [ -z "$apk" ]; then
        echo "no test APK in $dir/build/outputs/apk/androidTest/debug"
        status=1
        continue
    fi
    if [ "$module" = ":app" ] || [ "$module" = ":wear" ]; then
        install_apk "$(find "$dir/build/outputs/apk/debug" -name '*.apk' -print -quit)" "${package%.test}" || { status=1; continue; }
    fi
    install_apk "$apk" "$package" || { status=1; continue; }
    wake_phone
    runner="$(adb shell pm list instrumentation | tr -d '\r' | sed -n "s|^instrumentation:\($package/[^ ]*\) .*|\1|p" | head -1)"

    # Extra files a test writes (StyleScreenshots' pictures, a language check's failing screen) go
    # where Gradle's own run would put them, so scripts that read them work either way: the media
    # folder of the package the tests run in (:app's tests run in the app itself).
    target="$(adb shell pm list instrumentation | tr -d '\r' | sed -n "s|^instrumentation:$package/.*(target=\(.*\))|\1|p" | head -1)"
    outdir="/sdcard/Android/media/${target:-$package}/additional_test_output"
    adb shell "rm -rf $outdir && mkdir -p $outdir" > /dev/null 2>&1
    args=(-e additionalTestOutputDir "$outdir")
    [ -n "$filter" ] && args+=(-e tests_regex "$(quote ".*($filter).*")")
    [ -n "$class" ] && args+=(-e class "$(qualify "$module" "$class")")
    # The annotations in :core:testing a run leaves out (a comma-separated list for the runner).
    skip=()
    if [ $thorough = 1 ]; then args+=(-e thorough true); else skip+=(io.github.klaboukes.twangle.core.testing.Thorough); fi
    if [ $languages = 0 ]; then skip+=(io.github.klaboukes.twangle.core.testing.EveryLanguage); else args+=(-e everyLanguage true); fi
    [ $languages = 1 ] && args+=(-e languages "$DAY_LANGUAGES")
    if [ ${#skip[@]} -gt 0 ] && grep -q '":core:testing"' "$dir/build.gradle.kts"; then
        args+=(-e notAnnotation "$(IFS=,; echo "${skip[*]}")")
    fi
    for pair in "${extra[@]}"; do args+=(-e "${pair%%=*}" "$(quote "${pair#*=}")"); done

    if [ $cool = 1 ] && [ -z "$filter$class" ]; then
        # One class at a time, so the phone can cool down between them (it waits only when warm).
        echo
        for name in $(test_classes "$module"); do
            wait_cool
            printf '  %-28s ' "$name"
            run_tests "$module" "$package" "$runner" "build/ui-tests${module//:/-}-$name$TAG.log" \
                "${args[@]}" -e class "$(qualify "$module" "$name")" || failed=1
        done
    else
        run_tests "$module" "$package" "$runner" "$log" "${args[@]}" || failed=1
    fi
    off="$(ms_since_screen_off)"
    if [ -n "$off" ] && [ "$off" -lt $(((SECONDS - start) * 1000)) ]; then
        echo "  The phone's screen went off during this run, so its failures say nothing: leave the screen on and run again."
        failed=1
    fi
    if [ $failed = 0 ]; then note_pass "$module"; else status=1; fi
    if [ -n "$(adb shell ls "$outdir" 2>/dev/null | tr -d '\r')" ]; then
        pulled="$dir/build/outputs/connected_android_test_additional_output$TAG"
        rm -rf "$pulled" && mkdir -p "$pulled"
        adb pull "$outdir/." "$pulled" > /dev/null 2>&1
    fi
done
exit $status
