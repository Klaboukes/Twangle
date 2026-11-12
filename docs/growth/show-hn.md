# Show HN draft

Phase 92, *After the code is open*. **The owner** posts it on https://news.ycombinator.com/submit
from their own account: the title, the GitHub address as the URL, then the text below as the first
comment. It works best on a weekday morning in the US (around 15:00 to 17:00 Dutch time). Stay
around for the first two hours to answer questions. Claude can draft answers to the hard ones.

Hacker News readers will ask how the code was written, so the post says it up front.

## Title
Show HN: Twangle – a guitar tuner for Android with no ads and no internet permission

## URL
https://github.com/Klaboukes/Twangle

## First comment
> Hi HN, I made Twangle, a free guitar tuner for Android (and Wear OS). Most free tuners on the Play
> Store show ads, or ask for an account or more permissions than a tuner needs. I wanted one that does
> none of that and stays free, so I built it.
>
> - It finds the pitch with YIN (de Cheveigné & Kawahara, 2002), about 47 times a second, within
>   ±1 cent on test signals, from a 5-string bass's low B to the highest frets.
> - The app has no INTERNET permission, so it can't send anything anywhere. Audio is analysed live
>   and never stored. No analytics, no crash reporting.
> - Besides the tuner: a strum check (every string at once), a 12th-fret intonation check, chord
>   names, a metronome, chord charts, scales, a capo helper and ear training, in 36 languages, with
>   TalkBack support and a high-contrast look.
> - The watch app tunes with the watch's own microphone, with no phone needed.
>
> It's GPL-3.0. The Play build has one non-free library (Google's in-app review dialog). The free
> build, `-Ptwangle.foss=true`, leaves it out; that's the one going to F-Droid.
>
> About how it's made: I've been a software engineer for over 20 years, but I'm new to Android and
> Kotlin. The code is written with Claude (Anthropic's AI), to my design and decisions; I review
> every change and test it on real phones with a real guitar. The rules that work follows are in
> CLAUDE.md, if you're curious how that works in practice.
>
> Google Play: https://play.google.com/store/apps/details?id=io.github.klaboukes.twangle
> Website: https://klaboukes.github.io/
>
> I'd love to hear what you think, especially from players and from anyone who knows pitch
> detection better than I do.

## Before posting
- Check the facts against the app as it is then (version, languages, tools).
- If F-Droid has Twangle by then, add its link
  (`https://f-droid.org/packages/io.github.klaboukes.twangle/`) beside Google Play's.
