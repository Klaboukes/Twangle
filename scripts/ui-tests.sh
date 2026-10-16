#!/usr/bin/env bash
# Runs the on-device UI tests, only for the modules a change touches, and prints a short summary per
# module. See .claude/skills/ui-test/SKILL.md.
#
#   scripts/ui-tests.sh                          modules affected by this branch (vs main) + uncommitted work
#   scripts/ui-tests.sh --all                    every module with UI tests
#   scripts/ui-tests.sh :feature:tuner           whole module(s)
#   scripts/ui-tests.sh :feature:tuner -f Tip    only tests whose Class#method name matches a regex
#   scripts/ui-tests.sh :feature:tuner -c TunerContentTest#switchingModesMovesNothingWithTheLargestFont
#   scripts/ui-tests.sh :feature:tuner -c MeterLookScreenshots -a meterScreenshots=true
#                                                another instrumentation argument (-a key=value, repeatable)
#   scripts/ui-tests.sh --thorough ...           also the double checks: every style, and the @Thorough
#                                                tests (other font sizes, languages, orientations)
#   scripts/ui-tests.sh --cool ...               one test class at a time, waiting while the phone is
#                                                warmer than COOL_BELOW (default 37 °C): long runs
#   scripts/ui-tests.sh --languages ...          also the @EveryLanguage tests: screens in every
#                                                language Twangle ships (long; the overnight run)
#
# A normal run is quick and leaves out the double checks: the style tests run in the default style
# only, and tests marked @Thorough (:core:testing) are skipped. The overnight run
# (scripts/overnight-ui-tests.sh) passes --thorough and checks everything. --quick is accepted and
# changes nothing.
#
# One Gradle call builds every test APK (and the app, for :app). Each module's tests are then
# installed and run straight on the phone with `am instrument`: Gradle's own test task costs 15-25 s
# more per module and uninstalls the app afterwards. A module's run is stopped after MODULE_TIMEOUT
# seconds (default 600); a single test is limited by its own timeout rule.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
ADB_EXE="${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"
MODULE_TIMEOUT="${MODULE_TIMEOUT:-600}"
COOL_BELOW="${COOL_BELOW:-37}"
BUILD_TIMEOUT="${BUILD_TIMEOUT:-600}"
UI_MODULES=(:feature:tuner :feature:settings :feature:metronome :feature:practice :core:audio :app)

usage() { sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }

# Git Bash turns arguments that look like paths ("/sdcard/...") into Windows paths for adb.exe; the
# phone's paths must pass unchanged. Local files are therefore always given relative to the repo.
adb() { MSYS_NO_PATHCONV=1 "$ADB_EXE" "$@"; }

