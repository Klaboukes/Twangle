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
#
# While the look of the app is still changing, a normal run leaves out the double checks: the style
# tests run in the default style only, and tests marked @Thorough (:core:testing) are skipped.
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
BUILD_TIMEOUT="${BUILD_TIMEOUT:-600}"
UI_MODULES=(:feature:tuner :feature:settings :core:audio :app)

usage() { sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }

# Git Bash turns arguments that look like paths ("/sdcard/...") into Windows paths for adb.exe; the
# phone's paths must pass unchanged. Local files are therefore always given relative to the repo.
adb() { MSYS_NO_PATHCONV=1 "$ADB_EXE" "$@"; }

# Maps changed paths to the UI test modules that can see the change (a module's own sources, or
# anything it depends on). Docs, CI files and JVM unit tests need no device run.
affected_modules() {
    local base changed
    base="$(git merge-base HEAD origin/main 2>/dev/null || git merge-base HEAD main)"
    changed="$( { git diff --name-only "$base"; git ls-files --others --exclude-standard; } | sort -u)"
    local tuner=0 settings=0 audio=0 app=0 path
    while IFS= read -r path; do
        [ -z "$path" ] && continue
        case "$path" in
            *.md | .github/* | .claude/* | scripts/* | docs/* | */src/test/* | core/dsp/src/benchmark/*) ;;
            feature/tuner/src/androidTest/*) tuner=1 ;;
            feature/settings/src/androidTest/*) settings=1 ;;
            core/audio/src/androidTest/*) audio=1 ;;
            app/src/androidTest/*) app=1 ;;
            feature/tuner/*) tuner=1; app=1 ;;
            feature/settings/*) settings=1; app=1 ;;
            core/audio/*) audio=1; tuner=1; app=1 ;;
            core/dsp/* | core/settings/* | core/designsystem/*) tuner=1; settings=1; app=1 ;;
            core/testing/*) tuner=1; settings=1 ;;
            app/*) app=1 ;;
            baselineprofile/*) ;;
            *) tuner=1; settings=1; audio=1; app=1 ;; # build files, version catalog, Gradle wrapper
        esac
    done <<< "$changed"
    [ $tuner = 1 ] && echo :feature:tuner
    [ $settings = 1 ] && echo :feature:settings
    [ $audio = 1 ] && echo :core:audio
    [ $app = 1 ] && echo :app
}

modules=() filter="" class="" extra=() thorough=0
while [ $# -gt 0 ]; do
    case "$1" in
        --all) modules=("${UI_MODULES[@]}") ;;
        --thorough) thorough=1 ;;
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

# MIUI (the Xiaomi test phone) refuses to let a test open the app's screen ("MIUILOG- Permission
# Denied Activity", "Abort background activity starts") unless the package has Xiaomi's own
# "start in background" app-op, MIUIOP 10021. Every install resets it, so a loop on the phone keeps
# setting it during the whole run. Needs "USB debugging (Security settings)" on (see CLAUDE.md).
miui_guard=""
if [ -n "$(adb shell getprop ro.miui.ui.version.name 2>/dev/null | tr -d '\r')" ]; then
    adb shell 'while true; do for p in $(pm list packages io.github.klaboukes.twangle | cut -d: -f2); do appops set $p 10021 allow; done; sleep 0.2; done' > /dev/null 2>&1 &
    miui_guard=$!
    trap '[ -n "$miui_guard" ] && kill $miui_guard 2>/dev/null' EXIT
fi

# Installs an APK (relative path); prints why not on failure.
install_apk() {
    local out
    out="$(adb install -r -t -g "$1" 2>&1)" && return 0
    echo "could not install $1: $(echo "$out" | tr -d '\r' | grep -E 'Failure|INSTALL_|error' | head -1)"
    return 1
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
    runner="$(adb shell pm list instrumentation | tr -d '\r' | sed -n "s|^instrumentation:\($package/[^ ]*\) .*|\1|p" | head -1)"

    # Extra files a test writes (MeterLookScreenshots' pictures) go where Gradle's own run would
    # put them, so scripts that read them work either way.
    outdir="/sdcard/Android/media/$package/additional_test_output"
    adb shell "rm -rf $outdir && mkdir -p $outdir" > /dev/null 2>&1
    args=(-e additionalTestOutputDir "$outdir")
    [ -n "$filter" ] && args+=(-e tests_regex "$(quote ".*($filter).*")")
    [ -n "$class" ] && args+=(-e class "$(qualify "$module" "$class")")
    if [ $thorough = 1 ]; then
        args+=(-e thorough true)
    elif grep -q '":core:testing"' "$dir/build.gradle.kts"; then
        args+=(-e notAnnotation io.github.klaboukes.twangle.core.testing.Thorough)
    fi
    for pair in "${extra[@]}"; do args+=(-e "${pair%%=*}" "$(quote "${pair#*=}")"); done

    timeout "$MODULE_TIMEOUT" env MSYS_NO_PATHCONV=1 "$ADB_EXE" shell am instrument -w -r "${args[@]}" "$runner" > "$log" 2>&1
    code=$?
    printf '%3ds  ' $((SECONDS - start))
    if [ $code = 124 ]; then
        adb shell am force-stop "$package" > /dev/null 2>&1
        echo "TIMED OUT after ${MODULE_TIMEOUT}s (log: $log)"
        status=1
    else
        summary="$(summarize "$log")" || status=1
        if [ -n "$summary" ]; then
            echo "$summary"
        else
            echo "no results (log: $log)"
            if [ $thorough = 0 ] && [ -n "$filter$class" ]; then
                echo "  (a test marked @Thorough only runs with --thorough)"
            fi
            tail -5 "$log"
        fi
    fi
    if [ -n "$(adb shell ls "$outdir" 2>/dev/null | tr -d '\r')" ]; then
        pulled="$dir/build/outputs/connected_android_test_additional_output"
        rm -rf "$pulled" && mkdir -p "$pulled"
        adb pull "$outdir/." "$pulled" > /dev/null 2>&1
    fi
done
exit $status
