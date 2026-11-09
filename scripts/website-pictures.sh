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

# The screens the pages show, as "<raw screen>:<the site's picture>". The site's pictures keep the numbers
# of 2.0's first listing (2026-11-05): the home page 1 (hero), 5 (the Practice tab's cards), 6, metronome
# and 7, the tuner's page 2, the strum check's 4, the chord name's and Chords' 6, the metronome's page
# "metronome", the instruments' 7. The from-scratch set (2026-11-09) numbers its screens anew: 1 the tuner
# in tune, 2 the metronome (also saved as raw-metronome), 4 a bass flat with "Tighten", 5 Chords, 6 the
# Practice cards, 7 the strum check. The instruments' sheet is no longer shot, so the site keeps its 7 as
# it is; 3 (drawn) and 8 (TalkBack at twice the text) show nothing a page needs.
SHOTS="1:1 4:2 7:4 6:5 5:6 metronome:metronome"
# Play locale -> the site's language folder.
LOCALES="en-US:en nl-NL:nl"

"$JAVA" "$SHRINK" "$ROOT/fastlane/metadata/android/en-US/images/icon.png" "$STATIC/img/icon.png" 192

for pair in $LOCALES; do
    locale="${pair%%:*}"
    lang="${pair##*:}"
    for shot in $SHOTS; do
        n="${shot##*:}"
        raw="$ROOT/build/store-screenshots/$locale/raw-${shot%%:*}.png"
        if [ ! -f "$raw" ]; then
            echo "Missing $raw: run scripts/store-screenshots.sh $locale first." >&2
            exit 1
        fi
        "$JAVA" "$SHRINK" "$raw" "$STATIC/shots/$lang/$n.jpg" 540
    done
done