# Maps changed paths to the UI test modules that can see the change (a module's own sources, or
# anything it depends on). Docs, CI files and JVM unit tests need no device run.
affected_modules() {
    local base changed
    base="$(git merge-base HEAD origin/main 2>/dev/null || git merge-base HEAD main)"
    changed="$( { git diff --name-only "$base"; git ls-files --others --exclude-standard; } | sort -u)"
    local tuner=0 settings=0 metronome=0 practice=0 audio=0 app=0 path
    while IFS= read -r path; do
        [ -z "$path" ] && continue
        case "$path" in
            *.md | .github/* | .claude/* | scripts/* | docs/* | */src/test/* | core/dsp/src/benchmark/*) ;;
            feature/tuner/src/androidTest/*) tuner=1 ;;
            feature/settings/src/androidTest/*) settings=1 ;;
            feature/metronome/src/androidTest/*) metronome=1 ;;
            feature/practice/src/androidTest/*) practice=1 ;;
            core/audio/src/androidTest/*) audio=1 ;;
            app/src/androidTest/*) app=1 ;;
            feature/tuner/*) tuner=1; app=1 ;;
            feature/settings/*) settings=1; app=1 ;;
            feature/metronome/*) metronome=1; app=1 ;;
            feature/practice/*) practice=1; app=1 ;;
            core/audio/*) audio=1; tuner=1; metronome=1; app=1 ;;
            core/dsp/* | core/settings/* | core/designsystem/*) tuner=1; settings=1; metronome=1; practice=1; app=1 ;;
            core/testing/*) tuner=1; settings=1; metronome=1; practice=1 ;;
            app/*) app=1 ;;
            baselineprofile/*) ;;
            wear/*) ;; # the watch app: checked on a Wear OS emulator, not on the phone
            *) tuner=1; settings=1; metronome=1; practice=1; audio=1; app=1 ;; # build files, version catalog, Gradle wrapper
        esac
    done <<< "$changed"
    [ $tuner = 1 ] && echo :feature:tuner
    [ $settings = 1 ] && echo :feature:settings
    [ $metronome = 1 ] && echo :feature:metronome
    [ $practice = 1 ] && echo :feature:practice
    [ $audio = 1 ] && echo :core:audio
    [ $app = 1 ] && echo :app
}

modules=() filter="" class="" extra=() thorough=0 cool=0 languages=0
while [ $# -gt 0 ]; do
    case "$1" in
        --all) modules=("${UI_MODULES[@]}") ;;
        --thorough) thorough=1 ;;
        --quick) thorough=0 ;;
        --cool) cool=1 ;;
        --languages) languages=1 ;;
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
if [ ${#modules[@]} -eq 0 ]; then
    mapfile -t modules < <(affected_modules)
    if [ ${#modules[@]} -eq 0 ]; then
        echo "No UI tests affected (only docs, CI or unit tests changed)."
        exit 0
    fi
    echo "Affected: ${modules[*]}"
fi

# Say plainly that the phone is missing instead of failing after the build.
if ! adb devices 2>/dev/null | tr -d '\r' | grep -q $'\tdevice$'; then
    echo "No phone connected (adb devices shows none). Connect the Xiaomi 12X and accept the USB-debugging prompt." >&2
    exit 3
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
    python - "$1" <<'EOF'
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
        else:
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
    # A class whose tests all wait for an argument (MeterLookScreenshots) runs none.
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

# Build everything first, in one Gradle call.
mkdir -p "$ROOT/build"
tasks=()
for module in "${modules[@]}"; do tasks+=("$module:assembleDebugAndroidTest"); done
[[ " ${modules[*]} " == *" :app "* ]] && tasks+=(":app:assembleDebug")
start=$SECONDS
if ! timeout "$BUILD_TIMEOUT" ./gradlew "${tasks[@]}" --console=plain > "$ROOT/build/ui-tests-build.log" 2>&1; then
    echo "Build failed (log: build/ui-tests-build.log)"
    grep -E "What went wrong|^e: |error:" -A3 "$ROOT/build/ui-tests-build.log" | head -15
    exit 1
fi
printf '%-18s %3ds\n' "build" $((SECONDS - start))

# One run at a time on the phone. Two runs (say, from two Claude sessions) break each other: an
# install stops the other run's tests ("Process crashed"), and their windows push each other into the
# background. The lock is a folder on the phone, made in one step; a lock older than LOCK_MAX_AGE
# seconds is left over from a run that was cut off, and is taken over.
LOCK=/data/local/tmp/twangle-ui-tests.lock
LOCK_MAX_AGE=3600
take_lock() {
    local age
    adb shell "mkdir $LOCK" > /dev/null 2>&1 && return 0
    age="$(adb shell "echo \$((\$(date +%s) - \$(stat -c %Y $LOCK)))" 2>/dev/null | tr -d '\r')"
    if [[ "$age" =~ ^[0-9]+$ ]] && [ "$age" -lt $LOCK_MAX_AGE ]; then
        echo "Another UI test run is using the phone (for ${age}s now), maybe from another session. Wait for it to finish and run again." >&2
        exit 5
    fi
    adb shell "rm -rf $LOCK && mkdir $LOCK" > /dev/null 2>&1
}
take_lock

# The tests expect an upright phone. With auto-rotate on and the phone lying on its side, the screen
# turns to landscape mid-run: the tuner hides its navigation bar there and the settings list is
# shorter, so checks that pass upright fail. Hold the screen upright for the run, then put the
# owner's rotation settings back.
ROTATION_AUTO="$(adb shell settings get system accelerometer_rotation 2>/dev/null | tr -d '\r')"
ROTATION_USER="$(adb shell settings get system user_rotation 2>/dev/null | tr -d '\r')"
adb shell "settings put system accelerometer_rotation 0 && settings put system user_rotation 0" > /dev/null 2>&1
restore_rotation() {
    [[ "$ROTATION_USER" =~ ^[0-9]+$ ]] && adb shell settings put system user_rotation "$ROTATION_USER" > /dev/null 2>&1
    [[ "$ROTATION_AUTO" =~ ^[0-9]+$ ]] && adb shell settings put system accelerometer_rotation "$ROTATION_AUTO" > /dev/null 2>&1
}
trap 'restore_rotation; adb shell "rm -rf $LOCK" > /dev/null 2>&1' EXIT
# Ctrl+C or `timeout` stop the script through its EXIT trap too, so the lock and the loop below go.
trap 'exit 130' INT TERM

# MIUI (the Xiaomi test phone) refuses to let a test open the app's screen ("MIUILOG- Permission
# Denied Activity", "Abort background activity starts") unless the package has Xiaomi's own
# "start in background" app-op, MIUIOP 10021. Every install resets it, so a loop on the phone keeps
# setting it during the whole run. Needs "USB debugging (Security settings)" on (see CLAUDE.md).
# The loop runs only while the lock is there, and at most LOCK_MAX_AGE seconds: one left running
# after its run costs the phone about a third of a core, and they used to pile up, one a run, until
# the tests ran out of time (the first test of a --thorough run, an Ancient one, first).
if [ -n "$(adb shell getprop ro.miui.ui.version.name 2>/dev/null | tr -d '\r')" ]; then
    guard='end=$(($(date +%s) + MAX)); while [ -d LOCK ] && [ $(date +%s) -lt $end ]; do for p in $(pm list packages io.github.klaboukes.twangle | cut -d: -f2); do appops set $p 10021 allow; done; sleep 0.2; done'
    guard="${guard//LOCK/$LOCK}"
    adb shell "${guard//MAX/$LOCK_MAX_AGE}" > /dev/null 2>&1 &
fi

# Tests can only draw while the phone's screen is on and unlocked. With the screen off (its power
# button) or the lock screen up, the test window is stopped: the first test of the run, the only one
# that opens it, waits for it until its time is up ("test timed out" in waitForComposeRoots) and the
# rest find "No compose hierarchies". In a --thorough run that first test is always an Ancient one
# (the first style), so it looks as if Ancient were slow. So wake the screen before each module, stop
# if the phone is locked, and say so if the screen went off during a run.
wake_phone() {
    adb shell input keyevent KEYCODE_WAKEUP > /dev/null 2>&1
    sleep 0.5
    if adb shell dumpsys window 2>/dev/null | tr -d '\r' | grep -q 'isKeyguardShowing=true'; then
        echo "The phone is locked: unlock the Xiaomi 12X, leave its screen on, and run again." >&2
        exit 4
    fi
}

# Milliseconds since the phone's screen last went off, by its power button or a time-out.
ms_since_screen_off() {
    adb shell dumpsys power 2>/dev/null | tr -d '\r' | sed -n 's/.*mLastSleepTime=[0-9-]* (\([0-9]*\) ms ago).*/\1/p' | head -1
}

