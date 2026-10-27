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

# The screens the pages show, by their number in docs/store-listing.md's table: the home page
# 1, 3, 5, 6 and 7a, the tuner's page 2, the chord name's 5, the pitch trail's 8, the metronome's 6,
# Chords' 7a, the Capo helper's 7b, the instruments' 3.
SHOTS="1 2 3 5 6 7a 7b 8"
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
        width=540
        [ "$n" = 8 ] && width=960  # the phone on its side: a wide picture across the page's column
        "$JAVA" "$SHRINK" "$raw" "$STATIC/shots/$lang/$n.jpg" "$width"
    done
done
