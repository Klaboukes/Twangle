#!/usr/bin/env bash
# Makes the website's own small copies of the app's pictures (Phase 54), to commit:
# the app icon from fastlane, and the screenshots from the last store-screenshot run
# (scripts/store-screenshots.sh leaves the screens as they were, without caption or frame,
# in build/store-screenshots/<locale>/raw-<n>.png; those are git-ignored and ~1 MB each).
# Each becomes a 540 px wide JPEG (about 60 KB) in website/static/shots/<language>/.
# Run it after the store screenshots were made again, check the pages, commit the pictures.
#   scripts/website-pictures.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
JAVA="$JAVA_HOME/bin/java"
SHRINK="$ROOT/website/tools/ShrinkImage.java"
STATIC="$ROOT/website/static"

# The screens the pages show, by their number in docs/store-listing.md's table (2.0's listing,
# 2026-11-05): the home page 1 (hero), 5 (the Practice tab's cards), 6, metronome and 7, the tuner's
# page 3, the strum check's 4, the chord name's and Chords' 6, the metronome's page "metronome"
# (saved with store screenshot 5, no store picture of its own), the instruments' 7. 2 (the microphone
# prompt) and 8 (TalkBack at twice the text) show nothing a page needs; Capo and the pitch trail have
# no 2.0 screen in the set, so their pages have none.
SHOTS="1 3 4 5 6 7 metronome"
# Play locale -> the site's language folder.
LOCALES="en-US:en nl-NL:nl"

"$JAVA" "$SHRINK" "$ROOT/fastlane/metadata/android/en-US/images/icon.png" "$STATIC/img/icon.png" 192

for pair in $LOCALES; do
    locale="${pair%%:*}"
    lang="${pair##*:}"
    for n in $SHOTS; do
        raw="$ROOT/build/store-screenshots/$locale/raw-$n.png"
        if [ ! -f "$raw" ]; then
            echo "Missing $raw: run scripts/store-screenshots.sh $locale first." >&2
            exit 1
        fi
        "$JAVA" "$SHRINK" "$raw" "$STATIC/shots/$lang/$n.jpg" 540
    done
done