# Installs an APK (relative path); prints why not on failure.
install_apk() {
    local out
    out="$(adb install -r -t -g "$1" 2>&1)" && return 0
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

# The battery's temperature in whole degrees (the phone reports tenths).
phone_temp() {
    local tenths
    tenths="$(adb shell dumpsys battery 2>/dev/null | tr -d '\r' | sed -n 's/^ *temperature: \([0-9]*\)/\1/p' | head -1)"
    echo $(( ${tenths:-0} / 10 ))
}

# Waits, at most 30 minutes, while the phone is warmer than COOL_BELOW. The screen stays on: turned
# off it could lock, and a locked phone stops the run.
wait_cool() {
    local t waited=0
    t="$(phone_temp)"
    [ "$t" -lt "$COOL_BELOW" ] && return 0
    printf '  (phone at %s °C, cooling' "$t"
    while [ "$t" -ge "$COOL_BELOW" ] && [ $waited -lt 1800 ]; do
        sleep 30; waited=$((waited + 30)); t="$(phone_temp)"
    done
    printf ' %ds to %s °C)\n' $waited "$t"
    wake_phone
}

status=0
for module in "${modules[@]}"; do
    start=$SECONDS
    dir="$(dir_of "$module")"
    log="build/ui-tests${module//:/-}.log"
    package="$(test_package "$module")"
    printf '%-18s ' "$module"
    apk="$(find "$dir/build/outputs/apk/androidTest/debug" -name '*.apk' -print -quit 2>/dev/null)"
    if [ -z "$apk" ]; then
        echo "no test APK in $dir/build/outputs/apk/androidTest/debug"
        status=1
        continue
    fi
    if [ "$module" = ":app" ]; then
        install_apk "$(find app/build/outputs/apk/debug -name '*.apk' -print -quit)" || { status=1; continue; }
    fi
    install_apk "$apk" || { status=1; continue; }
    wake_phone
    runner="$(adb shell pm list instrumentation | tr -d '\r' | sed -n "s|^instrumentation:\($package/[^ ]*\) .*|\1|p" | head -1)"

    # Extra files a test writes (MeterLookScreenshots' pictures, a language check's failing screen) go
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
    if [ $languages = 1 ]; then args+=(-e everyLanguage true); else skip+=(io.github.klaboukes.twangle.core.testing.EveryLanguage); fi
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
            run_tests "$module" "$package" "$runner" "build/ui-tests${module//:/-}-$name.log" \
                "${args[@]}" -e class "$(qualify "$module" "$name")" || status=1
        done
    else
        run_tests "$module" "$package" "$runner" "$log" "${args[@]}" || status=1
    fi
    off="$(ms_since_screen_off)"
    if [ -n "$off" ] && [ "$off" -lt $(((SECONDS - start) * 1000)) ]; then
        echo "  The phone's screen went off during this run, so its failures say nothing: leave the screen on and run again."
        status=1
    fi
    if [ -n "$(adb shell ls "$outdir" 2>/dev/null | tr -d '\r')" ]; then
        pulled="$dir/build/outputs/connected_android_test_additional_output"
        rm -rf "$pulled" && mkdir -p "$pulled"
        adb pull "$outdir/." "$pulled" > /dev/null 2>&1
    fi
done
exit $status
