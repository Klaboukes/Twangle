#!/usr/bin/env bash
# Times the pitch detector on the connected phone: CPU per frame for the standard (4096-sample)
# and low-note (8192-sample) windows at 48 kHz, the strum detector (16384 / 32768 samples), the
# watch's chain at 16 and 48 kHz (the little cores stand in for a watch's CPU), the tempo
# detector per microphone chunk, and the drone per 10 ms chunk of each sound.
# See docs/architecture/performance.md, "Low-note window" and "Watch CPU", and
# docs/architecture/strum-and-metronome.md, "Strum check" and "Tempo from music", and
# docs/architecture/notes-and-audio.md, "Drone".
#
#   scripts/dsp-phone-benchmark.sh                 wherever the phone's scheduler runs it
#   TASKSET=0f scripts/dsp-phone-benchmark.sh      pinned to CPUs 0–3 (the Xiaomi's little cores)
#
# Compiles :core:dsp with its test signals, turns it into a dex file with d8, copies it to
# /data/local/tmp and runs DspPhoneBenchmark there with dalvikvm (the phone's ART runtime, JIT
# warmed up; no app install, no sound). Takes about half a minute; the phone may stay locked.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
SDK="${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk"
ADB="$SDK/platform-tools/adb.exe"
D8="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)d8.bat"
CLASS=io.github.klaboukes.twangle.core.dsp.pitch.DspPhoneBenchmark
REMOTE=/data/local/tmp/twangle-dsp-benchmark.jar

if ! "$ADB" devices 2>/dev/null | grep -q $'\tdevice$'; then
    echo "No phone connected (adb devices shows none). Connect the Xiaomi 12X and accept the USB-debugging prompt." >&2
    exit 3
fi

./gradlew -q :core:dsp:testClasses --console=plain

KOTLIN="$(sed -n 's/^kotlin = "\(.*\)"/\1/p' gradle/libs.versions.toml)"
STDLIB="$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/$KOTLIN" -name "kotlin-stdlib-$KOTLIN.jar" | head -1)"
[ -n "$STDLIB" ] || { echo "kotlin-stdlib $KOTLIN not found in the Gradle cache" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
CLASSES=core/dsp/build/classes/kotlin
"$JAVA_HOME/bin/jar" cf "$WORK/dsp.jar" -C "$CLASSES/main" . -C "$CLASSES/testFixtures" .
(cd "$CLASSES/test" && "$JAVA_HOME/bin/jar" uf "$WORK/dsp.jar" io/github/klaboukes/twangle/core/dsp/pitch/DspPhoneBenchmark*.class)
"$D8" --release --min-api 26 --output "$WORK/benchmark.jar" "$WORK/dsp.jar" "$STDLIB"

# Git Bash would turn the phone's /data/... paths into Windows paths; keep them as written.
LOCAL_JAR="$WORK/benchmark.jar"
command -v cygpath >/dev/null && LOCAL_JAR="$(cygpath -w "$LOCAL_JAR")"
export MSYS_NO_PATHCONV=1
"$ADB" push "$LOCAL_JAR" "$REMOTE" >/dev/null
echo "Phone: $("$ADB" shell getprop ro.product.model | tr -d '\r'), Android $("$ADB" shell getprop ro.build.version.release | tr -d '\r')"
"$ADB" shell ${TASKSET:+taskset $TASKSET} dalvikvm -cp "$REMOTE" "$CLASS"
"$ADB" shell rm "$REMOTE"
