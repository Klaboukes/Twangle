#!/usr/bin/env bash
# Runs the on-device UI tests (connectedDebugAndroidTest), only for the modules a change touches,
# and prints a short summary per module. See .claude/skills/ui-test/SKILL.md.
#
#   scripts/ui-tests.sh                          modules affected by this branch (vs main) + uncommitted work
#   scripts/ui-tests.sh --all                    every module with UI tests
#   scripts/ui-tests.sh :feature:tuner           whole module(s)
#   scripts/ui-tests.sh :feature:tuner -f Tip    only tests whose Class#method name matches a regex
#   scripts/ui-tests.sh :feature:tuner -c TunerContentTest#switchingModesMovesNothingWithTheLargestFont
#   scripts/ui-tests.sh :feature:tuner -c MeterLookScreenshots -a meterScreenshots=true
#                                                another instrumentation argument (-a key=value, repeatable)
#
# Filters (-f/-c) apply per module, so each module runs in its own Gradle call. Each module call is
# killed after MODULE_TIMEOUT seconds (default 720); a single test is limited by its own timeout rule.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
ADB="${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"
MODULE_TIMEOUT="${MODULE_TIMEOUT:-720}"
UI_MODULES=(:feature:tuner :feature:settings :core:audio :app)

usage() { sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }

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

modules=() filter="" class="" extra=()
while [ $# -gt 0 ]; do
    case "$1" in
        --all) modules=("${UI_MODULES[@]}") ;;
        -f | --filter) filter="$2"; shift ;;
        -c | --class) class="$2"; shift ;;
        -a | --arg) extra+=("-Pandroid.testInstrumentationRunnerArguments.$2"); shift ;;
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

# Gradle needs a device; say so plainly instead of failing after the build.
if ! "$ADB" devices 2>/dev/null | grep -q $'\tdevice$'; then
    echo "No phone connected (adb devices shows none). Connect the Xiaomi 12X and accept the USB-debugging prompt." >&2
    exit 3
fi

# :feature:tuner -> feature/tuner
dir_of() { local d="${1#:}"; echo "${d//://}"; }

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

# Prints tests, failures, device seconds and the failing tests from the module's JUnit XML.
summarize() {
    local dir
    dir="$(dir_of "$1")/build/outputs/androidTest-results/connected/debug"
    python - "$dir" <<'EOF'
import glob, sys, xml.etree.ElementTree as ET
tests = fails = 0; secs = 0.0; failed = []
for f in glob.glob(sys.argv[1] + '/*.xml'):
    for suite in ET.parse(f).getroot().iter('testsuite'):
        tests += int(suite.get('tests', 0)); secs += float(suite.get('time', 0))
        fails += int(suite.get('failures', 0)) + int(suite.get('errors', 0))
        for case in suite.iter('testcase'):
            for bad in list(case.findall('failure')) + list(case.findall('error')):
                msg = (bad.get('message') or bad.text or '').strip().splitlines()
                failed.append(f"  FAIL {case.get('classname').rsplit('.', 1)[-1]}#{case.get('name')}: {msg[0][:200] if msg else ''}")
print(f"{tests} tests, {fails} failed, {secs:.0f}s on the phone")
print('\n'.join(failed))
EOF
}

# MIUI (the Xiaomi test phone) refuses to let a test open the app's screen ("MIUILOG- Permission
# Denied Activity", "Abort background activity starts") unless the package has Xiaomi's own
# "start in background" app-op, MIUIOP 10021. Every install resets it, and Gradle installs right
# before the tests start (~2 s), so a loop on the phone keeps setting it during the whole run.
# Needs "USB debugging (Security settings)" on (see CLAUDE.md).
miui_guard=""
if [ -n "$("$ADB" shell getprop ro.miui.ui.version.name 2>/dev/null | tr -d '\r')" ]; then
    "$ADB" shell 'while true; do for p in $(pm list packages io.github.klaboukes.twangle | cut -d: -f2); do appops set $p 10021 allow; done; sleep 0.2; done' > /dev/null 2>&1 &
    miui_guard=$!
    trap '[ -n "$miui_guard" ] && kill $miui_guard 2>/dev/null' EXIT
fi

status=0
for module in "${modules[@]}"; do
    args=("${extra[@]}")
    [ -n "$filter" ] && args+=("-Pandroid.testInstrumentationRunnerArguments.tests_regex=.*($filter).*")
    [ -n "$class" ] && args+=("-Pandroid.testInstrumentationRunnerArguments.class=$(qualify "$module" "$class")")
    log="$ROOT/build/ui-tests${module//:/-}.log"
    results="$(dir_of "$module")/build/outputs/androidTest-results/connected/debug"
    mkdir -p "$ROOT/build"
    rm -rf "$results"
    start=$SECONDS
    timeout "$MODULE_TIMEOUT" ./gradlew "$module:connectedDebugAndroidTest" "${args[@]}" --console=plain > "$log" 2>&1
    code=$?
    printf '%-18s %3ds  ' "$module" $((SECONDS - start))
    if [ $code = 124 ]; then
        echo "TIMED OUT after ${MODULE_TIMEOUT}s (log: $log)"
        status=1
    elif ! summarize "$module" 2>/dev/null | grep .; then
        echo "no results (log: $log)"
        grep -E "What went wrong|error:|e: " -A3 "$log" | head -15
        status=1
    fi
    [ $code != 0 ] && status=1
done

# Gradle uninstalls the app after :app's tests, which leaves the owner's phone without Twangle.
if [[ " ${modules[*]} " == *" :app "* ]]; then
    if timeout "$MODULE_TIMEOUT" ./gradlew installDebug --console=plain -q > "$ROOT/build/ui-tests-reinstall.log" 2>&1; then
        echo "Twangle reinstalled on the phone."
    else
        echo "Could not reinstall Twangle (log: $ROOT/build/ui-tests-reinstall.log)"
        status=1
    fi
fi
exit $status
